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
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.SchedulerStatusDTO;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.model.AsyncJobStatusCurator;
import org.candlepin.model.AsyncJobStatusCurator.AsyncJobStatusQueryBuilder;
import org.candlepin.model.InvalidOrderKeyException;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.resource.util.JobStateMapper;
import org.candlepin.resource.util.JobStateMapper.ExternalJobState;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import org.jboss.resteasy.core.ResteasyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;




/**
 * JobResource
 */
public class JobResource implements JobsApi {
    private static Logger log = LoggerFactory.getLogger(JobResource.class);

    private static final String NULL_OWNER_KEY = "null";
    private static final int MAX_JOB_RESULTS = 10000;

    private Configuration config;
    private I18n i18n;
    private ModelTranslator translator;
    private JobManager jobManager;
    private AsyncJobStatusCurator jobCurator;
    private OwnerCurator ownerCurator;

    private Set<String> triggerableJobKeys;


    @Inject
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

    @Override
    @Transactional
    public SchedulerStatusDTO getSchedulerStatus() {
        SchedulerStatusDTO output = new SchedulerStatusDTO();

        JobManager.ManagerState state = this.jobManager.getManagerState();
        output.isRunning(state == JobManager.ManagerState.RUNNING);

        // TODO: Add other stuff here as necessary (jobs stats like running, queued, etc.)

        return output;
    }

    @Override
    @Transactional
    public SchedulerStatusDTO setSchedulerStatus(Boolean running) {

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

    @Override
    @Transactional
    public Stream<AsyncJobStatusDTO> listJobStatuses(
        Set<String> ids,
        Set<String> keys,
        Set<String> states,
        Set<String> ownerKeys,
        Set<String> principals,
        Set<String> origins,
        Set<String> executors,
        OffsetDateTime after,
        OffsetDateTime before) {

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
            .setStartDate(after != null ? new Date(after.toInstant().toEpochMilli())  : null)
            .setEndDate(before != null ? new Date(before.toInstant().toEpochMilli())  : null);

        // Do paging bits, if necessary
        PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);
        if (pageRequest != null) {
            Page<Stream<AsyncJobStatusDTO>> page = new Page<>();
            page.setPageRequest(pageRequest);

            if (pageRequest.isPaging()) {
                queryBuilder.setOffset((pageRequest.getPage() - 1) * pageRequest.getPerPage())
                    .setLimit(pageRequest.getPerPage());
            }

            if (pageRequest.getSortBy() != null) {
                queryBuilder.setOrder(Collections.singleton(new AsyncJobStatusQueryBuilder.Order(
                    pageRequest.getSortBy(), pageRequest.getOrder() == PageRequest.Order.DESCENDING)));
            }

            page.setMaxRecords((int) this.jobCurator.getJobCount(queryBuilder));

            // Store the page for the LinkHeaderResponseFilter
            ResteasyContext.pushContext(Page.class, page);
        }
        // If no paging was specified, force a limit on amount of results
        else {
            if (this.jobCurator.getJobCount(queryBuilder) > MAX_JOB_RESULTS) {
                String errmsg = this.i18n.tr("This endpoint does not support returning more than {0} " +
                    "results at a time, please use paging.", MAX_JOB_RESULTS);
                throw new BadRequestException(errmsg);
            }
        }

        try {
            return this.jobManager.findJobs(queryBuilder).stream()
                .map(this.translator.getStreamMapper(AsyncJobStatus.class, AsyncJobStatusDTO.class));
        }
        catch (InvalidOrderKeyException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public AsyncJobStatusDTO getJobStatus(@Verify(AsyncJobStatus.class) String id) {

        if (id == null || id.isEmpty()) {
            String errmsg = this.i18n.tr("Job ID is null or empty");
            throw new BadRequestException(errmsg);
        }

        AsyncJobStatus status = this.jobManager.findJob(id);
        if (status == null) {
            String errmsg = this.i18n.tr("No job status found associated with the given job ID: {0}", id);
            throw new NotFoundException(errmsg);
        }

        return this.translator.translate(status, AsyncJobStatusDTO.class);
    }

    @Override
    @Transactional
    public AsyncJobStatusDTO cancelJob(@Verify(AsyncJobStatus.class) String id) {

        if (id == null || id.isEmpty()) {
            String errmsg = this.i18n.tr("Job ID is null or empty");
            throw new BadRequestException(errmsg);
        }

        try {
            // Due to the race conditions that could occur here, we'll just try and recover on
            // state exception.
            AsyncJobStatus status = this.jobManager.cancelJob(id);
            if (status == null) {
                String errmsg = this.i18n.tr("No job status found associated with the given job ID: {0}",
                    id);

                throw new NotFoundException(errmsg);
            }

            return this.translator.translate(status, AsyncJobStatusDTO.class);
        }
        catch (IllegalStateException e) {
            // Job is already in a terminal state.
            String errmsg = this.i18n.tr("Job {0} is already in a terminal state or otherwise cannot be " +
                "canceled at this time", id);
            throw new BadRequestException(errmsg, e);
        }
    }

    @Override
    @Transactional
    public Integer cleanupTerminalJobs(
        Set<String> ids,
        Set<String> keys,
        Set<String> states,
        Set<String> ownerKeys,
        Set<String> principals,
        Set<String> origins,
        Set<String> executors,
        OffsetDateTime after,
        OffsetDateTime before,
        Boolean force) {

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
            .setStartDate(after != null ? new Date(after.toInstant().toEpochMilli())  : null)
            .setEndDate(before != null ? new Date(before.toInstant().toEpochMilli())  : null);

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

    @Override
    @Transactional
    public AsyncJobStatusDTO scheduleJob(String jobKey) {

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
            Set<ExternalJobState> externalStates = new HashSet<>();
            Set<String> failedNames = new HashSet<>();

            for (String name : stateNames) {
                try {
                    externalStates.add(ExternalJobState.valueOf(name.toUpperCase()));
                }
                catch (IllegalArgumentException | NullPointerException e) {
                    failedNames.add(name);
                }
            }

            if (failedNames.size() > 0) {
                String errmsg = this.i18n.tr("Unknown job states: {0}", failedNames);
                throw new NotFoundException(errmsg);
            }

            jobStates = JobStateMapper.translateStates(externalStates);
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
    private void validateDateRange(OffsetDateTime start, OffsetDateTime end) {
        if (start != null && end != null) {
            if (start.compareTo(end) > 0) {
                String errmsg = this.i18n.tr(
                    "Invalid date range; start date occurs after the end date: {0} > {1}", start, end);

                throw new BadRequestException(errmsg);
            }
        }
    }

}
