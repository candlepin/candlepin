/**
 * Copyright (c) 2009 Red Hat, Inc.
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

import org.jboss.resteasy.spi.ApplicationException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * ApplicationExceptionMapper maps the RESTEasy ApplicationException
 * into JSON and allows the proper header to be set. This allows
 * Candlepin to control the flow of the exceptions.
 */
@Provider
public class ApplicationExceptionMapper extends CandlepinExceptionMapper
    implements ExceptionMapper<ApplicationException> {

    @Override
    public Response toResponse(ApplicationException exception) {
        return getDefaultBuilder(exception, null, determineBestMediaType()).build();
    }
}
