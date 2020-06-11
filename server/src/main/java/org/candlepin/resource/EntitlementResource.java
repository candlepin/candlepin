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
package org.candlepin.resource;

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobException;
import org.candlepin.async.JobManager;
import org.candlepin.async.tasks.RegenProductEntitlementCertsJob;
import org.candlepin.auth.Verify;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.PoolManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.EntitlementDTO;
import org.candlepin.dto.api.v1.KeyValueParamDTO;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EntitlementFilterBuilder;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.SubscriptionsCertificate;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.Enforcer.CallerType;
import org.candlepin.policy.js.entitlement.EntitlementRulesTranslator;
import org.candlepin.resource.util.EntitlementFinderUtil;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.core.ResteasyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * REST api gateway for the User object.
 */
public class EntitlementResource implements EntitlementsApi {
    private static final Logger log = LoggerFactory.getLogger(EntitlementResource.class);

    private final ConsumerCurator consumerCurator;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final PoolManager poolManager;
    private final EntitlementCurator entitlementCurator;
    private final I18n i18n;
    private final Entitler entitler;
    private final Enforcer enforcer;
    private final EntitlementRulesTranslator messageTranslator;
    private final JobManager jobManager;
    private final ModelTranslator translator;

    @Inject
    public EntitlementResource(EntitlementCurator entitlementCurator,
        ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        PoolManager poolManager,
        I18n i18n,
        Entitler entitler,
        Enforcer enforcer,
        EntitlementRulesTranslator messageTranslator,
        JobManager jobManager,
        ModelTranslator translator) {

        this.entitlementCurator = Objects.requireNonNull(entitlementCurator);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.i18n = Objects.requireNonNull(i18n);
        this.poolManager = Objects.requireNonNull(poolManager);
        this.entitler = Objects.requireNonNull(entitler);
        this.enforcer = Objects.requireNonNull(enforcer);
        this.messageTranslator = Objects.requireNonNull(messageTranslator);
        this.jobManager = Objects.requireNonNull(jobManager);
        this.translator = Objects.requireNonNull(translator);
    }

    private void verifyExistence(Object o, String id) {
        if (o == null) {
            throw new RuntimeException(i18n.tr("Object with ID \"{0}\" could not found.", id));
        }
    }

    @Override
    public EntitlementDTO hasEntitlement(String consumerUuid, String productId) {
        Consumer consumer = consumerCurator.findByUuid(consumerUuid);
        verifyExistence(consumer, consumerUuid);

        for (Entitlement entitlement : consumer.getEntitlements()) {
            Pool entitlementPool = entitlement.getPool();
            if (entitlementPool == null) {
                continue;
            }

            Product poolProduct = entitlementPool.getProduct();
            if (poolProduct == null) {
                continue;
            }

            if (productId.equals(poolProduct.getId())) {
                return this.translator.translate(entitlement, EntitlementDTO.class);
            }
        }

        throw new NotFoundException(i18n.tr("Unit \"{0}\" has no subscription for product \"{1}\".",
            consumerUuid, productId));
    }

    @Override
    public List<EntitlementDTO> listAllForConsumer(
        String consumerUuid,
        String matches,
        List<KeyValueParamDTO> attrFilters) {

        PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);
        EntitlementFilterBuilder filters = EntitlementFinderUtil.createFilter(matches, attrFilters);
        Page<List<Entitlement>> p;
        if (consumerUuid != null) {
            Consumer consumer = consumerCurator.findByUuid(consumerUuid);
            if (consumer == null) {
                throw new BadRequestException(
                    i18n.tr("Unit with ID \"{0}\" could not be found.", consumerUuid));
            }
            p = entitlementCurator.listByConsumer(consumer, null, filters, pageRequest);
        }
        else {
            p = entitlementCurator.listAll(filters, pageRequest);
        }

        // Store the page for the LinkHeaderResponseFilter
        ResteasyContext.pushContext(Page.class, p);

        List<EntitlementDTO> entitlementDTOs = new ArrayList<>();
        for (Entitlement entitlement : p.getPageData()) {
            entitlementDTOs.add(this.translator.translate(entitlement, EntitlementDTO.class));
        }
        return entitlementDTOs;
    }

    @Override
    public EntitlementDTO getEntitlement(@Verify(Entitlement.class) String entitlementId) {
        Entitlement entitlement = entitlementCurator.get(entitlementId);

        if (entitlement == null) {
            throw new NotFoundException(
                i18n.tr("Entitlement with ID \"{0}\" could not be found.", entitlementId)
            );
        }

        // Impl note:
        // If the entitlement is dirty, this performs an in-place update of the entitlement, not a
        // generation of a new entitlement object, as the name would imply.
        poolManager.regenerateDirtyEntitlements(Arrays.asList(entitlement));

        return this.translator.translate(entitlement, EntitlementDTO.class);
    }

    @Override
    public void updateEntitlement(@Verify(Entitlement.class) String id, EntitlementDTO update) {
        //TODO Why do we accept a whole Entitlement object here, if we only use the quantity field???

        // Check that quantity param was set and is not 0:
        if (update.getQuantity() <= 0) {
            throw new BadRequestException(i18n.tr("Quantity value must be greater than 0."));
        }

        // Verify entitlement exists:
        Entitlement entitlement = entitlementCurator.get(id);
        if (entitlement != null) {
            // make sure that this will be a change
            if (!entitlement.getQuantity().equals(update.getQuantity())) {
                Consumer consumer = entitlement.getConsumer();
                entitler.adjustEntitlementQuantity(consumer, entitlement, update.getQuantity());
            }
        }
        else {
            throw new NotFoundException(i18n.tr("Entitlement with ID \"{0}\" could not be found.", id));
        }
    }

    /**
     * Retrieves a Subscription Certificate.  We can't return CdnInfo at this
     * time, but when the time comes this is the implementation we want to start
     * from. It will require changes to thumbslug.
     * Will also @Produces(MediaType.APPLICATION_JSON)", value = "getUpstreamCert"
     */
    @Override
    public String getUpstreamCert(String entitlementId) {
        Entitlement ent = entitlementCurator.get(entitlementId);
        if (ent == null) {
            throw new NotFoundException(i18n.tr(
                "Entitlement with ID \"{0}\" could not be found.", entitlementId));
        }

        Pool entPool = ent.getPool();
        if (!StringUtils.isBlank(entPool.getSourceStackId())) {
            /*
             * A derived pool originating from a stacked parent pool will have no subscription
             * ID as the pool is technically from many subscriptions. (i.e. all the
             * entitlements in the stack) In this case we must look up an active entitlement
             * in the hosts stack, and use this as our upstream certificate.
             */
            log.debug("Entitlement is from a stack derived pool, searching for oldest " +
                "active entitlements in source stack.");

            ent = entitlementCurator.findUpstreamEntitlementForStack(
                entPool.getSourceStack().getSourceConsumer(),
                entPool.getSourceStackId());
        }

        if (ent == null || ent.getPool() == null || ent.getPool().getCertificate() == null) {
            throw new NotFoundException(
                i18n.tr("Unable to find upstream certificate for entitlement: {0}", entitlementId)
            );
        }

        SubscriptionsCertificate cert = ent.getPool().getCertificate();
        return cert.getCert() + cert.getKey();
    }

    @Override
    public void unbind(String dbid) {
        Entitlement toDelete = entitlementCurator.get(dbid);
        if (toDelete != null) {
            poolManager.revokeEntitlement(toDelete);
            return;
        }
        throw new NotFoundException(
            i18n.tr("Entitlement with ID \"{0}\" could not be found.", dbid));
    }

    @Override
    public AsyncJobStatusDTO regenerateEntitlementCertificatesForProduct(
        String productId, Boolean lazyRegen) {

        JobConfig config = RegenProductEntitlementCertsJob.createJobConfig()
            .setProductId(productId)
            .setLazyRegeneration(lazyRegen);

        AsyncJobStatus status = null;
        try {
            status = this.jobManager.queueJob(config);
        }
        catch (JobException e) {
            e.printStackTrace();
        }
        return this.translator.translate(status, AsyncJobStatusDTO.class);
    }

    @Transactional
    @Override
    public Response migrateEntitlement(@Verify(Entitlement.class) String id,
        @Verify(Consumer.class) String uuid, Integer quantity) {
        // confirm entitlement
        Entitlement entitlement = entitlementCurator.get(id);
        List<Entitlement> entitlements = new ArrayList<>();

        if (entitlement != null) {
            if (quantity == null) {
                quantity = entitlement.getQuantity();
            }
            if (quantity > 0 && quantity <= entitlement.getQuantity()) {
                Consumer sourceConsumer = entitlement.getConsumer();
                Consumer destinationConsumer = consumerCurator.verifyAndLookupConsumer(uuid);

                ConsumerType scType = this.consumerTypeCurator.getConsumerType(sourceConsumer);
                ConsumerType dcType = this.consumerTypeCurator.getConsumerType(destinationConsumer);

                if (!scType.isManifest()) {
                    throw new BadRequestException(i18n.tr(
                        "Entitlement migration is not permissible for units of type \"{0}\"",
                        scType.getLabel()));
                }

                if (!dcType.isManifest()) {
                    throw new BadRequestException(i18n.tr(
                        "Entitlement migration is not permissible for units of type \"{0}\"",
                        dcType.getLabel()));
                }

                if (!sourceConsumer.getOwnerId().equals(destinationConsumer.getOwnerId())) {
                    throw new BadRequestException(i18n.tr(
                        "Source and destination units must belong to the same organization"));
                }

                // test to ensure destination can use the pool
                ValidationResult result = enforcer.preEntitlement(destinationConsumer,
                    entitlement.getPool(), 0, CallerType.BIND);

                if (!result.isSuccessful()) {
                    throw new BadRequestException(i18n.tr(
                        "The entitlement cannot be utilized by the destination unit: ") +
                        messageTranslator.poolErrorToMessage(entitlement.getPool(),
                            result.getErrors().get(0)));
                }

                if (quantity.intValue() == entitlement.getQuantity()) {
                    unbind(id);
                }
                else {
                    entitler.adjustEntitlementQuantity(sourceConsumer, entitlement,
                        entitlement.getQuantity() - quantity);
                }

                Pool pool = entitlement.getPool();
                entitlements.addAll(entitler.bindByPoolQuantity(destinationConsumer, pool.getId(), quantity));

                // Trigger events:
                entitler.sendEvents(entitlements);
            }
            else {
                throw new BadRequestException(i18n.tr(
                    "The quantity specified must be greater than zero " +
                    "and less than or equal to the total for this entitlement"));
            }
        }
        else {
            throw new NotFoundException(
                i18n.tr("Entitlement with ID \"{0}\" could not be found.", id));
        }

        List<EntitlementDTO> entitlementDTOs = new ArrayList<>();
        for (Entitlement entitlementModel : entitlements) {
            entitlementDTOs.add(this.translator.translate(entitlementModel, EntitlementDTO.class));
        }
        return Response.status(Response.Status.OK)
                .type(MediaType.APPLICATION_JSON).entity(entitlementDTOs).build();
    }

}
