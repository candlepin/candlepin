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

public class ExportData {
    private final Configuration configuration;
    private final PoolCurator poolCurator;
    private final ProductCurator productCurator;
    private final ModelTranslator modelTranslator;

    private final MetaDTO metaDTO;
    private final ConsumerDTO consumerDTO;
    private final Map<String, ProductDTO> products = new HashMap<>();
    private final Map<String, SubscriptionDTO> subscriptions = new HashMap<>();

    public ExportData(Consumer consumer, ModelTranslator modelTranslator, Configuration configuration, PoolCurator poolCurator,
        ProductCurator productCurator, PrincipalProvider principalProvider) {
        this.poolCurator = poolCurator;
        this.productCurator = productCurator;
        Objects.requireNonNull(configuration);
        Objects.requireNonNull(modelTranslator);
        Objects.requireNonNull(principalProvider);
        Objects.requireNonNull(consumer);

        this.configuration = configuration;
        this.modelTranslator = modelTranslator;

        this.consumerDTO = modelTranslator.translate(consumer, ConsumerDTO.class)
            .setUrlApi(this.getPrefixApiUrl())
            .setUrlWeb(this.getWebAppUrl());
        this.metaDTO = createMeta(configuration, principalProvider);
        this.addProductsByConsumer(consumer);
    }

    public ConsumerDTO getConsumerDTO() {
        return consumerDTO;
    }

    public Map<String, ProductDTO> getProducts() {
        return products;
    }

    public Map<String, SubscriptionDTO> getSubscriptions() {
        return subscriptions;
    }

    public MetaDTO getMetaDTO() {
        return metaDTO;
    }

    public Path getWorkDirectory() {
        return Paths.get(configuration.getString(ConfigProperties.SYNC_WORK_DIR));
    }

    private MetaDTO createMeta(Configuration configuration, PrincipalProvider principalProvider) {
        Meta meta = new Meta(this.getVersion(), new Date(), principalProvider.get()
            .getName(),
            configuration.getString(ConfigProperties.PREFIX_WEBURL), null);
        return this.modelTranslator.translate(meta, MetaDTO.class);
    }

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

    private String getVersion() {
        Map<String, String> map = VersionUtil.getVersionMap();
        return map.get("version") + "-" + map.get("release");
    }

    private String getWebAppUrl() {
        return configuration.getString(ConfigProperties.PREFIX_WEBURL);
    }

    private String getPrefixApiUrl() {
        return configuration.getString(ConfigProperties.PREFIX_APIURL);
    }
}
