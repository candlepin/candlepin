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


import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.ser.BeanPropertyWriter;

/**
 * ExportBeanPropertyFilter: A jackson filter used during creation of exports.
 * It looks for any fields on objects with the @SkipOnExport annotation, and skips them.
 */
public class ExportBeanPropertyFilter extends JsonBeanPropertyFilter {

    @Override
    public void serializeAsField(Object obj, JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider, BeanPropertyWriter writer) throws Exception {
        if (!annotationPresent(obj, writer.getName(), SkipExport.class)) {
            writer.serializeAsField(obj, jsonGenerator, serializerProvider);
        }
    }

}
