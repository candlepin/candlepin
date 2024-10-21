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
import org.candlepin.async.JobManager;
import org.candlepin.async.tasks.RefreshPoolsJob;
import org.candlepin.auth.SecurityHole;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.ProductQueryBuilder;
import org.candlepin.model.QueryBuilder.Inclusion;
import org.candlepin.paging.PagingUtilFactory;
import org.candlepin.resource.server.v1.ProductsApi;

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Inject;

/**
 * API Gateway into /product
 */
public class ProductResource implements ProductsApi {
    private static final Logger log = LoggerFactory.getLogger(ProductResource.class);

    private final Configuration config;
    private final I18n i18n;
    private final ProductCurator productCurator;
    private final OwnerCurator ownerCurator;
    private final ProductCertificateCurator productCertCurator;
    private final PagingUtilFactory pagingUtilFactory;
    private final ModelTranslator translator;
    private final JobManager jobManager;

    @Inject
    public ProductResource(
        Configuration config,
        I18n i18n,
        ProductCurator productCurator,
        OwnerCurator ownerCurator,
        ProductCertificateCurator productCertCurator,
        PagingUtilFactory pagingUtilFactory,
        ModelTranslator translator,
        JobManager jobManager) {

        this.config = Objects.requireNonNull(config);
        this.i18n = Objects.requireNonNull(i18n);
        this.productCurator = Objects.requireNonNull(productCurator);
        this.productCertCurator = Objects.requireNonNull(productCertCurator);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.pagingUtilFactory = Objects.requireNonNull(pagingUtilFactory);
        this.translator = Objects.requireNonNull(translator);
        this.jobManager = Objects.requireNonNull(jobManager);
    }

    /**
     * Retrieves a Product instance for the product with the specified id. If no matching product
     * could be found, this method throws an exception.
     *
     * @param productUuid
     *  The ID of the product to retrieve
     *
     * @throws NotFoundException
     *  if no matching product could be found with the specified id
     *
     * @return
     *  the Product instance for the product with the specified id
     */
    private Product fetchProduct(String productUuid) {
        Product product = this.productCurator.get(productUuid);

        if (product == null) {
            throw new NotFoundException(
                i18n.tr("Product with UUID \"{0}\" could not be found.", productUuid)
            );
        }

        return product;
    }

    /**
     * Retrieves an Owner instance for the owner with the specified key/account. If a matching owner could
     * not be found, this method throws an exception.
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
    private Owner resolveOwner(String key) {
        Owner owner = this.ownerCurator.getByKey(key);
        if (owner == null) {
            throw new NotFoundException(i18n.tr("Owner with key \"{0}\" was not found.", key));
        }

        return owner;
    }

    @Override
    @Transactional
    // GET /products
    public Stream<ProductDTO> getProducts(List<String> ownerKeys, List<String> productIds,
        List<String> productNames, String active, String custom) {

        List<Owner> owners = (ownerKeys != null ? ownerKeys.stream() : Stream.<String>empty())
            .map(this::resolveOwner)
            .toList();

        Inclusion activeInc = Inclusion.fromName(active, Inclusion.EXCLUSIVE)
            .orElseThrow(() ->
                new BadRequestException(i18n.tr("Invalid active inclusion type: {0}", active)));

        Inclusion customInc = Inclusion.fromName(custom, Inclusion.INCLUDE)
            .orElseThrow(() ->
                new BadRequestException(i18n.tr("Invalid custom inclusion type: {0}", custom)));

        ProductQueryBuilder queryBuilder = this.productCurator.getProductQueryBuilder()
            .addOwners(owners)
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
    @SecurityHole
    public ProductDTO getProductByUuid(String productUuid) {
        Product product = this.fetchProduct(productUuid);
        return this.translator.translate(product, ProductDTO.class);
    }

    @Override
    @Transactional
    public Stream<AsyncJobStatusDTO> refreshPoolsForProducts(List<String> productIds, Boolean lazyRegen) {
        if (productIds.isEmpty()) {
            throw new BadRequestException(i18n.tr("No product IDs specified"));
        }

        if (config.getBoolean(ConfigProperties.STANDALONE)) {
            log.warn("Ignoring refresh pools request due to standalone config.");
            return null;
        }

        Function<Owner, JobConfig> jobConfigMapper = (owner) -> RefreshPoolsJob.createJobConfig()
            .setOwner(owner)
            .setLazyRegeneration(lazyRegen);

        Function<JobConfig, AsyncJobStatus> jobQueueMapper = (config) -> {
            try {
                return this.jobManager.queueJob(config);
            }
            catch (Exception e) {
                log.debug("Exception occurred while queueing job: {}", config, e);
                return new AsyncJobStatus()
                    .setName(RefreshPoolsJob.JOB_NAME)
                    .setState(AsyncJobStatus.JobState.FAILED)
                    .setJobResult(e.toString());
            }
        };

        return this.ownerCurator.getOwnersWithProducts(productIds)
            .stream()
            .map(jobConfigMapper)
            .map(jobQueueMapper)
            .map(this.translator.getStreamMapper(AsyncJobStatus.class, AsyncJobStatusDTO.class));
    }
}
