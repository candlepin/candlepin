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

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;

import com.google.common.base.Predicate;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * X509Util
 */
public abstract class X509Util {

    private static Logger log = LoggerFactory.getLogger(X509Util.class);

    public static final Predicate<Product>
    PROD_FILTER_PREDICATE = new Predicate<Product>() {
        @Override
        public boolean apply(Product product) {
            return product != null && StringUtils.isNumeric(product.getId());
        }
    };

    public static final String ARCH_FACT = "uname.machine";
    public static final String PRODUCT_ARCH_ATTR = "arch";

    /**
     * Scan the product content looking for any we should filter out.
     *
     * Will filter out any content which modifies another product if the consumer does
     * not have an entitlement granting them access to that product.
     *
     * Will also filter out any content not promoted to the consumer's environment
     * if environment filtering is enabled.
     *
     * @param prod the product who's content we should filter
     * @param ent the original entitlement
     * @param entCurator
     * @param promotedContent
     * @param filterEnvironment show content also be filtered by environment.
     * @return ProductContent to include in the certificate.
     */
    public Set<ProductContent> filterProductContent(Product prod, Entitlement ent,
        EntitlementCurator entCurator, Map<String, EnvironmentContent> promotedContent,
        boolean filterEnvironment) {
        Set<ProductContent> filtered = new HashSet<ProductContent>();

        for (ProductContent pc : prod.getProductContent()) {
            // Filter any content not promoted to environment.
            if (filterEnvironment &&
                (ent.getConsumer().getEnvironment() != null &&
                    !promotedContent.containsKey(pc.getContent().getId()))) {

                log.debug("Skipping content not promoted to environment: " +
                    pc.getContent().getId());
                continue;
            }

            boolean include = true;
            if (pc.getContent().getModifiedProductIds().size() > 0) {
                include = false;
                Set<String> prodIds = pc.getContent().getModifiedProductIds();
                // If consumer has an entitlement to just one of the modified products,
                // we will include this content set:
                for (String prodId : prodIds) {
                    Set<Entitlement> entsProviding = entCurator.listProviding(
                        ent.getConsumer(), prodId, ent.getStartDate(), ent.getEndDate());
                    if (entsProviding.size() > 0) {
                        include = true;
                        break;
                    }
                }
            }

            if (include) {
                filtered.add(pc);
            }
        }
        return filtered;
    }

    /**
     * Creates a Content url from the prefix and the path
     * @param contentPrefix to prepend to the path
     * @param pc the product content
     * @return the complete content path
     */
    public String createFullContentPath(String contentPrefix, ProductContent pc) {
        String prefix = "/";
        String contentPath = pc.getContent().getContentUrl();
        // Allow for the case wherethe content url is a true url.
        // If that is true, then return it as is.
        if (contentPath.startsWith("http://") ||
            contentPath.startsWith("file://") ||
            contentPath.startsWith("https://") ||
            contentPath.startsWith("ftp://")) {
            return contentPath;
        }

        if (!StringUtils.isEmpty(contentPrefix)) {
            // Ensure there is no double // in the URL. See BZ952735
            // remove them all except one.
            prefix = StringUtils.stripEnd(contentPrefix, "/") + prefix;
        }
        contentPath = StringUtils.stripStart(contentPath, "/");
        return prefix + contentPath;
    }



    /*
     * remove content sets that do not match the consumers arch
     */
    public Set<ProductContent> filterContentByContentArch(
        Set<ProductContent> pcSet, Consumer consumer, Product product) {
        Set<ProductContent> filtered = new HashSet<ProductContent>();


        String consumerArch = consumer.getFact(ARCH_FACT);
        log.debug("consumerArch: " + consumerArch);

        if (consumerArch == null) {
            log.debug("consumer: " + consumer.getId() + " has no " +
                ARCH_FACT + " attribute.");
            log.debug("Not filtering by arch");
            return pcSet;
        }


        for (ProductContent pc : pcSet) {
            boolean canUse = true;
            Set<String> contentArches = Arch.parseArches(pc.getContent().getArches());
            Set<String> productArches =
                Arch.parseArches(product.getAttributeValue(PRODUCT_ARCH_ATTR));

            log.debug("productContent arch list for " +
                pc.getContent().getLabel());

            log.debug("contentArches: " + contentArches);
            log.debug("productArches: " + productArches);

            // Empty or null Content.arches should result in
            // inheriting the arches from the product
            if (contentArches.isEmpty()) {
                log.debug("Content set " + pc.getContent().getLabel() +
                    " does not specify content arches");

                // No content arch, see if there is a Product arch
                // and if so inherit it.
                if (!productArches.isEmpty()) {
                    contentArches.addAll(productArches);
                    log.debug("Using the arches from the product " +
                        product.toString());
                    log.debug("productArches: " + productArches.toString());
                }
                else {
                    // No Product arches either, log it, but do
                    // not filter out this content
                    log.debug("No arch attributes found for content or product");
                }
            }

            for (String contentArch : contentArches) {
                log.debug("Checking consumerArch " + consumerArch +
                    " can use content for " + contentArch);
                log.debug("arch.contentForConsumer" +
                    Arch.contentForConsumer(contentArch, consumerArch));

                if (Arch.contentForConsumer(contentArch, consumerArch)) {
                    log.debug("Can use content " +
                        pc.getContent().getLabel() + " for arch " + contentArch);
                    canUse = true;
                    break;
                }
                else {
                    log.debug("Can not use content " +
                        pc.getContent().getLabel() + " for arch " +
                        contentArch);
                    canUse = false;
                }
            }

            // If we found a workable arch for this content, include it
            // also include content where no arch was found at all (on
            // Content or on Product)
            if (canUse) {
                filtered.add(pc);
                log.debug("Including content " +
                    pc.getContent().getLabel());
            }
            else {
                log.debug("Skipping content " + pc.getContent().getLabel());
            }

        }
        log.debug("Arch approriate content for " +
            consumerArch + " includes: ");
        for (ProductContent apc : filtered) {
            log.debug("\t " + apc.toString());
        }
        return filtered;
    }

}
