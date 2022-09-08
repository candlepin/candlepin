/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.spec.bootstrap.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.client.request.Response;

import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.AssertionsForClassTypes;
import org.assertj.core.api.ThrowableAssert;

import java.util.function.Supplier;

public final class StatusCodeAssertions {

    private StatusCodeAssertions() {
        throw new UnsupportedOperationException();
    }

    public static AbstractThrowableAssert<?, ? extends Throwable> assertBadRequest(
        ThrowableAssert.ThrowingCallable callable) {
        ApiException exception = catchApiException(callable);
        return assertReturnCode(400, exception);
    }

    public static AbstractThrowableAssert<?, ? extends Throwable> assertUnauthorized(
        ThrowableAssert.ThrowingCallable callable) {
        ApiException exception = catchApiException(callable);
        return assertReturnCode(401, exception);
    }

    public static AbstractThrowableAssert<?, ? extends Throwable> assertForbidden(
        ThrowableAssert.ThrowingCallable callable) {
        ApiException exception = catchApiException(callable);
        return assertReturnCode(403, exception);
    }

    public static AbstractThrowableAssert<?, ? extends Throwable> assertNotFound(
        ThrowableAssert.ThrowingCallable callable) {
        ApiException exception = catchApiException(callable);
        return assertReturnCode(404, exception);
    }

    public static AbstractThrowableAssert<?, ? extends Throwable> assertConflict(
        ThrowableAssert.ThrowingCallable callable) {
        ApiException exception = catchApiException(callable);
        return assertReturnCode(409, exception);
    }

    public static AbstractThrowableAssert<?, ? extends Throwable> assertGone(
        ThrowableAssert.ThrowingCallable callable) {
        ApiException exception = catchApiException(callable);
        return assertReturnCode(410, exception);
    }

    public static CandlepinStatusAssert assertThatStatus(
        ThrowableAssert.ThrowingCallable callable) {
        ApiException exception = catchApiException(callable);
        return new CandlepinStatusAssert(exception);
    }

    public static CandlepinStatusAssert assertThatStatus(
        Supplier<Response> callable) {
        ApiException exception = new ApiExceptionAdapter(callable.get());
        return new CandlepinStatusAssert(exception);
    }

    public static ApiException catchApiException(ThrowableAssert.ThrowingCallable code) {
        ApiException exception = AssertionsForClassTypes.catchThrowableOfType(code, ApiException.class);
        assertThat(exception)
            .as("Expected ApiException but nothing was thrown")
            .isNotNull()
            .as("Expected successful request")
            // Cause will be empty if we successfully reached the server
            .hasNoCause();
        return exception;
    }

    private static AbstractThrowableAssert<?, ? extends Throwable> assertReturnCode(
        int statusCode, ApiException exception) {
        return assertThat(exception).hasFieldOrPropertyWithValue("code", statusCode);
    }

}
