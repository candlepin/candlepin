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
package org.candlepin.util;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.pki.X509ExtensionWrapper;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * X509ExtensionUtil for V1 Certificates
 */
public class X509ExtensionUtil  extends X509Util{

    private static Logger log = LoggerFactory.getLogger(X509ExtensionUtil.class);
    private Configuration config;

    // If we're generating a cert with more content sets than this limit, we will error
    // out, as the certificate is likely too large for the CDN:
    public static final int V1_CONTENT_LIMIT = 185;

    @Inject
    public X509ExtensionUtil(Configuration config) {
        // Output everything in UTC
        this.config = config;
    }

    public Set<X509ExtensionWrapper> consumerExtensions(Consumer consumer) {
        Set<X509ExtensionWrapper> toReturn = new LinkedHashSet<X509ExtensionWrapper>();

        // 1.3.6.1.4.1.2312.9.5.1
        // REDHAT_OID here seems wrong...
        String consumerOid = OIDUtil.REDHAT_OID + "." +
            OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.SYSTEM_NAMESPACE_KEY);
        toReturn.add(new X509ExtensionWrapper(consumerOid + "." +
            OIDUtil.SYSTEM_OIDS.get(OIDUtil.UUID_KEY), false, consumer
            .getUuid()));

        return toReturn;
    }

    public Set<X509ExtensionWrapper> subscriptionExtensions(Entitlement ent) {
        Pool pool = ent.getPool();
        SimpleDateFormat iso8601DateFormat = Util.getUTCDateFormat();
        Set<X509ExtensionWrapper> toReturn = new LinkedHashSet<X509ExtensionWrapper>();
        // Subscription/order info
        // need the sub product name, not id here
        // NOTE: order ~= subscription
        // entitlement == entitlement

        String subscriptionOid = OIDUtil.REDHAT_OID + "." +
            OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ORDER_NAMESPACE_KEY);
        if (pool.getProductId() != null) {
            toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
                OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_NAME_KEY), false, ent
                .getPool().getProductName()));
        }
        toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
            OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_NUMBER_KEY), false, pool
            .getOrderNumber()));
        toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
            OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_SKU_KEY), false,
            pool.getProductId().toString()));
        toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
            OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_QUANTITY_KEY), false, pool
            .getQuantity().toString()));
        String socketLimit = pool.getProduct().getAttributeValue("sockets");
        if (socketLimit != null) {
            toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
                OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_SOCKETLIMIT_KEY), false,
                socketLimit));
        }
        toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
            OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_STARTDATE_KEY), false,
            iso8601DateFormat.format(pool.getStartDate())));
        toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
            OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_ENDDATE_KEY), false,
            iso8601DateFormat.format(pool.getEndDate())));
        // TODO : use keys
        String warningPeriod = pool.getProduct().getAttributeValue("warning_period");
        if (warningPeriod == null) {
            warningPeriod = "0";
        }
        toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
            OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_WARNING_PERIOD), false,
            warningPeriod));
        if (pool.getContractNumber() != null) {
            toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
                OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_CONTRACT_NUMBER_KEY),
                false, pool.getContractNumber()));
        }
        // Add the account number
        if (pool.getAccountNumber() != null) {
            toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
                OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_ACCOUNT_NUMBER_KEY),
                false, pool.getAccountNumber()));
        }
        // Add Smart Management, default to "not managed"
        String mgmt = pool.getProduct().getAttributeValue("management_enabled");
        mgmt = (mgmt == null) ? "0" : mgmt;
        toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
            OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_PROVIDES_MANAGEMENT_KEY),
            false, mgmt));

        String supportLevel = pool.getProduct().getAttributeValue("support_level");
        String supportType = pool.getProduct().getAttributeValue("support_type");
        if (supportLevel != null) {
            toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
                OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_SUPPORT_LEVEL), false,
                supportLevel));
        }
        if (supportType != null) {
            toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
                OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_SUPPORT_TYPE), false,
                supportType));
        }
        String stackingId = pool.getProduct().getAttributeValue("stacking_id");
        if (stackingId != null) {
            toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
                OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_STACKING_ID), false,
                stackingId));
        }
        //code "true" as "1" so it matches other bools in the cert
        String virtOnly = ent.getPool().getAttributeValue("virt_only");
        if (virtOnly != null && virtOnly.equals("true")) {
            toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
                OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_VIRT_ONLY_KEY), false,
                "1"));

        }
        return toReturn;
    }

    public List<X509ExtensionWrapper> entitlementExtensions(
        Entitlement entitlement) {
        String entitlementOid = OIDUtil.REDHAT_OID + "." +
            OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ORDER_NAMESPACE_KEY);
        return Collections.singletonList(new X509ExtensionWrapper(
            entitlementOid + "." +
                OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_QUANTITY_USED), false,
            entitlement.getQuantity().toString()));
    }

    public Set<X509ExtensionWrapper> productExtensions(Product product) {
        Set<X509ExtensionWrapper> toReturn = new LinkedHashSet<X509ExtensionWrapper>();

        String productCertOid = OIDUtil.REDHAT_OID + "." +
            OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.PRODUCT_CERT_NAMESPACE_KEY);

        // XXX need to deal with non hash style IDs
        String productOid = productCertOid + "." + product.getId();

        toReturn.add(new X509ExtensionWrapper(productOid + "." +
            OIDUtil.ORDER_PRODUCT_OIDS.get(OIDUtil.OP_NAME_KEY), false, product
            .getName()));

        String arch = product.hasAttribute("arch") ?
            product.getAttributeValue("arch") : "";
        toReturn.add(new X509ExtensionWrapper(productOid + "." +
            OIDUtil.ORDER_PRODUCT_OIDS.get(OIDUtil.OP_ARCH_KEY), false, arch));

        String version = product.hasAttribute("version") ?
            product.getAttributeValue("version") : "";
        toReturn.add(new X509ExtensionWrapper(productOid + "." +
            OIDUtil.ORDER_PRODUCT_OIDS.get(OIDUtil.OP_VERSION_KEY), false, version));

        String brandType = product.hasAttribute("brand_type") ?
            product.getAttributeValue("brand_type") : "";
        toReturn.add(new X509ExtensionWrapper(productOid + "." +
            OIDUtil.ORDER_PRODUCT_OIDS.get(OIDUtil.OP_BRAND_TYPE_KEY), false, brandType));

        return toReturn;
    }

    public Set<X509ExtensionWrapper> contentExtensions(
        Collection<ProductContent> productContentList, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent, Consumer consumer, Product skuProduct) {

        Set<ProductContent> productContent = new HashSet<ProductContent>(productContentList);
        Set<X509ExtensionWrapper> toReturn = new LinkedHashSet<X509ExtensionWrapper>();

        boolean enableEnvironmentFiltering = config.getBoolean(ConfigProperties.ENV_CONTENT_FILTERING);

        List<String> skuDisabled = skuProduct.getSkuDisabledContentIds();
        List<String> skuEnabled = skuProduct.getSkuEnabledContentIds();

        // For V1 certificates we're going to error out if we exceed a limit which is
        // likely going to generate a certificate too large for the CDN, and return an
        // informative error message to the user.
        for (ProductContent pc : productContent) {
            // augment the content path with the prefix if it is passed in
            String contentPath = this.createFullContentPath(contentPrefix, pc);

            // If we get a content type we don't have content type OID for
            // skip it. see rhbz#997970
            if (!OIDUtil.CF_REPO_TYPE.containsKey(pc.getContent().getType())) {
                log.warn("No content type OID found for " + pc.getContent() +
                    " with content type: " + pc.getContent().getType());
                continue;
            }
            String contentOid = OIDUtil.REDHAT_OID +
                "." +
                OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.CHANNEL_FAMILY_NAMESPACE_KEY) + "." +
                pc.getContent().getId().toString() + "." +
                OIDUtil.CF_REPO_TYPE.get(pc.getContent().getType());
            toReturn.add(new X509ExtensionWrapper(contentOid, false, pc
                .getContent().getType()));
            toReturn.add(new X509ExtensionWrapper(contentOid + "." +
                OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_NAME_KEY), false, pc
                .getContent().getName()));
            toReturn.add(new X509ExtensionWrapper(contentOid + "." +
                OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_LABEL_KEY), false,
                pc.getContent().getLabel()));
            toReturn.add(new X509ExtensionWrapper(contentOid + "." +
                OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_VENDOR_ID_KEY),
                false, pc.getContent().getVendor()));
            toReturn.add(new X509ExtensionWrapper(contentOid + "." +
                OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_DOWNLOAD_URL_KEY),
                false, contentPath));
            toReturn.add(new X509ExtensionWrapper(contentOid + "." +
                OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_GPG_URL_KEY), false,
                pc.getContent().getGpgUrl()));

            Boolean enabled = pc.isEnabled();
            log.debug("default enabled flag = " + enabled);

            // sku level content enable override. if on both lists, active wins.
            if (skuDisabled.contains(pc.getContent().getId())) {
                enabled = false;
            }
            if (skuEnabled.contains(pc.getContent().getId())) {
                enabled = true;
            }

            // Check if we should override the enabled flag due to setting on promoted
            // content:
            if ((consumer.getEnvironment() != null) && enableEnvironmentFiltering) {
                // we know content has been promoted at this point:
                Boolean enabledOverride = promotedContent.get(
                    pc.getContent().getId()).getEnabled();
                if (enabledOverride != null) {
                    log.debug("overriding enabled flag: " + enabledOverride);
                    enabled = enabledOverride;
                }
            }

            toReturn.add(new X509ExtensionWrapper(contentOid + "." +
                OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_ENABLED), false,
                (enabled) ? "1" : "0"));

            // Include metadata expiry if specified on the content:
            if (pc.getContent().getMetadataExpire() != null) {
                toReturn.add(new X509ExtensionWrapper(contentOid + "." +
                    OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_METADATA_EXPIRE),
                    false, pc.getContent().getMetadataExpire().toString()));
            }

            // Include required tags if specified on the content set:
            String requiredTags = pc.getContent().getRequiredTags();
            if ((requiredTags != null) && !requiredTags.equals("")) {
                toReturn.add(new X509ExtensionWrapper(contentOid + "." +
                    OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_REQUIRED_TAGS),
                    false, requiredTags));
            }

        }

        return toReturn;
    }
}
