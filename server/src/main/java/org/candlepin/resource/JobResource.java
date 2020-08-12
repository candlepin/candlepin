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

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobException;
import org.candlepin.async.JobManager;
import org.candlepin.async.StateManagementException;
import org.candlepin.auth.Verify;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.SchedulerStatusDTO;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.model.AsyncJobStatusCurator;
import org.candlepin.model.AsyncJobStatusCurator.AsyncJobStatusQueryBuilder;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.resteasy.DateFormat;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * JobResource
 */
@Component
@Transactional
@Path("/jobs")
@Api(value = "jobs", authorizations = { @Authorization("basic") })
public class JobResource {
    private static Logger log = LoggerFactory.getLogger(JobResource.class);

    private static final String NULL_OWNER_KEY = "null";

    private Configuration config;
    private I18n i18n;
    private ModelTranslator translator;
    private JobManager jobManager;
    private AsyncJobStatusCurator jobCurator;
    private OwnerCurator ownerCurator;

    private Set<String> triggerableJobKeys;

    @Autowired
    public JobResource(Configuration config, I18n i18n, ModelTranslator translator, JobManager jobManager,
        AsyncJobStatusCurator jobCurator, OwnerCurator ownerCurator) {

        this.config = Objects.requireNonNull(config);
        this.i18n = Objects.requireNonNull(i18n);
        this.translator = Objects.requireNonNull(translator);
        this.jobManager = Objects.requireNonNull(jobManager);
        this.jobCurator = Objects.requireNonNull(jobCurator);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);

        // This will be instantiated on demand
        this.triggerableJobKeys = null;
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
    @Transactional
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
    @Transactional
    public SchedulerStatusDTO setSchedulerStatus(
        @QueryParam("running") @DefaultValue("true") boolean running) {

        try {
            // Impl note: This is kind of lazy and may run into problems in obscure circumstances where
            // the state has been paused for a specific reason (suspend mode, for instance) or is otherwise
            // in a state where we can't start/pause. In such cases, we'll trigger a StateManagementException
            // or an IllegalStateException

            if (running) {
                this.jobManager.resume();
            }
            else {
                this.jobManager.suspend();
            }
        }
        catch (IllegalStateException | StateManagementException e) {
            String errmsg = this.i18n.tr("Error setting scheduler status");
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
    @Transactional
    public Stream<AsyncJobStatusDTO> listJobStatuses(
        @QueryParam("id") Set<String> ids,
        @QueryParam("key") Set<String> keys,
        @QueryParam("state") Set<String> states,
        @QueryParam("owner") Set<String> ownerKeys,
        @QueryParam("principal") Set<String> principals,
        @QueryParam("origin") Set<String> origins,
        @QueryParam("executor") Set<String> executors,
        @QueryParam("after") @DateFormat Date after,
        @QueryParam("before") @DateFormat Date before) {

        // Convert and validate state names to actual states
        Set<JobState> jobStates = this.translateJobStateNames(states);
        Set<String> ownerIds = this.translateOwnerKeys(ownerKeys);

        this.validateDateRange(after, before);

        // TODO: Should we bother checking the other params as well? Seems like they're
        // self-validating

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setJobIds(ids)
            .setJobKeys(keys)
            .setJobStates(jobStates)
            .setOwnerIds(ownerIds)
            .setPrincipalNames(principals)
            .setOrigins(origins)
            .setExecutors(executors)
            .setStartDate(after)
            .setEndDate(before);

        return this.jobManager.findJobs(queryBuilder).stream()
            .map(this.translator.getStreamMapper(AsyncJobStatus.class, AsyncJobStatusDTO.class));
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
    @Transactional
    public AsyncJobStatusDTO getJobStatus(
        @PathParam("job_id") @Verify(AsyncJobStatus.class) String jobId) {

        if (jobId == null || jobId.isEmpty()) {
            String errmsg = this.i18n.tr("Job ID is null or empty");
            throw new BadRequestException(errmsg);
        }

        AsyncJobStatus status = this.jobManager.findJob(jobId);
        if (status == null) {
            String errmsg = this.i18n.tr("No job status found associated with the given job ID: {0}", jobId);
            throw new NotFoundException(errmsg);
        }

        return this.translator.translate(status, AsyncJobStatusDTO.class);
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
    @Transactional
    public AsyncJobStatusDTO cancelJob(
        @PathParam("job_id") @Verify(AsyncJobStatus.class) String jobId) {

        if (jobId == null || jobId.isEmpty()) {
            String errmsg = this.i18n.tr("Job ID is null or empty");
            throw new BadRequestException(errmsg);
        }

        try {
            // Due to the race conditions that could occur here, we'll just try and recover on
            // state exception.
            AsyncJobStatus status = this.jobManager.cancelJob(jobId);
            if (status == null) {
                String errmsg = this.i18n.tr("No job status found associated with the given job ID: {0}",
                    jobId);

                throw new NotFoundException(errmsg);
            }

            return this.translator.translate(status, AsyncJobStatusDTO.class);
        }
        catch (IllegalStateException e) {
            // Job is already in a terminal state.
            String errmsg = this.i18n.tr("Job {0} is already in a terminal state or otherwise cannot be " +
                "canceled at this time", jobId);
            throw new BadRequestException(errmsg, e);
        }
    }

    @ApiOperation(
        value = "cleans up terminal jobs matching the provided criteria",
        response = AsyncJobStatusDTO.class)
    @ApiResponses({
        @ApiResponse(code = 400, message = ""),
        @ApiResponse(code = 404, message = "")
    })
    @DELETE
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public int cleanupTerminalJobs(
        @QueryParam("id") Set<String> ids,
        @QueryParam("key") Set<String> keys,
        @QueryParam("state") Set<String> states,
        @QueryParam("owner") Set<String> ownerKeys,
        @QueryParam("principal") Set<String> principals,
        @QueryParam("origin") Set<String> origins,
        @QueryParam("executor") Set<String> executors,
        @QueryParam("after") @DateFormat Date after,
        @QueryParam("before") @DateFormat Date before,
        @QueryParam("force") @DefaultValue("false") boolean force) {

        // Convert state names
        Set<JobState> jobStates = this.translateJobStateNames(states);
        Set<String> ownerIds = this.translateOwnerKeys(ownerKeys);

        this.validateDateRange(after, before);

        // TODO: Should we bother checking the other params as well? Seems like they're
        // self-validating

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setJobIds(ids)
            .setJobKeys(keys)
            .setJobStates(jobStates)
            .setOwnerIds(ownerIds)
            .setPrincipalNames(principals)
            .setOrigins(origins)
            .setExecutors(executors)
            .setStartDate(after)
            .setEndDate(before);

        int count;

        if (force) {
            // If we're forcing the cleanup (see: deletion), skip the job manager and go right to
            // the DB, doing whatever unchecked badness the user has decided to do with this.
            count = this.jobCurator.deleteJobs(queryBuilder);
        }
        else {
            count = this.jobManager.cleanupJobs(queryBuilder);
        }

        return count;
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
    @Transactional
    public AsyncJobStatusDTO scheduleJob(
        @PathParam("job_key") String jobKey) {

        if (jobKey == null || jobKey.isEmpty()) {
            String errmsg = this.i18n.tr("Job key is null or empty");
            throw new BadRequestException(errmsg);
        }

        // Until we come up with a secure and consistent way to provide type-safe parameters to
        // the jobs, we'll follow the triggerJob method from the old resource and limit this to
        // explicitly allowed jobs.
        if (!this.jobIsSchedulable(jobKey)) {
            String errmsg = this.i18n.tr("Job \"{0}\" is not configured for manual scheduling", jobKey);
            throw new ForbiddenException(errmsg);
        }

        // Impl note: At the time of writing, we don't have any other details to add here.
        JobConfig config = JobConfig.forJob(jobKey);

        // TODO: figure out a relatively safe way of adding arbitrary parameters to the job
        // for (Object entry : request.getParameterMap().entrySet()) {
        //     Map.Entry<String, String[]> queryParam = (Map.Entry<String, String[]>) entry;

        //     String param = queryParam.getKey();
        //     String[] vals = queryParam.getValue();

        //     config.setJobArgument(param, vals.length > 1 ? vals : vals[0]);
        // }

        try {
            AsyncJobStatus status = this.jobManager.queueJob(config);
            return this.translator.translate(status, AsyncJobStatusDTO.class);
        }
        catch (JobException e) {
            String errmsg = this.i18n.tr("An unexpected exception occurred while scheduling job \"{0}\"",
                jobKey);

            throw new IseException(errmsg, e);
        }
    }

    /**
     * Checks if the provided job key is schedulable according to Candlepin's current configuration.
     *
     * @param jobKey
     *  the job key to check
     *
     * @return
     *  true if the job can be scheduled; false otherwise
     */
    private boolean jobIsSchedulable(String jobKey) {
        if (this.triggerableJobKeys == null) {
            this.triggerableJobKeys = new HashSet<>();

            String jobList = this.config.getString(ConfigProperties.ASYNC_JOBS_TRIGGERABLE_JOBS, null);
            if (jobList != null) {
                for (String job : jobList.split("\\s*,\\s*")) {
                    if (job.length() > 0) {
                        this.triggerableJobKeys.add(job.toLowerCase());
                    }
                }
            }
        }

        return jobKey != null && this.triggerableJobKeys.contains(jobKey.toLowerCase());
    }


    /**
     * Translates the given job state names into a set of job states. If any of the provided states
     * cannot be translated, this method throws a NotFoundException with an error message containing
     * the failed names. If the provided collection of state names is null, this method returns
     * null.
     *
     * @param stateNames
     *  a collection of job state names to translate
     *
     * @throws NotFoundException
     *  if the provided collection of state names contains one or more names that cannot be
     *  translated
     *
     * @return
     *  a set of JobStates representing the provided state names
     */
    private Set<JobState> translateJobStateNames(Collection<String> stateNames) {
        Set<JobState> jobStates = null;

        if (stateNames != null) {
            jobStates = new HashSet<>();
            Set<String> failedNames = new HashSet<>();

            for (String name : stateNames) {
                try {
                    jobStates.add(JobState.valueOf(name.toUpperCase()));
                }
                catch (IllegalArgumentException | NullPointerException e) {
                    failedNames.add(name);
                }
            }

            if (failedNames.size() > 0) {
                String errmsg = this.i18n.tr("Unknown job states: {0}", failedNames);
                throw new NotFoundException(errmsg);
            }
        }

        return jobStates;
    }

    /**
     * Translates the provided owner keys in a way that doesn't expose information about the
     * (non-)existence of owners by key, nor widens the queries when keys are provided that don't
     * represent existing owners.
     *
     * Keys provided to this method will be resolved using a (slow) lookup of owners. Keys
     * representing existing owners will be converted to the owner ID as appropriate. Invalid keys
     * will be left as-is to avoid reducing the lookup to nothing.
     *
     * The special case of the string "null" will be converted to a null value to allow looking up
     * jobs that aren't associated with an owner.
     *
     * @param ownerKeys
     *  a collection of owner keys to translate
     *
     * @return
     *  a set of owner IDs translated from the given keys
     */
    private Set<String> translateOwnerKeys(Collection<String> ownerKeys) {
        Set<String> ownerIds = null;

        if (ownerKeys != null && !ownerKeys.isEmpty()) {
            Map<String, Owner> ownerMap = new HashMap<>();
            ownerIds = new HashSet<>();

            for (Owner owner : this.ownerCurator.getByKeys(ownerKeys)) {
                ownerMap.put(owner.getKey(), owner);
            }

            for (String key : ownerKeys) {
                if (NULL_OWNER_KEY.equalsIgnoreCase(key)) {
                    ownerIds.add(null);
                }
                else {
                    Owner owner = ownerMap.get(key);
                    ownerIds.add(owner != null ? owner.getId() : key);
                }
            }
        }

        return ownerIds;
    }

    /**
     * Checks that the provided date range is a logical range, by ensuring it is null, open-ended,
     * or in chronological order.
     *
     * @param start
     *  the beginning of the date range
     *
     * @param end
     *  the end of the date range
     *
     * @throws BadRequestException
     *  if the start and end dates are not null and the start date is after the end date
     */
    private void validateDateRange(Date start, Date end) {
        if (start != null && end != null) {
            if (start.compareTo(end) > 0) {
                String errmsg = this.i18n.tr(
                    "Invalid date range; start date occurs after the end date: {0} > {1}", start, end);

                throw new BadRequestException(errmsg);
            }
        }
    }

}
