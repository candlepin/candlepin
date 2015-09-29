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
package org.candlepin.subservice.model;

import org.candlepin.model.AbstractHibernateObject;

import org.hibernate.annotations.Cascade;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.hibernate.annotations.GenericGenerator;



@Entity
@Table(name = "cps_subscriptions")
public class Subscription extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Size(max = 32)
    @NotNull
    protected String id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    @NotNull
    protected Product product;

    @ManyToOne
    @JoinColumn(name = "derived_product_id", nullable = false)
    @NotNull
    protected Product derivedProduct;

    @ManyToMany
    @JoinTable(
        name = "cps_sub_provided_products",
        joinColumns = {@JoinColumn(name = "subscription_id", insertable = false, updatable = false)},
        inverseJoinColumns = {@JoinColumn(name = "product_id")}
    )
    protected Set<Product> providedProducts;

    @ManyToMany
    @JoinTable(
        name = "cps_sub_derived_products",
        joinColumns = {@JoinColumn(name = "subscription_id", insertable = false, updatable = false)},
        inverseJoinColumns = {@JoinColumn(name = "product_id")}
    )
    protected Set<Product> derivedProvidedProducts;

    @OneToMany
    @JoinTable(name = "cps_sub_branding",
        joinColumns = @JoinColumn(name = "subscription_id"),
        inverseJoinColumns = @JoinColumn(name = "branding_id"))
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    protected Set<Branding> branding;

    @NotNull
    protected Long quantity;

    @NotNull
    protected Date startDate;

    @NotNull
    protected Date endDate;

    @Size(max = 255)
    protected String contractNumber;

    @Size(max = 255)
    protected String accountNumber;

    @Size(max = 255)
    protected String orderNumber;

    protected Date modified;

    @Size(max = 255)
    protected String upstreamPoolId;

    @Size(max = 37)
    protected String upstreamEntitlementId;

    @Size(max = 255)
    protected String upstreamConsumerId;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "certificate_id")
    protected SubscriptionCertificate cert;

    @OneToOne
    @JoinColumn(name = "cdn_id")
    protected Cdn cdn;


    public Subscription() {
        this.providedProducts = new HashSet<Product>();
        this.derivedProvidedProducts = new HashSet<Product>();
        this.branding = new HashSet<Branding>();
    }

    // TODO:
    // Add convenience constructors



    public String getId() {
        return this.id;
    }

    public Subscription setId(String id) {
        this.id = id;
        return this;
    }

    public Product getProduct() {
        return this.product;
    }

    public Subscription setProduct(Product product) {
        this.product = product;
        return this;
    }

    public Product getDerivedProduct() {
        return this.derivedProduct;
    }

    public Subscription setDerivedProduct(Product derivedProduct) {
        this.derivedProduct = derivedProduct;
        return this;
    }

    public Set<Product> getProvidedProducts() {
        return this.providedProducts;
    }

    public Subscription setProvidedProducts(Set<Product> providedProducts) {
        this.providedProducts.clear();

        if (providedProducts != null) {
            this.providedProducts.addAll(providedProducts);
        }

        return this;
    }

    public Set<Product> getDerivedProvidedProducts() {
        return this.derivedProvidedProducts;
    }

    public Subscription setDerivedProvidedProducts(Set<Product> derivedProvidedProducts) {
        this.derivedProvidedProducts.clear();

        if (derivedProvidedProducts != null) {
            this.derivedProvidedProducts.addAll(derivedProvidedProducts);
        }

        return this;
    }

    public Set<Branding> getBranding() {
        return this.branding;
    }

    public Subscription setBranding(Set<Branding> branding) {
        this.branding.clear();

        if (branding != null) {
            this.branding.addAll(branding);
        }

        return this;
    }

    public Long getQuantity() {
        return this.quantity;
    }

    public Subscription setQuantity(Long quantity) {
        this.quantity = quantity;
        return this;
    }

    public Date getStartDate() {
        return this.startDate;
    }

    public Subscription setStartDate(Date startDate) {
        this.startDate = startDate;
        return this;
    }

    public Date getEndDate() {
        return this.endDate;
    }

    public Subscription setEndDate(Date endDate) {
        this.endDate = endDate;
        return this;
    }

    public String getContractNumber() {
        return this.contractNumber;
    }

    public Subscription setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
        return this;
    }

    public String getAccountNumber() {
        return this.accountNumber;
    }

    public Subscription setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
        return this;
    }

    public String getOrderNumber() {
        return this.orderNumber;
    }

    public Subscription setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
        return this;
    }

    public Date getModified() {
        return this.modified;
    }

    public Subscription setModified(Date modified) {
        this.modified = modified;
        return this;
    }

    public String getUpstreamPoolId() {
        return this.upstreamPoolId;
    }

    public Subscription setUpstreamPoolId(String upstreamPoolId) {
        this.upstreamPoolId = upstreamPoolId;
        return this;
    }

    public String getUpstreamEntitlementId() {
        return this.upstreamEntitlementId;
    }

    public Subscription setUpstreamEntitlementId(String upstreamEntitlementId) {
        this.upstreamEntitlementId = upstreamEntitlementId;
        return this;
    }

    public String getUpstreamConsumerId() {
        return this.upstreamConsumerId;
    }

    public Subscription setUpstreamConsumerId(String upstreamConsumerId) {
        this.upstreamConsumerId = upstreamConsumerId;
        return this;
    }

    public SubscriptionCertificate getCertificate() {
        return this.cert;
    }

    public Subscription setCertifcate(SubscriptionCertificate cert) {
        this.cert = cert;
        return this;
    }

    public Cdn getCdn() {
        return this.cdn;
    }

    public Subscription setCdn(Cdn cdn) {
        this.cdn = cdn;
        return this;
    }

    public org.candlepin.model.dto.Subscription toSubscriptionDTO() {
        org.candlepin.model.dto.Subscription output = new org.candlepin.model.dto.Subscription();

        output.setId(this.id);
        output.setOwner(null);
        output.setCreated(this.getCreated());
        output.setUpdated(this.getUpdated());
        output.setProduct(this.product);
        output.setDerivedProduct(this.derivedProduct);
        output.setProvidedProducts(this.providedProducts);
        output.setDerivedProvidedProducts(this.derivedProvidedProducts);
        output.setBranding(this.branding);
        output.setQuantity(this.quantity);
        output.setStartDate(this.startDate);
        output.setEndDate(this.endDate);
        output.setContractNumber(this.contractNumber);
        output.setAccountNumber(this.accountNumber);
        output.setOrderNumber(this.orderNumber);
        output.setModified(this.modified);
        output.setUpstreamPoolId(this.upstreamPoolId);
        output.setUpstreamEntitlementId(this.upstreamEntitlementId);
        output.setUpstreamConsumerId(this.upstreamConsumerId);
        output.setCertificate(this.cert);
        output.setCdn(this.cdn);

        return output;
    }

}
