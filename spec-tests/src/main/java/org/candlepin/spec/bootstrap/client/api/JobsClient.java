/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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
package org.candlepin.spec.bootstrap.client.api;

import org.candlepin.ApiClient;
import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.resource.JobsApi;

import java.util.List;
import java.util.Set;

public class JobsClient extends JobsApi {

    /** The default amount of time we should wait for a job to terminate, in milliseconds **/
    public static final long DEFAULT_JOB_WAIT_DURATION = 60000;

    public JobsClient(ApiClient client) {
        super(client);
    }

    /**
     * Waits for a job to reach a terminal state, or for the given timeout to elapse. If the job
     * does not terminate in the specified duration, this method throws an exception.
     *
     * @param jobId
     *  the ID of the job to wait for
     *
     * @param timeout
     *  the max duration in milliseconds to wait for the job to terminate
     *
     * @throws IllegalArgumentException
     *  if jobId is null or empty, or the given timeout is not a positive integer
     *
     * @throws IllegalStateException
     *  if the job does not terminate in the allotted time
     *
     * @return
     *  an AsyncJobStatusDTO representing the final state of the job
     */
    public AsyncJobStatusDTO waitForJob(String jobId, long timeout) throws ApiException {
        if (jobId == null || jobId.isEmpty()) {
            throw new IllegalArgumentException("jobId is null or empty");
        }

        if (timeout <= 0L) {
            throw new IllegalArgumentException("timeout is not a positive integer");
        }

        Set<String> terminalStates = Set.of("FINISHED", "FAILED", "CANCELED", "ABORTED");
        long startTime = System.currentTimeMillis();

        do {
            AsyncJobStatusDTO status = this.getJobStatus(jobId);
            if (status == null) {
                // This shouldn't ever happen; getJobStatus should 404 if the job isn't found
                throw new IllegalStateException("Job status lookup returned null");
            }

            if (terminalStates.contains(status.getState())) {
                return status;
            }

            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                throw new RuntimeException("Unexpected interrupt", e);
            }
        }
        while (System.currentTimeMillis() - startTime < timeout);

        // If we don't know the job state, this should not return *anything*, as our tests are
        // written with the assumption that the job has completed successfully.
        throw new IllegalStateException("job did not terminate in the specified duration");
    }

    /**
     * Waits for a job to reach a terminal state. If the job does not terminate in the default
     * time specified, this method throws an exception.
     *
     * @param jobId
     *  the ID of the job to wait for
     *
     * @throws IllegalArgumentException
     *  if jobId is null or empty, or the given timeout is not a positive integer
     *
     * @throws IllegalStateException
     *  if the job does not terminate in the allotted time
     *
     * @return
     *  an AsyncJobStatusDTO representing the final state of the job
     */
    public AsyncJobStatusDTO waitForJob(String jobId) throws ApiException {
        return waitForJob(jobId, DEFAULT_JOB_WAIT_DURATION);
    }

    /**
     * Waits for a job to reach a terminal state, or for the given timeout to elapse. If the job
     * does not terminate in the specified duration, this method throws an exception.
     *
     * @param job
     *  the job to wait for
     *
     * @param timeout
     *  the max duration in milliseconds to wait for the job to terminate
     *
     * @throws IllegalArgumentException
     *  if job is null lacks a job ID, or the given timeout is not a positive integer
     *
     * @throws IllegalStateException
     *  if the job does not terminate in the allotted time
     *
     * @return
     *  an AsyncJobStatusDTO representing the final state of the job
     */
    public AsyncJobStatusDTO waitForJob(AsyncJobStatusDTO job, long timeout) throws ApiException {
        if (job == null || job.getId() == null || job.getId().isEmpty()) {
            throw new IllegalArgumentException("job is null, or lacks a job ID");
        }

        return waitForJob(job.getId(), timeout);
    }

    /**
     * Waits for a job to reach a terminal state. If the job does not terminate in the default
     * time specified, this method throws an exception.
     *
     * @param job
     *  the job to wait for
     *
     * @throws IllegalArgumentException
     *  if job is null or lacks a job ID
     *
     * @throws IllegalStateException
     *  if the job does not terminate in the allotted time
     *
     * @return
     *  an AsyncJobStatusDTO representing the final state of the job
     */
    public AsyncJobStatusDTO waitForJob(AsyncJobStatusDTO job) throws ApiException {
        return waitForJob(job, DEFAULT_JOB_WAIT_DURATION);
    }

    /**
     * Fetches a set of job statuses matching the given filter options
     * @param ownerKey Filter jobs based on a single owner
     * @param ids List of ID's to search over
     * @param status Filter for jobs based on status
     * @return List&lt;AsyncJobStatusDTO&gt;
     * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the response body
     */
    public List<AsyncJobStatusDTO> listMatchingJobStatusForOrg(String ownerKey, Set<String> ids,
        String status) throws ApiException {

        if (ownerKey == null) {
            throw new RuntimeException("The owner cannot be null");
        }
        return super.listJobStatuses(ids, null, (status == null ? null : Set.of(status)),
            Set.of(ownerKey), null, null, null, null, null,
            null, null, null, null);
    }
}
