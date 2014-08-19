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
package org.candlepin.pinsetter.tasks;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.policy.js.compliance.ComplianceRules;

import com.google.inject.Inject;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.List;

/**
 * Job to recalculate compliance for consumers when entitlements become active
 */
public class ActiveEntitlementJob extends KingpinJob {

    // Every hour:
    public static final String DEFAULT_SCHEDULE = "0 0 0/1 * * ?";

    private ConsumerCurator consumerCurator;
    private ComplianceRules complianceRules;

    @Inject
    public ActiveEntitlementJob(ConsumerCurator consumerCurator,
            ComplianceRules complianceRules) {
        this.consumerCurator = consumerCurator;
        this.complianceRules = complianceRules;
    }

    @Override
    public void toExecute(JobExecutionContext ctx) throws JobExecutionException {
        // not uuids
        List<String> ids = consumerCurator.getConsumerIdsWithStartedEnts();
        for (String id : ids) {
            Consumer c = consumerCurator.find(id);
            complianceRules.getStatus(c);
        }
    }
}
