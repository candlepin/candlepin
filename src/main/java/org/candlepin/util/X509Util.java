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

import org.candlepin.controller.util.PromotedContent;
import org.candlepin.model.Consumer;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;

import com.google.common.base.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;



public abstract class X509Util {

    private static Logger log = LoggerFactory.getLogger(X509Util.class);

    public static final Predicate<Product> PROD_FILTER_PREDICATE = new Predicate<>() {
        /**
         * Test if a product should be used for generating an entitlement certificate.
         * This test is currently limited to whether or not the product id is numeric.
         *
         * This test has to include whether or not the product id is numeric
         * because V1 certificates include the product ID in the OID
         * of the certificate extension.
         *
         * If the numeric limitation can be lifted then this test may be able to be altered
         * to only test if a product has content sets associated. That would effectively
         * trim out marketing products & all other products that do not have any associated
         * content sets.
         *
         * @param product The product to test.
         * @return True if the supplied product contains content that should be included
         *         in an entitlement certificate.
         */
        @Override
        public boolean apply(Product product) {
            return product != null && StringUtils.isNumeric(product.getId());
        }
    };

    public static final String ARCH_FACT = "uname.machine";

    /**
     * The 'supported_architectures' fact is used to provide the information which
     * architectures (like i386, amd64) a host supports. This is used e.g. on
     * Debian / Ubuntu hosts to support multiple architectures. A common scenario is to
     * use amd64 and i386 on Debian / Ubuntu. Therefore, the host needs to be able to install
     * packages from amd64 and i386 repositories.
     */
    public static final String SUPPORTED_ARCH_FACT = "supported_architectures";

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
     * @param promotedContent
     * @param filterEnvironment show content also be filtered by environment.
     * @return ProductContent to include in the certificate.
     */
    public Set<ProductContent> filterProductContent(Product prod, Consumer consumer,
        PromotedContent promotedContent, boolean filterEnvironment,
        Set<String> entitledProductIds, boolean contentAccessMode) {
        Set<ProductContent> filtered = new HashSet<>();

        for (ProductContent pc : prod.getProductContent()) {
            // Filter any content not promoted to environment.
            if (filterEnvironment && !consumer.getEnvironmentIds().isEmpty() &&
                !promotedContent.contains(pc)) {

                log.debug("Skipping content not promoted to environment: {}", pc.getContent());
                continue;
            }

            boolean include = true;
            if (!contentAccessMode && pc.getContent().getModifiedProductIds().size() > 0) {
                include = false;
                Collection<String> prodIds = pc.getContent().getModifiedProductIds();
                // If consumer has an entitlement to just one of the modified products,
                // we will include this content set:
                for (String prodId : prodIds) {
                    if (entitledProductIds.contains(prodId)) {
                        include = true;
                        break;
                    }
                }
            }

            if (include) {
                filtered.add(pc);
            }
            else {
                log.debug("No entitlements found for modified products.");
                log.debug("Skipping content set: " + pc.getContent());
            }
        }
        return filtered;
    }

    /**
     * Filter out the content sets that do not match the consumers arch
     *
     * @param pcSet
     *  a product contents to be filtered
     * @param consumer
     *  a consumer for whom to filter content
     * @param product
     *  a product for which to filter content
     * @return
     *  a filtered collection of product contents
     */
    public Set<ProductContent> filterContentByContentArch(
        Set<ProductContent> pcSet, Consumer consumer, Product product) {
        Set<ProductContent> filtered = new HashSet<>();
        Set<String> consumerArches = archesOf(consumer);

        if (consumerArches.isEmpty()) {
            log.debug("consumer: {} has no {} / {} attribute",
                consumer.getId(), ARCH_FACT, SUPPORTED_ARCH_FACT);
            log.debug("Not filtering by arch");
            return pcSet;
        }

        for (ProductContent pc : pcSet) {
            Set<String> contentArches = archesOf(product, pc);
            boolean canUse = hasCompatibleArchitecture(consumerArches, contentArches);

            // If we found a workable arch for this content, include it
            // also include content where no arch was found at all (on
            // Content or on Product)
            if (canUse) {
                filtered.add(pc);
            }

        }
        return filtered;
    }

    private Set<String> archesOf(Consumer consumer) {
        Set<String> consumerArches = new HashSet<>();

        String supportedArches = consumer.getFact(SUPPORTED_ARCH_FACT);

        if (supportedArches != null) {
            consumerArches = Arch.parseArches(supportedArches);
        }

        String archFact = consumer.getFact(ARCH_FACT);
        if (archFact != null) {
            consumerArches.add(archFact);
        }
        return consumerArches;
    }

    private Set<String> archesOf(Product product, ProductContent pc) {
        Set<String> contentArches = Arch.parseArches(pc.getContent().getArches());
        // Empty or null Content.arches should result in
        // inheriting the arches from the product
        if (contentArches.isEmpty()) {
            Set<String> productArches = Arch.parseArches(product
                .getAttributeValue(Product.Attributes.ARCHITECTURE));
            if (!productArches.isEmpty()) {
                contentArches.addAll(productArches);
            }
            else {
                // No Product arches either, log it, but do
                // not filter out this content
                log.debug("No arch attributes found for content or product");
            }
        }
        return contentArches;
    }

    /**
     * Returns true/false depending on whether or not there is a match/mismatch between the consumer's
     * supported architectures and the content's supported architectures.
     *
     * @param consumerArches The architectures that the consumer supports
     * @param contentArches The architectures that the content supports
     * @return False if none of the content's architectures match to any of the consumer architectures,
     * and True if at least one of the content's architectures match to at least one of the consumer
     * architectures, or if the content architectures are empty.
     */
    private boolean hasCompatibleArchitecture(Set<String> consumerArches, Set<String> contentArches) {
        if (contentArches.isEmpty()) {
            return true;
        }

        for (String contentArch : contentArches) {
            for (String consumerArch : consumerArches) {
                if (Arch.contentForConsumer(contentArch, consumerArch)) {
                    return true;
                }
            }
        }
        return false;
    }

}
