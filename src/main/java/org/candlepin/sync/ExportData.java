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
import org.candlepin.dto.manifest.v2.BrandingDTO;
import org.candlepin.dto.manifest.v2.ConsumerDTO;
import org.candlepin.dto.manifest.v2.ContentDTO;
import org.candlepin.dto.manifest.v2.MetaDTO;
import org.candlepin.dto.manifest.v2.ProductDTO;
import org.candlepin.dto.manifest.v2.SubscriptionDTO;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.version.VersionUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExportData {
    private final Configuration configuration;

    private final MetaDTO metaDTO;
    private final ConsumerDTO consumerDTO;
    private final Map<String, ProductDTO> products = new HashMap<>();
    private final Map<String, SubscriptionDTO> subscriptions = new HashMap<>();

    public ExportData(Consumer consumer, ModelTranslator modelTranslator, Configuration configuration, PrincipalProvider principalProvider) {
        this.configuration = configuration;

        this.consumerDTO = modelTranslator.translate(consumer, ConsumerDTO.Builder.class)
            .setUrlApi(getPrefixApiUrl())
            .setUrlWeb(getWebAppUrl())
            .build();
        this.metaDTO = createMeta(configuration, principalProvider);
        addProductsByConsumer(consumer);
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
        return MetaDTO.builder()
            .setManifestVersion("2")
            .setCreated(new Date())
            .setPrincipalName(principalProvider.get().getUsername())
            .setVersion(getVersion())
            .setWebAppPrefix(configuration.getString(ConfigProperties.PREFIX_WEBURL))
            .build();
    }

    private void addProductsByConsumer(Consumer consumer) {
        // TODO: This could be bulked into a single query rather than iterating and likely hitting
        // a bunch of lazy lookups.
        for (Entitlement entitlement : consumer.getEntitlements()) {
            Pool pool = entitlement.getPool();

            subscriptions.put(
                pool.getSubscriptionId(),
                SubscriptionDTO.builder()
                    .setAccountNumber(pool.getAccountNumber())
                    .setContractNumber(pool.getContractNumber())
                    .setSubscriptionId(pool.getSubscriptionId())
                    .setProductId(pool.getProductId())
                    .setStartDate(entitlement.getStartDate())
                    .setEndDate(entitlement.getEndDate())
                    .build());

            Product product = pool.getProduct();

            this.collectProducts(product, products);
        }
    }

    /**
     * Adds the specified product and all of its children products to the given product map, using
     * products' ID (*not* UUID) as the key in the map. If the specified product is null or does not
     * have a product ID, it will be silently ignored.
     *
     * @param product    the product to add to the map
     * @param productMap the map in which to collect products
     */
    private void collectProducts(Product product, Map<String, ProductDTO> productMap) {
        if (product == null || product.getId() == null) {
            return;
        }

        productMap.put(product.getId(), convertProduct(product));

        // Add provided products (if applicable)
        Collection<Product> providedProducts = product.getProvidedProducts();
        if (providedProducts != null) {
            providedProducts.forEach(p -> this.collectProducts(p, productMap));
        }

        // Recursively add derived product (if applicable)
        this.collectProducts(product.getDerivedProduct(), productMap);
    }

    private ProductDTO convertProduct(Product product) {
        if (product == null || product.getId() == null) {
            return null;
        }

        return ProductDTO.builder()
            .setId(product.getId())
            .setName(product.getName())
            .setDerivedProduct(convertProduct(product.getDerivedProduct()))
            .setAttributes(product.getAttributes())
            .setProductContent(convertContent(product))
            .setBrandings(convertBranding(product))
            .build();
    }

    private Map<String, ContentDTO> convertContent(Product product) {
        return product.getProductContent()
            .stream()
            .map(ProductContent::getContent)
            .map(content ->
                ContentDTO.builder()
                    .setId(content.getId())
                    .setName(content.getName())
                    .setType(content.getType())
                    .setLabel(content.getLabel())
                    .setVendor(content.getVendor())
                    .setContentUrl(content.getContentUrl())
                    .setRequiredTags(content.getRequiredTags())
                    .setReleaseVer(content.getReleaseVersion())
                    .setGpgUrl(content.getGpgUrl())
                    .setArches(content.getArches())
                    .build())
            .collect(Collectors.toMap(ContentDTO::id, Function.identity()));
    }

    private Set<BrandingDTO> convertBranding(Product product) {
        return product.getBranding()
            .stream()
            .map(branding ->
                BrandingDTO.builder()
                    .setId(branding.getId())
                    .setProductId(branding.getProductId())
                    .setName(branding.getName())
                    .setType(branding.getType())
                    .build())
            .collect(Collectors.toSet());
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
