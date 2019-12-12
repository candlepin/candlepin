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
 * The JobExecutionContext provides context-specific data and arguments to a job at the time of
 * execution.
 */
public interface JobExecutionContext {

    /**
     * Fetches the arguments for this execution of the job
     *
     * @return
     *  a JobArguments instance with the arguments for this execution of the job
     */
    JobArguments getJobArguments();

    /**
     * Fetches the name of the principal that created this job. This may be different from the
     * principal of the context in which the job is executing.
     *
     * @return
     *  the name of the principal which created the executing job
     */
    String getPrincipalName();


    // TODO: Add other stuff here as necessary

}
