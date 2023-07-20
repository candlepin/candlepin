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
package org.candlepin.resource;

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SecurityHole;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.Verify;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.PoolService;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.CdnDTO;
import org.candlepin.dto.api.server.v1.CertificateDTO;
import org.candlepin.dto.api.server.v1.EntitlementDTO;
import org.candlepin.dto.api.server.v1.PoolDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolFilterBuilder;
import org.candlepin.model.SubscriptionsCertificate;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.resource.server.v1.PoolsApi;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.util.Util;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.spi.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;



/**
 * API gateway for the EntitlementPool
 */
public class PoolResource implements PoolsApi {
    private static final Logger log = LoggerFactory.getLogger(PoolResource.class);

    private final ConsumerCurator consumerCurator;
    private final OwnerCurator ownerCurator;
    private final I18n i18n;
    private final PoolManager poolManager;
    private final PoolService poolService;
    private final CalculatedAttributesUtil calculatedAttributesUtil;
    private final ModelTranslator translator;
    private final PrincipalProvider principalProvider;

    @Inject
    public PoolResource(ConsumerCurator consumerCurator, OwnerCurator ownerCurator,
        I18n i18n, PoolManager poolManager, CalculatedAttributesUtil calculatedAttributesUtil,
        ModelTranslator translator, PrincipalProvider principalProvider, PoolService poolService) {

        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.i18n = Objects.requireNonNull(i18n);
        this.poolManager = Objects.requireNonNull(poolManager);
        this.calculatedAttributesUtil = Objects.requireNonNull(calculatedAttributesUtil);
        this.translator = Objects.requireNonNull(translator);
        this.principalProvider = Objects.requireNonNull(principalProvider);
        this.poolService = Objects.requireNonNull(poolService);
    }

    @Override
    @Deprecated
    @SecurityHole
    public List<PoolDTO> listPools(
        String ownerId,
        String consumerUuid,
        String productId,
        Boolean listAll,
        OffsetDateTime activeOn,
        Integer page, Integer perPage, String order, String sortBy) {
        Principal principal = this.principalProvider.get();
        PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);

        // Make sure we were given sane query parameters:
        if (consumerUuid != null && ownerId != null) {
            throw new BadRequestException(i18n.tr("Cannot filter on both owner and unit"));
        }
        if (consumerUuid == null && ownerId == null && productId != null) {
            throw new BadRequestException(i18n.tr("A unit or owner is needed to filter on product"));
        }

        Date activeOnDate = activeOn != null ?
            Util.toDate(activeOn) :
            (new Date());

        Consumer c = null;
        String oId = null;
        if (consumerUuid != null) {
            c = consumerCurator.findByUuid(consumerUuid);
            if (c == null) {
                throw new NotFoundException(i18n.tr("Unit: {0} not found", consumerUuid));
            }

            // Now that we have a consumer, check that this principal can access it:
            if (!principal.canAccess(c, SubResource.NONE, Access.READ_ONLY)) {
                throw new ForbiddenException(i18n.tr("User {0} cannot access unit {1}",
                    principal.getPrincipalName(), consumerUuid));
            }

            if (listAll) {
                oId = c.getOwnerId();
            }
        }

        if (ownerId != null) {
            Owner o = ownerCurator.secureGet(ownerId);
            if (o == null) {
                throw new NotFoundException(i18n.tr("owner: {0}", ownerId));
            }
            oId = o.getId();
            // Now that we have an owner, check that this principal can access it:
            if (!principal.canAccess(o, SubResource.POOLS, Access.READ_ONLY)) {
                throw new ForbiddenException(i18n.tr("User {0} cannot access owner {1}",
                    principal.getPrincipalName(), o.getKey()));
            }
        }

        // If we have no consumer, and no owner specified, kick 'em out unless they
        // have full system access (this is the same as requesting all pools in
        // the system).
        if (consumerUuid == null && ownerId == null && !principal.hasFullAccess()) {
            throw new ForbiddenException(i18n.tr("User {0} cannot access all pools.",
                principal.getPrincipalName()));
        }

        Page<List<Pool>> pageResponse = poolManager.listAvailableEntitlementPools(c, null, oId,
            productId, null, activeOnDate, listAll, new PoolFilterBuilder(), pageRequest,
            false, false, null);
        List<Pool> poolList = pageResponse.getPageData();

        calculatedAttributesUtil.setCalculatedAttributes(poolList, activeOnDate);
        calculatedAttributesUtil.setQuantityAttributes(poolList, c, activeOnDate);

        // Store the page for the LinkHeaderResponseFilter
        ResteasyContext.pushContext(Page.class, pageResponse);

        List<PoolDTO> poolDTOs = new ArrayList<>();
        for (Pool pool : poolList) {
            poolDTOs.add(translator.translate(pool, PoolDTO.class));
        }
        return poolDTOs;
    }

    @Override
    public PoolDTO getPool(@Verify(Pool.class) String id, String consumerUuid, OffsetDateTime activeOn) {

        Principal principal = this.principalProvider.get();
        Pool toReturn = this.poolService.get(id);

        Consumer c = null;
        if (consumerUuid != null) {
            c = consumerCurator.findByUuid(consumerUuid);
            if (c == null) {
                throw new NotFoundException(i18n.tr("consumer: {0} not found", consumerUuid));
            }

            if (!principal.canAccess(c, SubResource.NONE, Access.READ_ONLY)) {
                throw new ForbiddenException(i18n.tr("User {0} cannot access consumer {1}",
                    principal.getPrincipalName(), c.getUuid()));
            }
        }

        if (toReturn != null) {
            Date activeOnDate = Util.toDate(activeOn);
            if (activeOn == null) {
                activeOnDate = new Date();
            }

            toReturn.setCalculatedAttributes(
                calculatedAttributesUtil.buildCalculatedAttributes(toReturn, activeOnDate));

            calculatedAttributesUtil.setQuantityAttributes(toReturn, c, activeOnDate);

            return translator.translate(toReturn, PoolDTO.class);
        }

        throw new NotFoundException(i18n.tr("Subscription Pool with ID \"{0}\" could not be found.", id));
    }

    @Override
    public void deletePool(String id) {
        Pool pool = this.poolService.get(id);
        if (pool == null) {
            throw new NotFoundException(i18n.tr("Entitlement Pool with ID \"{0}\" could not be found.", id));
        }

        if (pool.getType() != PoolType.NORMAL) {
            throw new BadRequestException(i18n.tr("Cannot delete bonus pools, as they are auto generated"));
        }

        this.poolService.deletePools(Collections.singleton(pool));

        Owner owner = pool.getOwner();
        log.debug("Synchronizing last content update for org: {}", owner);
        owner.syncLastContentUpdate();
        this.ownerCurator.merge(owner);
    }

    @Override
    public CdnDTO getPoolCdn(@Verify(Pool.class) String id) {
        Pool pool = this.poolService.get(id);

        if (pool == null) {
            throw new NotFoundException(i18n.tr("Subscription Pool with ID \"{0}\" could not be found.", id));
        }

        return this.translator.translate(pool.getCdn(), CdnDTO.class);
    }

    @Override
    public List<String> listEntitledConsumerUuids(String id) {
        return poolManager.listEntitledConsumerUuids(id);
    }

    @Override
    public List<EntitlementDTO> getPoolEntitlements(
        @Verify(value = Pool.class, subResource = SubResource.ENTITLEMENTS) String id) {

        Pool pool = this.poolService.get(id);

        if (pool == null) {
            throw new NotFoundException(i18n.tr("Subscription Pool with ID \"{0}\" could not be found.", id));
        }

        List<EntitlementDTO> entitlementDTOs = new ArrayList<>();
        for (Entitlement entitlement : pool.getEntitlements()) {
            entitlementDTOs.add(this.translator.translate(entitlement, EntitlementDTO.class));
        }

        return entitlementDTOs;
    }

    /**
     * Retrieves the pool certificate for the given ID. If the pool
     * cannot be found or does not have a certificate, this method throws a NotFoundException.
     *
     * @param poolId
     *  The pool ID for which to retrieve a subscription certificate
     *
     * @throws NotFoundException
     *  if the pool cannot be found or the pool does not have a certificate
     *
     * @return
     *  the certificate associated with the specified pool
     */
    protected SubscriptionsCertificate getPoolCertificate(String poolId) {
        Pool pool = this.poolService.get(poolId);

        if (pool == null) {
            throw new NotFoundException(i18n.tr(
                "Pool with ID \"{0}\" could not be found.", poolId));
        }

        if (pool.getCertificate() == null) {
            throw new NotFoundException(
                i18n.tr("A certificate was not found for pool \"{0}\"", poolId));
        }

        return pool.getCertificate();
    }

    @Override
    public Object getSubCert(String poolId) {

        HttpRequest httpRequest = ResteasyContext.getContextData(HttpRequest.class);

        MediaType mediaType = httpRequest == null ? null :
            httpRequest.getHttpHeaders().getMediaType();

        if (mediaType != null && mediaType.equals(MediaType.TEXT_PLAIN_TYPE)) {
            SubscriptionsCertificate cert = this.getPoolCertificate(poolId);
            return cert.getCert() + cert.getKey();
        }

        return this.translator.translate(this.getPoolCertificate(poolId), CertificateDTO.class);
    }
}
