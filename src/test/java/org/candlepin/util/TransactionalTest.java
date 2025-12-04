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
package org.candlepin.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.util.Transactional.State;
import org.candlepin.util.function.CheckedRunnable;
import org.candlepin.util.function.CheckedSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.transaction.Status;



/**
 * Test suite for the Transactional object
 */
public class TransactionalTest  {

    /**
     * Test class that lets us control transaction state for testing various scenarios
     */
    public static class TestTransaction implements EntityTransaction {

        private boolean active;
        private boolean rollbackOnly;
        private int lastStatus;

        @Override
        public void begin() {
            this.active = true;
        }

        @Override
        public void commit() {
            if (!this.active) {
                throw new IllegalStateException();
            }

            this.active = false;
            this.lastStatus = Status.STATUS_COMMITTED;
        }

        @Override
        public boolean getRollbackOnly() {
            return this.rollbackOnly;
        }

        @Override
        public boolean isActive() {
            return this.active;
        }

        @Override
        public void rollback() {
            if (!this.active) {
                throw new IllegalStateException();
            }

            this.active = false;
            this.lastStatus = Status.STATUS_ROLLEDBACK;
        }

        @Override
        public void setRollbackOnly() {
            this.rollbackOnly = true;
        }

        public int getLastStatus() {
            return this.lastStatus;
        }
    }

    private EntityManager entityManager;
    private EntityTransaction transaction;

    @BeforeEach
    protected void init() {
        this.entityManager = mock(EntityManager.class);
        this.transaction = spy(new TestTransaction());

        doReturn(this.transaction).when(this.entityManager).getTransaction();
    }

    private Transactional buildTransactional() {
        return new Transactional(this.entityManager);
    }

    @Test
    public void testSetCommitListener() {
        Transactional transactional = this.buildTransactional();

        transactional.onCommit(status -> { });
    }

    @Test
    public void testSetMultipleCommitListeners() {
        Transactional transactional = this.buildTransactional();

        transactional.onCommit(status -> { });
        transactional.onCommit(status -> { });
    }

    @Test
    public void testSetSameCommitListenerRepeatedly() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener = status -> { };

        transactional.onCommit(listener);
        transactional.onCommit(listener);
    }

    @Test
    public void testCannotSetNullCommitListener() {
        Transactional transactional = this.buildTransactional();
        assertThrows(IllegalArgumentException.class, () -> transactional.onCommit(null));
    }

    @Test
    public void testCannotSetCommitListenerInNonExclusiveMode() {
        Transactional.Listener listener = status -> { };
        Transactional transactional = this.buildTransactional()
            .allowExistingTransactions();

        assertThrows(IllegalStateException.class, () -> transactional.onCommit(listener));
    }

    @Test
    public void testSetRollbackListener() {
        Transactional transactional = this.buildTransactional();

        transactional.onRollback(status -> { });
    }

    @Test
    public void testSetMultipleRollbackListeners() {
        Transactional transactional = this.buildTransactional();

        transactional.onRollback(status -> { });
        transactional.onRollback(status -> { });
    }

    @Test
    public void testSetSameRollbackListenerRepeatedly() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener = status -> { };

        transactional.onRollback(listener);
        transactional.onRollback(listener);
    }

    @Test
    public void testCannotSetNullRollbackListener() {
        Transactional transactional = this.buildTransactional();
        assertThrows(IllegalArgumentException.class, () -> transactional.onRollback(null));
    }

    @Test
    public void testCannotSetRollbackListenerInNonExclusiveMode() {
        Transactional.Listener listener = status -> { };
        Transactional transactional = this.buildTransactional()
            .allowExistingTransactions();

        assertThrows(IllegalStateException.class, () -> transactional.onRollback(listener));
    }

    @Test
    public void testSetOnCompleteListener() {
        Transactional transactional = this.buildTransactional();

        transactional.onComplete(status -> { });
    }

    @Test
    public void testSetMultipleOnCompleteListeners() {
        Transactional transactional = this.buildTransactional();

        transactional.onComplete(status -> { });
        transactional.onComplete(status -> { });
    }

    @Test
    public void testSetSameOnCompleteListenerRepeatedly() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener = status -> { };

        transactional.onComplete(listener);
        transactional.onComplete(listener);
    }

    @Test
    public void testCannotSetNullCompleteListener() {
        Transactional transactional = this.buildTransactional();
        assertThrows(IllegalArgumentException.class, () -> transactional.onComplete(null));
    }

    @Test
    public void testExecuteWithRunnable() throws Exception {
        Transactional transactional = this.buildTransactional();
        Runnable task = mock(Runnable.class);

        transactional.execute(task);

        verify(task, times(1)).run();
    }

    @Test
    public void testExecuteWithCheckedRunnable() throws Exception {
        Transactional transactional = this.buildTransactional();
        CheckedRunnable task = mock(CheckedRunnable.class);

        transactional.checkedExecute(task);

        verify(task, times(1)).run();
    }

    @Test
    public void testExecuteWithSupplier() throws Exception {
        Transactional transactional = this.buildTransactional();

        String expected = "output";
        Supplier<String> task = mock(Supplier.class);
        doReturn(expected).when(task).get();

        String output = transactional.execute(task);

        verify(task, times(1)).get();
        assertEquals(expected, output);
    }

    @Test
    public void testExecuteWithCheckedSupplier() throws Exception {
        Transactional transactional = this.buildTransactional();

        String expected = "output";
        CheckedSupplier<String, RuntimeException> task = mock(CheckedSupplier.class);
        doReturn(expected).when(task).get();

        String output = transactional.checkedExecute(task);

        verify(task, times(1)).get();
        assertEquals(expected, output);
    }

    @Test
    public void testExecutionOfRunnableFailsOnActiveTransaction() {
        Transactional transactional = this.buildTransactional();

        this.transaction.begin();

        Runnable task = () -> { };
        assertThrows(IllegalStateException.class, () -> transactional.execute(task));
    }

    @Test
    public void testExecutionOfSupplierFailsOnActiveTransaction() {
        Transactional transactional = this.buildTransactional();

        this.transaction.begin();

        Supplier task = () -> "generated_output";
        assertThrows(IllegalStateException.class, () -> transactional.execute(task));
    }

    @Test
    public void testExecutionOfCheckedRunnableFailsOnActiveTransaction() {
        Transactional transactional = this.buildTransactional();

        this.transaction.begin();

        CheckedRunnable task = () -> { };
        assertThrows(IllegalStateException.class, () -> transactional.checkedExecute(task));
    }

    @Test
    public void testExecutionOfCheckedSupplierFailsOnActiveTransaction() {
        Transactional transactional = this.buildTransactional();

        this.transaction.begin();

        CheckedSupplier task = () -> "generated_output";
        assertThrows(IllegalStateException.class, () -> transactional.checkedExecute(task));
    }

    @Test
    public void testExecutionOfRunnableCanUseActiveTransactions() {
        Transactional transactional = this.buildTransactional()
            .allowExistingTransactions();

        this.transaction.begin();
        reset(this.transaction);

        Runnable task = () -> { };

        transactional.execute(task);

        verify(this.transaction, never()).begin();
        verify(this.transaction, never()).commit();
    }

    @Test
    public void testExecutionOfSupplierCanUseActiveTransactions() {
        Transactional transactional = this.buildTransactional()
            .allowExistingTransactions();

        this.transaction.begin();
        reset(this.transaction);

        String expected = "generated_output";
        Supplier<String> task = () -> expected;

        String output = transactional.execute(task);

        assertEquals(expected, output);
        verify(this.transaction, never()).begin();
        verify(this.transaction, never()).commit();
    }

    @Test
    public void testExecutionOfCheckedRunnableCanUseActiveTransactions() {
        Transactional transactional = this.buildTransactional()
            .allowExistingTransactions();

        this.transaction.begin();
        reset(this.transaction);

        CheckedRunnable<RuntimeException> task = () -> { };

        transactional.checkedExecute(task);

        verify(this.transaction, never()).begin();
        verify(this.transaction, never()).commit();
    }

    @Test
    public void testExecutionOfCheckedSupplierCanUseActiveTransactions() {
        Transactional transactional = this.buildTransactional()
            .allowExistingTransactions();

        this.transaction.begin();
        reset(this.transaction);

        String expected = "generated_output";
        CheckedSupplier<String, RuntimeException> task = () -> expected;

        String output = transactional.checkedExecute(task);

        assertEquals(expected, output);
        verify(this.transaction, never()).begin();
        verify(this.transaction, never()).commit();
    }

    @Test
    public void testRollbackOnUncaughtRunnableException() throws Exception {
        Transactional transactional = this.buildTransactional();
        Runnable task = () -> { throw new SecurityException("kaboom"); };

        assertThrows(TransactionExecutionException.class, () -> transactional.execute(task));

        verify(this.transaction, times(1)).begin();
        verify(this.transaction, times(1)).rollback();
    }

    @Test
    public void testRollbackOnUncaughtSupplierException() throws Exception {
        Transactional transactional = this.buildTransactional();
        Supplier task = () -> { throw new SecurityException("kaboom"); };

        assertThrows(TransactionExecutionException.class, () -> transactional.execute(task));

        verify(this.transaction, times(1)).begin();
        verify(this.transaction, times(1)).rollback();
    }

    @Test
    public void testRollbackOnCheckedRunnableException() throws Exception {
        Transactional transactional = this.buildTransactional();
        CheckedRunnable task = () -> { throw new SecurityException("kaboom"); };

        assertThrows(SecurityException.class, () -> transactional.checkedExecute(task));

        verify(this.transaction, times(1)).begin();
        verify(this.transaction, times(1)).rollback();
    }

    @Test
    public void testRollbackOnCheckedSupplierException() throws Exception {
        Transactional transactional = this.buildTransactional();
        CheckedSupplier task = () -> { throw new SecurityException("kaboom"); };

        assertThrows(SecurityException.class, () -> transactional.checkedExecute(task));

        verify(this.transaction, times(1)).begin();
        verify(this.transaction, times(1)).rollback();
    }

    @Test
    public void testCommitOnUncaughtRunnableExceptionWithCommitOnException() throws Exception {
        Transactional transactional = this.buildTransactional()
            .commitOnException();

        Runnable task = () -> { throw new SecurityException("kaboom"); };

        assertThrows(TransactionExecutionException.class, () -> transactional.execute(task));

        verify(this.transaction, times(1)).begin();
        verify(this.transaction, times(1)).commit();
    }

    @Test
    public void testCommitOnUncaughtSupplierExceptionWithCommitOnException() throws Exception {
        Transactional transactional = this.buildTransactional()
            .commitOnException();

        Supplier task = () -> { throw new SecurityException("kaboom"); };

        assertThrows(TransactionExecutionException.class, () -> transactional.execute(task));

        verify(this.transaction, times(1)).begin();
        verify(this.transaction, times(1)).commit();
    }

    @Test
    public void testCommitOnCheckedRunnableExceptionWithCommitOnException() throws Exception {
        Transactional transactional = this.buildTransactional()
            .commitOnException();

        CheckedRunnable task = () -> { throw new SecurityException("kaboom"); };

        assertThrows(SecurityException.class, () -> transactional.checkedExecute(task));

        verify(this.transaction, times(1)).begin();
        verify(this.transaction, times(1)).commit();
    }

    @Test
    public void testCommitOnCheckedSupplierExceptionWithCommitOnException() throws Exception {
        Transactional transactional = this.buildTransactional()
            .commitOnException();

        CheckedSupplier task = () -> { throw new SecurityException("kaboom"); };

        assertThrows(SecurityException.class, () -> transactional.checkedExecute(task));

        verify(this.transaction, times(1)).begin();
        verify(this.transaction, times(1)).commit();
    }

    @Test
    public void testExecuteRunsCommitListenersAfterExecutionOfRunnable() {
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onCommit(listener);

        Runnable task = () -> { };
        transactional.execute(task);

        verify(listener, times(1)).transactionComplete(State.COMMITTED);
    }

    @Test
    public void testExecuteRunsCommitListenersAfterExecutionOfSupplier() {
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onCommit(listener);

        String expected = "generated_output";
        Supplier<String> task = () -> expected;

        String output = transactional.execute(task);

        assertEquals(expected, output);
        verify(listener, times(1)).transactionComplete(State.COMMITTED);
    }

    @Test
    public void testExecuteRunsCommitListenersAfterExecutionOfCheckedRunnable() {
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onCommit(listener);

        CheckedRunnable task = () -> { };
        transactional.checkedExecute(task);

        verify(listener, times(1)).transactionComplete(State.COMMITTED);
    }

    @Test
    public void testExecuteRunsCommitListenersAfterExecutionOfCheckedSupplier() {
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onCommit(listener);

        String expected = "generated_output";
        CheckedSupplier<String, RuntimeException> task = () -> expected;

        String output = transactional.checkedExecute(task);

        assertEquals(expected, output);
        verify(listener, times(1)).transactionComplete(State.COMMITTED);
    }

    @Test
    public void testExecuteRunsMultipleCommitListenersAfterExecutionOfRunnable() {
        Transactional.Listener listener1 = mock(Transactional.Listener.class);
        Transactional.Listener listener2 = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onCommit(listener1)
            .onCommit(listener2);

        Runnable task = () -> { };
        transactional.execute(task);

        verify(listener1, times(1)).transactionComplete(State.COMMITTED);
        verify(listener2, times(1)).transactionComplete(State.COMMITTED);
    }

    @Test
    public void testExecuteRunsMultipleCommitListenersAfterExecutionOfSupplier() {
        Transactional.Listener listener1 = mock(Transactional.Listener.class);
        Transactional.Listener listener2 = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onCommit(listener1)
            .onCommit(listener2);

        String expected = "generated_output";
        Supplier<String> task = () -> expected;

        String output = transactional.execute(task);

        assertEquals(expected, output);
        verify(listener1, times(1)).transactionComplete(State.COMMITTED);
        verify(listener2, times(1)).transactionComplete(State.COMMITTED);
    }

    @Test
    public void testExecuteRunsMultipleCommitListenersAfterExecutionOfCheckedRunnable() {
        Transactional.Listener listener1 = mock(Transactional.Listener.class);
        Transactional.Listener listener2 = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onCommit(listener1)
            .onCommit(listener2);

        CheckedRunnable<RuntimeException> task = () -> { };

        transactional.checkedExecute(task);

        verify(listener1, times(1)).transactionComplete(State.COMMITTED);
        verify(listener2, times(1)).transactionComplete(State.COMMITTED);
    }

    @Test
    public void testExecuteRunsMultipleCommitListenersAfterExecutionOfCheckedSupplier() {
        Transactional.Listener listener1 = mock(Transactional.Listener.class);
        Transactional.Listener listener2 = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onCommit(listener1)
            .onCommit(listener2);

        String expected = "generated_output";
        CheckedSupplier<String, RuntimeException> task = () -> expected;

        String output = transactional.checkedExecute(task);

        assertEquals(expected, output);
        verify(listener1, times(1)).transactionComplete(State.COMMITTED);
        verify(listener2, times(1)).transactionComplete(State.COMMITTED);
    }


    @Test
    public void testExecuteRunsIdenticalCommitListenersAfterExecutionOfRunnable() {
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onCommit(listener)
            .onCommit(listener);

        Runnable task = () -> { };
        transactional.execute(task);

        verify(listener, times(2)).transactionComplete(State.COMMITTED);
    }

    @Test
    public void testExecuteRunsIdenticalCommitListenersAfterExecutionOfSupplier() {
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onCommit(listener)
            .onCommit(listener);

        String expected = "generated_output";
        Supplier<String> task = () -> expected;

        String output = transactional.execute(task);

        assertEquals(expected, output);
        verify(listener, times(2)).transactionComplete(State.COMMITTED);
    }

    @Test
    public void testExecuteRunsIdenticalCommitListenersAfterExecutionOfCheckedRunnable() {
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onCommit(listener)
            .onCommit(listener);

        CheckedRunnable<RuntimeException> task = () -> { };

        transactional.checkedExecute(task);

        verify(listener, times(2)).transactionComplete(State.COMMITTED);
    }

    @Test
    public void testExecuteRunsIdenticalCommitListenersAfterExecutionOfCheckedSupplier() {
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onCommit(listener)
            .onCommit(listener);

        String expected = "generated_output";
        CheckedSupplier<String, RuntimeException> task = () -> expected;

        String output = transactional.checkedExecute(task);

        assertEquals(expected, output);

        verify(listener, times(2)).transactionComplete(State.COMMITTED);
    }

    @Test
    public void testExecuteRunsRollbackListenersAfterExecutionOfRunnable() {
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onRollback(listener);

        Runnable task = () -> { this.transaction.setRollbackOnly(); };
        transactional.execute(task);

        verify(listener, times(1)).transactionComplete(State.ROLLED_BACK);
    }

    @Test
    public void testExecuteRunsRollbackListenersAfterExecutionOfSupplier() {
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onRollback(listener);

        String expected = "generated_output";
        Supplier<String> task = () -> {
            this.transaction.setRollbackOnly();
            return expected;
        };

        String output = transactional.execute(task);

        assertEquals(expected, output);
        verify(listener, times(1)).transactionComplete(State.ROLLED_BACK);
    }

    @Test
    public void testExecuteRunsRollbackListenersAfterExecutionOfCheckedRunnable() {
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onRollback(listener);

        CheckedRunnable<RuntimeException> task = () -> { this.transaction.setRollbackOnly(); };

        transactional.checkedExecute(task);

        verify(listener, times(1)).transactionComplete(State.ROLLED_BACK);
    }

    @Test
    public void testExecuteRunsRollbackListenersAfterExecutionOfCheckedSupplier() {
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onRollback(listener);

        String expected = "generated_output";
        CheckedSupplier<String, RuntimeException> task = () -> {
            this.transaction.setRollbackOnly();
            return expected;
        };

        String output = transactional.checkedExecute(task);

        assertEquals(expected, output);
        verify(listener, times(1)).transactionComplete(State.ROLLED_BACK);
    }

    @Test
    public void testExecuteRunsMultipleRollbackListenersAfterExecutionOfRunnable() {
        Transactional.Listener listener1 = mock(Transactional.Listener.class);
        Transactional.Listener listener2 = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onRollback(listener1)
            .onRollback(listener2);

        Runnable task = () -> { this.transaction.setRollbackOnly(); };
        transactional.execute(task);

        verify(listener1, times(1)).transactionComplete(State.ROLLED_BACK);
        verify(listener2, times(1)).transactionComplete(State.ROLLED_BACK);
    }

    @Test
    public void testExecuteRunsMultipleRollbackListenersAfterExecutionOfSupplier() {
        Transactional.Listener listener1 = mock(Transactional.Listener.class);
        Transactional.Listener listener2 = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onRollback(listener1)
            .onRollback(listener2);

        String expected = "generated_output";
        Supplier<String> task = () -> {
            this.transaction.setRollbackOnly();
            return expected;
        };

        String output = transactional.execute(task);

        assertEquals(expected, output);
        verify(listener1, times(1)).transactionComplete(State.ROLLED_BACK);
        verify(listener2, times(1)).transactionComplete(State.ROLLED_BACK);
    }

    @Test
    public void testExecuteRunsMultipleRollbackListenersAfterExecutionOfCheckedRunnable() {
        Transactional.Listener listener1 = mock(Transactional.Listener.class);
        Transactional.Listener listener2 = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onRollback(listener1)
            .onRollback(listener2);

        CheckedRunnable<RuntimeException> task = () -> { this.transaction.setRollbackOnly(); };

        transactional.checkedExecute(task);

        verify(listener1, times(1)).transactionComplete(State.ROLLED_BACK);
        verify(listener2, times(1)).transactionComplete(State.ROLLED_BACK);
    }

    @Test
    public void testExecuteRunsMultipleRollbackListenersAfterExecutionOfCheckedSupplier() {
        Transactional.Listener listener1 = mock(Transactional.Listener.class);
        Transactional.Listener listener2 = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onRollback(listener1)
            .onRollback(listener2);

        String expected = "generated_output";
        CheckedSupplier<String, RuntimeException> task = () -> {
            this.transaction.setRollbackOnly();
            return expected;
        };

        String output = transactional.checkedExecute(task);

        assertEquals(expected, output);
        verify(listener1, times(1)).transactionComplete(State.ROLLED_BACK);
        verify(listener2, times(1)).transactionComplete(State.ROLLED_BACK);
    }

    @Test
    public void testExecuteRunsIdenticalRollbackListenersAfterExecutionOfRunnable() {
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onRollback(listener)
            .onRollback(listener);

        Runnable task = () -> {
            this.transaction.setRollbackOnly();
        };

        transactional.execute(task);

        verify(listener, times(2)).transactionComplete(State.ROLLED_BACK);
    }

    @Test
    public void testExecuteRunsIdenticalRollbackListenersAfterExecutionOfSupplier() {
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onRollback(listener)
            .onRollback(listener);

        String expected = "generated_output";
        CheckedSupplier<String, RuntimeException> task = () -> {
            this.transaction.setRollbackOnly();
            return expected;
        };

        String output = transactional.checkedExecute(task);

        assertEquals(expected, output);
        verify(listener, times(2)).transactionComplete(State.ROLLED_BACK);
    }

    @Test
    public void testExecuteRunsIdenticalRollbackListenersAfterExecutionOfCheckedRunnable() {
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onRollback(listener)
            .onRollback(listener);

        CheckedRunnable<RuntimeException> task = () -> {
            this.transaction.setRollbackOnly();
        };

        transactional.checkedExecute(task);

        verify(listener, times(2)).transactionComplete(State.ROLLED_BACK);
    }

    @Test
    public void testExecuteRunsIdenticalRollbackListenersAfterExecutionOfCheckedSupplier() {

        Transactional.Listener listener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .onRollback(listener)
            .onRollback(listener);

        String expected = "generated_output";
        CheckedSupplier<String, RuntimeException> task = () -> {
            this.transaction.setRollbackOnly();
            return expected;
        };

        String output = transactional.checkedExecute(task);

        assertEquals(expected, output);
        verify(listener, times(2)).transactionComplete(State.ROLLED_BACK);
    }

    @Test
    public void testExceptionWhenTransactionCommittedDuringExclusiveExecutionOfRunnable() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Runnable task = () -> { this.transaction.rollback(); };

        assertThrows(IllegalStateException.class, () -> transactional.execute(task));
    }

    @Test
    public void testExceptionWhenTransactionCommittedDuringExclusiveExecutionOfSupplier() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Supplier task = () -> {
            this.transaction.rollback();
            return "output";
        };

        assertThrows(IllegalStateException.class, () -> transactional.execute(task));
    }

    @Test
    public void testExceptionWhenTransactionCommittedDuringExclusiveExecutionOfCheckedRunnable() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener = mock(Transactional.Listener.class);

        CheckedRunnable task = () -> { this.transaction.rollback(); };

        assertThrows(IllegalStateException.class, () -> transactional.checkedExecute(task));
    }

    @Test
    public void testExceptionWhenTransactionCommittedDuringExclusiveExecutionOfCheckedSupplier() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener = mock(Transactional.Listener.class);

        CheckedSupplier task = () -> {
            this.transaction.rollback();
            return "output";
        };

        assertThrows(IllegalStateException.class, () -> transactional.checkedExecute(task));
    }

    @Test
    public void testExceptionWhenTransactionRolledBackDuringExclusiveExecutionOfRunnable() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Runnable task = () -> { this.transaction.rollback(); };

        assertThrows(IllegalStateException.class, () -> transactional.execute(task));
    }

    @Test
    public void testExceptionWhenTransactionRolledBackDuringExclusiveExecutionOfSupplier() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener = mock(Transactional.Listener.class);

        Supplier task = () -> {
            this.transaction.rollback();
            return "output";
        };

        assertThrows(IllegalStateException.class, () -> transactional.execute(task));
    }

    @Test
    public void testExceptionWhenTransactionRolledBackDuringExclusiveExecutionOfCheckedRunnable() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener = mock(Transactional.Listener.class);

        CheckedRunnable task = () -> { this.transaction.rollback(); };

        assertThrows(IllegalStateException.class, () -> transactional.checkedExecute(task));
    }

    @Test
    public void testExceptionWhenTransactionRolledBackDuringExclusiveExecutionOfCheckedSupplier() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener = mock(Transactional.Listener.class);

        CheckedSupplier task = () -> {
            this.transaction.rollback();
            return "output";
        };

        assertThrows(IllegalStateException.class, () -> transactional.checkedExecute(task));
    }

    @Test
    public void testExecuteRunsCommitListenersAfterExceptionInRunnableWithCommitOnException() {
        Transactional.Listener commitListener = mock(Transactional.Listener.class);
        Transactional.Listener rollbackListener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .commitOnException()
            .onCommit(commitListener)
            .onRollback(rollbackListener);

        Runnable task = () -> { throw new SecurityException("Kaboom"); };

        assertThrows(TransactionExecutionException.class, () -> transactional.execute(task));

        verify(commitListener, times(1)).transactionComplete(State.COMMITTED);
        verify(rollbackListener, never()).transactionComplete(State.ROLLED_BACK);
    }

    @Test
    public void testExecuteRunsRollbackListenersAfterExceptionInSupplierWithCommitOnException() {
        Transactional.Listener commitListener = mock(Transactional.Listener.class);
        Transactional.Listener rollbackListener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .commitOnException()
            .onCommit(commitListener)
            .onRollback(rollbackListener);

        Supplier<String> task = () -> { throw new SecurityException("Kaboom"); };

        assertThrows(TransactionExecutionException.class, () -> transactional.execute(task));

        verify(commitListener, times(1)).transactionComplete(State.COMMITTED);
        verify(rollbackListener, never()).transactionComplete(State.ROLLED_BACK);
    }

    @Test
    public void testExecuteRunsRollbackListenersAfterExceptionInCheckedRunnableWithCommitOnException() {
        Transactional.Listener commitListener = mock(Transactional.Listener.class);
        Transactional.Listener rollbackListener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .commitOnException()
            .onCommit(commitListener)
            .onRollback(rollbackListener);

        CheckedRunnable<RuntimeException> task = () -> { throw new SecurityException("Kaboom"); };

        assertThrows(SecurityException.class, () -> transactional.checkedExecute(task));

        verify(commitListener, times(1)).transactionComplete(State.COMMITTED);
        verify(rollbackListener, never()).transactionComplete(State.ROLLED_BACK);
    }

    @Test
    public void testExecuteRunsRollbackListenersAfterExceptionInCheckedSupplierWithCommitOnException() {
        Transactional.Listener commitListener = mock(Transactional.Listener.class);
        Transactional.Listener rollbackListener = mock(Transactional.Listener.class);

        Transactional transactional = this.buildTransactional()
            .commitOnException()
            .onCommit(commitListener)
            .onRollback(rollbackListener);

        CheckedSupplier<String, RuntimeException> task = () -> { throw new SecurityException("Kaboom"); };

        assertThrows(SecurityException.class, () -> transactional.checkedExecute(task));

        verify(commitListener, times(1)).transactionComplete(State.COMMITTED);
        verify(rollbackListener, never()).transactionComplete(State.ROLLED_BACK);
    }

    @Test
    public void testRuntimeExceptionsInCheckedRunnableDoNotTriggerCastClassException() {
        Transactional transactional = this.buildTransactional();

        CheckedRunnable<GeneralSecurityException> task = () -> {
            int val = Integer.parseInt("hello");
            throw new GeneralSecurityException("uh oh!");
        };

        assertThrows(NumberFormatException.class, () -> transactional.checkedExecute(task));
    }

    @Test
    public void testRuntimeExceptionsInCheckedSupplierDoNotTriggerCastClassException() {
        Transactional transactional = this.buildTransactional();

        CheckedSupplier<String, GeneralSecurityException> task = () -> {
            int val = Integer.parseInt("hello");
            throw new GeneralSecurityException("uh oh!");
        };

        assertThrows(NumberFormatException.class, () -> transactional.checkedExecute(task));
    }

    @Test
    public void testCheckedExceptionsInCheckedRunnableDoNotTriggerCastClassException() {
        Transactional transactional = this.buildTransactional();

        CheckedRunnable genericTask = () -> {
            boolean branch = true;
            if (branch) {
                throw new GeneralSecurityException("uh oh!");
            }

            throw new IOException("nope");
        };

        CheckedRunnable<IOException> task = (CheckedRunnable<IOException>) genericTask;

        assertThrows(GeneralSecurityException.class, () -> transactional.checkedExecute(task));
    }

    @Test
    public void testCheckedExceptionsInCheckedSupplierDoNotTriggerCastClassException() {
        Transactional transactional = this.buildTransactional();

        CheckedSupplier genericTask = () -> {
            boolean branch = true;
            if (branch) {
                throw new GeneralSecurityException("uh oh!");
            }

            throw new IOException("nope");
        };

        CheckedSupplier<String, IOException> task = (CheckedSupplier<String, IOException>) genericTask;

        assertThrows(GeneralSecurityException.class, () -> transactional.checkedExecute(task));
    }

}
