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
package org.candlepin.pinsetter.core.model;

import org.candlepin.auth.Principal;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.pinsetter.core.PinsetterJobListener;
import org.candlepin.pinsetter.tasks.KingpinJob;

import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
/**
 * Represents the current status for a long-running job.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_job")
public class JobStatus extends AbstractHibernateObject {

    public static final String TARGET_TYPE = "target_type";
    public static final String TARGET_ID = "target_id";
    public static final String OWNER_ID = "owner_id";
    public static final int RESULT_COL_LENGTH = 255;

    /**
     * Indicates possible states for a particular job.
     */
    public enum JobState {
        CREATED,
        PENDING,
        RUNNING,
        FINISHED,
        CANCELED,
        FAILED,
        WAITING; // added late, reordering would be problematic now
    }

    /**
     * Types that a job can operate on
     */
    public enum TargetType {
        OWNER,
        CONSUMER,
        PRODUCT;
    }

    @Id
    @Size(max = 255)
    @NotNull
    private String id;

    @Column(length = 15)
    @Size(max = 15)
    private String jobGroup;

    private JobState state;
    private Date startTime;
    private Date finishTime;

    @Column(length = RESULT_COL_LENGTH)
    @Size(max = RESULT_COL_LENGTH)
    private String result;

    @Size(max = 255)
    private String principalName;

    private TargetType targetType;

    @Size(max = 255)
    private String targetId;

    @Size(max = 255)
    private String ownerId;

    @Column(length = 255)
    private Class<? extends KingpinJob> jobClass;

    private byte[] resultData;

    @Transient
    private boolean cloakData = false;

    public JobStatus() { }

    public JobStatus(JobDetail jobDetail) {
        this(jobDetail, false);
    }

    public JobStatus(JobDetail jobDetail, boolean waiting) {
        this.id = jobDetail.getKey().getName();
        this.jobGroup = jobDetail.getKey().getGroup();
        this.state = waiting ? JobState.WAITING : JobState.CREATED;
        this.ownerId = this.getOwnerId(jobDetail);
        this.targetType = getTargetType(jobDetail);
        this.targetId = getTargetId(jobDetail);
        this.principalName = getPrincipalName(jobDetail);
        this.jobClass = getJobClass(jobDetail);
    }

    private String getPrincipalName(JobDetail detail) {
        Principal p = (Principal) detail.getJobDataMap().get(
            PinsetterJobListener.PRINCIPAL_KEY);
        return p != null ? p.getPrincipalName() : "unknown";
    }

    private TargetType getTargetType(JobDetail jobDetail) {
        return (TargetType) jobDetail.getJobDataMap().get(TARGET_TYPE);
    }

    private String getTargetId(JobDetail jobDetail) {
        return (String) jobDetail.getJobDataMap().get(TARGET_ID);
    }

    private String getOwnerId(JobDetail jobDetail) {
        return (String) jobDetail.getJobDataMap().get(OWNER_ID);
    }

    @SuppressWarnings("unchecked")
    private Class<? extends KingpinJob> getJobClass(JobDetail jobDetail) {
        return (Class<? extends KingpinJob>) jobDetail.getJobClass();
    }

    public void update(JobExecutionContext context) {
        this.startTime = context.getFireTime();
        long runTime = context.getJobRunTime();

        if (this.startTime != null) {
            setState(JobState.RUNNING);

            if (runTime > -1) {
                setState(JobState.FINISHED);
                this.finishTime = new Date(startTime.getTime() + runTime);
            }
        }
        else {
            setState(JobState.PENDING);
        }

        Object jobResult = context.getResult();
        if (jobResult != null) {
            // TODO Check for instance of JobResult and set these appropriately
            //      Which will allow for a result message of sorts instead of just
            //      a class name resulting from Class.toString()
            // BZ1004780: setResult truncates long strings
            // setting result directly causes database issues.
            setResult(jobResult.toString());
            setResultData(jobResult);
        }
    }

    public String getId() {
        return id;
    }

    public String getGroup() {
        return jobGroup;
    }

    public Date getFinishTime() {
        return finishTime;
    }

    public String getResult() {
        return result;
    }

    public Date getStartTime() {
        return startTime;
    }

    public JobState getState() {
        return state;
    }

    public void setState(JobState state) {
        this.state = state;
    }

    public String getTargetType() {
        if (targetType == null) {
            return null;
        }

        return targetType.name().toLowerCase();
    }

    public String getTargetId() {
        return targetId;
    }


    public String getStatusPath() {
        return "/jobs/" + this.id;
    }

    public void setResult(String result) {
        // truncate the result to fit column
        if (result == null || result.length() < RESULT_COL_LENGTH) {
            this.result = result;
        }
        else {
            this.result = result.substring(0, RESULT_COL_LENGTH);
        }
    }

    public String getPrincipalName() {
        return this.principalName;
    }

    public String getOwnerId() {
        return this.ownerId;
    }

    @XmlTransient
    public Class<? extends KingpinJob> getJobClass() {
        return jobClass;
    }

    @XmlTransient
    public JobKey getJobKey() {
        return new JobKey(this.getId(), this.getGroup());
    }

    public boolean isDone() {
        return this.state == JobState.CANCELED ||
            this.state == JobState.FAILED ||
            this.state == JobState.FINISHED;
    }

    public void setResultData(Object resultData)  {
        if (resultData == null) { return; }

        byte[] data = new byte[0];
        ByteArrayOutputStream baos = null;
        ObjectOutputStream oos = null;

        try {
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(resultData);
            data = baos.toByteArray();
        }
        catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        finally {
            try {
                if (baos != null) {
                    baos.close();
                }
                if (oos != null) {
                    oos.close();
                }
            }
            catch (Exception e) {
                // unable to close streams
            }
        }
        this.resultData = data;
    }

    public Object getResultData() {
        if (this.resultData == null) { return null; }
        if (this.cloakData) { return "[cloaked]"; }

        Object result = null;
        ByteArrayInputStream bais = null;
        ObjectInputStream ois = null;

        try {
            bais = new ByteArrayInputStream(this.resultData);
            ois = new ObjectInputStream(bais);
            result = ois.readObject();
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }
        finally {
            try {
                if (bais != null) {
                    bais.close();
                }
                if (ois != null) {
                    ois.close();
                }
            }
            catch (Exception e) {
                // unable to close streams
            }
        }

        return result;
    }

    public void cloakResultData(boolean cloak) {
        this.cloakData = cloak;
    }

    public String toString() {
        return String.format("JobStatus [id: %s, type: %s, owner: %s, target: %s (%s), state: %s]",
            this.id,
            this.jobClass != null ? this.jobClass.getSimpleName() : null,
            this.ownerId,
            this.targetId,
            this.targetType,
            this.state
        );
    }
}
