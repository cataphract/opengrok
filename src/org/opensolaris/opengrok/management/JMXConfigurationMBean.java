/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.management;

import java.io.IOException;


/**
 * MBean for doing settings and get on the OpenGrok RuntimeEnvironment
 *
 * @author Jan Berg
 */
public interface JMXConfigurationMBean {

    /**
     * Get the current OpenGrok configuration object
     * @return String XML representation of the opengrok configuration
     */
    public String getConfiguration();

    /**
     * Deploy a new configuration for OpenGrok
     * @param config String the configuration object in xml to set
     */
    public void setConfiguration(String config) throws IOException;
}
