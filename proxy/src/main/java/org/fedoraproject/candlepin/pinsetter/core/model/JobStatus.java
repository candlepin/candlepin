/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.pinsetter.core.model;

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.model.AbstractHibernateObject;
import org.fedoraproject.candlepin.pinsetter.core.PinsetterJobListener;

import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;

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

    /**
     * Indicates possible states for a particular job.
     */
    public enum JobState {
        CREATED,
        PENDING,
        RUNNING,
        FINISHED,
        CANCELLED,
        FAILED;
    }

    /**
     * Types that a job can operate on
     */
    public enum TargetType {
        OWNER,
        CONSUMER;
    }

    @Id
    private String id;
    @Column(length = 15)
    private String jobGroup;
    private JobState state;
    private Date startTime;
    private Date finishTime;
    private String result;
    private String principalName;

    private TargetType targetType;
    private String targetId;

    public JobStatus() { }

    public JobStatus(JobDetail jobDetail) {
        this.id = jobDetail.getName();
        this.jobGroup = jobDetail.getGroup();
        this.state = JobState.CREATED;
        this.targetType = getTargetType(jobDetail);
        this.targetId = getTargetId(jobDetail);
        this.principalName = getPrincipalName(jobDetail);
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

    public void update(JobExecutionContext context) {
        this.startTime = context.getFireTime();
        long runTime = context.getJobRunTime();

        if (this.startTime != null) {
            this.state = JobState.RUNNING;

            if (runTime > -1) {
                this.finishTime = new Date(startTime.getTime() + runTime);
                this.state = JobState.FINISHED;
            }
        }
        else {
            this.state = JobState.PENDING;
        }

        Object jobResult = context.getResult();
        if (jobResult != null) {
            this.result = jobResult.toString();
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
        this.result = result;
    }

    public String getPrincipalName() {
        return this.principalName;
    }
}
