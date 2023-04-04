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
package org.candlepin.model;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.hibernate.annotations.GenericGenerator;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Represents a product installed (not necessarily entitled) on a consumer.
 *
 * Used for compliance checking and healing to find appropriate subscriptions.
 */
@Entity
@Table(name = ConsumerInstalledProduct.DB_TABLE)
public class ConsumerInstalledProduct extends AbstractHibernateObject {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_installed_products";

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Consumer consumer;

    public ConsumerInstalledProduct() {
        // intentionally left empty
    }

    public String getProductId() {
        return productId;
    }

    public ConsumerInstalledProduct setProductId(String productId) {
        this.productId = productId;
        return this;
    }

    public String getProductName() {
        return productName;
    }

    public ConsumerInstalledProduct setProductName(String productName) {
        this.productName = productName;
        return this;
    }

    public String getId() {
        return id;
    }

    public ConsumerInstalledProduct setId(String id) {
        this.id = id;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public ConsumerInstalledProduct setVersion(String version) {
        this.version = version;
        return this;
    }

    public String getArch() {
        return arch;
    }

    public ConsumerInstalledProduct setArch(String arch) {
        this.arch = arch;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public ConsumerInstalledProduct setStatus(String status) {
        this.status = status;
        return this;
    }

    public Date getStartDate() {
        return startDate;
    }

    public ConsumerInstalledProduct setStartDate(Date startDate) {
        this.startDate = startDate;
        return this;
    }

    public Date getEndDate() {
        return endDate;
    }

    public ConsumerInstalledProduct setEndDate(Date endDate) {
        this.endDate = endDate;
        return this;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public ConsumerInstalledProduct setConsumer(Consumer consumer) {
        this.consumer = consumer;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof ConsumerInstalledProduct)) {
            return false;
        }

        ConsumerInstalledProduct that = (ConsumerInstalledProduct) obj;

        return new EqualsBuilder()
            .append(this.getProductId(), that.getProductId())
            .append(this.getProductName(), that.getProductName())
            .append(this.getVersion(), that.getVersion())
            .append(this.getArch(), that.getArch())
            .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(7, 23)
            .append(this.getProductId())
            .append(this.getProductName())
            .toHashCode();
    }
}
