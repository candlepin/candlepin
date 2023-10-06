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

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.function.Supplier;



public class ConstraintViolationRetryWrapperTest {

    private Exception buildConstraintViolation() {
        return new ConstraintViolationException("test exception", new SQLException(), null);
    }

    @Test
    public void testNegativeRetriesValuesDisallowed() {
        assertThrows(IllegalArgumentException.class, () -> new ConstraintViolationRetryWrapper().retries(-5));
    }

    @Test
    public void testExecuteSucceedsWithoutRetries() {
        String expected = "success";
        Exception exception = this.buildConstraintViolation();

        Supplier<String> supplier = mock(Supplier.class);
        doReturn(expected).doThrow(new RuntimeException("fail")).when(supplier).get();

        String output = new ConstraintViolationRetryWrapper()
            .retries(0)
            .execute(supplier);

        verify(supplier, times(1)).get();
        assertEquals(expected, output);
    }

    @Test
    public void testExecuteWontRetryWithoutRetries() {
        String expected = "success";
        Exception exception = this.buildConstraintViolation();

        Supplier<String> supplier = mock(Supplier.class);
        doThrow(exception).doReturn(expected).doThrow(new RuntimeException("fail")).when(supplier).get();

        Exception actual = assertThrows(ConstraintViolationException.class, () -> {
            new ConstraintViolationRetryWrapper()
                .retries(0)
                .execute(supplier);
        });

        verify(supplier, times(1)).get();
        assertEquals(exception, actual);
    }

    @Test
    public void testExecuteReturnsUponSuccess() {
        String expected = "success";
        Exception exception = this.buildConstraintViolation();

        Supplier<String> supplier = mock(Supplier.class);
        doReturn(expected).doThrow(new RuntimeException("fail")).when(supplier).get();

        String output = new ConstraintViolationRetryWrapper()
            .retries(2)
            .execute(supplier);

        verify(supplier, times(1)).get();
        assertEquals(expected, output);
    }

    @Test
    public void testExecuteRetriesOnConstraintViolation() {
        String expected = "success";
        Exception exception = this.buildConstraintViolation();

        Supplier<String> supplier = mock(Supplier.class);
        doThrow(exception).doReturn(expected).doThrow(new RuntimeException("fail")).when(supplier).get();

        String output = new ConstraintViolationRetryWrapper()
            .retries(2)
            .execute(supplier);

        verify(supplier, times(2)).get();
        assertEquals(expected, output);
    }

    @Test
    public void testExecuteRetriesOnNestedConstraintViolation() {
        String expected = "success";
        Exception exception = new RuntimeException("outer exception", this.buildConstraintViolation());

        Supplier<String> supplier = mock(Supplier.class);
        doThrow(exception).doReturn(expected).doThrow(new RuntimeException("fail")).when(supplier).get();

        String output = new ConstraintViolationRetryWrapper()
            .retries(2)
            .execute(supplier);

        verify(supplier, times(2)).get();
        assertEquals(expected, output);
    }

    @Test
    public void testExecuteLimitsRetriesOnConstraintViolation() {
        String expected = "success";
        Exception exception = this.buildConstraintViolation();

        Supplier<String> supplier = mock(Supplier.class);
        doThrow(exception).doThrow(exception).doThrow(exception).doReturn(expected).when(supplier).get();

        Exception actual = assertThrows(ConstraintViolationException.class, () -> {
            new ConstraintViolationRetryWrapper()
                .retries(2)
                .execute(supplier);
        });

        verify(supplier, times(3)).get();
        assertEquals(exception, actual);
    }

    @Test
    public void testRetryOmittedForNonConstraintViolationExceptions() {
        Exception exception = new RuntimeException("fail");

        Supplier<String> supplier = mock(Supplier.class);
        doThrow(exception).when(supplier).get();

        Exception actual = assertThrows(RuntimeException.class, () -> {
            new ConstraintViolationRetryWrapper()
                .retries(3)
                .execute(supplier);
        });

        verify(supplier, times(1)).get();
        assertEquals(exception, actual);
    }

}
