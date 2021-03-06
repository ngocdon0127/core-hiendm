/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package com.hh.xml.internal.ws.addressing;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.hh.xml.internal.ws.addressing.model.ActionNotSupportedException;
import com.hh.xml.internal.ws.addressing.model.InvalidAddressingHeaderException;
import com.hh.xml.internal.ws.api.EndpointAddress;
import com.hh.xml.internal.ws.api.SOAPVersion;
import com.hh.xml.internal.ws.api.WSBinding;
import com.hh.xml.internal.ws.api.WSService;
import com.hh.xml.internal.ws.api.addressing.WSEndpointReference;
import com.hh.xml.internal.ws.api.message.HeaderList;
import com.hh.xml.internal.ws.api.message.Message;
import com.hh.xml.internal.ws.api.message.Messages;
import com.hh.xml.internal.ws.api.message.Packet;
import com.hh.xml.internal.ws.api.model.wsdl.WSDLBoundOperation;
import com.hh.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.hh.xml.internal.ws.api.pipe.ClientTubeAssemblerContext;
import com.hh.xml.internal.ws.api.pipe.Fiber;
import com.hh.xml.internal.ws.api.pipe.NextAction;
import com.hh.xml.internal.ws.api.pipe.TransportTubeFactory;
import com.hh.xml.internal.ws.api.pipe.Tube;
import com.hh.xml.internal.ws.api.pipe.TubeCloner;
import com.hh.xml.internal.ws.api.server.WSEndpoint;
import com.hh.xml.internal.ws.developer.JAXWSProperties;
import com.hh.xml.internal.ws.message.FaultDetailHeader;
import com.hh.xml.internal.ws.resources.AddressingMessages;
import com.hh.xml.internal.ws.binding.BindingImpl;

import com.hh.webservice.soap.SOAPFault;
import com.hh.webservice.ws.WebServiceException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles WS-Addressing for the server.
 *
 * @author Rama Pulavarthi
 * @author Kohsuke Kawaguchi
 * @author Arun Gupta
 */
public class WsaServerTube extends WsaTube {
    private WSEndpoint endpoint;
    // store the replyTo/faultTo of the message currently being processed.
    // both will be set to non-null in processRequest
    private WSEndpointReference replyTo;
    private WSEndpointReference faultTo;
    private boolean isAnonymousRequired = false;
    /**
     * WSDLBoundOperation calculated on the Request payload.
     * Used for determining ReplyTo or Fault Action for non-anonymous responses     *
     */
    private WSDLBoundOperation wbo;
    
    public WsaServerTube(WSEndpoint endpoint, @NotNull WSDLPort wsdlPort, WSBinding binding, Tube next) {
        super(wsdlPort, binding, next);
        this.endpoint = endpoint;

    }

    public WsaServerTube(WsaServerTube that, TubeCloner cloner) {
        super(that, cloner);
        endpoint = that.endpoint;
    }

    public WsaServerTube copy(TubeCloner cloner) {
        return new WsaServerTube(this, cloner);
    }

    @Override
    public @NotNull NextAction processRequest(Packet request) {
        Message msg = request.getMessage();
        if(msg==null)   return doInvoke(next,request); // hmm?

        // expose bunch of addressing related properties for advanced applications
        request.addSatellite(new WsaPropertyBag(addressingVersion,soapVersion,request));

        // Store request ReplyTo and FaultTo in requestPacket.invocationProperties
        // so that they can be used after responsePacket is received.
        // These properties are used if a fault is thrown from the subsequent Pipe/Tubes.

        HeaderList hl = request.getMessage().getHeaders();
        try {
            replyTo = hl.getReplyTo(addressingVersion, soapVersion);
            faultTo = hl.getFaultTo(addressingVersion, soapVersion);
        } catch (InvalidAddressingHeaderException e) {
            LOGGER.log(Level.WARNING,
                    addressingVersion.getInvalidMapText()+", Problem header:" + e.getProblemHeader()+ ", Reason: "+ e.getSubsubcode(),e);
            SOAPFault soapFault = helper.createInvalidAddressingHeaderFault(e, addressingVersion);
            // WS-A fault processing for one-way methods
            if ((wsdlPort!=null) && request.getMessage().isOneWay(wsdlPort)) {
                Packet response = request.createServerResponse(null, wsdlPort, null, binding);
                return doReturnWith(response);
            }

            Message m = Messages.create(soapFault);
            if (soapVersion == SOAPVersion.SOAP_11) {
                FaultDetailHeader s11FaultDetailHeader = new FaultDetailHeader(addressingVersion, addressingVersion.problemHeaderQNameTag.getLocalPart(), e.getProblemHeader());
                m.getHeaders().add(s11FaultDetailHeader);
            }

            Packet response = request.createServerResponse(m, wsdlPort, null, binding);
            return doReturnWith(response);
        }

        // defaulting
        if (replyTo == null)    replyTo = addressingVersion.anonymousEpr;
        if (faultTo == null)    faultTo = replyTo;

        wbo = getWSDLBoundOperation(request);
        isAnonymousRequired = isAnonymousRequired(wbo);

        Packet p = validateInboundHeaders(request);
        // if one-way message and WS-A header processing fault has occurred,
        // then do no further processing
        if (p.getMessage() == null)
            // request message is invalid, exception is logged by now  and response is sent back  with null message
            return doReturnWith(p);

        // if we find an error in addressing header, just turn around the direction here
        if (p.getMessage().isFault()) {
            // close the transportBackChannel if we know that
            // we'll never use them
            if (!(isAnonymousRequired) &&
                    !faultTo.isAnonymous() && request.transportBackChannel != null)
                request.transportBackChannel.close();
            return processResponse(p);
        }
        // close the transportBackChannel if we know that
        // we'll never use them
        if (!(isAnonymousRequired) &&
                !replyTo.isAnonymous() && !faultTo.isAnonymous() &&
                request.transportBackChannel != null)
            request.transportBackChannel.close();
        return doInvoke(next,p);
    }

    protected boolean isAnonymousRequired(@Nullable WSDLBoundOperation wbo) {
        //this requirement can only be specified in W3C case, Override this in W3C case.
        return false;
    }

    protected void checkAnonymousSemantics(WSDLBoundOperation wbo, WSEndpointReference replyTo, WSEndpointReference faultTo) {
        //this requirement can only be specified in W3C case, Override this in W3C case.
    }

    @Override
    public @NotNull NextAction processResponse(Packet response) {
        Message msg = response.getMessage();
        if (msg ==null)
            return doReturnWith(response);  // one way message. Nothing to see here. Move on.

        WSEndpointReference target = msg.isFault()?faultTo:replyTo;

        if(target.isAnonymous() || isAnonymousRequired )
            // the response will go back the back channel. most common case
            return doReturnWith(response);

        if(target.isNone()) {
            // the caller doesn't want to hear about it, so proceed like one-way
            response.setMessage(null);
            return doReturnWith(response);
        }

        // send the response to this EPR.
        processNonAnonymousReply(response,target);

        // then we'll proceed the rest like one-way.
        response.setMessage(null);
        return doReturnWith(response);
    }

    /**
     * Send a response to a non-anonymous address. Also closes the transport back channel
     * of {@link Packet} if it's not closed already.
     *
     * <p>
     * TODO: ideally we should be doing this by creating a new fiber.
     *
     * @param packet
     *      The response from our server, which will be delivered to the destination.
     * @param target
     *      Where do we send the packet to?
     */
    private void processNonAnonymousReply(final Packet packet, WSEndpointReference target) {
        // at this point we know we won't be sending anything back through the back channel,
        // so close it first to let the client go.
        if (packet.transportBackChannel != null)
            packet.transportBackChannel.close();

        if ((wsdlPort!=null) && packet.getMessage().isOneWay(wsdlPort)) {
            // one way message but with replyTo. I believe this is a hack for WS-TX - KK.
            LOGGER.fine(AddressingMessages.NON_ANONYMOUS_RESPONSE_ONEWAY());
            return;
        }

        EndpointAddress adrs;
        try {
             adrs = new EndpointAddress(URI.create(target.getAddress()));
        } catch (NullPointerException e) {
            throw new WebServiceException(e);
        } catch (IllegalArgumentException e) {
            throw new WebServiceException(e);
        }

        // we need to assemble a pipeline to talk to this endpoint.
        // TODO: what to pass as WSService?
        Tube transport = TransportTubeFactory.create(Thread.currentThread().getContextClassLoader(),
            new ClientTubeAssemblerContext(adrs, wsdlPort, (WSService) null, binding,endpoint.getContainer(),((BindingImpl)binding).createCodec(),null));

        packet.endpointAddress = adrs;
        String action = packet.getMessage().isFault() ?
                helper.getFaultAction(wbo, packet) :
                helper.getOutputAction(wbo);
        //set the SOAPAction, as its got to be same as wsa:Action
        packet.soapAction = action;
        packet.expectReply = false;
        Fiber.current().runSync(transport, packet);
    }

    @Override
    protected void validateAction(Packet packet) {
        //There may not be a WSDL operation.  There may not even be a WSDL.
        //For instance this may be a RM CreateSequence message.
        WSDLBoundOperation wsdlBoundOperation = getWSDLBoundOperation(packet);

        if (wsdlBoundOperation == null)
            return;

        String gotA = packet.getMessage().getHeaders().getAction(addressingVersion, soapVersion);

        if (gotA == null)
            throw new WebServiceException(AddressingMessages.VALIDATION_SERVER_NULL_ACTION());

        String expected = helper.getInputAction(packet);
        String soapAction = helper.getSOAPAction(packet);
        if (helper.isInputActionDefault(packet) && (soapAction != null && !soapAction.equals("")))
            expected = soapAction;

        if (expected != null && !gotA.equals(expected)) {
            throw new ActionNotSupportedException(gotA);
        }
    }

    @Override
    protected void checkMessageAddressingProperties(Packet packet) {
        super.checkMessageAddressingProperties(packet);

        // wsaw:Anonymous validation
        WSDLBoundOperation wsdlBoundOperation = getWSDLBoundOperation(packet);
        checkAnonymousSemantics(wsdlBoundOperation, replyTo, faultTo);
         // check if addresses are valid
        checkNonAnonymousAddresses(replyTo,faultTo);
    }

    private void checkNonAnonymousAddresses(WSEndpointReference replyTo, WSEndpointReference faultTo) {
        if (!replyTo.isAnonymous()) {
            try {
                new EndpointAddress(URI.create(replyTo.getAddress()));
            } catch (Exception e) {
                throw new InvalidAddressingHeaderException(addressingVersion.replyToTag, addressingVersion.invalidAddressTag);
            }
        }
        //for now only validate ReplyTo
        /*
        if (!faultTo.isAnonymous()) {
            try {
                new EndpointAddress(URI.create(faultTo.getAddress()));
            } catch (IllegalArgumentException e) {
                throw new InvalidAddressingHeaderException(addressingVersion.faultToTag, addressingVersion.invalidAddressTag);
            }
        }
        */

    }

    /**
     * @deprecated
     *      Use {@link JAXWSProperties#ADDRESSING_MESSAGEID}.
     */
    public static final String REQUEST_MESSAGE_ID = "com.sun.xml.internal.ws.addressing.request.messageID";

    private static final Logger LOGGER = Logger.getLogger(WsaServerTube.class.getName());
}
