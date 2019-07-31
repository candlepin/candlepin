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
package org.candlepin.async;

import org.candlepin.async.JobManager.ManagerState;



/**
 * The StateManagementException represents an unexpected error that occurred while attempting to
 * update the job manager's state. The manager may be left in an unexpected, bad state and some
 * recovery should be attempted to correct it.
 */
public class StateManagementException extends RuntimeException {

    protected final ManagerState initialState;
    protected final ManagerState intendedState;


    /**
     * Constructs a new exception with null as its detail message. The cause is not initialized,
     * and may subsequently be initialized by a call to initCause(java.lang.Throwable).
     *
     * @param initialState
     *  The initial state of the job status before any changes were made leading to the exception
     *
     * @param intendedState
     *  The intended state the job status was transitioning to at the time of the exception
     */
    public StateManagementException(ManagerState initialState, ManagerState intendedState) {
        this(initialState, intendedState, null, null);
    }

    /**
     * Constructs a new exception with the specified detail message. The cause is not initialized,
     * and may subsequently be initialized by a call to initCause(java.lang.Throwable).
     *
     * @param initialState
     *  The initial state of the job status before any changes were made leading to the exception
     *
     * @param intendedState
     *  The intended state the job status was transitioning to at the time of the exception
     *
     * @param message
     *  the detail message. The detail message is saved for later retrieval by the getMessage()
     *  method.
     */
    public StateManagementException(ManagerState initialState, ManagerState intendedState, String message) {
        this(initialState, intendedState, message, null);
    }

    /**
     * Constructs a new exception with the specified cause and a detail message of
     * <tt>(cause == null ? null : cause.toString())</tt> (which typically contains the and
     * detail message of cause). This constructor is useful for exceptions that are little more
     * than wrappers for other throwables (for example, PrivilegedActionException).
     *
     * @param initialState
     *  The initial state of the job status before any changes were made leading to the exception
     *
     * @param intendedState
     *  The intended state the job status was transitioning to at the time of the exception
     *
     * @param cause
     *  the cause (which is saved for later retrieval by the Throwable.getCause() method). A null
     *  value is permitted, and indicates that the cause is nonexistent or unknown.
     */
    public StateManagementException(ManagerState initialState, ManagerState intendedState,
        Throwable cause) {

        this(initialState, intendedState, null, cause);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     * <p></p>
     * Note that the detail message associated with cause is not automatically incorporated in this
     * exception's detail message.
     *
     * @param initialState
     *  The initial state of the job status before any changes were made leading to the exception
     *
     * @param intendedState
     *  The intended state the job status was transitioning to at the time of the exception
     *
     * @param message
     *  the detail message. The detail message is saved for later retrieval by the getMessage()
     *  method.
     *
     * @param cause
     *  the cause (which is saved for later retrieval by the Throwable.getCause() method). A null
     *  value is permitted, and indicates that the cause is nonexistent or unknown.
     */
    public StateManagementException(ManagerState initialState, ManagerState intendedState,
        String message, Throwable cause) {

        super(message, cause);

        this.initialState = initialState;
        this.intendedState = intendedState;
    }

    /**
     * Fetches the initial state of the job manager before the most recent state change was attempted.
     *
     * @return
     *  the initial state of the job manager before the update failed
     */
    public ManagerState getInitialState() {
        return this.initialState;
    }

    /**
     * Fetches the intended final state of the job manager before the failure occurred.
     *
     * @return
     *  the intended final state of the job manager before the update failed
     */
    public ManagerState getIntendedState() {
        return this.intendedState;
    }
}
