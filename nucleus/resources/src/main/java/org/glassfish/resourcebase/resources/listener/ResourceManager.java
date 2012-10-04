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

package org.glassfish.resourcebase.resources.listener;

import com.sun.enterprise.config.serverbeans.*;
import com.sun.enterprise.util.LocalStringManagerImpl;
import com.sun.logging.LogDomains;
import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.hk2.api.IterableProvider;
import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.internal.api.ClassLoaderHierarchy;
import org.glassfish.resourcebase.resources.api.*;
import org.glassfish.resourcebase.resources.util.ResourceManagerFactory;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.*;
import org.jvnet.hk2.config.types.Property;

import javax.inject.Inject;
import javax.inject.Provider;
import java.beans.PropertyChangeEvent;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resource manager to bind various resources during start-up, create/update/delete of resource/pool
 *
 * @author Jagadish Ramu
 */
@RunLevel( value= 2, mode=RunLevel.RUNLEVEL_MODE_NON_VALIDATING)
@Service(name="ResourceManager") // this name is used in ApplicationLoaderService
public class ResourceManager implements PostConstruct, PreDestroy, ConfigListener {

  private static final Logger logger =
            LogDomains.getLogger(ResourceManager.class, LogDomains.RESOURCE_BUNDLE);

    private static LocalStringManagerImpl localStrings =
            new LocalStringManagerImpl(ResourceManager.class);

    @Inject
    private ServiceLocator locator;

    @Inject
    private ResourcesBinder resourcesBinder;

    @Inject
    private org.glassfish.resourcebase.resources.util.BindableResourcesHelper bindableResourcesHelper;

    @Inject
    private IterableProvider<ResourceManagerLifecycleListener> resourceManagerLifecycleListenerProviders;

    @Inject
    private Provider<ResourceManagerFactory> resourceManagerFactoryProvider;

    @Inject
    private ServerEnvironment environment;

    @Inject
    private Domain domain;

    @Inject
    private ClassLoaderHierarchy clh;

    public void postConstruct() {
        notifyListeners(ResourceManagerLifecycleListener.EVENT.STARTUP);
        deployResources(domain.getResources().getResources());
        addListenerToResources();
        addListenerToResourceRefs();
        addListenerToServer();
    }

    private void notifyListeners(ResourceManagerLifecycleListener.EVENT event) {
        for (ResourceManagerLifecycleListener listener : resourceManagerLifecycleListenerProviders) {
            listener.resourceManagerLifecycleEvent(event);
        }
    }

    private void addListenerToResources() {
        Resources resources = domain.getResources();
        ObservableBean bean = (ObservableBean) ConfigSupport.getImpl(resources);
        bean.addListener(this);
    }

    private void addListenerToServer() {
        Server server = domain.getServerNamed(environment.getInstanceName());
        ObservableBean bean = (ObservableBean) ConfigSupport.getImpl(server);
        bean.addListener(this);
    }

    /**
     * deploy resources
     *
     * @param resources list
     */
    public void deployResources(Collection<Resource> resources) {
        for (Resource resource : resources) {
            if (resource instanceof BindableResource) {
                BindableResource bindableResource = (BindableResource) resource;
                if (bindableResourcesHelper.isBindableResourceEnabled(bindableResource)) {
                    ResourceInfo resourceInfo = new ResourceInfo(bindableResource.getJndiName());
                    resourcesBinder.deployResource(resourceInfo, resource);
                }
            } else if (resource instanceof ServerResource) {
            	deployServerResource(resource);
            } else if (resource instanceof ResourcePool) {
                // ignore, as they are loaded lazily
            } else {
                // only other resource types left are RAC, CWSM
                try {
                    getResourceDeployer(resource).deployResource(resource);
                } catch (Exception e) {
                    Object[] params = {org.glassfish.resourcebase.resources.util.ResourceUtil.getGenericResourceInfo(resource), e};
                    logger.log(Level.WARNING, "resources.resource-manager.deploy-resource-failed", params);
                }
            }
        }
        /* TODO V3 
            will there be a chance of double listener registration for a resource ?
            eg: allresources added during startup, resources of a particular
            connector during connector startup / redeploy ?
        */
        addListenerToResources(resources);
    }

    private void deployServerResource(Resource resource) {
        ResourceDeployer deployer = getResourceDeployer(resource);
        if (deployer == null) {
            logger.log(
                    Level.WARNING,
                    "resources.resource-manager.no-resource-deployer",
                    new Object[] { resource.getIdentity() }
                    );
            return;
        }
        ResourceDeployerValidator deployerValidator = getResourceDeployerValidator(deployer);
        try {
            if (deployerValidator.isEnabledLocally(resource))
                deployer.deployResource(resource);
        } catch (Exception e) {
            Object[] params = {
                    org.glassfish.resourcebase.resources.util.ResourceUtil.getGenericResourceInfo(resource), e };
            logger.log(
                    Level.WARNING,
                    "resources.resource-manager.deploy-resource-failed",
                    params);
        }
    }

    private ResourceDeployerValidator getResourceDeployerValidator(
            ResourceDeployer deployer) {
        ResourceDeployerInfo annotation = deployer.getClass().
        	getAnnotation(ResourceDeployerInfo.class);
        ResourceDeployerValidator deployerValidator =
        	locator.getService(annotation.validator());
        return deployerValidator;
    }


    public Resources getAllResources() {
        return domain.getResources();
    }


    /**
     * Do cleanup of system-resource-adapter, resources, pools
     */
    public void preDestroy() {
        removeListenerForAllResources();
        removeListenerForResources();
        removeListenerForResourceRefs();
        removeListenerForServer();
        notifyListeners(ResourceManagerLifecycleListener.EVENT.SHUTDOWN);
    }

    private void removeListenerForServer() {
        Server server = domain.getServerNamed(environment.getInstanceName());
        ObservableBean bean = (ObservableBean) ConfigSupport.getImpl(server);
        bean.removeListener(this);
    }

    /**
     * undeploy the given set of resources<br>
     * <b>care has to be taken for the case of dependent resources<br>
     * eg : all resources need to be undeployed <br>
     * before undeploying the pool that they refer to</b>
     *
     * @param resources list of resources
     */
    public void undeployResources(Collection<Resource> resources) {
        for (Resource resource : resources) {
            try {
                ResourceDeployer deployer = getResourceDeployer(resource);
                if (deployer != null)
                    deployer.undeployResource(resource);
                else {
                    Object[] params = {resource.getIdentity()};
                    logger.log(Level.WARNING, 
                            "resources.resource-manager.undeploy-no-resource-deployer", 
                            params);
                }
            } catch (Exception e) {
                Object[] params = {org.glassfish.resourcebase.resources.util.ResourceUtil.getGenericResourceInfo(resource), e};
                logger.log(Level.WARNING, "resources.resource-manager.undeploy-resource-failed", params);
            } finally {
                removeListenerForResource(resource);
            }
        }
    }

    /**
     * Notification that @Configured objects that were injected have changed
     *
     * @param events list of changes
     */
    public UnprocessedChangeEvents changed(PropertyChangeEvent[] events) {
        return ConfigSupport.sortAndDispatch(events, new PropertyChangeHandler(events), logger);
    }

    class PropertyChangeHandler implements Changed {

        PropertyChangeEvent[] events;

        private PropertyChangeHandler(PropertyChangeEvent[] events) {
            this.events = events;
        }

        /**
         * Notification of a change on a configuration object
         *
         * @param type            type of change : ADD mean the changedInstance was added to the parent
         *                        REMOVE means the changedInstance was removed from the parent, CHANGE means the
         *                        changedInstance has mutated.
         * @param changedType     type of the configuration object
         * @param changedInstance changed instance.
         */
        public <T extends ConfigBeanProxy> NotProcessed changed(TYPE type, Class<T> changedType, T changedInstance) {
	    NotProcessed np = null;
            ClassLoader contextCL = Thread.currentThread().getContextClassLoader();
            try {
                ClassLoader ccl = clh.getConnectorClassLoader(null);
                Thread.currentThread().setContextClassLoader(ccl);
                switch (type) {
                    case ADD:
                        if (logger.isLoggable(Level.FINE)) {
                            logger.fine("A new " + changedType.getName() + " was added : " + changedInstance);
                        }
                        np = handleAddEvent(changedInstance);
                        break;

                    case CHANGE:
                        if (logger.isLoggable(Level.FINE)) {
                            logger.fine("A " + changedType.getName() + " was changed : " + changedInstance);
                        }
                        np = handleChangeEvent(changedInstance);
                        break;

                    case REMOVE:
                        if (logger.isLoggable(Level.FINE)) {
                            logger.fine("A " + changedType.getName() + " was removed : " + changedInstance);
                        }
                        np = handleRemoveEvent(changedInstance);
                        break;

                    default:
                        np = new NotProcessed("Unrecognized type of change: " + type);
                        break;
                }
                return np;
            } finally {
                Thread.currentThread().setContextClassLoader(contextCL);
            }
        }

        private <T extends ConfigBeanProxy> NotProcessed handleChangeEvent(T instance) {
            NotProcessed np = null;
            //TODO V3 handle enabled / disabled / resource-ref / redeploy ?
            try {
                if (org.glassfish.resourcebase.resources.util.ResourceUtil.isValidEventType(instance)) {
                    ResourceDeployer deployer = getResourceDeployer(instance);
                    boolean enabledAttributeChange = false;
                    if (instance instanceof BindableResource ||
                        instance instanceof ServerResource) {
                        for (PropertyChangeEvent event : events) {
                            String propertyName = event.getPropertyName();
                            //Depending on the type of event (disable/enable, invoke the
                            //method on deployer.
                            if ("enabled".equalsIgnoreCase(propertyName)) {
                                enabledAttributeChange = true;
                                BindableResource bindableResource = (BindableResource) instance;
                                if (deployer != null) {
                                    boolean newValue = Boolean.valueOf(event.getNewValue().toString());
                                    boolean oldValue = Boolean.valueOf(event.getOldValue().toString());
                                    if (!(newValue && oldValue)) {
                                        if (newValue) {
                                            deployer.enableResource(bindableResource);
                                        } else {
                                            deployer.disableResource(bindableResource);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (!enabledAttributeChange && deployer != null) {
                        if (instance instanceof BindableResource) {
                            BindableResource bindableResource = (BindableResource) instance;
                            if (getEnabledResourceRefforResource(bindableResource) && Boolean.valueOf(bindableResource.getEnabled())) {
                                deployer.redeployResource(instance);
                            }
                        } else if (instance instanceof ServerResource){
                            // FIXME: if Resource namespace is global, then we can collapse
                            // this and the above branch using the getIdentity() attribute?
                            ServerResource serverResource = (ServerResource) instance; 
                            if (isServerResourceEnabled(serverResource)) {
                                deployer.redeployResource(instance);
                            }
                        } else {
                            deployer.redeployResource(instance);
                        }
                    }
                } else if (org.glassfish.resourcebase.resources.util.ResourceUtil.isValidEventType(instance.getParent())) {
                    //Added in case of a property change
                    //check for validity of the property's parent and redeploy
                    ResourceDeployer deployer = getResourceDeployer(instance);
                    if (instance.getParent() instanceof BindableResource) {
                        BindableResource bindableResource = (BindableResource) instance.getParent();
                        if (getEnabledResourceRefforResource(bindableResource) && Boolean.valueOf(bindableResource.getEnabled())) {
                            getResourceDeployer(instance.getParent()).redeployResource(instance.getParent());
                        }
                    } else if (instance instanceof ServerResource && deployer != null){
                        // FIXME: if Resource namespace is global, then we can collapse
                        // this and the above branch using the getIdentity() attribute?
                        ServerResource serverResource = (ServerResource) instance; 
                        if (isServerResourceEnabled(serverResource)) {
                            deployer.redeployResource(instance);
                        }
                    } else {
                        getResourceDeployer(instance.getParent()).redeployResource(instance.getParent());
                    }
                } else if (instance instanceof ResourceRef) {
                    ResourceRef ref = (ResourceRef) instance;
                    String refName = ref.getRef();
                    BindableResource bindableResource = null;
                    ResourceDeployer deployer = null;
                    for (PropertyChangeEvent event : events) {
                        String propertyName = event.getPropertyName();
                        //Depending on the type of event (disable/enable, invoke the 
                        //method on deployer.
                        if ("enabled".equalsIgnoreCase(propertyName)) {
                            bindableResource = (BindableResource)
                                    org.glassfish.resourcebase.resources.util.ResourceUtil.getBindableResourceByName(domain.getResources(), refName);
                            deployer = getResourceDeployer(bindableResource);
                            if (deployer != null) {
                                //both cannot be true or false
                                boolean newValue = Boolean.valueOf(event.getNewValue().toString());
                                boolean oldValue = Boolean.valueOf(event.getOldValue().toString());
                                if (!(newValue && oldValue)) {
                                    if (newValue) {
                                        deployer.enableResource(bindableResource);
                                    } else {
                                        deployer.disableResource(bindableResource);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "resources.resource-manager.change-event-failed", ex);
                np = new NotProcessed(
                        localStrings.getLocalString(
                                "resources.resource-manager.change-event-failed",
                                "Change event failed"));
            }
            return np;
        }

        private boolean isServerResourceEnabled(ServerResource serverResource) {
            return getEnabledResourceRefforResource(serverResource) && 
                    Boolean.valueOf(serverResource.getEnabled());
        }

        private <T extends ConfigBeanProxy> NotProcessed handleAddEvent(T instance) {
            NotProcessed np = null;
            //Add listener to the changed instance object
            ResourceManager.this.addListenerToResource(instance);

            if (instance instanceof BindableResource) {
                //ignore - as bindable-resources are handled during resource-ref creation
            } else if (instance instanceof ServerResource) {
                //ignore - as bindable-resources are handled during resource-ref creation
            } else if (instance instanceof ResourcePool) {
                //ignore - as pools are handled lazily
            } else if (instance instanceof Resource) {
                //only resource type left are RAC and CWSM
                try {
                    getResourceDeployer(instance).deployResource(instance);
                } catch (Exception e) {
                    Object params[] = {org.glassfish.resourcebase.resources.util.ResourceUtil.getGenericResourceInfo((Resource) instance), e};
                    logger.log(Level.WARNING, "resources.resource-manager.deploy-resource-failed", params);
                }
            } else if (instance instanceof Property) {
                //Property is not handled here. It is handled as part of the
                //Change event of the resource. 
            } else if (instance instanceof ResourceRef) {
                //create-resource-ref
                ResourceRef ref = (ResourceRef) instance;
                Resource resource = org.glassfish.resourcebase.resources.util.ResourceUtil.getResourceByIdentity(domain.getResources(), ref.getRef());
                if (resource instanceof BindableResource) {
                    BindableResource br = (BindableResource) resource;
                    if (Boolean.valueOf(ref.getEnabled()) && Boolean.valueOf(br.getEnabled())) {
                        ResourceInfo resourceInfo = new ResourceInfo(br.getJndiName());
                        resourcesBinder.deployResource(resourceInfo, br);
                    }
                } else if (resource instanceof ServerResource) {
                    ServerResource sr = (ServerResource) resource;
                      try {
                        if (Boolean.valueOf(ref.getEnabled())
                            && Boolean.valueOf(sr.getEnabled())) {
                          ResourceDeployer deployer = getResourceDeployer(resource);
                          if (deployer != null)
                            deployer.deployResource(resource);
                        }
                      } catch (Exception e) {
                        Object params[] = {org.glassfish.resourcebase.resources.util.ResourceUtil.getGenericResourceInfo((Resource) instance), e};
                        logger.log(Level.WARNING, "resources.resource-manager.deploy-resource-failed", params);
                      }
                }
            }
            return np;
        }

        private <T extends ConfigBeanProxy> NotProcessed handleRemoveEvent(final T instance) {
            NotProcessed np = null;
            try {
                //this check ensures that a valid resource is handled
                if (instance instanceof BindableResource) {
                    //ignore as bindable-resources will have resource-ref.
                    ResourceManager.this.removeListenerForResource(instance);
                } else if (instance instanceof Resource) {
                    //Remove listener from the removed instance
                    ResourceManager.this.removeListenerForResource(instance);
                    //get appropriate deployer and undeploy resource
                    ResourceDeployer resourceDeployer = getResourceDeployer(instance);
                    if (resourceDeployer != null)
                      resourceDeployer.undeployResource(instance);
                } else if (org.glassfish.resourcebase.resources.util.ResourceUtil.isValidEventType(instance.getParent())) {
                    //Added in case of a property remove
                    //check for validity of the property's parent and redeploy
                    if (instance.getParent() instanceof BindableResource) {
                        BindableResource bindableResource = (BindableResource) instance.getParent();
                        if (getEnabledResourceRefforResource(bindableResource) && Boolean.valueOf(bindableResource.getEnabled())) {
                            getResourceDeployer(instance.getParent()).redeployResource(instance.getParent());
                        }
                    } else if (instance.getParent() instanceof ServerResource){
                        // FIXME: if Resource namespace is global, then we can collapse
                        // this and the above branch using the getIdentity() attribute?
                        ServerResource serverResource = (ServerResource) instance; 
                        ResourceDeployer deployer = getResourceDeployer(serverResource);
                        if (deployer != null && isServerResourceEnabled(serverResource)) {
                            deployer.redeployResource(instance);
                        }
                    } else {
                        getResourceDeployer(instance.getParent()).redeployResource(instance.getParent());
                    }

                } else if (instance instanceof ResourceRef) {
                    //delete-resource-ref
                    ResourceRef ref = (ResourceRef) instance;
                    Resource resource = (Resource) org.glassfish.resourcebase.resources.util.ResourceUtil.getResourceByName(
                            Resource.class, domain.getResources(), ref.getRef());
//                    BindableResource resource = (BindableResource)
//                            ResourceUtil.getBindableResourceByName(domain.getResources(), ref.getRef());
                    //get appropriate deployer and undeploy resource
                    boolean resourceEnabled = isResourceEnabled(resource);
                    if (resourceEnabled && Boolean.valueOf(ref.getEnabled())) {
                        ResourceDeployer resourceDeployer =
                            getResourceDeployer(resource);
                        if (resourceDeployer != null)
                            resourceDeployer.undeployResource(resource);
                    }
                    //Remove listener from the removed instance
                    ResourceManager.this.removeListenerForResource(instance);
                }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "resources.resource-manager.remove-event-failed");
                np = new NotProcessed(
                        localStrings.getLocalString(
                                "resources.resource-manager.remove-event-failed",
                                "Remove event failed"));
            }
            return np;
        }

        private boolean isResourceEnabled(Resource resource) {
            boolean resourceEnabled = Boolean.valueOf(
                    resource instanceof BindableResource ? 
                            ((BindableResource) resource).getEnabled() 
                            : (resource instanceof ServerResource ? 
                                    ((ServerResource) resource).getEnabled() : "false"));
            return resourceEnabled;
        }
    }


    /**
     * Add listener to all resources
     * Invoked from postConstruct()
     *
     * @param resources list of resources for which listeners will be registered.
     */
    private void addListenerToResources(Collection<Resource> resources) {
        for (Resource configuredResource : resources) {
            addListenerToResource(configuredResource);
        }
    }

    private boolean getEnabledResourceRefforResource(BindableResource bindableResource) {
        for (ResourceRef ref : getResourceRefs()) {
            if (ref.getRef().equals(bindableResource.getJndiName())) {
                return Boolean.valueOf(ref.getEnabled());
            }
        }
        return false;
    }

    private boolean getEnabledResourceRefforResource(ServerResource bindableResource) {
        for (ResourceRef ref : getResourceRefs()) {
            if (ref.getRef().equals(bindableResource.getIdentity())) {
                return Boolean.valueOf(ref.getEnabled());
            }
        }
        return false;
    }

    private void addListenerToResourceRefs() {
        for (ResourceRef ref : getResourceRefs()) {
            addListenerToResource(ref);
        }
    }

    private List<ResourceRef> getResourceRefs() {
        //Instead of injecting ResourceRef[] config array (which will inject all resource-refs in domain.xml
        //including the ones in other server instances), get appropriate instance's resource-refs alone.
        return domain.getServerNamed(environment.getInstanceName()).getResourceRef();
    }

    /**
     * Add listener to a generic resource
     * Used in the case of create asadmin command when listeners have to
     * be added to the specific resource
     *
     * @param instance instance to which listener will be registered
     */
    private void addListenerToResource(Object instance) {
        ObservableBean bean = null;

        //add listener to all types of Resource
        if (instance instanceof Resource) {
            bean = (ObservableBean) ConfigSupport.getImpl((ConfigBeanProxy) instance);
            bean.addListener(this);
        } else if (instance instanceof ResourceRef) {
            bean = (ObservableBean) ConfigSupport.getImpl((ConfigBeanProxy) instance);
            bean.addListener(this);
        }
    }


    /**
     * Remove listener from a resource
     * Used in the case of delete asadmin command
     *
     * @param instance remove the resource from listening to resource events
     */
    private void removeListenerForResource(Object instance) {
        ObservableBean bean = null;

        if (instance instanceof Resource) {
            bean = (ObservableBean) ConfigSupport.getImpl((ConfigBeanProxy) instance);
            bean.removeListener(this);
        } else if (instance instanceof ResourceRef) {
            bean = (ObservableBean) ConfigSupport.getImpl((ConfigBeanProxy) instance);
            bean.removeListener(this);
        }
    }

    /**
     * Remove listener from all resource refs.
     * Invoked from preDestroy().
     */
    private void removeListenerForResourceRefs() {
        for (ResourceRef ref : getResourceRefs()) {
            removeListenerForResource(ref);
        }
    }

    private void removeListenerForResources() {
        ObservableBean bean = (ObservableBean) ConfigSupport.getImpl(domain.getResources());
        bean.removeListener(this);
    }

    /**
     * Remove listener from all resources
     */
    private void removeListenerForAllResources() {
        for (Resource configuredResource : domain.getResources().getResources()) {
            removeListenerForResource(configuredResource);
        }
    }

    /**
     * Given a <i>resource</i> instance, appropriate deployer will be provided
     *
     * @param resource resource instance
     * @return ResourceDeployer
     */
    private ResourceDeployer getResourceDeployer(Object resource) {
        return resourceManagerFactoryProvider.get().getResourceDeployer(resource);
    }
}
