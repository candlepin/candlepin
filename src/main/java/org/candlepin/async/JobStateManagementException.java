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

import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatus.JobState;



/**
 * The JobStateManagementException represents an unexpected error that occurred while attempting to
 * update a given job's state. The job is now in an unexpected, bad state and some recovery should
 * be attempted to correct it.
 */
public class JobStateManagementException extends JobException {

    protected final AsyncJobStatus status;
    protected final JobState initialState;
    protected final JobState intendedState;


    /**
     * Constructs a new exception with null as its detail message. The cause is not initialized,
     * and may subsequently be initialized by a call to initCause(java.lang.Throwable).
     *
     * @param status
     *  The AsyncJobStatus instance being updated at the time of failure
     *
     * @param initialState
     *  The initial state of the job status before any changes were made leading to the exception
     *
     * @param intendedState
     *  The intended state the job status was transitioning to at the time of the exception
     */
    public JobStateManagementException(AsyncJobStatus status, JobState initialState, JobState intendedState) {
        this(status, initialState, intendedState, null, null, false);
    }

    /**
     * Constructs a new exception with the specified detail message. The cause is not initialized,
     * and may subsequently be initialized by a call to initCause(java.lang.Throwable).
     *
     * @param status
     *  The AsyncJobStatus instance being updated at the time of failure
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
    public JobStateManagementException(AsyncJobStatus status, JobState initialState, JobState intendedState,
        String message) {

        this(status, initialState, intendedState, message, null, false);
    }

    /**
     * Constructs a new exception with the specified cause and a detail message of
     * <tt>(cause == null ? null : cause.toString())</tt> (which typically contains the and
     * detail message of cause). This constructor is useful for exceptions that are little more
     * than wrappers for other throwables (for example, PrivilegedActionException).
     *
     * @param status
     *  The AsyncJobStatus instance being updated at the time of failure
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
    public JobStateManagementException(AsyncJobStatus status, JobState initialState, JobState intendedState,
        Throwable cause) {

        this(status, initialState, intendedState, null, cause, false);
    }

    /**
     * Constructs a new exception with null as its detail message. The cause is not initialized,
     * and may subsequently be initialized by a call to initCause(java.lang.Throwable).
     *
     * @param status
     *  The AsyncJobStatus instance being updated at the time of failure
     *
     * @param initialState
     *  The initial state of the job status before any changes were made leading to the exception
     *
     * @param intendedState
     *  The intended state the job status was transitioning to at the time of the exception
     *
     * @param terminal
     *  whether or not the exception is terminal or non-recoverable and the job should not be
     *  retried and any associated job messages should be discarded
     */
    public JobStateManagementException(AsyncJobStatus status, JobState initialState, JobState intendedState,
        boolean terminal) {

        this(status, initialState, intendedState, null, null, terminal);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     * <p></p>
     * Note that the detail message associated with cause is not automatically incorporated in this
     * exception's detail message.
     *
     * @param status
     *  The AsyncJobStatus instance being updated at the time of failure
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
    public JobStateManagementException(AsyncJobStatus status, JobState initialState, JobState intendedState,
        String message, Throwable cause) {

        this(status, initialState, intendedState, message, cause, false);
    }

    /**
     * Constructs a new exception with the specified detail message. The cause is not initialized,
     * and may subsequently be initialized by a call to initCause(java.lang.Throwable).
     *
     * @param status
     *  The AsyncJobStatus instance being updated at the time of failure
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
     * @param terminal
     *  whether or not the exception is terminal or non-recoverable and the job should not be
     *  retried and any associated job messages should be discarded
     */
    public JobStateManagementException(AsyncJobStatus status, JobState initialState, JobState intendedState,
        String message, boolean terminal) {

        this(status, initialState, intendedState, message, null, terminal);
    }

    /**
     * Constructs a new exception with the specified cause and a detail message of
     * <tt>(cause == null ? null : cause.toString())</tt> (which typically contains the and
     * detail message of cause). This constructor is useful for exceptions that are little more
     * than wrappers for other throwables (for example, PrivilegedActionException).
     *
     * @param status
     *  The AsyncJobStatus instance being updated at the time of failure
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
     *
     * @param terminal
     *  whether or not the exception is terminal or non-recoverable and the job should not be
     *  retried and any associated job messages should be discarded
     */
    public JobStateManagementException(AsyncJobStatus status, JobState initialState, JobState intendedState,
        Throwable cause, boolean terminal) {

        this(status, initialState, intendedState, null, cause, terminal);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     * <p></p>
     * Note that the detail message associated with cause is not automatically incorporated in this
     * exception's detail message.
     *
     * @param status
     *  The AsyncJobStatus instance being updated at the time of failure
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
     *
     * @param terminal
     *  whether or not the exception is terminal or non-recoverable and the job should not be
     *  retried and any associated job messages should be discarded
     */
    public JobStateManagementException(AsyncJobStatus status, JobState initialState, JobState intendedState,
        String message, Throwable cause, boolean terminal) {

        super(message, cause, terminal);

        this.status = status;
        this.initialState = initialState;
        this.intendedState = intendedState;
    }

    /**
     * Fetches the job status instance that failed to have its state updated properly.
     *
     * @return
     *  the AsyncJobStatus instance that failed to update
     */
    public AsyncJobStatus getJobStatus() {
        return this.status;
    }

    /**
     * Fetches the initial state of the job status before the most recent state change was attempted.
     *
     * @return
     *  the initial state of the AsyncJobStatus instance before the update failed
     */
    public JobState getInitialState() {
        return this.initialState;
    }

    /**
     * Fetches the intended final state of the job status before the failure occurred.
     *
     * @return
     *  the intended final state of the AsyncJobStatus instance before the update failed
     */
    public JobState getIntendedState() {
        return this.intendedState;
    }
}
