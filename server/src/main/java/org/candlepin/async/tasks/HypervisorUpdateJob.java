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
import org.candlepin.auth.Principal;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.HypervisorConsumerDTO;
import org.candlepin.dto.api.v1.HypervisorUpdateResultDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.HypervisorId;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.VirtConsumerMap;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.resource.util.GuestMigration;
import org.candlepin.service.SubscriptionServiceAdapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Asynchronous job for update and creation of hypervisors for specific
 * {@link Owner}. A job will wait for a running job of the same Owner to
 * finish before beginning execution
 */
public class HypervisorUpdateJob implements AsyncJob {

    private static Logger log = LoggerFactory.getLogger(HypervisorUpdateJob.class);

    private ObjectMapper mapper;
    private OwnerCurator ownerCurator;
    private ConsumerCurator consumerCurator;
    private ConsumerResource consumerResource;
    private I18n i18n;
    private ConsumerType hypervisorType;
    private SubscriptionServiceAdapter subAdapter;
    private ComplianceRules complianceRules;
    private ModelTranslator translator;

    public static final String JOB_KEY = "HYPERVISOR_UPDATE_JOB";
    private static final String JOB_NAME = "hypervisor_update";
    private static final String OWNER_KEY = "org";
    public static final String CREATE = "create";
    private static final String REPORTER_ID = "reporter_id";
    private static final String DATA = "data";
    private static final String PRINCIPAL = "principal";

    @Inject
    public HypervisorUpdateJob(OwnerCurator ownerCurator, ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator, ConsumerResource consumerResource, I18n i18n,
        SubscriptionServiceAdapter subAdapter, ComplianceRules complianceRules, ModelTranslator translator,
        @Named("HypervisorUpdateJobObjectMapper") ObjectMapper objectMapper) {
        this.ownerCurator = ownerCurator;
        this.consumerCurator = consumerCurator;
        this.consumerResource = consumerResource;
        this.i18n = i18n;
        this.subAdapter = subAdapter;
        this.complianceRules = complianceRules;
        this.translator = translator;
        this.hypervisorType = consumerTypeCurator.getByLabel(ConsumerTypeEnum.HYPERVISOR.getLabel(), true);
        this.mapper = objectMapper;
    }

    public static HypervisorUpdateJobConfig createConfig() {
        return new HypervisorUpdateJobConfig();
    }

    /**
     * {@inheritDoc}
     *
     * Updates or creates missing hypervisors for specific {@link Owner} as an
     * async job.
     *
     * @param context the job's execution context
     */
    @Transactional
    @SuppressWarnings({ "checkstyle:indentation", "checkstyle:methodlength" })
    public Object execute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobArguments arguments = context.getJobArguments();
            String ownerKey = arguments.getAsString(OWNER_KEY);
            Boolean create = arguments.getAsBoolean(CREATE);
            String principal = arguments.getAsString(PRINCIPAL);
            String jobReporterId = arguments.getAsString(REPORTER_ID);

            HypervisorUpdateResultDTO result = new HypervisorUpdateResultDTO();

            Owner owner = ownerCurator.getByKey(ownerKey);
            if (owner == null) {
                log.warn("Hypervisor update attempted against non-existent org id \"{}\"", ownerKey);
                return "Nothing to do. Owner does not exist";
            }

            if (owner.isAutobindDisabled() || owner.isContentAccessEnabled()) {
                String caMessage = owner.isContentAccessEnabled() ?
                    " because of the content access mode setting" : "";
                log.debug("Could not update host/guest mapping. Auto-Attach is disabled for owner {}{}",
                    owner.getKey(), caMessage);
                throw new BadRequestException(
                    i18n.tr("Could not update host/guest mapping. Auto-attach is disabled for owner {0}{1}.",
                        owner.getKey(), caMessage));
            }

            byte[] data = arguments.getAs(DATA, byte[].class);
            String json = decompress(data);
            HypervisorList hypervisors = mapper.readValue(json, HypervisorList.class);
            log.debug("Hypervisor consumers for create/update: {}", hypervisors.getHypervisors().size());
            log.debug("Updating hypervisor consumers for org {}", ownerKey);

            Set<String> hosts = new HashSet<>();
            Set<String> guests = new HashSet<>();
            Map<String, Consumer> incomingHosts = new HashMap<>();
            parseHypervisorList(hypervisors, hosts, guests, incomingHosts);
            // TODO Need to ensure that we retrieve existing guestIds from the DB before continuing.

            // Maps virt hypervisor ID to registered consumer for that hypervisor, should one exist:
            VirtConsumerMap hypervisorKnownConsumersMap =
                consumerCurator.getHostConsumersMap(owner, hypervisors.getHypervisors());
            Map<String, Consumer> systemUuidKnownConsumersMap = new HashMap<>();
            for (Consumer consumer : hypervisorKnownConsumersMap.getConsumers()) {
                if (consumer.hasFact(Consumer.Facts.SYSTEM_UUID)) {
                    systemUuidKnownConsumersMap.put(
                        consumer.getFact(Consumer.Facts.SYSTEM_UUID), consumer);
                }
            }

            Map<String, GuestId> guestIds = consumerCurator.getGuestIdMap(guests, owner);
            for (String hypervisorId : hosts) {
                Consumer incoming = incomingHosts.get(hypervisorId);
                Consumer knownHost = hypervisorKnownConsumersMap.get(hypervisorId);
                // HypervisorId might be different in candlepin
                if (knownHost == null && incoming.hasFact(Consumer.Facts.SYSTEM_UUID) &&
                    systemUuidKnownConsumersMap.get(incoming.getFact(Consumer.Facts.SYSTEM_UUID)) != null) {
                    knownHost = systemUuidKnownConsumersMap.get(incoming.getFact(Consumer.Facts.SYSTEM_UUID));
                    if (knownHost != null) {
                        log.debug("Found a known host by system uuid");
                    }
                }

                Consumer reportedOnConsumer = null;

                if (knownHost == null) {
                    if (!create) {
                        result.addFailed(hypervisorId,
                            "Unable to find hypervisor with id " + hypervisorId + " in org " + ownerKey);
                    }
                    else {
                        log.debug("Registering new host consumer for hypervisor ID: {}", hypervisorId);
                        Consumer newHost = createConsumerForHypervisorId(hypervisorId, jobReporterId, owner,
                            principal, incoming);

                        // Since we just created this new consumer, we can migrate the guests immediately
                        GuestMigration guestMigration = new GuestMigration(consumerCurator)
                            .buildMigrationManifest(incoming, newHost);

                        // Now that we have the new consumer persisted, immediately migrate the guests to it
                        if (guestMigration.isMigrationPending()) {
                            guestMigration.migrate(false);
                        }

                        hypervisorKnownConsumersMap.add(hypervisorId, newHost);
                        result.addCreated(this.translator.translate(newHost, HypervisorConsumerDTO.class));
                        reportedOnConsumer = newHost;
                    }
                }
                else {
                    boolean hypervisorIdUpdated = false;
                    if (knownHost.getHypervisorId() != null && !hypervisorId.equalsIgnoreCase(knownHost
                        .getHypervisorId().getHypervisorId())) {
                        hypervisorIdUpdated = true;
                        log.debug("Changing hypervisor id to [" + hypervisorId + "]");
                        knownHost.getHypervisorId().setHypervisorId(hypervisorId);
                    }

                    reportedOnConsumer = knownHost;
                    if (jobReporterId != null && knownHost.getHypervisorId() != null &&
                        hypervisorId.equalsIgnoreCase(knownHost.getHypervisorId().getHypervisorId()) &&
                        knownHost.getHypervisorId().getReporterId() != null &&
                        !jobReporterId.equalsIgnoreCase(knownHost.getHypervisorId().getReporterId())) {
                        log.debug("Reporter changed for Hypervisor {} of Owner {} from {} to {}",
                            hypervisorId, ownerKey, knownHost.getHypervisorId().getReporterId(),
                            jobReporterId);
                    }
                    boolean typeUpdated = false;
                    if (!hypervisorType.getId().equals(knownHost.getTypeId())) {
                        typeUpdated = true;
                        knownHost.setType(hypervisorType);
                    }

                    GuestMigration guestMigration = new GuestMigration(consumerCurator)
                        .buildMigrationManifest(incoming, knownHost);

                    boolean factsUpdated = consumerResource.checkForFactsUpdate(knownHost, incoming);

                    if (factsUpdated || guestMigration.isMigrationPending() || typeUpdated ||
                        hypervisorIdUpdated) {
                        knownHost.setLastCheckin(new Date());
                        guestMigration.migrate(false);
                        result.addUpdated(this.translator.translate(knownHost, HypervisorConsumerDTO.class));
                    }
                    else {
                        result.addUnchanged(
                            this.translator.translate(knownHost, HypervisorConsumerDTO.class));
                    }
                }
                // update reporter id if it changed
                if (jobReporterId != null && reportedOnConsumer != null &&
                    reportedOnConsumer.getHypervisorId() != null &&
                    (reportedOnConsumer.getHypervisorId().getReporterId() == null ||
                        !jobReporterId.contentEquals(reportedOnConsumer.getHypervisorId().getReporterId()))) {
                    reportedOnConsumer.getHypervisorId().setReporterId(jobReporterId);
                }
                else if (jobReporterId == null) {
                    log.debug("hypervisor checkin reported asynchronously without reporter id " +
                        "for hypervisor:{} of owner:{}", hypervisorId, ownerKey);
                }
            }

            for (Consumer consumer : hypervisorKnownConsumersMap.getConsumers()) {
                consumer = result.wasCreated(
                    this.translator.translate(consumer, HypervisorConsumerDTO.class)) ?
                    consumerCurator.create(consumer, false) :
                    consumerCurator.update(consumer, false);
            }

            consumerCurator.flush();

            log.info("Summary for report from {} by principal {}\n {}", jobReporterId, principal, result);
            return result;
        }
        catch (Exception e) {
            log.error("HypervisorUpdateJob encountered a problem.", e);
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    private void parseHypervisorList(HypervisorList hypervisorList, Set<String> hosts,
        Set<String> guests, Map<String, Consumer> incomingHosts) {
        int emptyGuestIdCount = 0;
        int emptyHypervisorIdCount = 0;

        List<Consumer> l = hypervisorList.getHypervisors();
        for (Iterator<Consumer> hypervisors = l.iterator(); hypervisors.hasNext();) {
            Consumer hypervisor = hypervisors.next();

            HypervisorId idWrapper = hypervisor.getHypervisorId();

            if (idWrapper == null) {
                continue;
            }

            String id = idWrapper.getHypervisorId();

            if (id == null) {
                continue;
            }

            if ("".equals(id)) {
                hypervisors.remove();
                emptyHypervisorIdCount++;
                continue;
            }

            incomingHosts.put(id, hypervisor);
            hosts.add(id);

            List<GuestId> guestsIdList = hypervisor.getGuestIds();

            if (guestsIdList == null || guestsIdList.isEmpty()) {
                continue;
            }

            for (Iterator<GuestId> guestIds = guestsIdList.iterator(); guestIds.hasNext();) {
                GuestId guestId = guestIds.next();
                if (StringUtils.isEmpty(guestId.getGuestId())) {
                    guestIds.remove();
                    emptyGuestIdCount++;
                }
                else {
                    guests.add(guestId.getGuestId());
                }
            }
        }

        if (emptyHypervisorIdCount > 0) {
            log.debug("Ignoring {} hypervisors with empty hypervisor IDs", emptyHypervisorIdCount);
        }

        if (emptyGuestIdCount > 0) {
            log.debug("Ignoring {} empty/null guestId(s)", emptyGuestIdCount);
        }
    }

    private static byte[] compress(String text) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            OutputStream out = new DeflaterOutputStream(baos);
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.close();
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
        return baos.toByteArray();
    }

    private static String decompress(byte[] bytes) {
        InputStream in = new InflaterInputStream(new ByteArrayInputStream(bytes));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
    }
    /*
     * Create a new hypervisor type consumer to represent the incoming hypervisorId
     */

    private Consumer createConsumerForHypervisorId(String incHypervisorId, String reporterId,
        Owner owner, String principal, Consumer incoming) {
        Consumer consumer = new Consumer();
        if (incoming.getName() != null) {
            consumer.setName(incoming.getName());
        }
        else {
            consumer.setName(sanitizeHypervisorId(incHypervisorId));
        }
        consumer.setType(hypervisorType);
        consumer.setFact("uname.machine", "x86_64");
        consumer.setGuestIds(new ArrayList<>());
        consumer.setLastCheckin(new Date());
        consumer.setOwner(owner);
        consumer.setAutoheal(true);
        consumer.setCanActivate(subAdapter.canActivateSubscription(consumer));
        if (owner.getDefaultServiceLevel() != null) {
            consumer.setServiceLevel(owner.getDefaultServiceLevel());
        }
        else {
            consumer.setServiceLevel("");
        }
        if (principal != null) {
            consumer.setUsername(principal);
        }
        consumer.setEntitlementCount(0L);
        // TODO: Refactor this to not call resource methods directly
        consumerResource.sanitizeConsumerFacts(consumer);


        // Create HypervisorId
        HypervisorId hypervisorId = new HypervisorId(consumer, owner, incHypervisorId);
        hypervisorId.setReporterId(reporterId);
        consumer.setHypervisorId(hypervisorId);

        // TODO: Refactor this to not call resource methods directly
        consumerResource.checkForFactsUpdate(consumer, incoming);

        return consumer;
    }

    /*
     * Make sure the HypervisorId is a valid consumer name.
     */
    private String sanitizeHypervisorId(String incHypervisorId) {
        // Same validation as consumerResource.checkConsumerName
        if (incHypervisorId.indexOf('#') == 0) {
            log.debug("Hypervisor id cannot begin with # character");
            incHypervisorId = incHypervisorId.substring(1);
        }

        int max = Consumer.MAX_LENGTH_OF_CONSUMER_NAME;
        if (incHypervisorId.length() > max) {
            log.debug("Hypervisor id too long, truncating");
            incHypervisorId = incHypervisorId.substring(0, max);
        }
        return incHypervisorId;
    }

    /**
     * Job configuration object for the hypervisor update job
     */
    public static class HypervisorUpdateJobConfig extends JobConfig {

        public HypervisorUpdateJobConfig() {
            this.setJobKey(JOB_KEY)
                .setJobName(JOB_NAME)
                .addConstraint(JobConstraints.uniqueByArgument(OWNER_KEY));
        }

        /**
         * Sets the owner for this hypervisor update job.
         *
         * @param owner
         *  the owner to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public HypervisorUpdateJobConfig setOwner(final Owner owner) {
            if (owner == null) {
                throw new IllegalArgumentException("owner is null");
            }

            this.setJobArgument(OWNER_KEY, owner.getKey())
                .setLogLevel(owner.getLogLevel());

            return this;
        }

        public HypervisorUpdateJobConfig setReporter(final String reporterId) {
            this.setJobArgument(REPORTER_ID, reporterId);

            return this;
        }

        public HypervisorUpdateJobConfig setCreateMissing(final boolean create) {
            this.setJobArgument(CREATE, create);

            return this;
        }

        public HypervisorUpdateJobConfig setData(final String data) {
            if (data == null || data.isEmpty()) {
                throw new IllegalArgumentException("hypervisor data is null");
            }

            this.setJobArgument(DATA, compress(data));

            return this;
        }

        public HypervisorUpdateJobConfig setPrincipal(final Principal principal) {
            if (principal == null) {
                throw new IllegalArgumentException("principal is null");
            }

            this.setJobArgument(PRINCIPAL, principal.getUsername());

            return this;
        }

        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            try {
                final JobArguments arguments = this.getJobArguments();

                final String ownerKey = arguments.getAsString(OWNER_KEY);
                final Boolean create = arguments.getAsBoolean(CREATE);
                final String data = arguments.getAsString(DATA);

                if (ownerKey == null || ownerKey.isEmpty()) {
                    final String errmsg = "owner has not been set!";
                    throw new JobConfigValidationException(errmsg);
                }
                if (create == null) {
                    final String errmsg = "create flag has not been set!";
                    throw new JobConfigValidationException(errmsg);
                }
                if (data == null || data.isEmpty()) {
                    final String errmsg = "hypervisor data has not been set!";
                    throw new JobConfigValidationException(errmsg);
                }

            }
            catch (ArgumentConversionException e) {
                final String errmsg = "One or more required arguments are of the wrong type";
                throw new JobConfigValidationException(errmsg, e);
            }
        }
    }

    /**
     * Class for holding the list of consumers in the stored json text
     *
     * @author wpoteat
     */
    public static class HypervisorList {
        private List<Consumer> hypervisors;

        public List<Consumer> getHypervisors() {
            return this.hypervisors;
        }

        public void setConsumers(List<Consumer> hypervisors) {
            this.hypervisors = hypervisors;
        }
    }

}
