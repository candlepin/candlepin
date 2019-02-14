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

import com.google.inject.Injector;
import com.google.inject.persist.UnitOfWork;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Principal;
import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.guice.CandlepinRequestScope;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.model.AsyncJobStatusCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.inject.Inject;
import javax.inject.Singleton;
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

    private final AsyncJobStatusCurator jobCurator;
    private final JobMessageDispatcher dispatcher;
    private final CandlepinRequestScope candlepinRequestScope;
    private final PrincipalProvider principalProvider;
    private final Injector injector;



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
        job.setName(builder.getJobName());
        job.setGroup(builder.getJobGroup());

        // Add environment-specific metadata
        job.setOrigin(Util.getHostname());
        job.setCorrelationId(MDC.get(LoggingFilter.CSID));

        Principal principal = this.principalProvider.get();
        job.setPrincipal(principal != null ? principal.getName() : null);

        // TODO: add manually added metadata

        job.setMaxAttempts(builder.getRetryCount() + 1);
        job.setJobData(builder.getJobArguments());

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

        AsyncJobStatus job = this.buildJobStatus(builder);
        job.setState(JobState.CREATED);

        try {
            // Persist the job status so that the ID will be generated.
            job = this.jobCurator.create(job);

            // Build and send the job message.
            JobMessage message = new JobMessage(job.getId(), job.getJobKey());
            this.dispatcher.sendJobMessage(message);

            // Update the job state to QUEUED
            job = job.setState(JobState.QUEUED);
            this.jobCurator.merge(job);
        }
        catch (Exception e) {
            log.warn("Error sending async job message.", e);

            // We couldn't send the message so discard the job status as it is no longer relevant.
            if (job != null && job.getId() != null) {
                this.jobCurator.delete(job);
            }

            throw new RuntimeException("Error sending async job message.", e);
        }

        // Done!
        return job;
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
    public AsyncJobStatus executeJob(final JobMessage message)
        throws PreJobExecutionException, JobExecutionException {
        final AsyncJobStatus status = this.jobCurator.get(message.getJobId());
        if (status == null) {
            throw new PreJobExecutionException("JobStatus(" + message.toString() + ") was not found.");
        }
        if (status.getState() == JobState.CANCELED) {
            throw new PreJobExecutionException("Job(" + message.toString() + ") was CANCELLED.");
        }
        final Class<? extends AsyncJob> jobClass = getJobClass(message.getJobKey());
        candlepinRequestScope.enter();
        final EventSink eventSink = injector.getInstance(EventSink.class);
        final UnitOfWork uow = injector.getInstance(UnitOfWork.class);
        uow.begin();
        try {
            setupLogging(status);
            final AsyncJob job = injector.getInstance(jobClass);
            if (job == null) {
                throw new PreJobExecutionException("Job(" + message.toString() + ") could not be created.");
            }
            setRunning(message.getJobId());
            final Object result = job.execute(status);
            updateStatus(message.getJobId(), AsyncJobStatus.JobState.COMPLETED, result);
            eventSink.sendEvents();
            return status;
        }
        catch (Exception e) {
            updateStatus(message.getJobId(), AsyncJobStatus.JobState.FAILED, e.getMessage());
            eventSink.rollback();
            throw e;
        }
        finally {
            uow.end();
            candlepinRequestScope.exit();
        }
    }

    private void setupLogging(final AsyncJobStatus jdata) {
        MDC.put("requestType", "job");
        if (jdata != null) {
            final JobDataMap map = jdata.getJobData();

            String requestUuid = null;
            String orgKey = null;
            String orgLogLevel = null;

            MDC.put(LoggingFilter.CSID, jdata.getCorrelationId());
            if (map != null) {
                requestUuid = map.getAsString("requestUuid");
                // Impl note: we use the OWNER_ID map key to store the org key
                orgKey = map.getAsString(JobStatus.OWNER_ID);
                orgLogLevel = map.getAsString(JobStatus.OWNER_LOG_LEVEL);
            }

            if (requestUuid != null) {
                MDC.put("requestUuid", requestUuid);
            }

            if (orgKey != null) {
                MDC.put("org", orgKey);
            }

            if (orgLogLevel != null) {
                MDC.put("orgLogLevel", orgLogLevel);
            }
        }
    }

    private void setRunning(final String jobId) {
        final AsyncJobStatus status = this.jobCurator.get(jobId);
        status.setJobExecSource(Util.getHostname());
        status.setState(AsyncJobStatus.JobState.RUNNING);
        this.jobCurator.merge(status);
    }

    private void updateStatus(final String jobId, final AsyncJobStatus.JobState state, final Object result) {
        final AsyncJobStatus status = this.jobCurator.get(jobId);
        status.setJobResult(result);
        status.setState(state);
        this.jobCurator.merge(status);
    }

}
