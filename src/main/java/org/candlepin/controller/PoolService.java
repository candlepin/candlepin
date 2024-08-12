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
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.policy.SystemPurposeComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.pool.PoolHelper;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.policy.js.pool.PoolUpdate;
import org.candlepin.util.Traceable;

import com.google.common.collect.Iterables;
import com.google.inject.persist.Transactional;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;



public class PoolService {

    private static final Logger log = LoggerFactory.getLogger(PoolService.class);
    private static final long UNLIMITED_QUANTITY = -1L;

    private final I18n i18n;
    private final PoolCurator poolCurator;
    private final EventSink sink;
    private final EventFactory eventFactory;
    private final PoolRules poolRules;
    private final EntitlementCurator entitlementCurator;
    private final ConsumerCurator consumerCurator;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final EntitlementCertificateCurator entitlementCertificateCurator;
    private final ComplianceRules complianceRules;
    private final SystemPurposeComplianceRules systemPurposeComplianceRules;
    private final Configuration config;

    @Inject
    public PoolService(
        PoolCurator poolCurator,
        EventSink sink,
        EventFactory eventFactory,
        PoolRules poolRules,
        EntitlementCurator entitlementCurator,
        ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        EntitlementCertificateCurator entitlementCertCurator,
        ComplianceRules complianceRules,
        SystemPurposeComplianceRules systemPurposeComplianceRules,
        Configuration configuration,
        I18n i18n) {

        this.poolCurator = Objects.requireNonNull(poolCurator);
        this.sink = Objects.requireNonNull(sink);
        this.eventFactory = Objects.requireNonNull(eventFactory);
        this.entitlementCurator = Objects.requireNonNull(entitlementCurator);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.poolRules = Objects.requireNonNull(poolRules);
        this.entitlementCertificateCurator = Objects.requireNonNull(entitlementCertCurator);
        this.complianceRules = Objects.requireNonNull(complianceRules);
        this.systemPurposeComplianceRules = Objects.requireNonNull(systemPurposeComplianceRules);
        this.i18n = Objects.requireNonNull(i18n);
        this.config = Objects.requireNonNull(configuration);
    }

    public Pool get(String poolId) {
        return this.poolCurator.get(poolId);
    }

    public Pool createPool(Pool pool) {
        return this.createPool(pool, true);
    }

    public Pool createPool(Pool pool, boolean flush) {
        if (pool == null) {
            return null;
        }

        // We're assuming that net-new pools will not yet have an ID
        if (pool.getId() == null) {
            pool = this.poolCurator.create(pool, flush);
            log.debug("  created pool: {}", pool);

            if (pool != null) {
                sink.emitPoolCreated(pool);
            }
        }
        else {
            pool = this.poolCurator.merge(pool);
            log.debug("  updated pool: {}", pool);
        }

        return pool;
    }

    public List<Pool> listPoolsByOwner(Owner owner) {
        return poolCurator.listByOwner(owner);
    }

    /**
     * Cleanup entitlements and safely delete the given pool.
     *
     * @param pool
     */
    @Transactional
    public void deletePool(Pool pool) {
        Event event = eventFactory.poolDeleted(pool);

        // Must do a full revoke for all entitlements:
        for (Entitlement e : poolCurator.entitlementsIn(pool)) {
            e.setDeletedFromPool(true);
            revokeEntitlement(e);
        }

        poolCurator.delete(pool);
        sink.queueEvent(event);
    }

    public void deletePools(Collection<Pool> pools) {
        this.deletePools(pools, null);
    }

    @Transactional
    @Traceable
    @SuppressWarnings("checkstyle:methodlength")
    public void deletePools(Collection<Pool> pools, Collection<String> alreadyDeletedPoolIds) {
        if (pools == null || pools.isEmpty()) {
            return;
        }

        log.info("Attempting to delete {} pools...", pools.size());

        // TODO: Remove this and fix the bugs it works around. We absolutely should not be
        // passing state through the various codepaths like this. It makes things far messier
        // than they need to be and is resulting in running slow calculations multiple times.
        if (alreadyDeletedPoolIds == null) {
            alreadyDeletedPoolIds = new HashSet<>();
        }

        Set<String> poolIds = new HashSet<>();
        Set<String> entitlementIds = new HashSet<>();
        Owner owner = null;

        // Convert pools to pool IDs.
        log.info("Fetching related pools and entitlements...");
        for (Pool pool : pools) {
            if (owner == null) {
                owner = pool.getOwner();
            }

            poolIds.add(pool.getId());
        }

        // Fetch pools which are derived from the pools we're going to delete...
        poolIds.addAll(this.poolCurator.getDerivedPoolIdsForPools(poolIds));

        // Fetch related pools and entitlements (recursively)
        Collection<String> pids = poolIds;
        int cachedSize;
        do {
            // Fetch entitlement IDs for our set of pools
            Collection<String> eids = this.poolCurator.getEntitlementIdsForPools(pids);

            // Fetch pools which are derived from these entitlements...
            pids = this.poolCurator.getPoolIdsForSourceEntitlements(eids);

            // Fetch stack derived pools which will be unentitled when we revoke entitlements
            // Impl note: This may occassionally miss stack derived pools in cases where our
            // entitlement count exceeds the IN block limitations. In those cases, we'll end
            // up doing a recursive call into this method, which sucks, but will still work.
            pids.addAll(this.poolCurator.getUnentitledStackDerivedPoolIds(eids));

            // Fetch pools which are derived from the pools we're going to delete...
            pids.addAll(this.poolCurator.getDerivedPoolIdsForPools(pids));

            // Add the new entitlement and pool IDs to our list of things to delete
            cachedSize = poolIds.size();
            entitlementIds.addAll(eids);
            poolIds.addAll(pids);
        }
        while (poolIds.size() != cachedSize);

        // If we've been provided a collection of already-deleted pool IDs, remove those from
        // the list so we don't try to delete them again.
        // TODO: Remove this and stop recursively calling into this method.
        if (alreadyDeletedPoolIds != null) {
            poolIds.removeAll(alreadyDeletedPoolIds);
        }

        // Lock pools we're going to delete (also, fetch them for event generation/slow deletes)
        pools = this.poolCurator.lockAndLoad(poolIds);

        if (!pools.isEmpty()) {
            log.info("Locked {} pools for deletion...", pools.size());

            // Impl note:
            // There is a fair bit of duplicated work between the actions below this block and
            // methods like revokeEntitlements. However, the decision was made to decouple these
            // methods explicitly to avoid situations such as fetching collections of pools, getting
            // entitlements from them (a slow process in itself) and then passing it off to another
            // standalone method which repeats the process of fetching pools and related entitlements.
            //
            // More work can be done in revokeEntitlements to optimize that method and maybe make it
            // slightly more generic so that this work can be offloaded to it again. Though, at the time
            // of writing, that's no small undertaking. Even changing this method has far-reaching
            // consequences when trying to remove direct uses of entities as far as interoperability is
            // concerned. Going forward we need to be more aware of the amount of duplication we're
            // adding to our code when writing standlone/generic utility methods and linking them
            // together, and perhaps take steps to avoid getting into situations like these two methods.

            // Fetch the list of pools which are related to the entitlements but are *not* being
            // deleted. We'll need to update the quantities on these.
            Collection<String> affectedPoolIds = this.poolCurator.getPoolIdsForEntitlements(entitlementIds);
            affectedPoolIds.removeAll(poolIds);

            // Fetch entitlements (uggh).
            // TODO: Stop doing this. Update the bits below to not use the entities directly and
            // do the updates via queries.
            // Impl note: we have to fetch these in blocks to guard against the case where we're
            // attempting to fetch more entitlements than the parameter limit allows (~32k).
            Set<Entitlement> entitlements = new HashSet<>();

            if (!entitlementIds.isEmpty()) {
                Iterable<List<String>> blocks = Iterables.partition(entitlementIds,
                    this.entitlementCurator.getInBlockSize());

                for (List<String> block : blocks) {
                    entitlements.addAll(this.entitlementCurator.listAllByIds(block));
                }
            }

            // Mark remaining dependent entitlements dirty for this consumer
            this.entitlementCurator.markDependentEntitlementsDirty(entitlementIds);

            // Unlink the pools and entitlements we're about to delete so we don't error out while
            // trying to delete entitlements.
            this.poolCurator.clearPoolSourceEntitlementRefs(poolIds);

            // Revoke/delete entitlements
            if (!entitlements.isEmpty()) {
                log.info("Revoking {} entitlements...", entitlements.size());
                this.entitlementCurator.unlinkEntitlements(entitlements);
                this.entitlementCertificateCurator.deleteByEntitlementIds(entitlementIds);
                this.entitlementCurator.batchDeleteByIds(entitlementIds);
                this.entitlementCurator.flush();
                this.entitlementCurator.batchDetach(entitlements);
                log.info("Entitlements successfully revoked");
            }
            else {
                log.info("Skipping entitlement revocation; no entitlements to revoke");
            }

            // Delete pools
            log.info("Deleting {} pools...", pools.size());
            this.poolCurator.batchDelete(pools, alreadyDeletedPoolIds);
            this.poolCurator.flush();
            log.info("Pools successfully deleted");

            if (!entitlements.isEmpty()) {
                // Update entitlement counts on affected, non-deleted pools
                log.info("Updating entitlement counts on remaining, affected pools...");
                Map<Consumer, List<Entitlement>> consumerStackedEnts = new HashMap<>();
                List<Pool> poolsToSave = new LinkedList<>();
                Set<String> stackIds = new HashSet<>();

                for (Entitlement entitlement : entitlements) {
                    // Since we're sifting through these already, let's also sort them into consumer lists
                    // for some of the other methods we'll be calling later
                    Consumer consumer = entitlement.getConsumer();
                    Pool pool = entitlement.getPool();

                    List<Entitlement> stackedEntitlements = consumerStackedEnts
                        .computeIfAbsent(consumer, k -> new LinkedList<>());

                    if (!pool.isDerived() &&
                        pool.hasProductAttribute(Product.Attributes.STACKING_ID)) {

                        stackedEntitlements.add(entitlement);
                        stackIds.add(entitlement.getPool().getStackId());
                    }

                    // Update quantities if the entitlement quantity is non-zero
                    int quantity = entitlement.getQuantity() != null ? entitlement.getQuantity() : 0;
                    if (quantity != 0) {
                        // Update the pool quantities if we didn't delete it
                        if (affectedPoolIds.contains(pool.getId())) {
                            pool.setConsumed(pool.getConsumed() - quantity);
                            poolsToSave.add(pool);
                        }

                        // Update entitlement counts for affected consumers...
                        consumer.setEntitlementCount(consumer.getEntitlementCount() - quantity);

                        // Set the number exported if we're working with a manifest distributor
                        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);
                        if (ctype != null && ctype.isManifest()) {
                            pool.setExported(pool.getExported() - quantity);
                        }
                    }
                }

                this.poolCurator.updateAll(poolsToSave, false, false);
                this.consumerCurator.updateAll(consumerStackedEnts.keySet(), false, false);
                this.consumerCurator.flush();

                log.info("Entitlement counts successfully updated for {} pools and {} consumers",
                    poolsToSave.size(), consumerStackedEnts.size());

                // Update stacked entitlements for affected consumers(???)
                if (!stackIds.isEmpty()) {
                    // Get consumer + pool tuples for stack ids
                    Map<String, Set<String>> consumerStackDerivedPoolIds = this.poolCurator
                        .getConsumerStackDerivedPoolIdMap(stackIds);

                    if (!consumerStackDerivedPoolIds.isEmpty()) {
                        log.info("Updating stacked entitlements for {} consumers...",
                            consumerStackDerivedPoolIds.size());

                        for (Consumer consumer : consumerStackedEnts.keySet()) {
                            Set<String> subPoolIds = consumerStackDerivedPoolIds.get(consumer.getId());

                            if (subPoolIds != null && !subPoolIds.isEmpty()) {
                                // Resolve pool IDs...
                                Collection<Pool> subPools = this.poolCurator.listAllByIds(subPoolIds);

                                // Invoke the rules engine to update the affected pools
                                if (subPools != null && !subPools.isEmpty()) {
                                    log.debug("Updating {} stacking pools for consumer: {}",
                                        subPools.size(), consumer);

                                    this.updatePoolsFromStack(
                                        consumer, subPools, null, alreadyDeletedPoolIds, true);
                                }
                            }
                        }
                    }
                }

                this.consumerCurator.flush();

                // Fire post-unbind events for revoked entitlements
                log.info("Firing post-unbind events for {} entitlements...", entitlements.size());
                entitlements.forEach(this::postUnbind);

                log.info("Recomputing status for {} consumers", consumerStackedEnts.keySet().size());

                // Recalculate status for affected consumers
                for (List<Consumer> subList : Iterables.partition(consumerStackedEnts.keySet(), 1000)) {
                    for (Consumer consumer : subList) {
                        this.complianceRules.getStatus(consumer);
                        this.systemPurposeComplianceRules.getStatus(consumer, consumer.getEntitlements(),
                            null, true);

                        // Detach the consumer object (and its children that receive cascaded detaches),
                        // otherwise during the status calculations, the facts proxy objects objects will be
                        // resolved and the memory use will grow linearly with the number of consumers
                        // instead of remaining constant as we calculate the status of each consumer.
                        //
                        // See BZ 1584259 for details
                        this.consumerCurator.detach(consumer);
                    }
                    this.consumerCurator.flush();
                }

                log.info("All statuses recomputed");
            }

            // Impl note:
            // We don't need to fire entitlement revocation events, since they're all being revoked as
            // a consequence of the pools being deleted.

            // Fire pool deletion events
            // This part hurts so much. Because we output the whole entity, we have to fetch the bloody
            // things before we delete them.
            log.info("Firing pool deletion events for {} pools...", pools.size());
            for (Pool pool : pools) {
                this.sink.queueEvent(this.eventFactory.poolDeleted(pool));
            }
        }
        else {
            log.info("Skipping pool deletion; no pools to delete");
        }
    }

    /**
     * Run post-entitlement actions.
     *
     * @param ent
     *  The entitlement that needs to be revoked
     */
    public void postUnbind(Entitlement ent) {
        Pool pool = ent.getPool();

        // Can this attribute appear on pools?
        if (pool.hasAttribute(Product.Attributes.VIRT_LIMIT) ||
            pool.getProduct().hasAttribute(Product.Attributes.VIRT_LIMIT)) {

            Map<String, String> attributes = PoolHelper.getFlattenedAttributes(pool);
            Consumer consumer = ent.getConsumer();
            postUnbindVirtLimit(ent, pool, consumer, attributes);
        }
    }

    private void postUnbindVirtLimit(Entitlement entitlement, Pool pool,
        Consumer consumer, Map<String, String> attributes) {

        log.debug("Running virt_limit post unbind.");

        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

        boolean hostLimited = "true".equals(attributes.get(Product.Attributes.HOST_LIMITED));

        if (!config.getBoolean(ConfigProperties.STANDALONE) && !hostLimited && ctype.isManifest()) {
            // We're making an assumption that VIRT_LIMIT is defined the same way in every possible
            // source for the attributes map.
            String virtLimit = attributes.get(Product.Attributes.VIRT_LIMIT);

            if (!"unlimited".equals(virtLimit)) {
                /*
                 * Case I As we have unbound an entitlement from a physical pool that was previously exported,
                 * we need to add back the reduced bonus pool quantity.
                 *
                 * Case II If Primary pool quantity is unlimited, with non-zero virt_limit & pool under
                 * consideration is of type Unmapped guest or Bonus pool, set its quantity to be unlimited.
                 */
                int virtQuantity = Integer.parseInt(virtLimit) * entitlement.getQuantity();
                if (virtQuantity > 0) {
                    List<Pool> pools = this.getBySubscriptionId(pool.getOwner(), pool.getSubscriptionId());
                    boolean isPrimaryPoolUnlimited = isPrimaryPoolUnlimited(pools);

                    for (Pool derivedPool : pools) {
                        if (derivedPool.getAttributeValue(Pool.Attributes.DERIVED_POOL) != null) {
                            if (isPrimaryPoolUnlimited && (derivedPool.getType() == Pool.PoolType.BONUS ||
                                derivedPool.getType() == Pool.PoolType.UNMAPPED_GUEST)) {
                                // Set pool quantity to be unlimited.
                                this.setPoolQuantity(derivedPool, UNLIMITED_QUANTITY);
                            }
                            else {
                                this.setPoolQuantity(derivedPool,
                                    derivedPool.adjustQuantity(virtQuantity));
                            }
                        }
                    }
                }
            }
            else {
                // As we have unbound an entitlement from a physical pool that
                // was previously
                // exported, we need to set the unlimited bonus pool quantity to
                // -1.
                List<Pool> pools = this.getBySubscriptionId(pool.getOwner(),
                    pool.getSubscriptionId());
                for (Pool derivedPool : pools) {
                    if (derivedPool.getAttributeValue(Pool.Attributes.DERIVED_POOL) != null &&
                        derivedPool.getQuantity() == 0) {

                        this.setPoolQuantity(derivedPool, -1);
                    }
                }
            }
        }
    }

    public List<Pool> getBySubscriptionId(Owner owner, String id) {
        return this.poolCurator.getBySubscriptionId(owner, id);
    }

    public List<Pool> getBySubscriptionIds(String ownerId, Collection<String> subscriptionIds) {
        if (CollectionUtils.isNotEmpty(subscriptionIds)) {
            return this.poolCurator.getBySubscriptionIds(ownerId, subscriptionIds);
        }

        return new ArrayList<>();
    }

    /**
     * Updates the pool based on the entitlements in the specified stack.
     *
     * @param pools
     * @param consumer
     * @return updates
     */
    public List<PoolUpdate> updatePoolsFromStack(Consumer consumer, Collection<Pool> pools,
        Collection<Entitlement> newEntitlements, Collection<String> alreadyDeletedPools,
        boolean deleteIfNoStackedEnts) {

        Set<String> sourceStackIds = pools.stream()
            .map(Pool::getSourceStackId)
            .collect(Collectors.toSet());

        List<Entitlement> allEntitlements = this.entitlementCurator.findByStackIds(consumer, sourceStackIds);
        if (CollectionUtils.isNotEmpty(newEntitlements)) {
            allEntitlements.addAll(newEntitlements);
        }

        Map<String, List<Entitlement>> entitlementMap = new HashMap<>();
        for (Entitlement entitlement : allEntitlements) {
            List<Entitlement> ents = entitlementMap
                .computeIfAbsent(entitlement.getPool().getStackId(), k -> new ArrayList<>());
            ents.add(entitlement);
        }

        List<PoolUpdate> result = new ArrayList<>();
        List<Pool> poolsToDelete = new ArrayList<>();
        for (Pool pool : pools) {
            List<Entitlement> entitlements = entitlementMap.get(pool.getSourceStackId());
            if (CollectionUtils.isNotEmpty(entitlements)) {
                result.add(this.poolRules.updatePoolFromStackedEntitlements(pool, entitlements,
                    Collections.emptyMap()));
            }
            else if (deleteIfNoStackedEnts) {
                poolsToDelete.add(pool);
            }
        }

        if (!poolsToDelete.isEmpty()) {
            this.deletePools(poolsToDelete, alreadyDeletedPools);
        }

        return result;
    }

    /**
     * Set the count of a pool. The caller sets the absolute quantity. Current use is setting unlimited
     * bonus pool to -1 or 0.
     *
     * @param pool
     *  The pool.
     * @param set
     *  the long amount to set
     * @return pool
     */
    public Pool setPoolQuantity(Pool pool, long set) {
        this.poolCurator.lock(pool);
        pool.setQuantity(set);
        return poolCurator.merge(pool);
    }

    @Transactional
    public void revokeEntitlement(Entitlement entitlement) {
        revokeEntitlements(Collections.singletonList(entitlement));
    }

    @Transactional
    public int revokeAllEntitlements(Consumer consumer) {
        return revokeAllEntitlements(consumer, true);
    }

    @Transactional
    public int revokeAllEntitlements(Consumer consumer, boolean regenCertsAndStatuses) {
        List<Entitlement> entsToDelete = entitlementCurator.listByConsumer(consumer);
        revokeEntitlements(entsToDelete, null, regenCertsAndStatuses);
        return entsToDelete.size();
    }

    @Transactional
    public int revokeAllEntitlements(Collection<String> consumerUuid, boolean regenCertsAndStatuses) {
        List<Entitlement> entsToDelete = entitlementCurator.listByConsumerUuids(consumerUuid);
        revokeEntitlements(entsToDelete, null, regenCertsAndStatuses);
        return entsToDelete.size();
    }

    @Transactional
    public Set<Pool> revokeEntitlements(List<Entitlement> entsToRevoke) {
        return revokeEntitlements(entsToRevoke, null, true);
    }

    /**
     * Revokes the given set of entitlements.
     *
     * @param entsToRevoke
     *  entitlements to revoke
     * @param alreadyDeletedPools
     *  pools to skip deletion as they have already been deleted
     * @param regenCertsAndStatuses
     *  if this revocation should also trigger regeneration of certificates and recomputation of
     *  statuses. For performance reasons some callers might choose to set this to false.
     * @return the pools that are deleted as a consequence of revoking entitlements
     */
    @Transactional
    @Traceable
    public Set<Pool> revokeEntitlements(List<Entitlement> entsToRevoke, Set<String> alreadyDeletedPools,
        boolean regenCertsAndStatuses) {

        if (CollectionUtils.isEmpty(entsToRevoke)) {
            return null;
        }

        log.debug("Starting batch revoke of {} entitlements", entsToRevoke.size());
        if (log.isTraceEnabled()) {
            log.trace("Entitlements IDs: {}", getEntIds(entsToRevoke));
        }

        Set<Pool> poolsToDelete = this.poolCurator.listBySourceEntitlements(entsToRevoke);

        log.debug("Found {} additional pools to delete from source entitlements", poolsToDelete.size());
        List<String> poolIdsToDelete = getPoolIds(poolsToDelete);
        if (log.isTraceEnabled()) {
            log.trace("Additional pool IDs: {}", poolIdsToDelete);
        }

        Set<Pool> poolsToLock = new HashSet<>(poolsToDelete);
        for (Entitlement ent : entsToRevoke) {
            poolsToLock.add(ent.getPool());
        }

        this.poolCurator.lock(poolsToLock);
        this.poolCurator.refresh(poolsToLock);

        log.info("Batch revoking {} entitlements", entsToRevoke.size());
        entsToRevoke = new ArrayList<>(entsToRevoke);

        for (Pool pool : poolsToDelete) {
            for (Entitlement ent : pool.getEntitlements()) {
                ent.setDeletedFromPool(true);
                entsToRevoke.add(ent);
            }
        }

        log.debug("Adjusting consumed quantities on pools");
        Set<Consumer> consumersToUpdate = new HashSet<>();
        List<Pool> poolsToSave = new ArrayList<>();
        Set<String> entIdsToRevoke = new HashSet<>();
        for (Entitlement ent : entsToRevoke) {
            // TODO: Should we throw an exception if we find a malformed/incomplete entitlement
            // or just continue silently ignoring them?
            if (ent == null || ent.getId() == null) {
                continue;
            }

            // Collect the entitlement IDs to revoke seeing as we are iterating over them anyway.
            entIdsToRevoke.add(ent.getId());

            // We need to trigger lazy load of provided products
            // to have access to those products later in this method.
            Pool pool = ent.getPool();
            int entQuantity = ent.getQuantity() != null ? ent.getQuantity() : 0;

            pool.setConsumed(pool.getConsumed() - entQuantity);
            Consumer consumer = ent.getConsumer();
            ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

            if (ctype != null && ctype.isManifest()) {
                pool.setExported(pool.getExported() - entQuantity);
            }

            consumer.setEntitlementCount(consumer.getEntitlementCount() - entQuantity);
            consumersToUpdate.add(consumer);
            poolsToSave.add(pool);
        }

        consumerCurator.bulkUpdate(consumersToUpdate, false);
        poolCurator.updateAll(poolsToSave, false, false);

        /*
         * Before deleting the entitlements, we need to find out if there are any modifier entitlements that
         * need to have their certificates regenerated
         */
        if (regenCertsAndStatuses) {
            log.debug("Marking dependent entitlements as dirty...");
            int update = this.entitlementCurator.markDependentEntitlementsDirty(entIdsToRevoke);
            log.debug("{} dependent entitlements marked dirty.", update);
        }

        log.info("Starting batch delete of pools");
        poolCurator.batchDelete(poolsToDelete, alreadyDeletedPools);
        firePoolDeletedEvents(poolsToDelete);
        log.info("Starting batch delete of entitlements");
        entitlementCurator.batchDelete(entsToRevoke);
        log.info("Starting delete flush");
        entitlementCurator.flush();
        log.info("All deletes flushed successfully");

        updateStackingEntitlements(entsToRevoke, alreadyDeletedPools);
        postUnbind(entsToRevoke);

        if (regenCertsAndStatuses) {
            Set<Consumer> consumers = consumersOf(entsToRevoke);
            recomputeStatusForConsumers(consumers);
        }
        else {
            log.info("Regeneration and status computation was not requested finishing batch revoke");
        }
        sendDeletedEvents(entsToRevoke);
        return poolsToDelete;
    }

    private Set<Consumer> consumersOf(List<Entitlement> entitlements) {
        return entitlements.stream()
            .map(Entitlement::getConsumer)
            .collect(Collectors.toSet());
    }

    private void firePoolDeletedEvents(Set<Pool> poolsToDelete) {
        poolsToDelete.stream()
            .map(this.eventFactory::poolDeleted)
            .forEach(this.sink::queueEvent);
    }

    private void postUnbind(Collection<Entitlement> entitlements) {
        entitlements.forEach(this::postUnbind);
    }

    private void recomputeStatusForConsumers(Set<Consumer> consumers) {
        log.info("Recomputing status for {} consumers.", consumers.size());
        int i = 1;
        for (Consumer consumer : consumers) {
            if (i++ % 1000 == 0) {
                consumerCurator.flush();
            }
            complianceRules.getStatus(consumer);
            systemPurposeComplianceRules.getStatus(consumer, consumer.getEntitlements(), null, true);
        }
        consumerCurator.flush();
        log.info("All statuses recomputed.");
    }

    private void sendDeletedEvents(List<Entitlement> entsToRevoke) {
        // for each deleted entitlement, create an event
        for (Entitlement entitlement : entsToRevoke) {
            if (entitlement.deletedFromPool()) {
                continue;
            }

            Consumer consumer = entitlement.getConsumer();
            Event event = eventFactory.entitlementDeleted(entitlement);

            if (!entitlement.isValid() && entitlement.getPool().isUnmappedGuestPool()) {
                Consumer host = this.consumerCurator.getHost(consumer.getFact(Consumer.Facts.VIRT_UUID),
                    consumer.getOwnerId());

                if (host == null) {
                    event = eventFactory.entitlementExpired(entitlement);
                    event.setMessageText(event.getMessageText() + ": " + i18n.tr("Unmapped guest " +
                        "entitlement expired without establishing a host/guest mapping."));
                }
            }

            sink.queueEvent(event);
        }
    }

    /**
     * Helper method for log debug messages
     *
     * @param entitlements
     * @return
     */
    private List<String> getEntIds(Collection<Entitlement> entitlements) {
        return entitlements.stream()
            .map(Entitlement::getId)
            .toList();
    }

    /**
     * Helper method for log debug messages
     *
     * @param pools
     *  pools to get IDs for
     * @return List pool ID list
     */
    private List<String> getPoolIds(Collection<Pool> pools) {
        return pools.stream()
            .map(Pool::getId)
            .toList();
    }

    /**
     * Updates the pool based on the entitlements in the specified stack.
     *
     * @param pools
     * @param consumer
     * @return updates
     */
    public List<PoolUpdate> updatePoolsFromStack(Consumer consumer, Collection<Pool> pools,
        Collection<Entitlement> entitlements, boolean deleteIfNoStackedEnts) {
        return updatePoolsFromStack(consumer, pools, entitlements, null, deleteIfNoStackedEnts);
    }

    /**
     * Filter the given entitlements so that this method returns only the entitlements that are part of
     * some stack. Then update them accordingly
     *
     * @param entsToRevoke
     * @param alreadyDeletedPools
     *  pools to skip deletion as they have already been deleted
     */
    private void updateStackingEntitlements(List<Entitlement> entsToRevoke, Set<String> alreadyDeletedPools) {
        Map<Consumer, List<Entitlement>> stackingEntsByConsumer = this.stackingEntitlementsOf(entsToRevoke);
        log.debug("Found stacking entitlements for {} consumers", stackingEntsByConsumer.size());

        Set<String> allStackingIds = this.stackIdsOf(stackingEntsByConsumer.values());
        List<Pool> pools = this.poolCurator
            .getSubPoolsForStackIds(stackingEntsByConsumer.keySet(), allStackingIds);

        this.bulkUpdatePoolsFromStack(stackingEntsByConsumer.keySet(), pools, alreadyDeletedPools, true);
    }

    /**
     * Updates the pool based on the entitlements in the specified stack.
     */
    public void bulkUpdatePoolsFromStack(Set<Consumer> consumers, List<Pool> pools,
        Collection<String> alreadyDeletedPools, boolean deleteIfNoStackedEnts) {

        log.debug("Bulk updating {} pools for {} consumers.", pools.size(), consumers.size());
        List<Entitlement> stackingEntitlements = findStackingEntitlementsOf(pools);
        log.debug("found {} stacking entitlements.", stackingEntitlements.size());
        List<Entitlement> filteredEntitlements = filterByConsumers(consumers, stackingEntitlements);
        Map<String, List<Entitlement>> entitlementsByStackingId = groupByStackingId(filteredEntitlements);

        updatePoolsWithStackingEntitlements(pools, entitlementsByStackingId);

        if (deleteIfNoStackedEnts) {
            List<Pool> poolsToDelete = this.filterPoolsWithoutStackingEntitlements(pools,
                entitlementsByStackingId);

            if (!poolsToDelete.isEmpty()) {
                this.deletePools(poolsToDelete, alreadyDeletedPools);
            }
        }
    }

    private List<Entitlement> findStackingEntitlementsOf(List<Pool> pools) {
        Set<String> sourceStackIds = stackIdsOfPools(pools);
        log.debug("Found {} source stacks", sourceStackIds.size());
        return this.entitlementCurator.findByStackIds(null, sourceStackIds);
    }

    private Set<String> stackIdsOfPools(List<Pool> pools) {
        return pools.stream()
            .map(Pool::getSourceStackId)
            .collect(Collectors.toSet());
    }

    private List<Pool> filterPoolsWithoutStackingEntitlements(
        List<Pool> pools, Map<String, List<Entitlement>> entitlementsByStackingId) {
        List<Pool> poolsToDelete = new ArrayList<>();
        for (Pool pool : pools) {
            List<Entitlement> entitlements = entitlementsByStackingId.get(pool.getSourceStackId());
            if (CollectionUtils.isEmpty(entitlements)) {
                poolsToDelete.add(pool);
            }
        }
        return poolsToDelete;
    }

    private void updatePoolsWithStackingEntitlements(List<Pool> pools,
        Map<String, List<Entitlement>> entitlementsByStackingId) {
        for (Pool pool : pools) {
            List<Entitlement> entitlements = entitlementsByStackingId.get(pool.getSourceStackId());
            if (CollectionUtils.isNotEmpty(entitlements)) {
                this.poolRules.updatePoolFromStackedEntitlements(pool, entitlements,
                    Collections.emptyMap());
            }
        }
    }

    private Map<String, List<Entitlement>> groupByConsumerUuid(List<Entitlement> entitlements) {
        return entitlements.stream()
            .collect(Collectors.groupingBy(e -> e.getConsumer().getUuid()));
    }

    private Map<String, List<Entitlement>> groupByStackingId(List<Entitlement> entitlements) {
        return entitlements.stream()
            .collect(Collectors.groupingBy(entitlement -> entitlement.getPool().getStackId()));
    }

    private List<Entitlement> filterByConsumers(Set<Consumer> consumers, List<Entitlement> entitlements) {
        Map<String, List<Entitlement>> entitlementsByConsumerUuid = groupByConsumerUuid(entitlements);
        List<Entitlement> filteredEntitlements = new ArrayList<>(consumers.size());
        for (Consumer consumer : consumers) {
            if (entitlementsByConsumerUuid.containsKey(consumer.getUuid())) {
                final List<Entitlement> foundEntitlements = entitlementsByConsumerUuid
                    .get(consumer.getUuid());
                log.debug("Found {} entitlements for consumer: {}", foundEntitlements.size(),
                    consumer.getUuid());
                filteredEntitlements.addAll(foundEntitlements);
            }
        }
        return filteredEntitlements;
    }

    private Map<Consumer, List<Entitlement>> stackingEntitlementsOf(List<Entitlement> entitlements) {
        return entitlements.stream()
            .filter(entitlement -> !entitlement.getPool().isDerived())
            .filter(entitlement -> entitlement.getPool().isStacked())
            .collect(Collectors.groupingBy(Entitlement::getConsumer));
    }

    private Set<String> stackIdsOf(Collection<List<Entitlement>> entitlementsPerConsumer) {
        return entitlementsPerConsumer.stream()
            .map(this::stackIdsOf)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    private Set<String> stackIdsOf(List<Entitlement> entitlements) {
        return entitlements.stream()
            .map(Entitlement::getPool)
            .map(Pool::getStackId)
            .collect(Collectors.toSet());
    }

    private boolean isPrimaryPoolUnlimited(List<Pool> pools) {
        return pools.stream()
            .map(Pool::getQuantity)
            .anyMatch(quantity -> quantity == UNLIMITED_QUANTITY);
    }

}
