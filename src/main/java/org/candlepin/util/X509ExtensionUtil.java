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
import org.candlepin.model.Consumer;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.pki.OID;
import org.candlepin.pki.RepoType;
import org.candlepin.pki.X509Extension;
import org.candlepin.pki.certs.X509StringExtension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * X509ExtensionUtil for V1 Certificates
 */
@Singleton
public class X509ExtensionUtil extends X509Util {

    private static final Logger log = LoggerFactory.getLogger(X509ExtensionUtil.class);
    private final Configuration config;

    // If we're generating a cert with more content sets than this limit, we will error
    // out, as the certificate is likely too large for the CDN:
    public static final int V1_CONTENT_LIMIT = 185;

    @Inject
    public X509ExtensionUtil(Configuration config) {
        // Output everything in UTC
        this.config = config;
    }

    public Set<X509Extension> consumerExtensions(Consumer consumer) {
        return Set.of(
            // 1.3.6.1.4.1.2312.9.5.1
            new X509StringExtension(OID.System.UUID.value(), consumer.getUuid())
        );
    }

    public Set<X509Extension> subscriptionExtensions(Pool pool) {
        SimpleDateFormat iso8601DateFormat = Util.getUTCDateFormat();
        Set<X509Extension> toReturn = new LinkedHashSet<>();
        // Subscription/order info
        // need the sub product name, not id here
        // NOTE: order ~= subscription
        // entitlement == entitlement

        Product poolProduct = pool.getProduct();
        if (poolProduct == null) {
            throw new IllegalArgumentException("pool lacks a valid product");
        }

        if (poolProduct.getId() != null) {
            toReturn.add(new X509StringExtension(OID.Order.NAME.value(), poolProduct.getName()));
        }

        toReturn.add(new X509StringExtension(OID.Order.NUMBER.value(), pool.getOrderNumber()));
        toReturn.add(new X509StringExtension(OID.Order.SKU.value(), poolProduct.getId()));
        toReturn.add(new X509StringExtension(OID.Order.QUANTITY.value(), pool.getQuantity().toString()));

        String socketLimit = pool.getProduct().getAttributeValue(Product.Attributes.SOCKETS);
        if (socketLimit != null) {
            toReturn.add(new X509StringExtension(OID.Order.SOCKET_LIMIT.value(), socketLimit));
        }

        toReturn.add(new X509StringExtension(OID.Order.START_DATE.value(),
            iso8601DateFormat.format(pool.getStartDate())));
        toReturn.add(new X509StringExtension(OID.Order.END_DATE.value(),
            iso8601DateFormat.format(pool.getEndDate())));

        String warningPeriod = poolProduct.getAttributeValue(Product.Attributes.WARNING_PERIOD);
        warningPeriod = (warningPeriod == null ? "0" : warningPeriod);
        toReturn.add(new X509StringExtension(OID.Order.WARNING_PERIOD.value(), warningPeriod));

        if (pool.getContractNumber() != null) {
            toReturn.add(new X509StringExtension(OID.Order.CONTRACT_NUMBER.value(),
                pool.getContractNumber()));
        }

        // Add the account number
        if (pool.getAccountNumber() != null) {
            toReturn.add(new X509StringExtension(OID.Order.ACCOUNT_NUMBER.value(), pool.getAccountNumber()));
        }

        // Add Smart Management, default to "not managed"
        String mgmt = poolProduct.getAttributeValue(Product.Attributes.MANAGEMENT_ENABLED);
        mgmt = (mgmt == null ? "0" : mgmt);
        toReturn.add(new X509StringExtension(OID.Order.PROVIDES_MANAGEMENT.value(), mgmt));

        String supportLevel = poolProduct.getAttributeValue(Product.Attributes.SUPPORT_LEVEL);
        String supportType = poolProduct.getAttributeValue(Product.Attributes.SUPPORT_TYPE);
        if (supportLevel != null) {
            toReturn.add(new X509StringExtension(OID.Order.SUPPORT_LEVEL.value(), supportLevel));
        }

        if (supportType != null) {
            toReturn.add(new X509StringExtension(OID.Order.SUPPORT_TYPE.value(), supportType));
        }

        String stackingId = pool.getProduct().getAttributeValue(Product.Attributes.STACKING_ID);
        if (stackingId != null) {
            toReturn.add(new X509StringExtension(OID.Order.STACKING_ID.value(), stackingId));
        }

        //code "true" as "1" so it matches other bools in the cert
        String virtOnly = pool.getAttributeValue(Product.Attributes.VIRT_ONLY);
        if (virtOnly != null && virtOnly.equals("true")) {
            toReturn.add(new X509StringExtension(OID.Order.VIRT_ONLY.value(), "1"));
        }

        return toReturn;
    }

    public List<X509Extension> entitlementExtensions(Integer quantity) {
        return List.of(
            new X509StringExtension(OID.Order.QUANTITY_USED.value(), quantity.toString())
        );
    }

    public Set<X509Extension> productExtensions(Product product) {
        String arch = product.getAttributeValue(Product.Attributes.ARCHITECTURE);
        String version = product.getAttributeValue(Product.Attributes.VERSION);
        String brandType = product.getAttributeValue(Product.Attributes.BRANDING_TYPE);

        return Set.of(
            new X509StringExtension(OID.ProductCertificate.NAME.value(product.getId()), product.getName()),
            new X509StringExtension(OID.ProductCertificate.ARCH.value(product.getId()), arch),
            new X509StringExtension(OID.ProductCertificate.VERSION.value(product.getId()), version),
            new X509StringExtension(OID.ProductCertificate.BRAND_TYPE.value(product.getId()), brandType)
        );
    }

    public Set<X509Extension> contentExtensions(Collection<ProductContent> productContentList,
        PromotedContent promotedContent, Consumer consumer, Product skuProduct) {

        Set<ProductContent> productContent = new HashSet<>(productContentList);
        Set<X509Extension> toReturn = new LinkedHashSet<>();

        boolean enableEnvironmentFiltering = config.getBoolean(ConfigProperties.ENV_CONTENT_FILTERING);

        List<String> skuDisabled = skuProduct.getSkuDisabledContentIds();
        List<String> skuEnabled = skuProduct.getSkuEnabledContentIds();

        // For V1 certificates we're going to error out if we exceed a limit which is
        // likely going to generate a certificate too large for the CDN, and return an
        // informative error message to the user.
        for (ProductContent pc : productContent) {
            String contentPath = promotedContent.getPath(pc);

            // If we get a content type we don't have content type OID for
            // skip it. see rhbz#997970
            RepoType repoType = RepoType.from(pc.getContent().getType());
            if (repoType == null) {
                log.warn("No content type OID found for {} with content type: {}",
                    pc.getContent(), pc.getContent().getType());

                continue;
            }

            String contentId = pc.getContentId();
            toReturn.add(new X509StringExtension(OID.ChannelFamily.namespace(repoType, contentId),
                repoType.type()));
            toReturn.add(new X509StringExtension(OID.ChannelFamily.NAME.value(repoType, contentId),
                pc.getContent().getName()));
            toReturn.add(new X509StringExtension(OID.ChannelFamily.LABEL.value(repoType, contentId),
                pc.getContent().getLabel()));
            toReturn.add(new X509StringExtension(OID.ChannelFamily.VENDOR_ID.value(repoType, contentId),
                pc.getContent().getVendor()));
            toReturn.add(new X509StringExtension(OID.ChannelFamily.DOWNLOAD_URL.value(repoType, contentId),
                contentPath));
            toReturn.add(new X509StringExtension(OID.ChannelFamily.GPG_URL.value(repoType, contentId),
                pc.getContent().getGpgUrl()));

            Boolean enabled = pc.isEnabled();
            log.debug("default enabled flag = {}", enabled);

            // sku level content enable override. if on both lists, active wins.
            if (skuDisabled.contains(contentId)) {
                enabled = false;
            }
            if (skuEnabled.contains(contentId)) {
                enabled = true;
            }

            // Check if we should override the enabled flag due to setting on promoted
            // content:
            if (enableEnvironmentFiltering && !consumer.getEnvironmentIds().isEmpty()) {
                // we know content has been promoted at this point:
                Boolean enabledOverride = promotedContent.isEnabled(pc);
                if (enabledOverride != null) {
                    log.debug("overriding enabled flag: {}", enabledOverride);
                    enabled = enabledOverride;
                }
            }

            toReturn.add(new X509StringExtension(OID.ChannelFamily.ENABLED.value(repoType, contentId),
                (enabled) ? "1" : "0"));

            // Include metadata expiry if specified on the content:
            Long metadataExpiration = pc.getContent().getMetadataExpiration();
            if (metadataExpiration != null) {
                toReturn.add(new X509StringExtension(OID.ChannelFamily.METADATA_EXPIRE
                    .value(repoType, contentId), metadataExpiration.toString()));
            }

            // Include required tags if specified on the content set:
            String requiredTags = pc.getContent().getRequiredTags();
            if ((requiredTags != null) && !requiredTags.isBlank()) {
                toReturn.add(new X509StringExtension(OID.ChannelFamily.REQUIRED_TAGS
                    .value(repoType, contentId), requiredTags));
            }

        }

        return toReturn;
    }
}
