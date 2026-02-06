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
package org.candlepin.controller;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EntitlementFilterBuilder;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.js.entitlement.EntitlementRulesTranslator;
import org.candlepin.resource.dto.AutobindData;

import com.google.common.collect.Iterables;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;

import javax.inject.Inject;



public class Entitler {

    private static final Logger log = LoggerFactory.getLogger(Entitler.class);

    private final Configuration config;
    private final ConsumerCurator consumerCurator;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final EventFactory evtFactory;
    private final EventSink sink;
    private final EntitlementRulesTranslator messageTranslator;
    private final EntitlementCurator entitlementCurator;
    private final I18n i18n;
    private final OwnerCurator ownerCurator;
    private final PoolCurator poolCurator;
    private final PoolManager poolManager;
    private final PoolService poolService;

    @Inject
    public Entitler(PoolManager pm, PoolService poolService, ConsumerCurator cc, I18n i18n,
        EventFactory evtFactory, EventSink sink, EntitlementRulesTranslator messageTranslator,
        EntitlementCurator entitlementCurator, Configuration config,
        OwnerCurator ownerCurator, PoolCurator poolCurator, ConsumerTypeCurator ctc) {

        this.poolManager = Objects.requireNonNull(pm);
        this.poolService = Objects.requireNonNull(poolService);
        this.i18n = Objects.requireNonNull(i18n);
        this.evtFactory = Objects.requireNonNull(evtFactory);
        this.sink = Objects.requireNonNull(sink);
        this.consumerCurator = Objects.requireNonNull(cc);
        this.messageTranslator = Objects.requireNonNull(messageTranslator);
        this.entitlementCurator = Objects.requireNonNull(entitlementCurator);
        this.config = Objects.requireNonNull(config);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.poolCurator = Objects.requireNonNull(poolCurator);
        this.consumerTypeCurator = Objects.requireNonNull(ctc);
    }

    public List<Entitlement> bindByPoolQuantity(Consumer consumer, String poolId, Integer quantity) {
        Map<String, Integer> poolMap = new HashMap<>();
        poolMap.put(poolId, quantity);

        try {
            return bindByPoolQuantities(consumer, poolMap);
        }
        catch (EntitlementRefusedException e) {
            // TODO: Could be multiple errors, but we'll just report the first one for now
            Pool pool = poolCurator.get(poolId);
            throw new ForbiddenException(messageTranslator.poolErrorToMessage(
                pool, e.getResults().get(poolId).getErrors().get(0)), e);
        }
    }

    public List<Entitlement> bindByPoolQuantities(String consumerUuid,
        Map<String, Integer> poolIdAndQuantities) throws EntitlementRefusedException {

        Consumer c = consumerCurator.findByUuid(consumerUuid);
        return bindByPoolQuantities(c, poolIdAndQuantities);
    }

    public List<Entitlement> bindByPoolQuantities(Consumer consumer,
        Map<String, Integer> poolIdAndQuantities) throws EntitlementRefusedException {
        // Attempt to create entitlements:

        try {
            List<Entitlement> entitlementList = poolManager.entitleByPools(consumer, poolIdAndQuantities);
            log.debug("Created {} entitlements.", entitlementList.size());
            return entitlementList;
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    public void adjustEntitlementQuantity(Consumer consumer, Entitlement ent,
        Integer quantity) {
        // Attempt to adjust an entitlement:
        try {
            poolManager.adjustEntitlementQuantity(consumer, ent, quantity);
        }
        catch (EntitlementRefusedException e) {
            // TODO: Could be multiple errors, but we'll just report the first one for now:
            throw new ForbiddenException(messageTranslator.entitlementErrorToMessage(
                ent, e.getResults().values().iterator().next().getErrors().get(0)), e);
        }
    }

    public List<Entitlement> bindByProducts(Collection<String> productIds, String consumerUuid,
        Date entitleDate, Collection<String> fromPools)
        throws AutobindDisabledForOwnerException, AutobindHypervisorDisabledException {

        Consumer consumer = consumerCurator.findByUuid(consumerUuid);
        Owner owner = ownerCurator.findOwnerById(consumer.getOwnerId());

        AutobindData data = new AutobindData(consumer, owner)
            .on(entitleDate)
            .forProducts(productIds)
            .withPools(fromPools);

        return bindByProducts(data);
    }

    /**
     * Entitles the given Consumer to the given Product. Will seek out pools
     * which provide access to this product, either directly or as a child, and
     * select the best one based on a call to the rules engine.
     *
     * @param data AutobindData encapsulating data required for an autobind request
     * @return List of Entitlements
     * @throws AutobindDisabledForOwnerException when an autobind attempt is made and the owner
     *         has it disabled.
     * @throws AutobindHypervisorDisabledException when an autobind attempt is made on a hypervisor
     *         and the owner has it disabled.
     */
    public List<Entitlement> bindByProducts(AutobindData data)
        throws AutobindDisabledForOwnerException, AutobindHypervisorDisabledException {

        return bindByProducts(data, false);
    }

    /**
     *
     * Force option is used to heal entire org
     *
     * @param data AutobindData encapsulating data required for an autobind request
     * @param force heal host even if it has autoheal disabled
     * @return List of Entitlements
     * @throws AutobindDisabledForOwnerException when an autobind attempt is made and the owner
     *         has it disabled.
     * @throws AutobindHypervisorDisabledException when an autobind attempt is made on a hypervisor
     *         and the owner has it disabled.
     */
    @Transactional
    public List<Entitlement> bindByProducts(AutobindData data, boolean force)
        throws AutobindDisabledForOwnerException, AutobindHypervisorDisabledException {

        Consumer consumer = data.getConsumer();
        Owner owner = data.getOwner();
        ConsumerType type = this.consumerTypeCurator.getConsumerType(consumer);

        // Don't autobind if the org's autobind is entirely disabled
        if (owner.isAutobindDisabled()) {
            log.info("Auto-attach is disabled for org {}; skipping auto-attach for consumer {}",
                owner.getKey(), consumer.getUuid());
            throw new AutobindDisabledForOwnerException(this.i18n.tr(
                "Auto-attach is disabled for owner {0}", owner.getKey()));
        }

        // Don't autobind if the consumer is a hypervisor and hypervisor autobind is disabled in this org
        if (owner.isAutobindHypervisorDisabled() && ConsumerTypeEnum.HYPERVISOR.matches(type)) {
            log.info("Auto-attach is disabled for hypervisors of org {}; skipping auto-attach for " +
                "consumer {}", owner.getKey(), consumer.getUuid());
            throw new AutobindHypervisorDisabledException(this.i18n.tr(
                "Auto-attach is disabled for hypervisors of owner {0}", owner.getKey()));
        }

        // Don't autobind if the org is in SCA mode; but also don't fail?
        if (owner.isUsingSimpleContentAccess()) {
            log.info("Auto-attach is disabled for owner {} while using simple content access",
                owner.getKey());
        }

        return Collections.EMPTY_LIST;
    }

    public int revokeUnmappedGuestEntitlements(Consumer consumer) {
        int total = 0;

        List<Entitlement> unmappedGuestEntitlements = consumer != null ?
            entitlementCurator.findByPoolAttribute(consumer, "unmapped_guests_only", "true") :
            entitlementCurator.findByPoolAttribute("unmapped_guests_only", "true");

        List<Entitlement> entsToDelete = new LinkedList<>();

        for (Entitlement entitlement : unmappedGuestEntitlements) {
            if (!entitlement.isValid()) {
                entsToDelete.add(entitlement);
                ++total;
            }
        }

        if (!entsToDelete.isEmpty()) {
            for (List<Entitlement> ents : this.partition(entsToDelete)) {
                this.poolService.revokeEntitlements(ents);
            }
        }

        return total;
    }

    private Iterable<List<Entitlement>> partition(List<Entitlement> entsToDelete) {
        return Iterables.partition(entsToDelete, config.getInt(ConfigProperties.ENTITLER_BULK_SIZE));
    }

    public int revokeUnmappedGuestEntitlements() {
        return revokeUnmappedGuestEntitlements(null);
    }

    public void sendEvents(List<Entitlement> entitlements) {
        if (entitlements != null) {
            for (Entitlement entitlement : entitlements) {
                Event event = evtFactory.entitlementCreated(entitlement);
                sink.queueEvent(event);
            }
        }
    }
}
