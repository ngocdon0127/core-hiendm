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

package com.hh.xml.internal.ws.client;

import com.sun.istack.internal.NotNull;
import com.hh.xml.internal.ws.api.EndpointAddress;
import com.hh.xml.internal.ws.api.PropertySet;
import com.hh.xml.internal.ws.api.message.Packet;

import com.hh.webservice.ws.BindingProvider;
import com.hh.webservice.ws.WebServiceException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Request context implementation.
 *
 * <h2>Why a custom map?</h2>
 * <p>
 * The JAX-WS spec exposes properties as a {@link Map}, but if we just use
 * an ordinary {@link HashMap} for this, it doesn't work as fast as we'd like
 * it to be. Hence we have this class.
 *
 * <p>
 * We expect the user to set a few properties and then use that same
 * setting to make a bunch of invocations. So we'd like to take some hit
 * when the user actually sets a property to do some computation,
 * then use that computed value during a method invocation again and again.
 *
 * <p>
 * For this goal, we use {@link PropertySet} and implement some properties
 * as virtual properties backed by methods. This allows us to do the computation
 * in the setter, and store it in a field.
 *
 * <p>
 * These fields are used by {@link Stub#process} to populate a {@link Packet}.
 *
 *
 *
 * <h2>How it works?</h2>
 * <p>
 * We make an assumption that a request context is mostly used to just
 * get and put values, not really for things like enumerating or size.
 *
 * <p>
 * So we start by maintaining state as a combination of {@link #others}
 * bag and strongly-typed fields. As long as the application uses
 * just {@link Map#put}, {@link Map#get}, and {@link Map#putAll}, we can
 * do things in this way. In this mode a {@link Map} we return works as
 * a view into {@link RequestContext}, and by itself it maintains no state.
 *
 * <p>
 * If {@link RequestContext} is in this mode, its state can be copied
 * efficiently into {@link Packet}.
 *
 * <p>
 * Once the application uses any other {@link Map} method, we move to
 * the "fallback" mode, where the data is actually stored in a {@link HashMap},
 * this is necessary for implementing the map interface contract correctly.
 *
 * <p>
 * To be safe, once we fallback, we'll never come back to the efficient state.
 *
 *
 *
 * <h2>Caution</h2>
 * <p>
 * Once we are in the fallback mode, none of the strongly typed field will
 * be used, and they may contain stale values. So the only method
 * the code outside this class can safely use is {@link #copy()},
 * {@link #fill(Packet)}, and constructors. Do not access the strongly
 * typed fields nor {@link #others} directly.
 *
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings({"SuspiciousMethodCalls"})
public final class RequestContext extends PropertySet {
    private static final Logger LOGGER = Logger.getLogger(RequestContext.class.getName());
    /**
     * The default value to be use for {@link #contentNegotiation} obtained
     * from a system property.
     * <p>
     * This enables content negotiation to be easily switched on by setting
     * a system property on the command line for testing purposes tests.
     */
    private static ContentNegotiation defaultContentNegotiation =
            ContentNegotiation.obtainFromSystemProperty();

    /**
     * Stores properties that don't fit the strongly-typed fields.
     */
    private final Map<String,Object> others;

    /**
     * The endpoint address to which this message is sent to.
     *
     * <p>
     * This is the actual data store for {@link BindingProvider#ENDPOINT_ADDRESS_PROPERTY}.
     */
    private @NotNull EndpointAddress endpointAddress;

    /**
     * Creates {@link BindingProvider#ENDPOINT_ADDRESS_PROPERTY} view
     * on top of {@link #endpointAddress}.
     *
     * @deprecated
     *      always access {@link #endpointAddress}.
     */
    @Property(BindingProvider.ENDPOINT_ADDRESS_PROPERTY)
    public String getEndPointAddressString() {
        return endpointAddress.toString();
    }

    public void setEndPointAddressString(String s) {
        if(s==null)
            throw new IllegalArgumentException();
        else
            this.endpointAddress = EndpointAddress.create(s);
    }

    public void setEndpointAddress(@NotNull EndpointAddress epa) {
        this.endpointAddress = epa;
    }

    public @NotNull EndpointAddress getEndpointAddress() {
        return endpointAddress;
    }

    /**
     * The value of {@link ContentNegotiation#PROPERTY}
     * property.
     */
    public ContentNegotiation contentNegotiation = defaultContentNegotiation;

    @Property(ContentNegotiation.PROPERTY)
    public String getContentNegotiationString() {
        return contentNegotiation.toString();
    }

    public void setContentNegotiationString(String s) {
        if(s==null)
            contentNegotiation = ContentNegotiation.none;
        else {
            try {
                contentNegotiation = ContentNegotiation.valueOf(s);
            } catch (IllegalArgumentException e) {
                // If the value is not recognized default to none
                contentNegotiation = ContentNegotiation.none;
            }
        }
    }
    /**
     * The value of the SOAPAction header associated with the message.
     *
     * <p>
     * For outgoing messages, the transport may sends out this value.
     * If this field is null, the transport may choose to send <tt>""</tt>
     * (quoted empty string.)
     *
     * For incoming messages, the transport will set this field.
     * If the incoming message did not contain the SOAPAction header,
     * the transport sets this field to null.
     *
     * <p>
     * If the value is non-null, it must be always in the quoted form.
     * The value can be null.
     *
     * <p>
     * Note that the way the transport sends this value out depends on
     * transport and SOAP version.
     *
     * For HTTP transport and SOAP 1.1, BP requires that SOAPAction
     * header is present (See {@BP R2744} and {@BP R2745}.) For SOAP 1.2,
     * this is moved to the parameter of the "application/soap+xml".
     */

    private String soapAction;

    @Property(BindingProvider.SOAPACTION_URI_PROPERTY)
    public String getSoapAction(){
        return soapAction;
    }
    public void setSoapAction(String sAction){
        if(sAction == null) {
            throw new IllegalArgumentException("SOAPAction value cannot be null");
        }
        soapAction = sAction;
    }

    /**
     * This controls whether BindingProvider.SOAPACTION_URI_PROPERTY is used.
     * See BindingProvider.SOAPACTION_USE_PROPERTY for details.
     *
     * This only control whether value of BindingProvider.SOAPACTION_URI_PROPERTY is used or not and not
     * if it can be sent if it can be obtained by other means such as WSDL binding
     */
    private Boolean soapActionUse;
    @Property(BindingProvider.SOAPACTION_USE_PROPERTY)
    public Boolean getSoapActionUse(){
        return soapActionUse;
    }
    public void setSoapActionUse(Boolean sActionUse){
        soapActionUse = sActionUse;
    }

    /**
     * {@link Map} exposed to the user application.
     */
    private final MapView mapView = new MapView();

    /**
     * Creates an empty {@link RequestContext}.
     */
    /*package*/ RequestContext() {
        others = new HashMap<String, Object>();
    }

    /**
     * Copy constructor.
     */
    private RequestContext(RequestContext that) {
        others = new HashMap<String,Object>(that.others);
        endpointAddress = that.endpointAddress;
        soapAction = that.soapAction;
        contentNegotiation = that.contentNegotiation;
        // this is fragile, but it works faster
    }

    /**
     * The efficient get method that reads from {@link RequestContext}.
     */
    public Object get(Object key) {
        if(super.supports(key))
            return super.get(key);
        else
            return others.get(key);
    }

    /**
     * The efficient put method that updates {@link RequestContext}.
     */
    public Object put(String key, Object value) {
        if(super.supports(key))
            return super.put(key,value);
        else
            return others.put(key,value);
    }

    /**
     * Gets the {@link Map} view of this request context.
     *
     * @return
     *      Always same object. Returned map is live.
     */
    public Map<String,Object> getMapView() {
        return mapView;
    }

    /**
     * Fill a {@link Packet} with values of this {@link RequestContext}.
     */
    public void fill(Packet packet, boolean isAddressingEnabled) {
        if(mapView.fallbackMap==null) {
            if (endpointAddress != null)
                packet.endpointAddress = endpointAddress;
            packet.contentNegotiation = contentNegotiation;

            //JAX-WS-596: Check the semantics of SOAPACTION_USE_PROPERTY before using the SOAPACTION_URI_PROPERTY for
            // SoapAction as specified in the javadoc of BindingProvider. The spec seems to be little contradicting with
            //  javadoc and says that the use property effects the sending of SOAPAction property.
            // Since the user has the capability to set the value as "" if needed, implement the javadoc behavior.

            if ((soapActionUse != null && soapActionUse) || (soapActionUse == null && isAddressingEnabled)) {
                if (soapAction != null) {
                    packet.soapAction = soapAction;
                }
            }
            if((!isAddressingEnabled && (soapActionUse == null || !soapActionUse)) && soapAction != null) {
                LOGGER.warning("BindingProvider.SOAPACTION_URI_PROPERTY is set in the RequestContext but is ineffective," +
                        " Either set BindingProvider.SOAPACTION_USE_PROPERTY to true or enable AddressingFeature");
            }
            if(!others.isEmpty()) {
                packet.invocationProperties.putAll(others);
                //if it is not standard property it deafults to Scope.HANDLER
                packet.getHandlerScopePropertyNames(false).addAll(others.keySet());
            }
        } else {
            Set<String> handlerScopePropertyNames = new HashSet<String>();
            // fallback mode, simply copy map in a slow way
            for (Entry<String,Object> entry : mapView.fallbackMap.entrySet()) {
                String key = entry.getKey();
                if(packet.supports(key))
                    packet.put(key,entry.getValue());
                else
                    packet.invocationProperties.put(key,entry.getValue());

                //if it is not standard property it deafults to Scope.HANDLER
                if(!super.supports(key)) {
                    handlerScopePropertyNames.add(key);
                }
            }

            if(!handlerScopePropertyNames.isEmpty())
                packet.getHandlerScopePropertyNames(false).addAll(handlerScopePropertyNames);
        }
    }

    public RequestContext copy() {
        return new RequestContext(this);
    }


    private final class MapView implements Map<String,Object> {
        private Map<String,Object> fallbackMap;

        private Map<String,Object> fallback() {
            if(fallbackMap==null) {
                // has to fall back. fill in fallbackMap
                fallbackMap = new HashMap<String,Object>(others);
                // then put all known properties
                for (Map.Entry<String,Accessor> prop : propMap.entrySet()) {
                    fallbackMap.put(prop.getKey(),prop.getValue().get(RequestContext.this));
                }
            }
            return fallbackMap;
        }

        public int size() {
            return fallback().size();
        }

        public boolean isEmpty() {
            return fallback().isEmpty();
        }

        public boolean containsKey(Object key) {
            return fallback().containsKey(key);
        }

        public boolean containsValue(Object value) {
            return fallback().containsValue(value);
        }

        public Object get(Object key) {
            if (fallbackMap ==null) {
                return RequestContext.this.get(key);
            } else {
                return fallback().get(key);
            }
        }

        public Object put(String key, Object value) {
            if(fallbackMap ==null)
                return RequestContext.this.put(key,value);
            else
                return fallback().put(key, value);
        }

        public Object remove(Object key) {
            if (fallbackMap ==null) {
                return RequestContext.this.remove(key);
            } else {
                return fallback().remove(key);
            }
        }

        public void putAll(Map<? extends String, ? extends Object> t) {
            for (Entry<? extends String, ? extends Object> e : t.entrySet()) {
                put(e.getKey(),e.getValue());
            }
        }

        public void clear() {
            fallback().clear();
        }

        public Set<String> keySet() {
            return fallback().keySet();
        }

        public Collection<Object> values() {
            return fallback().values();
        }

        public Set<Entry<String, Object>> entrySet() {
            return fallback().entrySet();
        }
    }

    protected PropertyMap getPropertyMap() {
        return propMap;
    }

    private static final PropertyMap propMap = parse(RequestContext.class);
}
