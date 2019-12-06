/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.common.resource.exceptions.mappers;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;


/**
 * BadRequestExceptionMapper maps the RESTEasy BadRequestException into JSON and
 * allows the proper header to be set. This allows Candlepin to control the flow
 * of the exceptions.
 */
@Provider
public class IllegalArgumentExceptionMapper extends CandlepinExceptionMapper
    implements ExceptionMapper<IllegalArgumentException> {

    @Override
    public final Response toResponse(final IllegalArgumentException exception) {
        return getDefaultBuilder(exception, Status.BAD_REQUEST,
            determineBestMediaType()).build();
    }

}
