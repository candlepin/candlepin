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
package org.candlepin.common.util;

import org.candlepin.common.exceptions.CandlepinParameterParseException;
import org.candlepin.common.exceptions.ExceptionMessage;
import org.candlepin.common.exceptions.mappers.CandlepinExceptionMapper;

import com.google.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.xnap.commons.i18n.I18n;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Provider;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

/**
 * Class is able to detect whether an exception contains error related to wrong
 * parameters in an JAX-RS resource. In addition to that this class is able to
 * build an http error response for that exception. Historical notes: This had
 * to be extracted from BadRequestExceptionMapper, because the new RestEasy
 * changed the way it handles (now throws NotFoundException) unparseable
 * parameters of JAX-RS resources.
 *
 * @author fnguyen
 */
public class JaxRsExceptionResponseBuilder {

    /**
     * Regex for JAX-RS exception class names.
     */
    private static final Pattern PARAM_REGEX = Pattern
            .compile("(?:javax\\.ws\\.rs\\.\\w+\\(\\\")([\\w\\s]+)(\\\"\\))");

    /**
     * Regex to extract the errored values from the JAX-RS Exception.
     */
    private static final Pattern ILLEGAL_VAL_REGEX = Pattern
            .compile(":?value\\sis\\s'([\\w\\s]+)(:?'\\sfor)");

    /**
     * Service for i18n.
     */
    @Inject
    private Provider<I18n> i18n;

    /**
     * Just to get access to determineBestMediaType() without having to doing
     * inheritance.
     */
    @Inject
    private CandlepinExceptionMapper cem;

    /**
     * This method inspects exception and decides whether it relates to wrong
     * parameters passed to a JAX-RS resource method.
     * @param exception
     *        JAX-RS implementation generated exception.
     * @return True if and only if the exception contains error regarding JAX-RS
     *         parameters
     */
    public final boolean canHandle(final Exception exception) {
        Throwable cause = exception.getCause();

        if (cause instanceof CandlepinParameterParseException) {
            return true;
        }
        String msg = exception.getMessage();

        if (StringUtils.isNotEmpty(msg)) {
            Matcher paramMatcher = PARAM_REGEX.matcher(msg);
            Matcher illegalValMatcher = ILLEGAL_VAL_REGEX.matcher(msg);
            if (paramMatcher.find() && illegalValMatcher.find()) {
                if ((paramMatcher.groupCount() == 2) &&
                        (illegalValMatcher.groupCount() == 2)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Exceptions are thrown by the JAX-RS implementation when problems such as
     * incorrect types of parameters for JAX-RS resources are passed to a
     * method. Example would be string instead of a number passed as a
     * parameter. This method takes such an exception and builds pretty
     * undestandable HTTP resoponse for the client.
     *
     * @param exception
     *        JAX-RS implementation generated exception.
     * @return Response for the client
     */
    public final Response getResponse(final Exception exception) {
        if (!canHandle(exception)) {
            throw new IllegalArgumentException(
                    "ResponseBuilder cannot handle this exception, you should call canHandle first",
                    exception);
        }

        Map<String, String> map = VersionUtil.getVersionMap();
        ResponseBuilder bldr = Response.status(Status.BAD_REQUEST)
                .type(cem.determineBestMediaType())
                .header(VersionUtil.VERSION_HEADER,
                        map.get("version") + "-" + map.get("release"));

        Throwable cause = exception.getCause();
        if (cause instanceof CandlepinParameterParseException) {
            String msg = i18n.get()
                    .tr("Invalid format for query parameter {0}. " +
                            "Expected format: {1}",
                    ((CandlepinParameterParseException) cause).getParamName(),
                    ((CandlepinParameterParseException) cause)
                            .getExpectedFormat());
            bldr.entity(new ExceptionMessage(msg));
        }
        else {
            String msg = exception.getMessage();
            Matcher paramMatcher = PARAM_REGEX.matcher(msg);
            Matcher illegalValMatcher = ILLEGAL_VAL_REGEX.matcher(msg);
            paramMatcher.find();
            illegalValMatcher.find();
            String errorMessage = i18n.get().tr(
                    "{0} is not a valid value for {1}",
                    illegalValMatcher.group(1), paramMatcher.group(1));
            bldr.entity(new ExceptionMessage(errorMessage));
        }
        return bldr.build();
    }

}
