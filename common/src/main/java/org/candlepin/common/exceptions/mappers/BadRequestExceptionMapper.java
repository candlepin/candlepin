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
package org.candlepin.common.exceptions.mappers;

import org.candlepin.common.exceptions.ExceptionMessage;
import org.candlepin.common.util.JaxRsExceptionResponseBuilder;
import org.candlepin.common.util.VersionUtil;

import org.jboss.resteasy.spi.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xnap.commons.i18n.I18n;

import java.util.Map;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;



/**
 * BadRequestExceptionMapper maps the RESTEasy BadRequestException into JSON and
 * allows the proper header to be set. This allows Candlepin to control the flow
 * of the exceptions.
 */
@Component
@Provider
public class BadRequestExceptionMapper extends CandlepinExceptionMapper
    implements ExceptionMapper<BadRequestException> {

    /**
     * Service that handles JAX-RS exceptions.
     */
    @Autowired
    private JaxRsExceptionResponseBuilder badQueryParamHandler;

    @Override
    public final Response toResponse(final BadRequestException exception) {
        if (badQueryParamHandler.canHandle(exception)) {
            return badQueryParamHandler.getResponse(exception);
        }
        else {
            // This is here to preserve old functionality.
            // It may be just as good to use getDefaultBuilder here
            Map<String, String> map = VersionUtil.getVersionMap();
            ResponseBuilder bldr = Response.status(Status.BAD_REQUEST)
                .type(determineBestMediaType())
                .header(VersionUtil.VERSION_HEADER, map.get("version") + "-" + map.get("release"));

            // Use the message in the original exception if we have it
            String message = null;
            for (Throwable current = exception; current != null; current = current.getCause()) {
                message = current.getMessage();
            }

            // Set default message if we didn't have anything...
            if (message == null || message.isEmpty()) {
                message = I18n.marktr("Bad Request");
            }

            return bldr.entity(new ExceptionMessage(i18n.get().tr(message))).build();
        }
    }
}
