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
package org.candlepin.model;

import org.candlepin.async.JobArguments;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.hibernate.AbstractJsonConverter;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import org.hibernate.annotations.GenericGenerator;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Converter;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;



/**
 * Represents the current status for a long-running job.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = AsyncJobStatus.DB_TABLE)
public class AsyncJobStatus extends AbstractHibernateObject implements JobExecutionContext {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_async_jobs";

    /** Enum of job states; terminal states represent states at which the job will no longer change */
    public enum JobState {
        /** The job has been created, but not yet queued or executed */
        CREATED("WAITING", "SCHEDULED", "QUEUED", "RUNNING", "CANCELED", "ABORTED"),
        /** The job is blocked by a collision or inability to queue the job message */
        WAITING("SCHEDULED", "QUEUED", "RUNNING", "CANCELED", "ABORTED"),
        /** The job has been scheduled to run at some time in the future */
        SCHEDULED("QUEUED", "RUNNING", "CANCELED", "ABORTED"),
        /** The job has been sent to the backing job messaging/queueing system to be picked up */
        QUEUED("RUNNING", "CANCELED"),
        /** The job has been picked up and is currently being executed */
        RUNNING("FAILED", "FAILED_WITH_RETRY", "FINISHED", "CANCELED"),
        /** The job failed during execution, and has been rescheduled to be retried */
        FAILED_WITH_RETRY("SCHEDULED", "QUEUED", "RUNNING", "CANCELED"),
        /** The job has completed successfully */
        FINISHED(),
        /** The job failed during execution in a way that does not allow retries */
        FAILED(),
        /** The job was canceled by request */
        CANCELED(),
        /** The job was aborted due to an inability to schedule or queue the job */
        ABORTED();

        private final String[] transitions;

        JobState(String... transitions) {
            this.transitions = transitions != null && transitions.length > 0 ? transitions : null;
        }

        public boolean isValidTransition(JobState state) {
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
     * A simple container object to collect all of the data to be stored in as a serialized field
     */
    @SuppressWarnings("checkstyle:VisibilityModifier")
    private static class SerializedJobData {
        public Map<String, String> metadata;
        public JobArguments arguments;
        public Object result;

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (!(obj instanceof SerializedJobData)) {
                return false;
            }

            SerializedJobData that = (SerializedJobData) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.metadata, that.metadata)
                .append(this.arguments, that.arguments)
                .append(this.result, that.result);

            return builder.isEquals();
        }

        @Override
        public int hashCode() {
            HashCodeBuilder builder = new HashCodeBuilder(37, 7)
                .append(this.metadata)
                .append(this.arguments)
                .append(this.result);

            return builder.toHashCode();
        }
    }

    /**
     * JSON converter class for the SerializedJobData type.
     */
    @Converter
    public static class JobDataJsonConverter extends AbstractJsonConverter<SerializedJobData> {
        public JobDataJsonConverter() {
            super(SerializedJobData.class);
        }
    }


    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @NotNull
    private String id;

    @Column(name = "job_key")
    private String jobKey;

    @Column(name = "job_group")
    private String group;

    @NotNull
    private String name;
    private String origin;
    private String executor;
    private String principal;

    @Column(name = "owner_id")
    private String ownerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", insertable = false, updatable = false)
    private Owner owner;

    @Column(name = "log_level")
    private String logLevel;
    @Column(name = "log_execution_details")
    private boolean logExecutionDetails;

    // @ElementCollection(fetch = FetchType.EAGER)
    // @CollectionTable(name = "cp_async_job_metadata", joinColumns = @JoinColumn(name = "job_id"))
    // @MapKeyColumn(name = "\"key\"")
    // @Column(name = "\"value\"")
    // private Map<String, String> metadata;

    @NotNull
    private JobState state;

    @Column(name = "previous_state")
    private JobState previousState;

    // @ElementCollection(fetch = FetchType.LAZY)
    // @CollectionTable(name = "cp_async_job_constraints", joinColumns = @JoinColumn(name = "job_id"))
    // @MapKeyColumn(name = "key")
    // @Column(name = "value")
    // private Map<String, String> constraints;

    private int attempts;
    @Column(name = "max_attempts")
    private int maxAttempts;

    @Column(name = "start_time")
    private Date startTime;
    @Column(name = "end_time")
    private Date endTime;

    @Column(name = "job_data")
    @Convert(converter = JobDataJsonConverter.class)
    private SerializedJobData jobData;



    /**
     * Creates a new AsyncJobStatus instance with no configuration
     */
    public AsyncJobStatus() {
        // this.constraints = new HashMap<>();
        this.state = JobState.CREATED;

        this.attempts = 0;
        this.maxAttempts = 1;

        this.logExecutionDetails = true;

        this.jobData = new SerializedJobData();
    }

    /**
     * Fetches the ID of this job status instance. If the ID has not yet been set, this method
     * returns null.
     *
     * @return
     *  the current ID of this job status, or null if the ID has not yet been set
     */
    public String getId() {
        return this.id;
    }

    // /**
    //  * Sets the ID of this job status instance. The ID cannot be null or empty, and cannot be set
    //  * if an ID has already been provided.
    //  *
    //  * @param id
    //  *  the ID to set for this job status
    //  *
    //  * @throws IllegalArgumentException
    //  *  if id is null or empty
    //  *
    //  * @throws IllegalStateException
    //  *  if the ID has already been set
    //  *
    //  * @return
    //  *  this job status instance
    //  */
    // public AsyncJobStatus setId(String id) {
    //     if (id == null || id.matches("^\\s*$")) {
    //         throw new IllegalArgumentException("id is null or empty");
    //     }

    //     if (this.id != null) {
    //         throw new IllegalStateException("id is already set");
    //     }

    //     this.id = id;
    //     return this;
    // }

    /**
     * Fetches the fully-qualified class name for this job. If the job class has not yet been set,
     * this method returns null.
     *
     * @return
     *  the fully-qualified class name of this job, or null if the class has not yet been set
     */
    public String getJobKey() {
        return this.jobKey;
    }

    /**
     * Sets the key for the job class to handle execution of this job. If the key does represent a
     * valid job class at the time of scheduling or execution, the job will permanently enter the
     * FAILED state.
     *
     * @param jobKey
     *  The key representing the job class to handle execution of this job
     *
     * @throws IllegalArgumentException
     *  if jobKey is null or empty
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus setJobKey(String jobKey) {
        if (jobKey == null || jobKey.isEmpty()) {
            throw new IllegalArgumentException("jobKey is null or empty");
        }

        this.jobKey = jobKey;

        if (this.getName() == null) {
            this.setName(jobKey);
        }

        return this;
    }

    /**
     * Fetches the name of this job status. If the job has not yet been given a name, this method
     * returns null.
     *
     * @return
     *  The name for this job status, or null if the name has not been set
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name for this job. Once set, the name cannot be cleared.
     *
     * @param name
     *  The name to set for this job status
     *
     * @throws IllegalArgumentException
     *  if name is null or empty
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus setName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("job name is null or empty");
        }

        this.name = name;

        return this;
    }

    /**
     * Fetches the group for this job status. If the job is not part of a group or the group has
     * not yet been set, this method returns null.
     *
     * @return
     *  The group for this job status, or null if the group has not been set
     */
    public String getGroup() {
        return this.group;
    }

    /**
     * Sets the group for this job. If the group is null or empty, any existing group will be
     * cleared.
     *
     * @param group
     *  The group to set for this job status, or null to clear it
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus setGroup(String group) {
        this.group = (group != null && !group.isEmpty()) ? group : null;
        return this;
    }

    /**
     * Fetches the origin of this job status. If the origin has not yet been set, this method
     * returns null.
     *
     * @return
     *  The origin of this job status, or null if the origin has not been set
     */
    public String getOrigin() {
        return this.origin;
    }

    /**
     * Sets the origin of this job. If the origin is null or empty, any existing origin will be
     * cleared.
     *
     * @param origin
     *  The origin to set of this job status, or null to clear it
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus setOrigin(String origin) {
        this.origin = (origin != null && !origin.isEmpty()) ? origin : null;
        return this;
    }

    /**
     * Fetches the executor of this job. If the job has not yet been run, or the executor has
     * otherwise not been set, this method returns null.
     *
     * @return
     *  the executor of this job, or null if the executor has not been set
     */
    public String getExecutor() {
        return this.executor;
    }

    /**
     * Sets the executor of this job. If the executor is null or empty, any existing executor will
     * be cleared.
     *
     * @param executor
     *  The executor to set to this job status, or null to clear it
     *
     * @return this job status instance
     */
    public AsyncJobStatus setExecutor(final String executor) {
        this.executor = (executor != null && !executor.isEmpty()) ? executor : null;
        return this;
    }

    /**
     * Fetches the name of the principal that created this job status. If the job is a system-level
     * job, or the principal has not yet been set, this method returns null.
     *
     * @return
     *  The name of the principal that created this job, or null if the job is a system-level job
     *  or the principal has not been set
     */
    public String getPrincipalName() {
        return this.principal;
    }

    /**
     * Sets the name of the principal that created this job. If the principal is null or empty, any
     * existing principal will be cleared.
     *
     * @param principal
     *  The name of the principal to set for this job status, or null to clear it
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus setPrincipalName(String principal) {
        this.principal = (principal != null && !principal.isEmpty()) ? principal : null;
        return this;
    }

    /**
     * Sets the owner for this job's context. The context owner will be used for the following
     * purposes if set:
     *
     *  - owner-specific job queries/lookups
     *  - setting the log level if an explicit log level is not set and one is present at the org
     *    level
     *  - setting the owner metadata if present; overriding any value set for the owner metadata
     *    key if applicable
     *
     * Note that the context owner is not available via job arguments unless it is also explicitly
     * set as an argument.
     *
     * @param owner
     *  the owner to set for this job's context
     *
     * @throws IllegalArgumentException
     *  if owner is not null and does not have an ID
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus setContextOwner(Owner owner) {
        if (owner != null && owner.getId() == null) {
            throw new IllegalArgumentException("owner is null and lacks an ID");
        }

        if (owner != null) {
            this.owner = owner;
            this.ownerId = owner.getId();
        }
        else {
            this.owner = null;
            this.ownerId = null;
        }

        return this;
    }

    /**
     * Fetches the ID for this job's context owner. If this job is not run in the context of an
     * owner, this method returns null.
     *
     * @return
     *  the ID of this job's context owner, or null if this job is not run in the context of a
     *  specific owner
     */
    public String getContextOwnerId() {
        return this.ownerId;
    }

    /**
     * Fetches the context owner for this job. If this job is not run in the context of an owner,
     * this method returns null. This method may perform a lazy lookup of the owner, and should
     * generally be avoided in situations where the owner ID is sufficient.
     *
     * @return
     *  this job's context owner, or null if this job is not run in the context of a specific owner
     */
    public Owner getContextOwner() {
        return this.owner;
    }

    /**
     * Fetches the log level with which this job will be executed. If the log level has not been
     * set, this method returns null.
     *
     * @return
     *  the log level for this job, or null if the log level has not been set
     */
    public String getLogLevel() {
        return this.logLevel;
    }

    /**
     * Sets the log level with which this job will be executed. If the log level is null or empty,
     * any existing log level will be cleared.
     *
     * @param logLevel
     *  the log level to set for this job, or null to clear it
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus setLogLevel(String logLevel) {
        this.logLevel = logLevel != null && !logLevel.isEmpty() ? logLevel : null;
        return this;
    }

    /**
     * Fetches whether or not the execution details, such as job initialization and total runtime
     * upon successful completion, should be logged for this job. Defaults to true.
     *
     * @return
     *  true if the execution details for this job should be logged; false otherwise
     */
    public boolean logExecutionDetails() {
        return this.logExecutionDetails;
    }

    /**
     * Sets whether or not the execution details, such as job initialization and total runtime
     * upon successful completion, should be logged for this job.
     *
     * @param enabled
     *  true to enable logging of execution details; false to disable it
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus logExecutionDetails(boolean enabled) {
        this.logExecutionDetails = enabled;
        return this;
    }

    /**
     * Fetches the metadata for this job. If this job does not have any metadata, this method
     * returns an empty map.
     *
     * @return
     *  the job's metadata as a map
     */
    public Map<String, String> getMetadata() {
        return this.jobData.metadata != null ?
            Collections.unmodifiableMap(this.jobData.metadata) :
            Collections.emptyMap();
    }

    /**
     * Sets the metadata for this job. If the metadata is null or empty, any existing metadata will
     * be cleared.
     *
     * @param metadata
     *  The metadata to assign to this job
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus setMetadata(Map<String, String> metadata) {
        this.jobData.metadata = metadata != null ? new HashMap<>(metadata) : null;
        return this;
    }

    /**
     * Adds or updates a metadata entry for this job.
     *
     * @param key
     *  The key for the metadata entry
     *
     * @param value
     *  The value of the metadata entry
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus addMetadata(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        if (this.jobData.metadata == null) {
            this.jobData.metadata = new HashMap<>();
        }

        this.jobData.metadata.put(key, value);
        return this;
    }

    /**
     * Fetches the current state of this job.
     *
     * @return
     *  The current state of this job
     */
    public JobState getState() {
        return this.state;
    }

    /**
     * Fetches the previous state of this job. If the job state has not yet been updated, this
     * method returns null.
     *
     * @return
     *  the previous state of this job
     */
    public JobState getPreviousState() {
        return this.previousState;
    }

    /**
     * Sets the name of the state that created this job. If the state is null or empty, any
     * existing state will be cleared.
     *
     * @param state
     *  The state to set for this job status
     *
     * @throws IllegalArgumentException
     *  if state is null
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus setState(JobState state) {
        if (state == null) {
            throw new IllegalArgumentException("job state is null");
        }

        if (state != this.state) {
            this.previousState = this.state;
        }

        this.state = state;
        return this;
    }

    /**
     * Fetches the number of times this job has been run. If the job has not yet been run, this
     * method returns zero.
     *
     * @return
     *  the number of times this job has been run
     */
    public int getAttempts() {
        return this.attempts;
    }

    /**
     * Increments the number of times this job has attempted to run to completion.
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus incrementAttempts() {
        ++this.attempts;
        return this;
    }

    /**
     * Fetches the maximum number of times this job will attempt to run before failing terminally.
     *
     * @return
     *  the maximum number of times this job will attempt to run
     */
    public int getMaxAttempts() {
        return this.maxAttempts;
    }

    /**
     * Sets the maximum number of times this job will attempt to run before failing terminally.
     * Changing this value after a job has reached a terminal state will have no effect. Values
     * less than one will be treated as one.
     *
     * @param maxAttempts
     *  the maximum attempts
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : 1;
        return this;
    }

    /**
     * Fetches the start time of the most recent run attempt of this job. If this job has not yet
     * been attempted, this method returns null.
     *
     * @return
     *  the start time of the most recent run attempt of this job, or null if the job has not yet
     *  been attempted
     */
    public Date getStartTime() {
        return this.startTime;
    }

    /**
     * Sets or clears the start time of this job's most recent run attempt. If the start time is
     * null, any existing start time will be cleared.
     *
     * @param startTime
     *  The startTime to set as the start time of the most recent run for this job, or null to
     *  clear the start time
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus setStartTime(Date startTime) {
        this.startTime = startTime;
        return this;
    }

    /**
     * Fetches the end time of the most recent run attempt of this job. The end time will represent
     * the time the job returned control to the job executor, either through successful completion
     * or failure. If this job has not yet been attempted, or it is currently in progress, this
     * method returns null.
     *
     * @return
     *  the end time of the most recent run attempt of this job, or null if the job has not yet
     *  been attempted or the first attempt has not yet ended
     */
    public Date getEndTime() {
        return this.endTime;
    }

    /**
     * Sets or clears the end time of this job's most recent run attempt. If the end time is
     * null, any existing end time will be cleared.
     *
     * @param endTime
     *  The endTime to set as the end time of the most recent run for this job, or null to
     *  clear the end time
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus setEndTime(Date endTime) {
        this.endTime = endTime;
        return this;
    }

    /**
     * Fetches the runtime arguments for this job
     *
     * @return
     *  the runtime arguments for this job
     */
    public JobArguments getJobArguments() {
        return this.jobData.arguments;
    }

    /**
     * Sets the arguments this job will receive at runtime
     *
     * @param arguments
     *  The arguments to provide to the job at runtime
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus setJobArguments(JobArguments arguments) {
        this.jobData.arguments = arguments;
        return this;
    }

    /**
     * Fetches the result from the job's most recent execution. If the job has not yet been run,
     * or the job does not produce any output, this method returns null.
     *
     * @return
     *  the output/result of the job's most recent execution, or null if no result is available
     */
    public Object getJobResult() {
        return this.jobData.result;
    }

    /**
     * Sets the result of the last execution of this job.
     *
     * @param result
     *  The output from the job
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus setJobResult(Object result) {
        this.jobData.result = result;
        return this;
    }

    /**
     * @{inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("AsyncJobStatus [id: %s, name: %s, key: %s, state: %s]",
            this.getId(), this.getName(), this.getJobKey(), this.getState().name());
    }
}
