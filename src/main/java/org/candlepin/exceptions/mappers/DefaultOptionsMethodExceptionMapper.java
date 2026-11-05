/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.exceptions.mappers;

import org.candlepin.exceptions.ExceptionMessage;

import org.jboss.resteasy.spi.DefaultOptionsMethodException;

import java.util.List;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * DefaultOptionsMethodExceptionMapper maps the RESTEasy
 * DefaultOptionsMethodException into JSON and allows the proper header to be
 * set. This allows Candlepin to control the flow of the exceptions.
 */
@Provider
public class DefaultOptionsMethodExceptionMapper extends
    CandlepinExceptionMapper implements
    ExceptionMapper<DefaultOptionsMethodException> {

    private static final List<String> ENTITY_HEADERS = List.of(
        HttpHeaders.CONTENT_LENGTH,
        HttpHeaders.CONTENT_ENCODING,
        HttpHeaders.CONTENT_LANGUAGE,
        HttpHeaders.CONTENT_LOCATION,
        "Content-Range",
        "Content-Disposition",
        HttpHeaders.ETAG,
        HttpHeaders.LAST_MODIFIED,
        "Content-MD5",
        "Digest",
        "Content-Digest",
        "Repr-Digest"
    );

    @Override
    public Response toResponse(DefaultOptionsMethodException exception) {
        Response resp = exception.getResponse();
        if (resp != null) {
            if (resp.getStatus() < Response.Status.BAD_REQUEST.getStatusCode()) {
                return resp;
            }

            Response.Status status = Response.Status.fromStatusCode(resp.getStatus());
            String message = status != null ? this.getStatusMessage(status) :
                this.i18n.get().tr("Request failed with status code {0}", resp.getStatus());
            ResponseBuilder builder = Response.fromResponse(resp)
                .entity(new ExceptionMessage(message))
                .type(this.determineBestMediaType());

            // These headers describe the original entity and cannot describe the replacement error body.
            for (String header : ENTITY_HEADERS) {
                builder.header(header, null);
            }

            return builder.build();
        }

        return this.getDefaultBuilder(exception, Response.Status.INTERNAL_SERVER_ERROR)
            .entity(new ExceptionMessage(this.i18n.get().tr("Internal Server Error")))
            .build();
    }
}
