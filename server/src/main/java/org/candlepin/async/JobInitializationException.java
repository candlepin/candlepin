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
 * The JobInitializationException represents an error that occurs before a job is
 * actually executed by the JobManager. The JobManager should throw this exeception
 * anytime that a job is invalid and can not be run. The JobMessageReceiver will
 * discard the associated job message when it encounters this exception, so it should
 * only be thrown in cases where the job is no longer applicable.
 *
 * <pre>
 *     Example scenarios include:
 *         - Job status could not be found before execution.
 *         - Job was determined cancelled before execution.
 * </pre>
 */
public class JobInitializationException extends JobException {

    /**
     * Create an instance of this exception with the specified message.
     *
     * @param message a message describing the cause of the exception.
     */
    public JobInitializationException(String message) {
        super(message);
    }
}
