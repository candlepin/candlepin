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
import org.candlepin.auth.JobPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SystemPrincipal;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.mode.CandlepinModeManager;
import org.candlepin.controller.mode.CandlepinModeManager.Mode;
import org.candlepin.controller.mode.ModeChangeListener;
import org.candlepin.guice.CandlepinRequestScope;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.model.AsyncJobStatusCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.util.Util;

import com.google.inject.Injector;
import com.google.inject.persist.Transactional;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import org.jboss.resteasy.core.ResteasyContext;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ScheduleBuilder;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;
import javax.transaction.Status;
import javax.transaction.Synchronization;



/**
 * The JobManager manages the queueing, execution and general bookkeeping on jobs
 */
@Singleton
public class JobManager implements ModeChangeListener {
    private static final Logger log = LoggerFactory.getLogger(JobManager.class);

    private static final String UNKNOWN_OWNER_KEY = "-UNKNOWN-";

    private static final String MDC_REQUEST_TYPE_KEY = "requestType";
    private static final String MDC_REQUEST_UUID_KEY = "requestUuid";
    private static final String MDC_LOG_LEVEL_KEY = "logLevel";

    private static final String QRTZ_GROUP_CONFIG = "cp_async_config";
    private static final String QRTZ_GROUP_MANUAL = "cp_async_manual";

    private static final Object SUSPEND_KEY_DEFAULT = "default_suspend_key";
    private static final Object SUSPEND_KEY_TRIGGERED = "triggered_suspend_key";

    /** Stores our mapping of job keys to job classes */
    private static final Map<String, Class<? extends AsyncJob>> JOB_KEY_MAP = new HashMap<>();

    /**
     * Enum representing known manager states, and valid state transitions
     */
    public enum ManagerState {
        // Impl note: We have to use strings here since we can't reference enums that haven't yet
        // been defined. This is slightly less efficient than I'd like, but whatever.
        CREATED("INITIALIZED", "SHUTDOWN"),
        INITIALIZED("RUNNING", "SUSPENDED", "SHUTDOWN"),
        RUNNING("RUNNING", "SUSPENDED", "SHUTDOWN"),
        SUSPENDED("RUNNING", "SUSPENDED", "SHUTDOWN"),
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
            String jobKey = context.getJobDetail().getKey().getName();

            try {
                log.trace("Queuing job: {}", jobKey);
                this.manager.queueJob(JobConfig.forJob(jobKey));
            }
            catch (JobException e) {
                String errmsg = String.format("Unable to queue scheduled job: %s", jobKey);
                throw new org.quartz.JobExecutionException(errmsg, e);
            }
        }
    }

    /**
     * The JobMessageSynchronizer commits or rolls back a messenger transaction upon the completion
     * of a database tranaction.
     */
    private static class JobMessageSynchronizer implements Synchronization {
        /** An array of states that are valid to synchronize against */
        public static final TransactionStatus[] ACTIVE_STATES = {
            TransactionStatus.ACTIVE, TransactionStatus.MARKED_ROLLBACK
        };

        private final JobMessageDispatcher dispatcher;

        public JobMessageSynchronizer(JobMessageDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public void afterCompletion(int status) {
            try {
                switch (status) {
                    case Status.STATUS_COMMITTED:
                        log.debug("Transaction committed");
                        this.dispatcher.commit();
                        break;

                    case Status.STATUS_ROLLEDBACK:
                        log.debug("Transaction rolled back");
                        this.dispatcher.rollback();
                        break;

                    default:
                        // We don't care about other states, and they shouldn't be provided here anyhow
                        log.debug("Received unexpected transaction completion status: {}", status);
                }
            }
            catch (JobMessageDispatchException e) {
                log.error("Error occurred while attempting to synchronize database and message bus", e);
            }
        }

        @Override
        public void beforeCompletion() {
            // Intentionally left empty
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
    private final CandlepinModeManager modeManager;
    private final AsyncJobStatusCurator jobCurator;
    private final OwnerCurator ownerCurator;
    private final JobMessageDispatcher dispatcher;
    private final JobMessageReceiver receiver;
    private final CandlepinRequestScope candlepinRequestScope;
    private final PrincipalProvider principalProvider;
    private final Injector injector;

    private ManagerState state;
    private JobMessageSynchronizer synchronizer;
    private QuartzJobExecutor qrtzExecutor;
    private Scheduler scheduler;
    private ThreadLocal<Map<String, String>> mdcState;
    private Set<Object> suspendKeys;

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
        CandlepinModeManager modeManager,
        AsyncJobStatusCurator jobCurator,
        OwnerCurator ownerCurator,
        JobMessageDispatcher dispatcher,
        JobMessageReceiver receiver,
        PrincipalProvider principalProvider,
        CandlepinRequestScope scope,
        Injector injector) {

        this.configuration = Objects.requireNonNull(configuration);
        this.schedulerFactory = Objects.requireNonNull(schedulerFactory);
        this.modeManager = Objects.requireNonNull(modeManager);
        this.jobCurator = Objects.requireNonNull(jobCurator);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.receiver = Objects.requireNonNull(receiver);
        this.candlepinRequestScope = Objects.requireNonNull(scope);
        this.principalProvider = Objects.requireNonNull(principalProvider);
        this.injector = Objects.requireNonNull(injector);

        this.state = ManagerState.CREATED;
        this.qrtzExecutor = new QuartzJobExecutor(this);
        this.mdcState = new ThreadLocal<>();
        this.suspendKeys = new HashSet<>();

        this.synchronizer = new JobMessageSynchronizer(this.dispatcher);

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
        this.clustered = config.getBoolean(ConfigProperties.QUARTZ_CLUSTERED_MODE, false);

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
    public synchronized void initialize() throws StateManagementException {
        // TODO: We're probably going to want to add some bits to avoid queuing/scheduling new jobs
        // during shutdown. We probably also want to have a means of closing the message listeners
        // backing all of this as well, so we don't try to execute jobs before we're initialized or
        // during/after shutdown.

        // Perform state transition
        this.validateStateTransition(ManagerState.INITIALIZED);

        try {
            log.info("Initializing job manager");

            if (this.state == ManagerState.CREATED) {
                if (this.isSchedulerEnabled()) {
                    if (this.scheduler == null || this.scheduler.isShutdown()) {
                        this.scheduler = this.schedulerFactory.getScheduler();
                    }

                    this.synchronizeJobSchedule();
                    this.scheduler.setJobFactory(this.qrtzExecutor);
                }

                if (!this.receiver.isInitialized()) {
                    this.receiver.initialize(this);
                }

                this.modeManager.registerModeChangeListener(this);

                // Check if Candlepin's current operating mode would prevent us from starting
                // normally.
                if (this.modeManager.getCurrentMode() == Mode.SUSPEND) {
                    this.suspendKeys.add(SUSPEND_KEY_TRIGGERED);
                }
            }

            log.info("Job manager initialization complete");
            this.state = ManagerState.INITIALIZED;
        }
        catch (Exception e) {
            String errmsg = "Unexpected exception occurred during initialization";

            log.error(errmsg, e);

            try {
                if (this.scheduler != null) {
                    this.scheduler.shutdown();
                    this.scheduler = null;
                }
            }
            catch (SchedulerException se) {
                log.error("Unable to shutdown quartz scheduler while processing other exceptions", se);
            }

            throw new StateManagementException(this.state, ManagerState.INITIALIZED, errmsg, e);
        }
    }

    /**
     * Attempts to start or resume this job manager by lifting the default suspend key. If all
     * suspend keys have been lifted, the job manager will be resumed. If the job manager is not
     * currently in a suspended state, this method silently returns.
     *
     * @throws IllegalStateException
     *  if this JobManager has not been initialized or has already shutdown
     */
    public void start() throws StateManagementException {
        this.start(SUSPEND_KEY_DEFAULT);
    }

    /**
     * Attempts to start or resume this job manager by lifting the specified suspend key. If all
     * suspend keys have been lifted, the job manager will be resumed. If the job manager is not
     * currently in a suspended state, this method silently returns.
     *
     * @param key
     *  the suspend key to lift; must not be null
     *
     * @throws IllegalArgumentException
     *  if the specified suspend key is null
     *
     * @throws IllegalStateException
     *  if this JobManager has not been initialized, has already shutdown or Candlepin is
     *  in suspend mode
     */
    public synchronized void start(Object key) throws StateManagementException {
        log.trace("Start request received with suspend key: {}", key);

        if (key == null) {
            throw new IllegalArgumentException("suspend key is null");
        }

        this.validateStateTransition(ManagerState.RUNNING);

        try {
            if (this.state == ManagerState.INITIALIZED || this.state == ManagerState.SUSPENDED) {
                if (this.suspendKeys.remove(key)) {
                    log.debug("Lifted job manager suspend key: {} ({} remaining)", key,
                        this.suspendKeys.size());
                }

                if (this.suspendKeys.isEmpty()) {
                    String startType = (this.state == ManagerState.INITIALIZED ? "started" : "resumed");
                    log.info("Job manager {}", startType);

                    if (this.isSchedulerEnabled()) {
                        this.scheduler.start();
                    }

                    this.receiver.start();

                    this.state = ManagerState.RUNNING;
                }
                else {
                    log.debug("Job manager still suspended by {} keys", this.suspendKeys.size());
                    this.state = ManagerState.SUSPENDED;
                }
            }
        }
        catch (Exception e) {
            String errmsg = "Unexpected exception occurred while starting job manager";

            log.error(errmsg, e);
            throw new StateManagementException(this.state, ManagerState.RUNNING, errmsg, e);
        }
    }

    /**
     * Attempts to resume this job manager by lifting the default suspend key. If all suspend keys
     * have been lifted, the job manager will be resumed. If the job manager is not currently in a
     * suspended state, this method silently returns.
     *
     * @throws IllegalStateException
     *  if this JobManager has not been initialized or has already shutdown
     */
    public void resume() throws StateManagementException {
        this.start(SUSPEND_KEY_DEFAULT);
    }

    /**
     * Attempts to resume this job manager by lifting the specified suspend key. If all suspend keys
     * have been lifted, the job manager will be resumed. If the job manager is not currently in a
     * suspended state, this method silently returns.
     *
     * @param key
     *  the suspend key to lift; cannot be null
     *
     * @throws IllegalArgumentException
     *  if the specified suspend key is null
     *
     * @throws IllegalStateException
     *  if this JobManager has not been initialized, has already shutdown or Candlepin is
     *  in suspend mode
     */
    public void resume(Object key) throws StateManagementException {
        this.start(key);
    }

    /**
     * Suspends this job manager with the default suspend key, preventing jobs from being executed
     * until the manager is resumed by lifting all applied suspend keys. If the job manager is
     * already suspended, this method silently returns.
     * <p></p>
     * Suspending will not stop currently executing jobs, nor will it prevent new jobs from being
     * scheduled or queued for later execution. If a scheduled job's next appointed time occurs
     * while the manager is suspended, the collision will be resolved according to the job's
     * constraints.
     *
     * @throws IllegalStateException
     *  if this JobManager has not been initialized or has already shutdown
     */
    public void suspend() throws StateManagementException {
        this.suspend(SUSPEND_KEY_DEFAULT);
    }

    /**
     * Suspends this job manager with the specified suspend key, preventing jobs from being executed
     * until the manager is resumed by lifting all applied suspend keys. If the job manager is
     * already suspended, this method silently returns.
     * <p></p>
     * Suspending will not stop currently executing jobs, nor will it prevent new jobs from being
     * scheduled or queued for later execution. If a scheduled job's next appointed time occurs
     * while the manager is suspended, the collision will be resolved according to the job's
     * constraints.
     * <p></p>
     * The key provided to suspend job execution can be any object except those with non-standard,
     * or inconsistent implementations of equals and/or hashCode.
     *
     * @param key
     *  the suspend key to apply; cannot be null
     *
     * @throws IllegalArgumentException
     *  if the specified suspend key is null
     *
     * @throws IllegalStateException
     *  if this JobManager has not been initialized or has already shutdown
     */
    public synchronized void suspend(Object key) throws StateManagementException {
        log.trace("Suspend request received with key: {}", key);

        if (key == null) {
            throw new IllegalArgumentException("suspend key is null");
        }

        this.validateStateTransition(ManagerState.SUSPENDED);

        try {
            if (this.suspendKeys.add(key)) {
                log.debug("Suspending job manager with key: {}", key);
            }

            if (this.state == ManagerState.INITIALIZED || this.state == ManagerState.RUNNING) {
                this.receiver.suspend();

                if (this.isSchedulerEnabled()) {
                    this.scheduler.standby();
                }

                log.info("Job manager suspended");
                this.state = ManagerState.SUSPENDED;
            }
        }
        catch (Exception e) {
            String errmsg = "Unexpected exception occurred while pausing job manager";

            log.error(errmsg, e);
            throw new StateManagementException(this.state, ManagerState.SUSPENDED, errmsg, e);
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
    public synchronized void shutdown() throws StateManagementException {
        // TODO: actually do something with this

        this.validateStateTransition(ManagerState.SHUTDOWN);

        try {
            log.info("Shutting down job manager");

            if (this.state == ManagerState.RUNNING || this.state == ManagerState.SUSPENDED) {
                this.dispatcher.shutdown();
                this.receiver.shutdown();

                if (this.isSchedulerEnabled()) {
                    this.scheduler.shutdown(true);
                }
            }

            log.info("Job manager shut down");
            this.state = ManagerState.SHUTDOWN;
        }
        catch (Exception e) {
            String errmsg = "Unexpected exception occurred while shutting down job manager";

            log.error(errmsg, e);
            throw new StateManagementException(this.state, ManagerState.SHUTDOWN, errmsg, e);
        }
    }

    /**
     * Fetches the current state of this job manager.
     *
     * @return
     *  the current state of this job manager
     */
    public synchronized ManagerState getManagerState() {
        return this.state;
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
     * Checks if the specified job is enabled. Jobs default to enabled, but can be enabled or
     * disabled based on a handful of configurations. The general processing of these configs is in
     * order of most to least explicit, leading to the following algorithm:
     *
     * - If the job-specific configuration option is specified, that value is used in all
     *   situations.
     * - If no job-specific configuration option is provided, the blacklist is checked. If the job
     *   appears on the blacklist, it is disabled.
     * - If no job-specific configuration option is provided and the job does not appear on the
     *   blacklist, the whitelist is checked. If no whitelist is provided, or the job appears on
     *   the whitelist, it is enabled. If the whitelist is specified and the job is not present, it
     *   is disabled.
     * - If all of the above checks fail or are otherwise inconclusive, the job is assumed to be
     *   enabled.
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

        if (config != null) {
            if (config.containsKey(ConfigProperties.ASYNC_JOBS_JOB_ENABLED)) {
                return config.getBoolean(ConfigProperties.ASYNC_JOBS_JOB_ENABLED);
            }

            // Property is not defined for this job; check other fields
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
     * Checks if the job scheduler is enabled.
     * <p></p>
     * The scheduler controls whether or jobs will be automatically fired at their scheduled times.
     * Disabling the scheduler will *not* prevent jobs from being scheduled by other means (on event
     * or manual execution), but it will prevent this node from triggering jobs according to any
     * schedule set for them.
     *
     * @return
     *  true if the scheduler is enabled; false otherwise
     */
    public boolean isSchedulerEnabled() {
        return this.configuration.getBoolean(ConfigProperties.ASYNC_JOBS_SCHEDULER_ENABLED, true);
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

                String schedule = config.getString(ConfigProperties.ASYNC_JOBS_JOB_SCHEDULE, null);
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

                String schedule = config.getString(ConfigProperties.ASYNC_JOBS_JOB_SCHEDULE, null);
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
        job.setState(JobState.CREATED);

        job.setJobKey(builder.getJobKey());
        job.setName(builder.getJobName() != null ? builder.getJobName() : builder.getJobKey());
        job.setGroup(builder.getJobGroup());
        job.setContextOwner(builder.getContextOwner());

        // Add environment-specific metadata...
        job.setOrigin(Util.getHostname());
        Principal principal = this.principalProvider.get();
        job.setPrincipalName(principal != null ? principal.getName() : null);

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
     * Performs a job state transition after validating the transition is a valid one.
     *
     * @param status
     *  the job status to update
     *
     * @param state
     *  the job state to transition to
     *
     * @throws IllegalStateException
     *  if the state transition is not valid
     *
     * @return
     *  a refrence to the provided job status
     */
    private AsyncJobStatus setJobState(AsyncJobStatus status, JobState state) {
        JobState currentState = status.getState();

        if (!currentState.isValidTransition(state)) {
            String errmsg = String.format("Cannot transition from state %s to %s",
                currentState.name(), state.name());

            throw new IllegalStateException(errmsg);
        }

        return status.setState(state);
    }

    /**
     * Fetches the job status associated with the specified job ID. If no such job status could be
     * found, this method returns null.
     *
     * @param jobId
     *  the ID of the job to fetch
     *
     * @return
     *  the job status associated with the specified job ID, or null if no such job status could be
     *  found
     */
    public AsyncJobStatus findJob(String jobId) {
        return this.jobCurator.get(jobId);
    }

    /**
     * Fetches a collection of jobs based on the provided filter data in the query builder. If the
     * query builder is null or contains no arguments, this method will return all known async jobs.
     *
     * @param queryBuilder
     *  an AsyncJobStatusQueryBuilder instance containing the various arguments or filters to use
     *  to select jobs
     *
     * @return
     *  a list of jobs matching the provided query arguments/filters
     */
    public List<AsyncJobStatus> findJobs(AsyncJobStatusCurator.AsyncJobStatusQueryBuilder queryBuilder) {
        return this.jobCurator.findJobs(queryBuilder);
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

        ManagerState state = this.getManagerState();
        if (state != ManagerState.RUNNING) {
            // Check if we're paused. If so, and if the "queue while paused" config is not set,
            // throw our usual ISE
            if (state != ManagerState.SUSPENDED ||
                !this.configuration.getBoolean(ConfigProperties.ASYNC_JOBS_QUEUE_WHILE_SUSPENDED)) {

                String msg = String.format("Jobs cannot be queued while the manager is in the %s state",
                    state);

                throw new IllegalStateException(msg);
            }
        }

        if (config == null) {
            throw new IllegalArgumentException("job config is null");
        }

        config.validate();

        // TODO:
        // Don't allow queueing jobs which are disabled? Should that be disabled entirely or not
        // runnable by this node?

        AsyncJobStatus status = this.buildJobStatus(config);

        try {
            // Check if the queueing is blocked by constraints
            Collection<JobConstraint> constraints = config.getConstraints();
            Set<AsyncJobStatus> blockingJobs = new HashSet<>();

            if (constraints != null && !constraints.isEmpty()) {
                Collection<AsyncJobStatus> existingJobs = Collections.unmodifiableList(
                    this.jobCurator.getNonTerminalJobs());

                // Check inbound job's constraints
                for (JobConstraint constraint : constraints) {
                    Collection<AsyncJobStatus> blocking = constraint.test(status, existingJobs);

                    if (blocking != null) {
                        blockingJobs.addAll(blocking);
                    }
                }

                // TODO: Add support for two-way checking of job constraints
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

                this.setJobState(status, JobState.ABORTED);
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
            this.setJobState(status, JobState.QUEUED);
        }
        catch (JobMessageDispatchException e) { // Temporary exception branch
            log.error("Unable to dispatch job message for new job: {}; deleting job and returning",
                status.getName(), e);

            // We created the job, but were unable to send the job to Artemis. The job is dead
            // at this point. We *could* retry, but at the time of writing, we have no mechanism
            // for enabling retry or async messaging (comes with scheduling). As such, we'll
            // kill the job

            this.jobCurator.delete(status);

            this.setJobState(status, JobState.FAILED);
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

            this.setJobState(status, JobState.FAILED);
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
    @Transactional
    protected AsyncJobStatus postJobStatusMessage(AsyncJobStatus status)
        throws JobStateManagementException, JobMessageDispatchException {

        // Impl note:
        // To minimize the possibility of having a race condition between updating the job status
        // in the database and sending the job message to Artemis, this method should always run
        // within the context of a DB transaction. At the time of writing, this method is annotated
        // with the @Transactional tag to force this, but it can be removed if, and only if, other
        // measures are taken to ensure we can properly delay the sending of the job message until
        // after the database is updated.

        try {
            // Build and send the job message
            JobMessage message = new JobMessage(status.getId(), status.getJobKey());
            this.dispatcher.postJobMessage(message);

            // Update the job's status
            status = this.updateJobStatus(status, JobState.QUEUED, null);

            // Register our synchronizer to commit or rollback the dispatcher based on whether
            // or not the current DB transaction completes
            Session session = this.jobCurator.currentSession();
            Transaction transaction = session.getTransaction();

            if (transaction != null &&
                transaction.getStatus().isOneOf(JobMessageSynchronizer.ACTIVE_STATES)) {

                // We have an active transaction (probably); register the synchronizer to pass
                // through the commit/rollback to the messaging bus.
                transaction.registerSynchronization(this.synchronizer);
            }
            else {
                // No (active) transaction, immediately commit the messages. This should never happen.
                log.warn("No active transaction while posting job messages; dispatching immediately.");
                this.dispatcher.commit();
            }

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
    public synchronized AsyncJobStatus executeJob(JobMessage message) throws JobException {

        ManagerState state = this.getManagerState();
        if (this.getManagerState() != ManagerState.RUNNING) {
            String msg = String.format("Jobs cannot be executed while the manager is in the %s state", state);
            throw new IllegalStateException(msg);
        }

        AsyncJobStatus status = this.fetchJobStatus(message);

        // If the job was canceled, just return. No need to do anything special here.
        if (status.getState() == JobState.CANCELED) {
            log.debug("Skipping canceled job: {} ({})", status.getJobKey(), status.getId());
            return status;
        }

        final Class<? extends AsyncJob> jobClass = getJobClass(status.getJobKey());

        // Maybe in this case it'd be better to attempt to use the job key as the job class
        // rather than failing directly. This would allow use of aliases and explicit job
        // classes.
        if (jobClass == null) {
            String errmsg = String.format("No registered job class for job: %s", status.getJobKey());

            this.updateJobStatus(status, JobState.FAILED, errmsg);

            log.error(errmsg);
            throw new JobInitializationException(errmsg, true);
        }

        try {
            this.setupJobRuntimeEnvironment(status);

            EventSink eventSink = injector.getInstance(EventSink.class);
            AsyncJob job = injector.getInstance(jobClass);

            if (job == null) {
                String errmsg = String.format("Unable to instantiate job class \"{}\" for job: {}",
                    jobClass.getName(), status.getJobKey());

                log.error(errmsg);
                throw new JobInitializationException(errmsg);
            }

            status.setExecutor(Util.getHostname());
            status.incrementAttempts();
            status.setStartTime(new Date());
            status.setEndTime(null);
            status = this.updateJobStatus(status, JobState.RUNNING, null);

            // Impl note: We need to be sure we do not have a transaction open at this point
            EntityTransaction transaction = this.jobCurator.getTransaction();
            if (transaction != null && transaction.isActive()) {
                throw new IllegalStateException("A remnant transaction is open before executing job");
            }

            if (status.logExecutionDetails()) {
                log.info("Starting job \"{}\" using class: {}", status.getName(), jobClass.getName());
            }

            Object result = null;

            try {
                result = job.execute(status);

                // If a transaction was left open, we should scream about it. Note that this will
                // cause the job to fail if the session cannot be terminated cleanly.
                this.checkPostJobExecutionTransactionStatus(status);
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
            status = this.updateJobStatus(status, JobState.FINISHED, result);

            if (status.logExecutionDetails()) {
                log.info("Job \"{}\" completed in {}ms", status.getName(), this.getJobRuntime(status));
            }

            return status;
        }
        finally {
            this.teardownJobRuntimeEnvironment();
        }
    }

    /**
     * Configures the job's runtime environment, performing the following operations:
     *
     *  - Entering the new injection scope (CandlepinRequestScope)
     *  - Setting up the logging level
     *  - Injecting the job's metadata to the logging backend
     *  - Setting up the principal to use during job runtime
     *
     * @param status
     *  the job status to use to configure the runtime environment
     */
    private void setupJobRuntimeEnvironment(AsyncJobStatus status) {
        // Enter custom scope
        this.candlepinRequestScope.enter();

        // Save MDC state
        this.mdcState.set(MDC.getCopyOfContextMap());

        MDC.put(MDC_REQUEST_TYPE_KEY, "job");
        MDC.put(MDC_REQUEST_UUID_KEY, status.getId());

        // Inject all of our metadata...
        for (Map.Entry<String, String> entry : status.getMetadata().entrySet()) {
            MDC.put(entry.getKey(), entry.getValue());
        }

        // Attempt to lookup the owner
        String ownerId = status.getContextOwnerId();
        String ownerKey = null;
        String ownerLogLevel = null;

        if (ownerId != null && !ownerId.isEmpty()) {
            Owner contextOwner = this.ownerCurator.get(ownerId);

            if (contextOwner != null) {
                ownerKey = contextOwner.getKey();
                ownerLogLevel = contextOwner.getLogLevel();
            }
            else {
                log.warn("Owner ID specified for job does not exist: {}", ownerId);
                ownerKey = UNKNOWN_OWNER_KEY;
            }
        }

        // If we have an owner, override whatever metadata we've set with the actual owner's key.
        if (ownerKey != null) {
            MDC.put(LoggingFilter.OWNER_KEY, ownerKey);
        }

        // Set our logging level according to the following:
        // - If the job has an explicit log level set, use that
        // - If the job is running in the context of an owner and the owner has a log level set, use that
        String jobLogLevel = status.getLogLevel();

        if (jobLogLevel != null && !jobLogLevel.isEmpty()) {
            MDC.put(MDC_LOG_LEVEL_KEY, jobLogLevel);
        }
        else if (ownerLogLevel != null && !ownerLogLevel.isEmpty()) {
            MDC.put(MDC_LOG_LEVEL_KEY, ownerLogLevel);
        }

        // Setup and inject the principal
        String name = status.getPrincipalName();
        Principal principal = name != null ? new JobPrincipal(name) : new SystemPrincipal();

        ResteasyContext.pushContext(Principal.class, principal);
    }

    /**
     * Tears down the job's runtime environment, performing the following operations:
     *
     *  - Removing the job's context principal from the environment
     *  - Leaving the injection scope (CandlepinRequestScope)
     */
    private void teardownJobRuntimeEnvironment() {
        // Pop principal info
        ResteasyContext.popContextData(Principal.class);

        // Restore original MDC state
        Map<String, String> state = this.mdcState.get();
        if (state != null) {
            MDC.setContextMap(state);
        }
        else {
            MDC.clear();
        }

        // Leave scope
        this.candlepinRequestScope.exit();
    }

    /**
     * Checks that the job did not leave an active session open after completing its normal
     * execution. If a session was left open, this method attempts to commit it, or roll it back
     * if it is set to rollback only. If the session cannot be cleaned up, this method throws a
     * PersistenceException.
     *
     * @param status
     *  The AsyncJobStatus instance for the job being executed
     *
     * @throws PersistenceException
     *  if an unexpected exception occurs while terminating the session
     */
    private void checkPostJobExecutionTransactionStatus(AsyncJobStatus status) throws PersistenceException {
        try {
            EntityTransaction transaction = this.jobCurator.getTransaction();
            if (transaction != null && transaction.isActive()) {
                if (!transaction.getRollbackOnly()) {
                    log.warn("Job \"{}\" terminated with an open session; committing session...",
                        status.getName());

                    transaction.commit();
                }
                else {
                    log.warn("Job \"{}\" terminated with an open, rollback-only session; " +
                        "rolling back session...", status.getName());

                    transaction.rollback();
                }
            }
        }
        catch (PersistenceException e) {
            log.error("Unable to cleanup remnant job session; post-job database state is now undefined!");
            throw e;
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
    private AsyncJobStatus fetchJobStatus(JobMessage message)
        throws JobInitializationException {

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

        // Sanity check: make sure we don't try to execute a job that's disabled on this node
        // This shouldn't happen, as we should be properly filtering which messages we pull off the
        // message queue, but if that fails, we'll fall back to failing non-terminally.
        if (status.getJobKey() == null || !this.isJobEnabled(status.getJobKey())) {
            String errmsg = String.format("Job \"%s\" (%s) is not enabled on this node",
                status.getId(), status.getJobKey());

            log.error(errmsg);
            throw new JobInitializationException(errmsg, false);
        }

        JobState jobState = status.getState();

        // The "CANCELED" state is a special case we'll handle semi-silently in the execute method,
        // as it's not an error to cancel a QUEUED job, and we still want to get through the message
        // normally.
        if (jobState == null || (!JobState.CANCELED.equals(jobState) && jobState.isTerminal())) {
            String errmsg = String.format("Job \"%s\" (%s) is in an unknown or terminal state: %s",
                status.getId(), status.getJobKey(), status.getState());

            log.error(errmsg);
            throw new JobInitializationException(errmsg, true);
        }
        else if (jobState != JobState.QUEUED) {
            // Warn if we're about to execute a job that's not queued for execution (this is likely
            // just state recovery and is probably okay).
            log.warn("Job \"{}\" ({}) is in an unexpected, non-terminal state: {}", status.getId(),
                status.getJobKey(), status.getState());

            log.warn("Recovering state and executing...");
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
    @Transactional
    protected AsyncJobStatus updateJobStatus(AsyncJobStatus status, JobState state, Object result)
        throws JobStateManagementException {

        // Impl note:
        // Due to some Hibernate shenanigans, this method should only be called within the context
        // of a database transaction. If called without a transaction, the update may be lost.

        JobState initState = status.getState();

        try {
            this.setJobState(status, state);
            status.setJobResult(result);

            status = this.jobCurator.merge(status);

            return status;
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

        return (start != null) ? end.getTime() - start.getTime() : -1;
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

        // Rollback the transaction if one is still open
        EntityTransaction transaction = this.jobCurator.getTransaction();
        if (transaction != null && transaction.isActive()) {
            transaction.rollback();
        }

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
     * Cancels the job associated with the specified job ID. If no such job status could be found,
     * this method returns null.
     *
     * @param jobId
     *  the ID of the job to cancel
     *
     * @throws IllegalStateException
     *  if the job is already in a terminal state
     *
     * @return
     *  the updated job status associated with the canceled job, or null if no such job status
     *  could be found
     */
    @Transactional
    public synchronized AsyncJobStatus cancelJob(String jobId) {
        if (jobId == null || jobId.isEmpty()) {
            throw new IllegalArgumentException("jobId is null or empty");
        }

        AsyncJobStatus status = this.jobCurator.get(jobId);
        if (status != null) {
            if (status.getState().isTerminal()) {
                throw new IllegalStateException("job is already in a terminal state: " + status);
            }

            if (status.getState() != JobState.RUNNING) {
                this.setJobState(status, JobState.CANCELED);
            }
            else {
                // Impl note: With the locking, we probably shouldn't cancel a job that's in a
                // running state, since the state change is almost guaranteed to get clobbered. For
                // now, we'll just make sure it's not set to retry if it fails; ensuring the current
                // run is the last run.
                log.warn("Attempting to cancel job that's already running: {}", status);

                if (status.getMaxAttempts() > 1) {
                    log.warn("Setting max attempts to: 1");
                    status.setMaxAttempts(1);
                }

                // TODO: Also set retry behavior to default to false here if that's added in
                // the future
            }

            status = this.jobCurator.merge(status);
        }

        return status;
    }

    /**
     * Cleans up all jobs in the given terminal states within the date range provided. If no states
     * are provided, this method defaults to all terminal states. If non-terminal states are
     * provided, they will be ignored.
     *
     * @param queryBuilder
     *  an AsyncJobStatusQueryBuilder instance containing the various arguments or filters to use
     *  to select jobs
     *
     * @return
     *  the number of jobs deleted as a result of this operation
     */
    @Transactional
    public synchronized int cleanupJobs(AsyncJobStatusCurator.AsyncJobStatusQueryBuilder queryBuilder) {
        // Prepare for the defaults...
        if (queryBuilder == null) {
            queryBuilder = new AsyncJobStatusCurator.AsyncJobStatusQueryBuilder();
        }

        Collection<JobState> states = queryBuilder.getJobStates();
        if (states != null && !states.isEmpty()) {
            // Make sure we don't attempt to blast some non-terminal jobs.
            states = states.stream()
                .filter(state -> state != null && state.isTerminal())
                .collect(Collectors.toSet());
        }
        else {
            // Set the default: all terminal states
            states = Arrays.stream(JobState.values())
                .filter(state -> state.isTerminal())
                .collect(Collectors.toSet());
        }

        queryBuilder.setJobStates(states);

        // TODO: any other sanity restrictions deemed necessary

        return this.jobCurator.deleteJobs(queryBuilder);
    }

    /**
     * @{inheritDoc}
     */
    @Override
    public void handleModeChange(CandlepinModeManager modeManager, Mode previousMode, Mode currentMode) {
        if (currentMode != null) {
            switch (currentMode) {
                case SUSPEND:
                    this.suspend(SUSPEND_KEY_TRIGGERED);
                    break;

                case NORMAL:
                    this.resume(SUSPEND_KEY_TRIGGERED);
                    break;

                default:
                    log.warn("Received an unexpected mode change notice: {}", currentMode);
            }
        }
    }

}
