/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.hh.xml.internal.bind.v2.schemagen.xmlschema;

import com.hh.xml.internal.txw2.TypedXmlWriter;
import com.hh.xml.internal.txw2.annotation.XmlElement;

public interface SchemaTop
    extends Redefinable, TypedXmlWriter
{


    @XmlElement
    public TopLevelAttribute attribute();

    @XmlElement
    public TopLevelElement element();

}
