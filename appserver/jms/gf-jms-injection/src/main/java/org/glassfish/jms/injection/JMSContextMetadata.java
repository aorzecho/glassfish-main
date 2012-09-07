/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.jms.injection;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jms.JMSConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSPasswordCredential;
import javax.jms.JMSSessionMode;
import org.glassfish.internal.api.RelativePathResolver;
import com.sun.enterprise.util.LocalStringManagerImpl;
import com.sun.logging.LogDomains;

/**
 * Serializable object which holds the information about the JMSContext
 * that was specified at the injection point.
 */
public class JMSContextMetadata implements Serializable {
    private final static Logger logger = LogDomains.getLogger(JMSContextMetadata.class, LogDomains.JMS_LOGGER);
    private final static LocalStringManagerImpl localStrings = new LocalStringManagerImpl(JMSContextMetadata.class);

    private final String lookup;
    private final int sessionMode;
    private final String userName;
    private final String password;

    JMSContextMetadata(JMSConnectionFactory jmsConnectionFactoryAnnot, JMSSessionMode sessionModeAnnot, JMSPasswordCredential credentialAnnot) {
        if (jmsConnectionFactoryAnnot == null) {
            lookup = null;
        } else {
            lookup = jmsConnectionFactoryAnnot.value().trim();
        }

        if (sessionModeAnnot == null) {
            sessionMode = JMSContext.AUTO_ACKNOWLEDGE;
        } else {
            sessionMode = sessionModeAnnot.value();
        }

        if (credentialAnnot == null) {
            userName = null;
            password = null;
        } else {
            userName = credentialAnnot.userName();
            password = getUnAliasedPwd(credentialAnnot.password());
        }
    }

    public String getLookup() {
       return lookup;
    }

    public int getSessionMode(){
        return sessionMode;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("JMSContextMetadata[");
        sb.append("lookup=").append(lookup);
        sb.append(", sessionMode=").append(sessionMode);
        sb.append(", username=").append(userName);
        sb.append(", password=");
        if (password != null)
            sb.append("xxxxxx");
        else
            sb.append("null");
        sb.append("]");
        return sb.toString();
    }

    private boolean isPasswordAlias(String password){
        if (password != null && password.startsWith("${ALIAS="))
            return true;
        return false;
    }

    private String getUnAliasedPwd(String password) {
        if (password != null && isPasswordAlias(password)) {
            try {
                String unalisedPwd = RelativePathResolver.getRealPasswordFromAlias(password);
                if (unalisedPwd != null && !"".equals(unalisedPwd))
                    return unalisedPwd;
            } catch (Exception e) {
                logger.log(Level.WARNING, localStrings.getLocalString("decrypt.password.fail", 
                           "Failed to unalias password for the reason: {0}."), e.toString());
            }
        }
        return password;
    }
}
