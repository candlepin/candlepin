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
package org.candlepin.resource;

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobManager;
import org.candlepin.async.tasks.RefreshPoolsJob;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.ProductCertificateDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.ResultIterator;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * API Gateway into /product
 */
public class ProductResource implements ProductsApi {

    private static final Logger log = LoggerFactory.getLogger(ProductResource.class);
    private final ProductCurator productCurator;
    private final OwnerCurator ownerCurator;
    private final ProductCertificateCurator productCertCurator;
    private final Configuration config;
    private final I18n i18n;
    private final ModelTranslator translator;
    private final JobManager jobManager;

    @Inject
    public ProductResource(ProductCurator productCurator, OwnerCurator ownerCurator,
        ProductCertificateCurator productCertCurator, Configuration config, I18n i18n,
        ModelTranslator translator, JobManager jobManager) {

        this.productCurator = Objects.requireNonNull(productCurator);
        this.productCertCurator = Objects.requireNonNull(productCertCurator);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.config = Objects.requireNonNull(config);
        this.i18n = Objects.requireNonNull(i18n);
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

    @Override
    @SecurityHole
    public ProductDTO getProduct(String productUuid) {
        Product product = this.fetchProduct(productUuid);
        return this.translator.translate(product, ProductDTO.class);
    }

    @Override
    @SecurityHole
    public ProductCertificateDTO getProductCertificate(String productUuid) {
        // TODO:
        // Should this be enabled globally? This will create a cert if it hasn't yet been created.

        Product product = this.fetchProduct(productUuid);
        ProductCertificate productCertificate = this.productCertCurator.getCertForProduct(product);
        return this.translator.translate(productCertificate, ProductCertificateDTO.class);
    }

    @Override
    public CandlepinQuery<OwnerDTO> getProductOwners(List<String> productUuids) {
        if (productUuids.isEmpty()) {
            throw new BadRequestException(i18n.tr("No product IDs specified"));
        }

        return this.translator.translateQuery(
            this.ownerCurator.getOwnersWithProducts(productUuids), OwnerDTO.class);
    }

    @Override
    public Stream<AsyncJobStatusDTO> refreshPoolsForProduct(
        List<String> productUuids, Boolean lazyRegen) {

        if (productUuids.isEmpty()) {
            throw new BadRequestException(i18n.tr("No product IDs specified"));
        }

        if (config.getBoolean(ConfigProperties.STANDALONE)) {
            log.warn("Ignoring refresh pools request due to standalone config.");
            return null;
        }

        ResultIterator<Owner> iterator = this.ownerCurator.getOwnersWithProducts(productUuids).iterate();
        List<JobConfig<RefreshPoolsJob.RefreshPoolsJobConfig>> jobConfigs = new LinkedList<>();
        while (iterator.hasNext()) {
            JobConfig<RefreshPoolsJob.RefreshPoolsJobConfig> config = RefreshPoolsJob.createJobConfig()
                .setOwner(iterator.next())
                .setLazyRegeneration(lazyRegen);
            jobConfigs.add(config);
        }
        iterator.close();

        List<AsyncJobStatus> statuses = new LinkedList<>();
        for (JobConfig<RefreshPoolsJob.RefreshPoolsJobConfig> config : jobConfigs) {
            try {
                statuses.add(this.jobManager.queueJob(config));
            }
            catch (Exception e) {
                statuses.add(new AsyncJobStatus()
                    .setName(RefreshPoolsJob.JOB_NAME)
                    .setState(AsyncJobStatus.JobState.FAILED)
                    .setJobResult(e.toString()));
            }
        }

        return statuses.stream().map(
            this.translator.getStreamMapper(AsyncJobStatus.class, AsyncJobStatusDTO.class));
    }
}
