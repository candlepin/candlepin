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

import com.google.inject.Injector;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import javax.inject.Inject;

/**
 * Various filters can use utility methods to get annotations on the implementations as well as the
 * interfaces, so that we can use SecurityHole and other annotations on generated resource implementations.
 */
public class AnnotationLocator {
    private static final Logger log = LoggerFactory.getLogger(AnnotationLocator.class);

    private MethodLocator methodLocator;

    private Injector injector;

    @Inject
    public AnnotationLocator(MethodLocator methodLocator) {
        // Maintain the insertion order for nice output in debug statement
        this.methodLocator = methodLocator;
        this.injector = injector;
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
        Method concreteMethod = this.methodLocator.getConcreteMethod(method);
        if (concreteMethod != null) {
            T annotation = concreteMethod.getAnnotation(annotationClass);
            if (annotation != null) {
                return annotation;
            }
        }

        return method.getAnnotation(annotationClass);
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
        Method concreteMethod = this.methodLocator.getConcreteMethod(method);
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
