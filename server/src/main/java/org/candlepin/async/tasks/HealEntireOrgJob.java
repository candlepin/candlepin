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

import org.candlepin.async.ArgumentConversionException;
import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobConstraints;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.controller.AutobindDisabledForOwnerException;
import org.candlepin.controller.Entitler;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.resource.dto.AutobindData;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Date;
import java.util.List;

/**
 * HealEntireOrgJob
 */
public class HealEntireOrgJob implements AsyncJob {

    private static Logger log = LoggerFactory.getLogger(HealEntireOrgJob.class);
    public static final String JOB_KEY = "HEAL_ENTIRE_ORG_JOB";
    private static final String JOB_NAME = "heal entire org job";
    public static final String OWNER_KEY = "org";
    public static final String ENTITLE_DATE_KEY = "entitle_date";

    private OwnerCurator ownerCurator;
    private Entitler entitler;
    private ConsumerCurator consumerCurator;
    private I18n i18n;

    @Inject
    public HealEntireOrgJob(Entitler entitler, ConsumerCurator consumerCurator, OwnerCurator ownerCurator,
        I18n i18n) {
        this.entitler = entitler;
        this.consumerCurator = consumerCurator;
        this.ownerCurator = ownerCurator;
        this.i18n = i18n;
    }

    @Override
    public Object execute(JobExecutionContext context) throws JobExecutionException {
        try {

            JobArguments arguments = context.getJobArguments();
            String ownerKey = arguments.getAsString(OWNER_KEY);
            Owner owner = ownerCurator.getByKey(ownerKey);
            if (owner == null) {
                log.warn("Healing attempted against non-existent org key \"{}\"", ownerKey);
                throw new JobExecutionException(
                    i18n.tr("Healing attempted against non-existent org key \"{}\"", ownerKey), true);
            }
            if (owner.isAutobindDisabled() || owner.isContentAccessEnabled()) {
                String caMessage = owner.isContentAccessEnabled() ?
                    " because of the content access mode setting" : "";
                throw new JobExecutionException(
                    i18n.tr("Auto-attach is disabled for owner {0}{1}.", owner.getKey(), caMessage), true);
            }

            Date entitleDate = arguments.getAs(ENTITLE_DATE_KEY, Date.class);
            StringBuilder result = new StringBuilder();
            for (String uuid : ownerCurator.getConsumerUuids(owner).list()) {
                // Do not send in product IDs.  CandlepinPoolManager will take care
                // of looking up the non or partially compliant products to bind.
                try {
                    Consumer consumer = consumerCurator.getConsumer(uuid);
                    healSingleConsumer(consumer, owner, entitleDate);
                    result.append("Successfully healed consumer with UUID: ").append(uuid).append("\n");
                }
                // We want to catch everything and continue.
                // Perhaps add something to surface errors later
                catch (Exception e) {
                    log.debug("Healing failed for UUID \"{}\" with message: {}", uuid, e.getMessage());
                    result.append("Healing failed for UUID: ").append(uuid).append("\n");
                }
            }
            return result;
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
    @Transactional
    private void healSingleConsumer(Consumer consumer, Owner owner, Date date)
        throws AutobindDisabledForOwnerException {
        List<Entitlement> ents = entitler.bindByProducts(AutobindData.create(consumer, owner).on(date), true);
        entitler.sendEvents(ents);
    }

    /**
     * Job configuration object for the heal entire org job
     */
    public static class HealEntireOrgJobConfig extends JobConfig {

        public HealEntireOrgJobConfig() {
            this.setJobKey(JOB_KEY).setJobName(JOB_NAME)
                .addConstraint(JobConstraints.uniqueByArguments(OWNER_KEY));
        }

        public HealEntireOrgJobConfig setOwner(final Owner owner) {
            if (owner == null) {
                throw new IllegalArgumentException("Owner is null");
            }
            this.setJobMetadata(LoggingFilter.OWNER_KEY, owner.getKey())
                .setJobArgument(OWNER_KEY, owner.getKey()).setLogLevel(owner.getLogLevel());
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
