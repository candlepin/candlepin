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
package org.candlepin.gutterball.model.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.GenericGenerator;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * A model representing a snapshot of an Entitlement at a given point in time.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "gb_entitlement_snap")
public class Entitlement {
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    @JsonIgnore
    private String id;

    @XmlTransient
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compliance_snap_id", nullable = false)
    @NotNull
    private Compliance complianceSnapshot;

    private int quantity;

    // TODO Entitlement sourceEntitlement

    @Column(name = "start_date", nullable = false)
    @NotNull
    private Date startDate;

    @Column(name = "end_date", nullable = false)
    @NotNull
    private Date endDate;

    @Column(name = "product_id", nullable = false)
    @Size(max = 255)
    @NotNull
    private String productId;

    @Size(max = 255)
    @Column(name = "derived_product_id")
    private String derivedProductId;

    @Size(max = 255)
    @Column(name = "product_name")
    private String productName;

    @Size(max = 255)
    @Column(name = "derived_product_name")
    private String derivedProductName;

    @Size(max = 255)
    @Column(name = "restricted_to_username")
    private String restrictedToUsername;

    @Size(max = 255)
    @Column(name = "contract_number")
    private String contractNumber;

    @Size(max = 255)
    @Column(name = "account_number")
    private String accountNumber;

    @Size(max = 255)
    @Column(name = "order_number")
    private String orderNumber;

    @ElementCollection
    @CollectionTable(name = "gb_ent_attr_snap",
                     joinColumns = @JoinColumn(name = "ent_snap_id"))
    @MapKeyColumn(name = "gb_ent_attr_name")
    @Column(name = "gb_ent_attr_value")
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    private Map<String, String> attributes;

    @ElementCollection
    @CollectionTable(name = "gb_ent_prov_prod_snap",
                     joinColumns = @JoinColumn(name = "ent_snap_id"))
    @MapKeyColumn(name = "gb_ent_prov_prod_id")
    @Column(name = "gb_ent_prov_prod_name")
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    private Map<String, String> providedProducts;

    @ElementCollection
    @CollectionTable(name = "gb_ent_der_prod_attr_snap",
                     joinColumns = @JoinColumn(name = "ent_snap_id"))
    @MapKeyColumn(name = "gb_ent_der_prod_attr_name")
    @Column(name = "gb_ent_der_prod_attr_value")
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    private Map<String, String> derivedProductAttributes;

    @ElementCollection
    @CollectionTable(name = "gb_ent_der_prov_prod_snap",
                     joinColumns = @JoinColumn(name = "ent_snap_id"))
    @MapKeyColumn(name = "gb_ent_der_prov_prod_id")
    @Column(name = "gb_ent_der_prov_prod_name")
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    private Map<String, String> derivedProvidedProducts;

    public Entitlement() {
        attributes = new HashMap<String, String>();
        derivedProductAttributes = new HashMap<String, String>();
        providedProducts = new HashMap<String, String>();
        derivedProvidedProducts = new HashMap<String, String>();
    }

    public Entitlement(int quantity, Date startDate, Date endDate) {
        this();

        this.quantity = quantity;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @XmlTransient
    public Compliance getComplianceSnapshot() {
        return complianceSnapshot;
    }

    public void setComplianceSnapshot(Compliance complianceSnapshot) {
        this.complianceSnapshot = complianceSnapshot;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
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

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getDerivedProductId() {
        return derivedProductId;
    }

    public void setDerivedProductId(String derivedProductId) {
        this.derivedProductId = derivedProductId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getDerivedProductName() {
        return derivedProductName;
    }

    public void setDerivedProductName(String derivedProductName) {
        this.derivedProductName = derivedProductName;
    }

    public String getRestrictedToUsername() {
        return restrictedToUsername;
    }

    public void setRestrictedToUsername(String restrictedToUsername) {
        this.restrictedToUsername = restrictedToUsername;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public Map<String, String> getProvidedProducts() {
        return providedProducts;
    }

    public void setProvidedProducts(Map<String, String> providedProducts) {
        this.providedProducts = providedProducts;
    }

    public Map<String, String> getDerivedProvidedProducts() {
        return derivedProvidedProducts;
    }

    public void setDerivedProvidedProducts(
            Map<String, String> derivedProvidedProducts) {
        this.derivedProvidedProducts = derivedProvidedProducts;
    }

    public Map<String, String> getDerivedProductAttributes() {
        return derivedProductAttributes;
    }

    public void setDerivedProductAttributes(
            Map<String, String> derivedProductAttributes) {
        this.derivedProductAttributes = derivedProductAttributes;
    }

}
