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

import org.apache.commons.lang3.ArrayUtils;
import org.jboss.resteasy.spi.metadata.ResourceBuilder;
import org.jboss.resteasy.spi.metadata.ResourceClass;
import org.jboss.resteasy.spi.metadata.ResourceLocator;
import org.jboss.resteasy.spi.metadata.ResourceMethod;
import org.jboss.resteasy.util.GetRestful;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
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
 *
 * Inspired by ResourceLocatorMap.
 *
 * This map is populated during servlet initialization and then locked. Various filters can use utility
 * methods to get annotations on the implementations as well as the interfaces, so that we can use
 * SecurityHole and other annotations on generated resource implementations.
 */
public class AnnotationLocator {
    private static final Logger log = LoggerFactory.getLogger(AnnotationLocator.class);

    private Map<Method, Method> internalMap;
    private boolean hasBeenInitialized = false;

    private Injector injector;

    @Inject
    public AnnotationLocator(Injector injector) {
        // Maintain the insertion order for nice output in debug statement
        internalMap = new LinkedHashMap<>();
        this.injector = injector;
    }

    @SuppressWarnings("rawtypes")
    public synchronized void init() {
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

    /**
     * Looks up annotation for a resource from the concrete implementation.
     *
     * Falls back to any instance of that annotation on the method itself if no concrete implementation is
     * registered.
     *
     * @param method method to get annotation for
     * @param annotationClass which annotation to try to get
     * @return annotation or null, if no such annotation is present
     */
    public <T extends Annotation> T getAnnotation(Method method, Class<T> annotationClass) {
        Method concreteMethod = internalMap.get(method);
        if (concreteMethod != null) {
            T annotation = concreteMethod.getAnnotation(annotationClass);
            if (annotation != null) {
                return annotation;
            }
        }

        return method.getAnnotation(annotationClass);
    }

    private void lock() {
        hasBeenInitialized = true;
        internalMap = ImmutableMap.copyOf(internalMap);
    }

    /**
     * Get all parameter annotations for all parameters, including those on both interfaces and
     * concrete method signatures.
     *
     * @param method method to inspect
     * @return Stream of annotations
     */
    public Annotation[][] getParameterAnnotations(Method method) {
        Annotation[][] annotations = method.getParameterAnnotations();
        Method concreteMethod = internalMap.get(method);
        if (concreteMethod != null) {
            Annotation[][] concreteAnnotations = concreteMethod.getParameterAnnotations();
            for (int i = 0; i < annotations.length; i++) {
                Annotation[] concreteParamAnnotations = concreteAnnotations[i];
                if (concreteParamAnnotations.length > 0) {
                    annotations[i] = ArrayUtils.addAll(annotations[i], concreteParamAnnotations);
                }
            }
        }

        return annotations;
    }
}
