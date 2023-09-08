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
import org.candlepin.async.tasks.RefreshPoolsForProductsJob;
import org.candlepin.auth.Verify;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.ProductManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.server.v1.ProductCertificateDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.resource.server.v1.ProductsApi;
import org.candlepin.resource.util.InfoAdapter;
import org.candlepin.resource.validation.DTOValidator;

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.persistence.LockModeType;



/**
 * API Gateway into /product
 */
public class ProductResource implements ProductsApi {
    private static final Logger log = LoggerFactory.getLogger(ProductResource.class);

    private final Configuration config;
    private final I18n i18n;
    private final ModelTranslator translator;
    private final DTOValidator validator;

    private final ProductManager productManager;
    private final JobManager jobManager;

    private final ProductCurator productCurator;
    private final ProductCertificateCurator productCertCurator;
    private final ContentCurator contentCurator;

    @Inject
    public ProductResource(
        Configuration config, I18n i18n, ModelTranslator translator, DTOValidator validator,
        ProductManager productManager, JobManager jobManager, ProductCurator productCurator,
        ProductCertificateCurator productCertCurator, ContentCurator contentCurator) {

        this.config = Objects.requireNonNull(config);
        this.i18n = Objects.requireNonNull(i18n);
        this.translator = Objects.requireNonNull(translator);
        this.validator = Objects.requireNonNull(validator);

        this.productManager = Objects.requireNonNull(productManager);
        this.jobManager = Objects.requireNonNull(jobManager);

        this.productCurator = Objects.requireNonNull(productCurator);
        this.productCertCurator = Objects.requireNonNull(productCertCurator);
        this.contentCurator = Objects.requireNonNull(contentCurator);
    }

    /**
     * Retrieves a Product instance for the product with the specified ID. If no matching product
     * could be found, this method throws an exception.
     *
     * @param productId
     *  The ID of the product to retrieve
     *
     * @param lockMode
     *  the locking mode with which to fetch the product, or null to omit locking the product
     *
     * @throws NotFoundException
     *  if no matching product could be found with the specified ID
     *
     * @return
     *  the product instance for the product with the specified ID
     */
    private Product resolveProductId(String productId, LockModeType lockMode) {
        Product product = this.productCurator.getProductById(productId, lockMode);

        if (product == null) {
            throw new NotFoundException(i18n.tr("Product with ID \"{0}\" could not be found.", productId));
        }

        return product;
    }

    /**
     * Alias for resolveProductId that omits the lock mode.
     *
     * @param productId
     *  The ID of the product to retrieve
     *
     * @throws NotFoundException
     *  if no matching product could be found with the specified ID
     *
     * @return
     *  the product instance for the product with the specified ID
     */
    private Product resolveProductId(String productId) {
        return this.resolveProductId(productId, null);
    }

    /**
     * Retrieves a mapping of content IDs to content instances for the given content IDs. If one or
     * more IDs could not be resolved to existing content, this method throws an exception.
     *
     * @param contentIds
     *  a collection of content IDs to resolve to content instances
     *
     * @throws NotFoundException
     *  if one or more content IDs could not be resolved to an existing content instance
     *
     * @return
     *  a map of content IDs to resolved content instances
     */
    private Map<String, Content> resolveContentIds(Collection<String> contentIds) {
        // Convert the contentIds to a set so we can easily/accurately check if our resolution step
        // failed for any of the IDs
        Set<String> ids = contentIds instanceof Set ? (Set<String>) contentIds : (new HashSet<>(contentIds));

        Map<String, Content> resolved = this.contentCurator.getContentsByIds(ids);

        if (resolved.size() != ids.size()) {
            ids.removeAll(resolved.keySet());
            throw new NotFoundException(this.i18n.tr("One or more contents could not be found: {0}", ids));
        }

        return resolved;
    }

    /**
     * GET /products
     */
    @Override
    @Transactional
    public Stream<ProductDTO> getProductsByIds(List<String> productIds) {
        Collection<Product> products = productIds != null && !productIds.isEmpty() ?
            this.productCurator.getProductsByIds(productIds).values() :
            this.productCurator.listAll().list();

        return products.stream()
            .map(this.translator.getStreamMapper(Product.class, ProductDTO.class));
    }

    /**
     * GET /products/{product_id}
     */
    @Override
    @Transactional
    public ProductDTO getProductById(String productId) {
        Product product = this.resolveProductId(productId);

        return this.translator.translate(product, ProductDTO.class);
    }

    /**
     * POST /products/{product_id}
     */
    @Override
    @Transactional
    public ProductDTO createProduct(ProductDTO productDTO) {
        if (productDTO.getId() == null || productDTO.getId().isBlank()) {
            throw new BadRequestException(i18n.tr("product has a null or invalid ID"));
        }

        if (productDTO.getName() == null || productDTO.getName().isBlank()) {
            throw new BadRequestException(i18n.tr("product has a null or invalid name"));
        }

        if (this.productCurator.productExistsById(productDTO.getId())) {
            throw new BadRequestException(i18n.tr("a product already exists with ID: " + productDTO.getId()));
        }

        this.validator.validateCollectionElementsNotNull(productDTO::getBranding,
            productDTO::getDependentProductIds, productDTO::getProductContent);

        Product created = this.productManager.createProduct(InfoAdapter.productInfoAdapter(productDTO));
        return this.translator.translate(created, ProductDTO.class);
    }

    /**
     * PUT /products/{product_id}
     */
    @Override
    @Transactional
    public ProductDTO updateProduct(String productId, ProductDTO productDTO) {
        // TODO: FIXME: No matter when we lock in this method, we run the risk of our update
        // clobbering a parallel update. Our best-case scenario is that we do our initial fetch
        // with a lock, but transaction isolation or Hibernate caching may still burn us there since
        // by the time we apply our lock, something else may have snuck through with its lookup.
        Product product = this.resolveProductId(productId, LockModeType.PESSIMISTIC_WRITE);

        if (product.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{0}\" is locked", product.getId()));
        }

        this.validator.validateCollectionElementsNotNull(
            productDTO::getBranding, productDTO::getDependentProductIds, productDTO::getProductContent);

        // This field should be ignored during an update anyway, but just to be certain, we'll
        // override it with the ID specified in the request
        productDTO.setId(productId);

        Product updated = this.productManager.updateProduct(product,
            InfoAdapter.productInfoAdapter(productDTO));

        return this.translator.translate(updated, ProductDTO.class);
    }

    /**
     * POST /{product_id}/batch_content
     */
    @Override
    @Transactional
    public ProductDTO addMultipleContentsToProduct(String productId, Map<String, Boolean> contentMap) {
        Product product = this.resolveProductId(productId, LockModeType.PESSIMISTIC_WRITE);

        if (product.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{0}\" is locked", product.getId()));
        }

        Map<String, Content> resolvedContent = this.resolveContentIds(contentMap.keySet());
        boolean changed = false;

        for (Map.Entry<String, Content> entry : resolvedContent.entrySet()) {
            String cid = entry.getKey();

            // Normalize enabled value so we don't break anything
            Boolean enabled = contentMap.get(cid);
            enabled = enabled != null ? enabled : ProductContent.DEFAULT_ENABLED_STATE;

            changed |= product.addContent(entry.getValue(), enabled);
        }

        if (changed) {
            product = this.productCurator.merge(product);
        }

        return this.translator.translate(product, ProductDTO.class);
    }

    /**
     * POST /{product_id}/content/{content_id}
     */
    @Override
    public ProductDTO addContentToProduct(String productId, String contentId, Boolean enabled) {
        // TODO: Should probably verify that contentId is not null here to avoid problems with Map.of
        return this.addMultipleContentsToProduct(productId, Map.of(contentId, enabled));
    }

    /**
     * DELETE /{product_id}/batch_content
     */
    @Override
    @Transactional
    public ProductDTO removeMultipleContentsFromProduct(String productId, List<String> contentIds) {
        Product product = this.resolveProductId(productId, LockModeType.PESSIMISTIC_WRITE);

        if (product.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{0}\" is locked", product.getId()));
        }

        boolean changed = false;

        for (String cid : contentIds) {
            changed = product.removeContent(cid);
        }

        if (changed) {
            product = this.productCurator.merge(product);
        }

        return this.translator.translate(product, ProductDTO.class);
    }

    /**
     * DELETE /{product_id}/content/{content_id}
     */
    @Override
    public ProductDTO removeContentFromProduct(String productId, String contentId) {
        return this.removeMultipleContentsFromProduct(productId, List.of(contentId));
    }

    /**
     * DELETE /products/{product_id}
     */
    @Override
    @Transactional
    public void deleteProduct(String productId) {
        Product product = this.resolveProductId(productId, LockModeType.PESSIMISTIC_WRITE);

        if (product.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{0}\" is locked", product.getId()));
        }

        if (this.productCurator.productHasSubscriptions(product)) {
            throw new BadRequestException(i18n.tr(
                "Product \"{0}\" cannot be deleted while referenced by one or more subscriptions",
                productId));
        }

        if (this.productCurator.productHasParentProducts(product)) {
            throw new BadRequestException(i18n.tr(
                "Product \"{0}\" cannot be deleted while referenced by one or more parent products",
                productId));
        }

        this.productManager.deleteProduct(product);
    }

    /**
     * GET /products/{product_id}/certificate
     */
    @Override
    @Transactional
    public ProductCertificateDTO getProductCertificate(String productId) {
        if (productId == null || !productId.matches("\\d+")) {
            throw new BadRequestException(
                i18n.tr("Only engineering products with numeric IDs may have product certificates"));
        }

        Product product = this.resolveProductId(productId);
        ProductCertificate productCertificate = this.productCertCurator.getCertForProduct(product);

        return this.translator.translate(productCertificate, ProductCertificateDTO.class);
    }

    /**
     * PUT /products/subscriptions
     */
    @Override
    public AsyncJobStatusDTO refreshPoolsForProducts(List<String> productIds, Boolean lazyRegen) {
        if (productIds == null || productIds.isEmpty()) {
            throw new BadRequestException(this.i18n.tr("No product IDs specified"));
        }

        if (config.getBoolean(ConfigProperties.STANDALONE)) {
            log.warn("Ignoring refresh pools request due to standalone config.");
            return null;
        }

        // TODO: FIXME: rewrite this to use the RefreshPoolsForProduct job, which itself needs a
        // rewrite to use the refresher's ability to specify multiple products

        Map<String, Product> productMap = this.productCurator.getProductsByIds(productIds);

        if (productMap.size() != productIds.size()) {
            Set<String> ids = new HashSet<>(productIds);
            ids.removeAll(productMap.keySet());

            throw new NotFoundException(this.i18n.tr("One or more products could not be found: {0}", ids));
        }

        JobConfig config = RefreshPoolsForProductsJob.createJobConfig()
            .setProducts(productMap.values())
            .setLazy(lazyRegen);

        try {
            AsyncJobStatus status = this.jobManager.queueJob(config);
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
     * PUT /products/{product_id}/subscriptions
     */
    @Override
    public AsyncJobStatusDTO refreshPoolsForProduct(String productId, Boolean lazyRegen) {
        if (productId == null || productId.isEmpty()) {
            throw new BadRequestException(i18n.tr("No product ID specified"));
        }

        return this.refreshPoolsForProducts(List.of(productId), lazyRegen);
    }

}
