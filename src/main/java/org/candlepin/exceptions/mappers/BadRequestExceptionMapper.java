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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.apache.commons.lang.StringUtils;
import org.candlepin.exceptions.ExceptionMessage;
import org.candlepin.exceptions.CandlepinParamterParseException;
import org.candlepin.util.VersionUtil;
import org.jboss.resteasy.spi.BadRequestException;

/**
 * BadRequestExceptionMapper maps the RESTEasy BadRequestException
 * into JSON and allows the proper header to be set. This allows
 * Candlepin to control the flow of the exceptions.
 */
@Provider
public class BadRequestExceptionMapper extends CandlepinExceptionMapper
    implements ExceptionMapper<BadRequestException> {

    private static final Pattern PARAM_REGEX = Pattern.compile(
        "(?:javax\\.ws\\.rs\\.\\w+\\(\\\")([\\w\\s]+)(\\\"\\))");

    private static final Pattern ILLEGAL_VAL_REGEX = Pattern.compile(
        ":?value\\sis\\s'([\\w\\s]+)(:?'\\sfor)");

    @Override
    public Response toResponse(BadRequestException exception) {
        Map<String, String> map = VersionUtil.getVersionMap();
        ResponseBuilder bldr = Response.status(Status.BAD_REQUEST).type(
            determineBestMediaType()).header(VersionUtil.VERSION_HEADER,
                map.get("version") + "-" + map.get("release"));

        Throwable cause = exception.getCause();
        if (cause instanceof CandlepinParamterParseException) {
            String msg = i18n.tr("Invalid format for query parameter {0}. " +
                "Expected format: {1}",
                ((CandlepinParamterParseException) cause).getParamName(),
                ((CandlepinParamterParseException) cause).getExpectedFormat());
            bldr.entity(new ExceptionMessage(msg));
        }
        else {
            String msg = exception.getMessage();
            if (StringUtils.isNotEmpty(msg)) {
                bldr.entity(new ExceptionMessage(extractIllegalValue(msg)));
            }
        }
        return bldr.build();
    }

    private String extractIllegalValue(String msg) {
        Matcher paramMatcher = PARAM_REGEX.matcher(msg);
        Matcher illegalValMatcher = ILLEGAL_VAL_REGEX.matcher(msg);
        if (paramMatcher.find() && illegalValMatcher.find()) {
            if ((paramMatcher.groupCount() & illegalValMatcher.groupCount()) == 2) {
                return i18n.tr("{0} is not a valid value for {1}",
                    illegalValMatcher.group(1), paramMatcher.group(1));
            }
        }
        return i18n.tr("Bad Request");
    }

}
