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
package com.sun.enterprise.admin.cli.remote;

import com.sun.enterprise.admin.progress.ProgressStatusClient;
import com.sun.enterprise.admin.remote.sse.GfSseInboundEvent;
import com.sun.enterprise.universal.i18n.LocalStringsImpl;
import com.sun.enterprise.util.StringUtils;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.core.MediaType;
import org.glassfish.api.admin.AdminCommandEventBroker.AdminCommandListener;
import org.glassfish.api.admin.CommandProgress;
import org.glassfish.api.admin.progress.ProgressStatusDTO;
import org.glassfish.api.admin.progress.ProgressStatusEvent;

/** Prints ProgressStatus changes to given logger
 *
 * @author mmares
 */
public class ProgressStatusPrinter implements AdminCommandListener<GfSseInboundEvent> {
    
    private class Ticker extends Thread {
        
        private final long pause;
        private volatile boolean stop = false;

        public Ticker(long pause) {
            this.pause = pause;
            this.setDaemon(true);
        }
        
        public void stopit() {
            this.stop = true;
        }
        
        @Override
        public void run() {
            do {
                try {
                    Thread.sleep(pause);
                } catch (InterruptedException ex) {
                }
                if (stop) {
                    return;
                }
            } while (spin());
        }
        
    }
    
    private static final LocalStringsImpl strings =
            new LocalStringsImpl(ProgressStatusPrinter.class);
    
    private static final char[] spinner = new char[] {'|', '/', '-', '\\'};
    
    private int lastPercentage = -1;
    private String lastMessage = "";
    private StringBuilder outMsg = new StringBuilder();
    private int lastSumSteps = -1;
    private int lastMsgLength = 0;
    private boolean firstPrint = true;
    private int spinnerIndex = -1;
    private Ticker ticker = null;
    
    private ProgressStatusClient client = new ProgressStatusClient(null);
    private CommandProgress commandProgress;
    private final boolean disableAnimation;
    private final boolean debugOutput; 
    private final Logger logger;

    /** Construct new printer
     * @param disableAnimation will print each message on new line and spinner as dots
     * @param debugOutput expect printing of other data together with progress. 
     *                    New line must be printed ASAP.
     */
    public ProgressStatusPrinter(boolean disableAnimation, boolean debugOutput, Logger logger) {
        this.disableAnimation = disableAnimation;
        this.debugOutput = debugOutput;
        if (logger == null) {
            this.logger = Logger.getLogger(ProgressStatusPrinter.class.getName());
        } else {
            this.logger = logger;
        }
    }
    
    private synchronized boolean spin() {
        if (commandProgress == null || 
                !commandProgress.isSpinnerActive() || 
                debugOutput ||
                lastMsgLength <= 1) {
            return false;
        }
        if (spinnerIndex >= 0 && !disableAnimation) {
            System.out.print("\b\b");
            lastMsgLength -= 2;
        }
        spinnerIndex++;
        if (spinnerIndex >= spinner.length) {
            spinnerIndex = 0;
        }
        if (disableAnimation) {
            System.out.print('.');
            lastMsgLength++;
        } else {
            System.out.print(spinner[spinnerIndex]);
            System.out.print(' ');
            lastMsgLength += 2;
        }
        return true;
    }
    
    @Override
    public synchronized void onAdminCommandEvent(String name, GfSseInboundEvent event) {
        try {
            if (CommandProgress.EVENT_PROGRESSSTATUS_STATE.equals(name)) {
                ProgressStatusDTO dto = event.getData(ProgressStatusDTO.class, MediaType.APPLICATION_JSON_TYPE);
                client.mirror(dto);
                commandProgress = (CommandProgress) client.getProgressStatus();
                if (StringUtils.ok(commandProgress.getName()) && !StringUtils.ok(commandProgress.getLastMessage())) {
                    commandProgress.progress(strings.getString("progressstatus.message.starting", "Starting"));
                }
            } else if (CommandProgress.EVENT_PROGRESSSTATUS_CHANGE.equals(name)) {
                if (commandProgress == null) {
                    logger.log(Level.WARNING, strings.get("progressstatus.event.applyerror", "Inapplicable progress status event"));
                    return;
                }
                ProgressStatusEvent pse = event.getData(ProgressStatusEvent.class, MediaType.APPLICATION_JSON_TYPE);
                client.mirror(pse);
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, strings.get("progressstatus.event.parseerror", "Can not parse progress status event"), ex);
        }
        //Now print
        if (commandProgress != null) {
            outMsg.setLength(0);
            boolean printIt = false;
            //Measurements
            int percentage = Math.round(commandProgress.computeCompletePortion() * 100);
            if (percentage >= 0) {
                outMsg.append(percentage);
                switch (outMsg.length()) {
                    case 1:
                        outMsg.insert(0, "  ");
                        break;
                    case 2:
                        outMsg.insert(0, ' ');
                        break;
                    default:
                        break;
                }
                outMsg.append('%');
                if (percentage > lastPercentage) {
                    printIt = true;
                    lastPercentage = percentage;
                }
            } else {
                int sumSteps = commandProgress.computeSumSteps();
                outMsg.append(sumSteps);
                if (sumSteps > lastSumSteps) {
                    printIt = true;
                    lastSumSteps = sumSteps;
                }
            }
            //Message
            String message = commandProgress.getLastMessage();
            if (message != null && message.length() > 0) {
                outMsg.append(": ");
                outMsg.append(message);
                if (!message.equals(lastMessage)) {
                    printIt = true;
                    lastMessage = message;
                }
            }
            //Print
            if (printIt) {
                if (disableAnimation || debugOutput) {
                    if (!firstPrint && !debugOutput) {
                        System.out.println();
                    }
                    firstPrint = false;
                    System.out.print(outMsg);
                    this.lastMsgLength = outMsg.length();
                    if (debugOutput) {
                        System.out.println();
                    }
                } else {
                    if (!firstPrint) {
                        System.out.print('\r');
                    } else {
                        firstPrint = false;
                    }
                    System.out.print(outMsg);
                    System.out.print(' ');
                    int spaceCount = lastMsgLength - outMsg.length();
                    for (int i = 0; i < spaceCount; i++) {
                        System.out.print(' ');
                    }
                    for (int i = 0; i < spaceCount; i++) {
                        System.out.print('\b');
                    }
                    lastMsgLength = outMsg.length();
                    spinnerIndex = -1;
                }
            }
            //Change ticker
            if (!debugOutput && commandProgress != null) {
                if (commandProgress.isSpinnerActive()) {
                    if (this.ticker == null) {
                        this.ticker = new Ticker(disableAnimation ? 1500L : 500L);
                        this.ticker.start();
                    }
                } else {
                    if (this.ticker != null) {
                        this.ticker.stopit();
                        this.ticker = null;
                    }
                }
            }
        }
    }
    
    public synchronized void deleteLastMessage() {
        if (lastMsgLength <= 0) {
            return;
        }
        if (disableAnimation || debugOutput) {
            System.out.println();
        } else {
            System.out.print('\r');
            for (int i = 0; i < lastMsgLength; i++) {
                System.out.print(' ');
            }
            System.out.print('\r');
        }
        if (this.ticker != null) {
            this.ticker.stopit();
            this.ticker = null;
        }
        lastMsgLength = 0;
    }

    public synchronized void reset() {
        client = new ProgressStatusClient(null);
        commandProgress = null;
        lastPercentage = -1;
        lastMessage = "";
        outMsg.setLength(0);
        lastSumSteps = -1;
        lastMsgLength = 0;
        firstPrint = true;
        if (this.ticker != null) {
            this.ticker.stopit();
            this.ticker = null;
        }
    }
    
}
