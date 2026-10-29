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
import org.candlepin.model.PoolExportProjection;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.version.VersionUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Gathers and translates the data required for a version 2 manifest export
 * <p>
 * Created to support separation of duties in the export code: this class gathers domain data and prepares
 * manifest DTOs, while {@link ExporterV2} handles serialization, archive layout, and file creation.
 * <p>
 * Aggregation translates the supplied consumer into a {@link ConsumerDTO} and sets its API and web URLs
 * from configuration. It also builds a {@link MetaDTO} containing the manifest format version,
 * creation time, current principal's name, and configured web URL.
 * <p>
 * Subscription selection uses the consumer's owner, rather than the consumer's entitlements. The pool
 * curator loads that owner's unexpired {@link Pool.PoolType#NORMAL} pools, including pools whose start
 * dates are in the future. When {@link ConfigProperties#EXPORT_FILTER_NON_GLOBAL_NAMESPACE} is enabled,
 * only pools referencing global products are selected.
 * <p>
 * Distinct non-null product UUIDs from those projections seed a recursive lookup of derived and provided products. The
 * product curator initializes branding, content, and product relationships for translation. The same
 * namespace setting applies throughout traversal: when enabled, non-global products are excluded and
 * traversal stops at them. Each selected pool projection supplies a {@link SubscriptionDTO}, keyed by
 * pool ID, and each resolved product into a {@link ProductDTO}, keyed by product ID. Repeated
 * IDs replace earlier map entries.
 * <p>
 * This singleton retains only injected dependencies. Each call to {@link #aggregate(Consumer)} creates
 * separate export data, reads the current configuration and principal, and captures the work directory
 * without creating it. No data from an individual export is retained between calls.
 * <p>
 * As export requirements grow, additional data gathering and translation can be added here while archive
 * writing remains the exporter's responsibility. Any new data intended for the manifest also requires
 * corresponding serialization support in the exporter.
 */
@Singleton
public class ExportDataAggregator {
    private final Configuration configuration;
    private final PoolCurator poolCurator;
    private final ProductCurator productCurator;
    private final ModelTranslator modelTranslator;
    private final PrincipalProvider principalProvider;

    /**
     * Initializes the dependencies used to gather export data
     *
     * @param modelTranslator
     *     translator for domain models and manifest DTOs
     * @param configuration
     *     source of export filtering, URLs, and work directory settings
     * @param poolCurator
     *     curator used to load eligible owner pools
     * @param productCurator
     *     curator used to resolve pool products and their related products
     * @param principalProvider
     *     provider of the principal creating each export
     * @throws NullPointerException
     *     if any dependency is null
     */
    @Inject
    public ExportDataAggregator(ModelTranslator modelTranslator, Configuration configuration, PoolCurator poolCurator,
        ProductCurator productCurator, PrincipalProvider principalProvider) {
        this.modelTranslator = Objects.requireNonNull(modelTranslator);
        this.configuration = Objects.requireNonNull(configuration);
        this.poolCurator = Objects.requireNonNull(poolCurator);
        this.productCurator = Objects.requireNonNull(productCurator);
        this.principalProvider = Objects.requireNonNull(principalProvider);
    }

    /**
     * Assembles independent data for a version 2 manifest export
     *
     * @param consumer
     *     consumer to represent in the manifest and whose owner supplies the exported pools
     * @throws NullPointerException
     *     if the consumer is null
     * @return the gathered data for this export
     */
    public ExportData aggregate(Consumer consumer) {
        Objects.requireNonNull(consumer);

        ConsumerDTO consumerDTO = this.modelTranslator.translate(consumer, ConsumerDTO.class)
            .setUrlApi(this.configuration.getString(ConfigProperties.PREFIX_APIURL))
            .setUrlWeb(this.configuration.getString(ConfigProperties.PREFIX_WEBURL));
        MetaDTO metaDTO = this.createMeta();
        Path workDirectory = Paths.get(this.configuration.getString(ConfigProperties.SYNC_WORK_DIR));
        Map<String, ProductDTO> products = new HashMap<>();
        Map<String, SubscriptionDTO> subscriptions = new HashMap<>();
        this.addProductsByConsumer(consumer, products, subscriptions);

        return new ExportData(metaDTO, consumerDTO, products, subscriptions, workDirectory);
    }

    private MetaDTO createMeta() {
        Map<String, String> version = VersionUtil.getVersionMap();
        Meta meta = new Meta();
        meta.setVersion(version.get("version") + "-" + version.get("release"));
        meta.setPrincipalName(this.principalProvider.get().getName());
        meta.setWebAppPrefix(this.configuration.getString(ConfigProperties.PREFIX_WEBURL));
        return this.modelTranslator.translate(meta, MetaDTO.class);
    }

    private void addProductsByConsumer(Consumer consumer, Map<String, ProductDTO> products,
        Map<String, SubscriptionDTO> subscriptions) {
        boolean filterNamespaces = this.configuration.getBoolean(ConfigProperties.EXPORT_FILTER_NON_GLOBAL_NAMESPACE);
        List<PoolExportProjection> pools = this.poolCurator.listByOwnerTypeWithNamespaceFilter(
            consumer.getOwner()
                .getId(),
            filterNamespaces,
            Pool.PoolType.NORMAL);

        List<Product> resolvedProducts = this.productCurator.getProductsByIdsRecursive(
            filterNamespaces,
            pools.stream()
                .map(PoolExportProjection::productUuid)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));

        pools.forEach(pool -> subscriptions.put(
            pool.poolId(),
            this.modelTranslator.translate(pool, SubscriptionDTO.class)));

        resolvedProducts.forEach(product -> products.put(product.getId(), this.modelTranslator.translate(product,
            ProductDTO.class)));
    }

}
