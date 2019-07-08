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
package org.candlepin.async.tasks;

import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.policy.SystemPurposeComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceRules;

import com.google.inject.Inject;

import java.util.List;

/**
 * Job to recalculate compliance for consumers when entitlements become active
 */
public class ActiveEntitlementJob implements AsyncJob {

    public static final String JOB_KEY = "ACTIVE_ENTITLEMENT_JOB";
    private static final String JOB_NAME = "active entitlement job";
    // Every hour:
    public static final String DEFAULT_SCHEDULE = "0 0 0/1 * * ?";

    private ConsumerCurator consumerCurator;
    private ComplianceRules complianceRules;
    private SystemPurposeComplianceRules systemPurposeComplianceRules;

    @Inject
    public ActiveEntitlementJob(ConsumerCurator consumerCurator, ComplianceRules complianceRules,
        SystemPurposeComplianceRules systemPurposeComplianceRules) {
        this.consumerCurator = consumerCurator;
        this.complianceRules = complianceRules;
        this.systemPurposeComplianceRules = systemPurposeComplianceRules;
    }

    @Override
    public Object execute(JobExecutionContext context) throws JobExecutionException {
        List<String> ids = consumerCurator.getConsumerIdsWithStartedEnts();
        for (String id : ids) {
            Consumer c = consumerCurator.get(id);
            complianceRules.getStatus(c);
            systemPurposeComplianceRules.getStatus(c, c.getEntitlements(), null, true);
        }
        return "Completed active entitlement job for " + ids;
    }
}
