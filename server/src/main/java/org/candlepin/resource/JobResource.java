/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.resource;

import org.candlepin.auth.Verify;
import org.candlepin.async.JobManager;
import org.candlepin.async.StateManagementException;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.SchedulerStatusDTO;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatusCurator;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

/**
 * JobResource
 */
@Path("/jobs")
@Api(value = "jobs", authorizations = { @Authorization("basic") })
public class JobResource {
    private static Logger log = LoggerFactory.getLogger(JobResource.class);

    private I18n i18n;
    private ModelTranslator translator;
    private JobManager jobManager;
    private AsyncJobStatusCurator curator;


    @Inject
    public JobResource(I18n i18n, ModelTranslator translator, JobManager jobManager) {
        this.i18n = Objects.requireNonNull(i18n);
        this.translator = Objects.requireNonNull(translator);
        this.jobManager = Objects.requireNonNull(jobManager);
    }



    // Scheduler status
    @ApiOperation(
        value = "fetches the status of the job scheduler for this Candlepin node",
        response = AsyncJobStatusDTO.class, responseContainer = "set")
    @ApiResponses({
        @ApiResponse(code = 400, message = ""),
        @ApiResponse(code = 404, message = "")
    })
    @GET
    @Path("/scheduler")
    @Produces(MediaType.APPLICATION_JSON)
    public SchedulerStatusDTO getSchedulerStatus() {
        SchedulerStatusDTO output = new SchedulerStatusDTO();

        JobManager.ManagerState state = this.jobManager.getManagerState();
        output.setRunning(state == JobManager.ManagerState.RUNNING);

        // TODO: Add other stuff here as necessary (jobs stats like running, queued, etc.)

        return output;
    }

    @ApiOperation(
        value = "enables or disables the job scheduler for this Candlepin node",
        response = AsyncJobStatusDTO.class, responseContainer = "set")
    @ApiResponses({
        @ApiResponse(code = 400, message = ""),
        @ApiResponse(code = 404, message = "")
    })
    @POST
    @Path("/scheduler")
    @Produces(MediaType.APPLICATION_JSON)
    public SchedulerStatusDTO setSchedulerStatus(boolean running) {

        try {
            // Impl note: This is kind of lazy and may run into problems in obscure circumstances where
            // the state has been paused for a specific reason (suspend mode, for instance) or is otherwise
            // in a state where we can't start/pause. In such cases, we'll trigger a StateManagementException
            // or an IllegalStateException

            if (running) {
                this.jobManager.start();
            }
            else {
                this.jobManager.pause();
            }
        }
        catch (IllegalStateException | StateManagementException e) {
            String errmsg = i18n.tr("Error setting scheduler status");
            throw new IseException(errmsg, e);
        }

        return this.getSchedulerStatus();
    }


    // Job status
    @ApiOperation(
        value = "fetches a set of job statuses matching the given filter options",
        response = AsyncJobStatusDTO.class, responseContainer = "set")
    @ApiResponses({
        @ApiResponse(code = 400, message = ""),
        @ApiResponse(code = 404, message = "")
    })
    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public CandlepinQuery<AsyncJobStatusDTO> listJobStatuses(
        @QueryParam("key") List<String> keys,
        @QueryParam("state") List<String> states,
        @QueryParam("owner") List<String> ownerKeys
        ) {

        throw new UnsupportedOperationException("Not yet implemented");
    }

    @ApiOperation(
        value = "fetches the job status associated with the specified job ID",
        response = AsyncJobStatusDTO.class)
    @ApiResponses({
        @ApiResponse(code = 400, message = ""),
        @ApiResponse(code = 404, message = "")
    })
    @GET
    @Path("/{job_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public AsyncJobStatusDTO getJobStatus(
        @PathParam("job_id") @Verify(AsyncJobStatus.class) String jobId) {

        throw new UnsupportedOperationException("Not yet implemented");
    }

    @ApiOperation(
        value = "cancels the job associated with the specified job ID",
        response = AsyncJobStatusDTO.class)
    @ApiResponses({
        @ApiResponse(code = 400, message = ""),
        @ApiResponse(code = 404, message = "")
    })
    @DELETE
    @Path("/{job_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public AsyncJobStatusDTO cancelJob(
        @PathParam("job_id") @Verify(AsyncJobStatus.class) String jobId) {

        throw new UnsupportedOperationException("Not yet implemented");
    }

    @ApiOperation(
        value = "cancels the job associated with the specified job ID",
        response = AsyncJobStatusDTO.class)
    @ApiResponses({
        @ApiResponse(code = 400, message = ""),
        @ApiResponse(code = 404, message = "")
    })
    @DELETE
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public AsyncJobStatusDTO cleanupTerminalJobs(
        @QueryParam("cutoff") Date cutoff,
        @QueryParam("state") List<String> states) {

        throw new UnsupportedOperationException("Not yet implemented");
    }


    @ApiOperation(
        value = "schedules a job using the specified key and job properties",
        response = AsyncJobStatusDTO.class)
    @ApiResponses({
        @ApiResponse(code = 400, message = ""),
        @ApiResponse(code = 404, message = "")
    })
    @POST
    @Path("schedule/{job_key}")
    @Produces(MediaType.APPLICATION_JSON)
    public AsyncJobStatusDTO scheduleJob(
        @PathParam("job_key") String jobKey) {

        throw new UnsupportedOperationException("Not yet implemented");
    }

}
