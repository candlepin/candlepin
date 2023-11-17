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
package org.candlepin.spec.bootstrap.assertions;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;

import org.assertj.core.api.AbstractAssert;

/**
 * A set of assertions for verification of job status
 */
public class JobStatusAssert extends AbstractAssert<JobStatusAssert, AsyncJobStatusDTO> {
    public JobStatusAssert(AsyncJobStatusDTO status) {
        super(status, JobStatusAssert.class);
    }

    /**
     * Entry point to the job assertions.
     *
     * @param actual job to be asserted
     * @return assert instance
     */
    public static JobStatusAssert assertThatJob(AsyncJobStatusDTO actual) {
        return new JobStatusAssert(actual);
    }

    /**
     * Verifies whether the job is in the finished state.
     *
     * @return this instance
     */
    public JobStatusAssert isFinished() {
        isInState("FINISHED");
        return this;
    }

    /**
     * Verifies whether the job is in the failed state.
     *
     * @return this instance
     */
    public JobStatusAssert isFailed() {
        isInState("FAILED");
        return this;
    }

    /**
     * Verifies whether the job is in the canceled state.
     *
     * @return this instance
     */
    public JobStatusAssert isCanceled() {
        isInState("CANCELED");
        return this;
    }

    /**
     * Verifies whether the job result contains the given text.
     *
     * @return this instance
     */
    public JobStatusAssert contains(String text) {
        String resultData = getResultAsString();
        if (resultData == null || !resultData.contains(text)) {
            failWithMessage("Expected job result to contain %s but it does not.", text);
        }
        return this;
    }

    /**
     * Verifies whether the job result does not contain the given text.
     *
     * @return this instance
     */
    public JobStatusAssert doesNotContain(String text) {
        String resultData = getResultAsString();
        if (resultData == null || resultData.contains(text)) {
            failWithMessage("Expected job result to not contain %s but it does.", text);
        }
        return this;
    }

    private String getResultAsString() {
        Object resultData = this.actual.getResultData();
        if (resultData != null) {
            return resultData.toString();
        }
        return null;
    }

    private void isInState(String expectedState) {
        if (this.actual.getState() == null || !actual.getState().equalsIgnoreCase(expectedState)) {
            failWithMessage("Expected job status to be %s but was %s",
                expectedState, actual.getState());
        }
    }

}
