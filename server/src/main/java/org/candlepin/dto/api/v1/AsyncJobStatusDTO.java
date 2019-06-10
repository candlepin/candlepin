/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.TimestampedCandlepinDTO;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.Date;



/**
 * A DTO representation of the AsyncJobStatus entity
 */
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "DTO representing an async job status")
public class AsyncJobStatusDTO extends TimestampedCandlepinDTO<AsyncJobStatusDTO> {
    public static final long serialVersionUID = 1L;

    @ApiModelProperty(example = "ff808081554a3e4101554a3e9033005d")
    private String id;

    @ApiModelProperty(example = "refresh_pools-ff808081554a3e4101554a3e9033005d")
    private String name;

    @ApiModelProperty(example = "refresh")
    private String group;

    @ApiModelProperty(example = "candlepin.myhost.com")
    private String origin;

    @ApiModelProperty(example = "candlepin.myhost.com")
    private String executor;

    @ApiModelProperty(example = "admin")
    private String principal;

    @ApiModelProperty(example = "RUNNING")
    private String state;

    @ApiModelProperty(example = "QUEUED")
    private String previousState;

    @ApiModelProperty(example = "2019-05-08 09:42:37.000")
    private Date startTime;

    @ApiModelProperty(example = "2019-05-08 09:42:37.000")
    private Date endTime;

    @ApiModelProperty(example = "1")
    private Integer attempts;

    @ApiModelProperty(example = "3")
    private Integer maxAttempts;

    @ApiModelProperty(example = "Refresh completed successfully!")
    private Object result;


    /**
     * Initializes a new AsyncJobStatusDTO instance with null values.
     */
    public AsyncJobStatusDTO() {
        super();
    }

    /**
     * Initializes a new AsyncJobStatusDTO instance using the data contained by the given DTO.
     *
     * @param source
     *  The source DTO from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     */
    public AsyncJobStatusDTO(AsyncJobStatusDTO source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.populate(source);
    }

    /**
     * Retrieves the ID of the job status represented by this DTO. If the ID has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the ID of the job status, or null if the ID has not yet been defined
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the ID of the job status represented by this DTO.
     *
     * @param id
     *  The ID of the job status represented by this DTO
     *
     * @return
     *  a reference to this DTO
     */
    public AsyncJobStatusDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the name of the job status represented by this DTO. If the name has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the name of the job status, or null if the name has not yet been defined
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name of the job status represented by this DTO.
     *
     * @param name
     *  The name of the job status represented by this DTO
     *
     * @return
     *  a reference to this DTO
     */
    public AsyncJobStatusDTO setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Retrieves the group of the job status represented by this DTO. If the group has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the group of the job status, or null if the group has not yet been defined
     */
    public String getGroup() {
        return this.group;
    }

    /**
     * Sets the group of the job status represented by this DTO.
     *
     * @param group
     *  The group of the job status represented by this DTO
     *
     * @return
     *  a reference to this DTO
     */
    public AsyncJobStatusDTO setGroup(String group) {
        this.group = group;
        return this;
    }

    /**
     * Retrieves the origin of the job status represented by this DTO. If the origin has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the origin of the job status, or null if the origin has not yet been defined
     */
    public String getOrigin() {
        return this.origin;
    }

    /**
     * Sets the origin of the job status represented by this DTO.
     *
     * @param origin
     *  The origin of the job status represented by this DTO
     *
     * @return
     *  a reference to this DTO
     */
    public AsyncJobStatusDTO setOrigin(String origin) {
        this.origin = origin;
        return this;
    }

    /**
     * Retrieves the executor of the job status represented by this DTO. If the executor has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the executor of the job status, or null if the executor has not yet been defined
     */
    public String getExecutor() {
        return this.executor;
    }

    /**
     * Sets the executor of the job status represented by this DTO.
     *
     * @param executor
     *  The executor of the job status represented by this DTO
     *
     * @return
     *  a reference to this DTO
     */
    public AsyncJobStatusDTO setExecutor(String executor) {
        this.executor = executor;
        return this;
    }

    /**
     * Retrieves the principal of the job status represented by this DTO. If the principal has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the principal of the job status, or null if the principal has not yet been defined
     */
    public String getPrincipal() {
        return this.principal;
    }

    /**
     * Sets the principal of the job status represented by this DTO.
     *
     * @param principal
     *  The principal of the job status represented by this DTO
     *
     * @return
     *  a reference to this DTO
     */
    public AsyncJobStatusDTO setPrincipal(String principal) {
        this.principal = principal;
        return this;
    }

    /**
     * Retrieves the state of the job status represented by this DTO. If the state has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the state of the job status, or null if the state has not yet been defined
     */
    public String getState() {
        return this.state;
    }

    /**
     * Sets the state of the job status represented by this DTO.
     *
     * @param state
     *  The state of the job status represented by this DTO
     *
     * @return
     *  a reference to this DTO
     */
    public AsyncJobStatusDTO setState(String state) {
        this.state = state;
        return this;
    }

    /**
     * Retrieves the previous state of the job status represented by this DTO. If the previous state
     * has not yet been defined, this method returns null.
     *
     * @return
     *  the previous state of the job status, or null if the previous state has not yet been defined
     */
    public String getPreviousState() {
        return this.previousState;
    }

    /**
     * Sets the previous state of the job status represented by this DTO.
     *
     * @param pstate
     *  The previous state of the job status represented by this DTO
     *
     * @return
     *  a reference to this DTO
     */
    public AsyncJobStatusDTO setPreviousState(String pstate) {
        this.previousState = pstate;
        return this;
    }

    /**
     * Retrieves the start time of the last execution of the job status represented by this DTO. If
     * the start time has not yet been defined, this method returns null.
     *
     * @return
     *  the start time of the last execution of the job status, or null if the start time has not
     *  yet been defined
     */
    public Date getStartTime() {
        return this.startTime;
    }

    /**
     * Sets the start time of the last execution of job status represented by this DTO.
     *
     * @param startTime
     *  The start time of the last execution of the job status represented by this DTO
     *
     * @return
     *  a reference to this DTO
     */
    public AsyncJobStatusDTO setStartTime(Date startTime) {
        this.startTime = startTime;
        return this;
    }

    /**
     * Retrieves the end time of the last execution of the job status represented by this DTO. If
     * the end time has not yet been defined, this method returns null.
     *
     * @return
     *  the end time of the last execution of the job status, or null if the end time has not
     *  yet been defined
     */
    public Date getEndTime() {
        return this.endTime;
    }

    /**
     * Sets the end time of the last execution of job status represented by this DTO.
     *
     * @param endTime
     *  The end time of the last execution of the job status represented by this DTO
     *
     * @return
     *  a reference to this DTO
     */
    public AsyncJobStatusDTO setEndTime(Date endTime) {
        this.endTime = endTime;
        return this;
    }

    /**
     * Retrieves the number of times this job has been attempted. If the number of attempts has not
     * been set, this method returns null.
     *
     * @return
     *  the number of times this job has been attempted, or null if the number of attempts has not
     *  been set
     */
    public Integer getAttempts() {
        return this.attempts;
    }

    /**
     * Sets the number of times this job represented by this DTO has been attempted.
     *
     * @param attempts
     *  The number of execution attempts of the job represented by this DTO
     *
     * @return
     *  a reference to this DTO
     */
    public AsyncJobStatusDTO setAttempts(Integer attempts) {
        this.attempts = attempts;
        return this;
    }

    /**
     * Retrieves the maximum number of times this job will be attempted. If the maximum attempts
     * has not been set, this method returns null.
     *
     * @return
     *  the maximum number of times this job will be attempted, or null if the maximum attempts has
     *  not been set
     */
    public Integer getMaxAttempts() {
        return this.maxAttempts;
    }

    /**
     * Sets the maximum number of times this job represented by this DTO will be attempted.
     *
     * @param maxAttempts
     *  The maximum number of execution attempts of the job represented by this DTO
     *
     * @return
     *  a reference to this DTO
     */
    public AsyncJobStatusDTO setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
        return this;
    }

    /**
     * Retrieves the result of the last execution of the job status represented by this DTO. If the
     * result has not yet been defined, this method returns null.
     *
     * @return
     *  the result of the last execution of the job status, or null if the result has not yet been
     *  defined
     */
    public Object getResult() {
        return this.result;
    }

    /**
     * Sets the result of the last execution of the job status represented by this DTO.
     *
     * @param result
     *  The result of the last execution of the job status represented by this DTO
     *
     * @return
     *  a reference to this DTO
     */
    public AsyncJobStatusDTO setResult(Object result) {
        this.result = result;
        return this;
    }

    /**
     * Retrieves the URL path of this AsyncJobStatusDTO object.
     *
     * @return the URL path of this AsyncJobStatusDTO object.
     */
    public String getStatusPath() {
        //TODO: The `/async` resource is temporary for testing ONLY. This should change to `/jobs` once that
        // resource has been converted to use the new job framework!
        return this.getId() != null ? String.format("/async/%s", this.getId()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("AsyncJobStatusDTO [id: %s, name: %s, group: %s, state: %s]",
            this.getId(), this.getName(), this.getGroup(), this.getState());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof AsyncJobStatusDTO && super.equals(obj)) {
            AsyncJobStatusDTO that = (AsyncJobStatusDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getGroup(), that.getGroup())
                .append(this.getName(), that.getName())
                .append(this.getOrigin(), that.getOrigin())
                .append(this.getExecutor(), that.getExecutor())
                .append(this.getPrincipal(), that.getPrincipal())
                .append(this.getState(), that.getState())
                .append(this.getPreviousState(), that.getPreviousState())
                .append(this.getStartTime(), that.getStartTime())
                .append(this.getEndTime(), that.getEndTime())
                .append(this.getAttempts(), that.getAttempts())
                .append(this.getMaxAttempts(), that.getMaxAttempts())
                .append(this.getResult(), that.getResult());

            return builder.isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getId())
            .append(this.getGroup())
            .append(this.getName())
            .append(this.getOrigin())
            .append(this.getExecutor())
            .append(this.getPrincipal())
            .append(this.getState())
            .append(this.getPreviousState())
            .append(this.getStartTime())
            .append(this.getEndTime())
            .append(this.getAttempts())
            .append(this.getMaxAttempts())
            .append(this.getResult());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncJobStatusDTO clone() {
        AsyncJobStatusDTO copy = super.clone();

        Date startTime = this.getStartTime();
        copy.setStartTime(startTime != null ? (Date) startTime.clone() : null);

        Date endTime = this.getEndTime();
        copy.setEndTime(endTime != null ? (Date) endTime.clone() : null);

        // Impl note:
        // We *should* be cloning the result, but since we don't know the type, we can't actually
        // clone it safely, due to known shortcomings in Java's cloning framework. We can't use
        // Object.clone, Cloneable provides no targetable methods and reflection is janky. We're
        // just going to hope for the best here and have to be diligent when it comes to actual
        // usage within Candlepin.

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncJobStatusDTO populate(AsyncJobStatusDTO source) {
        super.populate(source);

        this.setId(source.getId())
            .setGroup(source.getGroup())
            .setName(source.getName())
            .setOrigin(source.getOrigin())
            .setExecutor(source.getExecutor())
            .setPrincipal(source.getPrincipal())
            .setState(source.getState())
            .setPreviousState(source.getPreviousState())
            .setStartTime(source.getStartTime())
            .setEndTime(source.getEndTime())
            .setAttempts(source.getAttempts())
            .setMaxAttempts(source.getMaxAttempts())
            .setResult(source.getResult());

        return this;
    }
}
