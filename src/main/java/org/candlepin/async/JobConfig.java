/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.model.Owner;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * The JobConfig object collects the configuration for a job, which can be provided to the
 * JobManager to queue a new instance of that job.
 * <p></p>
 * This class allows for a generic type definition, which subclasses should use to specify
 * the exact subclass or subclass type represents this class. This function is purely to
 * facilitate method chaining with mutators that return a self reference.
 *
 * For example, if we have a JobConfig subclass named TestJobConfig, it should be declared as
 * follows:
 *
 * {@code
 *      public TestJobConfig extends JobConfig<TestJobConfig>
 * }
 *
 * While redundant, this signals the specific JobConfig subclass to return by mutators implemented
 * by the base JobConfig class. Similarly, if the TestJobConfig was designed such that it is
 * intended to be further subclassed, it should be declared as follows:
 * {@code
 *      public [abstract] TestJobConfig<T extends TestJobConfig> extends JobConfig<T>
 * }
 *
 * Mutators implemented in TestJobConfig then would have declarations as such:
 * {@code
 *      public T setSomeValue(String value) {
 *          this.value = value; // Assign the value
 *
 *          return (T) this;
 *      }
 * }
 *
 * Subclasses would then follow one of these patterns as necessary according to their design and
 * intent.
 *
 * @param <T>
 *  the concrete JobConfig class being implemented
 */
public class JobConfig<T extends JobConfig> {

    private String key;
    private String name;
    private String group;
    private Owner owner;
    private Map<String, String> arguments;
    private Set<JobConstraint> constraints;
    private int retries;
    private String logLevel;
    private boolean logExecutionDetails;

    /**
     * Creates an empty JobConfig
     */
    public JobConfig() {
        this.arguments = new HashMap<>();
        this.constraints = new HashSet<>();

        this.retries = 0;
        this.logExecutionDetails = true;
    }

    /**
     * Creates a new JobConfig for the specified asyncronous job task.
     *
     * @param jobKey
     *  The key representing the job class to handle execution of this job
     *
     * @return
     *  a new JobConfig instance for the specified job class
     */
    public static JobConfig forJob(String jobKey) {
        JobConfig config = new JobConfig();

        return config.setJobKey(jobKey);
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
     *  this JobConfig instance
     */
    public T setJobKey(String jobKey) {
        if (jobKey == null || jobKey.isEmpty()) {
            throw new IllegalArgumentException("jobKey is null or empty");
        }

        this.key = jobKey;
        return (T) this;
    }

    /**
     * Fetches the job key set for this job
     *
     * @return
     *  the key representing the job class to handle execution of this job, or null if the job
     *  class has not yet been set
     */
    public String getJobKey() {
        return this.key;
    }

    /**
     * Sets the name of this job.
     *
     * @param jobName
     *  The name to set for this job
     *
     * @throws IllegalArgumentException
     *  if the job name is null or empty
     *
     * @return
     *  this JobConfig instance
     */
    public T setJobName(String jobName) {
        if (jobName == null || jobName.isEmpty()) {
            throw new IllegalArgumentException("jobName is null or empty");
        }

        this.name = jobName;
        return (T) this;
    }

    /**
     * Fetches the name of this job. If the job name has not yet been set, this method returns
     * null.
     *
     * @return
     *  The name of this job, or null if the name has not been set
     */
    public String getJobName() {
        return this.name;
    }

    /**
     * Sets the group name for this job.
     *
     * @param jobGroup
     *  The name of the group to set for this job
     *
     * @return
     *  this JobConfig instance
     */
    public T setJobGroup(String jobGroup) {
        this.group = jobGroup != null && !jobGroup.isEmpty() ? jobGroup : null;
        return (T) this;
    }

    /**
     * Fetches the group for this job. If the job group has not yet been set, this method returns
     * null.
     *
     * @return
     *  The group for this job, or null if the group has not been set
     */
    public String getJobGroup() {
        return this.group;
    }

    /**
     * Sets the owner for the context of this job. The context owner will be used for the following
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
     * @return
     *  this JobConfig instance
     */
    public T setContextOwner(Owner owner) {
        this.owner = owner;
        return (T) this;
    }

    /**
     * Alias of setContextOwner which may be overridden to by subclasses to set the owner in both
     * the job context and job arguments.
     *
     * @param owner
     *  the owner to set for this job's context
     *
     * @return
     *  this JobConfig instance
     */
    public T setOwner(Owner owner) {
        return this.setContextOwner(owner);
    }

    /**
     * Fetches the context owner for this job. If this job is not run in the context of an owner,
     * this method returns null.
     *
     * @return
     *  this job's context owner, or null if this job is not run in the context of a specific owner
     */
    public Owner getContextOwner() {
        return this.owner;
    }

    /**
     * Sets the value of an argument to pass into the job at the time of execution. As the value
     * may be serialized and deserialized before the job is executed, it is best to avoid setting
     * any values which are sensitive to transient data or have large trees of data.
     *
     * @param arg
     *  The name of the argument to pass to the job at the time of execution
     *
     * @param value
     *  The value of the argument
     *
     * @return
     *  this JobConfig instance
     */
    public T setJobArgument(String arg, Object value) {
        if (arg == null) {
            throw new IllegalArgumentException("arg is null");
        }

        this.arguments.put(arg, JobArguments.serialize(value));
        return (T) this;
    }

    /**
     * Fetches the arguments set for this job. If no arguments have been set for this job,
     * this method returns an empty map. Note that the map returned by this method cannot be
     * modified directly, though changes made to the arguments by this config will be reflected
     * in the returned map.
     *
     * @return
     *  a map of arguments to pass to the job at execution time
     */
    public JobArguments getJobArguments() {
        return new JobArguments(this.arguments != null ? this.arguments : Collections.emptyMap());
    }

    /**
     * Adds a queuing constraint to this job config. If the constraint has already been added, it
     * will not be added again.
     *
     * @param constraint
     *  the queuing constraint to add to this job
     *
     * @return
     *  this JobConfig instance
     */
    public T addConstraint(JobConstraint constraint) {
        if (constraint == null) {
            throw new IllegalArgumentException("constraint is null");
        }

        this.constraints.add(constraint);
        return (T) this;
    }

    /**
     * Fetches the collection of queuing constraints to apply to this job. If this job does not have
     * any constraints, this method returns an empty collection.
     *
     * @return
     *  a collection of queuing constraints to apply to this job
     */
    public Collection<JobConstraint> getConstraints() {
        return Collections.unmodifiableSet(this.constraints);
    }

    /**
     * Sets the number of times this job will be retried if it fails to complete normally. Values
     * lower than 1 indicate the job will not be retried at all on failure.
     *
     * @param count
     *  The number of times the job should be retried on failure
     *
     * @return
     *  this JobConfig instance
     */
    public T setRetryCount(int count) {
        this.retries = Math.max(0, count);
        return (T) this;
    }

    /**
     * Fetches the number of times this job should be retried if it fails to complete successfully.
     *
     * @return
     *  The number of times this job should be retried, or zero if the job should not be retried
     */
    public int getRetryCount() {
        return this.retries;
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
     *  this JobConfig instance
     */
    public T setLogLevel(String logLevel) {
        this.logLevel = logLevel != null && !logLevel.isEmpty() ? logLevel : null;
        return (T) this;
    }

    /**
     * Fetches whether or not the execution details, such as job initialization and total runtime
     * upon successful completion, should be logged for this job.
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
     *  this JobConfig instance
     */
    public T logExecutionDetails(boolean enabled) {
        this.logExecutionDetails = enabled;
        return (T) this;
    }

    /**
     * Validates whether or not this config is valid. By default, only the job key is required to
     * be a valid job configuration, but subclasses may have more specific requirements.
     *
     * @throws JobConfigValidationException
     *  if this config is incomplete or contains invalid values needed for the job to be queued and
     *  executed
     */
    public void validate() throws JobConfigValidationException {
        String key = this.getJobKey();
        if (key == null || key.isEmpty()) {
            throw new JobConfigValidationException("Job key is null or empty");
        }
    }

}
