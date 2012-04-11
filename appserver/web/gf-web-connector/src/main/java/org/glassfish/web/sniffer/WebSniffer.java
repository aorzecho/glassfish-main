/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2006-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.web.sniffer;

import com.sun.enterprise.module.ModulesRegistry;
import org.glassfish.api.container.Sniffer;
import org.glassfish.api.deployment.archive.ReadableArchive;
import org.glassfish.deployment.common.DeploymentUtils;
import org.glassfish.internal.deployment.GenericSniffer;
import javax.inject.Inject;
import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.Singleton;

import java.util.ArrayList;
import java.util.List;



/**
 * Implementation of the Sniffer for the web container.
 * 
 * @author Jerome Dochez
 */
@Service(name="web")
@Scoped(Singleton.class)
public class WebSniffer  extends GenericSniffer {

    public WebSniffer() {
        super("web", "WEB-INF/web.xml", null);
    }

    @Override
    public String[] getURLPatterns() {
        // anything finishing with jsp or jspx
        return new String[] { "*.jsp", "*.jspx" };
    }

    /**
     * Returns true if the passed file or directory is recognized by this
     * instance.
     *
     * @param location the file or directory to explore 
     * @param loader class loader for this application
     * @return true if this sniffer handles this application type
     */
    public boolean handles(ReadableArchive location, ClassLoader loader) {
        return DeploymentUtils.isWebArchive(location);
    }

    private static final String[] containers = { "com.sun.enterprise.web.WebContainer" };

    public String[] getContainersNames() {
        return containers;
    }

    /**
     * @return whether this sniffer should be visible to user
     *
     */
    public boolean isUserVisible() {
        return true;
    }
    
    private static final List<String> deploymentConfigurationPaths = 
            initDeploymentConfigurationPaths();
    
    private static List<String> initDeploymentConfigurationPaths() {
        final List<String> result = new ArrayList<String>();
        result.add("WEB-INF/web.xml");
        result.add("WEB-INF/sun-web.xml");
        result.add("WEB-INF/glassfish-web.xml");
        result.add("WEB-INF/weblogic.xml");
        return result;
    }
    
    /**
     * Returns the web-oriented descriptor paths that might exist in a web
     * app.
     * 
     * @return list of the deployment descriptor paths
     */
    @Override
    protected List<String> getDeploymentConfigurationPaths() {
        return deploymentConfigurationPaths;
    }

    /**
     * @return the set of the sniffers that should not co-exist for the
     * same module. For example, ejb and appclient sniffers should not
     * be returned in the sniffer list for a certain module.
     * This method will be used to validate and filter the retrieved sniffer
     * lists for a certain module
     *
     */
    public String[] getIncompatibleSnifferTypes() {
        return new String[] {"connector"};
    }

    // TODO(Sahoo): Ideally we should have separate sniffer for JSP, but since WebSniffer is already
    // handling JSPs, we must make sure that all JSP related modules get installed by WebSniffer as well.
    // javax.el is needed because org.apache.jasper.runtime.JspApplicationContextImpl.getExpressionFactory
    // does ExpressionFactory.newInstance("com.sun.el.ExpressionFactoryImpl") which looks up the class
    // using TCL. The loadClass will fail unless javax.el.jar, which contains this class, is installed.
    private String[] containerModuleNames = {"org.glassfish.web.glue",
            "org.glassfish.web.javax.servlet.jsp",
            "org.glassfish.web.javax.el"
    };

    @Override
    protected String[] getContainerModuleNames() {
        return containerModuleNames;
    }
}
