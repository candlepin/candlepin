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
package org.candlepin.util;

import java.lang.reflect.Type;
import java.util.Set;

import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.introspect.BasicBeanDescription;
import org.codehaus.jackson.map.ser.BeanSerializer;
import org.codehaus.jackson.map.ser.BeanSerializerModifier;

/**
 * NonRecursiveBeanSerializerModifer
 */
class NonRecursiveBeanSerializerModifer extends BeanSerializerModifier {

    private Set<Type> seenClasses;

    NonRecursiveBeanSerializerModifer() {
        seenClasses = Util.newSet();
    }

    void resetSeen() {
        seenClasses.clear();
    }

    @Override
    public JsonSerializer<?> modifySerializer(SerializationConfig config,
        BasicBeanDescription beanDesc, JsonSerializer<?> serializer) {
        if (serializer instanceof BeanSerializer) {
            return new NonRecursiveBeanSerializer((BeanSerializer) serializer, seenClasses);
        }
        return serializer;
    }
}
