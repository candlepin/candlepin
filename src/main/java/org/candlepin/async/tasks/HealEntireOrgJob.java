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

import org.candlepin.async.ArgumentConversionException;
import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobConstraints;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.audit.EventSink;
import org.candlepin.controller.AutobindDisabledForOwnerException;
import org.candlepin.controller.AutobindHypervisorDisabledException;
import org.candlepin.controller.Entitler;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.util.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;



/**
 * HealEntireOrgJob
 */
public class HealEntireOrgJob implements AsyncJob {
    private static final Logger log = LoggerFactory.getLogger(HealEntireOrgJob.class);

    public static final String JOB_KEY = "HealEntireOrgJob";
    public static final String JOB_NAME = "Heal Organization";

    public static final String OWNER_KEY = "org";
    public static final String ENTITLE_DATE_KEY = "entitle_date";

    private final Entitler entitler;
    private final EventSink eventSink;
    private final ConsumerCurator consumerCurator;
    private final OwnerCurator ownerCurator;
    private final I18n i18n;

    @Inject
    public HealEntireOrgJob(Entitler entitler, EventSink eventSink, ConsumerCurator consumerCurator,
        OwnerCurator ownerCurator, I18n i18n) {

        this.entitler = Objects.requireNonNull(entitler);
        this.eventSink = Objects.requireNonNull(eventSink);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.i18n = Objects.requireNonNull(i18n);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobArguments arguments = context.getJobArguments();
            String ownerKey = arguments.getAsString(OWNER_KEY);
            Owner owner = ownerCurator.getByKey(ownerKey);

            if (owner == null) {
                log.warn("Healing attempted against non-existent org key \"{}\"", ownerKey);
                throw new JobExecutionException(
                    i18n.tr("Healing attempted against non-existent org key \"{}\"", ownerKey), true);
            }

            if (owner.isAutobindDisabled()) {
                String errmsg = this.i18n.tr("Auto-attach is disabled for owner {0}", owner.getKey());
                throw new JobExecutionException(errmsg, true);
            }

            if (owner.isUsingSimpleContentAccess()) {
                String errmsg = this.i18n.tr("Auto-attach is disabled for owner {0} while using simple " +
                    "content access", owner.getKey());
                throw new JobExecutionException(errmsg, true);
            }

            Date entitleDate = arguments.getAs(ENTITLE_DATE_KEY, Date.class);
            StringBuilder result = new StringBuilder();

            Transactional<String> transaction = this.consumerCurator.transactional(this::healSingleConsumer)
                .onCommit(status -> eventSink.sendEvents())
                .onRollback(status -> eventSink.rollback());

            for (String uuid : ownerCurator.getConsumerUuids(owner).list()) {
                // Do not send in product IDs.  CandlepinPoolManager will take care
                // of looking up the non or partially compliant products to bind.
                try {
                    Consumer consumer = consumerCurator.getConsumer(uuid);

                    String output = transaction.execute(consumer, owner, entitleDate);
                    result.append(output);
                }
                catch (Exception e) {
                    // We want to catch everything and continue.
                    // Perhaps add something to surface errors later
                    String errmsg = String.format("Healing failed for consumer with UUID: %s", uuid);

                    log.debug(errmsg, e);
                    result.append(errmsg).append("\n");
                }
            }

            context.setJobResult(result.toString());
        }
        catch (Exception e) {
            log.error("HealEntireOrgJob encountered a problem.", e);
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    /**
     * Creates a JobConfig configured to execute the heal entire org job. Callers may further
     * manipulate the JobConfig as necessary before queuing it.
     *
     * @return a JobConfig instance configured to execute the  heal entire org job
     */
    public static HealEntireOrgJobConfig createJobConfig() {
        return new HealEntireOrgJobConfig();
    }

    /*
     * Each consumer heal should be a separate transaction
     */
    public String healSingleConsumer(Object... args)
        throws AutobindDisabledForOwnerException, AutobindHypervisorDisabledException {

        Consumer consumer = (Consumer) args[0];
        Owner owner = (Owner) args[1];
        Date date = (Date) args[2];

        AutobindData autobindData = new AutobindData(consumer, owner)
            .on(date);

        List<Entitlement> ents = entitler.bindByProducts(autobindData, true);
        entitler.sendEvents(ents);

        return String.format("Successfully healed consumer with UUID: %s\n", consumer.getUuid());
    }

    /**
     * Job configuration object for the heal entire org job
     */
    public static class HealEntireOrgJobConfig extends JobConfig<HealEntireOrgJobConfig> {

        public HealEntireOrgJobConfig() {
            this.setJobKey(JOB_KEY)
                .setJobName(JOB_NAME)
                .addConstraint(JobConstraints.uniqueByArguments(OWNER_KEY));
        }

        public HealEntireOrgJobConfig setOwner(final Owner owner) {
            if (owner == null) {
                throw new IllegalArgumentException("Owner is null");
            }

            this.setContextOwner(owner)
                .setJobArgument(OWNER_KEY, owner.getKey());

            return this;
        }

        public HealEntireOrgJobConfig setEntitleDate(final Date entitleDate) {
            this.setJobArgument(ENTITLE_DATE_KEY, entitleDate);
            return this;
        }

        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            try {
                JobArguments arguments = this.getJobArguments();

                String ownerKey = arguments.getAsString(OWNER_KEY);
                if (ownerKey == null || ownerKey.isEmpty()) {
                    String errmsg = "owner has not been set, or the provided owner lacks a key";
                    throw new JobConfigValidationException(errmsg);
                }

                Date entitleDate = arguments.getAs(ENTITLE_DATE_KEY, Date.class);
                if (entitleDate == null) {
                    String errmsg = "entitle date has not been set";
                    throw new JobConfigValidationException(errmsg);
                }
            }
            catch (ArgumentConversionException e) {
                String errmsg = "One or more required arguments are of the wrong type";
                throw new JobConfigValidationException(errmsg, e);
            }
        }
    }
}
