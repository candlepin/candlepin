/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Injector;
import com.google.inject.persist.UnitOfWork;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Principal;
import org.candlepin.auth.PrincipalData;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.guice.CandlepinRequestScope;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.model.AsyncJobStatusCurator;
import org.candlepin.util.Util;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


/**
 * The JobManager manages the queueing, execution and general bookkeeping on jobs
 */
@Singleton
public class JobManager {

    private static Logger log = LoggerFactory.getLogger(JobManager.class);

    /** Stores our mapping of job keys to job classes */
    private static Map<String, Class<? extends AsyncJob>> jobs = new HashMap<>();


    /**
     * Registers the given class for the specified key. If the key was already registered to
     * another class, the previously registered class will be returned.
     *
     * @param jobKey
     *  The key under which to register the job class
     *
     * @param jobClass
     *  The job class to register
     *
     * @throws IllegalArgumentException
     *  if jobKey is null or empty, or jobClass is null
     *
     * @return
     *  the job class previously registered to the given key, or null if the key was not already
     *  registered
     */
    public static Class<? extends AsyncJob> registerJob(String jobKey, Class<? extends AsyncJob> jobClass) {
        if (jobKey == null || jobKey.isEmpty()) {
            throw new IllegalArgumentException("jobKey is null or empty");
        }

        if (jobClass == null) {
            throw new IllegalArgumentException("jobClass is null");
        }

        log.info("Registering job: {}: {}", jobKey, jobClass.getCanonicalName());
        return jobs.put(jobKey, jobClass);
    }

    /**
     * Removes the registration for the specified job key, if present. If the given key is not
     * registered to any job class, this function returns null.
     *
     * @param jobKey
     *  The job key to unregister
     *
     * @throws IllegalArgumentException
     *  if jobKey is null or empty
     *
     * @return
     *  the job class previously registered to the given key, or null if the key was not already
     *  registered
     */
    public static Class<? extends AsyncJob> unregisterJob(String jobKey) {
        if (jobKey == null || jobKey.isEmpty()) {
            throw new IllegalArgumentException("jobKey is null or empty");
        }

        return jobs.remove(jobKey);
    }

    /**
     * Fetches the job class registered to the specified key. If the key is not registered, this
     * function returns null.
     *
     * @param jobKey
     *  The key for which to fetch the job class
     *
     * @throws IllegalArgumentException
     *  if jobKey is null or empty
     *
     * @return
     *  the job class registered to the given key, or null if the key is not registered
     */
    public static Class<? extends AsyncJob> getJobClass(String jobKey) {
        if (jobKey == null || jobKey.isEmpty()) {
            throw new IllegalArgumentException("jobKey is null or empty");
        }

        return jobs.get(jobKey);
    }


    private final AsyncJobStatusCurator jobCurator;
    private final JobMessageDispatcher dispatcher;
    private final CandlepinRequestScope candlepinRequestScope;
    private final PrincipalProvider principalProvider;
    private final Injector injector;

    /**
     * Creates a new JobManager instance
     */
    @Inject
    public JobManager(
        final AsyncJobStatusCurator jobCurator,
        final JobMessageDispatcher dispatcher,
        final PrincipalProvider principalProvider,
        final CandlepinRequestScope scope,
        final Injector injector) {
        this.jobCurator = Objects.requireNonNull(jobCurator);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.candlepinRequestScope = Objects.requireNonNull(scope);
        this.principalProvider = Objects.requireNonNull(principalProvider);
        this.injector = Objects.requireNonNull(injector);
    }

    /**
     * Builds an AsyncJobStatus instance from the given job details. The job status will not be
     * a managed entity and will need to be manually persisted by the caller.
     *
     * @param builder
     *  The JobBuilder to use to build the job status
     *
     * @throws IllegalArgumentException
     *  if detail is null
     *
     * @return
     *  the newly constructed, unmanaged AsyncJobStatus instance
     */
    private AsyncJobStatus buildJobStatus(JobBuilder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("job builder is null");
        }

        AsyncJobStatus job = new AsyncJobStatus();

        job.setJobKey(builder.getJobKey());
        job.setName(builder.getJobName() != null ? builder.getJobName() : builder.getJobKey());
        job.setGroup(builder.getJobGroup());

        // Add environment-specific metadata...
        job.setOrigin(Util.getHostname());
        Principal principal = this.principalProvider.get();
        job.setPrincipal(principal != null ? principal.getName() : null);

        // Metadata and Logging configuration...
        job.setMetadata(builder.getJobMetadata());

        String csid = MDC.get(LoggingFilter.CSID_KEY);
        if (csid != null && !csid.isEmpty()) {
            job.addMetadata(LoggingFilter.CSID_KEY, csid);
        }

        job.setLogLevel(builder.getLogLevel());
        job.logExecutionDetails(builder.logExecutionDetails());

        // Retry and runtime configuration...
        job.setMaxAttempts(builder.getRetryCount() + 1);
        Map<String, Object> jobArguments = new HashMap<>(builder.getJobArguments());

        // TODO: Find better way to pass principal to the execution thread.
        if (principal != null) {
            try {
                String toJson = Util.toJson(new PrincipalData(principal.getType(), principal.getName()));
                jobArguments.put(AsyncJobStatus.PRINCIPAL_KEY, toJson);
            }
            catch (JsonProcessingException e) {
                log.error("Could not parse the principal data");
            }
        }

        job.setJobData(Collections.unmodifiableMap(jobArguments));

        return job;
    }

    /**
     * Queues a job to be run on any Candlepin node backed by the same database as this node, and
     * is configured to process jobs matching the type of the specified job. If multiple nodes are
     * able to process a given job, there is no mechanism to guarantee consistently repeatable
     * behavior as to which node will actually execute the job.
     * <p></p>
     * If the specified job is one which is unique by some criteria, and a matching job is already
     * in the queue or currently executing, a new job will not be queued and the existing job's
     * job status will be returned instead.
     *
     * @param builder
     *  A JobBuilder instance representing the configuration of the job to queue
     *
     * @return
     *  an AsyncJobStatus instance representing the queued job's status, or the status of the
     *  existing job if it already exists
     */
    public AsyncJobStatus queueJob(JobBuilder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("job builder is null");
        }

        // TODO:
        // Add job filtering/deduplication by criteria

        AsyncJobStatus status = this.buildJobStatus(builder);
        status.setState(JobState.CREATED);

        try {
            // Persist the job status so that the ID will be generated.
            status = this.jobCurator.create(status);

            // Build and send the job message and update the job state accordingly
            status = this.postJobStatusMessage(status);
        }
        catch (JobStateManagementException e) {
            if (log.isDebugEnabled()) {
                log.error("Unable to update state for job: {}; leaving job in its previous state for " +
                    "state resync upon execution", status.getName(), e);
            }
            else {
                log.error("Unable to update state for job: {}; leaving job in its previous state for " +
                    "state resync upon execution", status.getName());
            }

            // We were unable to update the state from CREATED->QUEUED, but we were able to send
            // the message to Artemis. We *should* be fine once the job executes and corrects
            // itself. The job will skip the QUEUED state and transition right into RUNNING, but
            // that's fine... I guess.

            // Manually update the state just to be certain. This won't persist, but at least our
            // output will be consistent.
            status.setState(JobState.QUEUED);
        }
        catch (JobMessageDispatchException e) { // Temporary exception branch
            log.error("Unable to dispatch job message for new job: {}; deleting job and returning",
                status.getName(), e);

            // We created the job, but were unable to send the job to Artemis. The job is dead
            // at this point. We *could* retry, but at the time of writing, we have no mechanism
            // for enabling retry or async messaging (comes with scheduling). As such, we'll
            // kill the job

            this.jobCurator.delete(status);

            status.setState(JobState.FAILED);
            status.setJobResult(e);
        }
        catch (Exception e) {
            log.error("Unexpected exception occurred while queueing job: {}", status.getName(), e);

            // Uh oh. If we're here, something very very bad has happened. This probably indicates
            // the database is unavailable or we, otherwise, cannot persist the AsyncJobStatus
            // instance. We'd delete it, but that'll likely fail, too. Best thing to do here is
            // just return the job status with the FAILED info.

            // If this occurs do to some other unexpected failure, we'll have some state cleanup
            // to deal with, probably.

            status.setState(JobState.FAILED);
            status.setJobResult(e);
        }

        // Done!
        return status;
    }

    /**
     * Creates and dispatches a job message for the given job status, then updates the state of
     * the job to QUEUED.
     *
     * @param status
     *  The job for which to dispatch a job message
     *
     * @return
     *  the updated job status
     */
    private AsyncJobStatus postJobStatusMessage(AsyncJobStatus status)
        throws JobStateManagementException, JobMessageDispatchException {

        try {
            // Build and send the job message
            JobMessage message = new JobMessage(status.getId(), status.getJobKey());
            this.dispatcher.sendJobMessage(message);

            // Update the job's status
            status = this.updateJobStatus(status, JobState.QUEUED, null);

            return status;
        }
        catch (JobMessageDispatchException e) {
            log.error("Job \"{}\" could not be queued; failed to dispatch job message", status.getName(), e);

            this.updateJobStatus(status, JobState.FAILED, e);

            throw e;
        }
    }

    /**
     * Executes the specified job immediately on this Candlepin node, skipping any filtering or
     * deduplication mechanisms.
     * <p></p>
     * <strong>Note</strong>: Generally, this method should not be called directly, and jobs should
     * be queued using the <tt>queueJob</tt> method instead.
     *
     * @param message the JobMessage containing the information about the job that should
     *                be executed.
     *
     * @return
     *  a JobStatus instance representing the job's status
     */
    public AsyncJobStatus executeJob(final JobMessage message) throws JobException {
        AsyncJobStatus status = this.fetchJobStatus(message);

        setupPrincipal(status.getJobData());

        final Class<? extends AsyncJob> jobClass = getJobClass(message.getJobKey());
        candlepinRequestScope.enter();
        final EventSink eventSink = injector.getInstance(EventSink.class);
        final UnitOfWork uow = injector.getInstance(UnitOfWork.class);

        try {
            uow.begin();
            setupLogging(status);

            final AsyncJob job = injector.getInstance(jobClass);

            if (job == null) {
                log.error("Unable to instantiate job class \"{}\" for job: {}",
                    jobClass.getName(), message.getJobKey());

                throw new JobInitializationException("Unable to instantiate job class: " +
                    jobClass.getName());
            }

            status.setExecutor(Util.getHostname());
            status.incrementAttempts();
            status.setStartTime(new Date());
            status.setEndTime(null);
            this.updateJobStatus(status, JobState.RUNNING, null);

            if (status.logExecutionDetails()) {
                log.info("Starting job \"{}\" using class: {}", status.getName(), jobClass.getName());
            }

            Object result = null;

            try {
                result = job.execute(status);
            }
            catch (JobExecutionException e) {
                boolean retry = !e.isTerminal() && status.getAttempts() < status.getMaxAttempts();
                status = this.processJobFailure(status, eventSink, e, retry);

                throw e;
            }
            catch (Exception e) {
                boolean retry = status.getAttempts() < status.getMaxAttempts();
                status = this.processJobFailure(status, eventSink, e, retry);

                throw new JobExecutionException(e);
            }

            eventSink.sendEvents();
            status.setEndTime(new Date());
            status = this.updateJobStatus(status, JobState.COMPLETED, result);

            if (status.logExecutionDetails()) {
                log.info("Job \"{}\" completed in {}ms", status.getName(), this.getJobRuntime(status));
            }

            return status;
        }
        finally {
            uow.end();
            candlepinRequestScope.exit();
        }
    }

    /**
     * Configures the logging environment and injects metadata for the specified job
     *
     * @param status
     *  the job status to use to configure the logging environment
     */
    private void setupLogging(final AsyncJobStatus status) {
        MDC.put("requestType", "job");
        MDC.put("requestUuid", status.getId());

        // Inject all of our metadata...
        for (Map.Entry<String, String> entry : status.getMetadata().entrySet()) {
            MDC.put(entry.getKey(), entry.getValue());
        }

        // Update the logging level if we've one set...
        String logLevel = status.getLogLevel();
        if (logLevel != null && !logLevel.isEmpty()) {
            MDC.put("logLevel", logLevel);
        }
    }

    /**
     * Fetches and validates the job status associated with the given message.
     *
     * @param message
     *  The message for which to fetch the job status
     *
     * @throws JobInitializationException
     *  if the job status cannot be queried, found or it is not in a valid state
     *
     * @return
     *  the AsyncJobStatus instance associated with the given message
     */
    private AsyncJobStatus fetchJobStatus(JobMessage message) throws JobInitializationException {
        AsyncJobStatus status;

        try {
            status = this.jobCurator.get(message.getJobId());
        }
        catch (Exception e) {
            // This should only happen if the DB is down when we attempt to fetch a job from the
            // curator. This is a non-terminal error as we must assume the job is still there
            // waiting to be run
            String errmsg = String.format("Unable to query job status for message: %s", message);

            log.debug(errmsg, e);
            throw new JobInitializationException(errmsg, e, false);
        }

        if (status == null) {
            String errmsg = String.format("Unable to find job status for message: %s", message);

            log.error(errmsg);
            throw new JobInitializationException(errmsg, true);
        }

        if (status.getState() == null || status.getState().isTerminal()) {
            String errmsg = String.format("Job \"%s\" is in an unknown or terminal state: %s",
                status.getName(), status.getState());

            log.error(errmsg);
            throw new JobInitializationException(errmsg, true);
        }

        return status;

    }

    /**
     * Updates the state of the provided job status
     *
     * @param status
     *  The AsyncJobStatus to update
     *
     * @param state
     *  The state to set
     *
     * @param result
     *  The result to assign to the job
     *
     * @throws JobStateManagementException
     *  if the job state is unable to be updated due to a database failure
     *
     * @return
     *  the updated AsyncJobStatus entity
     */
    private AsyncJobStatus updateJobStatus(AsyncJobStatus status, JobState state, Object result)
        throws JobStateManagementException {

        JobState initState = status.getState();

        try {
            status.setState(state);
            status.setJobResult(result);

            return this.jobCurator.merge(status);
        }
        catch (Exception e) {
            String errmsg = String.format("Unable to update job state for job \"%s\": %s -> %s",
                status.getName(), initState, state);

            log.error(errmsg, e);
            throw new JobStateManagementException(status, initState, state, errmsg, e, state.isTerminal());
        }
    }

    /**
     * Calculates the runtime of the given job. If the job has not completed its execution attempt,
     * this method returns -1;
     *
     * @param status
     *  The AsyncJobStatus for which to calculate the job runtime
     *
     * @return
     *  the runtime of the job in milliseconds, or -1 if the job has not yet completed its most
     *  recent execution attempt
     */
    private long getJobRuntime(AsyncJobStatus status) {
        Date start = status.getStartTime();
        Date end = status.getEndTime();

        return (start != null && end != null) ? end.getTime() - start.getTime() : -1;
    }

    /**
     * Processes the failure of a job by rolling back pending events and updating the job state. If
     * the job is to be retried, a new job message will be dispatched accordingly.
     *
     * @param status
     *  an AsyncJobStatus instance representing the failed job
     *
     * @param exception
     *  the exception representing the failure that occurred
     *
     * @param retry
     *  whether or not the job should be retried
     *
     * @return
     *  the updated AsyncJobStatus entity
     */
    private AsyncJobStatus processJobFailure(AsyncJobStatus status, EventSink eventSink, Exception exception,
        boolean retry) throws JobStateManagementException, JobMessageDispatchException {

        // Set the end of the execution attempt
        status.setEndTime(new Date());

        // Rollback any unsent events
        eventSink.rollback();

        if (retry) {
            status = this.updateJobStatus(status, JobState.FAILED_WITH_RETRY, exception);

            log.warn("Job \"{}\" failed in {}ms; retrying...",
                status.getName(), this.getJobRuntime(status), exception);

            this.postJobStatusMessage(status);
        }
        else {
            status = this.updateJobStatus(status, JobState.FAILED, exception);

            log.error("Job \"{}\" failed in {}ms",
                status.getName(), this.getJobRuntime(status), exception);
        }

        return status;
    }


    private void setupPrincipal(final JobDataMap jobData) {
        // TODO remove this guard check once we have better way to share the principal
        if (!jobData.containsKey("principal")) {
            log.warn("Principal data are missing from the job data!");
            return;
        }
        final String principalJson = jobData.getAsString(AsyncJobStatus.PRINCIPAL_KEY);
        final PrincipalData principal = (PrincipalData) Util.fromJson(principalJson, PrincipalData.class);
        ResteasyProviderFactory.pushContext(
            Principal.class,
            new UserPrincipal(principal.getName(), Collections.emptyList(), false));
    }
}
