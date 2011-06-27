/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.admin.rest;

import com.sun.grizzly.tcp.http11.GrizzlyRequest;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Rest Sessions. 
 * @author Mitesh Meswani
 */
public class SessionManager {

    //TODO Singleton pattern. Get rid of it in next checkin. Put SessionManager at application level in ResourceConfig
    private static SessionManager theSessionManager = new SessionManager();
    private SessionManager() {}
    public static SessionManager getSessionManager() { return theSessionManager;}

    private final SecureRandom randomGenerator = new SecureRandom();

    private Map<String, SessionData> activeSessions = new ConcurrentHashMap<String, SessionData>(); //To guard against parallel mutation corrupting the map

    //TODO createSession is public. this package should not be exported
    public String createSession(GrizzlyRequest req) {
        String sessionId;
        do {
            sessionId = new BigInteger(130, randomGenerator).toString(16);
        } while(isSessionExist(sessionId));

        saveSession(sessionId, req);
        return sessionId;
    }

    public boolean authenticate(String sessionId, GrizzlyRequest req) {
        boolean authenticated = false;

        if(sessionId != null) {
            SessionData sessionData = activeSessions.get(sessionId);
            if(sessionData != null) {
                authenticated = sessionData.authenticate(req);
                if(authenticated) {
                    // update last access time
                    sessionData.updateLastAccessTime();
                } else {
                    activeSessions.remove(sessionId);
                }
            }
        }
        return authenticated;
    }

    /**
     * Deletes Session corresponding to given <code> sessionId </code>
     * @param sessionId
     * @return
     */
    public boolean deleteSession(String sessionId) {
        boolean sessionDeleted = false;
        if(sessionId != null) {
            SessionData removedSession = activeSessions.remove(sessionId);
            sessionDeleted = (removedSession != null);
        }
        return sessionDeleted;
    }

    private void saveSession(String sessionId, GrizzlyRequest req) {
        purgeInactiveSessions();
        activeSessions.put(sessionId, new SessionData(sessionId, req) );
    }

    private void purgeInactiveSessions() {
        Set<Map.Entry<String, SessionData>> activeSessionsSet = activeSessions.entrySet();
        for (Map.Entry<String, SessionData> entry : activeSessionsSet) {
            if(!entry.getValue().isSessionActive() ) {
                activeSessionsSet.remove(entry);
            }
        }
    }

    private boolean isSessionExist(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }

    private static class SessionData{
        private static long INACTIVE_SESSION_DEFAULT_LIFETIME_IN_MILIS = 30 /*mins*/ * 60 /*secs/min*/ * 1000 /*milis/seconds*/;
        private String sessionId;
        /** IP address of client as obtained from Grizzly request */
        private String clientAddress;
        private long creationTime = System.currentTimeMillis();
        private long lassAccessedTime = creationTime;
        private long inactiveSessionLifeTime = INACTIVE_SESSION_DEFAULT_LIFETIME_IN_MILIS;

        public SessionData(String sessionId, GrizzlyRequest req) {
            this.sessionId = sessionId;
            this.clientAddress = req.getRemoteAddr();
        }

        /**
         * @return true if the session has not timed out. false otherwise
         */
        public boolean isSessionActive() {
            return lassAccessedTime + inactiveSessionLifeTime > System.currentTimeMillis();
        }

        /**
         * Update the last accessed time to current time
         */
        public void updateLastAccessTime() {
            lassAccessedTime = System.currentTimeMillis();
        }

        private static final String DISABLE_REMOTE_ADDRESS_VALIDATION_PROPERTY_NAME = "org.glassfish.admin.rest.disable.remote.address.validation";
        private static final boolean disableRemoteAddressValidation = Boolean.getBoolean(DISABLE_REMOTE_ADDRESS_VALIDATION_PROPERTY_NAME);

        /**
         * @return true if session is still active and the request is from same remote address as the one session was
         * initiated from
         */
        public boolean authenticate(GrizzlyRequest req) {
            return isSessionActive() && (clientAddress.equals(req.getRemoteAddr()) || disableRemoteAddressValidation );
        }
    }


}
