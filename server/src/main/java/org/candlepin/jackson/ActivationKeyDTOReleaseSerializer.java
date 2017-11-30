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

/**
 * The ActivationKeyDTOReleaseSerializer handles the serialization of ActivationKeyDTO
 * field ReleaseVersion, writing it in the format of:
 * <pre> {@code "releaseVer":{"releaseVer":"string"} } </pre>
 */
public class ActivationKeyDTOReleaseSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String releaseVersion, JsonGenerator generator, SerializerProvider provider)
        throws IOException {

        generator.writeStartObject();
        generator.writeObjectField("releaseVer", releaseVersion);
        generator.writeEndObject();
    }
}
