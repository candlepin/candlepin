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

import org.candlepin.audit.EventSink;
import org.candlepin.auth.Principal;
import org.candlepin.auth.JobPrincipal;
import org.candlepin.auth.SystemPrincipal;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.ModeChangeListener;
import org.candlepin.controller.ModeManager;
import org.candlepin.guice.CandlepinRequestScope;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.model.AsyncJobStatusCurator;
import org.candlepin.model.CandlepinModeChange;
import org.candlepin.model.CandlepinModeChange.Mode;
import org.candlepin.util.Util;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import com.google.inject.Injector;
import com.google.inject.persist.UnitOfWork;

import org.quartz.CronTrigger;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ScheduleBuilder;
import org.quartz.Scheduler;
import org.quartz.SchedulerFactory;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;



/**
 * The JobManager manages the queueing, execution and general bookkeeping on jobs
 */
@Singleton
public class JobManager implements ModeChangeListener {
    private static final Logger log = LoggerFactory.getLogger(JobManager.class);

    private static final String QRTZ_GROUP_CONFIG = "cp_async_config";
    private static final String QRTZ_GROUP_MANUAL = "cp_async_manual";

    /** Stores our mapping of job keys to job classes */
    private static final Map<String, Class<? extends AsyncJob>> JOB_KEY_MAP = new HashMap<>();

    /**
     * Enum representing known manager states, and valid state transitions
     */
    public static enum ManagerState {
        // Impl note: We have to use strings here since we can't reference enums that haven't yet
        // been defined. This is slightly less efficient than I'd like, but whatever.
        CREATED("INITIALIZED", "SHUTDOWN"),
        INITIALIZED("RUNNING", "PAUSED", "SHUTDOWN"),
        RUNNING("RUNNING", "PAUSED", "SHUTDOWN"),
        PAUSED("RUNNING", "PAUSED", "SHUTDOWN"),
        SHUTDOWN();

        private final String[] transitions;

        ManagerState(String... transitions) {
            this.transitions = transitions != null && transitions.length > 0 ? transitions : null;
        }

        public boolean isValidTransition(ManagerState state) {
            if (state != null && this.transitions != null) {
                for (String transition : this.transitions) {
                    if (transition.equals(state.name())) {
                        return true;
                    }
                }
            }

            return false;
        }

        public boolean isTerminal() {
            return this.transitions == null;
        }
    }

    /**
     * Bridge between the Quartz job and Candlepin job execution interfaces
     */
    private static class QuartzJobExecutor implements Job, JobFactory {
        private final JobManager manager;

        public QuartzJobExecutor(JobManager manager) {
            this.manager = manager;
        }

        @Override
        public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler) throws SchedulerException {
            return this;
        }

        @Override
        public void execute(org.quartz.JobExecutionContext context) throws org.quartz.JobExecutionException {
            String name = context.getJobDetail().getKey().getName();

            try {
                log.trace("Queuing job: {}", name);
                this.manager.queueJob(JobConfig.forJob(name));
            }
            catch (JobException e) {
                String errmsg = String.format("Unable to queue scheduled job: %s", name);
                throw new org.quartz.JobExecutionException(errmsg, e);
            }
        }
    }

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
        return JOB_KEY_MAP.put(jobKey, jobClass);
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

        return JOB_KEY_MAP.remove(jobKey);
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

        return JOB_KEY_MAP.get(jobKey);
    }

    private final Configuration configuration;
    private final SchedulerFactory schedulerFactory;
    private final ModeManager modeManager;
    private final AsyncJobStatusCurator jobCurator;
    private final JobMessageDispatcher dispatcher;
    private final CandlepinRequestScope candlepinRequestScope;
    private final PrincipalProvider principalProvider;
    private final Injector injector;

    private ManagerState state;
    private QuartzJobExecutor qrtzExecutor;
    private Scheduler scheduler;

    private boolean clustered;
    private Set<String> whitelist;
    private Set<String> blacklist;
    private Map<String, Configuration> jobConfig;


    /**
     * Creates a new JobManager instance
     */
    @Inject
    public JobManager(
        Configuration configuration,
        SchedulerFactory schedulerFactory,
        ModeManager modeManager,
        AsyncJobStatusCurator jobCurator,
        JobMessageDispatcher dispatcher,
        PrincipalProvider principalProvider,
        CandlepinRequestScope scope,
        Injector injector) {

        this.configuration = Objects.requireNonNull(configuration);
        this.schedulerFactory = Objects.requireNonNull(schedulerFactory);
        this.modeManager = Objects.requireNonNull(modeManager);
        this.jobCurator = Objects.requireNonNull(jobCurator);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.candlepinRequestScope = Objects.requireNonNull(scope);
        this.principalProvider = Objects.requireNonNull(principalProvider);
        this.injector = Objects.requireNonNull(injector);

        this.state = ManagerState.CREATED;
        this.qrtzExecutor = new QuartzJobExecutor(this);

        this.readJobConfiguration(this.configuration);
    }

    /**
     * Reads the job configuration from the provided configuration.
     *
     * @param config
     *  The configuration instance from which to read the job config
     */
    private void readJobConfiguration(Configuration config) {
        // Check if our scheduler is running in "clustered" mode
        this.clustered = config.getBoolean("org.quartz.jobStore.isClustered", false);

        // Get the job whitelist and blacklist
        List<String> list = config.getList(ConfigProperties.ASYNC_JOBS_WHITELIST, null);
        this.whitelist = list != null ? new HashSet<>(list) : null;

        list = config.getList(ConfigProperties.ASYNC_JOBS_BLACKLIST, null);
        this.blacklist = list != null ? new HashSet<>(list) : null;

        // Read the per-job configuration
        this.jobConfig = new HashMap<>();
        String prefix = ConfigProperties.ASYNC_JOBS_PREFIX;
        Pattern regex = Pattern.compile("\\A" + Pattern.quote(prefix) + "(.+)\\.[^.]+\\z");

        Iterable<String> cfgKeys = config.getKeys();
        if (cfgKeys != null) {
            for (String key : cfgKeys) {
                Matcher matcher = regex.matcher(key);

                if (matcher.matches()) {
                    String job = matcher.group(1);

                    if (!this.jobConfig.containsKey(job)) {
                        Configuration subset = config.strippedSubset(prefix + job + '.');
                        this.jobConfig.put(job, subset);
                    }
                }
            }
        }
    }

    /**
     * Perform final initialization once that could not be performed during construction. Once this
     * method has been called, it should not be called again.
     *
     * @throws IllegalStateException
     *  if this JobManager has already been initialized, or is otherwise in a state where
     *  initialization cannot be performed
     */
    public synchronized void initialize() {
        // TODO: We're probably going to want to add some bits to avoid queuing/scheduling new jobs
        // during shutdown. We probably also want to have a means of closing the message listeners
        // backing all of this as well, so we don't try to execute jobs before we're initialized or
        // during/after shutdown.

        // Perform state transition
        this.validateStateTransition(ManagerState.INITIALIZED);

        try {
            log.info("Initializing job manager");

            if (this.state == ManagerState.CREATED) {
                if (this.scheduler == null || this.scheduler.isShutdown()) {
                    this.scheduler = this.schedulerFactory.getScheduler();
                }

                this.synchronizeJobSchedule();
                this.scheduler.setJobFactory(this.qrtzExecutor);
                this.modeManager.registerModeChangeListener(this);
            }

            log.info("Job manager initialization complete");
            this.state = ManagerState.INITIALIZED;
        }
        catch (Exception e) {
            log.error("Unexpected exception occurred during initialization", e);

            try {
                if (this.scheduler != null) {
                    this.scheduler.shutdown();
                    this.scheduler = null;
                }
            }
            catch (SchedulerException se) {
                log.error("Unable to shutdown quartz scheduler while processing other exceptions", se);
            }

            // TODO: Change this to something a bit nicer. Maybe a JobException?
            throw new RuntimeException(e);
        }
    }

    /**
     * Starts or resumes this job manager if it is currently not running, allowing jobs to be
     * executed. If the job manager is already running, this method silently returns.
     *
     * @throws IllegalStateException
     *  if this JobManager has not been initialized, has already shutdown or Candlepin is
     *  in suspend mode
     */
    public synchronized void start() {
        log.trace("Start request received");

        this.validateStateTransition(ManagerState.RUNNING);

        // Check that we're not in suspend mode
        CandlepinModeChange lastMode = this.modeManager.getLastCandlepinModeChange();
        if (lastMode != null && lastMode.getMode() != Mode.NORMAL) {
            throw new IllegalStateException("Candlepin must be in NORMAL mode to start the job manager");
        }

        try {
            if (this.state == ManagerState.INITIALIZED || this.state == ManagerState.PAUSED) {
                this.scheduler.start();
            }

            log.info("Job manager started");
            this.state = ManagerState.RUNNING;
        }
        catch (Exception e) {
            log.error("Unexpected exception occurred while starting job manager", e);
        }
    }

    /**
     * Synonym of the start operation.
     *
     * @throws IllegalStateException
     *  if this JobManager has not been initialized or has already shutdown
     */
    public void resume() {
        this.start();
    }


    /**
     * Pauses this job manager, preventing jobs from being executed until the manager is resumed.
     * If the job manager is already paused, this method silently returns.
     * <p></p>
     * Pausing will not stop currently executing jobs, nor will it prevent new jobs from being
     * scheduled or queued for later execution. If a scheduled job's next appointed time occurs
     * while the manager is paused, the collision will be resolved according to the job's
     * constraints.
     *
     * @throws IllegalStateException
     *  if this JobManager has not been initialized or has already shutdown
     */
    public synchronized void pause() {
        log.trace("Pause request received");

        this.validateStateTransition(ManagerState.PAUSED);

        try {
            if (this.state == ManagerState.RUNNING) {
                this.scheduler.standby();
            }

            log.info("Job manager paused");
            this.state = ManagerState.PAUSED;
        }
        catch (Exception e) {
            log.error("Unexpected exception occurred while pausing job manager", e);
        }
    }

    /**
     * Shuts down this job manager, preventing jobs from being scheduled, queued or executed.
     * Shutting down will not stop currently executing jobs, but will stop this manager from
     * processing any new execution requests.
     *
     * @throws IllegalStateException
     *  if this JobManager has already been shutdown, or is otherwise in a state where a shut
     *  down cannot be performed
     */
    public synchronized void shutdown() {
        // TODO: actually do something with this

        this.validateStateTransition(ManagerState.SHUTDOWN);

        try {
            log.info("Shutting down job manager");

            if (this.state == ManagerState.RUNNING || this.state == ManagerState.PAUSED) {
                this.scheduler.shutdown(true);
            }

            log.info("Job manager shut down");
            this.state = ManagerState.SHUTDOWN;
        }
        catch (Exception e) {
            log.error("Unexpected exception occurred while shutting down job manager", e);
        }
    }

    /**
     * Checks if the target state is a valid state from which the current state can transition.
     *
     * @param target
     *  The target state to enter
     *
     * @throws IllegalStateException
     *  if the target state is an invalid transition from the current state
     */
    private void validateStateTransition(ManagerState target) {
        if (!this.state.isValidTransition(target)) {
            String errmsg = String.format("Cannot transition manager state from \"%s\" to \"%s\"",
                this.state.name(), target.name());

            log.error(errmsg);
            throw new IllegalStateException(errmsg);
        }
    }

    /**
     * Checks if the specified job is enabled. Jobs default to enabled, but can be disabled if any
     * of the following occur, in order:
     *
     *  - The per-job enabled flag is set to false
     *  - The job is in the async jobs blacklist
     *  - The async jobs whitelist is non-empty and does not contain the specified job
     *
     * @param jobKey
     *  The key of the job to check, or the job's fully qualified class name if the job does not have
     *  a job key
     *
     * @return
     *  true if the job is enabled, false otherwise
     */
    public boolean isJobEnabled(String jobKey) {
        if (jobKey == null) {
            throw new IllegalArgumentException("jobKey is null");
        }

        // Check per-job config
        Configuration config = this.jobConfig.get(jobKey);
        if (config != null && !config.getBoolean(ConfigProperties.ASYNC_JOBS_SUFFIX_ENABLED, true)) {
            return false;
        }

        // Check blacklist
        if (this.blacklist != null && this.blacklist.contains(jobKey)) {
            return false;
        }

        // Check whitelist
        if (this.whitelist != null && !this.whitelist.contains(jobKey)) {
            return false;
        }

        // Job is enabled!
        return true;
    }


    /**
     * Ensures the jobs automatically scheduled according to the system configuration are in sync
     * with the current configuration.
     */
    private void synchronizeJobSchedule() throws SchedulerException {
        // TODO: Quartz isn't doing much for us here. Perhaps we'd be better off with our own
        // minimalistic scheduler, since we only need cron, delay and interval scheduling. Quartz
        // seems pretty heavy considering what we actually use it for here.

        GroupMatcher<JobKey> qrtzJobMatcher = GroupMatcher.jobGroupEquals(QRTZ_GROUP_CONFIG);
        Set<JobKey> qrtzJobKeys = this.scheduler.getJobKeys(qrtzJobMatcher);

        Set<String> existing = new HashSet<>();
        Set<JobKey> unschedule = new HashSet<>();

        // TODO: Improve this! There isn't a scenario where this works in the way it should.

        if (qrtzJobKeys != null && qrtzJobKeys.size() > 0) {
            log.debug("Checking {} existing scheduled jobs...", qrtzJobKeys.size());

            for (JobKey key : qrtzJobKeys) {
                // Impl note: using the key's name as the job key works, but it limits each
                // job to exactly one schedule, which may or may not be a good thing.
                Configuration config = this.jobConfig.get(key.getName());

                // Check if the job has any configuration at all
                if (config == null) {
                    unschedule.add(key);
                    continue;
                }

                String schedule = config.getString(ConfigProperties.ASYNC_JOBS_SUFFIX_SCHEDULE, null);
                boolean enabled = this.isJobEnabled(key.getName());

                // Check that the job is enabled and is still scheduled
                if (!enabled || schedule == null) {
                    unschedule.add(key);
                    continue;
                }

                Trigger trigger = this.scheduler.getTrigger(
                    TriggerKey.triggerKey(key.getName(), key.getGroup()));

                // Check that the job has a trigger, it's a cron trigger, and the schedule matches
                // the schedule in the current configuration
                if (trigger == null || !(trigger instanceof CronTrigger) ||
                    !((CronTrigger) trigger).getCronExpression().equals(schedule)) {

                    unschedule.add(key);
                    continue;
                }

                // Job exists and matches our schedule, we can skip it later
                existing.add(key.getName());
            }
        }

        // Unschedule dead/invalid jobs
        for (JobKey key : unschedule) {
            this.scheduler.deleteJob(key);
            log.info("Removed existing schedule for job: {}", key.getName());
        }

        // Schedule new jobs
        for (Map.Entry<String, Configuration> entry : this.jobConfig.entrySet()) {
            try {
                String jobName = entry.getKey();
                Configuration config = entry.getValue();

                String schedule = config.getString(ConfigProperties.ASYNC_JOBS_SUFFIX_SCHEDULE, null);
                boolean enabled = this.isJobEnabled(jobName);

                // If the job is not enabled, already scheduled or has no schedule, skip it.
                if (!enabled || existing.contains(jobName) || schedule == null) {
                    continue;
                }

                ScheduleBuilder qtzSchedule = CronScheduleBuilder.cronSchedule(schedule)
                    .withMisfireHandlingInstructionDoNothing();

                Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(jobName, QRTZ_GROUP_CONFIG)
                    .withSchedule(qtzSchedule)
                    .build();

                JobDetail detail = org.quartz.JobBuilder.newJob(QuartzJobExecutor.class)
                    .withIdentity(jobName, QRTZ_GROUP_CONFIG)
                    .build();

                this.scheduler.scheduleJob(detail, trigger);

                log.info("Scheduled job \"{}\" with cron schedule: {}", jobName, schedule);
            }
            catch (Exception e) {
                log.error("Unable to schedule job \"{}\":", e);
            }
        }
    }

    /**
     * Builds an AsyncJobStatus instance from the given job details. The job status will not be
     * a managed entity and will need to be manually persisted by the caller.
     *
     * @param builder
     *  The JobConfig to use to build the job status
     *
     * @throws IllegalArgumentException
     *  if detail is null
     *
     * @return
     *  the newly constructed, unmanaged AsyncJobStatus instance
     */
    private AsyncJobStatus buildJobStatus(JobConfig builder) {
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

        // Metadata and logging configuration...
        job.setMetadata(builder.getJobMetadata());

        String csid = MDC.get(LoggingFilter.CSID_KEY);
        if (csid != null && !csid.isEmpty()) {
            job.addMetadata(LoggingFilter.CSID_KEY, csid);
        }

        job.setLogLevel(builder.getLogLevel());
        job.logExecutionDetails(builder.logExecutionDetails());

        // Retry and runtime configuration...
        job.setMaxAttempts(builder.getRetryCount() + 1);
        job.setJobArguments(builder.getJobArguments());

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
     * @param config
     *  A JobConfig instance representing the configuration of the job to queue
     *
     * @return
     *  an AsyncJobStatus instance representing the queued job's status, or the status of the
     *  existing job if it already exists
     */
    public synchronized AsyncJobStatus queueJob(JobConfig config) throws JobException {
        // TODO: Check manager state

        if (config == null) {
            throw new IllegalArgumentException("job config is null");
        }

        config.validate();

        // TODO:
        // Don't allow queueing jobs which are disabled? Should that be disabled entirely or not
        // runnable by this node?

        AsyncJobStatus status = this.buildJobStatus(config);
        status.setState(JobState.CREATED);

        try {
            // Check if the queueing is blocked by constraints
            Collection<JobConstraint> constraints = config.getConstraints();
            Set<AsyncJobStatus> blockingJobs = new HashSet<>();

            if (constraints != null && !constraints.isEmpty()) {
                Collection<AsyncJobStatus> jobs = this.jobCurator.getNonTerminalJobs();

                for (AsyncJobStatus existing : jobs) {
                    // Check inbound job's constraints
                    for (JobConstraint constraint : constraints) {
                        if (constraint.test(status, existing)) {
                            blockingJobs.add(existing);
                        }
                    }

                    // TODO: Add support for two-way checking of job constraints
                }
            }

            if (blockingJobs.isEmpty()) {
                // Persist the job status so that the ID will be generated.
                status = this.jobCurator.create(status);

                // Build and send the job message and update the job state accordingly
                status = this.postJobStatusMessage(status);
                log.info("Job queued: {}", status);
            }
            else {
                // TODO: Add support for the WAITING option. For now, always default to ABORTED

                String jobIds = blockingJobs.stream()
                    .map(AsyncJobStatus::getId)
                    .collect(Collectors.joining(", "));

                status.setState(JobState.ABORTED);
                status.setJobResult(String.format("Job queuing blocked by the following jobs: %s", jobIds));

                log.info("Unable to queue job: {}; blocked by the following existing jobs: {}",
                    status.getName(), jobIds);
            }
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
            status.setJobResult(e.toString());
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
            status.setJobResult(e.toString());
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

            this.updateJobStatus(status, JobState.FAILED, e.toString());

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
    public synchronized AsyncJobStatus executeJob(final JobMessage message) throws JobException {
        // TODO: Check manager state

        AsyncJobStatus status = this.fetchJobStatus(message);

        final Class<? extends AsyncJob> jobClass = getJobClass(message.getJobKey());
        candlepinRequestScope.enter();
        final EventSink eventSink = injector.getInstance(EventSink.class);
        final UnitOfWork uow = injector.getInstance(UnitOfWork.class);

        try {
            uow.begin();
            this.setupLogging(status);
            this.setupPrincipal(status);

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

            ResteasyProviderFactory.popContextData(Principal.class);
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
     * Configures and injects the principal to be used during the execution of the specified job.
     *
     * @param status
     *  the job status to use to configure the principal
     */
    private void setupPrincipal(AsyncJobStatus status) {
        String name = status.getPrincipal();
        Principal principal = name != null ? new JobPrincipal(name) : new SystemPrincipal();

        ResteasyProviderFactory.pushContext(Principal.class, principal);
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

        // Sanity check: make sure we don't try to execute a job that's disabled on this node
        // This shouldn't happen, as we should be properly filtering which messages we pull off the
        // message queue, but if that fails, we'll fall back to failing non-terminally.
        if (status.getJobKey() == null || !this.isJobEnabled(status.getJobKey())) {
            String errmsg = String.format("Job \"%s\" (%s) is not enabled on this node",
                status.getName(), status.getJobKey());

            log.error(errmsg);
            throw new JobInitializationException(errmsg, false);
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
     * @param throwable
     *  the Throwable instance representing the failure that occurred
     *
     * @param retry
     *  whether or not the job should be retried
     *
     * @return
     *  the updated AsyncJobStatus entity
     */
    private AsyncJobStatus processJobFailure(AsyncJobStatus status, EventSink eventSink, Throwable throwable,
        boolean retry) throws JobStateManagementException, JobMessageDispatchException {

        // Set the end of the execution attempt
        status.setEndTime(new Date());

        // Rollback any unsent events
        eventSink.rollback();

        // Only store the exception type and the message. Luckily for us, toString does exactly that.
        String result = throwable != null ? throwable.toString() : null;

        if (retry) {
            status = this.updateJobStatus(status, JobState.FAILED_WITH_RETRY, result);

            log.warn("Job \"{}\" failed in {}ms; retrying...",
                status.getName(), this.getJobRuntime(status), throwable);

            this.postJobStatusMessage(status);
        }
        else {
            status = this.updateJobStatus(status, JobState.FAILED, result);

            log.error("Job \"{}\" failed in {}ms",
                status.getName(), this.getJobRuntime(status), throwable);
        }

        return status;
    }

    /**
     * @{inheritDoc}
     */
    @Override
    public void modeChanged(Mode mode) {
        if (mode != null) {
            switch (mode) {
                case SUSPEND:
                    this.pause();
                    break;

                case NORMAL:
                    this.start();
                    break;

                default:
                    log.warn("Received an unexpected mode change notice: {}", mode);
            }
        }
    }
}
