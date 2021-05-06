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

import org.candlepin.jackson.OffsetDateTimeDeserializer;
import org.candlepin.util.Util;

import org.jboss.resteasy.spi.util.FindAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;

import javax.validation.constraints.Pattern;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

/**
 * ParamConverterProvider to enable use of OffsetDateTime in query parameters.
 */
@Provider
public class OffsetDateTimeParamConverterProvider implements ParamConverterProvider {

    @Override
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType.isAssignableFrom(OffsetDateTime.class)) {
            return (ParamConverter<T>) new OffsetDateTimeParamConverter(annotations);
        }
        return null;
    }

    /**
     * ParamConverter that uses an ObjectMapper to convert OffsetDateTime to/from String.
     */
    public static class OffsetDateTimeParamConverter implements ParamConverter<OffsetDateTime> {

        private static Logger log = LoggerFactory.getLogger(OffsetDateTimeParamConverter.class);
        private String NOW = "now";
        private String pattern;
        private OffsetDateTimeDeserializer offsetDateTimeDeserializer;

        public OffsetDateTimeParamConverter(Annotation[] annotations) {
            Pattern annotation = FindAnnotation.findAnnotation(annotations, Pattern.class);

            if (annotation != null) {
                this.pattern = annotation.regexp();
            }
            this.offsetDateTimeDeserializer = new OffsetDateTimeDeserializer();
        }

        @Override
        public OffsetDateTime fromString(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            if (NOW.equals(value)) {
                return OffsetDateTime.now();
            }

            try {
                if (this.pattern != null && !this.pattern.isBlank()) {
                    DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern(pattern)
                        .toFormatter();
                    return Util.parseOffsetDateTime(formatter, value);
                }
                else {
                    return this.offsetDateTimeDeserializer.deserialize(value);
                }
            }
            catch (DateTimeParseException e) {
                log.debug("Unable to parse date \"{}\" with pattern {}", value, pattern, e);
                throw new RuntimeException(I18n.marktr("Unable to parse date parameter"));
            }
        }

        @Override
        public String toString(OffsetDateTime value) {
            if (value == null) {
                return null;
            }
            return value.toString();
        }
    }
}
