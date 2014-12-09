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
package org.candlepin.common.jackson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Field;



/**
 * JsonBeanPropertyFilter
 */
public abstract class JsonBeanPropertyFilter extends CheckableBeanPropertyFilter {
    private static Logger log = LoggerFactory.getLogger(JsonBeanPropertyFilter.class);

    /**
     * Checks if the specified annotation has been applied to the given object's class, property
     * or accessor (in that order).
     *
     * @param obj
     *  The object to check for the annotation
     *
     * @param property
     *  The name of the property to check
     *
     * @param annotation
     *  The annotation for which to check
     *
     * @return
     *  True if the annotation is present either on the class, the property or the property's
     *  accessor; false otherwise.
     */
    protected boolean annotationPresent(Object obj, String property, Class<? extends Annotation> annotation) {
        // Check for the annotation on the class...
        if (obj.getClass().getAnnotation(annotation) != null) {
            return true;
        }

        // Check the property field
        try {
            Field field = obj.getClass().getField(property);

            if (field.getAnnotation(annotation) != null) {
                return true;
            }
        }
        catch (NoSuchFieldException e) {
            // Nope. Move on to the accessor check.
        }

        // Check the accessor
        String[] prefixes = { "get", "is" };
        property = property.substring(0, 1).toUpperCase() + property.substring(1);

        for (String prefix : prefixes) {
            try {
                Method method = obj.getClass().getMethod(prefix + property);

                if (method.getAnnotation(annotation) != null) {
                    return true;
                }
            }
            catch (NoSuchMethodException e) {
                // Doesn't exist.
            }
        }

        return false;
    }
}
