/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * ResourceLocatorMap holds a mapping of Methods to Resteasy ResourceLocators.  This map
 * is populated during servlet initialization and then locked.  The VerifyAuthorizationFilter
 * then uses the map to find the correct ResourceLocator for a Method so that we can get
 * the parameters passed to a Method and run authorization checks on them.
 */
public class ResourceLocatorMap implements Map<Method, ResourceLocator> {
    private static final Logger log = LoggerFactory.getLogger(ResourceLocatorMap.class);

    private Map<Method, ResourceLocator> internalMap;
    private MethodLocator methodLocator;
    private boolean hasBeenInitialized = false;

    private Injector injector;

    @Inject
    public ResourceLocatorMap(Injector injector, MethodLocator methodLocator) {
        // Maintain the insertion order for nice output in debug statement
        internalMap = new LinkedHashMap<>();
        this.methodLocator = methodLocator;
        this.injector = injector;
    }

    @Override
    public int size() {
        return internalMap.size();
    }

    @Override
    public boolean isEmpty() {
        return internalMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return internalMap.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return internalMap.containsKey(value);
    }

    @Override
    public ResourceLocator get(Object key) {
        Method concreteMethod = methodLocator.getConcreteMethod((Method) key);
        if (concreteMethod != null) {
            return internalMap.get(concreteMethod);
        }
        return internalMap.get(key);
    }

    @Override
    public ResourceLocator put(Method key, ResourceLocator value) {
        return internalMap.put(key, value);
    }

    @Override
    public ResourceLocator remove(Object key) {
        return internalMap.remove(key);
    }

    @Override
    public void putAll(Map<? extends Method, ? extends ResourceLocator> m) {
        internalMap.putAll(m);
    }

    @Override
    public void clear() {
        internalMap.clear();
    }

    @Override
    public Set<Method> keySet() {
        return internalMap.keySet();
    }

    @Override
    public Collection<ResourceLocator> values() {
        return internalMap.values();
    }

    @Override
    public Set<java.util.Map.Entry<Method, ResourceLocator>> entrySet() {
        return internalMap.entrySet();
    }

    @SuppressWarnings("rawtypes")
    public void init() {
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
                    registerLocators(resourceClass);
                }
            }
            else {
                ResourceClass resourceClass = ResourceBuilder.rootResourceFromAnnotations(clazz);
                registerLocators(resourceClass);
            }
        }

        logLocators();
        lock();
    }

    /**
     * Log what resources have been detected and warn about missing media types.
     *
     * Not having a @Produces annotation on a method can have subtle effects on the way Resteasy
     * resolves which method to dispatch a request to.
     *
     * Resource resolution order is detailed in the JAX-RS 2.0 specification in section 3.7.2.
     * Consider the following
     * {@code
     *   @PUT
     *   @Path("/foo")
     *   public String methodOne() {
     *       ...
     *   }
     *
     *   @PUT
     *   @Path("/{id}")
     *   @Produces(MediaType.APPLICATION_JSON)
     *   public String methodTwo(@PathParam("id") String id) {
     *       ....
     *   }
     * }
     *
     *   With a cursory reading of the specification, it appears that a request to
     *
     *   PUT /foo
     *
     *   should result in methodOne being selected since methodOne's Path has more
     *   literal characters than methodTwo.  However, methodTwo has a specific media
     *   type defined and methodOne does not (thus using the wildcard type as a default),
     *   methodTwo is actually the resource selected.
     *
     *   The same rules apply for @Consumes annotations.
     */
    protected void logLocators() {
        StringBuffer registered = new StringBuffer("Registered the following RESTful methods:\n");
        StringBuffer missingProduces = new StringBuffer();
        StringBuffer missingConsumes = new StringBuffer();
        for (Method m : keySet()) {
            String name = m.getDeclaringClass() + "." + m.getName();
            registered.append("\t" + name + "\n");

            if (!m.isAnnotationPresent(Produces.class)) {
                missingProduces.append("\t" + name + "\n");
            }

            if (m.isAnnotationPresent(GET.class) ||
                m.isAnnotationPresent(HEAD.class) ||
                m.isAnnotationPresent(DELETE.class) ||
                m.isAnnotationPresent(Deprecated.class)) {
                /* Technically GET, HEAD, and DELETE are allowed to have bodies (and therefore would
                 * need a Consumes annotation, but the HTTP 1.1 spec states in section 4.3 that any
                 * such body should be ignored.  See http://stackoverflow.com/a/983458/6124862
                 *
                 * Therefore, we won't print warnings on unannotated GET, HEAD, and DELETE methods.
                 *
                 * Deprecated methods are not expected to be updated.
                 */
                continue;
            }

            if (!m.isAnnotationPresent(Consumes.class)) {
                missingConsumes.append("\t" + name + "\n");
            }
            else {
                /* The purpose of all the ridiculousness below is to find methods that
                 * bind objects from the HTTP request body but that are marked as consuming
                 * the star slash star wildcard mediatype.  Candlepin only consumes JSON
                 * at the moment but even if that changes we still want to be explicit
                 * about what we accept.
                 */
                Consumes consumes = m.getAnnotation(Consumes.class);
                List<String> mediaTypes = Arrays.asList(consumes.value());
                if (mediaTypes.contains(MediaType.WILDCARD)) {
                    Annotation[][] allParamAnnotations = m.getParameterAnnotations();

                    boolean bindsBody = false;
                    for (Annotation[] paramAnnotations : allParamAnnotations) {
                        boolean boundObjectFromBody = true;
                        for (Annotation a : paramAnnotations) {
                            Class<? extends Annotation> clazz = a.annotationType();
                            if (QueryParam.class.isAssignableFrom(clazz) ||
                                PathParam.class.isAssignableFrom(clazz) ||
                                MatrixParam.class.isAssignableFrom(clazz) ||
                                HeaderParam.class.isAssignableFrom(clazz) ||
                                FormParam.class.isAssignableFrom(clazz) ||
                                CookieParam.class.isAssignableFrom(clazz)
                                ) {
                                boundObjectFromBody = false;
                                continue;
                            }
                        }
                        bindsBody = bindsBody || boundObjectFromBody;
                    }

                    if (bindsBody) {
                        log.warn("{} consumes a wildcard media type but binds an object from" +
                            " the request body.  Define specific media types consumed instead.", name);
                    }
                }

            }
        }

        if (log.isDebugEnabled()) {
            log.trace(registered.toString());
        }

        if (missingProduces.length() != 0) {
            log.warn("The following methods are missing a Produces annotation:\n{}", missingProduces);
        }

        if (missingConsumes.length() != 0) {
            log.warn("The following methods are missing a Consumes annotation:\n{}", missingConsumes);
        }
    }

    protected void registerLocators(ResourceClass resourceClass) {
        for (ResourceMethod resourceMethod : resourceClass.getResourceMethods()) {
            put(resourceMethod.getMethod(), resourceMethod);
        }
        for (ResourceLocator resourceMethod : resourceClass.getResourceLocators()) {
            put(resourceMethod.getMethod(), resourceMethod);
        }
    }

    public synchronized void lock() {
        hasBeenInitialized = true;
        internalMap = ImmutableMap.copyOf(internalMap);
    }
}
