/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.any;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.transaction.Status;



public class TransactionalTest  {

    /**
     * Test class that lets us control transaction state for testing various scenarios
     */
    private static class TestTransaction implements EntityTransaction {

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
    private void init() throws Exception {
        this.entityManager = mock(EntityManager.class);
        this.transaction = spy(new TestTransaction());

        doReturn(this.transaction).when(this.entityManager).getTransaction();
    }

    private Transactional buildTransactional() {
        return new Transactional(this.entityManager);
    }

    @Test
    public void testActionAssignmentOnlyOnce() {
        Transactional transactional = this.buildTransactional();

        transactional.wrap(args -> "action");

        assertThrows(IllegalStateException.class, () -> transactional.wrap(args -> "action"));
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
    public void testCannotExecuteWithoutAction() {
        Transactional transactional = this.buildTransactional();

        assertThrows(IllegalStateException.class, () -> transactional.execute());
    }

    @Test
    public void testExecuteRunsActionWithNoArgs() {
        Transactional transactional = this.buildTransactional();
        Transactional.Action action = mock(Transactional.Action.class);
        Object expected = "output";

        doReturn(expected).when(action).execute(any());

        Object actual = transactional.wrap(action)
            .execute();

        verify(action, times(1)).execute(new Object[0]);
        assertEquals(expected, actual);
    }

    @Test
    public void testExecuteRunsActionWithArgs() {
        Transactional transactional = this.buildTransactional();
        Transactional.Action action = mock(Transactional.Action.class);
        Object expected = "output";

        doReturn(expected).when(action).execute(any());

        Object actual = transactional.wrap(action)
            .execute("arg1", "arg2", "arg3");

        verify(action, times(1)).execute(new Object[] { "arg1", "arg2", "arg3" });
        assertEquals(expected, actual);
    }

    @Test
    public void testExecutionFailsOnActiveTransaction() {
        Transactional transactional = this.buildTransactional()
            .wrap(args -> "action");

        this.transaction.begin();

        assertThrows(IllegalStateException.class, () -> { transactional.execute(); });
    }

    @Test
    public void testExecutionCanUseActiveTransactions() {
        Transactional transactional = this.buildTransactional()
            .wrap(args -> "action")
            .allowExistingTransactions();

        this.transaction.begin();
        reset(this.transaction);

        Object output = transactional.execute();

        assertEquals("action", output);
        verify(this.transaction, never()).begin();
        verify(this.transaction, times(1)).commit();
    }

    @Test
    public void testRollbackOnUncaughtActionException() {
        Transactional transactional = this.buildTransactional();
        Transactional.Action action = mock(Transactional.Action.class);

        transactional.wrap(action);

        doThrow(new SecurityException("kaboom")).when(action).execute(any());
        assertThrows(SecurityException.class, () -> transactional.execute());

        verify(this.transaction, times(1)).begin();
        verify(this.transaction, times(1)).rollback();
    }

    @Test
    public void testExecuteRunsCommitListenersAfterExecution() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener = mock(Transactional.Listener.class);

        transactional.wrap(args -> "action")
            .onCommit(listener)
            .execute();

        verify(listener, times(1)).transactionComplete(Status.STATUS_COMMITTED);
    }

    @Test
    public void testExecuteRunsMultipleCommitListenersAfterExecution() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener1 = mock(Transactional.Listener.class);
        Transactional.Listener listener2 = mock(Transactional.Listener.class);

        transactional.wrap(args -> "action")
            .onCommit(listener1)
            .onCommit(listener2)
            .execute();

        verify(listener1, times(1)).transactionComplete(Status.STATUS_COMMITTED);
        verify(listener2, times(1)).transactionComplete(Status.STATUS_COMMITTED);
    }

    @Test
    public void testExecuteRunsIdenticalCommitListenersAfterExecution() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener = mock(Transactional.Listener.class);

        transactional.wrap(args -> "action")
            .onCommit(listener)
            .onCommit(listener)
            .execute();

        verify(listener, times(2)).transactionComplete(Status.STATUS_COMMITTED);
    }

    @Test
    public void testExecuteRunsRollbackListenersAfterExecution() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener = mock(Transactional.Listener.class);

        transactional.wrap(args -> { transaction.setRollbackOnly(); return null; })
            .onRollback(listener)
            .execute();

        verify(listener, times(1)).transactionComplete(Status.STATUS_ROLLEDBACK);
    }

    @Test
    public void testExecuteRunsMultipleRollbackListenersAfterExecution() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener1 = mock(Transactional.Listener.class);
        Transactional.Listener listener2 = mock(Transactional.Listener.class);

        transactional.wrap(args -> { transaction.setRollbackOnly(); return null; })
            .onRollback(listener1)
            .onRollback(listener2)
            .execute();

        verify(listener1, times(1)).transactionComplete(Status.STATUS_ROLLEDBACK);
        verify(listener2, times(1)).transactionComplete(Status.STATUS_ROLLEDBACK);
    }

    @Test
    public void testExecuteRunsIdenticalRollbackListenersAfterExecution() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener = mock(Transactional.Listener.class);

        transactional.wrap(args -> { transaction.setRollbackOnly(); return null; })
            .onRollback(listener)
            .onRollback(listener)
            .execute();

        verify(listener, times(2)).transactionComplete(Status.STATUS_ROLLEDBACK);
    }

    @Test
    public void testExceptionWhenTransactionCommittedDuringExclusiveExecution() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener = mock(Transactional.Listener.class);

        transactional.wrap(args -> { transaction.commit(); return null; });

        assertThrows(IllegalStateException.class, () -> transactional.execute());
    }

    @Test
    public void testExceptionWhenTransactionRolledBackDuringExclusiveExecution() {
        Transactional transactional = this.buildTransactional();
        Transactional.Listener listener = mock(Transactional.Listener.class);

        transactional.wrap(args -> { transaction.rollback(); return null; });

        assertThrows(IllegalStateException.class, () -> transactional.execute());
    }

}
