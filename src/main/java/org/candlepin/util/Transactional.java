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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.transaction.Status;



/**
 * The Transactional class represents a simple transaction wrapper around a given method or closure,
 * as well as providing an interface for performing supplemental action upon completion of the
 * transaction.
 * <p>
 * The wrapper can be reused within the context of a given operation if it is an operation that
 * needs to be performed several times, perhaps with different inputs. To facilitate this, the
 * arguments to pass into the action are provided on execution, rather than during configuration.
 * This allows the following pattern:
 *
 * <pre>{@code
 *  Transactional<String> transactional = this.curator.transactional(MyClass::some_action)
 *      .onCommit(MyClass::commit_action)
 *      .onRollback(MyClass::rollback_action)
 *
 *  for (String input : collection_of_inputs) {
 *      String result = transactional.execute(input);
 *  }
 * }</pre>
 *
 * @param <O>
 *  The output type of the action wrapped by this instance
 */
public class Transactional<O> {
    private static final Logger log = LoggerFactory.getLogger(Transactional.class);

    /**
     * An interface to execute an action within the bounds of a transaction
     *
     * @param <O>
     *  the output type of this action
     */
    @FunctionalInterface
    public interface Action<O> {

        /**
         * Called when a transaction this action is associated with has started.
         *
         * @param args
         *  the arguments provided at the start of the transaction
         *
         * @return
         *  the output of the operation (optional)
         */
        O execute(Object... args) throws Exception;
    }

    /**
     * A functional interface for listening for the completion of a transaction
     */
    @FunctionalInterface
    public interface Listener {

        /**
         * Called when a transaction this listener is registered to has completed.
         *
         * @param status
         *  the status of the transaction, can be compared to the values provided by
         *  javax.transaction.Status
         */
        void transactionComplete(int status);
    }

    /**
     * A functional interface for validating the output of an action and either committing or
     * rolling back the transaction as appropriate.
     *
     * @param <O>
     *  The output type processed by this validator
     */
    @FunctionalInterface
    public interface Validator<O> {

        /**
         * Called when the transaction action completes, and can be used to determine whether or
         * not the transaction should be committed or rolled back based on the value.
         * <p>
         * When a validator is provided to the <tt>rollbackWhen</tt> method, the transaction will
         * be rolled back if this method returns true and committed if it returns false. When
         * provided to the <tt>commitWhen</tt> method, the transaction will be committed if this
         * method returns true and rolled back if it returns false.
         *
         * @param output
         *  the output of the transaction action; may be null
         *
         * @return
         *  true if the registered transaction operation should be performed; false otherwise
         */
        boolean validate(O output);
    }

    private final EntityManager entityManager;
    private final List<Listener> commitListeners;
    private final List<Listener> rollbackListeners;
    private final List<Validator<O>> validators;

    private Action<O> action;
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

        this.commitListeners = new LinkedList<>();
        this.rollbackListeners = new LinkedList<>();
        this.validators = new LinkedList<>();

        this.commitOnException = false;
        this.exclusive = true;
    }

    /**
     * Sets the action to perform/run within a transactional boundry. If the action has already
     * been set, this method throws an exception.
     *
     * @param action
     *  the action(s) to perform
     *
     * @throws IllegalArgumentException
     *  if the provided action is null
     *
     * @throws IllegalStateException
     *  if the action has already been set
     *
     * @return
     *  this transactional wrapper
     */
    public Transactional<O> run(Action<O> action) {
        if (action == null) {
            throw new IllegalArgumentException("action is null");
        }

        if (this.action != null) {
            throw new IllegalStateException("action already set");
        }

        this.action = action;
        return this;
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
    public Transactional<O> onCommit(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is null");
        }

        this.commitListeners.add(listener);
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
    public Transactional<O> onRollback(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is null");
        }

        this.rollbackListeners.add(listener);
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
    public Transactional<O> onComplete(Listener listener) {
        this.onCommit(listener);
        this.onRollback(listener);

        return this;
    }

    /**
     * Adds a result validator that will only allow the transaction to be committed if the result
     * of the action passes validation. If the validation fails, the transaction will be rolled
     * back instead.
     * <p></p>
     * Multiple validators may be added, and a given validator may be added multiple times.
     *
     * @param validator
     *  a validator to use for testing the output of the transaction action
     *
     * @throws IllegalArgumentException
     *  if validator is null
     *
     * @return
     *  this transactional wrapper
     */
    public Transactional<O> commitIf(Validator<O> validator) {
        if (validator == null) {
            throw new IllegalArgumentException("validator is null");
        }

        this.validators.add(validator);
        return this;
    }

    /**
     * Adds a result validator that checks if the transaction should be rolled back upon completion
     * of the transactional operation. Multiple validators may be added, and a given validator may
     * be added multiple times.
     * <p></p>
     * <strong>Note:</strong> This method operates by wrapping the provided validator in another
     * validator that simply negates its output. The method name is provided purely for code clarity
     * purposes and should be avoided in favor of the <tt>commitIf</tt> method where appropriate.
     *
     * @param validator
     *  a validator to use for testing the output of the transaction action
     *
     * @throws IllegalArgumentException
     *  if validator is null
     *
     * @return
     *  this transactional wrapper
     */
    public Transactional<O> rollbackIf(Validator<O> validator) {
        if (validator == null) {
            throw new IllegalArgumentException("validator is null");
        }

        this.validators.add(output -> !validator.validate(output));
        return this;
    }

    /**
     * Sets this wrapper to automatically commit the transaction if an uncaught exception occurs.
     * By default, the wrapper will rollback the transaction on exception, but this method can be
     * used to specify the behavior.
     *
     * @return
     *  this transactional wrapper
     */
    public Transactional<O> commitOnException() {
        this.commitOnException = true;
        return this;
    }

    /**
     * Sets this wrapper to allow use of a pre-existing active transaction. When reuse mode is
     * enabled, a warning will be logged if a transactional block is executed with an active
     * transaction already present.
     *
     * @return
     *  this transactional wrapper
     */
    public Transactional<O> allowExistingTransactions() {
        this.exclusive = false;
        return this;
    }

    /**
     * Commits the transaction and notifies listeners
     */
    private void commitTransaction(EntityTransaction transaction) {
        transaction.commit();

        for (Listener listener : this.commitListeners) {
            listener.transactionComplete(Status.STATUS_COMMITTED);
        }
    }

    /**
     * Rolls back the transaction and notifies listeners
     */
    private void rollbackTransaction(EntityTransaction transaction) {
        transaction.rollback();

        for (Listener listener : this.rollbackListeners) {
            listener.transactionComplete(Status.STATUS_ROLLEDBACK);
        }
    }

    /**
     * Executes the given action within the context of an existing transaction.
     *
     * @param transaction
     *  a transaction object for the current transactional context
     *
     * @param action
     *  the action to execute transactionally
     *
     * @param args
     *  the arguments to pass through to the action
     *
     * @throws TransactionExecutionException
     *  if an exception occurs while executing the provided action
     *
     * @return
     *  the output of the given action
     */
    private O executeNested(EntityTransaction transaction, Action<O> action, Object... args) {
        // Impl note: at the time of writing, Hibernate does not support nested transactions
        String errmsg = "Transactional block executed with a transaction already started";
        if (this.exclusive) {
            throw new IllegalStateException(errmsg);
        }
        log.warn(errmsg);

        try {
            return action.execute(args);
        }
        catch (Exception e) {
            throw new TransactionExecutionException(e);
        }
    }

    /**
     * Executes the given action within the context of a new, fully-managed transaction.
     *
     * @param transaction
     *  a transaction object for the current transactional context
     *
     * @param action
     *  the action to execute transactionally
     *
     * @param args
     *  the arguments to pass through to the action
     *
     * @throws TransactionExecutionException
     *  if an exception occurs while executing the provided action
     *
     * @return
     *  the output of the given action
     */
    private O executeTransactional(EntityTransaction transaction, Action<O> action, Object... args) {
        transaction.begin();

        try {
            O output = action.execute(args);

            for (Validator<O> validator : this.validators) {
                if (!validator.validate(output)) {
                    log.debug("Transaction operation output failed validation: {}", output);

                    transaction.setRollbackOnly();
                    break;
                }
            }

            return output;
        }
        catch (Exception e) {
            if (!this.commitOnException) {
                transaction.setRollbackOnly();
            }

            throw new TransactionExecutionException(e);
        }
        finally {
            if (!transaction.isActive()) {
                String errmsg = "Transactional block completed without an active transaction";
                if (this.exclusive) {
                    throw new IllegalStateException(errmsg);
                }

                log.warn(errmsg);
            }

            if (!transaction.getRollbackOnly()) {
                this.commitTransaction(transaction);
            }
            else {
                this.rollbackTransaction(transaction);
            }
        }
    }

    /**
     * Executes this transactional block using the specified action by passing in the provided
     * arguments. The specified action will be used in place of any action previously set via the
     * call method. If no action is provided, this method will throw an exception.
     *
     * @param action
     *  the action to execute transactionally
     *
     * @param args
     *  the arguments to pass through to the action
     *
     * @throws IllegalArgumentException
     *  if no action is provided
     *
     * @throws TransactionExecutionException
     *  if an exception occurs while executing the provided action
     *
     * @return
     *  the output of the action
     */
    public O execute(Action<O> action, Object... args) {
        if (action == null) {
            throw new IllegalArgumentException("no action provided");
        }

        return this.run(action)
            .execute(args);
    }

    /**
     * Executes this transactional block, passing the specified arguments through to the previously
     * assigned action. If an action has not yet been set, this method throws an exception.
     *
     * @param args
     *  the arguments to pass through to the action
     *
     * @throws IllegalStateException
     *  if the transactional action has not yet been assigned
     *
     * @throws TransactionExecutionException
     *  if an exception occurs in the assigned action
     *
     * @return
     *  the output of the action
     */
    public O execute(Object... args) {
        if (this.action == null) {
            throw new IllegalStateException("no action provided");
        }

        EntityTransaction transaction = this.entityManager.getTransaction();
        if (transaction == null) {
            throw new IllegalStateException("Unable to fetch the current context transaction");
        }

        return !transaction.isActive() ?
            this.executeTransactional(transaction, this.action, args) :
            this.executeNested(transaction, this.action, args);
    }
}
