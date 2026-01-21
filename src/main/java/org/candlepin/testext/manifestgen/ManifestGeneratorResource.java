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
package org.candlepin.testext.manifestgen;

import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.dto.api.server.v1.SubscriptionDTO;
import org.candlepin.exceptions.IseException;
import org.candlepin.guice.CandlepinCapabilities;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.pki.certs.IdentityCertificateGenerator;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.resource.util.AttachedFile;
import org.candlepin.sync.ExportCreationException;

import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


/**
 * The ManifestGeneratorResource class provides an endpoint for generating a signed manifest from
 * JSON data containing subscription information. Should only be enabled during testing.
 */
@Path("/testext/manifestgen")
public class ManifestGeneratorResource {
    private static final Logger log = LoggerFactory.getLogger(ManifestGeneratorResource.class);

    private static final Pattern ARCHIVE_FILENAME_REGEX = Pattern.compile("\\.(?i:gz)\\z");

    private final OwnerCurator ownerCurator;
    private final ConsumerCurator consumerCurator;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final PoolCurator poolCurator;

    private final EntityMapperFactory entityMapperFactory;
    private final ManifestManager manifestManager;
    private final PoolManager poolManager;
    private final IdentityCertificateGenerator identityCertificateGenerator;

    private final ObjectMapper objectMapper;

    @Inject
    public ManifestGeneratorResource(OwnerCurator ownerCurator,
        ConsumerCurator consumerCurator, ConsumerTypeCurator consumerTypeCurator, PoolCurator poolCurator,
        EntityMapperFactory entityMapperFactory, ManifestManager manifestManager, PoolManager poolManager,
        IdentityCertificateGenerator identityCertificateGenerator, ObjectMapper objectMapper) {

        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.poolCurator = Objects.requireNonNull(poolCurator);

        this.entityMapperFactory = Objects.requireNonNull(entityMapperFactory);
        this.manifestManager = Objects.requireNonNull(manifestManager);
        this.poolManager = Objects.requireNonNull(poolManager);
        this.identityCertificateGenerator = Objects.requireNonNull(identityCertificateGenerator);

        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    /**
     * API to check if resource is alive
     *
     * @return always returns true
     */
    @GET
    @Path("/alive")
    @Produces(MediaType.TEXT_PLAIN)
    public Boolean isAlive() {
        return true;
    }

    /**
     * Begins a new transaction, ensuring a transaction is not already active.
     */
    private EntityTransaction beginTransaction() {
        EntityManager entityManager = this.ownerCurator.getEntityManager();

        EntityTransaction transaction = entityManager.getTransaction();
        if (transaction == null) {
            throw new IllegalStateException("Unable to fetch the current context transaction");
        }

        if (transaction.isActive()) {
            throw new IllegalStateException("Transaction has already begun");
        }

        transaction.begin();

        return transaction;
    }

    /**
     * Generates an owner in which to build the manifest
     */
    private Owner createManifestOwner(long rnd) {
        Owner owner = new Owner()
            .setKey("manifest_org-" + rnd)
            .setDisplayName("manifest org " + rnd);

        return this.ownerCurator.create(owner);
    }

    /**
     * Generates the manifest consumer for the given owner.
     */
    private Consumer createManifestConsumer(Owner owner, String uuid, long rnd) throws IOException,
        GeneralSecurityException {

        ConsumerType ctype = this.consumerTypeCurator.getByLabel(ConsumerTypeEnum.CANDLEPIN.getLabel());
        if (ctype == null) {
            // This shouldn't happen; but if it does, whatever. Just create it.
            log.warn("Consumer type \"{}\" not found; creating it...", ConsumerTypeEnum.CANDLEPIN.getLabel());
            ctype = this.consumerTypeCurator.create(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        }

        // Normalize provided UUID
        if (uuid != null && uuid.isBlank()) {
            uuid = null;
        }

        Map<String, String> facts = Map.of(Consumer.Facts.SYSTEM_CERTIFICATE_VERSION, "3.3");

        // Since we're using this very Candlepin instance to generate a manifest, the consumer
        // is capable of everything it is.
        List<ConsumerCapability> capabilities = CandlepinCapabilities.getCapabilities()
            .stream()
            .map(cname -> new ConsumerCapability(cname))
            .toList();

        Consumer consumer = new Consumer()
            .setUuid(uuid)
            .setType(ctype)
            .setName("manifest_consumer-" + rnd)
            .setOwner(owner)
            .setFacts(facts)
            .setCapabilities(capabilities);

        IdentityCertificate identityCert = this.identityCertificateGenerator.generate(consumer);
        consumer.setIdCert(identityCert);

        return this.consumerCurator.create(consumer);
    }

    private File generateManifest(List<SubscriptionDTO> subscriptions, String consumerUuid, String cdnLabel,
        String webAppPrefix, String apiUrl) throws ExportCreationException {

        long rnd = System.currentTimeMillis();

        // We *never* want to commit this transaction, as global data pollutes the testing space,
        // which is horrid for test integrity. Set the transaction to rollback-only so we don't
        // persist any of this data or state.
        EntityTransaction transaction = this.beginTransaction();
        transaction.setRollbackOnly();

        try {
            Owner owner = this.createManifestOwner(rnd);

            this.entityMapperFactory.getForOwner(owner)
                .addSubscriptions(subscriptions)
                .persist();

            // create consumer
            Consumer consumer = this.createManifestConsumer(owner, consumerUuid, rnd);

            // Bind pools
            // We could probably not consume the full quantity, but it's not really important. The
            // upstream will cease to exist after this operation anyway.
            Map<String, Integer> poolQuantities = new HashMap<>();
            for (Pool pool : this.poolCurator.listByOwner(owner)) {
                if (!pool.isDerived()) {
                    int quantity = pool.getQuantity() != null ? pool.getQuantity().intValue() : 1;
                    poolQuantities.put(pool.getId(), quantity);
                }
            }

            List<Entitlement> entitlements = this.poolManager.entitleByPools(consumer, poolQuantities);

            // Build manifest file
            File manifest = this.manifestManager.generateManifest(consumer.getUuid(),
                cdnLabel, webAppPrefix, apiUrl);

            return manifest;
        }
        catch (IOException | GeneralSecurityException e) {
            log.error("Unable to create manifest consumer", e);
            throw new IseException("Unable to create manifest consumer", e);
        }
        catch (EntitlementRefusedException e) {
            log.error("Unable to entitle pool for manifest generation", e);
            throw new IseException("Unable to entitle pools for manifest generation", e);
        }
        finally {
            transaction.rollback();
        }
    }

    @POST
    @Path("export")
    @Produces("application/zip")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response generateManifestFromJSON(
        @QueryParam("cdn_label") String cdnLabel,
        @QueryParam("webapp_prefix") String webAppPrefix,
        @QueryParam("api_url") String apiUrl,
        @QueryParam("consumer_uuid") String consumerUuid,
        List<SubscriptionDTO> pools) {

        try {
            File manifest = this.generateManifest(pools, consumerUuid, cdnLabel, webAppPrefix, apiUrl);

            return Response.ok(manifest, "application/zip")
                .header("Content-Disposition", "attachment; filename=" + manifest.getName())
                .build();
        }
        catch (ExportCreationException e) {
            throw new IseException("Unable to generate manifest from provided data", e);
        }
    }

    /**
     * Processes the attached file, deserializing it to the specified type, decompressing it if
     * necessary.
     *
     * @throws IOException
     *  if an exception occurs while deserializing the attached file
     *
     * @return
     *  The processed attached file, deserialized into the specified type
     */
    private <T> T processAttachedFile(AttachedFile attached, TypeReference<T> typeref) {
        try {
            InputStream istream = null;

            try {
                istream = attached.getInputStream();
                if (ARCHIVE_FILENAME_REGEX.matcher(attached.getFilename("")).find()) {
                    istream = new GZIPInputStream(istream);
                }

                return this.objectMapper.readValue(istream, typeref);
            }
            finally {
                if (istream != null) {
                    istream.close();
                }
            }
        }
        catch (IOException e) {
            log.error("Unable to deserialize attached file:", e);
            throw new IseException("Unable to deserialize attached file", e);
        }
    }

    @POST
    @Path("export")
    @Produces("application/zip")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response generateManifestFromFile(
        @QueryParam("cdn_label") String cdnLabel,
        @QueryParam("webapp_prefix") String webAppPrefix,
        @QueryParam("api_url") String apiUrl,
        @QueryParam("consumer_uuid") String consumerUuid,
        MultipartInput input) {

        AttachedFile attached = AttachedFile.getAttachedFile(input);
        TypeReference<List<SubscriptionDTO>> typeref = new TypeReference<List<SubscriptionDTO>>() {};
        List<SubscriptionDTO> pools = this.processAttachedFile(attached, typeref);

        try {
            File manifest = this.generateManifest(pools, consumerUuid, cdnLabel, webAppPrefix, apiUrl);

            return Response.ok(manifest, "application/zip")
                .header("Content-Disposition", "attachment; filename=" + manifest.getName())
                .build();
        }
        catch (ExportCreationException e) {
            throw new IseException("Unable to generate manifest from provided data", e);
        }
    }

}
