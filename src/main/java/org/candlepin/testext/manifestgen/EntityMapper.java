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
package org.candlepin.testext.manifestgen;

import org.candlepin.dto.api.server.v1.AttributeDTO;
import org.candlepin.dto.api.server.v1.BrandingDTO;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.dto.api.server.v1.ProductContentDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
import org.candlepin.dto.api.server.v1.SubscriptionDTO;
import org.candlepin.model.Branding;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.util.Util;

import org.hibernate.query.NativeQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;



/**
 * Utility class handling the conversion of API DTOs to persisted database entities
 */
public class EntityMapper {
    private static final Logger log = LoggerFactory.getLogger(EntityMapper.class);

    private final Owner owner;

    private final PoolCurator poolCurator;
    private final ProductCurator productCurator;
    private final ContentCurator contentCurator;

    private final Map<String, Content> contentMap;
    private final Map<String, Product> productMap;
    private final List<Pool> pools;


    public EntityMapper(Owner owner, PoolCurator poolCurator, ProductCurator productCurator,
        ContentCurator contentCurator) {

        this.owner = Objects.requireNonNull(owner);

        this.poolCurator = Objects.requireNonNull(poolCurator);
        this.productCurator = Objects.requireNonNull(productCurator);
        this.contentCurator = Objects.requireNonNull(contentCurator);

        this.contentMap = new HashMap<>();
        this.productMap = new HashMap<>();
        this.pools = new ArrayList<>();
    }

    private <T> void mergeNonNullField(T value, Consumer<T> mutator) {
        if (value != null) {
            mutator.accept(value);
        }
    }

    private Map<String, String> convertAttributes(Collection<AttributeDTO> dtos) {
        if (dtos == null) {
            return null;
        }

        return dtos.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(AttributeDTO::getName, AttributeDTO::getValue));
    }

    private Branding convertBranding(BrandingDTO bdto) {
        if (bdto == null) {
            return null;
        }

        return new Branding(bdto.getProductId(), bdto.getName(), bdto.getType());
    }

    private Content mergeContentData(ContentDTO cdto) {
        if (cdto == null) {
            return null;
        }

        // Fetch mapped entity
        Content entity = this.contentMap.computeIfAbsent(cdto.getId(), (cid) -> {
            if (cid == null || cid.isBlank()) {
                throw new IllegalStateException("content lacks an id: " + cdto);
            }

            Content fetched = this.contentCurator.getContentById(this.owner.getKey(), cid);
            if (fetched == null) {
                fetched = new Content(cid)
                    .setNamespace(this.owner.getKey());
            }

            return fetched;
        });

        // Merge content data
        this.mergeNonNullField(cdto.getType(), entity::setType);
        this.mergeNonNullField(cdto.getLabel(), entity::setLabel);
        this.mergeNonNullField(cdto.getName(), entity::setName);
        this.mergeNonNullField(cdto.getVendor(), entity::setVendor);
        this.mergeNonNullField(cdto.getContentUrl(), entity::setContentUrl);
        this.mergeNonNullField(cdto.getRequiredTags(), entity::setRequiredTags);
        this.mergeNonNullField(cdto.getReleaseVer(), entity::setReleaseVersion);
        this.mergeNonNullField(cdto.getGpgUrl(), entity::setGpgUrl);
        this.mergeNonNullField(cdto.getMetadataExpire(), entity::setMetadataExpiration);
        this.mergeNonNullField(cdto.getModifiedProductIds(), entity::setModifiedProductIds);
        this.mergeNonNullField(cdto.getArches(), entity::setArches);

        return entity;
    }

    private ProductContent convertProductContent(ProductContentDTO pcdto) {
        if (pcdto == null) {
            return null;
        }

        boolean enabled = pcdto.getEnabled() != null ?
            pcdto.getEnabled() :
            ProductContent.DEFAULT_ENABLED_STATE;

        return new ProductContent(this.mergeContentData(pcdto.getContent()), enabled);
    }

    private Product mergeProductData(ProductDTO pdto) {
        if (pdto == null) {
            return null;
        }

        // Fetch mapped entity
        Product entity = this.productMap.computeIfAbsent(pdto.getId(), (pid) -> {
            if (pid == null || pid.isBlank()) {
                throw new IllegalStateException("product lacks an id: " + pdto);
            }

            // Impl note:
            // We won't namespace stuff here since it'll be vanishing in a moment anyway, and at the
            // time of writing, this best reflects how export works and is used today.
            Product fetched = this.productCurator.getProductById(this.owner.getKey(), pid);
            if (fetched == null) {
                fetched = new Product()
                    .setId(pid)
                    .setNamespace(this.owner.getKey());
            }

            return fetched;
        });

        this.mergeNonNullField(pdto.getName(), entity::setName);
        this.mergeNonNullField(pdto.getMultiplier(), entity::setMultiplier);
        this.mergeNonNullField(pdto.getDependentProductIds(), entity::setDependentProductIds);

        // Attributes need to be converted because they have the worst encoding
        this.mergeNonNullField(this.convertAttributes(pdto.getAttributes()), entity::setAttributes);

        // This field violates conventions. Always update it.
        entity.setDerivedProduct(this.mergeProductData(pdto.getDerivedProduct()));

        // Provided products
        Collection<ProductDTO> providedProductDtos = pdto.getProvidedProducts();
        if (providedProductDtos != null) {
            List<Product> providedProducts = providedProductDtos.stream()
                .filter(Objects::nonNull)
                .map(this::mergeProductData)
                .toList();

            entity.setProvidedProducts(providedProducts);
        }

        // product content
        Collection<ProductContentDTO> productContentDto = pdto.getProductContent();
        if (productContentDto != null) {
            List<ProductContent> productContent = productContentDto.stream()
                .filter(Objects::nonNull)
                .map(this::convertProductContent)
                .toList();

            entity.setProductContent(productContent);
        }

        // branding
        Collection<BrandingDTO> brandingDtos = pdto.getBranding();
        if (brandingDtos != null) {
            List<Branding> branding = brandingDtos.stream()
                .filter(Objects::nonNull)
                .map(this::convertBranding)
                .toList();

            entity.setBranding(branding);
        }

        return entity;
    }

    public EntityMapper addSubscription(SubscriptionDTO sdto) {
        if (sdto == null) {
            return this;
        }

        // Impl note:
        // The org will be net-new for every request, so every pool will be created as new from the
        // subscription data. As such, we need not worry (as much) about merge behavior. At least
        // not on the pool proper.

        Pool pool = new Pool()
            .setOwner(this.owner)
            .setProduct(this.mergeProductData(sdto.getProduct()))
            .setQuantity(sdto.getQuantity())
            .setStartDate(Util.toDate(sdto.getStartDate()))
            .setEndDate(Util.toDate(sdto.getEndDate()))
            .setContractNumber(sdto.getContractNumber())
            .setAccountNumber(sdto.getAccountNumber())
            .setOrderNumber(sdto.getOrderNumber())
            .setManaged(true);

            // Apparently subscription DTOs don't have attributes, and pool DTOs don't have complete
            // product data. No matter which DTO we pick here, it's wrong. That said, if we want or
            // need to add attributes in the future, extend the SubscriptionDTO in this testext
            // rather than modifying the API DTOs directly.
            // .setAttributes(this.convertAttributes(sdto.getAttributes()))

        // If the sub has an ID explicity set, use it.
        String sid = sdto.getId();
        if (sid != null && !sid.isBlank()) {
            pool.setId(sid);
        }

        this.pools.add(pool);
        return this;
    }

    public EntityMapper addSubscriptions(Collection<SubscriptionDTO> subscriptionDtos) {
        if (subscriptionDtos == null) {
            return this;
        }

        subscriptionDtos.forEach(this::addSubscription);
        return this;
    }

    private Content persistContent(Content content) {
        return content != null && content.getUuid() == null ?
            this.contentCurator.create(content) :
            content;
    }

    private Product persistProduct(Product product) {
        if (product == null) {
            return product;
        }

        // Ensure children are persisted first
        this.persistProduct(product.getDerivedProduct());

        Optional.ofNullable(product.getProvidedProducts())
            .orElse(List.of())
            .forEach(this::persistProduct);

        Optional.ofNullable(product.getProductContent())
            .orElse(List.of())
            .stream()
            .filter(Objects::nonNull)
            .map(ProductContent::getContent)
            .forEach(this::persistContent);

        return product.getUuid() == null ?
            this.productCurator.create(product) :
            product;
    }

    private Pool persistPool(Pool pool) {
        if (pool == null) {
            return pool;
        }

        // Persist children
        this.persistProduct(pool.getProduct());

        // Impl note:
        // This is profoundly stupid, but Hibernate loves doing stupid Hibernate things. If we have
        // an explicit ID for the pool, we need to clear it, let Hibernate generate a new ID during
        // initial persist, immediately evict it from the entity manager, manually update the ID
        // with a native query, clear the query/entity cache, then requery as necessary. A dumb,
        // inefficient, waste of time and energy.
        if (pool.getId() != null) {
            String pid = pool.getId();
            pool.setId(null);

            pool = this.poolCurator.create(pool);
            this.poolCurator.detach(pool);

            int count = this.poolCurator.getEntityManager()
                .createNativeQuery("UPDATE cp_pool SET id = :new_id WHERE id = :old_id")
                .setParameter("old_id", pool.getId())
                .setParameter("new_id", pid)
                .unwrap(NativeQuery.class)
                .addSynchronizedEntityClass(Pool.class)
                .executeUpdate();

            pool = this.poolCurator.get(pid);
        }
        else {
            pool = this.poolCurator.create(pool);
        }

        return pool;
    }

    /**
     * Persists the current entities mapped by this entity mapper.
     *
     * @return
     *  a reference to this entity mapper
     */
    public EntityMapper persist() {
        this.pools.forEach(this::persistPool);
        return this;
    }

}
