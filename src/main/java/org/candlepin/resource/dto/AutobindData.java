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
package org.candlepin.resource.dto;

import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;



/**
 * Encapsulates data for an autobind
 */
public class AutobindData {

    private Owner owner;
    private Consumer consumer;

    private Date onDate;
    private Set<String> poolIds;
    private SortedSet<String> productIds;

    public AutobindData(Consumer consumer, Owner owner) {
        this.setOwner(owner)
            .setConsumer(consumer);

        this.poolIds = new HashSet<>();
        this.productIds = new TreeSet<>();
    }

    public AutobindData setOwner(Owner owner) {
        this.owner = owner;
        return this;
    }

    public Owner getOwner() {
        return this.owner;
    }

    public AutobindData setConsumer(Consumer consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }

        this.consumer = consumer;
        return this;
    }

    public Consumer getConsumer() {
        return this.consumer;
    }

    public AutobindData setOnDate(Date date) {
        this.onDate = date;
        return this;
    }

    /**
     * Alias of setOnDate; retained for legacy purposes.
     *
     * @param date
     *  the start date or effective date of the bind operation
     *
     * @return
     *  a reference to this AutobindData instance
     */
    public AutobindData on(Date date) {
        return this.setOnDate(date);
    }

    public Date getOnDate() {
        return this.onDate;
    }

    public AutobindData setPossiblePools(Collection<String> poolIds) {
        this.poolIds.clear();

        if (poolIds != null) {
            poolIds.stream()
                .filter(Objects::nonNull)
                .forEach(pid -> this.poolIds.add(pid));
        }

        return this;
    }

    /**
     * Alias of setPossiblePools; retained for legacy purposes.
     *
     * @param poolIds
     *  a collection of possible pool IDs to use for the bind operation
     *
     * @return
     *  a reference to this AutobindData instance
     */
    public AutobindData withPools(Collection<String> poolIds) {
        return this.setPossiblePools(poolIds);
    }

    public Set<String> getPossiblePools() {
        return this.poolIds;
    }

    public AutobindData setProductIds(Collection<String> productIds) {
        this.productIds.clear();

        if (productIds != null) {
            productIds.stream()
                .filter(Objects::nonNull)
                .forEach(pid -> this.productIds.add(pid));
        }

        return this;
    }

    /**
     * Alias of setProductIds; retained for legacy purposes
     *
     * @param productIds
     *  a collection of product IDs to use for the bind operation
     *
     * @return
     *  a reference to this AutobindData instance
     */
    public AutobindData forProducts(Collection<String> productIds) {
        return this.setProductIds(productIds);
    }

    public SortedSet<String> getProductIds() {
        return this.productIds;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AutobindData)) {
            return false;
        }

        AutobindData that = (AutobindData) other;

        // TODO: FIXME: Why is owner not part of this!?

        return new EqualsBuilder()
            .append(this.onDate, that.onDate)
            .append(this.productIds, that.productIds)
            .append(this.consumer, that.consumer)
            .append(this.poolIds, that.poolIds)
            .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.onDate)
            .append(this.productIds)
            .append(this.consumer)
            .append(this.poolIds)
            .hashCode();
    }

    @Override
    public String toString() {
        return String.format(
            "AutobindData [owner: %s, consumer: %s, onDate: %s, pool IDs: %s, product IDs: %s]",
            this.getOwner(), this.getConsumer(), this.getOnDate(), this.getPossiblePools(),
            this.getProductIds());
    }
}
