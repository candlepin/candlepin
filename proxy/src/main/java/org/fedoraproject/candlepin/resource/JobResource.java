/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.resource;

import com.google.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.fedoraproject.candlepin.model.JobCurator;
import org.fedoraproject.candlepin.pinsetter.core.model.JobStatus;
import org.fedoraproject.candlepin.pinsetter.core.model.JobStatus.JobState;

/**
 * JobResource
 */
@Path("/jobs")
public class JobResource {

    private JobCurator curator;

    @Inject
    public JobResource(JobCurator curator) {
        this.curator = curator;
    }

    @GET
    @Path("/{job_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public JobStatus getStatus(@PathParam("job_id") String jobId) {
        return curator.find(jobId);
    }

    @POST
    @Path("/{job_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public JobStatus getStatusAndDeleteIfFinished(@PathParam("job_id") String jobId) {
        JobStatus status = curator.find(jobId);

        if (status != null && status.getState() == JobState.FINISHED) {
            curator.delete(status);
        }

        return status;
    }

}
