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

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.ForeignKey;

/**
 * ProductCertificate
 */
@XmlRootElement(name = "cert")
@XmlAccessorType(XmlAccessType.PROPERTY)
@SequenceGenerator(name = "seq_product_cert", 
    sequenceName = "seq_product_cert", allocationSize = 1)
@Entity
@Table(name = "cp_product_certificate")
public class ProductCertificate extends AbstractCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, 
                    generator = "seq_product_cert")
    private Long id;
    
    @XmlTransient
    public Long getId() {
        return id;
    }
    
    // Note: Probably doesn't need to be many-to-one, but 
    // trying to mirror the entitlement model...
    @ManyToOne
    @ForeignKey(name = "fk_cert_product")
    @JoinColumn(nullable = false)
    private Product product;

    /**
     * @return the product
     */
    @XmlTransient
    public Product getProduct() {
        return product;
    }

    /**
     * @param product the product to set
     */
    public void setProduct(Product product) {
        this.product = product;
    }
}
