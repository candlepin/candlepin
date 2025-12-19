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
package org.candlepin.exceptions.mappers;

import org.jboss.resteasy.spi.ReaderException;

import tools.jackson.databind.DatabindException;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;


/**
 * ReaderExceptionMapper maps the RESTEasy ReaderException into JSON and allows
 * the proper header to be set. This allows Candlepin to control the flow of
 * the exceptions.
 */
@Provider
public class ReaderExceptionMapper extends CandlepinExceptionMapper
    implements ExceptionMapper<ReaderException> {

    @Override
    public Response toResponse(ReaderException exception) {
        Status status = null;

        if (exception.getResponse() != null) {
            status = Response.Status.fromStatusCode(exception.getResponse().getStatus());
        }

        // Impl note:
        // JsonMappingExceptions that occur as a result of user input are wrapped in a
        // ReaderException. We'll have to step through the exception chain and see if we find a
        // mapping exception (should be at the first iteration). If not, we'll just use our
        // default below.
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof DatabindException) {
                status = Response.Status.BAD_REQUEST;
                break;
            }
        }

        if (status == null) {
            status = Response.Status.INTERNAL_SERVER_ERROR;
        }

        return this.getDefaultBuilder(exception, status, determineBestMediaType()).build();
    }
}
