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

import org.candlepin.exceptions.CandlepinException;
import org.candlepin.util.VersionUtil;

import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * RuntimeExceptionMapper captures all other RuntimeExceptions (especially
 * CandlepinException) and maps into JSON and allows the proper header to be
 * set. This allows Candlepin to control the flow of the exceptions.
 * Unfortunately, because of the way RESTEasy handles exceptions having
 * just this ExceptionMapper is not enough which is why we have the other.
 */
@Provider
public class RuntimeExceptionMapper extends CandlepinExceptionMapper
    implements ExceptionMapper<RuntimeException> {

    @Override
    public Response toResponse(RuntimeException exception) {

        MediaType responseMediaType = determineBestMediaType();

        ResponseBuilder bldr = null;
        // Resteasy wraps the actual exception sometimes
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        if (cause instanceof CandlepinException) {
            bldr = getBuilder((CandlepinException) cause, responseMediaType);
        }
        else if (exception instanceof CandlepinException) {
            bldr = getBuilder((CandlepinException) exception, responseMediaType);
        }
        else {
            bldr = getDefaultBuilder(cause, null, responseMediaType);
        }

        return bldr.build();
    }

    protected ResponseBuilder getBuilder(CandlepinException exception,
        MediaType responseMediaType) {

        ResponseBuilder bldr = Response.status(exception.httpReturnCode())
            .entity(exception.message()).type(responseMediaType);
        Map<String, String> map = VersionUtil.getVersionMap();

        bldr.header(VersionUtil.VERSION_HEADER,
            map.get("version") + "-" + map.get("release"));

        for (Map.Entry<String, String> hdr : exception.headers().entrySet()) {
            bldr.header(hdr.getKey(), hdr.getValue());
        }

        return bldr;
    }
}
