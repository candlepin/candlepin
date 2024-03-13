/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.controller.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import java.util.function.Supplier;



public class ExpectedExceptionRetryWrapperTest {

    /**
     * Internal exception to avoid confusing generic runtime exceptions with our targeted, expected
     * exceptions
     */
    private static final class TestExecutionException extends RuntimeException {

        public TestExecutionException() {
            super();
        }

        public TestExecutionException(String message) {
            super(message);
        }

        public TestExecutionException(String message, Throwable cause) {
            super(message, cause);
        }

        public TestExecutionException(Throwable cause) {
            super(cause);
        }

    }

    /**
     * Internal exception to avoid confusing generic runtime exceptions with our targeted, expected
     * exceptions. Note that this exception *cannot* be part of the same class hierarchy as the
     * regular TestExecutionException class.
     */
    private static final class TestExecutionAltException extends RuntimeException {

        public TestExecutionAltException() {
            super();
        }

        public TestExecutionAltException(String message) {
            super(message);
        }

        public TestExecutionAltException(String message, Throwable cause) {
            super(message, cause);
        }

        public TestExecutionAltException(Throwable cause) {
            super(cause);
        }

    }

    /**
     * Internal exception to avoid confusing generic runtime exceptions with our targeted, failure
     * exceptions
     */
    private static final class TestFailureException extends RuntimeException {

        public TestFailureException() {
            super();
        }

        public TestFailureException(String message) {
            super(message);
        }

        public TestFailureException(String message, Throwable cause) {
            super(message, cause);
        }

        public TestFailureException(Throwable cause) {
            super(cause);
        }

    }

    @Test
    public void testNegativeRetriesValuesDisallowed() {
        assertThrows(IllegalArgumentException.class, () -> new ExpectedExceptionRetryWrapper().retries(-5));
    }

    @Test
    public void testAddExceptionRequiresNonNull() {
        assertThrows(IllegalArgumentException.class, () -> new ExpectedExceptionRetryWrapper()
            .addException(null));
    }

    @Test
    public void testAddExceptionPermitsReaddingSameExceptions() {
        new ExpectedExceptionRetryWrapper()
            .addException(TestExecutionException.class)
            .addException(TestExecutionException.class);

        // Nothing to verify at the time of writing; it just shouldn't crash out
    }

    @Test
    public void testExecuteSucceedsWithoutRetries() {
        String expected = "success";
        Exception exception = new TestExecutionException();

        Supplier<String> supplier = mock(Supplier.class);
        doReturn(expected).doThrow(new TestFailureException()).when(supplier).get();

        String output = new ExpectedExceptionRetryWrapper()
            .addException(exception.getClass())
            .retries(0)
            .execute(supplier);

        verify(supplier, times(1)).get();
        assertEquals(expected, output);
    }

    @Test
    public void testExecuteWontRetryWithoutRetries() {
        String expected = "success";
        Exception exception = new TestExecutionException();

        Supplier<String> supplier = mock(Supplier.class);
        doThrow(exception).doReturn(expected).doThrow(new TestFailureException()).when(supplier).get();

        Exception actual = assertThrows(exception.getClass(), () -> {
            new ExpectedExceptionRetryWrapper()
                .addException(exception.getClass())
                .retries(0)
                .execute(supplier);
        });

        verify(supplier, times(1)).get();
        assertEquals(exception, actual);
    }

    @Test
    public void testExecuteReturnsUponSuccess() {
        String expected = "success";
        Exception exception = new TestExecutionException();

        Supplier<String> supplier = mock(Supplier.class);
        doReturn(expected).doThrow(new TestFailureException()).when(supplier).get();

        String output = new ExpectedExceptionRetryWrapper()
            .addException(exception.getClass())
            .retries(2)
            .execute(supplier);

        verify(supplier, times(1)).get();
        assertEquals(expected, output);
    }

    @Test
    public void testExecuteRetriesOnExpectedException() {
        String expected = "success";
        Exception exception = new TestExecutionException();

        Supplier<String> supplier = mock(Supplier.class);
        doThrow(exception).doReturn(expected).doThrow(new TestFailureException()).when(supplier).get();

        String output = new ExpectedExceptionRetryWrapper()
            .addException(exception.getClass())
            .retries(2)
            .execute(supplier);

        verify(supplier, times(2)).get();
        assertEquals(expected, output);
    }

    @Test
    public void testExecuteRetriesOnMultipleExpectedExceptions() {
        String expected = "success";
        Exception exception1 = new TestExecutionException();
        Exception exception2 = new TestExecutionAltException();

        Supplier<String> supplier = mock(Supplier.class);
        doThrow(exception1).doThrow(exception2).doReturn(expected).doThrow(new TestFailureException())
            .when(supplier).get();

        String output = new ExpectedExceptionRetryWrapper()
            .addException(exception1.getClass())
            .addException(exception2.getClass())
            .retries(2)
            .execute(supplier);

        verify(supplier, times(3)).get();
        assertEquals(expected, output);
    }

    @Test
    public void testExecuteRetriesOnNestedExpectedException() {
        String expected = "success";
        Exception exception = new TestExecutionAltException("outer exception", new TestExecutionException());

        Supplier<String> supplier = mock(Supplier.class);
        doThrow(exception).doReturn(expected).doThrow(new TestFailureException()).when(supplier).get();

        String output = new ExpectedExceptionRetryWrapper()
            .addException(TestExecutionException.class)
            .retries(2)
            .execute(supplier);

        verify(supplier, times(2)).get();
        assertEquals(expected, output);
    }

    @Test
    public void testExecuteLimitsRetriesOnExpectedException() {
        String expected = "success";
        Exception exception = new TestExecutionException();

        Supplier<String> supplier = mock(Supplier.class);
        doThrow(exception).doThrow(exception).doThrow(exception).doReturn(expected).when(supplier).get();

        Exception actual = assertThrows(exception.getClass(), () -> {
            new ExpectedExceptionRetryWrapper()
                .addException(exception.getClass())
                .retries(2)
                .execute(supplier);
        });

        verify(supplier, times(3)).get();
        assertEquals(exception, actual);
    }

    @Test
    public void testRetryOmittedForNonExpectedExceptions() {
        Exception exception = new TestFailureException("fail");

        Supplier<String> supplier = mock(Supplier.class);
        doThrow(exception).when(supplier).get();

        Exception actual = assertThrows(TestFailureException.class, () -> {
            new ExpectedExceptionRetryWrapper()
                .addException(TestExecutionException.class)
                .retries(3)
                .execute(supplier);
        });

        verify(supplier, times(1)).get();
        assertEquals(exception, actual);
    }

}
