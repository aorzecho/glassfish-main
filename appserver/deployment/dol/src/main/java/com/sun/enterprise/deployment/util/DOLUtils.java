/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.deployment.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.deploy.shared.ModuleType;

import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.api.container.Sniffer;
import org.glassfish.api.deployment.DeployCommandParameters;
import org.glassfish.api.deployment.DeploymentContext;
import org.glassfish.api.deployment.archive.ArchiveHandler;
import org.glassfish.api.deployment.archive.ArchiveType;
import org.glassfish.api.deployment.archive.ReadableArchive;
import org.glassfish.deployment.common.DeploymentContextImpl;
import org.glassfish.deployment.common.DeploymentProperties;
import org.glassfish.deployment.common.DeploymentUtils;
import org.glassfish.deployment.common.ModuleDescriptor;
import org.glassfish.deployment.common.RootDeploymentDescriptor;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.classmodel.reflect.Types;
import org.glassfish.internal.api.Globals;
import org.glassfish.internal.data.ApplicationInfo;
import org.glassfish.internal.data.ApplicationRegistry;
import org.glassfish.internal.deployment.ExtendedDeploymentContext;
import org.glassfish.internal.deployment.SnifferManager;
import org.glassfish.loader.util.ASClassLoaderUtil;
import org.glassfish.hk2.api.ServiceLocator;
import org.xml.sax.SAXParseException;

import com.sun.enterprise.config.serverbeans.Applications;
import com.sun.enterprise.deployment.Application;
import com.sun.enterprise.deployment.BundleDescriptor;
import com.sun.enterprise.deployment.ConnectorDescriptor;
import com.sun.enterprise.deployment.archivist.Archivist;
import com.sun.enterprise.deployment.archivist.ArchivistFactory;
import com.sun.enterprise.deployment.deploy.shared.Util;
import com.sun.enterprise.deployment.io.ConfigurationDeploymentDescriptorFile;
import com.sun.enterprise.deployment.io.ConfigurationDeploymentDescriptorFileFor;
import com.sun.enterprise.deployment.io.DescriptorConstants;
import com.sun.enterprise.deployment.node.XMLElement;
import com.sun.enterprise.deployment.xml.TagNames;
import com.sun.enterprise.util.LocalStringManagerImpl;

import org.glassfish.logging.annotation.LogMessageInfo;
import org.glassfish.logging.annotation.LoggerInfo;
import org.glassfish.logging.annotation.LogMessagesResourceBundle;

/**
 * Utility class for convenienve methods
 *
 * @author  Jerome Dochez
 * @version 
 */
public class DOLUtils {
    
    public final static String W3C_XML_SCHEMA = "http://www.w3.org/2001/XMLSchema";
    public final static String SCHEMA_LOCATION_TAG = "xsi:schemaLocation";

    private static LocalStringManagerImpl localStrings =
            new LocalStringManagerImpl(DOLUtils.class);

    @LogMessagesResourceBundle
    private static final String SHARED_LOGMESSAGE_RESOURCE = "org.glassfish.deployment.LogMessages";

    @LoggerInfo(subsystem = "DEPLOYMENT", description="Deployment System Logger", publish=true)
    private static final String DEPLOYMENT_LOGGER = "javax.enterprise.system.tools.deployment.dol";

    public static final Logger deplLogger =
        Logger.getLogger(DEPLOYMENT_LOGGER, SHARED_LOGMESSAGE_RESOURCE);

    @LogMessageInfo(message = "Ignore {0} in archive {1}, as WLS counterpart runtime xml {2} is present in the same archive.", level="WARNING")
      private static final String COUNTERPART_CONFIGDD_EXISTS = "AS-DEPLOYMENT-00001";

    @LogMessageInfo(message = "Exception caught:  {0}.", level="WARNING")
      private static final String EXCEPTION_CAUGHT = "AS-DEPLOYMENT-00002";

    @LogMessageInfo(message = "{0} module [{1}] contains characteristics of other module type: {2}.", level="WARNING")
      private static final String INCOMPATIBLE_TYPE = "AS-DEPLOYMENT-00003";

    // The system property to control the precedence between GF DD
    // and WLS DD when they are both present. When this property is 
    // set to true, GF DD will have higher precedence over WLS DD.
    private static final String GFDD_OVER_WLSDD = "gfdd.over.wlsdd";

    // The system property to control whether we should just ignore 
    // WLS DD. When this property is set to true, WLS DD will be ignored.
    private static final String IGNORE_WLSDD = "ignore.wlsdd";

    /** no need to creates new DOLUtils */
    private DOLUtils() {
    }

    /**
     * @return a logger to use in the DOL implementation classes
     */
    public static synchronized Logger getDefaultLogger() {
        return deplLogger;
    }

    public static boolean equals(Object a, Object b) {
        return ((a == null && b == null) ||
                (a != null && a.equals(b)));
    }

    public static List<URI> getLibraryJarURIs(BundleDescriptor bundleDesc, ReadableArchive archive) throws Exception {
        if (bundleDesc == null) {
            return Collections.emptyList();
        }
        ModuleDescriptor moduleDesc = ((BundleDescriptor)bundleDesc).getModuleDescriptor();
        Application app = ((BundleDescriptor)moduleDesc.getDescriptor()).getApplication();
        return getLibraryJarURIs(app, archive);
    }

    public static List<URI> getLibraryJarURIs(Application app, ReadableArchive archive) throws Exception {
        List<URL> libraryURLs = new ArrayList<URL>();
        List<URI> libraryURIs = new ArrayList<URI>();

        // add libraries referenced through manifest
        libraryURLs.addAll(DeploymentUtils.getManifestLibraries(archive));

        ReadableArchive parentArchive = archive.getParentArchive();

        if (parentArchive == null) {
            return Collections.emptyList();
        }

        File appRoot = new File(parentArchive.getURI());

        // add libraries jars inside application lib directory
        libraryURLs.addAll(ASClassLoaderUtil.getAppLibDirLibrariesAsList(
            appRoot, app.getLibraryDirectory(), null));

        for (URL url : libraryURLs) {
            libraryURIs.add(Util.toURI(url));
        }
        return libraryURIs;
    } 

   public static BundleDescriptor getCurrentBundleForContext(
       DeploymentContext context) {
       ExtendedDeploymentContext ctx = (ExtendedDeploymentContext)context;
       Application application = context.getModuleMetaData(Application.class);
       if (application == null) return null; // this can happen for non-JavaEE type deployment. e.g., issue 15869
       if (ctx.getParentContext() == null) {
           if (application.isVirtual()) {
               // standalone module
               return application.getStandaloneBundleDescriptor();
           } else {
               // top level 
               return application;
           }
       } else {
           // a sub module of ear
           return application.getModuleByUri(ctx.getModuleUri());
       }
   }

    public static boolean isRAConnectionFactory(ServiceLocator habitat, 
        String type, Application thisApp) {
        // first check if this is a connection factory defined in a resource
        // adapter in this application
        if (isRAConnectionFactory(type, thisApp)) {
            return true;
        }

        // then check if this is a connection factory defined in a standalone 
        // resource adapter
        Applications applications = habitat.getService(Applications.class);
        if (applications != null) {
            List<com.sun.enterprise.config.serverbeans.Application> raApps = applications.getApplicationsWithSnifferType(com.sun.enterprise.config.serverbeans.ServerTags.CONNECTOR, true);
            ApplicationRegistry appRegistry = habitat.getService(ApplicationRegistry.class);
            for (com.sun.enterprise.config.serverbeans.Application raApp : raApps) {
                ApplicationInfo appInfo = appRegistry.get(raApp.getName());
                if (isRAConnectionFactory(type, appInfo.getMetaData(Application.class))) {   
                    return true;
                }   
            }
        }
        return false; 
    }

    private static boolean isRAConnectionFactory(String type, Application app) {
        if (app == null) {
            return false;
        }
        for (ConnectorDescriptor cd : app.getBundleDescriptors(ConnectorDescriptor.class)) {
            if (cd.getConnectionDefinitionByCFType(type) != null) {
                return true;
            }
        }
        return false;
    }

    public static ArchiveType earType() {
        return getModuleType(ModuleType.EAR.toString());
    }

    public static ArchiveType ejbType() {
        return getModuleType(ModuleType.EJB.toString());
    }

    public static ArchiveType carType() {
        return getModuleType(ModuleType.CAR.toString());
    }

    public static ArchiveType warType() {
        return getModuleType(ModuleType.WAR.toString());
    }

    public static ArchiveType rarType() {
        return getModuleType(ModuleType.RAR.toString());
    }

    /**
     * Utility method to retrieve a {@link ArchiveType} from a stringified module type.
     * Since {@link ArchiveType} is an extensible abstraction and implementations are plugged in via HK2 service
     * registry, this method returns null if HK2 service registry is not setup.
     *
     * If null is passed to this method, it returns null instead of returning an arbitrary ArchiveType or throwing
     * an exception.
     *
     * @param moduleType String equivalent of the module type being looked up. null is allowed.
     * @return the corresponding ArchiveType, null if no such module type exists or HK2 Service registry is not set up
     */
    public static ArchiveType getModuleType(String moduleType) {
        if (moduleType == null) {
            return null;
        }
        final ServiceLocator services = Globals.getDefaultHabitat();
        ArchiveType result = null;
        // This method is called without HK2 being setup when dol unit tests are run, so protect against NPE.
        if(services != null) {
            result = services.getService(ArchiveType.class, moduleType);
        }
        return result;
    }

    // returns true if GF DD should have higher precedence over 
    // WLS DD when both present in the same archive
    public static boolean isGFDDOverWLSDD() {
        return Boolean.valueOf(System.getProperty(GFDD_OVER_WLSDD));
    }

    // returns true if we should ignore WLS DD in the archive
    public static boolean isIgnoreWLSDD() {
        return Boolean.valueOf(System.getProperty(IGNORE_WLSDD)); 
    }

    // process the list of the configuration files, and return the sorted
    // configuration file with precedence from high to low
    // this list does not take consideration of what runtime files are 
    // present in the current archive
    private static List<ConfigurationDeploymentDescriptorFile> sortConfigurationDDFiles(List<ConfigurationDeploymentDescriptorFile> ddFiles, ArchiveType archiveType, ReadableArchive archive) {
        ConfigurationDeploymentDescriptorFile wlsConfDD = null;
        ConfigurationDeploymentDescriptorFile gfConfDD = null;
        ConfigurationDeploymentDescriptorFile sunConfDD = null;
        for (ConfigurationDeploymentDescriptorFile ddFile : ddFiles) {
            ddFile.setArchiveType(archiveType);
            String ddPath = ddFile.getDeploymentDescriptorPath();
            if (ddPath.indexOf(DescriptorConstants.WLS) != -1) {
                wlsConfDD = ddFile;
            } else if (ddPath.indexOf(DescriptorConstants.GF_PREFIX) != -1) {
                gfConfDD = ddFile;
            } else if (ddPath.indexOf(DescriptorConstants.S1AS_PREFIX) != -1) {
                sunConfDD = ddFile;
            }
        }
        List<ConfigurationDeploymentDescriptorFile> sortedConfDDFiles = new ArrayList<ConfigurationDeploymentDescriptorFile>(); 

        // if there is external runtime alternate deployment descriptor 
        // specified, just use that
        File runtimeAltDDFile = archive.getArchiveMetaData(
            DeploymentProperties.RUNTIME_ALT_DD, File.class);
        if (runtimeAltDDFile != null && runtimeAltDDFile.exists() && runtimeAltDDFile.isFile()) {
            String runtimeAltDDPath = runtimeAltDDFile.getPath();
            validateRuntimeAltDDPath(runtimeAltDDPath);
            if (runtimeAltDDPath.indexOf(
                DescriptorConstants.GF_PREFIX) != -1 && gfConfDD != null) {
                sortedConfDDFiles.add(gfConfDD);
                return sortedConfDDFiles;
            }
            if (runtimeAltDDPath.indexOf(
                DescriptorConstants.WLS) != -1 && wlsConfDD != null) {
                sortedConfDDFiles.add(wlsConfDD);
                return sortedConfDDFiles;
            }
        }

        // sort the deployment descriptor files by precedence order 
        // when they are present in the same archive

        if (Boolean.valueOf(System.getProperty(GFDD_OVER_WLSDD))) {
            // if this property set, it means we need to make GF deployment
            // descriptors higher precedence 
            if (gfConfDD != null) {
                sortedConfDDFiles.add(gfConfDD);
            }
            if (wlsConfDD != null) { 
                sortedConfDDFiles.add(wlsConfDD);
            }
        } else if (Boolean.valueOf(System.getProperty(IGNORE_WLSDD))) {
            // if this property set, it means we need to ignore 
            // WLS deployment descriptors 
            if (gfConfDD != null) {
                sortedConfDDFiles.add(gfConfDD);
            }
        } else  {
            // the default will be WLS DD has higher precedence
            if (wlsConfDD != null) { 
                sortedConfDDFiles.add(wlsConfDD);
            }
            if (gfConfDD != null) {
                sortedConfDDFiles.add(gfConfDD);
            }
        }

        if (sunConfDD != null) {
            sortedConfDDFiles.add(sunConfDD);
        }

        return sortedConfDDFiles;
    }

    public static void validateRuntimeAltDDPath(String runtimeAltDDPath) {
        if (runtimeAltDDPath.indexOf(DescriptorConstants.GF_PREFIX) == -1 && 
            runtimeAltDDPath.indexOf(DescriptorConstants.WLS) == -1) {
            String msg = localStrings.getLocalString(
                "enterprise.deployment.util.unsupportedruntimealtdd", "Unsupported external runtime alternate deployment descriptor [{0}].", new Object[] {runtimeAltDDPath});
            throw new IllegalArgumentException(msg);
        }
    }

    // process the list of the configuration files, and return the sorted
    // configuration file with precedence from high to low
    // this list takes consideration of what runtime files are 
    // present in the current archive
    public static List<ConfigurationDeploymentDescriptorFile> processConfigurationDDFiles(List<ConfigurationDeploymentDescriptorFile> ddFiles, ReadableArchive archive, ArchiveType archiveType) throws IOException {
        File runtimeAltDDFile = archive.getArchiveMetaData(
            DeploymentProperties.RUNTIME_ALT_DD, File.class);
        if (runtimeAltDDFile != null && runtimeAltDDFile.exists() && runtimeAltDDFile.isFile()) {
            // if there are external runtime alternate deployment descriptor 
            // specified, the config DD files are already processed
            return sortConfigurationDDFiles(ddFiles, archiveType, archive);
        }
        List<ConfigurationDeploymentDescriptorFile> processedConfDDFiles = new ArrayList<ConfigurationDeploymentDescriptorFile>();
        for (ConfigurationDeploymentDescriptorFile ddFile : sortConfigurationDDFiles(ddFiles, archiveType, archive)) {
            if (archive.exists(ddFile.getDeploymentDescriptorPath())) {
                processedConfDDFiles.add(ddFile);
            }
        }
        return processedConfDDFiles;
    }

    // read alternative runtime descriptor if there is an alternative runtime 
    // DD packaged inside the archive
    public static void readAlternativeRuntimeDescriptor(ReadableArchive appArchive, ReadableArchive embeddedArchive, Archivist archivist, BundleDescriptor descriptor, String altDDPath) throws IOException, SAXParseException {
        String altRuntimeDDPath = null;
        ConfigurationDeploymentDescriptorFile confDD = null;
        for (ConfigurationDeploymentDescriptorFile ddFile : sortConfigurationDDFiles(archivist.getConfigurationDDFiles(), archivist.getModuleType(), embeddedArchive)) {
            String ddPath = ddFile.getDeploymentDescriptorPath();
            if (ddPath.indexOf(DescriptorConstants.WLS) != -1 && 
                appArchive.exists(DescriptorConstants.WLS + altDDPath)) {
                // TODO: need to revisit this for WLS alt-dd pattern
                confDD = ddFile;
                altRuntimeDDPath = DescriptorConstants.WLS + altDDPath;
            } else if (ddPath.indexOf(DescriptorConstants.GF_PREFIX) != -1 &&
                appArchive.exists(DescriptorConstants.GF_PREFIX + altDDPath)) {
                confDD = ddFile;
                altRuntimeDDPath = DescriptorConstants.GF_PREFIX + altDDPath;
            } else if (ddPath.indexOf(DescriptorConstants.S1AS_PREFIX) != -1 
                && appArchive.exists(DescriptorConstants.S1AS_PREFIX + altDDPath)){
                confDD = ddFile;
                altRuntimeDDPath = DescriptorConstants.S1AS_PREFIX + altDDPath;
            }
        }

        if (confDD != null && altRuntimeDDPath != null) {
            // found an alternative runtime DD file
            InputStream is = appArchive.getEntry(altRuntimeDDPath); 
            confDD.setXMLValidation(
                archivist.getRuntimeXMLValidation());
            confDD.setXMLValidationLevel(
                archivist.getRuntimeXMLValidationLevel());
            if (appArchive.getURI()!=null) {
                confDD.setErrorReportingString(
                    appArchive.getURI().getSchemeSpecificPart());
            }

            confDD.read(descriptor, is);
            is.close();
            archivist.postRuntimeDDsRead(descriptor, embeddedArchive);
        } else {
            archivist.readRuntimeDeploymentDescriptor(embeddedArchive,descriptor);
        }
    }

    /**
     * Read the runtime deployment descriptors (can contained in one or
     * many file) set the corresponding information in the passed descriptor.
     * By default, the runtime deployment descriptors are all contained in
     * the xml file characterized with the path returned by
     *
     * @param confDDFiles the sorted configuration files for this archive
     * @param archive the archive
     * @param descriptor the initialized deployment descriptor
     * @param archivist the main archivist
     * @param warnIfMultipleDDs whether to log warnings if both the GlassFish and the legacy Sun descriptors are present
     */
    public static void readRuntimeDeploymentDescriptor(List<ConfigurationDeploymentDescriptorFile> confDDFiles, ReadableArchive archive, RootDeploymentDescriptor descriptor, Archivist main, final boolean warnIfMultipleDDs) throws IOException, SAXParseException {
        if (confDDFiles == null || confDDFiles.isEmpty()) {
            return;
        }
        ConfigurationDeploymentDescriptorFile confDD = confDDFiles.get(0);
        InputStream is = null;
        try {
            File runtimeAltDDFile = archive.getArchiveMetaData(
                DeploymentProperties.RUNTIME_ALT_DD, File.class);
            if (runtimeAltDDFile != null && runtimeAltDDFile.exists() && runtimeAltDDFile.isFile()) {
                is = new FileInputStream(runtimeAltDDFile);
            } else {
                is = archive.getEntry(confDD.getDeploymentDescriptorPath());
            }
            for (int i = 1; i < confDDFiles.size(); i++) {
                if (warnIfMultipleDDs) {
                    deplLogger.log(Level.WARNING,
                                   COUNTERPART_CONFIGDD_EXISTS,
                                   new Object[] {
                                     confDDFiles.get(i).getDeploymentDescriptorPath(),
                                     archive.getURI().getSchemeSpecificPart(),
                                     confDD.getDeploymentDescriptorPath()});
                }
            }
            confDD.setErrorReportingString(archive.getURI().getSchemeSpecificPart());
            if (confDD.isValidating()) {
              confDD.setXMLValidation(main.getRuntimeXMLValidation());
              confDD.setXMLValidationLevel(main.getRuntimeXMLValidationLevel());
            } else {
              confDD.setXMLValidation(false);
            }
            confDD.read(descriptor, is);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ioe) {
                }
            }
        }
    }


    public static void setExtensionArchivistForSubArchivist(ServiceLocator habitat, ReadableArchive archive, ModuleDescriptor md, Application app, Archivist subArchivist) {
        try {
            Collection<Sniffer> sniffers = getSniffersForModule(habitat, archive, md, app);
            ArchivistFactory archivistFactory = habitat.getService(ArchivistFactory.class);
            subArchivist.setExtensionArchivists(archivistFactory.getExtensionsArchivists(sniffers, subArchivist.getModuleType()));
        } catch (Exception e) {
            deplLogger.log(Level.WARNING,
                           EXCEPTION_CAUGHT,
                           new Object[] { e.getMessage(), e });
        }
    }

    // get sniffer list for sub modules of an ear application
    private static Collection<Sniffer> getSniffersForModule(ServiceLocator habitat, ReadableArchive archive, ModuleDescriptor md, Application app) throws Exception {
        ArchiveHandler handler = habitat.getService(ArchiveHandler.class, md.getModuleType().toString());
        SnifferManager snifferManager = habitat.getService(SnifferManager.class);
        List<URI> classPathURIs = handler.getClassPathURIs(archive);
        classPathURIs.addAll(getLibraryJarURIs(app, archive));
        Types types = archive.getParentArchive().getExtraData(Types.class);
        DeployCommandParameters parameters = new DeployCommandParameters(new File(archive.getURI()));
        ExtendedDeploymentContext context = new DeploymentContextImpl(null, archive, parameters, habitat.<ServerEnvironment>getService(ServerEnvironment.class));
        context.setArchiveHandler(handler);
        Collection<Sniffer> sniffers = snifferManager.getSniffers(context, classPathURIs, types);
        String type = getTypeFromModuleType(md.getModuleType());
        Sniffer mainSniffer = null;
        for (Sniffer sniffer : sniffers) {
            if (sniffer.getModuleType().equals(type)) {
                mainSniffer = sniffer;
            }
        }

        // if the sub module does not show characteristics of certain module
        // type, we should still use the application.xml defined module type
        // to add the appropriate sniffer
        if (mainSniffer == null) {
            mainSniffer = snifferManager.getSniffer(type);
            sniffers.add(mainSniffer);
        }

        String [] incompatibleTypes = mainSniffer.getIncompatibleSnifferTypes();
        List<String> allIncompatTypes = addAdditionalIncompatTypes(mainSniffer, incompatibleTypes);

        List<Sniffer> sniffersToRemove = new ArrayList<Sniffer>();
        for (Sniffer sniffer : sniffers) {
            for (String incompatType : allIncompatTypes) {
                if (sniffer.getModuleType().equals(incompatType)) {
                  deplLogger.log(Level.WARNING,
                                 INCOMPATIBLE_TYPE,
                                 new Object[] { type,
                                                md.getArchiveUri(),
                                                incompatType });

                    sniffersToRemove.add(sniffer);
                }
            }
        }

        sniffers.removeAll(sniffersToRemove);

        // store the module sniffer information so we don't need to 
        // recalculate them later
        Hashtable sniffersTable = archive.getParentArchive().getExtraData(Hashtable.class);
        if (sniffersTable == null) {
            sniffersTable = new Hashtable<String, Collection<Sniffer>>();
            archive.getParentArchive().setExtraData(Hashtable.class, sniffersTable);
        }
        sniffersTable.put(md.getArchiveUri(), sniffers);

        return sniffers;
    }

    private static String getTypeFromModuleType(ArchiveType moduleType) {
        if (moduleType.equals(DOLUtils.warType())) {
            return "web";
        } else if (moduleType.equals(DOLUtils.ejbType())) {
            return "ejb";
        } else if (moduleType.equals(DOLUtils.carType())) {
            return "appclient";
        } else if (moduleType.equals(DOLUtils.rarType())) {
            return "connector";
        }
        return null;
    }

    // this is to add additional incompatible sniffers at ear level where
    // we have information to determine what is the main sniffer
    private static List<String> addAdditionalIncompatTypes(Sniffer mainSniffer, String[] incompatTypes) {
        List<String> allIncompatTypes = new ArrayList<String>();
        for (String incompatType : incompatTypes) {
            allIncompatTypes.add(incompatType);
        }
        if (mainSniffer.getModuleType().equals("appclient")) {
            allIncompatTypes.add("ejb");
        } else if (mainSniffer.getModuleType().equals("ejb")) {
            allIncompatTypes.add("appclient");
        }
        return allIncompatTypes;
    }

    public static List<ConfigurationDeploymentDescriptorFile> getConfigurationDeploymentDescriptorFiles(ServiceLocator habitat, String containerType) {
        List<ConfigurationDeploymentDescriptorFile> confDDFiles = new ArrayList<ConfigurationDeploymentDescriptorFile>();
        for (ServiceHandle<?> serviceHandle : habitat.getAllServiceHandles(ConfigurationDeploymentDescriptorFileFor.class)) {
            ActiveDescriptor<?> descriptor = serviceHandle.getActiveDescriptor();
            String indexedType = descriptor.getMetadata().get(ConfigurationDeploymentDescriptorFileFor.DESCRIPTOR_FOR).get(0);
            if(indexedType.equals(containerType)) {
                ConfigurationDeploymentDescriptorFile confDD = (ConfigurationDeploymentDescriptorFile) serviceHandle.getService();
                confDDFiles.add(confDD);
            }
        }
        return confDDFiles;
    }

    /**
     * receives notiification of the value for a particular tag
     * 
     * @param element the xml element
     * @param value it's associated value
     */
    public static boolean setElementValue(XMLElement element,
                                          String value,
                                          Object o) {    
        if (SCHEMA_LOCATION_TAG.equals(element.getCompleteName())) {
            // we need to keep all the non j2ee/javaee schemaLocation tags
            StringTokenizer st = new StringTokenizer(value);
            StringBuffer sb = new StringBuffer();
            while (st.hasMoreElements()) {
                String namespace = (String) st.nextElement();
		String schema;
		if (st.hasMoreElements()) {
		    schema = (String) st.nextElement();
		} else {
		    schema = namespace;
		    namespace = TagNames.JAVAEE_NAMESPACE;
		}
                if (namespace.equals(TagNames.J2EE_NAMESPACE)) 
                    continue;
                if (namespace.equals(TagNames.JAVAEE_NAMESPACE)) 
                    continue;
                if (namespace.equals(W3C_XML_SCHEMA)) 
                    continue;
                sb.append(namespace);
                sb.append(" ");
                sb.append(schema);
            }
            String clientSchemaLocation = sb.toString();
            if (clientSchemaLocation!=null && clientSchemaLocation.length()!=0) {
                if (o instanceof RootDeploymentDescriptor) {
                    ((RootDeploymentDescriptor) o).setSchemaLocation(clientSchemaLocation);
                }
            }
            return true;
        } else if (element.getQName().equals(TagNames.METADATA_COMPLETE)) {
            if (o instanceof BundleDescriptor) {
                ((BundleDescriptor) o).setFullAttribute(value);
            }
            return true;
        }
        return false;
    }

  /*
   * Returns a list of the proprietary schema namespaces
   */
  public static List<String> getProprietarySchemaNamespaces() {
    ArrayList<String> ns = new ArrayList<String>();
    ns.add(DescriptorConstants.WLS_SCHEMA_NAMESPACE_BEA);
    ns.add(DescriptorConstants.WLS_SCHEMA_NAMESPACE_ORACLE);
    return ns;
  }

  /*
   * Returns a list of the proprietary dtd system IDs
   */
  public static List<String> getProprietaryDTDStart() {
    ArrayList<String> ns = new ArrayList<String>();
    ns.add(DescriptorConstants.WLS_DTD_SYSTEM_ID_BEA);
    return ns;
  }
}
