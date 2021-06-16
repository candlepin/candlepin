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
package org.candlepin.exceptions.mappers;

import org.jboss.resteasy.plugins.providers.jaxb.JAXBMarshalException;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * JAXBMarshalExceptionMapper maps the RESTEasy JAXBMarshalException
 * into JSON and allows the proper header to be set. This allows
 * Candlepin to control the flow of the exceptions.
 */
@Provider
public class JAXBMarshalExceptionMapper extends CandlepinExceptionMapper
    implements ExceptionMapper<JAXBMarshalException> {

    @Override
    public Response toResponse(JAXBMarshalException exception) {
        Status status = Response.Status.INTERNAL_SERVER_ERROR;
        if (exception.getResponse() != null) {
            status = Response.Status.fromStatusCode(
                exception.getResponse().getStatus());
        }
        return getDefaultBuilder(exception, status, determineBestMediaType()).build();
    }

}
