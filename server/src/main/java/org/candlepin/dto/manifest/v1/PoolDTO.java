/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.candlepin.common.jackson.HateoasInclude;
import org.candlepin.dto.CandlepinDTO;
import org.candlepin.util.SetView;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * A DTO representation of the Pool entity as used by the manifest import/export framework.
 */
@XmlRootElement(name = "pool")
@XmlAccessorType(XmlAccessType.PROPERTY)
@JsonFilter("PoolFilter")
public class PoolDTO extends CandlepinDTO<PoolDTO> {
    public static final long serialVersionUID = 1L;

    /**
     * Internal DTO object for ProvidedProduct
     */
    @JsonFilter("ProvidedProductFilter")
    public static class ProvidedProductDTO {
        private final String productId;
        private final String productName;

        @JsonCreator
        public ProvidedProductDTO(
            @JsonProperty("productId") String productId,
            @JsonProperty("productName") String productName) {
            if (productId == null || productId.isEmpty()) {
                throw new IllegalArgumentException("The product id is null or empty.");
            }

            this.productId = productId;
            this.productName = productName;
        }

        public String getProductId() {
            return this.productId;
        }

        public String getProductName() {
            return this.productName;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj instanceof ProvidedProductDTO) {
                ProvidedProductDTO that = (ProvidedProductDTO) obj;

                EqualsBuilder builder = new EqualsBuilder()
                    .append(this.getProductId(), that.getProductId())
                    .append(this.getProductName(), that.getProductName());

                return builder.isEquals();
            }

            return false;
        }

        @Override
        public int hashCode() {
            HashCodeBuilder builder = new HashCodeBuilder(37, 7)
                .append(this.getProductId())
                .append(this.getProductName());

            return builder.toHashCode();
        }
    }

    private String id;

    private String contractNumber;
    private String accountNumber;
    private String orderNumber;

    private Set<BrandingDTO> branding;

    private String productId;
    private String derivedProductId;

    private Set<ProvidedProductDTO> providedProducts;
    private Set<ProvidedProductDTO> derivedProvidedProducts;

    private Date startDate;
    private Date endDate;

    /**
     * Initializes a new PoolDTO instance with null values.
     */
    public PoolDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new PoolDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public PoolDTO(PoolDTO source) {
        super(source);
    }

    /**
     * Returns the internal db id.
     *
     * @return the db id.
     */
    @HateoasInclude
    public String getId() {
        return id;
    }

    /**
     * Sets the internal db id.
     *
     * @param id new db id.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Return the contract number for this pool's subscription.
     *
     * @return the contract number.
     */
    public String getContractNumber() {
        return contractNumber;
    }

    /**
     * Sets the contract number for this pool's subscription.
     *
     * @param contractNumber set the contract number of this subscription
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
        return this;
    }

    /**
     * Returns this pool's account number.
     *
     * @return this pool's account number.
     */
    public String getAccountNumber() {
        return accountNumber;
    }

    /**
     * Sets this pool's account number.
     *
     * @param accountNumber the account number to set on this pool.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
        return this;
    }

    /**
     * Returns this pool's order number.
     *
     * @return this pool's order number.
     */
    public String getOrderNumber() {
        return orderNumber;
    }

    /**
     * Sets this pool's order number.
     *
     * @param orderNumber the order number to set on this pool.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
        return this;
    }

    /**
     * Retrieves a view of the branding for the pool represented by this DTO. If the branding items
     * have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * set of branding items. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this pool DTO instance.
     *
     * @return
     *  the branding items associated with this key, or null if they have not yet been defined
     */
    public Set<BrandingDTO> getBranding() {
        return this.branding != null ? new SetView<>(this.branding) : null;
    }

    /**
     * Adds the collection of branding items to this Pool DTO.
     *
     * @param branding
     *  A set of branding items to attach to this DTO, or null to clear the existing ones
     *
     * @return
     *  A reference to this DTO
     */
    public PoolDTO setBranding(Set<BrandingDTO> branding) {
        if (branding != null) {
            if (this.branding == null) {
                this.branding = new HashSet<>();
            }
            else {
                this.branding.clear();
            }

            for (BrandingDTO dto : branding) {
                if (isNullOrIncomplete(dto)) {
                    throw new IllegalArgumentException(
                        "collection contains null or incomplete branding objects");
                }
            }

            this.branding.addAll(branding);
        }
        else {
            this.branding = null;
        }
        return this;
    }

    /**
     * Adds the given branding to this pool DTO.
     *
     * @param branding
     *  The branding to add to this pool DTO.
     *
     * @return
     *  true if this branding was not already contained in this pool DTO.
     */
    @JsonIgnore
    public boolean addBranding(BrandingDTO branding) {
        if (isNullOrIncomplete(branding)) {
            throw new IllegalArgumentException("branding is null or incomplete");
        }

        if (this.branding == null) {
            this.branding = new HashSet<>();
        }

        return this.branding.add(branding);
    }

    /**
     * Utility method to validate BrandingDTO input
     */
    private boolean isNullOrIncomplete(BrandingDTO branding) {
        return branding == null ||
            branding.getProductId() == null || branding.getProductId().isEmpty() ||
            branding.getName() == null || branding.getName().isEmpty() ||
            branding.getType() == null || branding.getType().isEmpty();
    }

    /**
     * Return the "top level" product this pool is for.
     * Note that pools can also provide access to other products.
     * See getProvidedProducts().
     *
     * @return Top level product ID.
     */
    @HateoasInclude
    public String getProductId() {
        return productId;
    }

    /**
     * Set the "top level" product this pool is for.
     * Note that pools can also provide access to other products.
     * See getProvidedProducts().
     *
     * @param productId Top level product ID.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setProductId(String productId) {
        this.productId = productId;
        return this;
    }

    /**
     * Returns the derived product id of this pool.
     *
     * @return the derived product id of this pool.
     */
    public String getDerivedProductId() {
        return derivedProductId;
    }

    /**
     * Sets the derived product id of this pool.
     *
     * @param derivedProductId set the derived product id of this pool.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setDerivedProductId(String derivedProductId) {
        this.derivedProductId = derivedProductId;
        return this;
    }

    /**
     * Retrieves a view of the provided products for the pool represented by this DTO.
     * If the provided products have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * set of provided products. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this pool DTO instance.
     *
     * @return
     *  the provided products associated with this key, or null if they have not yet been defined
     */
    public Set<ProvidedProductDTO> getProvidedProducts() {
        return this.providedProducts != null ? new SetView<>(this.providedProducts) : null;
    }

    /**
     * Adds the collection of provided products to this Pool DTO.
     *
     * @param providedProducts
     *  A set of provided products to attach to this DTO, or null to clear the existing ones
     *
     * @return
     *  A reference to this DTO
     */
    public PoolDTO setProvidedProducts(Set<ProvidedProductDTO> providedProducts) {
        if (providedProducts != null) {
            if (this.providedProducts == null) {
                this.providedProducts = new HashSet<>();
            }
            else {
                this.providedProducts.clear();
            }

            for (ProvidedProductDTO dto : providedProducts) {
                if (isNullOrIncomplete(dto)) {
                    throw new IllegalArgumentException(
                        "collection contains null or incomplete provided products");
                }
            }

            this.providedProducts.addAll(providedProducts);
        }
        else {
            this.providedProducts = null;
        }
        return this;
    }

    /**
     * Adds the given provided product to this pool DTO.
     *
     * @param providedProduct
     *  The provided product to add to this pool DTO.
     *
     * @return
     *  true if this provided product was not already contained in this pool DTO.
     */
    @JsonIgnore
    public boolean addProvidedProduct(ProvidedProductDTO providedProduct) {
        if (isNullOrIncomplete(providedProduct)) {
            throw new IllegalArgumentException("providedProduct is null or incomplete");
        }

        if (this.providedProducts == null) {
            this.providedProducts = new HashSet<>();
        }

        return this.providedProducts.add(providedProduct);
    }

    /**
     * Retrieves a view of the derived provided products for the pool represented by this DTO.
     * If the derived provided products have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * set of derived provided products. Elements cannot be added to the collection,
     * but elements may be removed.
     * Changes made to the collection will be reflected by this pool DTO instance.
     *
     * @return
     *  the derived provided products associated with this key, or null if they have not yet been defined
     */
    public Set<ProvidedProductDTO> getDerivedProvidedProducts() {
        return this.derivedProvidedProducts != null ?
            new SetView<>(this.derivedProvidedProducts) : null;
    }

    /**
     * Adds the collection of derived provided products to this Pool DTO.
     *
     * @param derivedProvidedProducts
     *  A set of derived provided products to attach to this DTO, or null to clear the existing ones
     *
     * @return
     *  A reference to this DTO
     */
    public PoolDTO setDerivedProvidedProducts(Set<ProvidedProductDTO> derivedProvidedProducts) {
        if (derivedProvidedProducts != null) {
            if (this.derivedProvidedProducts == null) {
                this.derivedProvidedProducts = new HashSet<>();
            }
            else {
                this.derivedProvidedProducts.clear();
            }

            for (ProvidedProductDTO dto : derivedProvidedProducts) {
                if (isNullOrIncomplete(dto)) {
                    throw new IllegalArgumentException(
                        "collection contains null or incomplete derived provided products");
                }
            }

            this.derivedProvidedProducts.addAll(derivedProvidedProducts);
        }
        else {
            this.derivedProvidedProducts = null;
        }
        return this;
    }

    /**
     * Adds the given derived provided product to this pool DTO.
     *
     * @param derivedProvidedProduct
     *  The derived provided product to add to this pool DTO.
     *
     * @return
     *  true if this derived provided product was not already contained in this pool DTO.
     */
    @JsonIgnore
    public boolean addDerivedProvidedProduct(ProvidedProductDTO derivedProvidedProduct) {
        if (isNullOrIncomplete(derivedProvidedProduct)) {
            throw new IllegalArgumentException("derivedProvidedProduct is null or incomplete");
        }

        if (this.derivedProvidedProducts == null) {
            this.derivedProvidedProducts = new HashSet<>();
        }

        return this.derivedProvidedProducts.add(derivedProvidedProduct);
    }

    /**
     * Utility method to validate ProvidedProductDTO input
     */
    private boolean isNullOrIncomplete(ProvidedProductDTO derivedProvidedProduct) {
        return derivedProvidedProduct == null ||
            derivedProvidedProduct.getProductId() == null ||
            derivedProvidedProduct.getProductId().isEmpty();
    }


    /**
     * Returns the start date of this pool.
     *
     * @return Returns the startDate of this pool.
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * Sets the start date of this pool.
     *
     * @param startDate the startDate of this pool.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setStartDate(Date startDate) {
        this.startDate = startDate;
        return this;
    }

    /**
     * Returns the end date of this pool.
     *
     * @return Returns the endDate of this pool.
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * Sets the end date of this pool.
     *
     * @param endDate the endDate of this pool.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setEndDate(Date endDate) {
        this.endDate = endDate;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("PoolDTO [id: %s, product id: %s, derived product id: %s, end date: %s]",
            this.getId(), this.getProductId(), this.getDerivedProductId(), this.getEndDate());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof PoolDTO) {
            PoolDTO that = (PoolDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getContractNumber(), that.getContractNumber())
                .append(this.getAccountNumber(), that.getAccountNumber())
                .append(this.getOrderNumber(), that.getOrderNumber())
                .append(this.getBranding(), that.getBranding())
                .append(this.getProductId(), that.getProductId())
                .append(this.getDerivedProductId(), that.getDerivedProductId())
                .append(this.getProvidedProducts(), that.getProvidedProducts())
                .append(this.getDerivedProvidedProducts(), that.getDerivedProvidedProducts())
                .append(this.getStartDate(), that.getStartDate())
                .append(this.getEndDate(), that.getEndDate());

            return builder.isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(this.getId())
            .append(this.getContractNumber())
            .append(this.getAccountNumber())
            .append(this.getOrderNumber())
            .append(this.getBranding())
            .append(this.getProductId())
            .append(this.getDerivedProductId())
            .append(this.getProvidedProducts())
            .append(this.getDerivedProvidedProducts())
            .append(this.getEndDate())
            .append(this.getStartDate());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolDTO clone() {
        PoolDTO copy = super.clone();

        copy.setBranding(this.getBranding());
        copy.setProvidedProducts(this.getProvidedProducts());
        copy.setDerivedProvidedProducts(this.getDerivedProvidedProducts());

        copy.endDate = this.endDate != null ? (Date) this.endDate.clone() : null;
        copy.startDate = this.startDate != null ? (Date) this.startDate.clone() : null;

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolDTO populate(PoolDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setContractNumber(source.getContractNumber());
        this.setAccountNumber(source.getAccountNumber());
        this.setOrderNumber(source.getOrderNumber());
        this.setBranding(source.getBranding());
        this.setProductId(source.getProductId());
        this.setDerivedProductId(source.getDerivedProductId());
        this.setProvidedProducts(source.getProvidedProducts());
        this.setDerivedProvidedProducts(source.getDerivedProvidedProducts());
        this.setEndDate(source.getEndDate());
        this.setStartDate(source.getStartDate());

        return this;
    }
}
