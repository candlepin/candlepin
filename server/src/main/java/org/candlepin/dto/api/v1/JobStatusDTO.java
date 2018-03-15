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
package org.candlepin.dto.api.v1;

import io.swagger.annotations.ApiModel;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.candlepin.dto.TimestampedCandlepinDTO;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Date;

/**
 * A DTO representation of the JobStatus entity
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "DTO representing a job status")
public class JobStatusDTO extends TimestampedCandlepinDTO<JobStatusDTO> {
    public static final long serialVersionUID = 1L;

    private String id;
    private String jobGroup;
    private String state;
    private Date startTime;
    private Date finishTime;
    private String result;
    private String principalName;
    private String targetType;
    private String targetId;
    private String ownerId;
    private String correlationId;

    private Object resultData;
    private Boolean done;

    /**
     * Initializes a new JobStatusDTO instance with null values.
     */
    public JobStatusDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new JobStatusDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public JobStatusDTO(JobStatusDTO source) {
        super(source);
    }

    /**
     * Retrieves the id field of this JobStatusDTO object.
     *
     * @return the id field of this JobStatusDTO object.
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the id to set on this JobStatusDTO object.
     *
     * @param id the id to set on this JobStatusDTO object.
     *
     * @return a reference to this DTO object.
     */
    public JobStatusDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the job group of this JobStatusDTO object.
     *
     * @return the job group of this JobStatusDTO object.
     */
    public String getGroup() {
        return jobGroup;
    }

    /**
     * Sets the job group to set on this JobStatusDTO object.
     *
     * @param jobGroup the job group to set on this JobStatusDTO object.
     *
     * @return a reference to this DTO object.
     */
    public JobStatusDTO setGroup(String jobGroup) {
        this.jobGroup = jobGroup;
        return this;
    }

    /**
     * Retrieves the state of this JobStatusDTO object.
     *
     * @return the state of this JobStatusDTO object.
     */
    public String getState() {
        return state;
    }

    /**
     * Sets the state to set on this JobStatusDTO object.
     *
     * @param state the state to set on this JobStatusDTO object.
     *
     * @return a reference to this DTO object.
     */
    public JobStatusDTO setState(String state) {
        this.state = state;
        return this;
    }

    /**
     * Retrieves the start time of this JobStatusDTO object.
     *
     * @return the start time of this JobStatusDTO object.
     */
    public Date getStartTime() {
        return startTime;
    }

    /**
     * Sets the start time to set on this JobStatusDTO object.
     *
     * @param startTime the start time to set on this JobStatusDTO object.
     *
     * @return a reference to this DTO object.
     */
    public JobStatusDTO setStartTime(Date startTime) {
        this.startTime = startTime;
        return this;
    }

    /**
     * Retrieves the finish time of this JobStatusDTO object.
     *
     * @return the finish time of this JobStatusDTO object.
     */
    public Date getFinishTime() {
        return finishTime;
    }

    /**
     * Sets the finish time to set on this JobStatusDTO object.
     *
     * @param finishTime the finish time to set on this JobStatusDTO object.
     *
     * @return a reference to this DTO object.
     */
    public JobStatusDTO setFinishTime(Date finishTime) {
        this.finishTime = finishTime;
        return this;
    }

    /**
     * Retrieves the result of this JobStatusDTO object.
     *
     * @return the result of this JobStatusDTO object.
     */
    public String getResult() {
        return result;
    }

    /**
     * Sets the result to set on this JobStatusDTO object.
     *
     * @param result the result to set on this JobStatusDTO object.
     *
     * @return a reference to this DTO object.
     */
    public JobStatusDTO setResult(String result) {
        this.result = result;
        return this;
    }

    /**
     * Retrieves the principal name of this JobStatusDTO object.
     *
     * @return the principal name of this JobStatusDTO object.
     */
    public String getPrincipalName() {
        return principalName;
    }

    /**
     * Sets the principal name to set on this JobStatusDTO object.
     *
     * @param principalName the principal name to set on this JobStatusDTO object.
     *
     * @return a reference to this DTO object.
     */
    public JobStatusDTO setPrincipalName(String principalName) {
        this.principalName = principalName;
        return this;
    }

    /**
     * Retrieves the target type of this JobStatusDTO object.
     *
     * @return the target type of this JobStatusDTO object.
     */
    public String getTargetType() {
        return targetType;
    }

    /**
     * Sets the target type to set on this JobStatusDTO object.
     *
     * @param targetType the target type to set on this JobStatusDTO object.
     *
     * @return a reference to this DTO object.
     */
    public JobStatusDTO setTargetType(String targetType) {
        this.targetType = targetType;
        return this;
    }

    /**
     * Retrieves the target id of this JobStatusDTO object.
     *
     * @return the target id of this JobStatusDTO object.
     */
    public String getTargetId() {
        return targetId;
    }

    /**
     * Sets the target id to set on this JobStatusDTO object.
     *
     * @param targetId the target id to set on this JobStatusDTO object.
     *
     * @return a reference to this DTO object.
     */
    public JobStatusDTO setTargetId(String targetId) {
        this.targetId = targetId;
        return this;
    }

    /**
     * Retrieves the owner id of this JobStatusDTO object.
     *
     * @return the owner id of this JobStatusDTO object.
     */
    public String getOwnerId() {
        return ownerId;
    }

    /**
     * Sets the owner id to set on this JobStatusDTO object.
     *
     * @param ownerId the owner id to set on this JobStatusDTO object.
     *
     * @return a reference to this DTO object.
     */
    public JobStatusDTO setOwnerId(String ownerId) {
        this.ownerId = ownerId;
        return this;
    }

    /**
     * Retrieves the correlation id of this JobStatusDTO object.
     *
     * @return the correlation id of this JobStatusDTO object.
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Sets the correlation id to set on this JobStatusDTO object.
     *
     * @param correlationId the correlation id to set on this JobStatusDTO object.
     *
     * @return a reference to this DTO object.
     */
    public JobStatusDTO setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    /**
     * Retrieves the result data of this JobStatusDTO object.
     *
     * @return the result data of this JobStatusDTO object.
     */
    public Object getResultData() {
        return resultData;
    }

    /**
     * Sets the result data to set on this JobStatusDTO object.
     *
     * @param resultData the result data to set on this JobStatusDTO object.
     *
     * @return a reference to this DTO object.
     */
    public JobStatusDTO setResultData(Object resultData) {
        this.resultData = resultData;
        return this;
    }

    /**
     * Returns true if the job that this JobStatusDTO object represents is done. False otherwise.
     *
     * @return true if the job that this JobStatusDTO object represents is done. False otherwise.
     */
    public Boolean isDone() {
        return done;
    }

    /**
     * Sets true or false depending on if the job that this JobStatusDTO object represents is done.
     *
     * @param done if the job that this JobStatusDTO object represents is done.
     *
     * @return a reference to this DTO object.
     */
    public JobStatusDTO setDone(Boolean done) {
        this.done = done;
        return this;
    }

    /**
     * Retrieves the URL path of this JobStatusDTO object.
     *
     * @return the URL path of this JobStatusDTO object.
     */
    public String getStatusPath() {
        return this.getId() != null ? String.format("/jobs/%s", this.getId()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("JobStatusDTO [id: %s, state: %s, target id: %s, target type: %s, state: %s]",
            this.getId(), this.getState(), this.getTargetId(), this.getTargetType(), this.getState());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof JobStatusDTO && super.equals(obj)) {
            JobStatusDTO that = (JobStatusDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getCorrelationId(), that.getCorrelationId())
                .append(this.getGroup(), that.getGroup())
                .append(this.getPrincipalName(), that.getPrincipalName())
                .append(this.getOwnerId(), that.getOwnerId())
                .append(this.getResult(), that.getResult())
                .append(this.getResultData(), that.getResultData())
                .append(this.getStartTime(), that.getStartTime())
                .append(this.getFinishTime(), that.getFinishTime())
                .append(this.getState(), that.getState())
                .append(this.getTargetId(), that.getTargetId())
                .append(this.getTargetType(), that.getTargetType())
                .append(this.isDone(), that.isDone());

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
            .append(this.getCorrelationId())
            .append(this.getGroup())
            .append(this.getPrincipalName())
            .append(this.getOwnerId())
            .append(this.getResult())
            .append(this.getResultData())
            .append(this.getStartTime())
            .append(this.getFinishTime())
            .append(this.getState())
            .append(this.getTargetId())
            .append(this.getTargetType())
            .append(this.isDone());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobStatusDTO clone() {
        JobStatusDTO copy = super.clone();
        copy.startTime = this.startTime != null ? (Date) this.startTime.clone() : null;
        copy.finishTime = this.finishTime != null ? (Date) this.finishTime.clone() : null;

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobStatusDTO populate(JobStatusDTO source) {
        super.populate(source);

        this.setId(source.getId())
            .setCorrelationId(source.getCorrelationId())
            .setGroup(source.getGroup())
            .setPrincipalName(source.getPrincipalName())
            .setOwnerId(source.getOwnerId())
            .setResult(source.getResult())
            .setResultData(source.getResultData())
            .setStartTime(source.getStartTime())
            .setFinishTime(source.getFinishTime())
            .setState(source.getState())
            .setTargetId(source.getTargetId())
            .setTargetType(source.getTargetType())
            .setDone(source.isDone());

        return this;
    }
}
