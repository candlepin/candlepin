/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.util.Transactional;

import java.util.List;
import java.util.Objects;

import javax.inject.Inject;



/**
 * Job to recalculate compliance for consumers when entitlements become active
 */
public class ActiveEntitlementJob implements AsyncJob {

    public static final String JOB_KEY = "ActiveEntitlementJob";
    public static final String JOB_NAME = "Active Entitlement";
    // Every hour
    public static final String DEFAULT_SCHEDULE = "0 0 0/1 * * ?";

    private final ConsumerCurator consumerCurator;
    private final ComplianceRules complianceRules;
    private final SystemPurposeComplianceRules systemPurposeComplianceRules;

    @Inject
    public ActiveEntitlementJob(ConsumerCurator consumerCurator, ComplianceRules complianceRules,
        SystemPurposeComplianceRules systemPurposeComplianceRules) {

        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.complianceRules = Objects.requireNonNull(complianceRules);
        this.systemPurposeComplianceRules = Objects.requireNonNull(systemPurposeComplianceRules);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Transactional transaction = this.consumerCurator.transactional((args) -> {
            if (args == null || args.length != 1) {
                throw new IllegalArgumentException("Unexpected value received for arguments: " + args);
            }

            Consumer consumer = (Consumer) args[0];

            this.complianceRules.getStatus(consumer);
            this.systemPurposeComplianceRules.getStatus(consumer, consumer.getEntitlements(), null, true);

            return null;
        });

        List<String> consumerIds = this.consumerCurator.getConsumerIdsWithStartedEnts();

        if (consumerIds != null && !consumerIds.isEmpty()) {
            consumerIds.stream()
                .map(id -> this.consumerCurator.get(id))
                .filter(Objects::nonNull)
                .forEach(transaction::execute);

            context.setJobResult("Entitlement status updated for %d consumers: %s", consumerIds.size(),
                consumerIds);
        }
        else {
            context.setJobResult("No consumers with entitlements pending activation found");
        }
    }
}
