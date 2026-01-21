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
import org.candlepin.auth.Principal;
import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.dto.api.server.v1.HypervisorUpdateResultDTO;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.service.impl.HypervisorUpdateAction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import javax.inject.Inject;
import javax.inject.Named;


/**
 * Asynchronous job for update and creation of hypervisors for specific
 * {@link Owner}. A job will wait for a running job of the same Owner to
 * finish before beginning execution
 */
public class HypervisorUpdateJob implements AsyncJob {
    private static final Logger log = LoggerFactory.getLogger(HypervisorUpdateJob.class);

    public static final String JOB_KEY = "HypervisorUpdateJob";
    public static final String JOB_NAME = "Hypervisor Update";

    private static final String OWNER_KEY = "org";
    private static final String CREATE_KEY = "create";
    private static final String REPORTER_ID_KEY = "reporter_id";
    private static final String DATA_KEY = "data";
    private static final String PRINCIPAL_KEY = "principal";

    private final ObjectMapper mapper;
    private final OwnerCurator ownerCurator;
    private final HypervisorUpdateAction hypervisorUpdateAction;

    @Inject
    public HypervisorUpdateJob(
        final OwnerCurator ownerCurator,
        final HypervisorUpdateAction hypervisorUpdateAction,
        @Named("HypervisorUpdateJobObjectMapper") final ObjectMapper objectMapper) {

        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.hypervisorUpdateAction = Objects.requireNonNull(hypervisorUpdateAction);
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
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        try {
            JobArguments arguments = context.getJobArguments();

            String ownerKey = arguments.getAsString(OWNER_KEY);
            Boolean create = arguments.getAsBoolean(CREATE_KEY);
            String principal = arguments.getAsString(PRINCIPAL_KEY);
            String jobReporterId = arguments.getAsString(REPORTER_ID_KEY);

            final Owner owner = ownerCurator.getByKey(ownerKey);
            if (owner == null) {
                String result = String.format("Hypervisor update attempted against non-existent org: \"%s\"",
                    ownerKey);

                log.warn(result);
                context.setJobResult(result);

                return;
            }

            final HypervisorList hypervisors = parsedHypervisors(arguments);
            final HypervisorUpdateAction.Result updateResult = hypervisorUpdateAction.update(
                owner, hypervisors.getHypervisors(), create, principal, jobReporterId);
            final HypervisorUpdateResultDTO result = updateResult.getResult();

            log.info("Summary for report from {} by principal {}\n {}", jobReporterId, principal, result);
            context.setJobResult(result);
        }
        catch (Exception e) {
            log.error("HypervisorUpdateJob encountered a problem.", e);
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    private HypervisorList parsedHypervisors(final JobArguments arguments) throws IOException {
        final byte[] data = arguments.getAs(DATA_KEY, byte[].class);
        final String json = decompress(data);
        return mapper.readValue(json, HypervisorList.class);
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
            this.setJobArgument(REPORTER_ID_KEY, reporterId);

            return this;
        }

        public HypervisorUpdateJobConfig setCreateMissing(final boolean create) {
            this.setJobArgument(CREATE_KEY, create);

            return this;
        }

        public HypervisorUpdateJobConfig setData(final String data) {
            if (data == null || data.isEmpty()) {
                throw new IllegalArgumentException("hypervisor data is null");
            }

            this.setJobArgument(DATA_KEY, compress(data));

            return this;
        }

        public HypervisorUpdateJobConfig setPrincipal(final Principal principal) {
            if (principal == null) {
                throw new IllegalArgumentException("principal is null");
            }

            this.setJobArgument(PRINCIPAL_KEY, principal.getUsername());

            return this;
        }

        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            try {
                final JobArguments arguments = this.getJobArguments();

                final String ownerKey = arguments.getAsString(OWNER_KEY);
                final Boolean create = arguments.getAsBoolean(CREATE_KEY);
                final String data = arguments.getAsString(DATA_KEY);

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
        private List<ConsumerDTO> hypervisors;

        public List<ConsumerDTO> getHypervisors() {
            return this.hypervisors;
        }

        public void setConsumers(List<ConsumerDTO> hypervisors) {
            this.hypervisors = hypervisors;
        }
    }

}
