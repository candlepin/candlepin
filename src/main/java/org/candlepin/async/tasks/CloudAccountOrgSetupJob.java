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
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.service.CloudProvider;
import org.candlepin.service.CloudRegistrationAdapter;
import org.candlepin.service.exception.CouldNotAcquireCloudAccountLockException;
import org.candlepin.service.exception.CouldNotEntitleOrganizationException;
import org.candlepin.service.model.CloudAccountData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import javax.inject.Inject;



public class CloudAccountOrgSetupJob implements AsyncJob {

    private static final Logger log = LoggerFactory.getLogger(CloudAccountOrgSetupJob.class);

    public static final String JOB_KEY = "CloudAccountOrgSetupJob";
    public static final String JOB_NAME = "Cloud Account Org Setup Job";

    protected static final String CLOUD_ACCOUNT_ID = "cloud_account_id";
    protected static final String OFFERING_ID = "offering_id";
    protected static final String CLOUD_PROVIDER = "cloud_provider";
    protected static final String OWNER_KEY = "owner_key";

    private final CloudRegistrationAdapter cloudAdapter;
    private OwnerCurator ownerCurator;

    @Inject
    public CloudAccountOrgSetupJob(CloudRegistrationAdapter cloudAdapter, OwnerCurator ownerCurator) {

        this.cloudAdapter = Objects.requireNonNull(cloudAdapter);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
    }

    /**
     * Executes this job.
     *
     * @param context
     *     the execution context for the job, containing the arguments and other context-specific data
     * @throws JobExecutionException
     *     if an exception occurs during job execution
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobArguments args = context.getJobArguments();

        String accountId = args.getAsString(CLOUD_ACCOUNT_ID);
        String offeringId = args.getAsString(OFFERING_ID);
        String ownerKey = args.getAsString(OWNER_KEY);
        CloudProvider cloudProviderShortName = args.getAs(CLOUD_PROVIDER, CloudProvider.class);

        try {
            CloudAccountData accountData = this.cloudAdapter.setupCloudAccountOrg(
                accountId, offeringId, cloudProviderShortName, ownerKey);

            Owner owner = ownerCurator.getByKey(accountData.ownerKey());
            if (owner == null) {
                if (accountData.isAnonymous() == null) {
                    throw new IllegalStateException("Cannot create owner. Incomplete account data received.");
                }
                else {
                    Owner newOwner = new Owner()
                        .setKey(accountData.ownerKey())
                        .setAnonymous(accountData.isAnonymous())
                        .setClaimed(false);
                    ownerCurator.create(newOwner);
                }
            }

            context.setJobResult("Entitled offering %s to owner %s (anonymous: %s).", offeringId,
                accountData.ownerKey(), accountData.isAnonymous());
            log.info("Entitled offering {} to owner {} (anonymous: {}).", offeringId,
                accountData.ownerKey(), accountData.isAnonymous());
        }
        catch (CouldNotEntitleOrganizationException | CouldNotAcquireCloudAccountLockException e) {
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    /**
     * Creates a JobConfig configured to execute the isAnonymous register job. Callers may further
     * manipulate the JobConfig as necessary before queuing it.
     *
     * @return a JobConfig instance configured to execute the isAnonymous register job
     */
    public static CloudAccountOrgSetupJobConfig createJobConfig() {
        return new CloudAccountOrgSetupJobConfig();
    }

    /**
     * Job configuration object for the export job
     */
    public static class CloudAccountOrgSetupJobConfig extends JobConfig<CloudAccountOrgSetupJobConfig> {

        public CloudAccountOrgSetupJobConfig() {
            this.setJobKey(JOB_KEY).setJobName(JOB_NAME).addConstraint(
                JobConstraints.uniqueByArguments(CLOUD_ACCOUNT_ID, OFFERING_ID)).setRetryCount(5);
        }

        /**
         * Sets the cloud account ID for this job
         *
         * @param cloudAccountId
         *     the cloud account ID to set for this job
         * @return a reference to this job config
         */
        public CloudAccountOrgSetupJobConfig setCloudAccountId(String cloudAccountId) {
            if (cloudAccountId == null || cloudAccountId.isBlank()) {
                throw new IllegalArgumentException("cloudAccountId is null or empty");
            }

            this.setJobArgument(CLOUD_ACCOUNT_ID, cloudAccountId);

            return this;
        }

        /**
         * Sets the cloud offering ID for this job
         *
         * @param cloudOfferingId
         *     the cloud offering ID to set for this job
         * @return a reference to this job config
         */
        public CloudAccountOrgSetupJobConfig setCloudOfferingId(String cloudOfferingId) {
            if (cloudOfferingId == null || cloudOfferingId.isBlank()) {
                throw new IllegalArgumentException("cloudOfferingId is null or empty");
            }

            this.setJobArgument(OFFERING_ID, cloudOfferingId);

            return this;
        }

        /**
         * Sets the cloud provider for this job
         *
         * @param cloudProvider
         *     cloud provider for this job
         * @return a reference to this job config
         */
        public CloudAccountOrgSetupJobConfig setCloudProvider(CloudProvider cloudProvider) {
            if (cloudProvider == null) {
                throw new IllegalArgumentException("cloudProvider is null");
            }

            this.setJobArgument(CLOUD_PROVIDER, cloudProvider);

            return this;
        }

        /**
         * Sets the owner key for this job
         *
         * @param ownerKey
         *     the owner key to set for this job
         * @return a reference to this job config
         */
        public CloudAccountOrgSetupJobConfig setOwnerKey(String ownerKey) {
            if (ownerKey != null && ownerKey.isBlank()) {
                throw new IllegalArgumentException("ownerKey is empty");
            }

            this.setJobArgument(OWNER_KEY, ownerKey);

            return this;
        }

        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            try {
                JobArguments arguments = this.getJobArguments();

                String accountId = arguments.getAsString(CLOUD_ACCOUNT_ID);
                String offeringId = arguments.getAsString(OFFERING_ID);
                String cloudProviderShortName = arguments.getAsString(CLOUD_PROVIDER);
                String ownerKey = arguments.getAsString(OWNER_KEY);

                if (accountId == null || accountId.isBlank()) {
                    String errmsg = "Cloud Account ID has not been set, or is empty";
                    throw new JobConfigValidationException(errmsg);
                }

                if (offeringId == null || offeringId.isBlank()) {
                    String errmsg = "Cloud Offering ID has not been set, or is empty";
                    throw new JobConfigValidationException(errmsg);
                }

                if (cloudProviderShortName == null) {
                    String errmsg = "Cloud provider has not been set";
                    throw new JobConfigValidationException(errmsg);
                }

                if (ownerKey != null && ownerKey.isBlank()) {
                    String errmsg = "Owner key is empty";
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
