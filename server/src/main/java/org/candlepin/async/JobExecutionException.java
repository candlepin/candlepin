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



/**
 * The JobExecutionException represents an unexpected error that occurred during the runtime of a
 * job. This exception may be thrown manually by the job itself, or generated if a runtime
 * exception bubbles up to the job management framework.
 */
public class JobExecutionException extends Exception {

    /**
     * Constructs a new exception with null as its detail message. The cause is not initialized,
     * and may subsequently be initialized by a call to initCause(java.lang.Throwable).
     */
    public JobExecutionException() {
        super();
    }

    /**
     * Constructs a new exception with the specified detail message. The cause is not initialized,
     * and may subsequently be initialized by a call to initCause(java.lang.Throwable).
     *
     * @param message
     *  the detail message. The detail message is saved for later retrieval by the getMessage()
     *  method.
     */
    public JobExecutionException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified cause and a detail message of
     * <tt>(cause == null ? null : cause.toString())</tt> (which typically contains the and
     * detail message of cause). This constructor is useful for exceptions that are little more
     * than wrappers for other throwables (for example, PrivilegedActionException).
     *
     * @param cause
     *  the cause (which is saved for later retrieval by the Throwable.getCause() method). A null
     *  value is permitted, and indicates that the cause is nonexistent or unknown.
     */
    public JobExecutionException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     * <p></p>
     * Note that the detail message associated with cause is not automatically incorporated in this
     * exception's detail message.
     *
     * @param message
     *  the detail message. The detail message is saved for later retrieval by the getMessage()
     *  method.
     *
     * @param cause
     *  the cause (which is saved for later retrieval by the Throwable.getCause() method). A null
     *  value is permitted, and indicates that the cause is nonexistent or unknown.
     */
    public JobExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

}
