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
import java.util.Collection;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.IseException;
import org.fedoraproject.candlepin.model.JobCurator;
import org.fedoraproject.candlepin.model.SchedulerStatus;
import org.fedoraproject.candlepin.pinsetter.core.PinsetterException;
import org.fedoraproject.candlepin.pinsetter.core.PinsetterKernel;
import org.fedoraproject.candlepin.pinsetter.core.model.JobStatus;
import org.fedoraproject.candlepin.pinsetter.core.model.JobStatus.JobState;

/**
 * JobResource
 */
@Path("/jobs")
public class JobResource {

    private JobCurator curator;
    private PinsetterKernel pk;
    private static String PAUSED_STATUS = "paused";
    private static String UNPAUSED_STATUS = "unpaused";
    

    @Inject
    public JobResource(JobCurator curator, PinsetterKernel pk) {
        this.curator = curator;
        this.pk = pk;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Collection<JobStatus> getStatuses(@QueryParam("owner") String ownerKey) {
        if (ownerKey == null || ownerKey.isEmpty()) {
            throw new BadRequestException("You must specify an owner key.");
        }

        return this.curator.findByOwnerKey(ownerKey);
    }
    
    @GET
    @Path("scheduler")
    @Produces(MediaType.APPLICATION_JSON)
    public SchedulerStatus getSchedulerStatus() {
        SchedulerStatus ss = new SchedulerStatus();
        try {
            ss.setRunning(pk.getSchedulerStatus());
        }
        catch (PinsetterException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return ss;
    }
    
    @POST
    @Path("scheduler")
    @Produces(MediaType.APPLICATION_JSON)
    public SchedulerStatus setSchedulerStatus(boolean running) {
        try {
            if (running) {
                pk.unpauseScheduler();
            } else
                pk.pauseScheduler();             
        } catch (PinsetterException pe) {
            throw new IseException("Error setting scheduler status");
        }
        return getSchedulerStatus();
    }

    @GET
    @Path("/{job_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public JobStatus getStatus(@PathParam("job_id") String jobId) {
        return curator.find(jobId);
    }
    
    @DELETE
    @Path("/{job_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public JobStatus cancel(@PathParam("job_id") String jobId) {
        JobStatus j = curator.find(jobId);
        if (j.getState().equals(JobState.CANCELLED)) {
            throw new BadRequestException("job already cancelled");
        }
        if (j.getState() != JobState.CREATED) {
            throw new BadRequestException("cannot cancel a job that is not in" +
                   "CREATED state");
        }
        return curator.cancel(jobId);
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
