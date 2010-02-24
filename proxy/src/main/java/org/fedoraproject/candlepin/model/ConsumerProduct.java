/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.hibernate.annotations.ForeignKey;

/**
 * ConsumerProducts store the products which a consumer may be consuming.
 *
 * NOTE: They are not necessarily entitled to all of these.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_consumer_products")
@SequenceGenerator(name = "seq_consumer_products",
        sequenceName = "seq_consumer_products", allocationSize = 1)
public class ConsumerProduct implements Persisted {
    
    // TODO: Don't know if this is a good idea, technically the consumer +
    // product oid should be the key.
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_consumer_products")
    private Long id;
    
    @ManyToOne
    @ForeignKey(name = "fk_consumer_product_owner")
    private Consumer consumer;    
    
    @Column(nullable = false)
    private String productId;

    /** {@inheritDoc} */
    public Long getId() {
        return id;
    }

    /**
     * @param id db id
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * @return the Consumer portion of the association.
     */
    public Consumer getConsumer() {
        return consumer;
    }

    /**
     * associate Consumer to a Product
     * @param consumer consumer to associate
     */
    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    /**
     * @return return the product id associated with the Consumer.
     */
    public String getProductId() {
        return productId;
    }

    /**
     * @param productId associate the product to a Consumer.
     */
    public void setProductId(String productId) {
        this.productId = productId;
    }
    
}
