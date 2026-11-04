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
import org.candlepin.exceptions.util.JaxRsExceptionResponseBuilder;

import org.jboss.resteasy.spi.BadRequestException;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;



/**
 * BadRequestExceptionMapper maps the RESTEasy BadRequestException into JSON and
 * allows the proper header to be set. This allows Candlepin to control the flow
 * of the exceptions.
 */
@Provider
public class BadRequestExceptionMapper extends CandlepinExceptionMapper
    implements ExceptionMapper<BadRequestException> {

    /**
     * Service that handles JAX-RS exceptions.
     */
    @Inject
    private JaxRsExceptionResponseBuilder badQueryParamHandler;

    @Override
    public final Response toResponse(final BadRequestException exception) {
        if (this.badQueryParamHandler.canHandle(exception)) {
            return this.badQueryParamHandler.getResponse(exception);
        }

        return this.getDefaultBuilder(exception, Status.BAD_REQUEST)
            .entity(new ExceptionMessage(this.i18n.get().tr("Bad Request")))
            .build();
    }
}
