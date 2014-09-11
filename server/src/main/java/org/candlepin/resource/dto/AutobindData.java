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
package org.candlepin.resource.dto;

import org.candlepin.model.Consumer;

import java.util.Collection;
import java.util.Date;

/**
 * Encapsulates data for an autobind
 */
public class AutobindData {

    private Date onDate;
    private String[] productIds;
    private Collection<String> possiblePools;
    private Consumer consumer;

    public AutobindData(Consumer consumer) {
        // Consumer is always required
        this.consumer = consumer;
    }

    public static AutobindData create(Consumer consumer) {
        return new AutobindData(consumer);
    }

    public AutobindData on(Date date) {
        this.onDate = date;
        return this;
    }

    public AutobindData withPools(Collection<String> possiblePools) {
        this.possiblePools = possiblePools;
        return this;
    }

    public AutobindData forProducts(String[] productIds) {
        this.productIds = productIds;
        return this;
    }

    public Date getOnDate() {
        return onDate;
    }

    public void setOnDate(Date onDate) {
        this.onDate = onDate;
    }

    public String[] getProductIds() {
        return productIds;
    }

    public void setProductIds(String[] productIds) {
        this.productIds = productIds;
    }

    public Collection<String> getPossiblePools() {
        return possiblePools;
    }

    public void setPossiblePools(Collection<String> possiblePools) {
        this.possiblePools = possiblePools;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }
}
