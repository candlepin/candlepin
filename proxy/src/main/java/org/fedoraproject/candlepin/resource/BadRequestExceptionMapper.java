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
package org.fedoraproject.candlepin.resource;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.jboss.resteasy.util.HttpHeaderNames;


/**
 * BadRequestExceptionMapper
 */

@Provider
public class BadRequestExceptionMapper implements ExceptionMapper<BadRequestException> {
    
//    @HeaderParam(HttpHeaderNames.CONTENT_TYPE)
//    private MediaType contentType;

    @Override
    public Response toResponse(BadRequestException exception) {
        return Response.status(Status.BAD_REQUEST)
            .entity(exception.message())
            .type(MediaType.APPLICATION_JSON)
            .build();        
    }
}
