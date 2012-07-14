/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.ejb.containers;

import java.util.ArrayList;
import java.util.logging.Logger;
import javax.inject.Inject;

import com.sun.ejb.Container;
import com.sun.ejb.ContainerFactory;
import com.sun.ejb.ContainerProvider;
import com.sun.ejb.containers.builder.StatefulContainerBuilder;
import com.sun.enterprise.security.SecurityContext;
import com.sun.logging.LogDomains;
import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.api.deployment.DeploymentContext;
import org.glassfish.ejb.deployment.descriptor.EjbDescriptor;
import org.glassfish.ejb.deployment.descriptor.EjbMessageBeanDescriptor;
import org.glassfish.ejb.deployment.descriptor.EjbSessionDescriptor;
import org.glassfish.ejb.security.application.EJBSecurityManager;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.Habitat;

@Service
public final class ContainerFactoryImpl implements ContainerFactory {

    @Inject
    private Habitat services;

    private static final Logger _logger = 
    	LogDomains.getLogger(ContainerFactoryImpl.class, LogDomains.EJB_LOGGER);
    
    public Container createContainer(EjbDescriptor ejbDescriptor,
				     ClassLoader loader, 
				     EJBSecurityManager sm,
				     DeploymentContext dynamicConfigContext)
	     throws Exception 
    {
        BaseContainer container = null;
        try {
            // instantiate container class
            if (ejbDescriptor instanceof EjbSessionDescriptor) {
                EjbSessionDescriptor sd = (EjbSessionDescriptor)ejbDescriptor;
                if ( sd.isStateless() ) {
                    container = new StatelessSessionContainer(ejbDescriptor, loader);
                } else if( sd.isStateful() ) {
                    StatefulContainerBuilder sfsbBuilder = services.getService(
                            StatefulContainerBuilder.class);
                    sfsbBuilder.buildContainer(dynamicConfigContext, ejbDescriptor, loader);
                    container = sfsbBuilder.getContainer();
                } else {

                    if (sd.hasContainerManagedConcurrency() ) {
                        container = new CMCSingletonContainer(ejbDescriptor, loader);
                    } else {
                        container = new BMCSingletonContainer(ejbDescriptor, loader);
                    }
                }
            } else {

              for (ContainerProvider provider : services.getAllByContract(ContainerProvider.class)) {
                  container = (BaseContainer)provider.getContainer(ejbDescriptor, loader);
                  if (container != null) {
                      break;
                  }
              }

              if (container == null) {
                    throw new RuntimeException(ejbDescriptor.getEjbTypeForDisplay() 
                            + " Container module is not available");
              }
            }
       
            container.setSecurityManager(sm);
            
            container.initializeHome();

            return container;
        } catch ( Exception ex ) {
            throw ex;
        }
    }

} //ContainerFactoryImpl


class BeanContext {
    ClassLoader previousClassLoader;
    boolean classLoaderSwitched;
    SecurityContext
            previousSecurityContext;
}

class ArrayListStack
    extends ArrayList
{
    /**
     * Creates a stack with the given initial size
     */
    public ArrayListStack(int size) {
        super(size);
    }
    
    /**
     * Creates a stack with a default size
     */
    public ArrayListStack() {
        super();
    }

    /**
     * Pushes an item onto the top of this stack. This method will internally
     * add elements to the <tt>ArrayList</tt> if the stack is full.
     *
     * @param   obj   the object to be pushed onto this stack.
     * @see     java.util.ArrayList#add
     */
    public void push(Object obj) {
        super.add(obj);
    }

    /**
     * Removes the object at the top of this stack and returns that 
     * object as the value of this function. 
     *
     * @return     The object at the top of this stack (the last item 
     *             of the <tt>ArrayList</tt> object). Null if stack is empty.
     */
    public Object pop() {
        int sz = super.size();
        return (sz > 0) ? super.remove(sz-1) : null;
    }
    
    /**
     * Tests if this stack is empty.
     *
     * @return  <code>true</code> if and only if this stack contains 
     *          no items; <code>false</code> otherwise.
     */
    public boolean empty() {
        return super.size() == 0;
    }

    /**
     * Looks at the object at the top of this stack without removing it 
     * from the stack. 
     *
     * @return     the object at the top of this stack (the last item 
     *             of the <tt>ArrayList</tt> object).  Null if stack is empty.
     */
    public Object peek() {
        int sz = size();
        return (sz > 0) ? super.get(sz-1) : null;
    }



} //ArrayListStack

