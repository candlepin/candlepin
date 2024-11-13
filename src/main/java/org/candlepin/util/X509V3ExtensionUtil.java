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
package org.candlepin.util;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.util.PromotedContent;
import org.candlepin.model.Branding;
import org.candlepin.model.Consumer;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.dto.Content;
import org.candlepin.model.dto.EntitlementBody;
import org.candlepin.pki.OID;
import org.candlepin.pki.X509Extension;
import org.candlepin.pki.certs.X509ByteExtension;
import org.candlepin.pki.certs.X509StringExtension;
import org.candlepin.pki.huffman.Huffman;

import com.google.common.collect.Collections2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;

import javax.inject.Inject;
import javax.inject.Singleton;


@Singleton
public class X509V3ExtensionUtil extends X509Util {
    private static final Logger log = LoggerFactory.getLogger(X509V3ExtensionUtil.class);
    public static final String CERT_VERSION = "3.4";

    private final Configuration config;
    private final EntitlementCurator entCurator;
    private final Huffman huffman;

    @Inject
    public X509V3ExtensionUtil(Configuration config, EntitlementCurator entCurator, Huffman huffman) {
        this.config = Objects.requireNonNull(config);
        this.entCurator = Objects.requireNonNull(entCurator);
        this.huffman = Objects.requireNonNull(huffman);
    }

    public Set<X509Extension> getExtensions() {
        return Set.of(
            new X509StringExtension(OID.EntitlementVersion.namespace(), CERT_VERSION)
        );
    }

    public Set<X509Extension> getByteExtensions(
        List<org.candlepin.model.dto.Product> productModels) throws IOException {
        Set<X509Extension> toReturn = new LinkedHashSet<>();

        EntitlementBody eb = createEntitlementBodyContent(productModels);

        toReturn.add(new X509ByteExtension(
            OID.EntitlementData.namespace(), retrieveContentValue(eb)));

        return toReturn;
    }

    private byte[] retrieveContentValue(EntitlementBody eb) throws IOException {
        List<Content> contentList = getContentList(eb);
        return this.huffman.retrieveContentValue(contentList);
    }

    public EntitlementBody createEntitlementBodyContent(
        List<org.candlepin.model.dto.Product> productModels) {

        EntitlementBody toReturn = new EntitlementBody();
        toReturn.setProducts(productModels);

        return toReturn;
    }

    public List<org.candlepin.model.dto.Product> createProducts(Product sku,
        Set<Product> products, PromotedContent promotedContent,
        Consumer consumer, Pool pool, Set<Pool> entitledPools) {

        List<org.candlepin.model.dto.Product> toReturn = new ArrayList<>();

        Set<String> entitledProductIds = entCurator.listEntitledProductIds(consumer, pool, entitledPools);

        for (Product p : Collections2.filter(products, PROD_FILTER_PREDICATE)) {
            toReturn.add(mapProduct(p, sku, promotedContent, consumer, pool, entitledProductIds));
        }

        return toReturn;
    }

    public org.candlepin.model.dto.Product mapProduct(Product engProduct, Product sku,
        PromotedContent promotedContent, Consumer consumer, Pool pool, Set<String> entitledProductIds) {

        org.candlepin.model.dto.Product toReturn = new org.candlepin.model.dto.Product();

        toReturn.setId(engProduct.getId());
        toReturn.setName(engProduct.getName());

        String version = engProduct.getAttributeValue(Product.Attributes.VERSION);
        toReturn.setVersion(version != null ? version : "");

        Branding brand = getBranding(pool, engProduct.getId());
        toReturn.setBrandType(brand.getType());
        toReturn.setBrandName(brand.getName());

        String productArches = engProduct.getAttributeValue(Product.Attributes.ARCHITECTURE);
        Set<String> productArchSet = Arch.parseArches(productArches);

        // FIXME: getParsedArches might make more sense to just return a list
        List<String> archList = new ArrayList<>(productArchSet);
        toReturn.setArchitectures(archList);
        boolean enableEnvironmentFiltering = config.getBoolean(ConfigProperties.ENV_CONTENT_FILTERING);
        boolean isUsingSCA = consumer == null ? true : consumer.getOwner().isUsingSimpleContentAccess();
        Set<ProductContent> filteredContent = filterProductContent(engProduct, consumer, promotedContent,
            enableEnvironmentFiltering, entitledProductIds, isUsingSCA);
        List<Content> content = createContent(filteredContent, sku, promotedContent,
            consumer, engProduct);
        toReturn.setContent(content);

        return toReturn;
    }

    /*
     * Return a branding object for the given engineering product ID if one exists for
     * the pool in question.
     */
    private Branding getBranding(Pool pool, String productId) {
        List<Branding> branding = pool.getProduct()
            .getBranding()
            .stream()
            .filter(elem -> productId.equals(elem.getProductId()))
            .toList();

        // If none exist, use null strings
        if (branding.isEmpty()) {
            return new Branding(productId, "", "");
        }

        // If multiple matching brands found, warn and then use the first
        if (branding.size() > 1) {
            log.warn("Found multiple brand names: product={}, contract={}, owner={}",
                productId, pool.getContractNumber(), pool.getOwner().getKey());
        }

        return branding.get(0);
    }

    /*
     * createContent
     *
     * productArchList is a list of arch strings parse from
     *   product attributes.
     */
    public List<Content> createContent(Set<ProductContent> productContent, Product sku,
        PromotedContent promotedContent, Consumer consumer, Product product) {

        List<Content> toReturn = new ArrayList<>();

        boolean enableEnvironmentFiltering = config.getBoolean(ConfigProperties.ENV_CONTENT_FILTERING);

        // Return only the contents that are arch appropriate
        Set<ProductContent> archAppropriateProductContent = filterContentByContentArch(
            productContent, consumer, product);

        List<String> skuDisabled = sku.getSkuDisabledContentIds();
        List<String> skuEnabled = sku.getSkuEnabledContentIds();

        for (ProductContent pc : archAppropriateProductContent) {
            Content content = new Content();

            String contentPath = promotedContent.getPath(pc);

            content.setId(pc.getContent().getId());
            content.setType(pc.getContent().getType());
            content.setName(pc.getContent().getName());
            content.setLabel(pc.getContent().getLabel());
            content.setVendor(pc.getContent().getVendor());
            content.setPath(contentPath);
            content.setGpgUrl(pc.getContent().getGpgUrl());

            // Set content model's arches here, inheriting from the product if
            // they are not set on the content.
            List<String> archesList = new ArrayList<>();

            Set<String> contentArches = Arch.parseArches(pc.getContent()
                .getArches());
            if (contentArches.isEmpty()) {
                archesList.addAll(Arch.parseArches(
                    product.getAttributeValue(Product.Attributes.ARCHITECTURE)));
            }
            else {
                archesList.addAll(Arch.parseArches(pc.getContent().getArches()));
            }
            content.setArches(archesList);

            Boolean enabled = pc.isEnabled();

            // sku level content enable override. if on both lists, active wins.
            if (skuDisabled.contains(pc.getContent().getId())) {
                enabled = false;
            }

            if (skuEnabled.contains(pc.getContent().getId())) {
                enabled = true;
            }

            // Check if we should override the enabled flag due to setting on promoted content
            if (enableEnvironmentFiltering && consumer != null && !consumer.getEnvironmentIds().isEmpty()) {
                // we know content has been promoted at this point
                Boolean enabledOverride = promotedContent.isEnabled(pc);
                if (enabledOverride != null) {
                    log.debug("overriding enabled flag: {}", enabledOverride);
                    enabled = enabledOverride;
                }
            }

            // only included if not the default value of true
            if (!enabled) {
                content.setEnabled(enabled);
            }

            // Include metadata expiry if specified on the content
            if (pc.getContent().getMetadataExpiration() != null) {
                content.setMetadataExpiration(pc.getContent().getMetadataExpiration());
            }

            // Include required tags if specified on the content set
            String requiredTags = pc.getContent().getRequiredTags();
            if ((requiredTags != null) && !requiredTags.equals("")) {
                StringTokenizer st = new StringTokenizer(requiredTags, ",");
                List<String> tagList = new ArrayList<>();
                while (st.hasMoreElements()) {
                    tagList.add((String) st.nextElement());
                }
                content.setRequiredTags(tagList);
            }
            toReturn.add(content);
        }
        return toReturn;
    }

    protected List<Content> getContentList(EntitlementBody eb) {
        // collect content URL's
        List<Content> contentList = new ArrayList<>();
        for (org.candlepin.model.dto.Product p : eb.getProducts()) {
            contentList.addAll(p.getContent());
        }
        return contentList;
    }

}
