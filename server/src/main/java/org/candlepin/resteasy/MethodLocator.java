/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.resteasy;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Binding;
import com.google.inject.Injector;

import org.jboss.resteasy.spi.metadata.ResourceBuilder;
import org.jboss.resteasy.spi.metadata.ResourceClass;
import org.jboss.resteasy.spi.metadata.ResourceLocator;
import org.jboss.resteasy.spi.metadata.ResourceMethod;
import org.jboss.resteasy.util.GetRestful;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * Holds a mapping of interface methods to concrete methods.
 * This map is populated during servlet initialization and then locked.
 */
public class MethodLocator {
    private static final Logger log = LoggerFactory.getLogger(MethodLocator.class);

    private Map<Method, Method> internalMap;
    private boolean hasBeenInitialized = false;

    private Injector injector;

    @Inject
    public MethodLocator(Injector injector) {
        // Maintain the insertion order for nice output in debug statement
        internalMap = new LinkedHashMap<>();
        this.injector = injector;
    }

    @SuppressWarnings("rawtypes")
    public synchronized MethodLocator init() {
        if (hasBeenInitialized) {
            throw new IllegalStateException("This map has already been initialized.");
        }

        List<Binding<?>> rootResourceBindings = new ArrayList<>();
        for (Binding<?> binding : injector.getBindings().values()) {
            Type type = binding.getKey().getTypeLiteral().getType();
            if (type instanceof Class) {
                Class<?> beanClass = (Class) type;
                if (GetRestful.isRootResource(beanClass)) {
                    rootResourceBindings.add(binding);
                }
            }
        }

        for (Binding<?> binding : rootResourceBindings) {
            Class<?> clazz = (Class) binding.getKey().getTypeLiteral().getType();
            if (Proxy.isProxyClass(clazz)) {
                for (Class<?> intf : clazz.getInterfaces()) {
                    ResourceClass resourceClass = ResourceBuilder.rootResourceFromAnnotations(intf);
                    registerConcreteMethods(resourceClass, clazz);
                }
            }
            else {
                ResourceClass resourceClass = ResourceBuilder.rootResourceFromAnnotations(clazz);
                registerConcreteMethods(resourceClass, clazz);
            }
        }

        lock();
        return this;
    }

    /**
     *
     * @param method
     * @return concrete method if available
     */
    public Method getConcreteMethod(Method method) {
        return this.internalMap.get(method);
    }

    private void registerConcreteMethods(ResourceClass resourceClass, Class<?> concreteClass) {
        for (ResourceMethod resourceMethod : resourceClass.getResourceMethods()) {
            registerConcreteMethod(resourceMethod.getMethod(), concreteClass);
        }

        for (ResourceLocator resourceMethod : resourceClass.getResourceLocators()) {
            registerConcreteMethod(resourceMethod.getMethod(), concreteClass);
        }
    }

    private void registerConcreteMethod(Method method, Class<?> concreteClass) {
        try {
            for (Class<?> iface : concreteClass.getInterfaces()) {
                Method ifaceMethod = iface.getMethod(method.getName(), method.getParameterTypes());
                internalMap.put(ifaceMethod, method);
            }
        }
        catch (NoSuchMethodException e) {
            log.error("Can't find concrete method for method {}!", method);
        }
    }

    private void lock() {
        hasBeenInitialized = true;
        internalMap = ImmutableMap.copyOf(internalMap);
    }

}
