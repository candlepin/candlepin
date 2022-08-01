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

    /** The default amount of time we should wait for a job to finish processing **/
    private static final long DEFAULT_TIMEOUT_MILLISECONDS = 60000;

    public JobsClient(ApiClient client) {
        super(client);
    }

    @Override
    public AsyncJobStatusDTO scheduleJob(String jobKey) throws ApiException {
        return super.scheduleJob(jobKey);
    }

    /**
     * Waits for a given job to finish processing based on the provided job Id.
     *
     * @param jobId - Id of the job to wait for.
     * @return the status of the job or null if the job has timed out.
     * @throws ApiException
     */
    public AsyncJobStatusDTO waitForJobToComplete(String jobId)
        throws ApiException {
        return waitForJobToComplete(jobId, DEFAULT_TIMEOUT_MILLISECONDS);
    }

    /**
     * Waits for a given job to finish processing based on the provided job Id.
     *
     * @param jobId   - Id of the job to wait for.
     * @param timeout - max duration in milliseconds to wait for the job to finish.
     * @return the status of the job or null if the job has timed out.
     * @throws ApiException
     */
    public AsyncJobStatusDTO waitForJobToComplete(String jobId, long timeout)
        throws ApiException {
        if (jobId == null || jobId.length() == 0) {
            throw new IllegalArgumentException("Job Id must not be null or empty.");
        }

        if (timeout <= 0L) {
            throw new IllegalArgumentException("Timeout must be greater than 0 milliseconds.");
        }

        Set<String> terminalStates = Set.of("FINISHED", "FAILED", "CANCELED", "ABORTED");
        long startTime = System.currentTimeMillis();
        long elapsedMilliseconds = 0L;
        AsyncJobStatusDTO status = null;
        while (elapsedMilliseconds <= timeout) {
            status = this.getJobStatus(jobId);
            if (status == null) {
                return status;
            }

            if (terminalStates.contains(status.getState())) {
                return status;
            }

            // Wait before trying again
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            elapsedMilliseconds = System.currentTimeMillis() - startTime;
        }

        return null;
    }

    /**
     * Fetches a set of job statuses matching the given filter options
     * @param ownerKey Filter jobs based on a single owner
     * @param ids List of ID's to search over
     * @param status Filter for jobs based on status
     * @return List&lt;AsyncJobStatusDTO&gt;
     * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the response body
     */
    public List<AsyncJobStatusDTO> listMatchingJobStatusForOrg(String ownerKey,
        Set<String> ids, String status) throws ApiException {
        if (ownerKey == null) {
            throw new RuntimeException("The owner cannot be null");
        }
        return super.listJobStatuses(ids, null, (status == null ? null : Set.of(status)),
            Set.of(ownerKey), null, null, null, null, null,
            null, null, null, null);
    }
}
