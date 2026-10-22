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

package org.candlepin.sync;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.manifest.v2.ConsumerDTO;
import org.candlepin.dto.manifest.v2.MetaDTO;
import org.candlepin.dto.manifest.v2.ProductDTO;
import org.candlepin.dto.manifest.v2.SubscriptionDTO;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.version.VersionUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Gathers and translates the data required for a version 2 manifest export
 * <p>
 * Created to support separation of duties in the export code: this class gathers domain data and prepares
 * manifest DTOs, while {@link ExporterV2} handles serialization, archive layout, and file creation.
 * <p>
 * Construction translates the supplied consumer into a {@link ConsumerDTO} and sets its API and web URLs
 * from configuration. It also builds a {@link MetaDTO} containing the Candlepin version and release,
 * creation time, current principal's name, and configured web URL.
 * <p>
 * Subscription selection uses the consumer's owner, rather than the consumer's entitlements. The pool
 * curator loads that owner's unexpired {@link Pool.PoolType#NORMAL} pools, including pools whose start
 * dates are in the future. When {@link ConfigProperties#EXPORT_FILTER_NON_GLOBAL_NAMESPACE} is enabled,
 * only pools referencing global products are selected.
 * <p>
 * Non-null product IDs from those pools seed a recursive lookup of derived and provided products. The
 * product curator initializes branding, content, and product relationships for translation. The same
 * namespace setting applies throughout traversal: when enabled, non-global products are excluded and
 * traversal stops at them. Each selected pool is translated into a {@link SubscriptionDTO}, keyed by
 * subscription ID, and each resolved product into a {@link ProductDTO}, keyed by product ID. Repeated
 * IDs replace earlier map entries.
 * <p>
 * Create an instance for each export. Data is gathered eagerly during construction and is not refreshed
 * from persistence; getters expose the mutable DTOs and maps. The work directory is read from configuration
 * when requested, and its creation is left to the exporter.
 * <p>
 * As export requirements grow, additional data gathering and translation can be added here while archive
 * writing remains the exporter's responsibility. Any new data intended for the manifest also requires
 * corresponding serialization support in the exporter.
 */
public class ExportData {
    /**
     * Export configuration, including namespace filtering and URL and work directory settings
     */
    private final Configuration configuration;
    /**
     * Source of owner pools eligible for export
     */
    private final PoolCurator poolCurator;
    /**
     * Source of products and their recursively resolved relationships
     */
    private final ProductCurator productCurator;
    /**
     * Translator from domain models to version 2 manifest DTOs
     */
    private final ModelTranslator modelTranslator;

    /**
     * Metadata captured when this export data is constructed
     */
    private final MetaDTO metaDTO;
    /**
     * Consumer representation with configured API and web application URLs
     */
    private final ConsumerDTO consumerDTO;
    /**
     * Exported products keyed by product ID
     */
    private final Map<String, ProductDTO> products = new HashMap<>();
    /**
     * Exported subscriptions keyed by subscription ID
     */
    private final Map<String, SubscriptionDTO> subscriptions = new HashMap<>();

    /**
     * Assembles the data for an export of the supplied consumer's owner
     *
     * @param consumer
     *     consumer to represent in the manifest and whose owner supplies the exported pools
     * @param modelTranslator
     *     translator for domain models and manifest DTOs
     * @param configuration
     *     source of export filtering, URLs, and work directory settings
     * @param poolCurator
     *     curator used to load eligible normal pools for the consumer's owner
     * @param productCurator
     *     curator used to resolve pool products and their related products
     * @param principalProvider
     *     provider of the current principal whose name identifies the export creator
     * @throws NullPointerException
     *     if any argument is null
     */
    public ExportData(Consumer consumer, ModelTranslator modelTranslator, Configuration configuration, PoolCurator poolCurator,
        ProductCurator productCurator, PrincipalProvider principalProvider) {
        this.poolCurator = Objects.requireNonNull(poolCurator);
        this.productCurator = Objects.requireNonNull(productCurator);
        this.configuration = Objects.requireNonNull(configuration);
        this.modelTranslator = Objects.requireNonNull(modelTranslator);
        Objects.requireNonNull(principalProvider);
        Objects.requireNonNull(consumer);

        this.consumerDTO = modelTranslator.translate(consumer, ConsumerDTO.class)
            .setUrlApi(this.getPrefixApiUrl())
            .setUrlWeb(this.getWebAppUrl());
        this.metaDTO = createMeta(configuration, principalProvider);
        this.addProductsByConsumer(consumer);
    }

    /**
     * Consumer representation for serialization
     *
     * @return the mutable consumer DTO held by this instance
     */
    public ConsumerDTO getConsumerDTO() {
        return consumerDTO;
    }

    /**
     * Products collected for this export
     *
     * @return the live, mutable map of product IDs to product DTOs; empty when no products were resolved
     */
    public Map<String, ProductDTO> getProducts() {
        return products;
    }

    /**
     * Subscriptions collected from eligible owner pools
     *
     * @return the live, mutable map of subscription IDs to subscription DTOs; empty when no pools were found
     */
    public Map<String, SubscriptionDTO> getSubscriptions() {
        return subscriptions;
    }

    /**
     * Export metadata, including creation time, creator, and Candlepin version
     *
     * @return the mutable metadata DTO held by this instance
     */
    public MetaDTO getMetaDTO() {
        return metaDTO;
    }

    /**
     * Configured directory for creating the export archive
     * <p>
     * Reads the configuration on each call without creating or validating the directory.
     *
     * @return the path configured by {@link ConfigProperties#SYNC_WORK_DIR}
     */
    public Path getWorkDirectory() {
        return Paths.get(configuration.getString(ConfigProperties.SYNC_WORK_DIR));
    }

    /**
     * Builds manifest metadata using the current time, principal, and server settings
     *
     * @param configuration
     *     source of the web application URL
     * @param principalProvider
     *     source of the export creator's name
     * @return the translated metadata DTO
     */
    private MetaDTO createMeta(Configuration configuration, PrincipalProvider principalProvider) {
        Meta meta = new Meta(this.getVersion(), new Date(), principalProvider.get()
            .getName(),
            configuration.getString(ConfigProperties.PREFIX_WEBURL), null);
        return this.modelTranslator.translate(meta, MetaDTO.class);
    }

    /**
     * Populates subscriptions and recursively resolved products from eligible normal owner pools
     * <p>
     * Entries sharing an ID replace earlier entries in the corresponding map.
     *
     * @param consumer
     *     consumer whose owner supplies the pools, subject to configured namespace filtering
     */
    private void addProductsByConsumer(Consumer consumer) {
        boolean filterNamespaces = this.configuration.getBoolean(ConfigProperties.EXPORT_FILTER_NON_GLOBAL_NAMESPACE);
        List<Pool> pools = this.poolCurator.listByOwnerForExportV2(
            consumer.getOwner()
                .getId(),
            filterNamespaces,
            Pool.PoolType.NORMAL);

        List<Product> products = this.productCurator.getProductsByIdsRecursiveForExportV2(
            filterNamespaces,
            pools.stream()
                .map(Pool::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));

        pools.forEach(pool -> this.subscriptions.put(
            pool.getSubscriptionId(),
            this.modelTranslator.translate(pool, SubscriptionDTO.class)));

        products.forEach(product -> this.products.put(product.getId(), this.modelTranslator.translate(product,
            ProductDTO.class)));
    }

    /**
     * Candlepin version identifier for manifest metadata
     *
     * @return the server version and release joined by a hyphen
     */
    private String getVersion() {
        Map<String, String> map = VersionUtil.getVersionMap();
        return map.get("version") + "-" + map.get("release");
    }

    /**
     * Configured web application URL
     *
     * @return the configured web URL prefix
     */
    private String getWebAppUrl() {
        return configuration.getString(ConfigProperties.PREFIX_WEBURL);
    }

    /**
     * Configured API URL
     *
     * @return the configured API URL prefix
     */
    private String getPrefixApiUrl() {
        return configuration.getString(ConfigProperties.PREFIX_APIURL);
    }
}
