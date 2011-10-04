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
package com.sun.enterprise.security.admin.cli;

import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.SecureAdmin;
import com.sun.enterprise.config.serverbeans.SecureAdminHelper;
import com.sun.enterprise.config.serverbeans.SecureAdminPrincipal;
import com.sun.enterprise.security.admin.cli.SecureAdminCommand.ConfigLevelContext;
import com.sun.enterprise.security.admin.cli.SecureAdminCommand.TopLevelContext;
import com.sun.enterprise.security.admin.cli.SecureAdminCommand.Work;
import com.sun.grizzly.config.dom.Protocol;
import java.io.IOException;
import java.security.KeyStoreException;
import java.util.Iterator;
import java.util.UUID;
import org.glassfish.api.Startup;
import org.jvnet.hk2.annotations.Inject;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.component.PostConstruct;
import org.jvnet.hk2.config.Transaction;
import org.jvnet.hk2.config.TransactionFailure;
/**
 *
 * Starting in GlassFish 3.1.2, the DAS uses SSL to send admin requests to
 * instances, whether the user has enabled secure admin or not.  For this to
 * work correctly when upgrading from earlier releases, there are some changes 
 * to the configuration that must be in place.  This start-up service makes
 * sure that the config is correct as quickly as possible to avoid degrading
 * start-up performance.
 * <p>
 * For 3.1.2 and later the configuration needs to include:
 * <pre>
 * {@code
 * <secure-admin special-admin-indicator="xxx">
 *   at least one <secure-admin-principal> element; if none, supply these defaults:
 * 
 *   <secure-admin-principal dn="dn-for-DAS"/>
 *   <secure-admin-principal dn="dn-for-instances"/>
 * }
 * </pre>
 * 
 * Further, the sec-admin-listener set-up needs to be added (if not already there)
 * for the non-DAS configurations.  Note that the work to configure the
 * listeners and related protocols are already implemented by SecureAdminCommand,
 * so this class delegates much of its work to that logic.
 * 
 * @author Tim Quinn
 */
@Service
public class SecureAdminStartupCheck implements Startup, PostConstruct {

    @Inject
    private Domain domain;
    
    @Inject
    private SecureAdminHelper secureAdminHelper;

    @Inject
    private Habitat habitat;
    
    private SecureAdmin secureAdmin = null;
    private boolean secureAdminWasCreated = false;
    
    private Transaction t = null;
    
    private TopLevelContext topLevelContext = null;
    

    @Override
    public void postConstruct() {
        try {
            ensureSecureAdminReady();
            for (Config c : domain.getConfigs().getConfig()) {
                if ( ! c.getName().equals(SecureAdminCommand.DAS_CONFIG_NAME)) {
                    if (!ensureConfigReady(c)) {
                        break;
                    }
                }
            }
            if (t != null) { 
                t.commit();
            }
            if (secureAdminWasCreated) {
                habitat.addComponent("secure-admin", secureAdmin);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
    }
    
    private void ensureSecureAdminReady() throws TransactionFailure, IOException, KeyStoreException {
        if (secureAdmin().getSpecialAdminIndicator().isEmpty()) {
            /*
             * Set the indicator to a unique value so we can distinguish
             * one domain from another.
             */
            topLevelContext().writableSecureAdmin().setSpecialAdminIndicator(specialAdminIndicator());
        }
        if (secureAdmin().getSecureAdminPrincipal().isEmpty() &&
            secureAdmin().getSecureAdminInternalUser().isEmpty()) {
            /*
             * Add principal(s) for the aliases.
             */
            addPrincipalForAlias(secureAdmin().dasAlias());
            addPrincipalForAlias(secureAdmin().instanceAlias());
        }
    }

    private TopLevelContext topLevelContext() {
        if (topLevelContext == null) {
            topLevelContext = new TopLevelContext(transaction(), domain);
        }
        return topLevelContext;
    }
    
    private void addPrincipalForAlias(final String alias) throws IOException, KeyStoreException, TransactionFailure {
        final SecureAdmin secureAdmin_w = topLevelContext().writableSecureAdmin();
        final SecureAdminPrincipal p = secureAdmin_w.createChild(SecureAdminPrincipal.class);
        p.setDn(secureAdminHelper.getDN(alias, true));
        secureAdmin_w.getSecureAdminPrincipal().add(p);
    }
    
    private String specialAdminIndicator() {
        final UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }
    
    private SecureAdmin secureAdmin() throws TransactionFailure {
        if (secureAdmin == null) {
            secureAdmin = domain.getSecureAdmin();
            if (secureAdmin == null) {
                secureAdmin = topLevelContext().writableSecureAdmin(); //writableDomain().createChild(SecureAdmin.class);
                secureAdminWasCreated = true;
                secureAdmin.setSpecialAdminIndicator(specialAdminIndicator());
            }
        }
        return secureAdmin;
    }
    
    private boolean ensureConfigReady(final Config c) throws TransactionFailure {
        /*
         * See if this config is already set up for secure admin.
         */
        Protocol secAdminProtocol = c.getNetworkConfig().getProtocols().findProtocol(SecureAdminCommand.SEC_ADMIN_LISTENER_PROTOCOL_NAME);
        if (secAdminProtocol != null) {
            return true;
        }
        final EnableSecureAdminCommand enableCmd = new EnableSecureAdminCommand();
        final Config c_w = t.enroll(c);
        ConfigLevelContext configLevelContext = 
                new ConfigLevelContext(topLevelContext, c_w);
        for (Iterator<Work<ConfigLevelContext>> it = enableCmd.perConfigSteps(); it.hasNext();) {
            final Work<ConfigLevelContext> step = it.next();
            if ( ! step.run(configLevelContext)) {
                t.rollback();
                return false;
            }
        }
        return true;
    }
    
    private Transaction transaction() {
        if (t == null) {
            t = new Transaction();
        }
        return t;
    }
    
    @Override
    public Lifecycle getLifecycle() {
        /*
         * The services runs only during start-up, not for the life of the server.
         */
        return Lifecycle.START;
    }
    
}
