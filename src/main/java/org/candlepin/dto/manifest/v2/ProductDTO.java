/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

package org.candlepin.dto.manifest.v2;

import org.candlepin.dto.TimestampedCandlepinDTO;
import org.candlepin.util.MapView;
import org.candlepin.util.SetView;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mutable product data for version 2 manifests
 */
public class ProductDTO extends TimestampedCandlepinDTO<ProductDTO> {

    public static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private Map<String, String> attributes;
    private String derivedProduct;
    private Set<String> providedProducts;
    private Map<String, ContentDTO> productContent;
    private Set<BrandingDTO> brandings;

    /**
     * Initializes an empty ProductDTO
     */
    public ProductDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a shallow copy of the source DTO
     *
     * @param source
     *     the DTO to copy
     */
    public ProductDTO(ProductDTO source) {
        super(source);
    }

    /**
     * Retrieves the id
     *
     * @return the id, or null if unset
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the id
     *
     * @param id
     *     the id, or null to clear it
     * @return this DTO
     */
    public ProductDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the name
     *
     * @return the name, or null if unset
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name
     *
     * @param name
     *     the name, or null to clear it
     * @return this DTO
     */
    public ProductDTO setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Retrieves the attributes
     *
     * @return the attributes, or null if unset
     */
    public Map<String, String> getAttributes() {
        return this.attributes != null ? new MapView<>(this.attributes) : null;
    }

    /**
     * Sets the attributes
     *
     * @param attributes
     *     the attributes, or null to clear it
     * @return this DTO
     */
    public ProductDTO setAttributes(Map<String, String> attributes) {
        this.attributes = attributes != null ? new HashMap<>(attributes) : null;
        return this;
    }

    /**
     * Retrieves the derived product ID
     *
     * @return the derived product ID, or null if unset
     */
    public String getDerivedProduct() {
        return this.derivedProduct;
    }

    /**
     * Sets the derived product ID
     *
     * @param derivedProduct
     *     the derived product ID, or null to clear it
     * @return this DTO
     */
    public ProductDTO setDerivedProduct(String derivedProduct) {
        this.derivedProduct = derivedProduct;
        return this;
    }

    /**
     * Retrieves a view of the provided product IDs
     *
     * @return the provided product IDs, or null if unset
     */
    public Set<String> getProvidedProducts() {
        return this.providedProducts != null ? new SetView<>(this.providedProducts) : null;
    }

    /**
     * Sets the provided product IDs using a copy of the collection
     *
     * @param providedProducts
     *     the provided product IDs, or null to clear them
     * @throws IllegalArgumentException
     *     if an ID is null or empty
     * @return this DTO
     */
    public ProductDTO setProvidedProducts(Collection<String> providedProducts) {
        Set<String> copy = null;
        if (providedProducts != null) {
            copy = new HashSet<>();
            for (String productId : providedProducts) {
                if (productId == null || productId.isEmpty()) {
                    throw new IllegalArgumentException("collection contains null or empty product IDs");
                }
                copy.add(productId);
            }
        }
        this.providedProducts = copy;
        return this;
    }

    /**
     * Retrieves the product content
     *
     * @return the product content, or null if unset
     */
    public Map<String, ContentDTO> getProductContent() {
        return this.productContent != null ? new MapView<>(this.productContent) : null;
    }

    /**
     * Sets the product content
     *
     * @param productContent
     *     the product content, or null to clear it
     * @return this DTO
     */
    public ProductDTO setProductContent(Map<String, ContentDTO> productContent) {
        this.productContent = productContent != null ? new HashMap<>(productContent) : null;
        return this;
    }

    /**
     * Retrieves the brandings
     *
     * @return the brandings, or null if unset
     */
    public Set<BrandingDTO> getBrandings() {
        return this.brandings != null ? new SetView<>(this.brandings) : null;
    }

    /**
     * Sets the brandings
     *
     * @param brandings
     *     the brandings, or null to clear it
     * @return this DTO
     */
    public ProductDTO setBrandings(Set<BrandingDTO> brandings) {
        this.brandings = brandings != null ? new HashSet<>(brandings) : null;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof ProductDTO that) || !super.equals(obj)) {
            return false;
        }

        return new EqualsBuilder()
            .append(this.getId(), that.getId())
            .append(this.getName(), that.getName())
            .append(this.getAttributes(), that.getAttributes())
            .append(this.getProvidedProducts(), that.getProvidedProducts())
            .append(this.getDerivedProduct(), that.getDerivedProduct())
            .append(this.getProductContent(), that.getProductContent())
            .append(this.getBrandings(), that.getBrandings())
            .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getId())
            .append(this.getName())
            .append(this.getAttributes())
            .append(this.getProvidedProducts())
            .append(this.getDerivedProduct())
            .append(this.getProductContent())
            .append(this.getBrandings())
            .toHashCode();
    }

    @Override
    public ProductDTO populate(ProductDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setName(source.getName());
        this.setAttributes(source.getAttributes());
        this.setDerivedProduct(source.getDerivedProduct());
        this.setProvidedProducts(source.getProvidedProducts());
        this.setProductContent(source.getProductContent());
        this.setBrandings(source.getBrandings());

        return this;
    }

    @Override
    public ProductDTO clone() {
        ProductDTO copy = super.clone();
        copy.setAttributes(this.getAttributes());
        copy.setProvidedProducts(this.providedProducts);
        if (this.productContent != null) {
            copy.productContent = new HashMap<>();
            this.productContent.forEach((id, content) ->
                copy.productContent.put(id, content != null ? content.clone() : null));
        }
        if (this.brandings != null) {
            copy.brandings = new HashSet<>();
            this.brandings.forEach(branding -> copy.brandings.add(branding != null ? branding.clone() : null));
        }
        return copy;
    }
}
