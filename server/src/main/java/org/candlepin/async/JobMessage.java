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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * The JobMessage class represents a message to be sent between Candlepin's job management system
 * and the messaging system connecting various Candlepin nodes or compatible systems.
 */
public class JobMessage {
    private String jobId;
    private String jobKey;

    @JsonCreator
    public JobMessage(@JsonProperty("jobId") String jobId, @JsonProperty("jobKey") String jobKey) {
        // Impl note:
        // While in most cases I would aggressively check for nulls or empty strings and throw an
        // exception, we don't want to do so here, as such exceptions get messy with how
        // deserialization works, making debugging much harder than it needs to be.
        //
        // Instead, we should verify that the values aren't null immediately after creation in
        // the area that is about to be using or handing off the job message instance. This will
        // allow for cleaner debugging in the event something goes sideways.

        this.jobId = jobId;
        this.jobKey = jobKey;
    }

    /**
     * Fetches the ID of the job represented by this message
     *
     * @return
     *  The ID of the job represented by this message, or null if the ID is not available
     */
    public String getJobId() {
        return this.jobId;
    }

    /**
     * Fetches the job key for the job represented by this message
     *
     * @return
     *  The job key for the job represented by this method, or null if the job key is not available
     */
    public String getJobKey() {
        return this.jobKey;
    }

    @Override
    public String toString() {
        return String.format("JobMessage [id: %s, key: %s]", this.jobKey, this.jobId);
    }
}
