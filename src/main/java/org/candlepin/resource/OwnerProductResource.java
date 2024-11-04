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
package org.candlepin.resource;

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobException;
import org.candlepin.async.JobManager;
import org.candlepin.async.tasks.RefreshPoolsForProductJob;
import org.candlepin.auth.Verify;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.ProductManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.dto.api.server.v1.ProductCertificateDTO;
import org.candlepin.dto.api.server.v1.ProductContentDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.ProductQueryBuilder;
import org.candlepin.model.QueryBuilder.Inclusion;
import org.candlepin.paging.PagingUtilFactory;
import org.candlepin.pki.certs.ProductCertificateGenerator;
import org.candlepin.resource.server.v1.OwnerProductApi;
import org.candlepin.resource.util.InfoAdapter;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.model.BrandingInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.util.Util;

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.persistence.LockModeType;



public class OwnerProductResource implements OwnerProductApi {
    private static final Logger log = LoggerFactory.getLogger(OwnerProductResource.class);

    private final I18n i18n;
    private final Configuration config;
    private final DTOValidator validator;
    private final ModelTranslator translator;
    private final JobManager jobManager;
    private final PagingUtilFactory pagingUtilFactory;
    private final OwnerCurator ownerCurator;
    private final ProductManager productManager;
    private final ProductCertificateGenerator productCertificateGenerator;
    private final ProductCurator productCurator;
    private final ContentCurator contentCurator;

    @Inject
    @SuppressWarnings("checkstyle:parameternumber")
    public OwnerProductResource(
        I18n i18n,
        Configuration config,
        ModelTranslator translator,
        JobManager jobManager,
        DTOValidator validator,
        PagingUtilFactory pagingUtilFactory,
        OwnerCurator ownerCurator,
        ProductManager productManager,
        ProductCertificateGenerator productCertificateGenerator,
        ProductCurator productCurator,
        ContentCurator contentCurator) {

        this.i18n = Objects.requireNonNull(i18n);
        this.config = Objects.requireNonNull(config);
        this.translator = Objects.requireNonNull(translator);
        this.jobManager = Objects.requireNonNull(jobManager);
        this.validator = Objects.requireNonNull(validator);
        this.pagingUtilFactory = Objects.requireNonNull(pagingUtilFactory);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.productManager = Objects.requireNonNull(productManager);
        this.productCertificateGenerator = Objects.requireNonNull(productCertificateGenerator);
        this.productCurator = Objects.requireNonNull(productCurator);
        this.contentCurator = Objects.requireNonNull(contentCurator);
    }

    /**
     * Validates that the specified entity belongs to the same namespace as the given organization.
     * If the entity is part of the global namespace, or another organization's namespace, this
     * method throws a ForbiddenException.
     *
     * @param owner
     *  the org to use to validate the product entity
     *
     * @param product
     *  the product entity to validate
     *
     * @throws ForbiddenException
     *  if the entity is not part of the given organization's namespace
     */
    private void validateProductNamespace(Owner owner, Product product) {
        String namespace = owner != null ? owner.getKey() : "";

        if (!namespace.equals(product.getNamespace())) {
            throw new ForbiddenException(this.i18n.tr(
                "Cannot modify or remove products defined outside of the organization's namespace"));
        }
    }

    /**
     * Attempts to resolve the given product ID reference to a product for the given organization.
     * This method will first attempt to resolve the product reference in the org's namespace, but
     * will fall back to the global namespace if that resolution attempt fails. If the product ID
     * cannot be resolved, this method throws an exception.
     *
     * @param owner
     *  the organization for which to resolve the product reference
     *
     * @param productId
     *  the product ID to resolve
     *
     * @param lockModeType
     *  the type of database lock to apply to any product instance returned by this method; if null
     *  or LockModeType.NONE, no database lock will be applied
     *
     * @throws IllegalArgumentException
     *  if owner is null, or if productId is null or empty
     *
     * @throws NotFoundException
     *  if a product with the specified ID cannot be found within the context of the given org
     *
     * @return
     *  the product for the specified ID
     */
    private Product resolveProductId(Owner owner, String productId, LockModeType lockModeType) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (productId == null || productId.isEmpty()) {
            throw new IllegalArgumentException("productId is null or empty");
        }

        Product product = this.productCurator.resolveProductId(owner.getKey(), productId, lockModeType);

        if (product == null) {
            throw new NotFoundException(
                i18n.tr("Unable to find a product with the ID \"{0}\" for owner \"{1}\"",
                    productId, owner.getKey()));
        }

        return product;
    }

    /**
     * Attempts to resolve the given content ID references to content in the specified
     * organization's namespace. If any content ID cannot be resolved to a content entity, this
     * method throws an exception. If any resolved content exists outside of the organization's
     * namespace, this method throws an exception
     *
     * @param owner
     *  the organization for which to resolve the content reference
     *
     * @param contentIds
     *  the collection of content IDs to resolve
     *
     * @param lockModeType
     *  the type of locking to apply to the returned entity, or null to omit any locking
     *
     * @throws IllegalArgumentException
     *  if owner is null, or if contentId is null or empty
     *
     * @throws NotFoundException
     *  if a content with the specified ID cannot be found within the context of the given org
     *
     * @throws ForbiddenException
     *  if any resolved content is defined outside of the organization's namespace
     *
     * @return
     *  the content for the specified ID
     */
    private Map<String, Content> resolveContentIds(Owner owner, Collection<String> contentIds,
        LockModeType lockModeType) {

        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        Set<String> cids = new HashSet<>(contentIds);
        String namespace = owner.getKey();

        Map<String, Content> contentMap = this.contentCurator.resolveContentIds(namespace, contentIds,
            lockModeType);

        // Verify that our lookup found everything we requested
        cids.removeAll(contentMap.keySet());
        if (!cids.isEmpty()) {
            throw new NotFoundException(
                this.i18n.tr("Unable to find one or more contents in the namespace \"{0}\" with IDs: {1}",
                    namespace, cids));
        }

        // Verify that our lookup only contains contents that belong to this org's namespace
        List<String> forbiddenIds = contentMap.values()
            .stream()
            .filter(content -> !namespace.equals(content.getNamespace()))
            .map(Content::getId)
            .toList();

        if (!forbiddenIds.isEmpty()) {
            throw new ForbiddenException(
                this.i18n.tr("Cannot attach content defined outside of the organization's namespace: {0}",
                    forbiddenIds));
        }

        return contentMap;
    }

    /**
     * Creates a new, unmanaged product template to use for building batch content changes to pass
     * along to the product manager.
     *
     * @param source
     *  the source product entity from which to copy the required data to create the product change
     *  template
     *
     * @param productContent
     *  the productContent collection to be returned by the ProductInfo instance generated by this
     *  method; may be null or empty
     *
     * @return
     *  an unmanaged product instance
     */
    private ProductInfo buildProductInfoForBatchContentChange(Product source,
        Collection<ProductContent> productContent) {

        if (source == null) {
            return null;
        }

        // Note: this must pass through with any fields that violate the "null = no change" paradigm
        // we use for API-based updates. At the time of writing that is only the derived product
        // field, but more should be added as necessary.
        return new ProductInfo() {
            @Override
            public String getId() {
                return source.getId();
            }

            @Override
            public String getName() {
                return null;
            }

            @Override
            public Long getMultiplier() {
                return null;
            }

            @Override
            public Collection<String> getDependentProductIds() {
                return null;
            }

            @Override
            public Map<String, String> getAttributes() {
                return null;
            }

            @Override
            public String getAttributeValue(String key) {
                return null;
            }

            @Override
            public Collection<? extends ProductContentInfo> getProductContent() {
                return productContent;
            }

            @Override
            public Collection<? extends BrandingInfo> getBranding() {
                return null;
            }

            @Override
            public ProductInfo getDerivedProduct() {
                return source.getDerivedProduct();
            }

            @Override
            public Collection<? extends ProductInfo> getProvidedProducts() {
                return null;
            }

            @Override
            public Date getCreated() {
                return null;
            }

            @Override
            public Date getUpdated() {
                return null;
            }
        };
    }

    /**
     * Validates the product references on the specified DTO. If the provided product collection has
     * any null elements or products with invalid IDs, or the derived product is present but has an
     * invalid ID, this method throws a BadRequestException.
     *
     * @param owner
     *  the owner to use as the context in which to perform the validation
     *
     * @param pdto
     *  the product DTO to validate
     *
     * @throws BadRequestException
     *  if any provided product reference is null or has an invalid ID, or the derived product has
     *  an invalid ID
     */
    private void validateDTOProductRefs(Owner owner, ProductDTO pdto) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        String namespace = owner.getKey();
        Set<String> pids = new HashSet<>();

        // TODO: We could probably write a pretty wrapper for all of this using the stuff in
        // java.util.function and upgrade our validator with a nice fluent, reusable interface

        // provided products
        Collection<ProductDTO> ppdtos = pdto.getProvidedProducts();
        if (ppdtos != null) {
            for (ProductDTO ppdto : ppdtos) {
                if (ppdto == null) {
                    throw new BadRequestException(
                        this.i18n.tr("provided products contains one or more null elements"));
                }

                String id = ppdto.getId();
                if (id == null || id.isBlank()) {
                    throw new BadRequestException(
                        this.i18n.tr("one or more provided product references lacks a valid ID"));
                }

                pids.add(id);
            }
        }

        // derived product
        ProductDTO dpdto = pdto.getDerivedProduct();
        if (dpdto != null) {
            String id = dpdto.getId();
            if (id == null || id.isBlank()) {
                throw new BadRequestException(this.i18n.tr("derived product reference lacks a valid ID"));
            }

            pids.add(id);
        }

        Map<String, Product> productMap = this.productCurator.resolveProductIds(namespace, pids,
            LockModeType.PESSIMISTIC_WRITE);

        pids.removeAll(productMap.keySet());
        if (!pids.isEmpty()) {
            throw new NotFoundException(
                this.i18n.tr("Unable to find one or more products in the namespace \"{0}\" with IDs: {1}",
                    namespace, pids));
        }

        // Verify that our lookup only contains products that belong to this org's namespace
        List<String> forbiddenIds = productMap.values()
            .stream()
            .filter(product -> !namespace.equals(product.getNamespace()))
            .map(Product::getId)
            .toList();

        if (!forbiddenIds.isEmpty()) {
            throw new ForbiddenException(
                this.i18n.tr("Cannot attach product defined outside of the organization's namespace: {0}",
                    forbiddenIds));
        }
    }

    /**
     * Validates the content references on the specified DTO. If the product content collection has
     * any null elements, join elements lacking a content reference, or contents with invalid IDs,
     * this method throws a BadRequestException.
     *
     * @param owner
     *  the owner to use as the context in which to perform the validation
     *
     * @param pdto
     *  the product DTO to validate
     *
     * @throws BadRequestException
     *  if any product content is null, lacks of a content reference, or has a content with an
     *  invalid ID
     */
    private void validateDTOContentRefs(Owner owner, ProductDTO pdto) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        String namespace = owner.getKey();
        Set<String> cids = new HashSet<>();

        Collection<ProductContentDTO> pcdtos = pdto.getProductContent();
        if (pcdtos != null) {
            for (ProductContentDTO pcdto : pcdtos) {
                if (pcdto == null) {
                    throw new BadRequestException(
                        this.i18n.tr("product contents contains one or more null elements"));
                }

                ContentDTO cdto = pcdto.getContent();
                if (cdto == null) {
                    throw new BadRequestException(
                        this.i18n.tr("product content contains one or more malformed content references"));
                }

                String id = cdto.getId();
                if (id == null || id.isBlank()) {
                    throw new BadRequestException(
                        this.i18n.tr("one or more content references lacks a valid ID"));
                }

                cids.add(id);
            }
        }

        this.resolveContentIds(owner, cids, LockModeType.PESSIMISTIC_WRITE);
    }

    /**
     * Validates the collections and entity references of the given product DTO. If any of the
     * collections are set, but contain null values, or have elements with invalid entity IDs, this
     * method throws a BadRequestException.
     *
     * @param owner
     *  the owner to use as the context in which to perform the validation
     *
     * @param pdto
     *  the product DTO to validate
     *
     * @throws BadRequestException
     *  if any of the validation steps fail for the given product DTO
     */
    private void validateProductDTO(Owner owner, ProductDTO pdto) {
        if (pdto == null) {
            return;
        }

        this.validator.validateCollectionElementsNotNull(pdto::getBranding, pdto::getDependentProductIds);

        this.validateDTOProductRefs(owner, pdto);
        this.validateDTOContentRefs(owner, pdto);
    }

    @Override
    @Transactional
    public ProductDTO createProduct(String ownerKey, ProductDTO dto) {
        Owner owner = this.getOwnerByKey(ownerKey);

        // Run the DTO through our various validations
        this.validateProductDTO(owner, dto);

        if (dto.getId() == null || dto.getId().isBlank()) {
            throw new BadRequestException(i18n.tr("product has a null or invalid ID"));
        }

        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BadRequestException(i18n.tr("product has a null or invalid name"));
        }

        // Verify the product doesn't already exist
        if (this.productCurator.resolveProductId(null, dto.getId()) != null) {
            throw new BadRequestException(
                this.i18n.tr("A product with ID \"{0}\" is already defined", dto.getId()));
        }

        // Create it
        Product entity = this.productManager.createProduct(owner, InfoAdapter.productInfoAdapter(dto));

        return this.translator.translate(entity, ProductDTO.class);
    }

    @Override
    @Transactional
    public ProductDTO getProductById(@Verify(Owner.class) String ownerKey, String productId) {
        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.resolveProductId(owner, productId, null);

        return this.translator.translate(product, ProductDTO.class);
    }

    /**
     * Generating product certificates for dev/testing is the only purpose for this endpoint.
     * It is not used in production in any capacity (product certificates themselves are not used in
     * production, in any capacity).
     *
     * @param ownerKey the owner key for which the product is scoped for
     * @param productId the product ID for which a certificate is to be fetched
     * @return a product certificate (generating one if one does not exist for the given product id)
     */
    @Override
    @Transactional
    public ProductCertificateDTO getProductCertificateById(String ownerKey, String productId) {
        if (!productId.matches("\\d+")) {
            throw new BadRequestException(i18n.tr("Only numeric product IDs are allowed."));
        }

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.resolveProductId(owner, productId, null);

        ProductCertificate productCertificate = this.productCertificateGenerator.generate(product);
        return this.translator.translate(productCertificate, ProductCertificateDTO.class);
    }

    @Override
    @Transactional
    // GET /owners/{key}/products
    public Stream<ProductDTO> getProductsByOwner(@Verify(Owner.class) String ownerKey,
        List<String> productIds, List<String> productNames, String active, String custom) {

        Owner owner = this.getOwnerByKey(ownerKey);

        Inclusion activeInc = Inclusion.fromName(active, Inclusion.EXCLUSIVE)
            .orElseThrow(() ->
                new BadRequestException(i18n.tr("Invalid active inclusion type: {0}", active)));

        Inclusion customInc = Inclusion.fromName(custom, Inclusion.INCLUDE)
            .orElseThrow(() ->
                new BadRequestException(i18n.tr("Invalid custom inclusion type: {0}", custom)));

        ProductQueryBuilder queryBuilder = this.productCurator.getProductQueryBuilder()
            .addOwners(owner)
            .addProductIds(productIds)
            .addProductNames(productNames)
            .setActive(activeInc)
            .setCustom(customInc);

        return this.pagingUtilFactory.forClass(Product.class)
            .applyPaging(queryBuilder)
            .map(this.translator.getStreamMapper(Product.class, ProductDTO.class));
    }

    @Override
    @Transactional
    public ProductDTO updateProduct(String ownerKey, String productId, ProductDTO pdto) {
        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.resolveProductId(owner, productId, LockModeType.PESSIMISTIC_WRITE);

        this.validateProductNamespace(owner, product);
        this.validateProductDTO(owner, pdto);

        ProductInfo update = InfoAdapter.productInfoAdapter(pdto);
        Product updated = this.productManager.updateProduct(owner, product, update, true);

        return this.translator.translate(updated, ProductDTO.class);
    }

    @Override
    @Transactional
    public ProductDTO addContentsToProduct(String ownerKey, String productId,
        Map<String, Boolean> contentMap) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.resolveProductId(owner, productId, LockModeType.PESSIMISTIC_WRITE);

        this.validateProductNamespace(owner, product);

        // Resolve content
        if (contentMap == null) {
            throw new BadRequestException(this.i18n.tr("No content IDs provided"));
        }

        Map<String, Content> resolvedContentMap = this.resolveContentIds(owner, contentMap.keySet(),
            LockModeType.PESSIMISTIC_WRITE);

        // Compile ProductContent collection to pass in to the product manager
        Map<String, ProductContent> productContentMap = new HashMap<>();

        product.getProductContent()
            .forEach(elem -> productContentMap.put(elem.getContentId(), elem));

        // Add requested content
        for (Content content : resolvedContentMap.values()) {
            String cid = content.getId();

            productContentMap.put(cid, new ProductContent(content,
                Util.firstOf(contentMap.get(cid), ProductContent.DEFAULT_ENABLED_STATE)));
        }

        // Create a template product to use for updating the content via ProductManager, but without
        // the extraneous stuff we shouldn't be touching.
        ProductInfo update = this.buildProductInfoForBatchContentChange(product, productContentMap.values());

        Product updated = this.productManager.updateProduct(owner, product, update, true);
        return this.translator.translate(updated, ProductDTO.class);
    }

    @Override
    @Transactional
    public ProductDTO addContentToProduct(String ownerKey, String productId, String contentId,
        Boolean enabled) {

        // Package the params up and pass it off to our batch method
        return this.addContentsToProduct(ownerKey, productId, Map.of(contentId, enabled));
    }

    @Override
    @Transactional
    public ProductDTO removeContentsFromProduct(String ownerKey, String productId, List<String> contentIds) {
        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.resolveProductId(owner, productId, LockModeType.PESSIMISTIC_WRITE);

        this.validateProductNamespace(owner, product);

        // Impl note:
        // This is kinda dumb, but for some profoundly silly reason List.contains(...) will NPE if
        // the list itself doesn't support nulls, instead of just returning false straight away.
        // Worse, since it fails with an NPE and not an IAE, you can't even catch it if you value
        // the sanity of future maintainers. Very bizarre design choice...
        if (contentIds == null || contentIds.stream().anyMatch(Objects::isNull)) {
            throw new BadRequestException(
                this.i18n.tr("No content IDs provided, or provided content ID list contains null IDs"));
        }

        // Compile ProductContent collection to pass in to the product manager
        Map<String, ProductContent> productContentMap = new HashMap<>();

        product.getProductContent()
            .forEach(elem -> productContentMap.put(elem.getContentId(), elem));

        // Remove requested content links
        contentIds.forEach(productContentMap::remove);

        // Create a template product to use for updating the content via ProductManager, but without
        // the extraneous stuff we shouldn't be touching.
        ProductInfo update = this.buildProductInfoForBatchContentChange(product, productContentMap.values());

        Product updated = this.productManager.updateProduct(owner, product, update, true);
        return this.translator.translate(updated, ProductDTO.class);
    }

    @Override
    @Transactional
    public ProductDTO removeContentFromProduct(String ownerKey, String productId, String contentId) {
        // Package up the params and pass it to our bulk operation
        return this.removeContentsFromProduct(ownerKey, productId, Collections.singletonList(contentId));
    }

    @Override
    @Transactional
    public void removeProduct(String ownerKey, String productId) {
        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.resolveProductId(owner, productId, LockModeType.PESSIMISTIC_WRITE);

        this.validateProductNamespace(owner, product);

        if (this.productCurator.productHasParentSubscriptions(product)) {
            throw new BadRequestException(i18n.tr(
                "Product \"{0}\" cannot be deleted while referenced by one or more subscriptions",
                productId));
        }

        if (this.productCurator.productHasParentProducts(product)) {
            throw new BadRequestException(i18n.tr(
                "Product \"{0}\" cannot be deleted while referenced by one or more products",
                productId));
        }

        this.productManager.removeProduct(owner, product);
    }

    @Override
    @Transactional
    public AsyncJobStatusDTO refreshPoolsForProduct(String ownerKey, String productId, Boolean lazyRegen) {
        if (config.getBoolean(ConfigProperties.STANDALONE)) {
            log.warn("Ignoring refresh pools request due to standalone config.");
            return null;
        }

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.resolveProductId(owner, productId, null);
        JobConfig config = RefreshPoolsForProductJob.createJobConfig()
            .setProduct(product)
            .setLazy(lazyRegen);

        try {
            AsyncJobStatus status = jobManager.queueJob(config);
            return this.translator.translate(status, AsyncJobStatusDTO.class);
        }
        catch (JobException e) {
            String errmsg = this.i18n.tr("An unexpected exception occurred while scheduling job \"{0}\"",
                config.getJobKey());
            log.error(errmsg, e);
            throw new IseException(errmsg, e);
        }
    }

    /**
     * Retrieves an Owner instance for the owner with the specified key/account. If a matching owner
     * could not be found, this method throws an exception.
     *
     * @param key
     *  The key for the owner to retrieve
     *
     * @throws NotFoundException
     *  if an owner could not be found for the specified key.
     *
     * @return
     *  the Owner instance for the owner with the specified key.
     *
     * @httpcode 200
     * @httpcode 404
     */
    private Owner getOwnerByKey(String key) {
        Owner owner = this.ownerCurator.getByKey(key);
        if (owner == null) {
            throw new NotFoundException(i18n.tr("Owner with key \"{0}\" was not found.", key));
        }

        return owner;
    }

}
