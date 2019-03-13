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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;



/**
 * The JobManager manages the queueing, execution and general bookkeeping on jobs
 */
public class JobBuilder {

    private String key;
    private String name;
    private String group;
    private Map<String, Object> arguments;
    private SortedMap<String, String> constraints;
    private int retries;
    private boolean logJobExecution;



    /**
     * Creates an empty JobBuilder
     */
    public JobBuilder() {
        this.arguments = new HashMap<>();
        this.constraints = new TreeMap<>();
    }

    /**
     * Creates a new JobBuilder for the specified asyncronous job task.
     *
     * @param jobKey
     *  The key representing the job class to handle execution of this job
     *
     * @return
     *  a new JobBuilder instance for the specified job class
     */
    public static JobBuilder forJob(String jobKey) {
        JobBuilder builder = new JobBuilder();

        return builder.setJobKey(jobKey);
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
     *  this JobBuilder instance
     */
    public JobBuilder setJobKey(String jobKey) {
        if (jobKey == null || jobKey.isEmpty()) {
            throw new IllegalArgumentException("jobKey is null or empty");
        }

        this.key = jobKey;
        return this;
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
     *  this JobBuilder instance
     */
    public JobBuilder setJobName(String jobName) {
        if (jobName == null || jobName.isEmpty()) {
            throw new IllegalArgumentException("jobName is null or empty");
        }

        this.name = jobName;
        return this;
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
     * @throws IllegalArgumentException
     *  if the group name is null or empty
     *
     * @return
     *  this JobBuilder instance
     */
    public JobBuilder setJobGroup(String jobGroup) {
        if (jobGroup == null || jobGroup.isEmpty()) {
            throw new IllegalArgumentException("jobGroup is null or empty");
        }

        this.group = jobGroup;
        return this;
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
     * Adds a unique constraint to this job. If this job is passed to the job manager while an
     * existing job matches any of the unique constraints, the new job will be rejected as a
     * duplicate.
     *
     * @param key
     *  The key defining the unique constraint; i.e. "product_id" or "owner_id"
     *
     * @param value
     *  The value of the unique constraint
     *
     * @throws IllegalArgumentException
     *  if either key or value is null or empty
     *
     * @return
     *  this JobBuilder instance
     */
    public JobBuilder setUniqueConstraint(String key, String value) {
        // Impl note:
        // It's potentially problematic if someone sets empty values here, but I don't think
        // it would break any queries or otherwise not work; it'd just be... silly and make
        // debugging a bit painful.
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key is null or empty");
        }

        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("value is null or empty");
        }

        this.constraints.put(key, value);
        return this;
    }

    /**
     * Fetches a mapping of the unique constraints set for this job. If the job does not have any
     * constraints, this method returns an empty map. Note that the map returned by this method
     * cannot be modified directly, though changes made to the unique constraints by this builder
     * will be reflected in the returned map.
     *
     * @return
     *  the map of unique constraints for this job
     */
    public SortedMap<String, String> getUniqueConstraints() {
        return Collections.unmodifiableSortedMap(this.constraints);
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
     *  this JobBuilder instance
     */
    public JobBuilder setJobArgument(String arg, Object value) {
        if (arg == null) {
            throw new IllegalArgumentException("arg is null");
        }

        this.arguments.put(arg, value);
        return this;
    }

    /**
     * Fetches the arguments set for this job. If no arguments have been set for this job,
     * this method returns an empty map. Note that the map returned by this method cannot be
     * modified directly, though changes made to the arguments by this builder will be reflected
     * in the returned map.
     *
     * @return
     *  a map of arguments to pass to the job at execution time
     */
    public Map<String, Object> getJobArguments() {
        return Collections.unmodifiableMap(this.arguments);
    }

    /**
     * Sets the number of times this job will be retried if it fails to complete normally. Values
     * lower than 1 indicate the job will not be retried at all on failure.
     *
     * @param count
     *  The number of times the job should be retried on failure
     *
     * @return
     *  this JobBuilder instance
     */
    public JobBuilder setRetryCount(int count) {
        this.retries = Math.max(0, count);
        return this;
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
     * Sets whether or not job execution logging is enabled or disabled for this job. If enabled,
     * the start time and total runtime of this job will be written to the logs when this job is
     * started and completed, respectively. Defaults to false.
     *
     * @param enabled
     *  Whether or not job execution logging is enabled or disabled
     *
     * @return
     *  this JobBuilder instance
     */
    public JobBuilder setJobExecutionLogging(boolean enabled) {
        this.logJobExecution = enabled;
        return this;
    }

    /**
     * Checks whether or not this job should log job execution time.
     *
     * @return
     *  true if the job's execution time should be logged; false otherwise
     */
    public boolean shouldLogJobExecution() {
        return this.logJobExecution;
    }



    // TODO:
    // Add stuff for setting job metadata, if it turns out we actually need that. A lot of the metadata
    // we would care about comes from the environment (principal, time of scheduling, request IDs, etc.),
    // so we probably won't need to set it explicitly, unless we're rescheduling. But even in such a case,
    // we won't be using a JobBuilder to do the rescheduling.

    // One thing we may want to consider is adding a metadata for everything we want logged, and just
    // dump all of the fields in the log line for any job that has it. Would be an easy, generic way
    // to add logging info to the jobs in a way that's not unique to any particular job.




}
        //      JobBuilder builder = new JobBuilder()
        //          .forTask(runnable job class or key here)
        //          .setName("my_task")
        //          .setTaskArgument("key", "value")
        //          .setTaskArgument("key2", "value2")
        //          .addUniqueConstraint("owner", owner_id_here)
        //          .addUniqueConstraint("product", product_id_here)
        //          .addTaskMetadata("correlation_id", cid)
        //          .setRetryCount(3)
        //
        //      jobManager.queueJob(builder);


