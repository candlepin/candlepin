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

public class ServiceUnavailableExceptionTest {
    private static final String RETRY_AFTER_HEADER_KEY = "Retry-After";

    @Test
    public void testConstructorWithMessage() {
        String expected = TestUtil.randomString();

        ServiceUnavailableException exception = new ServiceUnavailableException(expected);

        assertThat(exception)
            .isNotNull()
            .returns(expected, ServiceUnavailableException::getMessage);
    }

    @Test
    public void testConstructorWithMessageAndThrowable() {
        String expectedMsg = TestUtil.randomString();
        Throwable expectedCause = new Throwable(TestUtil.randomString());

        ServiceUnavailableException exception = new ServiceUnavailableException(expectedMsg, expectedCause);

        assertThat(exception)
            .isNotNull()
            .returns(expectedMsg, ServiceUnavailableException::getMessage)
            .returns(expectedCause, ServiceUnavailableException::getCause);
    }

    @Test
    public void testSetRetryAfter() {
        ServiceUnavailableException exception = new ServiceUnavailableException(TestUtil.randomString());

        Integer expected = 10;
        exception.setRetryAfter(expected);

        assertThat(exception.getRetryAfter())
            .isNotNull()
            .isEqualTo(expected);

        exception.setRetryAfter(null);

        assertThat(exception.getRetryAfter())
            .isNull();

        expected = 20;
        exception.setRetryAfter(expected);
        assertThat(exception.getRetryAfter())
            .isNotNull()
            .isEqualTo(expected);
    }

    @Test
    public void testHeaders() {
        ServiceUnavailableException exception = new ServiceUnavailableException(TestUtil.randomString());
        int expected = 25;
        exception.setRetryAfter(expected);

        Map<String, String> headers = exception.headers();

        assertThat(headers)
            .isNotNull()
            .containsKeys(RETRY_AFTER_HEADER_KEY)
            .containsEntry(RETRY_AFTER_HEADER_KEY, String.valueOf(expected));
    }

    @Test
    public void testHeadersWithNullRetryAfterValue() {
        ServiceUnavailableException exception = new ServiceUnavailableException(TestUtil.randomString());

        Map<String, String> headers = exception.headers();

        assertThat(headers)
            .isNotNull()
            .doesNotContainKeys(RETRY_AFTER_HEADER_KEY);
    }
}
