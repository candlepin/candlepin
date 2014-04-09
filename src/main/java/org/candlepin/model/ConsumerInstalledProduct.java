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
package org.candlepin.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.commons.lang.builder.HashCodeBuilder;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

/**
 * Represents a product installed (not necessarily entitled) on a consumer.
 *
 * Used for compliance checking and healing to find appropriate subscriptions.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_installed_products")
public class ConsumerInstalledProduct extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(name = "product_id", nullable = false)
    @Size(max = 255)
    @NotNull
    private String productId;

    @Column(name = "product_name")
    @Size(max = 255)
    private String productName;

    @Column(name = "product_version")
    @Size(max = 20)
    private String version;

    @Column(name = "product_arch")
    @Size(max = 63)
    private String arch;

    @Transient
    private String status;

    @Transient
    private Date startDate;

    @Transient
    private Date endDate;

    @ManyToOne
    @ForeignKey(name = "fk_consumer_installed_product")
    @JoinColumn(nullable = false)
    @XmlTransient
    @Index(name = "cp_installedproduct_consumer_fk_idx")
    private Consumer consumer;

    public ConsumerInstalledProduct() {
    }

    public ConsumerInstalledProduct(String productId, String productName) {
        this.productId = productId;
        this.productName = productName;
    }

    public ConsumerInstalledProduct(String productId, String productName,
        Consumer consumer) {
        this.productId = productId;
        this.productName = productName;
        this.consumer = consumer;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getArch() {
        return arch;
    }

    public void setArch(String arch) {
        this.arch = arch;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    @XmlTransient
    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ConsumerInstalledProduct)) {
            return false;
        }
        ConsumerInstalledProduct that = (ConsumerInstalledProduct) other;
        if (this.getProductId().equals(that.getProductId()) &&
            this.getProductName().equals(that.getProductName()) &&
            ((this.getVersion() == null && that.getVersion() == null) ||
            this.getVersion() != null && this.getVersion().equals(that.getVersion())) &&
            ((this.getArch() == null && that.getArch() == null) ||
            this.getArch() != null && this.getArch().equals(that.getArch()))) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(7, 23).append(getProductId())
            .append(getProductName()).toHashCode();
    }
}
