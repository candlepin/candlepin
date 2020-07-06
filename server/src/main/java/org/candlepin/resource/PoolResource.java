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

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.Verify;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.controller.PoolManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.CdnDTO;
import org.candlepin.dto.api.v1.CertificateDTO;
import org.candlepin.dto.api.v1.EntitlementDTO;
import org.candlepin.dto.api.v1.PoolDTO;
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
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ResourceDateParser;

import com.google.inject.Inject;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.ws.rs.core.MediaType;

/**
 * API gateway for the EntitlementPool
 */

public class PoolResource implements PoolsApi {
    private static Logger log = LoggerFactory.getLogger(PoolResource.class);

    private ConsumerCurator consumerCurator;
    private OwnerCurator ownerCurator;
    private I18n i18n;
    private PoolManager poolManager;
    private CalculatedAttributesUtil calculatedAttributesUtil;
    private ModelTranslator translator;
    private PrincipalProvider principalProvider;

    @Inject
    public PoolResource(ConsumerCurator consumerCurator, OwnerCurator ownerCurator,
        I18n i18n, PoolManager poolManager, CalculatedAttributesUtil calculatedAttributesUtil,
        ModelTranslator translator, PrincipalProvider principalProvider) {

        this.consumerCurator = consumerCurator;
        this.ownerCurator = ownerCurator;
        this.i18n = i18n;
        this.poolManager = poolManager;
        this.calculatedAttributesUtil = calculatedAttributesUtil;
        this.translator = translator;
        this.principalProvider = principalProvider;
    }

    @Override
    @Deprecated
    @SecurityHole
    public List<PoolDTO> list(String ownerId, String consumerUuid, String productId,
        Boolean listAll, String activeOn) {

        Principal principal = this.principalProvider.get();
        PageRequest pageRequest = ResteasyProviderFactory.getContextData(PageRequest.class);

        // Make sure we were given sane query parameters:
        if (consumerUuid != null && ownerId != null) {
            throw new BadRequestException(i18n.tr("Cannot filter on both owner and unit"));
        }
        if (consumerUuid == null && ownerId == null && productId != null) {
            throw new BadRequestException(i18n.tr("A unit or owner is needed to filter on product"));
        }

        Date activeOnDate = activeOn != null ?
            ResourceDateParser.parseDateString(activeOn) :
            new Date();

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

            if (listAll.booleanValue()) {
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

        Page<List<Pool>> page = poolManager.listAvailableEntitlementPools(c, null, oId,
            productId, null, activeOnDate, listAll.booleanValue(), new PoolFilterBuilder(), pageRequest,
            false, false, null);
        List<Pool> poolList = page.getPageData();

        calculatedAttributesUtil.setCalculatedAttributes(poolList, activeOnDate);
        calculatedAttributesUtil.setQuantityAttributes(poolList, c, activeOnDate);

        // Store the page for the LinkHeaderResponseFilter
        ResteasyContext.pushContext(Page.class, page);

        List<PoolDTO> poolDTOs = new ArrayList<>();
        for (Pool pool : poolList) {
            poolDTOs.add(translator.translate(pool, PoolDTO.class));
        }
        return poolDTOs;
    }

    @Override
    public PoolDTO getPool(@Verify(Pool.class) String id, String consumerUuid, String activeOn) {

        Principal principal = this.principalProvider.get();
        Pool toReturn = poolManager.get(id);

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
            Date activeOnDate = new Date();
            if (activeOn != null) {
                activeOnDate = ResourceDateParser.parseDateString(activeOn);
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
        Pool pool = poolManager.get(id);
        if (pool == null) {
            throw new NotFoundException(i18n.tr("Entitlement Pool with ID \"{0}\" could not be found.", id));
        }

        if (pool.getType() != PoolType.NORMAL) {
            throw new BadRequestException(i18n.tr("Cannot delete bonus pools, as they are auto generated"));
        }

        poolManager.deletePools(Collections.singleton(pool));

        Owner owner = pool.getOwner();
        log.debug("Synchronizing last content update for org: {}", owner);
        owner.syncLastContentUpdate();
        this.ownerCurator.merge(owner);
    }

    @Override
    public CdnDTO getPoolCdn(@Verify(Pool.class) String id) {

        Pool pool = poolManager.get(id);

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

        Pool pool = poolManager.get(id);

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
        Pool pool = poolManager.get(poolId);

        if (pool == null) {
            throw new NotFoundException(i18n.tr(
                "Pool with ID \"{0}\" could not be found.", poolId));
        }

        if (pool.getCertificate() == null) {
            throw new NotFoundException(
                i18n.tr("A certificate was not found for pool \"{0}\"", poolId)
            );
        }

        return pool.getCertificate();
    }

    @Override
    public Object getSubCert(String poolId) {

        HttpRequest httpRequest = ResteasyProviderFactory.getContextData(HttpRequest.class);

        MediaType mediaType = httpRequest == null ? null :
            httpRequest.getHttpHeaders().getMediaType();

        if (mediaType != null && mediaType.equals(MediaType.TEXT_PLAIN_TYPE)) {
            SubscriptionsCertificate cert = this.getPoolCertificate(poolId);
            return cert.getCert() + cert.getKey();
        }

        return this.translator.translate(this.getPoolCertificate(poolId), CertificateDTO.class);
    }
}
