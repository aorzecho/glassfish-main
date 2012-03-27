/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.paas.tenantmanager.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.glassfish.paas.tenantmanager.api.TenantConfigService;
import org.glassfish.paas.tenantmanager.api.TenantManager;
import org.glassfish.paas.tenantmanager.config.Tenant;
import org.glassfish.paas.tenantmanager.config.TenantAdmin;
import org.glassfish.paas.tenantmanager.config.TenantManagerConfig;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.SingleConfigCode;
import org.jvnet.hk2.config.TransactionFailure;

/**
 * Default implementation for {@link TenantManager}.
 * 
 * @author Andriy Zhdanov
 * 
 */
@Service
public class TenantManagerImpl implements TenantManager {

    /**
     * {@inheritDoc}
     */
    @Override
    public Tenant create(final String name, final String adminUserName) {
        // TODO: assert not exists?
        String dir = "";
        try {
            dir = new File(config.getFileStore().toURI()).getAbsolutePath() + "/" + name;
        } catch (URISyntaxException e1) {
            // can not happen
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        String filePath =  dir + "/tenant.xml";
        try {
            boolean created = new File(dir).mkdirs();
            // TODO: i18n
            logger.fine("Tenant dir " + dir + " was " + (created ? "" : "not ") + "created");
            // TODO: better assert created?
            File file = new File(filePath);
            created = file.createNewFile();
            logger.fine("Tenant file " + file + " was " + (created ? "" : "not ") + "created");
            // TODO: better assert created?
            Writer writer = new FileWriter(file);
            writer.write("<tenant name='" + name + "'/>");
            writer.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        String bak = null;
        try {
            bak = tenantConfigService.getCurrentTenant();
        } catch (IllegalArgumentException e) {
            // ignore "No current tenatn set"
        }
        tenantConfigService.setCurrentTenant(name);
        Tenant tenant = tenantConfigService.get(Tenant.class);
        tenantConfigService.setCurrentTenant(bak);
        
        try {
            ConfigSupport.apply(new SingleConfigCode<Tenant>() {
                @Override
                public Object run(Tenant tenant) throws TransactionFailure {
                    TenantAdmin tenantAdmin = tenant.createChild(TenantAdmin.class);
                    tenantAdmin.setName(adminUserName);
                    tenant.setTenantAdmin(tenantAdmin);
                    return tenant;
                }
                
            }, tenant);
        } catch (TransactionFailure e) {
            // TODO Auto-generated catch block
            e.printStackTrace();            
        }
        
        // TODO: add default admin adminUserName
        return tenant;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(String name) {
        // TODO Auto-generated method stub
    }
    
    // TODO: obtain from server-config
    @Inject
    private TenantManagerConfig config;
    
    @Inject
    private TenantConfigService tenantConfigService;

    @Inject
    private Logger logger;
}
