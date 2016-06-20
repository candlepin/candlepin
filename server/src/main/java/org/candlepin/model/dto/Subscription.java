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
package org.candlepin.model.dto;

import org.candlepin.model.Branding;
import org.candlepin.model.Cdn;
import org.candlepin.model.Eventful;
import org.candlepin.model.Named;
import org.candlepin.model.Owned;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.Pool;
import org.candlepin.model.SubscriptionsCertificate;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;



/**
 * Represents a Subscription
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFilter("DefaultFilter")
public class Subscription extends CandlepinDTO implements Owned, Named, Eventful {

    private String id;
    private Owner owner;
    private ProductData product;
    private ProductData derivedProduct;
    private Set<ProductData> providedProducts = new HashSet<ProductData>();
    private Set<ProductData> derivedProvidedProducts = new HashSet<ProductData>();
    private Set<Branding> branding = new HashSet<Branding>();
    private Long quantity;
    private Date startDate;
    private Date endDate;
    private String contractNumber;
    private String accountNumber;
    private Date modified;
    private String orderNumber;
    private String upstreamPoolId;
    private String upstreamEntitlementId;
    private String upstreamConsumerId;
    private SubscriptionsCertificate cert;
    private Cdn cdn;


    public Subscription() {
        // Intentionally left empty
    }

    public Subscription(Owner ownerIn, ProductData productIn, Set<ProductData> providedProducts,
        Long maxMembersIn, Date startDateIn, Date endDateIn, Date modified) {
        this.owner = ownerIn;
        this.product = productIn;
        this.providedProducts = providedProducts;
        this.quantity = maxMembersIn;
        this.startDate = startDateIn;
        this.endDate = endDateIn;
        this.modified = modified;
    }

    /**
     * Creates a new subscription DTO, copying data the given DTO
     *
     * @param source
     *  The source subscription DTO from which to copy data
     *
     * @throws IllegalArgumentException
     *  if pool is null
     */
    public Subscription(Subscription source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.populate(source);
    }

    /**
     * Creates a new subscription DTO, copying data the given pool entity.
     *
     * @param source
     *  The source pool entity from which to copy data
     *
     * @throws IllegalArgumentException
     *  if pool is null
     */
    public Subscription(Pool source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.populate(source);
    }

    public String toString() {
        String subscription = "Subscription [id = " + getId();
        if (product != null) {
            subscription += ", product = " + getProduct().getId();
        }
        if (owner != null) {
            subscription += ", owner = " + getOwner().getKey();
        }
        return subscription + "]";
    }

    /**
     * @return the subscription id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id subscription id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return then owner of the subscription.
     */
    public Owner getOwner() {
        return owner;
    }

    /**
     * @param owner The owner associated with the subscription.
     */
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    /**
     * @return the product associated with this subscription.
     */
    public ProductData getProduct() {
        return product;
    }

    /**
     * @param product The product associated with this subscription.
     */
    public void setProduct(ProductData product) {
        this.product = product;
    }

    /**
     * @return quantity of this subscription.
     */
    public Long getQuantity() {
        return quantity;
    }

    /**
     * @param quantity number of allowed usage.
     */
    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    /**
     * @return when the subscription started.
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * @param startDate when the subscription is to begin.
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * @return when the subscription ends.
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * @param endDate when the subscription ends.
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    /**
     * @return when the subscription was last changed.
     */
    public Date getModified() {
        return modified;
    }

    /**
     * @param modified when the subscription was changed.
     */
    public void setModified(Date modified) {
        this.modified = modified;
    }

    /**
     *
     * @return the subscription's contract number
     */
    public String getContractNumber() {
        return contractNumber;
    }

    /**
     * set the contract number
     * @param contractNumber
     */
    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    /**
     * set the account number
     * @param accountNumber
     */
    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    /**
     * @return the customer's account number
     */
    public String getAccountNumber() {
        return accountNumber;
    }

    /**
     * set the order number
     * @param orderNumber
     */
    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    /**
     * @return the order number
     */
    public String getOrderNumber() {
        return orderNumber;
    }

    /**
     * Check if this pool provides the given product ID.
     * @param desiredProductId
     * @return true if subscription provides product
     */
    public boolean provides(String desiredProductId) {
        if (desiredProductId == null) {
            throw new IllegalArgumentException("desiredProductId is null");
        }

        // Direct match?
        if (desiredProductId.equals(this.product.getUuid())) {
            return true;
        }

        // Check provided products:
        for (ProductData pd : providedProducts) {
            if (desiredProductId.equals(pd.getUuid())) {
                return true;
            }
        }

        return false;
    }

    public Set<ProductData> getProvidedProducts() {
        return providedProducts;
    }

    public void setProvidedProducts(Collection<ProductData> providedProducts) {
        this.providedProducts.clear();

        if (providedProducts != null) {
            this.providedProducts.addAll(providedProducts);
        }
    }

    public void setUpstreamPoolId(String upstreamPoolId) {
        this.upstreamPoolId = upstreamPoolId;
    }

    public String getUpstreamPoolId() {
        return upstreamPoolId;
    }

    public void setUpstreamEntitlementId(String upstreamEntitlementId) {
        this.upstreamEntitlementId = upstreamEntitlementId;
    }

    public String getUpstreamEntitlementId() {
        return upstreamEntitlementId;
    }

    public void setUpstreamConsumerId(String upstreamConsumerId) {
        this.upstreamConsumerId = upstreamConsumerId;
    }

    public String getUpstreamConsumerId() {
        return upstreamConsumerId;
    }

    public SubscriptionsCertificate getCertificate() {
        return cert;
    }

    public void setCertificate(SubscriptionsCertificate c) {
        cert = c;
    }

    public ProductData getDerivedProduct() {
        return derivedProduct;
    }

    public void setDerivedProduct(ProductData subProduct) {
        this.derivedProduct = subProduct;
    }

    public Set<ProductData> getDerivedProvidedProducts() {
        return derivedProvidedProducts;
    }

    public void setDerivedProvidedProducts(Collection<ProductData> subProvidedProducts) {
        this.derivedProvidedProducts.clear();

        if (subProvidedProducts != null) {
            this.derivedProvidedProducts.addAll(subProvidedProducts);
        }
    }

    public Cdn getCdn() {
        return cdn;
    }

    public void setCdn(Cdn cdn) {
        this.cdn = cdn;
    }

    public Set<Branding> getBranding() {
        return branding;
    }

    public void setBranding(Set<Branding> branding) {
        this.branding = branding;
    }

    public boolean isStacked() {
        return !StringUtils.isBlank(this.product.getAttributeValue("stacking_id"));
    }

    public String getStackId() {
        // Check if we are stacked first so we return null over empty string
        // when stacking_id = ""
        if (this.isStacked()) {
            return this.product.getAttributeValue("stacking_id");
        }
        return null;
    }

    public boolean createsSubPools() {
        String virtLimit = this.getProduct().getAttributeValue("virt_limit");
        return !StringUtils.isBlank(virtLimit) && !"0".equals(virtLimit);
    }

    @XmlTransient
    public String getName() {
        if (getProduct() != null) {
            return getProduct().getName();
        }
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Subscription)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        Subscription that = (Subscription) obj;

        EqualsBuilder builder = new EqualsBuilder()
            .append(this.id, that.id)
            .append(this.owner, that.owner)
            .append(this.product, that.product)
            .append(this.derivedProduct, that.derivedProduct)
            .append(this.providedProducts, that.providedProducts)
            .append(this.derivedProvidedProducts, that.derivedProvidedProducts)
            .append(this.branding, that.branding)
            .append(this.quantity, that.quantity)
            .append(this.startDate, that.startDate)
            .append(this.endDate, that.endDate)
            .append(this.contractNumber, that.contractNumber)
            .append(this.accountNumber, that.accountNumber)
            .append(this.modified, that.modified)
            .append(this.orderNumber, that.orderNumber)
            .append(this.upstreamPoolId, that.upstreamPoolId)
            .append(this.upstreamEntitlementId, that.upstreamEntitlementId)
            .append(this.upstreamConsumerId, that.upstreamConsumerId)
            .append(this.cert, that.cert)
            .append(this.cdn, that.cdn);

        return super.equals(obj) && builder.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.id)
            .append(this.owner)
            .append(this.product)
            .append(this.derivedProduct)
            .append(this.providedProducts)
            .append(this.derivedProvidedProducts)
            .append(this.branding)
            .append(this.quantity)
            .append(this.startDate)
            .append(this.endDate)
            .append(this.contractNumber)
            .append(this.accountNumber)
            .append(this.modified)
            .append(this.orderNumber)
            .append(this.upstreamPoolId)
            .append(this.upstreamEntitlementId)
            .append(this.upstreamConsumerId)
            .append(this.cert)
            .append(this.cdn);

        return builder.toHashCode();
    }

    @Override
    public Object clone() {
        Subscription copy = (Subscription) super.clone();

        copy.product = this.product != null ? (ProductData) this.product.clone() : null;
        copy.derivedProduct = this.derivedProduct != null ? (ProductData) this.derivedProduct.clone() : null;
        copy.startDate = this.startDate != null ? (Date) this.startDate.clone() : null;
        copy.endDate = this.endDate != null ? (Date) this.endDate.clone() : null;
        copy.modified = this.modified != null ? (Date) this.modified.clone() : null;

        if (this.providedProducts != null) {
            copy.providedProducts = new HashSet<ProductData>();

            for (ProductData dto : this.providedProducts) {
                copy.providedProducts.add((ProductData) dto.clone());
            }
        }

        if (this.derivedProvidedProducts != null) {
            copy.derivedProvidedProducts = new HashSet<ProductData>();

            for (ProductData dto : this.derivedProvidedProducts) {
                copy.derivedProvidedProducts.add((ProductData) dto.clone());
            }
        }

        if (this.branding != null) {
            copy.branding = new HashSet<Branding>();
            copy.branding.addAll(this.branding);
        }

        return copy;
    }

    /**
     * Populates this DTO with the data from the given source DTO.
     *
     * @param source
     *  The source DTO from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @return
     *  a reference to this DTO
     */
    public Subscription populate(Subscription source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        super.populate(source);

        this.setId(source.getId());
        this.setOwner(source.getOwner());
        this.setProduct(source.getProduct());
        this.setProvidedProducts(source.getProvidedProducts());
        this.setDerivedProduct(source.getDerivedProduct());
        this.setDerivedProvidedProducts(source.getDerivedProvidedProducts());
        this.setQuantity(source.getQuantity());
        this.setStartDate(source.getStartDate());
        this.setEndDate(source.getEndDate());
        this.setUpstreamEntitlementId(source.getUpstreamEntitlementId());
        this.setCdn(source.getCdn());
        this.setCertificate(source.getCertificate());
        this.setBranding(source.getBranding());

        return this;
    }

    /**
     * Populates this DTO with data from the given source entity.
     *
     * @param source
     *  The source entity from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @return
     *  a reference to this DTO
     */
    public Subscription populate(Pool source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        super.populate(source);

        this.setId(source.getSubscriptionId());

        this.setOwner(source.getOwner());
        this.setOwner(source.getOwner());
        this.setQuantity(source.getQuantity());
        this.setStartDate(source.getStartDate());
        this.setEndDate(source.getEndDate());
        this.setUpstreamEntitlementId(source.getUpstreamEntitlementId());
        this.setCdn(source.getCdn());
        this.setCertificate(source.getCertificate());

        //Pool.getBranding should return entity Branding. The Branding
        //entity has no futher relationships so everything should be ok.
        this.setBranding(source.getBranding());

        // Map actual products into product data
        this.setProduct(source.getProduct() != null ? new ProductData(source.getProduct()) : null);
        this.setDerivedProduct(
            source.getDerivedProduct() != null ? new ProductData(source.getDerivedProduct()) : null
        );

        Collection<Product> products = source.getProvidedProducts();
        if (products != null) {
            Collection<ProductData> pdata = new LinkedList<ProductData>();

            for (Product product : products) {
                pdata.add(product.toDTO());
            }

            this.setProvidedProducts(pdata);
        }
        else {
            this.setProvidedProducts(null);
        }

        products = source.getDerivedProvidedProducts();
        if (products != null) {
            Collection<ProductData> pdata = new LinkedList<ProductData>();

            for (Product product : products) {
                pdata.add(product.toDTO());
            }

            this.setDerivedProvidedProducts(pdata);
        }
        else {
            this.setDerivedProvidedProducts(null);
        }

        return this;
    }

}
