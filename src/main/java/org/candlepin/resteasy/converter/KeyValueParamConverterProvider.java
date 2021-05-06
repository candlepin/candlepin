/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.resteasy.converter;

import org.candlepin.common.exceptions.CandlepinParameterParseException;
import org.candlepin.dto.api.v1.KeyValueParamDTO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

/**
 * ParamConverterProvider to enable use of {@link KeyValueParamDTO} in query parameters.
 */
@Provider
public class KeyValueParamConverterProvider implements ParamConverterProvider {

    @Override
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType.isAssignableFrom(KeyValueParamDTO.class)) {
            return (ParamConverter<T>) new KeyValueParamConverter();
        }
        return null;
    }

    /**
     * ParamConverter that parses {@link KeyValueParamDTO} to/from String.
     */
    public static class KeyValueParamConverter implements ParamConverter<KeyValueParamDTO> {

        private static final Logger log = LoggerFactory.getLogger(KeyValueParamConverter.class);

        @Override
        public KeyValueParamDTO fromString(String inValue) {
            log.trace("Parsing key-value param from: {}", inValue);
            if (inValue == null) {
                return null;
            }
            // Maximum of two parts
            String[] parts = inValue.split(":", 2);
            if (parts.length <= 1) {
                throw new CandlepinParameterParseException("name:value");
            }

            return new KeyValueParamDTO()
                .key(parts[0])
                .value(parts[1]);
        }

        @Override
        public String toString(KeyValueParamDTO value) {
            if (value == null) {
                return null;
            }
            return String.format("%s:%s", value.getKey(), value.getValue());
        }
    }
}
