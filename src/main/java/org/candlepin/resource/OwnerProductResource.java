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
import org.candlepin.dto.api.server.v1.ProductCertificateDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.resource.server.v1.OwnerProductApi;
import org.candlepin.resource.util.InfoAdapter;
import org.candlepin.resource.validation.DTOValidator;

import com.google.inject.persist.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.persistence.LockModeType;

public class OwnerProductResource implements OwnerProductApi {

    private static Logger log = LoggerFactory.getLogger(OwnerProductResource.class);
    private OwnerCurator ownerCurator;
    private DTOValidator validator;
    private ModelTranslator translator;
    private I18n i18n;
    private Configuration config;
    private ProductManager productManager;
    private ProductCurator productCurator;
    private OwnerProductCurator ownerProductCurator;
    private ProductCertificateCurator productCertCurator;
    private JobManager jobManager;
    private OwnerContentCurator ownerContentCurator;

    @Inject
    @SuppressWarnings("checkstyle:parameternumber")
    public OwnerProductResource(OwnerCurator ownerCurator,
        I18n i18n,
        Configuration config,
        OwnerProductCurator ownerProductCurator,
        ModelTranslator translator,
        JobManager jobManager,
        DTOValidator validator,
        OwnerContentCurator ownerContentCurator,
        ProductManager productManager,
        ProductCertificateCurator productCertCurator,
        ProductCurator productCurator) {

        this.ownerCurator = ownerCurator;
        this.i18n = i18n;
        this.config = config;
        this.ownerProductCurator = ownerProductCurator;
        this.translator = translator;
        this.jobManager = jobManager;
        this.validator = validator;
        this.ownerContentCurator = ownerContentCurator;
        this.productManager = productManager;
        this.productCertCurator = productCertCurator;
        this.productCurator = productCurator;
    }

    @Override
    @Transactional
    public ProductDTO addBatchContent(String ownerKey, String productId, Map<String, Boolean> contentMap) {
        Owner owner = this.getOwnerByKey(ownerKey);

        // Lock the owner_product while we are doing the update for this org
        // This is done in order to prevent collisions in updates on different parts of the product
        if (!this.ownerProductCurator.lockOwnerProduct(owner, productId, LockModeType.PESSIMISTIC_WRITE)) {
            throw new NotFoundException(i18n.tr("Product with ID \"{0}\" could not be found.", productId));
        }
        Product product = this.ownerProductCurator.getProductById(owner, productId);

        if (product.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{0}\" is locked", product.getId()));
        }

        Product update = this.buildProductForBatchContentChange(product);

        boolean changed = false;
        for (Map.Entry<String, Boolean> entry : contentMap.entrySet()) {
            Content content = this.fetchContent(owner, entry.getKey());
            boolean enabled = entry.getValue() != null ?
                entry.getValue() :
                ProductContent.DEFAULT_ENABLED_STATE;

            changed |= update.addContent(content, enabled);
        }

        if (changed) {
            product = this.productManager.updateProduct(owner, update, true);
        }

        return this.translator.translate(product, ProductDTO.class);
    }

    @Override
    @Transactional
    public ProductDTO addContent(String ownerKey, String productId, String contentId, Boolean enabled) {
        // Package the params up and pass it off to our batch method
        Map<String, Boolean> contentMap = Collections.singletonMap(contentId, enabled);
        return this.addBatchContent(ownerKey, productId, contentMap);
    }

    @Override
    @Transactional
    public ProductDTO createProductByOwner(String ownerKey, ProductDTO dto) {
        if (dto.getId() == null || dto.getId().matches("^\\s*$")) {
            throw new BadRequestException(i18n.tr("product has a null or invalid ID"));
        }

        if (dto.getName() == null || dto.getName().matches("^\\s*$")) {
            throw new BadRequestException(i18n.tr("product has a null or invalid name"));
        }

        this.validator.validateCollectionElementsNotNull(dto::getBranding,
            dto::getDependentProductIds, dto::getProductContent);

        Owner owner = this.getOwnerByKey(ownerKey);
        Product entity = productManager.createProduct(owner, InfoAdapter.productInfoAdapter(dto));

        return this.translator.translate(entity, ProductDTO.class);
    }

    @Override
    @Transactional
    public void deleteProductByOwner(String ownerKey, String productId) {
        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.fetchProduct(owner, productId);

        if (product.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{0}\" is locked", product.getId()));
        }

        if (this.productCurator.productHasSubscriptions(owner, product)) {
            throw new BadRequestException(i18n.tr(
                "Product \"{0}\" cannot be deleted while referenced by one or more subscriptions",
                productId));
        }

        if (this.productCurator.productHasParentProducts(owner, product)) {
            throw new BadRequestException(i18n.tr(
                "Product \"{0}\" cannot be deleted while referenced by one or more products",
                productId));
        }

        this.productManager.removeProduct(owner, product);
    }

    @Override
    public ProductDTO getProductByOwner(@Verify(Owner.class) String ownerKey, String productId) {
        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.fetchProduct(owner, productId);

        return this.translator.translate(product, ProductDTO.class);
    }

    @Override
    @Transactional
    public ProductCertificateDTO getProductCertificateByOwner(String ownerKey, String productId) {
        if (!productId.matches("\\d+")) {
            throw new BadRequestException(i18n.tr("Only numeric product IDs are allowed."));
        }

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.fetchProduct(owner, productId);

        ProductCertificate productCertificate = this.productCertCurator.getCertForProduct(product);
        return this.translator.translate(productCertificate, ProductCertificateDTO.class);
    }

    @Override
    public CandlepinQuery<ProductDTO> getProductsByOwner(@Verify(Owner.class) String ownerKey,
        List<String> productIds) {

        Owner owner = getOwnerByKey(ownerKey);
        CandlepinQuery<Product> query = productIds != null && !productIds.isEmpty() ?
            this.ownerProductCurator.getProductsByIds(owner, productIds) :
            this.ownerProductCurator.getProductsByOwnerCPQ(owner);

        return this.translator.translateQuery(query, ProductDTO.class);
    }

    @Override
    @Transactional
    public AsyncJobStatusDTO refreshPoolsForProduct(String ownerKey, String productId, Boolean lazyRegen) {
        if (config.getBoolean(ConfigProperties.STANDALONE)) {
            log.warn("Ignoring refresh pools request due to standalone config.");
            return null;
        }

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.fetchProduct(owner, productId);
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

    @Override
    @Transactional
    public ProductDTO removeBatchContent(String ownerKey, String productId, List<String> contentIds) {
        Owner owner = this.getOwnerByKey(ownerKey);

        // Lock the owner_product while we are doing the update for this org
        // This is done in order to prevent collisions in updates on different parts of the product
        if (!this.ownerProductCurator.lockOwnerProduct(owner, productId, LockModeType.PESSIMISTIC_WRITE)) {
            throw new NotFoundException(i18n.tr("Product with ID \"{0}\" could not be found.", productId));
        }

        Product product = this.ownerProductCurator.getProductById(owner, productId);

        if (product.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{0}\" is locked", product.getId()));
        }

        Product update = this.buildProductForBatchContentChange(product);

        boolean changed = false;
        for (String contentId : contentIds) {
            changed |= update.removeContent(contentId);
        }

        if (changed) {
            product = this.productManager.updateProduct(owner, update, true);
        }

        return this.translator.translate(product, ProductDTO.class);
    }

    @Override
    @Transactional
    public ProductDTO removeContent(String ownerKey, String productId, String contentId) {
        // Package up the params and pass it to our bulk operation
        return this.removeBatchContent(ownerKey, productId, Collections.singletonList(contentId));
    }

    @Override
    @Transactional
    public ProductDTO updateProductByOwner(String ownerKey, String productId, ProductDTO update) {
        if (StringUtils.isEmpty(update.getId())) {
            update.setId(productId);
        }
        else if (!StringUtils.equals(update.getId(), productId)) {
            throw new BadRequestException(
                i18n.tr("Contradictory ids in update request: {0}, {1}", productId, update.getId()));
        }

        this.validator.validateCollectionElementsNotNull(
            update::getBranding, update::getDependentProductIds, update::getProductContent);

        Owner owner = this.getOwnerByKey(ownerKey);

        // Lock the owner_product while we are doing the update for this org.
        // This is done in order to prevent collisions in updates on different parts of the product.
        if (!this.ownerProductCurator.lockOwnerProduct(owner, productId, LockModeType.PESSIMISTIC_WRITE)) {
            throw new NotFoundException(i18n.tr("Product with ID \"{0}\" could not be found.", productId));
        }

        Product existing = this.ownerProductCurator.getProductById(owner, productId);

        if (existing.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{0}\" is locked", existing.getId()));
        }

        Product updated = this.productManager.updateProduct(owner, InfoAdapter.productInfoAdapter(update),
            true);

        return this.translator.translate(updated, ProductDTO.class);
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
    protected Owner getOwnerByKey(String key) {
        Owner owner = this.ownerCurator.getByKey(key);
        if (owner == null) {
            throw new NotFoundException(i18n.tr("Owner with key \"{0}\" was not found.", key));
        }

        return owner;
    }

    /**
     * Retrieves a Product instance for the product with the specified id. If no matching product
     * could be found, this method throws an exception.
     *
     * @param owner
     *  The organization
     *
     * @param productId
     *  The ID of the product to retrieve
     *
     * @throws NotFoundException
     *  if no matching product could be found with the specified id
     *
     * @return
     *  the Product instance for the product with the specified id
     */
    protected Product fetchProduct(Owner owner, String productId) {
        Product product = this.ownerProductCurator.getProductById(owner, productId);
        if (product == null) {
            throw new NotFoundException(i18n.tr("Product with ID \"{0}\" could not be found.", productId));
        }

        return product;
    }

    /**
     * Creates an new, unmanaged product instance using the Red Hat product ID and content of the
     * given product entity.
     *
     * @param entity
     *  the product instance from which to copy the Red Hat product ID and content
     *
     * @return
     *  an unmanaged product instance
     */
    private Product buildProductForBatchContentChange(Product entity) {
        // Impl note: we need to fully clone the object to ensure we don't make any changes to any
        // other fields; or we need to create a new product implementation that returns the correct
        // no-change value for every other field.
        return (Product) entity.clone();
    }

    /**
     * Retrieves the content entity with the given content ID for the specified owner. If a
     * matching entity could not be found, this method throws a NotFoundException.
     *
     * @param owner
     *  The owner in which to search for the content
     *
     * @param contentId
     *  The Red Hat ID of the content to retrieve
     *
     * @throws NotFoundException
     *  If a content with the specified Red Hat ID could not be found
     *
     * @return
     *  the content entity with the given owner and content ID
     */
    protected Content fetchContent(Owner owner, String contentId) {
        Content content = this.ownerContentCurator.getContentById(owner, contentId);

        if (content == null) {
            throw new NotFoundException(
                i18n.tr("Content with ID \"{0}\" could not be found.", contentId)
            );
        }

        return content;
    }
}
