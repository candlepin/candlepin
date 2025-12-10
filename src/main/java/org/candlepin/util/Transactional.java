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

import org.candlepin.util.function.CheckedRunnable;
import org.candlepin.util.function.CheckedSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;



/**
 * The Transactional class represents a simple transaction wrapper around a given method or lambda, as well
 * as providing an interface for performing supplemental action upon completion of the transaction.
 * <p>
 * The wrapper can be reused within the context of a given operation if it is an operation that needs to be
 * performed several times, perhaps with different inputs, blocks, or tasks. To facilitate this, the
 * Transactional object can be configured, and then the desired task provided to the execute method:
 *
 * <pre>{@code
 *  Transactional<String> transactional = this.curator.transactional()
 *      .onCommit(MyClass::commit_action)
 *      .onRollback(MyClass::rollback_action)
 *
 *  for (String input : collection_of_inputs) {
 *      String result = transactional.execute(() -> myTask(input));
 *  }
 * }</pre>
 */
public class Transactional {
    private static final Logger log = LoggerFactory.getLogger(Transactional.class);

    /**
     * Logical transaction states, loosely mapped to the detected state of the underlying transaction. These
     * values are provided to listeners upon completion of a transaction.
     */
    public static enum State {
        /** Used when the transaction was successfully committed */
        COMMITTED,

        /** Used when the transaction was rolled back, usually as the result of an uncaught exception */
        ROLLED_BACK
    }

    /**
     * A functional interface for listening for the completion of a transaction
     */
    @FunctionalInterface
    public interface Listener {
        /**
         * Called when a transaction this listener is registered to has completed.
         *
         * @param state
         *  the status of the transaction, can be compared to the values provided by
         *  jakarta.transaction.Status
         */
        void transactionComplete(State state);
    }

    private final EntityManager entityManager;
    private final Map<State, List<Listener>> listenerMap;

    private boolean commitOnException;
    private boolean exclusive;

    /**
     * Creates a new Transactional wrapper which will use the specified EntityManager for performing
     * session/transaction management.
     *
     * @param entityManager
     *  the EntityManager to use
     *
     * @throws IllegalArgumentException
     *  if the specified entity manager is null
     */
    public Transactional(EntityManager entityManager) {
        if (entityManager == null) {
            throw new IllegalArgumentException("entityManager is null");
        }

        this.entityManager = entityManager;

        this.listenerMap = new EnumMap<>(State.class);

        this.commitOnException = false;
        this.exclusive = true;
    }

    /**
     * Adds a supplemental action to perform only upon successful committal of the transaction.
     * Multiple listeners may be added, and a given listener may be added multiple times.
     *
     * @param listener
     *  the transaction listener to trigger on commit
     *
     * @throws IllegalArgumentException
     *  if listener is null
     *
     * @return
     *  this transactional wrapper
     */
    public Transactional onCommit(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is null");
        }

        if (!this.exclusive) {
            throw new IllegalStateException(
                "Transaction listeners cannot be used when nested transactions are permitted");
        }

        this.listenerMap.computeIfAbsent(State.COMMITTED, key -> new ArrayList<>())
            .add(listener);

        return this;
    }

    /**
     * Adds a supplemental action to perform only upon rollback the transaction.
     * Multiple listeners may be added, and a given listener may be added multiple times.
     *
     * @param listener
     *  the transaction listener to trigger on rollback
     *
     * @throws IllegalArgumentException
     *  if listener is null
     *
     * @return
     *  this transactional wrapper
     */
    public Transactional onRollback(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is null");
        }

        if (!this.exclusive) {
            throw new IllegalStateException(
                "Transaction listeners cannot be used when nested transactions are permitted");
        }

        this.listenerMap.computeIfAbsent(State.ROLLED_BACK, key -> new ArrayList<>())
            .add(listener);

        return this;
    }

    /**
     * Adds a supplemental action to perform upon completion of the transaction -- successful or
     * otherwise. Multiple listeners may be added, and a given listener may be added multiple times.
     *
     * @param listener
     *  the transaction listener to trigger on completion
     *
     * @throws IllegalArgumentException
     *  if listener is null
     *
     * @return
     *  this transactional wrapper
     */
    public Transactional onComplete(Listener listener) {
        this.onCommit(listener);
        this.onRollback(listener);

        return this;
    }

    /**
     * Sets this wrapper to automatically commit the transaction if an uncaught exception occurs.
     * By default, the wrapper will rollback the transaction on exception, but this method can be
     * used to modify that behavior.
     *
     * @return
     *  this transactional wrapper
     */
    public Transactional commitOnException() {
        this.commitOnException = true;
        return this;
    }

    /**
     * Permits executing this transactional within the bounds of an existing transaction. Enabling this
     * "non-exclusive" mode will omit any transaction management during execution, allowing the execution
     * of the block regardless of the current transaction state.
     * <p>
     * <strong>Warning:</strong> Transaction listeners cannot be used in non-exclusive mode, as it becomes
     * impossible to know when the transaction will terminate once execution leaves the bounds of this object.
     *
     * @return
     *  this transactional wrapper
     */
    public Transactional allowExistingTransactions() {
        // Impl note:
        // At the time of writing, JPA's API does not provide a means for hooking into a transaction from the
        // EntityManager, nor does it make it clear if using a TransactionManager at the same time as the
        // EntityManager will cause problems or otherwise have side effects. Because it is unknown what latent
        // effects using both could have, I am opting to simply disallow using non-exclusive mode with
        // listeners. We can revisit in the future as use cases demand.

        if (!this.listenerMap.isEmpty()) {
            throw new IllegalStateException("Nested transactions cannot be used with transaction listeners");
        }

        this.exclusive = false;
        return this;
    }

    /**
     * Notifies listeners of a completed transaction. The provided state is the state to be passed to
     * registered listeners.
     *
     * @param state
     *  the state of the transaction
     */
    private void notifyListeners(State state) {
        this.listenerMap.getOrDefault(state, List.of())
            .forEach(listener -> listener.transactionComplete(state));
    }

    /**
     * Executes the given task using a new, managed transaction. This method is should not be called directly
     * and should only ever be executed as a delegate of the executeTransaction method.
     *
     * @param transaction
     *  the transaction object to use to manage the underlying database transaction
     *
     * @param task
     *  the task to execute within the bounds of the transaction
     *
     * @return
     *  the value returned by the executed task
     */
    @SuppressWarnings("unchecked")
    private <T, E extends Exception> T executeManagedTransaction(EntityTransaction transaction,
        CheckedSupplier<T, E> task) throws E {

        transaction.begin();

        try {
            return task.get();
        }
        catch (Exception e) {
            if (transaction.isActive() && !this.commitOnException) {
                transaction.setRollbackOnly();
            }

            throw (E) e;
        }
        finally {
            if (!transaction.isActive()) {
                throw new IllegalStateException(
                    "Transactional block completed without an active transaction");
            }

            if (!transaction.getRollbackOnly()) {
                transaction.commit();
                this.notifyListeners(State.COMMITTED);
            }
            else {
                transaction.rollback();
                this.notifyListeners(State.ROLLED_BACK);
            }
        }
    }

    /**
     * Executes the given task using the existing transaction. This method is should not be called directly
     * and should only ever be executed as a delegate of the executeTransaction method.
     *
     * @param transaction
     *  the transaction object to use to manage the underlying database transaction
     *
     * @param task
     *  the task to execute within the bounds of the transaction
     *
     * @return
     *  the value returned by the executed task
     */
    @SuppressWarnings("unchecked")
    private <T, E extends Exception> T executeNestedTransaction(EntityTransaction transaction,
        CheckedSupplier<T, E> task) throws E {

        // Impl note: at the time of writing, JPA/Hibernate does not support nested transactions, nor
        // savepoints. Our only real recourse is pretending the transaction doesn't exist.
        if (this.exclusive) {
            String errmsg = "Transactional block executed with a transaction already active";
            log.error(errmsg);
            throw new IllegalStateException(errmsg);
        }
        else {
            log.warn("Transaction already started; executing block as a nested transaction");
        }

        try {
            return task.get();
        }
        catch (Exception e) {
            if (transaction.isActive() && !this.commitOnException) {
                transaction.setRollbackOnly();
            }

            throw (E) e;
        }

        // Impl note: this feels weird to omit, but since we don't know when the transaction will complete,
        // we can't fire off transaction listeners. The alternative is firing listeners preemptively with
        // fake statuses or a special state indicating the transaction didn't yet complete, but that doesn't
        // really achieve what they're intended to do.
    }

    /**
     * Executes the given task within the bounds of a transaction.
     *
     * @param transaction
     *  the transaction object to use to manage the underlying database transaction
     *
     * @param task
     *  the task to execute within the bounds of the transaction
     *
     * @return
     *  the value returned by the executed task
     */
    private <T, E extends Exception> T executeTransaction(CheckedSupplier<T, E> task) throws E {
        EntityTransaction transaction = this.entityManager.getTransaction();
        if (transaction == null) {
            throw new IllegalStateException("Unable to fetch the current context transaction");
        }

        return !transaction.isActive() ?
            this.executeManagedTransaction(transaction, task) :
            this.executeNestedTransaction(transaction, task);
    }

    /**
     * Executes the given task within the bounds of a transaction.
     *
     * @param task
     *  the task to execute
     *
     * @throws IllegalArgumentException
     *  if the given task is null
     */
    public void execute(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("no task provided");
        }

        CheckedSupplier<Object, TransactionExecutionException> wrapper = () -> {
            try {
                task.run();
                return null;
            }
            catch (Exception e) {
                throw new TransactionExecutionException(e);
            }
        };

        this.executeTransaction(wrapper);
    }

    /**
     * Executes the given task within the bounds of a transaction.
     *
     * @param task
     *  the task to execute
     *
     * @throws IllegalArgumentException
     *  if the given task is null
     *
     * @return
     *  the value returned by the given task
     */
    public <T> T execute(Supplier<T> task) {
        if (task == null) {
            throw new IllegalArgumentException("no task provided");
        }

        CheckedSupplier<T, TransactionExecutionException> wrapper = () -> {
            try {
                return task.get();
            }
            catch (Exception e) {
                throw new TransactionExecutionException(e);
            }
        };

        return this.executeTransaction(wrapper);
    }

    /**
     * Executes the given task within the bounds of a transaction.
     *
     * @param task
     *  the task to execute
     *
     * @throws IllegalArgumentException
     *  if the given task is null
     */
    public <E extends Exception> void checkedExecute(CheckedRunnable<E> task) throws E {
        if (task == null) {
            throw new IllegalArgumentException("no action provided");
        }

        CheckedSupplier<Object, E> wrapper = () -> {
            task.run();
            return null;
        };

        this.executeTransaction(wrapper);
    }

    /**
     * Executes the given task within the bounds of a transaction.
     *
     * @param task
     *  the task to execute
     *
     * @throws IllegalArgumentException
     *  if the given task is null
     *
     * @return
     *  the value returned by the given task
     */
    public <T, E extends Exception> T checkedExecute(CheckedSupplier<T, E> task) throws E {
        if (task == null) {
            throw new IllegalArgumentException("no action provided");
        }

        return this.executeTransaction(task);
    }

}
