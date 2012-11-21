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
package org.glassfish.admin.rest.utils;

import java.util.ArrayList;
import java.util.List;
import org.glassfish.admin.rest.composite.RestModel;
import org.glassfish.admin.rest.model.Message;
import org.glassfish.admin.rest.model.Message.Severity;
import org.glassfish.admin.rest.model.ResponseBody;

/**
 *
 * @author tmoreau
 */
public class ResponseBodyImpl implements ResponseBody {
    private List<Message> messages = new ArrayList<Message>();
    private RestModel entity;

    @Override
    public List<Message> getMessages() {
        return this.messages;
    }

    @Override
    public void setMessages(List<Message> val) {
        this.messages = val;
    }

    @Override
    public RestModel getEntity() {
        return entity;
    }

    @Override
    public ResponseBody setEntity(RestModel entity) {
        this.entity = entity;
        return this;
    }

    @Override
    public ResponseBodyImpl addSuccess(String message) {
        return add(Severity.SUCCESS, message);
    }

    @Override
    public ResponseBodyImpl addWarning(String message) {
        return add(Severity.WARNING, message);
    }

    @Override
    public ResponseBodyImpl addFailure(String message) {
        return add(Severity.FAILURE, message);
    }

    @Override
    public ResponseBodyImpl add(Severity severity, String message) {
        return add(new MessageImpl(severity, message));
    }

    @Override
    public ResponseBodyImpl add(Message message) {
        getMessages().add(message);
        return this;
    }
    
}
