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
package org.fedoraproject.candlepin.exceptions;

import static org.jboss.resteasy.util.MediaTypeHelper.*;

import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.jboss.resteasy.util.HttpHeaderNames;

import com.google.inject.Inject;
import com.google.inject.Injector;


/**
 * BadRequestExceptionMapper
 */
@Provider
public class CandlepinExceptionMapper implements ExceptionMapper<CandlepinException> {
    
    private static final List<MediaType> DESIRED_RESPONSE_TYPES =
        new LinkedList<MediaType>() {
            {
                add(MediaType.APPLICATION_JSON_TYPE);
                add(MediaType.APPLICATION_XML_TYPE);
                add(MediaType.TEXT_PLAIN_TYPE);
            }
        };
    
    @Inject private Injector injector;
    
    @Override
    public Response toResponse(CandlepinException exception) {
        
        HttpServletRequest request = injector.getInstance(HttpServletRequest.class);
        
        String header = request.getHeader(HttpHeaderNames.ACCEPT);
        MediaType responseMediaType = MediaType.APPLICATION_XML_TYPE;
        if (header != null) {     
            List<MediaType> headerMediaTypes = parseHeader(header);
            
            responseMediaType = 
                headerMediaTypes.size() == 0 ? 
                MediaType.TEXT_PLAIN_TYPE : 
                getBestMatch(DESIRED_RESPONSE_TYPES, headerMediaTypes);
        }
               
        return Response.status(exception.httpReturnCode())
            .entity(exception.message())
            .type(responseMediaType)
            .build();        
    }
}
