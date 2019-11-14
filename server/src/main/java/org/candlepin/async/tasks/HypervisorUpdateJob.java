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
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.VirtConsumerMap;
import org.candlepin.service.impl.HypervisorUpdateAction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;

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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
    private HypervisorUpdateAction hypervisorUpdateAction;
    private I18n i18n;
    private ModelTranslator translator;

    public static final String JOB_KEY = "HypervisorUpdateJob";
    private static final String JOB_NAME = "hypervisor_update";
    private static final String OWNER_KEY = "org";
    public static final String CREATE = "create";
    private static final String REPORTER_ID = "reporter_id";
    private static final String DATA = "data";
    private static final String PRINCIPAL = "principal";
    private static final int BULK_SIZE = 10;

    @Inject
    public HypervisorUpdateJob(
        final OwnerCurator ownerCurator,
        final ConsumerCurator consumerCurator,
        final ModelTranslator translator,
        final HypervisorUpdateAction hypervisorUpdateAction,
        final I18n i18n,
        @Named("HypervisorUpdateJobObjectMapper") final ObjectMapper objectMapper) {

        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.translator = Objects.requireNonNull(translator);
        this.hypervisorUpdateAction = Objects.requireNonNull(hypervisorUpdateAction);
        this.i18n = Objects.requireNonNull(i18n);
        this.mapper = Objects.requireNonNull(objectMapper);
    }

    public static HypervisorUpdateJobConfig createJobConfig() {
        return new HypervisorUpdateJobConfig();
    }

    /**
     * {@inheritDoc}
     *
     * Updates or creates missing hypervisors for specific {@link Owner} as an
     * async job.
     *
     * @param context the job's execution context
     * @return
     */
    @Override
    public Object execute(final JobExecutionContext context) throws JobExecutionException {
        try {
            JobArguments arguments = context.getJobArguments();
            String ownerKey = arguments.getAsString(OWNER_KEY);
            Boolean create = arguments.getAsBoolean(CREATE);
            String principal = arguments.getAsString(PRINCIPAL);
            String jobReporterId = arguments.getAsString(REPORTER_ID);

            final Owner owner = ownerCurator.getByKey(ownerKey);
            if (owner == null) {
                log.warn("Hypervisor update attempted against non-existent org id \"{}\"", ownerKey);
                return "Nothing to do. Owner does not exist";
            }

            if (owner.isAutobindDisabled() || owner.isContentAccessEnabled()) {
                final String caMessage = owner.isContentAccessEnabled() ?
                    " because of the content access mode setting" : "";
                log.debug("Could not update host/guest mapping. Auto-Attach is disabled for owner {}{}",
                    owner.getKey(), caMessage);
                throw new BadRequestException(
                    i18n.tr("Could not update host/guest mapping. Auto-attach is disabled for owner {0}{1}.",
                        owner.getKey(), caMessage));
            }

            final HypervisorList hypervisors = parsedHypervisors(arguments);
            final HypervisorUpdateAction.Result updateResult = hypervisorUpdateAction.update(
                owner, hypervisors.getHypervisors(), create, principal, jobReporterId);
            final HypervisorUpdateResultDTO result = updateResult.getResult();
            final VirtConsumerMap hypervisorKnownConsumersMap = updateResult.getKnownConsumers();

            final List<Consumer> created = new ArrayList<>();
            final List<Consumer> updated = new ArrayList<>();
            for (Consumer consumer : hypervisorKnownConsumersMap.getConsumers()) {
                final HypervisorConsumerDTO translated = this.translator.translate(
                    consumer, HypervisorConsumerDTO.class);
                if (result.wasCreated(translated)) {
                    created.add(consumer);
                }
                else {
                    updated.add(consumer);
                }
            }

            doInBulk(created, consumers -> consumerCurator.saveAll(consumers, false, false));
            doInBulk(updated, consumers -> consumerCurator.bulkUpdate(consumers, false));

            log.info("Summary for report from {} by principal {}\n {}", jobReporterId, principal, result);
            return result;
        }
        catch (Exception e) {
            log.error("HypervisorUpdateJob encountered a problem.", e);
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    private HypervisorList parsedHypervisors(final JobArguments arguments) throws IOException {
        final byte[] data = arguments.getAs(DATA, byte[].class);
        final String json = decompress(data);
        return mapper.readValue(json, HypervisorList.class);
    }

    private void doInBulk(final List<Consumer> created,
        final java.util.function.Consumer<Set<Consumer>> action) {
        Lists.partition(created, BULK_SIZE)
            .stream()
            .map(HashSet::new)
            .forEach(action);
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

    /**
     * Job configuration object for the hypervisor update job
     */
    public static class HypervisorUpdateJobConfig extends JobConfig<HypervisorUpdateJobConfig> {

        public HypervisorUpdateJobConfig() {
            this.setJobKey(JOB_KEY)
                .setJobName(JOB_NAME)
                .addConstraint(JobConstraints.uniqueByArguments(OWNER_KEY));
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

            this.setContextOwner(owner)
                .setJobArgument(OWNER_KEY, owner.getKey());

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
                Map<String, Object> arguments = this.getJobArguments();

                final String ownerKey = (String) arguments.get(OWNER_KEY);
                final Boolean create = (Boolean) arguments.get(CREATE);
                final String data = (String) arguments.get(DATA);

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
            catch (ClassCastException e) {
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
