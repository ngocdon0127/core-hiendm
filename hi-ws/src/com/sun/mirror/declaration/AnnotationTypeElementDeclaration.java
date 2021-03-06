/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.mirror.declaration;


/**
 * Represents an element of an annotation type.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this interface is included in {@link
 * javax.lang.model.element.ExecutableElement}.
 *
 * @author Joe Darcy
 * @author Scott Seligman
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public interface AnnotationTypeElementDeclaration extends MethodDeclaration {

    /**
     * Returns the default value of this element.
     *
     * @return the default value of this element, or null if this element
     * has no default.
     */
    AnnotationValue getDefaultValue();

    /**
     * {@inheritDoc}
     */
    AnnotationTypeDeclaration getDeclaringType();
}
