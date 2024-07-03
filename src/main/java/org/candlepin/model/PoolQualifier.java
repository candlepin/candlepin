/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
package org.candlepin.model;

import org.candlepin.model.activationkeys.ActivationKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Class used for defining criteria that reduces a collection of {@link Pool}s to a subset that meets
 * the criteria.
 */
public class PoolQualifier extends QueryArguments<PoolQualifier> {

    private Set<String> ids = new HashSet<>();
    private Set<String> productIds = new HashSet<>();
    private Set<String> matches = new HashSet<>();
    private Map<String, List<String>> attributes = new HashMap<>();
    private Set<String> subscriptionIds = new HashSet<>();

    private Date activeOn;
    private boolean addFuture = false;
    private boolean isOnlyFuture = false;
    private boolean includeWarnings = false;
    private Date after;

    private Consumer consumer;
    private String ownerId;
    private ActivationKey activationKey;

    /**
     * @return
     *  an unmodifiable set of all the {@link Pool} IDs that qualify a pool
     */
    public Set<String> getIds() {
        return Collections.unmodifiableSet(ids);
    }

    /**
     * Adds a {@link Pool} ID condition that a pool must fulfill to be considered qualified.
     *
     * @param id
     *  the ID of a pool that qualifies pools
     *
     * @return this instance
     */
    public PoolQualifier addId(String id) {
        if (id == null || id.isBlank()) {
            return this;
        }

        this.ids.add(id);
        return this;
    }

    /**
     * Adds multiple {@link Pool} IDs to create conditions that a pool must fulfill to be considered
     * qualified. If a pool's ID equals one of the provided IDs then it will be considered qualified.
     * Null and blank IDs are filtered from the provided collection of IDs.
     *
     * @param ids
     *  the IDs of pools that qualifies pools
     *
     * @return this instance
     */
    public PoolQualifier addIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return this;
        }

        ids.stream()
            .filter(Objects::nonNull)
            .filter(Predicate.not(String::isBlank))
            .forEach(this.ids::add);

        return this;
    }

    /**
     * Removes a {@link Pool} ID if it exists
     *
     * @param id
     *  the pool ID to remove
     *
     * @return this instance
     */
    public PoolQualifier removeId(String id) {
        if (id == null || id.isBlank()) {
            return this;
        }

        ids.remove(id);
        return this;
    }

    /**
     * @return
     *  an unmodifiable set of all the {@link Pool} product IDs that qualify a pool
     */
    public Set<String> getProductIds() {
        return Collections.unmodifiableSet(productIds);
    }

    /**
     * Adds a {@link Pool} product ID condition that a pool must fulfill to be considered qualified.
     *
     * @param productId
     *  the product ID that qualifies pools
     *
     * @return this instance
     */
    public PoolQualifier addProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            return this;
        }

        this.productIds.add(productId);
        return this;
    }

    /**
     * Adds multiple {@link Pool} product IDs to create conditions that a pool must fulfill to be considered
     * qualified. If a pool's product ID equals one of the provided IDs then it will be considered qualified.
     * Null and blank product IDs are filtered from the provided collection.
     *
     * @param productIds
     *  a collection of product IDs that qualifies pools
     *
     * @return this instance
     */
    public PoolQualifier addProductIds(Collection<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return this;
        }

        productIds.stream()
            .filter(Objects::nonNull)
            .filter(Predicate.not(String::isBlank))
            .forEach(this.productIds::add);

        return this;
    }

    /**
     * Removes a {@link Pool} product ID if it exists
     *
     * @param productId
     *  the product ID to remove
     *
     * @return this instance
     */
    public PoolQualifier removeProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            return this;
        }

        this.productIds.remove(productId);
        return this;
    }

    /**
     * @return
     *  an unmodifiable set of all the {@link Pool} match patterns that qualify a pool
     */
    public Set<String> getMatches() {
        return Collections.unmodifiableSet(matches);
    }

    /**
     * Adds a pattern that is applied to various pool related fields. If one of the fields satifies the
     * provided pattern then the pool will be considered qualified for this condition. This pattern can
     * include wildcards such as * and ?.
     *
     * @param match
     *  a pattern for determining if a pool related field is considered qualified
     *
     * @return this instance
     */
    public PoolQualifier addMatch(String match) {
        if (match == null || match.isBlank()) {
            return this;
        }

        this.matches.add(match);
        return this;
    }

    /**
     * Adds a collection of patterns that is applied to various pool related fields. If one of the fields
     * satifies one of the provided patterns then the pool will be considered qualified for this condition.
     * This pattern can include wildcards such as * and ?. Null and blank match values are filtered from the
     * provided collection.
     *
     * @param matches
     *  a collection of patterns for determining if a pool related field is considered qualified
     *
     * @return this instance
     */
    public PoolQualifier addMatches(Collection<String> matches) {
        if (matches == null || matches.isEmpty()) {
            return this;
        }

        matches.stream()
            .filter(Objects::nonNull)
            .filter(Predicate.not(String::isBlank))
            .forEach(this.matches::add);

        return this;
    }

    /**
     * Removes a match pattern if it exists
     *
     * @param match
     *  the match pattern to remove
     *
     * @return this instance
     */
    public PoolQualifier removeMatch(String match) {
        if (match == null || match.isBlank()) {
            return this;
        }

        this.matches.remove(match);
        return this;
    }

    /**
     * @return
     *  an unmodifiable map of all the {@link Pool} product attributes that qualify a pool
     */
    public Map<String, List<String>> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * Adds a {@link Pool} attribute condition that a pool must fulfill to be considered qualified. Multiple
     * attribute values can be added for the same attribute key.
     *
     * @param key
     *  the attribute key
     *
     * @param value
     *  the attribute value
     *
     * @return this instance
     */
    public PoolQualifier addAttribute(String key, String value) {
        if (key == null || key.isBlank()) {
            return this;
        }

        attributes.computeIfAbsent(key, k -> new ArrayList<>())
            .add(value);

        return this;
    }

    /**
     * Removes all {@link Pool} product attribute values based on a provided key.
     *
     * @param key
     *  the pool attribute key to remove
     *
     * @return this instance
     */
    public PoolQualifier removeAttribute(String key) {
        if (key == null || key.isBlank()) {
            return this;
        }

        attributes.remove(key);
        return this;
    }

    /**
     * @return the date the pools must be active on to be considered qualified
     */
    public Date getActiveOn() {
        return activeOn;
    }

    /**
     * Sets a date that a {@link Pool} must have been active on based on the start and end date of the pool.
     *
     * @param activeOn
     *  the date the pool must be active on to be considered qualified
     *
     * @return this instance
     */
    public PoolQualifier setActiveOn(Date activeOn) {
        this.activeOn = activeOn == null ? null : new Date(activeOn.getTime());
        return this;
    }

    /**
     * @return true if the active on date should be after a pool's end date
     */
    public boolean getAddFuture() {
        return addFuture;
    }

    /**
     * If set to true, the active on date must be after a {@link Pool}'s end date to be considered qualified
     * if and only if the only future value is false. By default this value is false.
     *
     * @param addFuture
     *  dictates if the active on date should be after a pool's end date
     *
     * @return this instance
     */
    public PoolQualifier setAddFuture(boolean addFuture) {
        this.addFuture = addFuture;
        return this;
    }

    /**
     * @return the only future value
     */
    public boolean isOnlyFuture() {
        return isOnlyFuture;
    }

    /**
     * If true, the active on date only needs to be greater that the {@link Pool}'s start date to
     * be considered qualified. By default this value is false.
     *
     * @param isOnlyFuture
     *  dictates if the active on date needs to only be greater than a pool's start date
     *
     * @return this instance
     */
    public PoolQualifier setOnlyFuture(boolean isOnlyFuture) {
        this.isOnlyFuture = isOnlyFuture;
        return this;
    }

    /**
     * @return if a pool is qualified if a rule warning was triggered for the pool
     */
    public boolean includeWarnings() {
        return includeWarnings;
    }

    /**
     * Defines if a pool is qualified if a rule warning was triggered for the pool if a Consumer is also
     * defined in this qualifier.
     *
     * @param includeWarnings
     *  true if you should include pools that trigger a rule warning, or false if they should be excluded
     *
     * @return this instance
     */
    public PoolQualifier setIncludeWarnings(boolean includeWarnings) {
        this.includeWarnings = includeWarnings;
        return this;
    }

    /**
     * @return the date that a {@Pool}'s start date must be greater than or equal to to be considered
     *  qualified
     */
    public Date getAfter() {
        return after;
    }

    /**
     * Sets a date that a {@Pool}'s start date must be greater than or equal to to be considered qualified.
     *
     * @param after
     *  the date a pool's start date must be greater than
     *
     * @return this instance
     */
    public PoolQualifier setAfter(Date after) {
        this.after = after == null ? null : new Date(after.getTime());
        return this;
    }

    /**
     * @return the {@link Consumer} set for this qualifier
     */
    public Consumer getConsumer() {
        return consumer;
    }

    /**
     * Adds a {@link Pool}'s consumer condition that a pool must fulfill to be considered qualified.
     * If an existing consumer was defined, it will be replaced by the new consumer.
     *
     * @param consumer
     *  the consumer of a pool that qualifies pools
     *
     * @return this instance
     */
    public PoolQualifier setConsumer(Consumer consumer) {
        this.consumer = consumer;
        return this;
    }

    /**
     * @return the owner ID set for this qualifier
     */
    public String getOwnerId() {
        return ownerId;
    }

    /**
     * Sets a {@link Pool} owner ID condition that a pool must fulfill to be considered qualified.
     * If an existing owner ID value was defined, it will be replaced by the new value.
     *
     * @param ownerId
     *  the owner ID of a pool that qualifies pools
     *
     * @return this instance
     */
    public PoolQualifier setOwnerId(String ownerId) {
        this.ownerId = ownerId;
        return this;
    }

    /**
     * @return
     *  an unmodifiable set of all the {@link Pool} subscription IDs that qualify a pool
     */
    public Set<String> getSubscriptionIds() {
        return Collections.unmodifiableSet(subscriptionIds);
    }

    /**
     * Adds a {@link Pool} subscription ID condition that a pool must fulfill to be considered qualified.
     *
     * @param subscriptionId
     *  the subscription ID that qualifies pools
     *
     * @return this instance
     */
    public PoolQualifier addSubscriptionId(String subscriptionId) {
        if (subscriptionId == null || subscriptionId.isBlank()) {
            return this;
        }

        this.subscriptionIds.add(subscriptionId);
        return this;
    }

    /**
     * Adds multiple {@link Pool} subscription IDs to creates conditions that a pool must fulfill to be
     * considered qualified. If a pool's subscription ID equals one of the provided IDs then it will be
     * considered qualified. Null and blank subscription IDs are filtered from the provided collection.
     *
     * @param subscriptionIds
     *  the subscription IDs that qualifies pools
     *
     * @return this instance
     */
    public PoolQualifier addSubscriptionIds(Collection<String> subscriptionIds) {
        if (subscriptionIds == null || subscriptionIds.isEmpty()) {
            return this;
        }

        subscriptionIds.stream()
            .filter(Objects::nonNull)
            .filter(Predicate.not(String::isBlank))
            .forEach(this.subscriptionIds::add);

        return this;
    }

    /**
     * Removes a {@link Pool} subscription ID.
     *
     * @param subscriptionId
     *  the pool subscription ID to remove
     *
     * @return this instance
     */
    public PoolQualifier removeSubscriptionId(String subscriptionId) {
        if (subscriptionId == null || subscriptionId.isBlank()) {
            return this;
        }

        this.subscriptionIds.remove(subscriptionId);
        return this;
    }

    /**
     * Sets a {@link Pool} activation key condition that a pool must fulfill to be considered qualified.
     * If an existing activation key value was defined, it will be replaced by the new value.
     *
     * @param activationKey
     *  the activation key that qualifies pools
     *
     * @return this instance
     */
    public PoolQualifier setActivationKey(ActivationKey activationKey) {
        this.activationKey = activationKey;

        return this;
    }

    /**
     * @return the activation key set for this qualifier
     */
    public ActivationKey getActivationKey() {
        return activationKey;
    }

}
