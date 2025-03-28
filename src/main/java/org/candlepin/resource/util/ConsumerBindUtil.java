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
package org.candlepin.resource.util;

import org.candlepin.controller.AutobindDisabledForOwnerException;
import org.candlepin.controller.AutobindHypervisorDisabledException;
import org.candlepin.controller.Entitler;
import org.candlepin.dto.rules.v1.SuggestedQuantityDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverride;
import org.candlepin.model.ConsumerContentOverrideCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Release;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;
import org.candlepin.model.activationkeys.ActivationKeyPool;
import org.candlepin.policy.js.quantity.QuantityRules;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.util.ServiceLevelValidator;
import org.candlepin.version.CertVersionConflictException;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;



/**
 * Responsible for handling activation keys for the Consumer creation in {@link ConsumerResource}
 */
public class ConsumerBindUtil {

    private Entitler entitler;
    private I18n i18n;
    private ConsumerContentOverrideCurator consumerContentOverrideCurator;
    private OwnerCurator ownerCurator;
    private QuantityRules quantityRules;
    private ServiceLevelValidator serviceLevelValidator;
    private PoolCurator poolCurator;
    private static Logger log = LoggerFactory.getLogger(ConsumerBindUtil.class);

    @Inject
    public ConsumerBindUtil(Entitler entitler, I18n i18n,
        ConsumerContentOverrideCurator consumerContentOverrideCurator,
        OwnerCurator ownerCurator, QuantityRules quantityRules, ServiceLevelValidator serviceLevelValidator,
        PoolCurator poolCurator) {
        this.entitler = entitler;
        this.i18n = i18n;
        this.consumerContentOverrideCurator = consumerContentOverrideCurator;
        this.ownerCurator = ownerCurator;
        this.quantityRules = quantityRules;
        this.serviceLevelValidator = serviceLevelValidator;
        this.poolCurator = poolCurator;
    }

    public void handleActivationKeys(Consumer consumer, List<ActivationKey> keys,
        boolean autoattachDisabledForOwner)
        throws AutobindDisabledForOwnerException, AutobindHypervisorDisabledException {

        boolean listSuccess = false;
        boolean scaEnabledForAny = false;
        boolean isAutoheal = BooleanUtils.isTrue(consumer.isAutoheal());

        // we need to lock all the pools in id order so that it won't deadlock if there are other
        // processes in the same space at the same time. Current code sorts and locks
        // per activation key which can lead to this deadlock when entitlement revocation is
        // occurring on the same pools
        Set<Pool> akPools = keys.stream()
            .filter(Objects::nonNull)
            .flatMap(key -> key.getPools().stream())
            .map(ActivationKeyPool::getPool)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        poolCurator.lock(akPools);

        for (ActivationKey key : keys) {
            boolean keySuccess = true;
            boolean scaEnabled = key.getOwner().isUsingSimpleContentAccess();
            scaEnabledForAny |= scaEnabled;

            keySuccess &= handleActivationKeyServiceLevel(consumer, key.getServiceLevel(), key.getOwner());
            handleActivationKeyOverrides(consumer, key.getContentOverrides());
            handleActivationKeyRelease(consumer, key.getReleaseVer());
            handleActivationKeyUsage(consumer, key.getUsage());
            handleActivationKeyRole(consumer, key.getRole());
            handleActivationKeyAddons(consumer, key.getAddOns());

            if (Boolean.TRUE.equals(key.isAutoAttach())) {
                if (autoattachDisabledForOwner) {
                    log.warn("Auto-attach disabled for owner; skipping auto-attach for consumer with " +
                        "activation key: {}, {}", consumer.getUuid(), key.getName());
                }
                else if (scaEnabled) {
                    log.warn("Owner is using simple content access; skipping auto-attach for consumer with " +
                        "activation key: {}, {}", consumer.getUuid(), key.getName());
                }
                else if (!isAutoheal) {
                    log.warn("Auto-heal disabled for consumer; skipping auto-attach for consumer with " +
                        "activation key: {}, {}", consumer.getUuid(), key.getName());
                }
                else {
                    // State checks passed, perform auto-attach
                    this.handleActivationKeyAutoBind(consumer, key);
                }
            }
            else {
                // In SCA mode, attaching specific pools is no longer supported for smoother transition.
                // Instead, log an informational message and skip pool attachment.
                if (scaEnabled) {
                    log.warn("Owner is using simple content access; skipping attaching pools for consumer " +
                        "with activation key: {}, {}", consumer.getUuid(), key.getName());
                }
                else {
                    keySuccess &= handleActivationKeyPools(consumer, key);
                }
            }

            listSuccess |= keySuccess;
        }

        if (!listSuccess && !scaEnabledForAny) {
            throw new BadRequestException(
                i18n.tr("None of the subscriptions on the activation key were available for attaching."));
        }
    }

    private boolean handleActivationKeyPools(Consumer consumer, ActivationKey key) {
        if (key.getPools().size() == 0) {
            return true;
        }

        boolean onePassed = false;
        List<ActivationKeyPool> toBind = new LinkedList<>();
        for (ActivationKeyPool akp : key.getPools()) {
            if (akp.getPool().getId() != null) {
                toBind.add(akp);
            }
        }

        // Sort pools before binding to avoid deadlocks
        Collections.sort(toBind);
        for (ActivationKeyPool akp : toBind) {
            int quantity = (akp.getQuantity() == null) ?
                getQuantityToBind(akp.getPool(), consumer) :
                akp.getQuantity().intValue();

            try {
                entitler.sendEvents(entitler.bindByPoolQuantity(consumer, akp.getPool().getId(), quantity));
                onePassed = true;
            }
            catch (ForbiddenException e) {
                log.warn(i18n.tr("Cannot bind to pool \"{0}\" in activation key \"{1}\": {2}",
                    akp.getPool().getId(), akp.getKey().getName(), e.getMessage()), e);
            }
        }

        return onePassed;
    }

    private void handleActivationKeyAutoBind(Consumer consumer, ActivationKey key)
        throws AutobindDisabledForOwnerException, AutobindHypervisorDisabledException {

        try {
            Set<String> productIds = new HashSet<>();
            Set<String> poolIds = key.getPools().stream()
                .map(ActivationKeyPool::getPoolId)
                .collect(Collectors.toSet());

            if (key.getProductIds() != null) {
                productIds.addAll(key.getProductIds());
            }

            for (ConsumerInstalledProduct cip : consumer.getInstalledProducts()) {
                productIds.add(cip.getProductId());
            }

            Owner owner = this.ownerCurator.findOwnerById(consumer.getOwnerId());
            AutobindData autobindData = new AutobindData(consumer, owner)
                .forProducts(productIds)
                .withPools(poolIds);

            List<Entitlement> ents = entitler.bindByProducts(autobindData);
            entitler.sendEvents(ents);
        }
        catch (ForbiddenException | CertVersionConflictException e) {
            throw e;
        }
        catch (RuntimeException e) {
            log.warn("Unable to attach a subscription for a product that " +
                "has no pool: " + e.getMessage(), e);
        }
    }

    private void handleActivationKeyOverrides(Consumer consumer,
        Set<ActivationKeyContentOverride> keyOverrides) {

        Map<String, Map<String, ConsumerContentOverride>> overrideMap = new HashMap<>();
        List<ConsumerContentOverride> existing = this.consumerContentOverrideCurator.getList(consumer);

        // Map the existing overrides
        for (ConsumerContentOverride override : existing) {
            String contentLabel = override.getContentLabel();
            String attrib = override.getName();

            // Impl note: content labels are case sensitive, but attribute names are *not*.
            overrideMap.computeIfAbsent(contentLabel, key -> new HashMap<>())
                .put(attrib.toLowerCase(), override);
        }

        // Create or update new entries from the activation key overrides
        for (ActivationKeyContentOverride override : keyOverrides) {
            String contentLabel = override.getContentLabel();
            String attrib = override.getName();

            overrideMap.computeIfAbsent(contentLabel, key -> new HashMap<>())
                .computeIfAbsent(attrib.toLowerCase(), key -> override.buildConsumerContentOverride(consumer))
                .setValue(override.getValue());
        }

        // Persist the changes
        overrideMap.values()
            .stream()
            .map(Map::values)
            .flatMap(Collection::stream)
            .forEach(this.consumerContentOverrideCurator::saveOrUpdate);
    }

    private void handleActivationKeyRelease(Consumer consumer, Release release) {
        String relVerString = release.getReleaseVer();
        if (relVerString != null && !relVerString.isEmpty()) {
            consumer.setReleaseVer(release);
        }
    }

    private boolean handleActivationKeyServiceLevel(Consumer consumer, String level, Owner owner) {
        if (!StringUtils.isBlank(level)) {
            try {
                serviceLevelValidator.validate(owner.getId(), level);
                consumer.setServiceLevel(level);
                return true;
            }
            catch (BadRequestException e) {
                log.warn(e.getMessage(), e);
                return false;
            }
        }
        else {
            return true;
        }
    }

    private void handleActivationKeyUsage(Consumer consumer, String usage) {
        if (usage != null && !usage.isEmpty()) {
            consumer.setUsage(usage);
        }
    }

    private void handleActivationKeyRole(Consumer consumer, String role) {
        if (role != null && !role.isEmpty()) {
            consumer.setRole(role);
        }
    }

    private void handleActivationKeyAddons(Consumer consumer, Set<String> addOns) {
        if (addOns != null && !addOns.isEmpty()) {
            Set<String> newAddOns = new HashSet<>();
            newAddOns.addAll(addOns);
            consumer.setAddOns(newAddOns);
        }
    }

    public int getQuantityToBind(Pool pool, Consumer consumer) {
        Date now = new Date();
        // If the pool is being attached in the future, calculate
        // suggested quantity on the start date
        Date onDate = now.before(pool.getStartDate()) ?
            pool.getStartDate() :
            now;
        SuggestedQuantityDTO suggested = quantityRules.getSuggestedQuantity(pool,
            consumer, onDate);
        int quantity = Math.max(suggested.getIncrement().intValue(),
            suggested.getSuggested().intValue());
        // It's possible that increment is greater than the number available
        // but whatever we do here, the bind will fail
        return quantity;
    }

    public void validateServiceLevel(String ownerId, String serviceLevel) {
        serviceLevelValidator.validate(ownerId, serviceLevel);
    }

}
