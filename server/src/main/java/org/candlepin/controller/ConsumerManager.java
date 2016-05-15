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
package org.candlepin.controller;


import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.JobCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.pinsetter.tasks.ConsumerComplianceJob;
import org.candlepin.policy.js.compliance.ComplianceRules;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Used to perform operations on Consumers that need more than just the consumer
 * curator.
 */
public class ConsumerManager {

    @Inject private static Logger log = LoggerFactory.getLogger(ConsumerManager.class);
    @Inject private JobCurator jobCurator;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private ComplianceRules complianceRules;

    @Transactional
    public void computeComplianceIfAsyncScheduled(List<Consumer> consumers) {

        if (CollectionUtils.isNotEmpty(consumers)) {
            List<String> consumerUuids = new ArrayList<String>();
            for (Consumer consumer : consumers) {
                consumerUuids.add(consumer.getUuid());
            }
            List<JobStatus> unfinishedJobs = jobCurator.getUnfinishedJobsByTargetIds(
                ConsumerComplianceJob.class, consumerUuids);
            List<String> scheduledConsumerUuids = new ArrayList<String>();
            if (CollectionUtils.isNotEmpty(unfinishedJobs)) {
                List<String> jobs = new ArrayList<String>();
                for (JobStatus status : unfinishedJobs) {
                    scheduledConsumerUuids.add(status.getTargetId());
                    jobs.add(status.getId());
                }
                Collections.sort(scheduledConsumerUuids);
                consumerCurator.lockAndLoadBatch(scheduledConsumerUuids);
                for (Consumer consumer : consumers) {
                    if (scheduledConsumerUuids.contains(consumer.getUuid())) {
                        complianceRules.getStatus(consumer, null, false, false);
                        consumerCurator.update(consumer);
                    }
                }
                int cancelledJobs = jobCurator.cancelIfNotRunning(jobs);
                log.debug("Cancelled {} redundant jobs", cancelledJobs);
            }
        }
    }

}
