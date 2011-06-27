/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2010 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.javaee.services;

import com.sun.appserv.connectors.internal.api.ResourceNamingService;
import org.glassfish.resource.common.ResourceInfo;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.annotations.Inject;
import org.jvnet.hk2.component.Habitat;
import org.glassfish.api.naming.GlassfishNamingManager;
import com.sun.enterprise.config.serverbeans.*;

import javax.naming.NamingException;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Binds proxy objects in the jndi namespace for all the resources and pools defined in the
 * configuration. Those objects will delay the instantiation of the actual resources and pools
 * until code looks them up in the naming manager.
 *
 * @author Jerome Dochez, Jagadish Ramu
 */
@Service
public class ResourcesBinder {

    @Inject
    private GlassfishNamingManager manager;

    @Inject
    private Logger logger;

    @Inject
    private Habitat genericResourceProxy;

    @Inject
    private ResourceNamingService resourceNamingService;

    /**
     * deploy proxy for the resource
     * @param resourceInfo   jndi name with which the resource need to be deployed
     * @param resource config object of the resource
     */
    public void deployResource( ResourceInfo resourceInfo, Resource resource){
        try{
            bindResource(resourceInfo, resource);
        }catch(NamingException ne){
            Object[] params = {resourceInfo, ne};
            logger.log(Level.SEVERE,"resources.resources-binder.bind-resource-failed", params);            
        }
    }

    /**
     * bind proxy for the resource
     * @param resource config object of the resource
     * @param jndiName jndi name with which the resource need to be deployed
     * @throws NamingException
     */
    private void bindResource(ResourceInfo resourceInfo, Resource resource) throws NamingException {
        ResourceProxy proxy = genericResourceProxy.getComponent(ResourceProxy.class);
        proxy.setResource(resource);
        proxy.setResourceInfo(resourceInfo);
        resourceNamingService.publishObject(resourceInfo, proxy, true);
    }
}
