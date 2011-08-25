/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */


package org.glassfish.fighterfish.test.util;

import org.ops4j.pax.exam.Option;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Represents configuration common to all tests.
 * It reads configuration information from System properties and configures various underlying objects.
 *
 * @author Sanjeeb.Sahoo@Sun.COM
 */
public class TestsConfiguration {

    private File gfHome;
    private String platform;
    private long testTimeout;
    private long examTimeout;

    protected Logger logger = Logger.getLogger(getClass().getPackage().getName());

    private static TestsConfiguration instance;
    private File fwStorage;

    public synchronized static TestsConfiguration getInstance() {
        if (instance == null) {
            instance = new TestsConfiguration(System.getProperties());
        }
        return instance;
    }

    private TestsConfiguration(Properties properties) {
        gfHome =  new File(properties.getProperty(Constants.GLASSFISH_INSTALL_ROOT_PROP));
        platform  = properties.getProperty(Constants.GLASSFISH_PLATFORM_PROP, Constants.DEFAULT_GLASSFISH_PLATFORM);
        testTimeout = Long.parseLong(
                properties.getProperty(Constants.FIGHTERFISH_TEST_TIMEOUT_PROP, Constants.FIGHTERFISH_TEST_TIMEOUT_DEFAULT_VALUE));
        examTimeout = Long.parseLong(
                properties.getProperty(Constants.EXAM_TIMEOUT_PROP, Constants.FIGHTERFISH_TEST_TIMEOUT_DEFAULT_VALUE));
        final String s = properties.getProperty(org.osgi.framework.Constants.FRAMEWORK_STORAGE);
        if (s != null) fwStorage = new File(s);
    }

    public long getTimeout() {
        return testTimeout;
    }

    public File getGfHome() {
        return gfHome;
    }

    public String getPlatform() {
        return platform;
    }

    public Option[] getPaxExamConfiguration() throws IOException {
        return new PaxExamConfigurator(getGfHome(), getPlatform(), examTimeout, fwStorage).configure();
    }
}
