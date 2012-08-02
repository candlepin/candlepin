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
package org.candlepin.json.model;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * EntitlementBody
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class EntitlementBody {

    private String consumer;
    private Integer quantity;
    private Subscription subscription;
    private Order order;
    private List<Product> products;

    /**
     * @param uuid
     */
    public void setConsumer(String uuid) {
        this.consumer = uuid;
    }

    /**
     * @param quantity
     */
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    /**
     * @param subscription
     */
    public void setSubscription(Subscription subscription) {
        this.subscription = subscription;
    }

    /**
     * @param order
     */
    public void setOrder(Order order) {
        this.order = order;
    }

    /**
     * @param products
     */
    public void setProducts(List<Product> products) {
        this.products = products;
    }

}
