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

import com.sun.logging.LogDomains;

import java.util.logging.Logger;
import javax.enterprise.deploy.shared.ModuleType;
import org.glassfish.deployment.common.DeploymentUtils;
import org.glassfish.deployment.common.ModuleDescriptor;
import org.glassfish.internal.deployment.ExtendedDeploymentContext;
import org.glassfish.loader.util.ASClassLoaderUtil;
import org.glassfish.api.deployment.archive.ReadableArchive;
import org.glassfish.api.deployment.archive.ArchiveType;
import org.glassfish.api.deployment.DeploymentContext;
import java.net.URL;
import java.net.URI;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import com.sun.enterprise.deployment.BundleDescriptor;
import com.sun.enterprise.deployment.Application;
import com.sun.enterprise.deployment.ConnectorDescriptor;
import com.sun.enterprise.config.serverbeans.Applications;
import com.sun.enterprise.deployment.deploy.shared.Util;
import org.glassfish.internal.data.ApplicationInfo;
import org.glassfish.internal.data.ApplicationRegistry;
import org.jvnet.hk2.component.BaseServiceLocator;
import org.glassfish.hk2.Services;
import org.glassfish.internal.api.Globals;
import org.glassfish.hk2.ContractLocator;

/**
 * Utility class for convenienve methods
 *
 * @author  Jerome Dochez
 * @version 
 */
public class DOLUtils {
    
    private static Logger logger=null;
    

    /** no need to creates new DOLUtils */
    private DOLUtils() {
    }

    /**
     * @return a logger to use in the DOL implementation classes
     */
    public static Logger getDefaultLogger() {
        if (logger==null) {
            logger = LogDomains.getLogger(DeploymentUtils.class, LogDomains.DPL_LOGGER);
        }
        return logger;
    }

    public static boolean equals(Object a, Object b) {
        return ((a == null && b == null) ||
                (a != null && a.equals(b)));
    }

    public static List<URI> getLibraryJarURIs(BundleDescriptor bundleDesc, ReadableArchive archive) throws Exception {
        List<URL> libraryURLs = new ArrayList<URL>();
        List<URI> libraryURIs = new ArrayList<URI>();

        // add libraries referenced through manifest
        libraryURLs.addAll(DeploymentUtils.getManifestLibraries(archive));

        ReadableArchive parentArchive = archive.getParentArchive();

        if (parentArchive == null || bundleDesc == null) {
            // ear level or standalone module
            for (URL url : libraryURLs) {
                libraryURIs.add(Util.toURI(url));
            }
            return libraryURIs;
        }

        File appRoot = new File(parentArchive.getURI());

        ModuleDescriptor moduleDesc = ((BundleDescriptor)bundleDesc).getModuleDescriptor();
        Application app = ((BundleDescriptor)moduleDesc.getDescriptor()).getApplication();

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

    public static boolean isRAConnectionFactory(BaseServiceLocator habitat, 
        String type, Application thisApp) {
        // first check if this is a connection factory defined in a resource
        // adapter in this application
        if (isRAConnectionFactory(type, thisApp)) {
            return true;
        }

        // then check if this is a connection factory defined in a standalone 
        // resource adapter
        Applications applications = habitat.getComponent(Applications.class);
        if (applications != null) {
            List<com.sun.enterprise.config.serverbeans.Application> raApps = applications.getApplicationsWithSnifferType(com.sun.enterprise.config.serverbeans.Application.CONNECTOR_SNIFFER_TYPE, true);
            ApplicationRegistry appRegistry = habitat.getComponent(ApplicationRegistry.class);
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
        final Services services = Globals.getDefaultServices();
        ArchiveType result = null;
        // This method is called without HK2 being setup when dol unit tests are run, so protect against NPE.
        if(services != null) {
            final ContractLocator<ArchiveType> provider =
                    services.forContract(ArchiveType.class).named(moduleType);
            if (provider!=null) {
                result = provider.get();
            }
        }
        return result;
    }
}
