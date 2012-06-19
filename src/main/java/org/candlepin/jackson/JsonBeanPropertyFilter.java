/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.jackson;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ser.BeanPropertyFilter;

/**
 * JsonBeanPropertyFilter
 */
public abstract class JsonBeanPropertyFilter implements BeanPropertyFilter {

    private static Logger log = Logger.getLogger(JsonBeanPropertyFilter.class);

    protected Boolean annotationPresent(Object obj, String propertyName,
        Class<? extends Annotation> clazz) {
        String methodName = "get" + propertyName.substring(0, 1).toUpperCase() +
            propertyName.substring(1);
        try {
            Method getter = obj.getClass().getMethod(methodName);
            Annotation a = getter.getAnnotation(clazz);
            if (a != null) {
                return true;
            }
        }
        catch (NoSuchMethodException e) {
            log.warn("Unable to serialize property '" + propertyName +
                " without getter: " + methodName);
            return false;
        }
        return false;
    }

}
