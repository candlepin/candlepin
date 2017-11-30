/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
import java.util.Set;

/**
 * The ActivationKeyDTOProductSerializer handles the serialization of ActivationKeyDTO product sets,
 * writing them in the format of:
 * <pre> {@code [{"productId":"value1"}, ... ,{"productId":"valueN"}] } </pre>
 */
public class ActivationKeyDTOProductSerializer extends JsonSerializer<Set<String>> {

    @Override
    public void serialize(Set<String> products, JsonGenerator generator, SerializerProvider provider)
        throws IOException {

        generator.writeStartArray();

        if (products != null && !products.isEmpty()) {
            for (String productId : products) {
                if (productId != null && !productId.isEmpty()) {
                    generator.writeStartObject();
                    generator.writeObjectField("productId", productId);
                    generator.writeEndObject();
                }
            }
        }

        generator.writeEndArray();
    }
}
