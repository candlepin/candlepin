/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.manifest.v1;

import org.candlepin.dto.CandlepinDTO;
import org.candlepin.model.Eventful;
import org.candlepin.model.Named;
import org.candlepin.model.Owned;
import org.candlepin.service.model.SubscriptionInfo;
import org.candlepin.util.ListView;
import org.candlepin.util.Util;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;



/**
 * A DTO representation of a subscription generated from an entitlement during import.
 *
 * <strong>WARNING</strong>: The Eventful, Named and Owned interfaces are only implemented as a
 * temporary requirement to be compatible with the event framework. Once the event framework has
 * been updated, these interfaces should be dropped from this object.
 */
@XmlRootElement(name = "subscription")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SubscriptionDTO extends CandlepinDTO<SubscriptionDTO> implements SubscriptionInfo,
    Eventful, Named, Owned {

    public static final long serialVersionUID = 1L;

    private String id;
    private OwnerDTO owner;

    private ProductDTO product;
    private List<ProductDTO> providedProducts;
    private ProductDTO derivedProduct;
    private List<ProductDTO> derivedProvidedProducts;

    private Long quantity;

    private Date startDate;
    private Date endDate;
    private Date lastModifiedDate;

    private String contractNumber;
    private String accountNumber;
    private String orderNumber;
    private String upstreamPoolId;
    private String upstreamEntitlementId;
    private String upstreamConsumerId;

    private CdnDTO cdn;
    private List<BrandingDTO> branding;
    private CertificateDTO certificate;


    /**
     * Initializes a new SubscriptionDTO instance with null values.
     */
    public SubscriptionDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new SubscriptionDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public SubscriptionDTO(SubscriptionDTO source) {
        super(source);
    }

    /**
     * Provided for compatibility with the Named interface for use with the eventing framework
     *
     * @deprecated
     *  This will be removed upon reworking the event framework
     *
     * @return
     *  The name of the product associated with this subscription
     */
    @Override
    @Deprecated
    @JsonIgnore
    public String getName() {
        return this.getProduct() != null ? this.getProduct().getName() : null;
    }

    /**
     * Provided for compatibility with the Owned interface for use with the eventing framework
     *
     * @deprecated
     *  This will be removed upon reworking the event framework
     *
     * @return
     *  The ID of the owner associated with this subscription
     */
    @Override
    @Deprecated
    @JsonIgnore
    public String getOwnerId() {
        return this.getOwner() != null ? this.getOwner().getId() : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getId() {
        return id;
    }

    /**
     * Sets or clears the database ID for this subscription.
     *
     * @param id
     *  The new database ID for this subscription, or null to clear the ID
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO getOwner() {
        return owner;
    }

    /**
     * Sets or clears the owner this subscription.
     *
     * @param owner
     *  The new owner this subscription, or null to clear the owner
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setOwner(OwnerDTO owner) {
        this.owner = owner;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductDTO getProduct() {
        return this.product;
    }

    /**
     * Sets or clears the product of this subscription.
     *
     * @param product
     *  The new product of this subscription, or null to clear the product
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setProduct(ProductDTO product) {
        this.product = product;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Collection<ProductDTO> getProvidedProducts() {
        return this.providedProducts != null ? new ListView<>(this.providedProducts) : null;
    }

    /**
     * Sets or clears the products provided by this subscription.
     *
     * @param products
     *  A collection of products to set as the provided products of this subscription, or null to
     *  indicate no change in provided products
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setProvidedProducts(Collection<? extends ProductDTO> products) {
        if (products != null) {
            if (this.providedProducts == null) {
                this.providedProducts = new ArrayList(products.size());
            }

            this.providedProducts.clear();

            for (ProductDTO product : products) {
                if (product == null || product.getId() == null || product.getId().isEmpty()) {
                    throw new IllegalArgumentException("products contains a null or incomplete product");
                }

                this.providedProducts.add(product);
            }
        }
        else {
            this.providedProducts = null;
        }

        return this;
    }

    // TODO: Add other CRUD operations here as need dictates

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductDTO getDerivedProduct() {
        return this.derivedProduct;
    }

    /**
     * Sets or clears the product provided by subscriptions derived from this subscription.
     *
     * @param product
     *  The new derived product of this subscription, or null to clear the derived product
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setDerivedProduct(ProductDTO product) {
        this.derivedProduct = product;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Collection<ProductDTO> getDerivedProvidedProducts() {
        return this.derivedProvidedProducts != null ? new ListView<>(this.derivedProvidedProducts) : null;
    }

    /**
     * Sets or clears the products provided by subscriptions derived from this subscription.
     *
     * @param products
     *  A collection of products to set as the derived provided products of this subscription, or
     *  null to indicate no change in derived provided products
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setDerivedProvidedProducts(Collection<? extends ProductDTO> products) {
        if (products != null) {
            if (this.derivedProvidedProducts == null) {
                this.derivedProvidedProducts = new ArrayList(Math.max(10, products.size()));
            }

            this.derivedProvidedProducts.clear();

            for (ProductDTO product : products) {
                if (product == null || product.getId() == null || product.getId().isEmpty()) {
                    throw new IllegalArgumentException("products contains a null or incomplete product");
                }

                this.derivedProvidedProducts.add(product);
            }
        }
        else {
            this.derivedProvidedProducts = null;
        }

        return this;
    }

    // TODO: Add other CRUD operations here as need dictates

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getQuantity() {
        return quantity;
    }

    /**
     * Sets or clears the provided quantity of this subscription
     *
     * @param quantity
     *  The provided quantity of this subscription, or null to clear the quantity
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setQuantity(Long quantity) {
        this.quantity = quantity;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Date getStartDate() {
        return this.startDate;
    }

    /**
     * Sets or clears the start date of this subscription
     *
     * @param date
     *  The start date of this subscription, or null to clear the start date
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setStartDate(Date date) {
        this.startDate = date;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Date getEndDate() {
        return this.endDate;
    }

    /**
     * Sets or clears the end date of this subscription
     *
     * @param date
     *  The end date of this subscription, or null to clear the end date
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setEndDate(Date date) {
        this.endDate = date;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Date getLastModified() {
        return this.lastModifiedDate;
    }

    /**
     * Sets or clears the last modified date of this subscription
     *
     * @param date
     *  The last modified date of this subscription, or null to clear the last modified date
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setLastModified(Date date) {
        this.lastModifiedDate = date;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getContractNumber() {
        return this.contractNumber;
    }

    /**
     * Sets or clears the contract number of this subscription
     *
     * @param contractNumber
     *  The contract number of this subscription, or null to clear the contract number
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAccountNumber() {
        return this.accountNumber;
    }

    /**
     * Sets or clears the account number of this subscription
     *
     * @param accountNumber
     *  The account number of this subscription, or null to clear the account number
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOrderNumber() {
        return this.orderNumber;
    }

    /**
     * Sets or clears the order number of this subscription
     *
     * @param orderNumber
     *  The order number of this subscription, or null to clear the order number
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUpstreamPoolId() {
        return this.upstreamPoolId;
    }

    /**
     * Sets or clears the upstream pool ID of this subscription
     *
     * @param upstreamPoolId
     *  The upstream pool ID of this subscription, or null to clear the upstream pool ID
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setUpstreamPoolId(String upstreamPoolId) {
        this.upstreamPoolId = upstreamPoolId;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUpstreamEntitlementId() {
        return this.upstreamEntitlementId;
    }

    /**
     * Sets or clears the upstream entitlement ID of this subscription
     *
     * @param upstreamEntitlementId
     *  The upstream entitlement ID of this subscription, or null to clear the upstream entitlement ID
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setUpstreamEntitlementId(String upstreamEntitlementId) {
        this.upstreamEntitlementId = upstreamEntitlementId;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUpstreamConsumerId() {
        return this.upstreamConsumerId;
    }

    /**
     * Sets or clears the upstream consumer ID of this subscription
     *
     * @param upstreamConsumerId
     *  The upstream consumer ID of this subscription, or null to clear the upstream consumer ID
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setUpstreamConsumerId(String upstreamConsumerId) {
        this.upstreamConsumerId = upstreamConsumerId;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CdnDTO getCdn() {
        return this.cdn;
    }

    /**
     * Sets or clears the CDN of this subscription
     *
     * @param cdn
     *  The CDN of this subscription, or null to clear the CDN
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setCdn(CdnDTO cdn) {
        this.cdn = cdn;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Collection<BrandingDTO> getBranding() {
        return this.branding != null ? new ListView<>(this.branding) : null;
    }

    /**
     * Sets or clears the branding information for products provided by subscriptions.
     *
     * @param branding
     *  A collection of branding instances to set as the branding for products provided by this
     *  subscription, or null to indicate no change in branding
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setBranding(Collection<? extends BrandingDTO> branding) {
        if (branding != null) {
            if (this.branding == null) {
                this.branding = new ArrayList(Math.max(10, branding.size()));
            }

            this.branding.clear();

            for (BrandingDTO bdata : branding) {
                if (bdata == null || bdata.getProductId() == null || bdata.getProductId().isEmpty()) {
                    throw new IllegalArgumentException(
                        "branding contains a null or incomplete branding instance");
                }

                this.branding.add(bdata);
            }
        }
        else {
            this.branding = null;
        }

        return this;
    }


    // TODO: Add other CRUD operations here as need dictates

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateDTO getCertificate() {
        return this.certificate;
    }

    /**
     * Sets or clears the certificate of this subscription
     *
     * @param certificate
     *  The certificate of this subscription, or null to clear the certificate
     *
     * @return
     *  a reference to this SubscriptionDTO
     */
    public SubscriptionDTO setCertificate(CertificateDTO certificate) {
        this.certificate = certificate;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        ProductDTO product = this.getProduct();

        String pid = product != null ? product.getId() : null;
        String pname = product != null ? product.getName() : null;

        return String.format("SubscriptionDTO [id: %s, product id: %s, product name: %s, quantity: %s]",
            this.getId(), pid, pname, this.getQuantity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof SubscriptionDTO) {
            SubscriptionDTO that = (SubscriptionDTO) obj;

            // We are not interested in making sure nested objects are equal; we're only
            // concerned with the reference to such an object.
            String thisOwnerId = this.getOwner() != null ? this.getOwner().getId() : null;
            String thatOwnerId = that.getOwner() != null ? that.getOwner().getId() : null;

            String thisProductId = this.getProduct() != null ? this.getProduct().getId() : null;
            String thatProductId = that.getProduct() != null ? that.getProduct().getId() : null;

            String thisDerivedProductId =
                this.getDerivedProduct() != null ? this.getDerivedProduct().getId() : null;
            String thatDerivedProductId =
                that.getDerivedProduct() != null ? that.getDerivedProduct().getId() : null;

            String thisCdnId = this.getCdn() != null ? this.getCdn().getId() : null;
            String thatCdnId = that.getCdn() != null ? that.getCdn().getId() : null;

            String thisCertificateId =
                this.getCertificate() != null ? this.getCertificate().getId() : null;
            String thatCertificateId =
                that.getCertificate() != null ? that.getCertificate().getId() : null;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(thisOwnerId, thatOwnerId)
                .append(thisProductId, thatProductId)
                .append(thisDerivedProductId, thatDerivedProductId)
                .append(this.getQuantity(), that.getQuantity())
                .append(this.getStartDate(), that.getStartDate())
                .append(this.getEndDate(), that.getEndDate())
                .append(this.getLastModified(), that.getLastModified())
                .append(this.getContractNumber(), that.getContractNumber())
                .append(this.getAccountNumber(), that.getAccountNumber())
                .append(this.getOrderNumber(), that.getOrderNumber())
                .append(this.getUpstreamPoolId(), that.getUpstreamPoolId())
                .append(this.getUpstreamEntitlementId(), that.getUpstreamEntitlementId())
                .append(this.getUpstreamConsumerId(), that.getUpstreamConsumerId())
                .append(thisCdnId, thatCdnId)
                .append(thisCertificateId, thatCertificateId);

            boolean equals = builder.isEquals();

            equals = equals &&
                Util.collectionsAreEqual(this.getProvidedProducts(), that.getProvidedProducts(),
                    (lhs, rhs) -> (lhs == rhs || (lhs != null && rhs != null &&
                        lhs.getId() != null && lhs.getId().equals(rhs.getId()))) ? 0 : 1);

            equals = equals &&
                Util.collectionsAreEqual(this.getDerivedProvidedProducts(), that.getDerivedProvidedProducts(),
                    (lhs, rhs) -> (lhs == rhs || (lhs != null && rhs != null &&
                        lhs.getId() != null && lhs.getId().equals(rhs.getId()))) ? 0 : 1);

            equals = equals &&
                Util.collectionsAreEqual(this.getBranding(), that.getBranding(),
                    (lhs, rhs) -> (lhs == rhs || (lhs != null && rhs != null &&
                        lhs.getId() != null && lhs.getId().equals(rhs.getId()))) ? 0 : 1);

            return equals;
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        // Like with the equals method, we are not interested in hashing nested objects; we're only
        // concerned with the reference to such an object.
        String thisOwnerId = this.getOwner() != null ? this.getOwner().getId() : null;
        String thisProductId = this.getProduct() != null ? this.getProduct().getId() : null;
        String thisDerivedProductId =
            this.getDerivedProduct() != null ? this.getDerivedProduct().getId() : null;

        String thisCdnId = this.getCdn() != null ? this.getCdn().getId() : null;
        String thisCertificateId = this.getCertificate() != null ? this.getCertificate().getId() : null;

        int ppAccumulator = 0;
        int dppAccumulator = 0;
        int bAccumulator = 0;

        Collection<ProductDTO> products = this.getProvidedProducts();
        if (products != null) {
            for (ProductDTO product : products) {
                ppAccumulator = ppAccumulator * 17 +
                    (product != null && product.getId() != null ? product.getId().hashCode() : 0);
            }
        }

        products = this.getDerivedProvidedProducts();
        if (products != null) {
            for (ProductDTO product : products) {
                dppAccumulator = dppAccumulator * 17 +
                    (product != null && product.getId() != null ? product.getId().hashCode() : 0);
            }
        }

        Collection<BrandingDTO> branding = this.getBranding();
        if (branding != null) {
            for (BrandingDTO bdata : branding) {
                bAccumulator = bAccumulator * 17 +
                    (bdata != null && bdata.getId() != null ? bdata.getId().hashCode() : 0);
            }
        }

        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(this.getId())
            .append(thisOwnerId)
            .append(thisProductId)
            .append(thisDerivedProductId)
            .append(this.getQuantity())
            .append(this.getStartDate())
            .append(this.getEndDate())
            .append(this.getLastModified())
            .append(this.getContractNumber())
            .append(this.getAccountNumber())
            .append(this.getOrderNumber())
            .append(this.getUpstreamPoolId())
            .append(this.getUpstreamEntitlementId())
            .append(this.getUpstreamConsumerId())
            .append(thisCdnId)
            .append(thisCertificateId)
            .append(ppAccumulator)
            .append(dppAccumulator)
            .append(bAccumulator);

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SubscriptionDTO clone() {
        SubscriptionDTO copy = super.clone();

        OwnerDTO owner = this.getOwner();
        copy.setOwner(owner != null ? owner.clone() : null);

        ProductDTO product = this.getProduct();
        copy.setProduct(product != null ? product.clone() : null);

        ProductDTO derivedProduct = this.getDerivedProduct();
        copy.setDerivedProduct(derivedProduct != null ? derivedProduct.clone() : null);

        copy.setProvidedProducts(this.getProvidedProducts());
        copy.setDerivedProvidedProducts(this.getDerivedProvidedProducts());

        Date endDate = this.getEndDate();
        copy.setEndDate(endDate != null ? (Date) endDate.clone() : null);

        Date startDate = this.getStartDate();
        copy.setStartDate(startDate != null ? (Date) startDate.clone() : null);

        Date lastModified = this.getLastModified();
        copy.setLastModified(lastModified != null ? (Date) lastModified.clone() : null);

        CdnDTO cdn = this.getCdn();
        copy.setCdn(cdn != null ? cdn.clone() : null);

        CertificateDTO certificate = this.getCertificate();
        copy.setCertificate(certificate != null ? certificate.clone() : null);

        copy.setBranding(this.getBranding());

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SubscriptionDTO populate(SubscriptionDTO source) {
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
        this.setLastModified(source.getLastModified());

        this.setContractNumber(source.getContractNumber());
        this.setAccountNumber(source.getAccountNumber());
        this.setOrderNumber(source.getOrderNumber());
        this.setUpstreamPoolId(source.getUpstreamPoolId());
        this.setUpstreamEntitlementId(source.getUpstreamEntitlementId());
        this.setUpstreamConsumerId(source.getUpstreamConsumerId());

        this.setCdn(source.getCdn());
        this.setBranding(source.getBranding());
        this.setCertificate(source.getCertificate());

        return this;
    }
}
