/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

import org.candlepin.exceptions.ExceptionMessage;

import jakarta.inject.Inject;
import jakarta.persistence.RollbackException;
import jakarta.validation.ValidationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * When validation fails, Hibernate can throw RollbackExceptions.  This mapper takes
 * those exceptions, opens them up, and if the cause is a ValidationException, it
 * delegates to the ValidationExceptionMapper.
 */
@Provider
public class RollbackExceptionMapper extends CandlepinExceptionMapper
    implements ExceptionMapper<RollbackException> {

    private final jakarta.inject.Provider<ValidationExceptionMapper> exceptionMapperProvider;

    @Inject
    public RollbackExceptionMapper(jakarta.inject.Provider<ValidationExceptionMapper> mapper) {
        this.exceptionMapperProvider = mapper;
    }

    @Override
    public Response toResponse(RollbackException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof ValidationException validationException) {
            return exceptionMapperProvider.get().toResponse(validationException);
        }

        return getDefaultBuilder(exception, Status.BAD_REQUEST)
            .entity(new ExceptionMessage(this.getStatusMessage(Status.BAD_REQUEST)))
            .build();
    }
}
