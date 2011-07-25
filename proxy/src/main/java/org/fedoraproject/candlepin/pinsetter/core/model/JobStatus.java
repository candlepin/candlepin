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

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import org.fedoraproject.candlepin.model.AbstractHibernateObject;
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

    public static final String OWNER_KEY = "owner_key";

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

    @Id
    private String id;
    private String jobGroup;
    private JobState state;
    private Date startTime;
    private Date finishTime;
    private String result;
    private String ownerKey;

    public JobStatus() { }

    public JobStatus(JobDetail jobDetail) {
        this.id = jobDetail.getName();
        this.jobGroup = jobDetail.getGroup();
        this.state = JobState.CREATED;
        this.ownerKey = getOwnerKey(jobDetail);
    }

    private String getOwnerKey(JobDetail jobDetail) {
        return (String) jobDetail.getJobDataMap().get(OWNER_KEY);
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

    @Override
    public Serializable getId() {
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

    public String getOwnerKey() {
        return ownerKey;
    }

    public String getStatusPath() {
        return "/jobs/" + this.id;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
