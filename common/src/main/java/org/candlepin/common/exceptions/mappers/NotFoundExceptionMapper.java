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

import org.candlepin.common.util.JaxRsExceptionResponseBuilder;

import com.google.inject.Inject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * NotFoundExceptionMapper maps the RESTEasy NotFoundException into JSON and
 * allows the proper header to be set. This allows Candlepin to control the flow
 * of the exceptions.
 */
@Component
@Provider
public class NotFoundExceptionMapper extends CandlepinExceptionMapper
    implements ExceptionMapper<NotFoundException> {

    /**
     * Service that handles JAX-RS exceptions.
     */
    @Autowired
    private JaxRsExceptionResponseBuilder badQueryParamHandler;

    // notice this takes in a resteasy NFE *NOT* the candlepin one.
    @Override
    public final Response toResponse(final NotFoundException exception) {
        if (badQueryParamHandler.canHandle(exception)) {
            return badQueryParamHandler.getResponse(exception);
        }

        return getDefaultBuilder(exception, Response.Status.NOT_FOUND,
                determineBestMediaType()).build();
    }

}
