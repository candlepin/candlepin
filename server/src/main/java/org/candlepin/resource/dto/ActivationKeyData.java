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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * DTO representing the activation key data to return to API consumers
 */
@XmlRootElement
public class ActivationKeyData {

    /**
     * DTO class representing activation key pool data
     */
    public static class AKPoolData {
        private String poolId;
        private Long quantity;

        public AKPoolData(String poolId, Long quantity) {
            this.poolId = poolId;
            this.quantity = quantity;
        }

        public String getPoolId() {
            return this.poolId;
        }

        public Long getQuantity() {
            return this.quantity;
        }
    }

    /**
     * DTO class representing activation key product data
     */
    public static class AKProductData {
        private String productId;

        public AKProductData(String productId) {
            this.productId = productId;
        }

        public String getProductId() {
            return this.productId;
        }
    }

    /**
     * DTO class representing activation key pool data
     */
    public static class AKContentOverride {
        private String contentLabel;
        private String name;
        private String value;

        public AKContentOverride(String contentLabel, String name, String value) {
            this.contentLabel = contentLabel;
            this.name = name;
            this.value = value;
        }

        public String getContentLabel() {
            return this.contentLabel;
        }

        public String getName() {
            return this.name;
        }

        public String getValue() {
            return this.value;
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

    public ActivationKeyData() {
        this.pools = new HashMap<String, AKPoolData>();
        this.products = new HashMap<String, AKProductData>();
        this.contentOverrides = new HashSet<AKContentOverride>();
    }

    public ActivationKeyData(ActivationKey source) {
        this();
        this.fromModel(source);
    }

    public ActivationKeyData setId(String id) {
        this.id = id;
        return this;
    }

    public String getId() {
        return this.id;
    }

    public ActivationKeyData setName(String name) {
        this.name = name;
        return this;
    }

    public String getName() {
        return this.name;
    }

    public ActivationKeyData setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getDescription() {
        return this.description;
    }

    public ActivationKeyData setOwner(Owner owner) {
        this.owner = owner;
        return this;
    }

    public Owner getOwner() {
        return this.owner;
    }

    public boolean addPool(Pool pool, Long quantity) {
        String poolId = pool.getId();
        this.pools.put(poolId, new AKPoolData(poolId, quantity));

        return true;
    }

    public boolean removePool(Pool pool) {
        return (this.pools.remove(pool.getId()) != null);
    }

    public ActivationKeyData setPools(Collection<AKPoolData> pools) {
        this.pools.clear();

        for (AKPoolData pool : pools) {
            this.pools.put(pool.getPoolId(), pool);
        }

        return this;
    }

    public Collection<AKPoolData> getPools() {
        return this.pools.values();
    }

    public boolean addProduct(Product product) {
        String productId = product.getId();
        this.products.put(productId, new AKProductData(productId));

        return true;
    }

    public boolean removeProduct(Product product) {
        return (this.products.remove(product.getId()) != null);
    }

    public ActivationKeyData setProducts(Collection<AKProductData> products) {
        this.products.clear();

        for (AKProductData product : products) {
            this.products.put(product.getProductId(), product);
        }

        return this;
    }

    public Collection<AKProductData> getProducts() {
        return this.products.values();
    }

    public boolean addContentOverride(ContentOverride override) {
        return this.contentOverrides.add(new AKContentOverride(
            override.getContentLabel(), override.getName(), override.getValue()
        ));
    }

    public boolean removeContentOverride(ContentOverride override) {
        List<AKContentOverride> remove = new LinkedList<AKContentOverride>();

        for (AKContentOverride candidate : this.contentOverrides) {
            if (candidate.getContentLabel().equals(override.getContentLabel()) &&
                candidate.getName().equals(override.getName())) {
                remove.add(candidate);
            }
        }

        return this.contentOverrides.removeAll(remove);
    }

    public ActivationKeyData setContentOverrides(Collection<AKContentOverride> overrides) {
        this.contentOverrides.clear();

        if (overrides != null) {
            this.contentOverrides.addAll(overrides);
        }

        return this;
    }

    public Collection<AKContentOverride> getContentOverrides() {
        return this.contentOverrides;
    }

    public ActivationKeyData setReleaseVersion(Release releaseVersion) {
        this.releaseVersion = releaseVersion;
        return this;
    }

    @JsonProperty("releaseVer")
    public Release getReleaseVersion() {
        return this.releaseVersion;
    }

    public ActivationKeyData setServiceLevel(String serviceLevel) {
        this.serviceLevel = serviceLevel;
        return this;
    }

    public String getServiceLevel() {
        return this.serviceLevel;
    }

    public void setAutoAttach(Boolean autoAttach) {
        this.autoAttach = autoAttach;
    }

    public Boolean isAutoAttach() {
        return autoAttach;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

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
