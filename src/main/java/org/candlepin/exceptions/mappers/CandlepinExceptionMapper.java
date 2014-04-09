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

import static org.jboss.resteasy.util.MediaTypeHelper.getBestMatch;
import static org.jboss.resteasy.util.MediaTypeHelper.parseHeader;

import org.candlepin.exceptions.ExceptionMessage;
import org.candlepin.util.VersionUtil;

import com.google.inject.Inject;

import org.jboss.resteasy.util.HttpHeaderNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;


/**
 * CandlepinExceptionMapper
 */
public class CandlepinExceptionMapper {
    private static final List<MediaType> DESIRED_RESPONSE_TYPES =
        new LinkedList<MediaType>() {
            {
                add(MediaType.APPLICATION_JSON_TYPE);
                add(MediaType.TEXT_PLAIN_TYPE);
                add(MediaType.APPLICATION_ATOM_XML_TYPE);
            }
        };

    // Use a provider so we get a scoped HttpServletRequest
    @Inject
    private javax.inject.Provider<HttpServletRequest> requestProvider;

    @Inject
    protected I18n i18n;

    private static Logger log = LoggerFactory.getLogger(CandlepinExceptionMapper.class);

    public MediaType determineBestMediaType() {
        HttpServletRequest request = requestProvider.get();

        String header = request.getHeader(HttpHeaderNames.ACCEPT);

        MediaType mediaType = null;

        if (header != null) {
            List<MediaType> headerMediaTypes = parseHeader(header);

            mediaType = headerMediaTypes.size() == 0 ? MediaType.TEXT_PLAIN_TYPE :
                getBestMatch(DESIRED_RESPONSE_TYPES, headerMediaTypes);
        }

        if (mediaType == null || (mediaType.getType().equals("*") &&
                mediaType.getSubtype().equals("*"))) {
            mediaType = MediaType.APPLICATION_JSON_TYPE;
        }

        return mediaType;
    }

    protected ResponseBuilder getDefaultBuilder(Throwable exception,
        Status status, MediaType responseMediaType) {

        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        String message = "";

        StackTraceElement[] stes = cause.getStackTrace();

        // 1051215: the exception may not have a stacktrace, rare but could
        // happen which breaks things.
        if (stes != null && stes.length > 0) {
            StackTraceElement ele = stes[0];
            int line = ele.getLineNumber();
            String method = ele.getMethodName();
            String clazz = ele.getClassName();
            message = i18n.tr("Runtime Error {0} at {1}.{2}:{3}",
                exception.getMessage(), clazz, method, line);
        }
        else {
            // just use the exception message
            message = i18n.tr("Runtime Error {0}", exception.getMessage());
        }

        log.error(message, exception);
        if (status == null) {
            status = Status.INTERNAL_SERVER_ERROR;
        }

        Map<String, String> map = VersionUtil.getVersionMap();

        return Response.status(status)
            .entity(new ExceptionMessage(message))
            .type(responseMediaType)
            .header(VersionUtil.VERSION_HEADER,
                map.get("version") + "-" + map.get("release"));
    }
}
