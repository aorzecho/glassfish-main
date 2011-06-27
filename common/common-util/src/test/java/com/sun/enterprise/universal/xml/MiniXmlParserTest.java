/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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
package com.sun.enterprise.universal.xml;

import java.io.File;
import java.net.*;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.sun.enterprise.util.HostAndPort;

/**
 * @author bnevins
 */
@org.junit.Ignore // This test takes a minute to run, so I (Sahoo) have disabled it.
@SuppressWarnings({"StaticNonFinalField"})
public class MiniXmlParserTest {

    private static File hasProfiler;
    private static File wrongOrder;
    private static File rightOrder;
    private static File noconfig;
    private static File adminport;
    private static File adminport2;
    private static File noCloseRightOrder;
    private static File noCloseWrongOrder;
    private static File noDomainName;
    private static File bigDomain;
    private static File monitoringFalse;
    private static File monitoringTrue;
    private static File monitoringNone;
    private static File v2DomainXml;
    private static File issue9127DomainXml;

    @BeforeClass
    public static void setUpClass() throws Exception {
        String wrongOrderString = MiniXmlParserTest.class.getClassLoader().getResource("wrongorder.xml").getPath();
        wrongOrder = new File(wrongOrderString);

        if (!wrongOrder.exists() && wrongOrderString.indexOf("%20") >= 0)
            throw new RuntimeException("You can not run unit tests if "
                    + "your workspace has a space in its path.");

        rightOrder = new File(MiniXmlParserTest.class.getClassLoader().getResource("rightorder.xml").getPath());
        noconfig = new File(MiniXmlParserTest.class.getClassLoader().getResource("noconfig.xml").getPath());
        hasProfiler = new File(MiniXmlParserTest.class.getClassLoader().getResource("hasprofiler.xml").getPath());
        adminport = new File(MiniXmlParserTest.class.getClassLoader().getResource("adminport.xml").getPath());
        adminport2 = new File(MiniXmlParserTest.class.getClassLoader().getResource("adminport2.xml").getPath());
        noCloseRightOrder = new File(
                MiniXmlParserTest.class.getClassLoader().getResource("rightordernoclosedomain.xml").getPath());
        noCloseWrongOrder = new File(
                MiniXmlParserTest.class.getClassLoader().getResource("wrongordernoclosedomain.xml").getPath());
        noDomainName = new File(MiniXmlParserTest.class.getClassLoader().getResource("nodomainname.xml").getPath());
        bigDomain = new File(MiniXmlParserTest.class.getClassLoader().getResource("big.xml").getPath());
        monitoringFalse = new File(
                MiniXmlParserTest.class.getClassLoader().getResource("monitoringFalse.xml").getPath());
        monitoringTrue = new File(MiniXmlParserTest.class.getClassLoader().getResource("monitoringTrue.xml").getPath());
        monitoringNone = new File(MiniXmlParserTest.class.getClassLoader().getResource("monitoringNone.xml").getPath());


        if (canAccessInternet())
            v2DomainXml = new File(MiniXmlParserTest.class.getClassLoader().getResource("v2domain.xml").getPath());
        else
            v2DomainXml = null;

        issue9127DomainXml = new File(MiniXmlParserTest.class.getClassLoader().getResource("domain9127.xml").getPath());
        assertTrue(wrongOrder.exists());
        assertTrue(rightOrder.exists());
        assertTrue(noconfig.exists());
        assertTrue(hasProfiler.exists());
        assertTrue(noDomainName.exists());
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

// --------------------------- CONSTRUCTORS ---------------------------
    public MiniXmlParserTest() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Positive Test Case where servers appears after configs
     *
     * @throws MiniXmlParserException
     */
    @Test
    public void serversAfterConfigs() {
        try {
            MiniXmlParser instance = new MiniXmlParser(wrongOrder, "server");
            Map<String, String> javaConfig = instance.getJavaConfig();
            List<String> jvmOptions = instance.getJvmOptions();
            assertEquals("JVMOPTION1", jvmOptions.get(0));
            assertEquals("JVMOPTION2", jvmOptions.get(1));
            assertEquals("test", javaConfig.get("test"));
        }
        catch (MiniXmlParserException ex) {
            Logger.getLogger(MiniXmlParserTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Test that the correct Exception is thrown for a null xml File
     *
     * @throws MiniXmlParserException
     */
    @Test(expected = MiniXmlParserException.class)
    public void nullXmlFile() throws MiniXmlParserException {
        new MiniXmlParser(null, "server");
    }

    /**
     * Test that the correct Exception is thrown for a non-existing xml File
     *
     * @throws MiniXmlParserException
     */
    @Test(expected = MiniXmlParserException.class)
    public void nonexistentFile() throws MiniXmlParserException {
        new MiniXmlParser(new File("."), "server");
    }

    /**
     * Positive Test Case where configs appears after servers
     *
     * @throws MiniXmlParserException
     */
    @Test
    public void configsAfterServers() {
        try {
            MiniXmlParser instance = new MiniXmlParser(rightOrder, "server");
            Map<String, String> javaConfig = instance.getJavaConfig();
            List<String> jvmOptions = instance.getJvmOptions();
            assertEquals("JVMOPTION1", jvmOptions.get(0));
            assertEquals("JVMOPTION2", jvmOptions.get(1));
            assertEquals("test", javaConfig.get("test"));
        }
        catch (MiniXmlParserException ex) {
            Logger.getLogger(MiniXmlParserTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Negative Test Case where there is no "server-config"
     *
     * @throws MiniXmlParserException
     */
    @Test(expected = MiniXmlParserException.class)
    public void noServerConfig() throws MiniXmlParserException {
        try {
            new MiniXmlParser(noconfig, "server");
        }
        catch (MiniXmlParserException ex) {
            throw ex;
        }
    }

    /*
     * Positive test cases -- look at <system-property>
     */
    @Test
    public void systemProperties() {
        try {
            MiniXmlParser instance = new MiniXmlParser(rightOrder, "server");
            Map<String, String> javaConfig = instance.getJavaConfig();
            List<String> jvmOptions = instance.getJvmOptions();
            Map<String, String> sysProps = instance.getSystemProperties();
            assertEquals("JVMOPTION1", jvmOptions.get(0));
            assertEquals("JVMOPTION2", jvmOptions.get(1));
            assertEquals("test", javaConfig.get("test"));
            assertEquals("true", sysProps.get("beforeJavaConfig"));
            assertEquals("true", sysProps.get("afterJavaConfig"));
            assertNull(sysProps.get("foo"));
            assertEquals(sysProps.size(), 3);
        }
        catch (MiniXmlParserException ex) {
            Logger.getLogger(MiniXmlParserTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /*
     * Positive test case -- make sure system-property in <server> overrides the one in <config>
     */
    @Test
    public void systemPropertyOverrides() {
        try {
            MiniXmlParser instance = new MiniXmlParser(rightOrder, "server");
            Map<String, String> sysProps = instance.getSystemProperties();
            assertEquals("valueFromServer", sysProps.get("test-prop"));
        }
        catch (MiniXmlParserException ex) {
            Logger.getLogger(MiniXmlParserTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /*
     * Positive test case -- make sure profiler is parsed correctly
     * here is the piece of xml it will be parsing:
     *
    <profiler classpath="/profiler/class/path" enabled="true" name="MyProfiler" native-library-path="/bin">
    <jvm-options>-Dprofiler3=foo3</jvm-options>
    <jvm-options>-Dprofiler2=foo2</jvm-options>
    <jvm-options>-Dprofiler1=foof</jvm-options>
    </profiler>
     *
     */
    @Test
    public void profilerParsing() {
        try {
            MiniXmlParser instance = new MiniXmlParser(hasProfiler, "server");
            Map<String, String> config = instance.getProfilerConfig();
            List<String> jvm = instance.getProfilerJvmOptions();
            Map<String, String> sysProps = instance.getProfilerSystemProperties();
            assertEquals(3, jvm.size());
            assertEquals("-Dprofiler3=foo3", jvm.get(0));
            assertEquals("-Dprofiler2=foo2", jvm.get(1));
            assertEquals("-Dprofiler1=foof", jvm.get(2));
            assertNotNull(config);
            assertEquals(3, config.size());
            assertEquals("/profiler/class/path", config.get("classpath"));
            assertEquals("MyProfiler", config.get("name"));
            assertEquals("/bin", config.get("native-library-path"));
            assertEquals(2, sysProps.size());
            assertEquals("value1", sysProps.get("name1"));
            assertEquals("value2", sysProps.get("name2"));
        }
        catch (MiniXmlParserException ex) {
            Logger.getLogger(MiniXmlParserTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /*
     * Exercise the parsing of asadmin virtual server, network-listener and port numbers
     * this one tests for TWO listeners
     */
    @Test
    public void findTwoAdminPorts() {
        try {
            MiniXmlParser instance = new MiniXmlParser(adminport2, "server");
            List<HostAndPort> addrs = instance.getAdminAddresses();
            assertEquals(2, addrs.size());
            boolean saw3333 = false, saw4444 = false, sawSecure = false;
            for (HostAndPort addr : addrs) {
                if (addr.getPort() == 3333)
                    saw3333 = true;
                if (addr.getPort() == 4444) {
                    saw4444 = true;
                    if (addr.isSecure())
                        sawSecure = true;
                }
            }
            assertTrue("Saw port 3333", saw3333);
            assertTrue("Saw port 4444", saw4444);
            assertTrue("Saw port 4444 security-enabled", sawSecure);
        }
        catch (MiniXmlParserException ex) {
            Logger.getLogger(MiniXmlParserTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /*
     * Exercise the parsing of asadmin virtual server, network-listener and port numbers
     * this one tests for ONE listener
     */
    @Test
    public void findOneAdminPort() {
        try {
            MiniXmlParser instance = new MiniXmlParser(adminport, "server");
            List<HostAndPort> addrs = instance.getAdminAddresses();
            assertEquals(1, addrs.size());
            assertEquals(3333, addrs.iterator().next().getPort());

            // clean v2 domain.xml
            if (v2DomainXml != null) {
                instance = new MiniXmlParser(v2DomainXml, "server");
                addrs = instance.getAdminAddresses();
                assertEquals(1, addrs.size());
                assertEquals(4848, addrs.iterator().next().getPort());
            }

            // domain.xml from issue 9127
            instance = new MiniXmlParser(issue9127DomainXml, "server");
            addrs = instance.getAdminAddresses();
            assertEquals(1, addrs.size());
            assertEquals(4848, addrs.iterator().next().getPort());
        }
        catch (MiniXmlParserException ex) {
            Logger.getLogger(MiniXmlParserTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Test that the correct Exception is thrown for a "right-order" xml that has no /domain  element in it
     *
     * @throws MiniXmlParserException
     */
    @Test(expected = MiniXmlParserException.class)
    public void testNoClosingDomainRightOrder() throws MiniXmlParserException {
        new MiniXmlParser(noCloseRightOrder, "server");
    }

    /**
     * Test that the correct Exception is thrown for a "wrong-order" xml that has no /domain  element in it
     *
     * @throws MiniXmlParserException
     */
    @Test(expected = MiniXmlParserException.class)
    public void testNoClosingDomainWrongOrder() throws MiniXmlParserException {
        new MiniXmlParser(noCloseWrongOrder, "server");
    }

    /**
     * Test that not having a domain-name is not fatal
     *
     * @throws MiniXmlParserException
     */
    @Test
    public void testNoDomainName() throws MiniXmlParserException {
        new MiniXmlParser(noDomainName, "server");
    }

    @Test
    public void testOldSchema() throws MiniXmlParserException {
        final MiniXmlParser parser = new MiniXmlParser(
                new File(getClass().getClassLoader().getResource("olddomain.xml").getPath()), "server");
        List<HostAndPort> addrs = parser.getAdminAddresses();
        assertEquals(1, addrs.size());
    }

    @Test
    public void testNoNetworkConfig() throws MiniXmlParserException {
        final MiniXmlParser parser = new MiniXmlParser(
                new File(getClass().getClassLoader().getResource("olddomain.xml").getPath()), "server");
        assert (!parser.hasNetworkConfig());
    }

    @Test
    public void testNetworkConfig() throws MiniXmlParserException {
        final MiniXmlParser parser = new MiniXmlParser(rightOrder, "server");
        assert (parser.hasNetworkConfig());
    }

    @Test
    public void timingTest() {
        try {
            long nanoStart = System.nanoTime();
            new MiniXmlParser(bigDomain, "server");
            long nanoStop = System.nanoTime();
            double d = (double) (nanoStop - nanoStart);
            d *= .001;
            d *= .001;
        }
        catch (MiniXmlParserException ex) {
            Logger.getLogger(MiniXmlParserTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Test
    public void testMonitoringTrue() throws MiniXmlParserException {
        MiniXmlParser instance = new MiniXmlParser(monitoringTrue, "server");
        assertTrue(instance.isMonitoringEnabled());
    }

    @Test
    public void testMonitoringFalse() throws MiniXmlParserException {
        MiniXmlParser instance = new MiniXmlParser(monitoringFalse, "server");
        assertTrue(!instance.isMonitoringEnabled());
    }

    @Test
    public void testMonitoringNone() throws MiniXmlParserException {
        MiniXmlParser instance = new MiniXmlParser(monitoringNone, "server");
        assertTrue(instance.isMonitoringEnabled());
    }

    @Test
    public void testV2DomainXml() throws MiniXmlParserException {
        if(v2DomainXml != null)
            new MiniXmlParser(v2DomainXml, "server");
    }

    private static boolean canAccessInternet() {
        String urlString = "http://www.sun.com/software/appserver/dtds/sun-domain_1_3.dtd";
        try {
            URL url = new URL(urlString);
            URLConnection urlc = url.openConnection();
            urlc.getInputStream().close(); // will throw if no internet!
            return true;
        }
        catch (Exception ex) {
            return true;
        }
    }
}
