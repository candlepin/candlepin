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
package org.candlepin.policy.js;

import org.candlepin.jackson.JsonBeanPropertyFilter;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonStreamContext;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.ser.BeanPropertyWriter;

/**
 * RulesBeanPropertyFilter: A Jackson filter for excluding certain properties when
 * serializing objects for passing in to the rules.
 */
public class RulesBeanPropertyFilter extends JsonBeanPropertyFilter {

    @Override
    public void serializeAsField(Object obj, JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider, BeanPropertyWriter writer) throws Exception {
        JsonStreamContext context = jsonGenerator.getOutputContext();

        // If annotated with rules exclude, we skip this property:
        if (!annotationPresent(obj, writer.getName(), RulesExclude.class)) {
            writer.serializeAsField(obj, jsonGenerator, serializerProvider);
        }
    }
}
