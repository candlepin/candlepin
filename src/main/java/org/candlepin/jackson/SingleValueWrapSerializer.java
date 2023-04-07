/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * The SingleValueWrapSerializer handles the serialization of wrapping a single field
 * in a JSON object/single-value-map, in the format of:
 * <pre> {@code "fieldName":"value" } </pre>
 *
 * Classes that extend this class should pass the name of the field they need to wrap
 * as an argument to the super constructor.
 */
public abstract class SingleValueWrapSerializer extends JsonSerializer<String> {

    private String fieldName;

    public SingleValueWrapSerializer(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public void serialize(String fieldValue, JsonGenerator generator, SerializerProvider provider)
        throws IOException {

        generator.writeStartObject();
        generator.writeObjectField(fieldName, fieldValue);
        generator.writeEndObject();
    }
}
