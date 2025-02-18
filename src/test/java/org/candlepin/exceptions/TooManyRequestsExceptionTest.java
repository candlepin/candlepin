/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
package org.candlepin.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;

import java.util.Map;

public class TooManyRequestsExceptionTest {

    private static final String RETRY_AFTER_HEADER_KEY = "Retry-After";

    @Test
    public void testConstructorWithMessage() {
        String expected = TestUtil.randomString();

        TooManyRequestsException exception = new TooManyRequestsException(expected);

        assertThat(exception)
            .isNotNull()
            .returns(expected, TooManyRequestsException::getMessage);
    }

    @Test
    public void testConstructorWithNullMessage() {
        ServiceUnavailableException exception = new ServiceUnavailableException(null);

        assertThat(exception)
            .isNotNull()
            .returns(null, ServiceUnavailableException::getMessage);
    }

    @Test
    public void testConstructorWithMessageAndThrowable() {
        String expectedMessage = TestUtil.randomString();
        Throwable expectedThrowable = new Throwable(TestUtil.randomString());

        TooManyRequestsException exception = new TooManyRequestsException(expectedMessage, expectedThrowable);

        assertThat(exception)
            .isNotNull()
            .returns(expectedMessage, TooManyRequestsException::getMessage)
            .returns(expectedThrowable, TooManyRequestsException::getCause);
    }

    @Test
    public void testConstructorWithNullMessageAndThrowable() {
        ServiceUnavailableException exception = new ServiceUnavailableException(null, null);

        assertThat(exception)
            .isNotNull()
            .returns(null, ServiceUnavailableException::getMessage)
            .returns(null, ServiceUnavailableException::getCause);
    }

    @Test
    public void testSetRetryAfterTime() {
        TooManyRequestsException exception = new TooManyRequestsException(TestUtil.randomString());

        Integer expected = 10;
        exception.setRetryAfterTime(expected);

        assertThat(exception.getRetryAfterTime())
            .isNotNull()
            .isEqualTo(expected);

        exception.setRetryAfterTime(null);

        assertThat(exception.getRetryAfterTime())
            .isNull();

        expected = 20;
        exception.setRetryAfterTime(expected);
        assertThat(exception.getRetryAfterTime())
            .isNotNull()
            .isEqualTo(expected);
    }

    @Test
    public void testHeaders() {
        TooManyRequestsException exception = new TooManyRequestsException(TestUtil.randomString());
        int expected = 25;
        exception.setRetryAfterTime(expected);

        Map<String, String> headers = exception.headers();

        assertThat(headers)
            .isNotNull()
            .containsKeys(RETRY_AFTER_HEADER_KEY)
            .containsEntry(RETRY_AFTER_HEADER_KEY, String.valueOf(expected));
    }

    @Test
    public void testHeadersWithNullRetryAfterTimeValue() {
        TooManyRequestsException exception = new TooManyRequestsException(TestUtil.randomString());

        Map<String, String> headers = exception.headers();

        assertThat(headers)
            .isNotNull()
            .doesNotContainKeys(RETRY_AFTER_HEADER_KEY);
    }

}
