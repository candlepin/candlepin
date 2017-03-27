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
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.JobCurator;
import org.candlepin.model.SchedulerStatus;
import org.candlepin.pinsetter.core.PinsetterException;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.pinsetter.core.model.JobStatus.JobState;
import org.candlepin.pinsetter.tasks.KingpinJob;
import org.candlepin.util.ElementTransformer;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.List;

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

    private JobCurator curator;
    private PinsetterKernel pk;
    private I18n i18n;

    private static Logger log = LoggerFactory.getLogger(JobResource.class);

    @Inject
    public JobResource(JobCurator curator, PinsetterKernel pk, I18n i18n) {
        this.curator = curator;
        this.pk = pk;
        this.i18n = i18n;
    }


    /**
     * Returns false if only one of the strings is not empty, otherwise
     * returns true.
     * @param owner param1
     * @param uuid param2
     * @param pname param3
     * @return a boolean
     */
    private boolean ensureOnlyOne(String owner, String uuid, String pname) {
        String[] params = new String[3];
        params[0] = owner;
        params[1] = uuid;
        params[2] = pname;

        boolean found = false;

        for (String s : params) {
            if (found && !StringUtils.isEmpty(s)) {
                return false;
            }
            else if (!StringUtils.isEmpty(s)) {
                found = true;
            }
        }

        return true;
    }

    @ApiOperation(notes = "Retrieves a list of Job Status", value = "getStatuses",
        response = JobStatus.class, responseContainer = "list")
    @ApiResponses({ @ApiResponse(code = 400, message = ""), @ApiResponse(code = 404, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public CandlepinQuery<JobStatus> getStatuses(
        @QueryParam("owner") String ownerKey,
        @QueryParam("consumer") String uuid,
        @QueryParam("principal") String principalName) {

        boolean allParamsEmpty = StringUtils.isEmpty(ownerKey) &&
            StringUtils.isEmpty(uuid) &&
            StringUtils.isEmpty(principalName);

        // make sure we only specified one
        if (allParamsEmpty || !ensureOnlyOne(ownerKey, uuid, principalName)) {
            throw new BadRequestException(i18n.tr("You must specify exactly " +
                "one of owner key, unit UUID, or principal name."));
        }

        CandlepinQuery<JobStatus> statuses = null;
        if (!StringUtils.isEmpty(ownerKey)) {
            statuses = curator.findByOwnerKey(ownerKey);
        }
        else if (!StringUtils.isEmpty(uuid)) {
            statuses = curator.findByConsumerUuid(uuid);
        }
        else if (!StringUtils.isEmpty(principalName)) {
            statuses = curator.findByPrincipalName(principalName);
        }

        if (statuses == null) {
            throw new NotFoundException("");
        }

        return statuses.transform(new ElementTransformer<JobStatus, JobStatus>() {
            @Override
            public JobStatus transform(JobStatus jobStatus) {
                return jobStatus.cloakResultData(true);
            }
        });
    }

    @ApiOperation(notes = "Retrieves the Scheduler Status", value = "getSchedulerStatus")
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

    @ApiOperation(notes = "Updates the Scheduler Status", value = "setSchedulerStatus")
    @ApiResponses({ @ApiResponse(code = 500, message = "") })
    @POST
    @Path("scheduler")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public SchedulerStatus setSchedulerStatus(boolean running) {
        try {
            if (running) {
                pk.unpauseScheduler();
            }
            else {
                pk.pauseScheduler();
            }
        }
        catch (PinsetterException pe) {
            throw new IseException(i18n.tr("Error setting scheduler status"));
        }
        return getSchedulerStatus();
    }

    @ApiOperation(notes = "Triggers select asynchronous jobs", value = "schedule")
    @ApiResponses({ @ApiResponse(code = 400, message = ""), @ApiResponse(code = 500, message = "") })
    @POST
    @Path("schedule/{task}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @SuppressWarnings("unchecked")
    public JobStatus schedule(@PathParam("task") String task) {

        /*
         * at the time of implementing this API, the only jobs that
         * are permissible are cron jobs.
         */
        String className = "org.candlepin.pinsetter.tasks." + task;
        try {
            boolean allowed = false;
            for (String permissibleJob : ConfigProperties.DEFAULT_TASK_LIST) {
                if (className.equalsIgnoreCase(permissibleJob)) {
                    allowed = true;
                    // helps ignore case
                    className = permissibleJob;
                }
            }
            if (allowed) {
                Class taskClass = Class.forName(className);
                return pk.scheduleSingleJob((Class<? extends KingpinJob>) taskClass, Util.generateUUID());
            }
            else {
                throw new BadRequestException(i18n.tr("Not a permissible job: {0}. Only {1} are permissible",
                    task, prettyPrintJobs(ConfigProperties.DEFAULT_TASK_LIST)));
            }
        }
        catch (ClassNotFoundException e) {
            throw new IseException(i18n.tr("Error trying to schedule {0}: {1}", className,
                e.getMessage()));
        }
        catch (PinsetterException e) {
            throw new IseException(i18n.tr("Error trying to schedule {0}: {1}", className,
                e.getMessage()));
        }
    }

    private String prettyPrintJobs(String... jobs) {
        List<String> jobNames = new ArrayList<String>();
        for (String job : jobs) {
            jobNames.add(StringUtils.substringAfterLast(job, "."));
        }
        return StringUtils.join(jobNames, ", ");
    }

    @ApiOperation(notes = "Retrieves a single Job Status", value = "getStatus")
    @GET
    @Path("/{job_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public JobStatus getStatus(@PathParam("job_id") @Verify(JobStatus.class) String jobId,
        @QueryParam("result_data") @DefaultValue("false") boolean resultData) {
        JobStatus js = curator.find(jobId);
        js.cloakResultData(!resultData);
        return js;
    }

    @ApiOperation(notes = "Cancels a Job Status", value = "cancel")
    @ApiResponses({ @ApiResponse(code = 400, message = ""), @ApiResponse(code = 404, message = "") })
    @DELETE
    @Path("/{job_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public JobStatus cancel(@PathParam("job_id") @Verify(JobStatus.class) String jobId) {
        JobStatus j = curator.find(jobId);
        if (j.getState().equals(JobState.CANCELED)) {
            throw new BadRequestException(i18n.tr("job already canceled"));
        }
        if (j.isDone()) {
            throw new BadRequestException(i18n.tr("cannot cancel a job that is in a finished state"));
        }
        return curator.cancel(jobId);
    }

    @ApiOperation(notes = "Retrieves a Job Status and Removes if finished",
        value = "getStatusAndDeleteIfFinished")
    @POST
    @Path("/{job_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    public JobStatus getStatusAndDeleteIfFinished(
        @PathParam("job_id") @Verify(JobStatus.class) String jobId) {
        JobStatus status = curator.find(jobId);

        if (status != null && status.getState() == JobState.FINISHED) {
            curator.delete(status);
        }

        return status;
    }
}
