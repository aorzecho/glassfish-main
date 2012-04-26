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


package org.glassfish.javaee.full.deployment;

import com.sun.enterprise.deployment.EarType;
import org.glassfish.api.deployment.archive.ArchiveDetector;
import org.glassfish.api.deployment.archive.ArchiveHandler;
import org.glassfish.api.deployment.archive.ArchiveType;
import org.glassfish.api.deployment.archive.ReadableArchive;
import org.glassfish.deployment.common.DeploymentUtils;
import javax.inject.Inject;
import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.component.Singleton;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Detects ear type archives.
 * It's rank can be set using system property {@link #EAR_DETECTOR_RANK_PROP}.
 * Default rank is {@link #DEFAULT_EAR_DETECTOR_RANK}.
 *
 * @author sanjeeb.sahoo@oracle.com
 */
@Service(name = EarDetector.ARCHIVE_TYPE)
@Scoped(Singleton.class)
public class EarDetector implements ArchiveDetector {

    public static final String EAR_DETECTOR_RANK_PROP = "glassfish.ear.detector.rank";
    public static final int DEFAULT_EAR_DETECTOR_RANK = 100;
    public static final String ARCHIVE_TYPE = EarType.ARCHIVE_TYPE;

    @Inject private Habitat services;
    @Inject private EarSniffer sniffer;
    @Inject private EarType archiveType;
    private ArchiveHandler archiveHandler;

    private Logger logger = Logger.getLogger(getClass().getPackage().getName());

    @Override
    public int rank() {
        return Integer.getInteger(EAR_DETECTOR_RANK_PROP, DEFAULT_EAR_DETECTOR_RANK);
    }

    @Override
    public boolean handles(ReadableArchive archive) throws IOException {
        return DeploymentUtils.isEAR(archive); // logic should be moved from DeploymentUtils to here
    }

    @Override
    public ArchiveHandler getArchiveHandler() {
        synchronized (this) {
            if(archiveHandler == null) {
                try {
                    sniffer.setup(null, logger);
                } catch (IOException e) {
                    throw new RuntimeException(e); // TODO(Sahoo): Proper Exception Handling
                }
                archiveHandler = services.forContract(ArchiveHandler.class).named(ARCHIVE_TYPE).get();
            }
            return archiveHandler;
        }
    }

    @Override
    public ArchiveType getArchiveType() {
        return archiveType;
    }
}
