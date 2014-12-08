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
package org.candlepin.audit;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Simple DTO for returning status of the HornetQ queues. Used for checking if events
 * are piling up for some reason or being delivered correctly.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class QueueStatus {

    private String queueName;
    private long pendingMessageCount;

    public QueueStatus() {
    }

    public QueueStatus(String queueName, long pendingMessageCount) {
        this.queueName = queueName;
        this.pendingMessageCount = pendingMessageCount;
    }
    public String getQueueName() {
        return queueName;
    }
    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }
    public long getPendingMessageCount() {
        return pendingMessageCount;
    }
    public void setPendingMessageCount(long pendingMessageCount) {
        this.pendingMessageCount = pendingMessageCount;
    }

}
