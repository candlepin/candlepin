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
package org.candlepin.resteasy;

import org.apache.commons.lang3.StringUtils;
import org.jboss.resteasy.spi.StringParameterUnmarshaller;
import org.jboss.resteasy.spi.util.FindAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.lang.annotation.Annotation;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.bind.DatatypeConverter;



/**
 * Formatter for parsing dates for parameters annotated with the DateFormat annotation.
 */
public class DateFormatter implements StringParameterUnmarshaller<Date> {
    private static Logger log = LoggerFactory.getLogger(DateFormatter.class);

    private String[] formats;

    public void setAnnotations(Annotation[] annotations) {
        DateFormat annotation = FindAnnotation.findAnnotation(annotations, DateFormat.class);

        this.formats = annotation.value();
    }

    public Date fromString(String value) {
        // If we're null/empty, we have nothing to parse, so return null
        if (!StringUtils.isBlank(value)) {
            // If the value is our special value for "now", return a new Date instance
            if (DateFormat.NOW.equals(value)) {
                return new Date();
            }

            // If we have any formats specified, use those...
            if (this.formats != null && this.formats.length > 0) {
                for (String format : this.formats) {
                    log.debug("Attempting to parse date \"{}\" using format: {}", value, format);

                    try {
                        SimpleDateFormat formatter = new SimpleDateFormat(format);
                        return formatter.parse(value);
                    }
                    catch (ParseException exception) {
                        // Whoops. Hopefully we have more formats to try...
                        log.debug("Unable to parse date \"{}\" with format {}", value, format, exception);
                    }
                }

                // If we made it here, we're out of formats to use to parse the date
                log.debug("Unable to parse date: {}", value);

                // TODO: If we ever work around the difficulty of getting an I18n instance at this
                // point, translate this directly with the value in the error message.
                throw new RuntimeException(I18n.marktr("Unable to parse date parameter"));
            }
            else {
                try {
                    log.debug("Attempting to parse date \"{}\" using ISO8601 date converter", value);

                    // Use the DatatypeConverter to parse the date, as it accepts dates in ISO8601
                    return DatatypeConverter.parseDateTime(value).getTime();
                }
                catch (IllegalArgumentException exception) {
                    log.debug("Unable to parse date \"{}\" using ISO8601 date converter", value, exception);

                    // TODO: Need more translation stuff here
                    throw new RuntimeException(I18n.marktr("Unable to parse date parameter"));
                }
            }
        }

        return null;
    }
}
