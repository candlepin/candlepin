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



/**
 * The JobExecutionContext provides context-specific data and arguments to a job at the time of
 * execution.
 */
public class JobExecutionContext {
    private final AsyncJobStatus job;

    /**
     * Creates a new job execution context for the given job
     *
     * @param job
     *  the job for this execution context
     *
     * @throws IllegalArgumentException
     *  if job is null
     */
    public JobExecutionContext(AsyncJobStatus job) {
        if (job == null) {
            throw new IllegalArgumentException("job is null");
        }

        this.job = job;
    }

    /**
     * Fetches the arguments for this execution of the job
     *
     * @return
     *  a JobArguments instance with the arguments for this execution of the job
     */
    public JobArguments getJobArguments() {
        return this.job.getJobArguments();
    }

    /**
     * Fetches the name of the principal that created this job. This may be different from the
     * principal of the context in which the job is executing.
     *
     * @return
     *  the name of the principal which created the executing job
     */
    public String getPrincipalName() {
        return this.job.getPrincipalName();
    }

    /**
     * Sets the result of the job's execution. If the result is not a string, it will be serialized
     * to JSON and stored without any class or other such deserialization information.
     *
     * @param result
     *  the result to set for the job execution
     */
    public void setJobResult(Object result) {
        this.job.setJobResult(result);
    }

    /**
     * Sets the specified formatted string as the result of the job's execution.
     *
     * @param format
     *  the format string to use
     *
     * @param args
     *  the arguments to use to construct the formatted result string
     */
    public void setJobResult(String format, Object... args) {
        // TODO: Change this to use a context-specific I18n instance so we can translate output for
        // strings built in this way.
        this.setJobResult(String.format(format, args));
    }

}
