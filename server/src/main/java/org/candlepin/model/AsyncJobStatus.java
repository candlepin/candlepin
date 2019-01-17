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

import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobDataMap;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
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
    public static final String DB_TABLE = "cp2_async_jobs";

    /** Enum of job states; terminal states represent states at which the job will no longer change */
    public enum JobState {
        /** The job has been created, but not yet queued or executed */
        CREATED(false),
        /** The job has been sent to the backing job messaging/queueing system to be picked up */
        QUEUED(false),
        /** The job has been picked up and is currently being executed */
        RUNNING(false),
        /** The job failed during execution, and has been rescheduled to be retried */
        FAILED_WITH_RETRY(false),
        /** The job has completed successfully */
        COMPLETED(true),
        /** The job failed during execution in a way that does not allow retries */
        FAILED(true),
        /** The job was canceled by request */
        CANCELED(true);

        private final boolean terminal;

        JobState(boolean terminal) {
            this.terminal = terminal;
        }

        public boolean isTerminal() {
            return this.terminal;
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
    private String principal;

    // TODO: If we add more stuff like this field, we should create another table to store
    // job metadata rather than continuing to bolt more columns onto this for fields that may
    // not be filled/configured globally (i.e. announce completion and job log level)
    @Column(name = "correlation_id")
    private String correlationId;

    private JobState state;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "cp2_async_job_constraints", joinColumns = @JoinColumn(name = "job_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    private Map<String, String> constraints;

    private int attempts;
    @Column(name = "max_attempts")
    private int maxAttempts;

    @Column(name = "start_time")
    private Date startTime;
    @Column(name = "end_time")
    private Date endTime;

    @Column(name = "job_data")
    @Type(type = "org.candlepin.hibernate.JsonSerializedDataType")
    private Object jobData;

    @Column(name = "job_result")
    @Type(type = "org.candlepin.hibernate.JsonSerializedDataType")
    private Object jobResult;


    /**
     * Creates a new AsyncJobStatus instance with no configuration
     */
    public AsyncJobStatus() {
        this.constraints = new HashMap<>();
        this.state = JobState.CREATED;
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
     * Fetches the name of the principal that created this job status. If the job is a system-level
     * job, or the principal has not yet been set, this method returns null.
     *
     * @return
     *  The name of the principal that created this job, or null if the job is a system-level job
     *  or the principal has not been set
     */
    public String getPrincipal() {
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
    public AsyncJobStatus setPrincipal(String principal) {
        this.principal = (principal != null && !principal.isEmpty()) ? principal : null;
        return this;
    }

    /**
     * Fetches the correlation ID of this job status. If the correlation ID has not yet been set,
     * this method returns null.
     *
     * @return
     *  The correlation ID of this job status, or null if the correlation ID has not been set
     */
    public String getCorrelationId() {
        return this.correlationId;
    }

    /**
     * Sets the correlation ID of this job. If the correlation ID is null or empty, any existing ID
     * will be cleared.
     *
     * @param correlationId
     *  The correlation ID to set of this job status, or null to clear it
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus setCorrelationId(String correlationId) {
        this.correlationId = (correlationId != null && !correlationId.isEmpty()) ? correlationId : null;
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
     * or failure. If this job has not yet been attempted, or its first attempt is currently in
     * progress, this method returns null.
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
     * Fetches the job data for this job. The map returned by this method is immutable. If the
     * job does not contain any job data, this method returns an empty map.
     *
     * @return
     *  the job's runtime data as a map
     */
    public JobDataMap getJobData() {
        return new JobDataMap(this.jobData != null ?
            Collections.unmodifiableMap((Map<String, Object>) this.jobData) :
            Collections.emptyMap());
    }

    /**
     * Sets the job data for this job.
     *
     * @param jobData
     *  The data to provide to the job during runtime
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus setJobData(Map<String, Object> jobData) {
        this.jobData = jobData;
        return this;
    }

    /**
     * Fetches the result from the job's most recent run attempt. If the job has not yet been run,
     * or the job does not produce any output, this method returns null.
     *
     * @return
     *  the output of this job's most recent run attempt, or null if the job has not yet been run
     */
    public Object getJobResult() {
        return this.jobResult;
    }

    /**
     * Sets the job data for this job.
     *
     * @param resultData
     *  The output from the job
     *
     * @return
     *  this job status instance
     */
    public AsyncJobStatus setJobResult(Object resultData) {
        this.jobResult = resultData;
        return this;
    }

}
