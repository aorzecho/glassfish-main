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
package org.glassfish.admin.rest.composite.metadata;

import java.lang.annotation.Annotation;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.glassfish.admin.rest.composite.CompositeUtil;
import org.jvnet.hk2.config.Attribute;

/**
 *
 * @author jdlee
 */
public class ParamMetadata {
    private String name;
    private String type;
    private String help;
    private String defaultValue;
    private boolean readOnly = false;
    private Object context;

    public ParamMetadata() {

    }
    public ParamMetadata(Object context, Class<?> paramType, String name, Annotation[] annotations) {
        this.name = name;
        this.context = context;
        type = paramType.getSimpleName();
        final CompositeUtil instance = CompositeUtil.instance();
        help = instance.getHelpText(annotations);
        defaultValue = getDefaultValue(annotations);

        for (Annotation a : annotations) {
            if (a.annotationType().equals(ReadOnly.class)) {
                readOnly = true;
            }
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getHelp() {
        return help;
    }

    public void setHelp(String help) {
        this.help = help;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public String toString() {
        return "ParamMetadata{" + "name=" + name + ", type=" + type + ", help=" + help + '}';
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("type", type);
        o.put("help", help);
        o.put("default", defaultValue);
        o.put("readOnly", readOnly);

        return o;
    }

    private String getDefaultValue(Annotation[] annos) {
        String defval = null;
        if (annos != null) {
            for (Annotation annotation : annos) {
                 if (Default.class.isAssignableFrom(annotation.getClass())) {
                    try {
                        Default def = (Default)annotation;
                        Class<? extends DefaultsGenerator> clazz = def.generator();
                        boolean useContext = def.useContext();
                        if (useContext) {
                            defval = ((DefaultsGenerator) context).getDefaultValue(name);
                            break;
                        } else if (!DefaultsGenerator.class.equals(clazz)) {
                            defval = clazz.newInstance().getDefaultValue(name);
                            break;
                        } else {
                        }
                    } catch (Exception ex) {
                        Logger.getLogger(CompositeUtil.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else if (Attribute.class.isAssignableFrom(annotation.getClass())) {
                    Attribute attr = (Attribute)annotation;
                    defval = attr.defaultValue();
                    break;
                }
            }
        }
        return defval;
    }

}
