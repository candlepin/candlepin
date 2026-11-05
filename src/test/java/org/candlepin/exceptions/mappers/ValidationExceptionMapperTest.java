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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.exceptions.ExceptionMessage;
import org.candlepin.version.VersionUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.ValidationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

class ValidationExceptionMapperTest extends TestExceptionMapperBase {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"https://internal.example/private secret-token"})
    public void testHidesValidationDiagnostics(String message) {
        ValidationException exception = new ValidationException(message, new IllegalStateException("private cause"));
        Response response = this.injector.getInstance(ValidationExceptionMapper.class).toResponse(exception);

        assertEquals(400, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertThat(response.getHeaderString(VersionUtil.VERSION_HEADER)).isNotBlank();
        this.verifyMessage(response, "Bad Request");
    }

    @Test
    public void testPreservesConstraintMessages() {
        ConstraintViolation<?> name = this.violation("name", "may not be null");
        ConstraintViolation<?> label = this.violation("label", "size must be between 0 and 255");
        ConstraintViolationException exception = new ConstraintViolationException("private diagnostics",
            Set.of(name, label));

        Response response = this.injector.getInstance(ValidationExceptionMapper.class).toResponse(exception);

        assertEquals(400, response.getStatus());
        assertThat(((ExceptionMessage) response.getEntity()).getDisplayMessage().split("; "))
            .containsExactlyInAnyOrder("name: may not be null", "label: size must be between 0 and 255");
    }

    private ConstraintViolation<?> violation(String property, String message) {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn(property);
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn(message);
        return violation;
    }

    @Override
    public Class<?> getMapperClass() {
        return ValidationExceptionMapper.class;
    }
}
