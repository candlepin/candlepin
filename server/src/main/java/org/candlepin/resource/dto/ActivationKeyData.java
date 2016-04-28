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
package org.candlepin.resource.dto;

import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyPool;
import org.candlepin.model.ContentOverride;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Release;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.xml.bind.annotation.XmlRootElement;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * DTO representing the activation key data exposed to the API layer.
 */
@XmlRootElement
public class ActivationKeyData {

    /**
     * DTO class representing activation key pool data.
     * <p/>
     * Every method provided by this class is a vanilla getter or setter and only performs simple
     * data validation on set and returns the current value (if any) on fetch.
     */
    public static class AKPoolData {
        private String poolId;
        private Long quantity;

        public AKPoolData() {
            // Intentionally left empty
        }

        public AKPoolData(String poolId, Long quantity) {
            this.setPoolId(poolId);
            this.setQuantity(quantity);
        }

        public String getPoolId() {
            return this.poolId;
        }

        public AKPoolData setPoolId(String poolId) {
            if (poolId == null) {
                throw new IllegalArgumentException("poolId is null");
            }

            this.poolId = poolId;
            return this;
        }

        public Long getQuantity() {
            return this.quantity;
        }

        public AKPoolData setQuantity(Long quantity) {
            this.quantity = quantity;
            return this;
        }
    }

    /**
     * DTO class representing activation key product data.
     * <p/>
     * Every method provided by this class is a vanilla getter or setter and only performs simple
     * data validation on set and returns the current value (if any) on fetch.
     */
    public static class AKProductData {
        private String productId;

        public AKProductData() {
            // Intentionally left empty
        }

        public AKProductData(String productId) {
            this.setProductId(productId);
        }

        public String getProductId() {
            return this.productId;
        }

        public AKProductData setProductId(String productId) {
            if (productId == null) {
                throw new IllegalArgumentException("productId is null");
            }

            this.productId = productId;
            return this;
        }
    }

    /**
     * DTO class representing activation key content overrides.
     * <p/>
     * Every method provided by this class is a vanilla getter or setter and only performs simple
     * data validation on set and returns the current value (if any) on fetch.
     */
    public static class AKContentOverride {
        private String contentLabel;
        private String name;
        private String value;

        public AKContentOverride() {
            // Intentionally left empty
        }

        public AKContentOverride(String contentLabel, String name, String value) {
            this.setContentLabel(contentLabel);
            this.setName(name);
            this.setValue(value);
        }

        public String getContentLabel() {
            return this.contentLabel;
        }

        public AKContentOverride setContentLabel(String contentLabel) {
            if (contentLabel == null) {
                throw new IllegalArgumentException("contentLabel is null");
            }

            this.contentLabel = contentLabel;
            return this;
        }

        public String getName() {
            return this.name;
        }

        public AKContentOverride setName(String name) {
            if (name == null) {
                throw new IllegalArgumentException("name is null");
            }

            this.name = name;
            return this;
        }

        public String getValue() {
            return this.value;
        }

        public AKContentOverride setValue(String value) {
            this.value = value;
            return this;
        }
    }


    private String id;
    private String name;
    private String description;
    private Owner owner;
    private Map<String, AKPoolData> pools;
    private Map<String, AKProductData> products;
    private Set<AKContentOverride> contentOverrides;
    private Release releaseVersion;
    private String serviceLevel;
    private Boolean autoAttach;
    private Date created;
    private Date updated;

    /**
     * Creates a new ActivationKeyData instance without any defined values.
     */
    public ActivationKeyData() {
        this.pools = new HashMap<String, AKPoolData>();
        this.products = new HashMap<String, AKProductData>();
        this.contentOverrides = new HashSet<AKContentOverride>();
    }

    /**
     * Creates a new ActivationKeyData instance using the given ActivationKey model to derive
     * the initialize the new DTO.
     *
     * @param source
     *  The source ActivationKey instance from which to initialize the new DTO instance
     */
    public ActivationKeyData(ActivationKey source) {
        this();
        this.fromModel(source);
    }

    /**
     * Sets the ID of the activation key for this DTO.
     *
     * @param id
     *  The database ID of the activation key, or null to clear the ID
     *
     * @return
     *  a reference to this DTO
     */
    public ActivationKeyData setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the ID of the activation key currently set in this DTO. If an ID has not yet been
     * defined, this method returns null.
     *
     * @return
     *  The ID of the activation key, or null if it has not yet been defined
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the name of the activation key for this DTO.
     *
     * @param name
     *  The name of the activation key, or null to clear the name
     *
     * @return
     *  a reference to this DTO
     */
    public ActivationKeyData setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Retrieves the name of the activation key currently set in this DTO. If a name has not yet
     * been defined, this method returns null.
     *
     * @return
     *  The name of the activation key, or null if it has not yet been defined
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the description of the activation key for this DTO.
     *
     * @param description
     *  The description of the activation key, or null to clear the description
     *
     * @return
     *  a reference to this DTO
     */
    public ActivationKeyData setDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Retrieves the description of the activation key currently set in this DTO. If a description
     * has not yet been defined, this method returns null.
     *
     * @return
     *  The description of the activation key, or null if it has not yet been defined
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Sets the owner/organization of the activation key for this DTO.
     *
     * @param owner
     *  An Owner instance representing the owning organization of the activation key, or null to clear the
     *  owner
     *
     * @return
     *  a reference to this DTO
     */
    public ActivationKeyData setOwner(Owner owner) {
        this.owner = owner;
        return this;
    }

    /**
     * Retrieves the owner of the activation key currently set in this DTO. If an owner has not yet
     * been defined, this method returns null.
     *
     * @return
     *  The owner of the activation key, or null if it has not yet been defined
     */
    public Owner getOwner() {
        return this.owner;
    }

    /**
     * Associates the given pool with the activation key for this DTO. If the pool has already been
     * added with a different quantity, the quantity will be updated.
     *
     * @param pool
     *  The pool to add to this DTO
     *
     * @param quantity
     *  The number of instances of the pool to be associated with the activation key
     *
     * @throws IllegalArgumentException
     *  if pool is null
     *
     * @return
     *  true if the pool was added successfully; false otherwise
     */
    public boolean addPool(Pool pool, Long quantity) {
        if (pool == null) {
            throw new IllegalArgumentException("pool is null");
        }

        String poolId = pool.getId();
        this.pools.put(poolId, new AKPoolData(poolId, quantity));

        return true;
    }

    /**
     * Removes the specified pool from the activation key for this DTO.
     *
     * @param pool
     *  The pool to remove from this DTO
     *
     * @throws IllegalArgumentException
     *  if pool is null
     *
     * @return
     *  true if the pool was found and removed successfully; false otherwise
     */
    public boolean removePool(Pool pool) {
        if (pool == null) {
            throw new IllegalArgumentException("pool is null");
        }

        return (this.pools.remove(pool.getId()) != null);
    }

    /**
     * Sets the pools to be associated with the activation key for this DTO, clearing any
     * previously associated pools. The pools must be provided as a collection of AKPoolData
     * instances referencing the pools and their respective quantity.
     * <p/>
     * Note: The collection provided will be used to populate an internal collection and further
     * changes to it after calling this method will not be reflected by this instance.
     *
     * @param pools
     *  A collection of AKPoolData instances representing the pools and their respective quantities
     *  to associate with the activation key
     *
     * @return
     *  A reference to this DTO
     */
    public ActivationKeyData setPools(Collection<AKPoolData> pools) {
        this.pools.clear();

        for (AKPoolData pool : pools) {
            this.pools.put(pool.getPoolId(), pool);
        }

        return this;
    }

    /**
     * Removes all pools from the activation key for this DTO.
     *
     * @return
     *  A reference to this DTO
     */
    public ActivationKeyData clearPools() {
        this.pools.clear();
        return this;
    }

    /**
     * Retrieves an unmodifiable collection of pools provided by the activation key in this DTO. If
     * the activation key does not provide any pools, this method returns an empty collection.
     *
     * @return
     *  an unmodifiable collection of pools provided by the activation key
     */
    public Collection<AKPoolData> getPools() {
        return Collections.unmodifiableCollection(this.pools.values());
    }

    /**
     * Associates the given product with the activation key for this DTO. If the product has
     * already been added, it will not be added again.
     *
     * @param product
     *  The product to add to the activation key
     *
     * @throws IllegalArgumentException
     *  if product is null
     *
     * @return
     *  true if the product was added successfully; false otherwise
     */
    public boolean addProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product is null");
        }

        String productId = product.getId();
        this.products.put(productId, new AKProductData(productId));

        return true;
    }

    /**
     * Removes the specified product from the activation key for this DTO.
     *
     * @param product
     *  The product to remove from this DTO
     *
     * @throws IllegalArgumentException
     *  if product is null
     *
     * @return
     *  true if the product was found and removed successfully; false otherwise
     */
    public boolean removeProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product is null");
        }

        return (this.products.remove(product.getId()) != null);
    }

    /**
     * Sets the products to be associated with the activation key for this DTO, clearing any
     * previously associated products. The products must be provided as a collection of
     * AKProductData instances referencing the products.
     * <p/>
     * Note: The collection provided will be used to populate an internal collection and further
     * changes to it after calling this method will not be reflected by this instance.
     *
     * @param products
     *  A collection of AKProductData instances representing the products to associate with the
     *  activation key
     *
     * @return
     *  A reference to this DTO
     */
    public ActivationKeyData setProducts(Collection<AKProductData> products) {
        this.products.clear();

        for (AKProductData product : products) {
            this.products.put(product.getProductId(), product);
        }

        return this;
    }

    /**
     * Removes all products from the activation key for this DTO.
     *
     * @return
     *  A reference to this DTO
     */
    public ActivationKeyData clearProducts() {
        this.products.clear();
        return this;
    }

    /**
     * Retrieves an unmodifiable collection of products provided by the activation key represented
     * by this DTO. If the activation key does not provide any products, this method returns an
     * empty collection.
     *
     * @return
     *  an unmodifiable collection of products provided by the activation key
     */
    public Collection<AKProductData> getProducts() {
        return Collections.unmodifiableCollection(this.products.values());
    }

    public boolean addContentOverride(ContentOverride override) {
        if (override == null) {
            throw new IllegalArgumentException("override is null");
        }

        return this.contentOverrides.add(new AKContentOverride(
            override.getContentLabel(), override.getName(), override.getValue()
        ));
    }

    /**
     * Removes the specified content override from the activation key for this DTO.
     *
     * @param override
     *  The content override to remove from this DTO
     *
     * @throws IllegalArgumentException
     *  if override is null
     *
     * @return
     *  true if the content override was found and removed successfully; false otherwise
     */
    public boolean removeContentOverride(ContentOverride override) {
        if (override == null) {
            throw new IllegalArgumentException("override is null");
        }

        List<AKContentOverride> remove = new LinkedList<AKContentOverride>();

        for (AKContentOverride candidate : this.contentOverrides) {
            if (candidate.getContentLabel().equals(override.getContentLabel()) &&
                candidate.getName().equals(override.getName())) {
                remove.add(candidate);
            }
        }

        return this.contentOverrides.removeAll(remove);
    }

    /**
     * Sets the content overrides to be associated with the activation key for this DTO, clearing
     * any previously associated overrides. The content overrides must be provided as a collection
     * of AKContentOverride instances representing the overrides.
     * <p/>
     * Note: The collection provided will be used to populate an internal collection and further
     * changes to it after calling this method will not be reflected by this instance.
     *
     * @param overrides
     *  A collection of AKContentOverride instances representing the content overrides to associate
     *  with the activation key
     *
     * @return
     *  A reference to this DTO
     */
    public ActivationKeyData setContentOverrides(Collection<AKContentOverride> overrides) {
        this.contentOverrides.clear();

        if (overrides != null) {
            this.contentOverrides.addAll(overrides);
        }

        return this;
    }

    /**
     * Removes all content overrides from the activation key for this DTO.
     *
     * @return
     *  A reference to this DTO
     */
    public ActivationKeyData clearContentOverrides() {
        this.contentOverrides.clear();
        return this;
    }

    /**
     * Retrieves the content overrides associated with the activation key represented by this DTO.
     * If the activation key does not have any content overrides, this method returns an empty
     * collection.
     *
     * @return
     *  a collection of content overrides associated with the activation key
     */
    public Collection<AKContentOverride> getContentOverrides() {
        return Collections.unmodifiableCollection(this.contentOverrides);
    }

    /**
     * Sets the release version for the activation key represented by this DTO
     *
     * @param releaseVersion
     *  A Release instance representing the release version for the activation key
     *
     * @return
     *  a reference to this DTO
     */
    public ActivationKeyData setReleaseVersion(Release releaseVersion) {
        this.releaseVersion = releaseVersion;
        return this;
    }

    /**
     * Retrieves the release version of the activation key currently set in this DTO. If the release
     * version has not yet been defined, this method returns null.
     *
     * @return
     *  The release version of the activation key, or null if the release version has not yet been
     *  defined
     */
    @JsonProperty("releaseVer")
    public Release getReleaseVersion() {
        return this.releaseVersion;
    }

    /**
     * Sets the service level for the activation key represented by this DTO
     *
     * @param serviceLevel
     *  The service level defined for the activation key, or null to clear the service level
     *
     * @return
     *  a reference to this DTO
     */
    public ActivationKeyData setServiceLevel(String serviceLevel) {
        this.serviceLevel = serviceLevel;
        return this;
    }

    /**
     * Retrieves the service level of the activation key currently set in this DTO. If the service
     * level has not yet been defined, this method returns null.
     *
     * @return
     *  The service level of the activation key, or null if the service level has not yet been
     *  defined
     */
    public String getServiceLevel() {
        return this.serviceLevel;
    }

    /**
     * Sets the auto-attach flag for the activation key represented by this DTO
     *
     * @param autoAttach
     *  The auto-attach flag defined for the activation key, or null to leave the flag undefined
     *
     * @return
     *  a reference to this DTO
     */
    public ActivationKeyData setAutoAttach(Boolean autoAttach) {
        this.autoAttach = autoAttach;
        return this;
    }

    /**
     * Checks whether or not the auto-attach flag is set for the activation key represented by this
     * DTO.
     *
     * @return
     *  A boolean value representing the state of the auto-attach flag, or null if the state has
     *  yet been defined
     */
    public Boolean isAutoAttach() {
        return autoAttach;
    }

    /**
     * Sets the creation date for the activation key represented by this DTO
     *
     * @param created
     *  A Date instance representing the creation date
     *
     * @return
     *  a reference to this DTO
     */
    public ActivationKeyData setCreated(Date created) {
        this.created = created;
        return this;
    }

    /**
     * Retrieves the creation date of the activation key currently set in this DTO. If the creation
     * date has not yet been defined, this method returns null.
     *
     * @return
     *  The creation date for the activation key, or null if the creation date has not yet been
     *  defined
     */
    public Date getCreated() {
        return created;
    }

    /**
     * Sets the last updated date for the activation key represented by this DTO
     *
     * @param updated
     *  A Date instance representing the last updated date
     *
     * @return
     *  a reference to this DTO
     */
    public ActivationKeyData setUpdated(Date updated) {
        this.updated = updated;
        return this;
    }

    /**
     * Retrieves the last update date of the activation key currently set in this DTO. If the last
     * update date has not yet been defined, this method returns null.
     *
     * @return
     *  The last update date for the activation key, or null if the last update date has not yet
     *  been defined
     */
    public Date getUpdated() {
        return updated;
    }

    /**
     * Sets the data for this DTO using the data associated with the given source ActivationKey
     * model object. If a given field is not set in the source object, it will be cleared or nulled
     * in this DTO.
     *
     * @param source
     *  The source model object from which to derive data for this DTO
     *
     * @return
     *  a reference to this DTO
     */
    public ActivationKeyData fromModel(ActivationKey source) {
        this.setId(source.getId());
        this.setName(source.getName());
        this.setDescription(source.getDescription());
        this.setOwner(source.getOwner());

        this.products.clear();
        for (Product product : source.getProducts()) {
            this.addProduct(product);
        }

        this.pools.clear();
        for (ActivationKeyPool akpool : source.getPools()) {
            this.addPool(akpool.getPool(), akpool.getQuantity());
        }

        this.contentOverrides.clear();
        for (ContentOverride override : source.getContentOverrides()) {
            this.addContentOverride(override);
        }

        this.setReleaseVersion(source.getReleaseVer());
        this.setServiceLevel(source.getServiceLevel());
        this.setAutoAttach(source.isAutoAttach());
        this.setCreated(source.getCreated());
        this.setUpdated(source.getUpdated());

        return this;
    }
}
