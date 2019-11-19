/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
 * <p></p>
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
    private static Logger log = LoggerFactory.getLogger(Transactional.class);

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
        O execute(Object... args);
    }

    /**
     * A functional interface for listening for the completion of a transaction
     */
    @FunctionalInterface
    public interface Listener {

        /**
         * Called when a transaction this listener is registered to has completed.
         */
        void transactionComplete(int status);
    }


    private final EntityManager entityManager;
    private final List<Listener> commitListeners;
    private final List<Listener> rollbackListeners;

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

        this.commitOnException = false;
        this.exclusive = true;
    }

    /**
     * Sets the action to perform within a transactional boundry. If the action has already been set,
     * this method throws an exception.
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
    public Transactional<O> wrap(Action<O> action) {
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
     * Executes this transactional, passing the specified arguments through to the action.
     *
     * @param args
     *  the args to pass through to the action
     *
     * @return
     *  the output of the action
     */
    public O execute(Object... args) {
        if (this.action == null) {
            throw new IllegalStateException("no action provided");
        }

        O output = null;
        EntityTransaction transaction = this.entityManager.getTransaction();

        if (!transaction.isActive()) {
            transaction.begin();
        }
        else {
            String errmsg = "Transactional block executed with a transaction already started";
            if (this.exclusive) {
                throw new IllegalStateException(errmsg);
            }

            log.warn(errmsg);
        }

        try {
            output = this.action.execute(args);
        }
        catch (Exception e) {
            if (!this.commitOnException) {
                transaction.setRollbackOnly();
            }

            throw e;
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

        return output;
    }

}
