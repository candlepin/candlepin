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
package org.fedoraproject.candlepin.resteasy;

import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.introspect.BasicBeanDescription;
import org.codehaus.jackson.map.ser.CustomSerializerFactory;
import org.codehaus.jackson.map.type.TypeFactory;
import org.codehaus.jackson.type.JavaType;

/**
 * CandlepinSerializerFactory
 */
public class CandlepinSerializerFactory extends CustomSerializerFactory {
    
    /* 
     * Copied directly from BeanSerializerFactory. This is technically the grandparent 
     * class to this one, but the method is hidden by implementation in 
     * CustomSerializerFactory, which we need to avoid. 
     */
    public JsonSerializer<Object> createSerializerSkipCustom(
        Class type, SerializationConfig config) {
        
        /* [JACKSON-220]: Very first thing, let's check annotations to
         * see if we have explicit definition
         */
        JavaType jt = TypeFactory.type(type);
        BasicBeanDescription beanDesc = config.introspect(jt.getRawClass());
        JsonSerializer<?> ser = findSerializerFromAnnotation(config, 
            beanDesc.getClassInfo());
        if (ser == null) {
            // First, fast lookup for exact type:
            ser = super.findSerializerByLookup(jt, config, beanDesc);
            if (ser == null) {
                // and then introspect for some safe (?) JDK types
                ser = super.findSerializerByPrimaryType(jt, config, beanDesc);
                if (ser == null) {
                    /* And this is where this class comes in: if type is
                     * not a known "primary JDK type", perhaps it's a bean?
                     * We can still get a null, if we can't find a single
                     * suitable bean property.
                     */
                    ser = this.findBeanSerializer(jt, config, beanDesc);
                    /* Finally: maybe we can still deal with it as an
                     * implementation of some basic JDK interface?
                     */
                    if (ser == null) {
                        ser = super.findSerializerByAddonType(jt, config, beanDesc);
                    }
                }
            }
        }
        return (JsonSerializer<Object>) ser;
    }
}
