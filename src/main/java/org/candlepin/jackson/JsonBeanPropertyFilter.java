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
package org.candlepin.jackson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * JsonBeanPropertyFilter
 */
public abstract class JsonBeanPropertyFilter extends CheckableBeanPropertyFilter {

    private static Logger log = LoggerFactory.getLogger(JsonBeanPropertyFilter.class);

    protected Boolean annotationPresent(Object obj, String propertyName,
        Class<? extends Annotation> clazz) {
        try {
            String postFix = propertyName.substring(0, 1).toUpperCase() +
                propertyName.substring(1);
            String methodName = "get" + postFix;
            Method getter = null;
            try {
                getter = obj.getClass().getMethod(methodName);
            }
            catch (NoSuchMethodException e) {
                // Look for common boolean pattern of "is"
                // instead of "get"
                methodName = "is" + postFix;
                getter = obj.getClass().getMethod(methodName);
            }
            Annotation a = getter.getAnnotation(clazz);
            if (a != null) {
                return true;
            }
        }
        catch (NoSuchMethodException e) {
            log.warn("Unable to serialize property '" + propertyName +
                " without a getter");
            return false;
        }
        return false;
    }

}
