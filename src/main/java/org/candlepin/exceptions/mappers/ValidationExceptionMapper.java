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

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.ValidationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.candlepin.exceptions.ExceptionMessage;
import org.candlepin.util.VersionUtil;

/**
 * ValidationExceptionMapper maps the RESTEasy BadRequestException
 * into JSON and allows the proper header to be set. This allows
 * Candlepin to control the flow of the exceptions.
 */
@Provider
public class ValidationExceptionMapper extends CandlepinExceptionMapper
    implements ExceptionMapper<ValidationException> {

    @Override
    public Response toResponse(ValidationException exception) {
        Map<String, String> map = VersionUtil.getVersionMap();
        ResponseBuilder bldr = Response.status(Status.BAD_REQUEST).type(
            determineBestMediaType()).header(VersionUtil.VERSION_HEADER,
                map.get("version") + "-" + map.get("release"));

        StringBuffer message = new StringBuffer();
        if (ConstraintViolationException.class.isAssignableFrom(exception.getClass())) {
            for (ConstraintViolation cv :
                ((ConstraintViolationException) exception).getConstraintViolations()) {
                message.append(cv.getPropertyPath().toString());
                message.append(": ");
                message.append(cv.getMessage());
            }
            bldr.entity(new ExceptionMessage(message.toString()));
        }
        else {
            getDefaultBuilder(exception, Response.Status.BAD_REQUEST, determineBestMediaType());
        }
        return bldr.build();
    }
}
