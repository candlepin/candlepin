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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.candlepin.model.Arch;
import org.candlepin.model.ArchCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;

import com.google.common.base.Predicate;


/**
 * X509Util
 */
public abstract class X509Util {

    private static Logger log = Logger.getLogger(X509Util.class);

    public static final Predicate<Product>
    PROD_FILTER_PREDICATE = new Predicate<Product>() {
        @Override
        public boolean apply(Product product) {
            return product != null && StringUtils.isNumeric(product.getId());
        }
    };

    public static final String ARCH_FACT = "uname.machine";

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
            if (filterEnvironment) {
                if (ent.getConsumer().getEnvironment() != null &&
                    !promotedContent.containsKey(pc.getContent().getId())) {
                    log.debug("Skipping content not promoted to environment: " +
                        pc.getContent().getId());
                    continue;
                }
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

    public Set<org.candlepin.model.Arch> getProductArches(Product product,
        ArchCurator archCurator) {
        Set<String> arches = product.getParsedArches();
        Set<org.candlepin.model.Arch> archSet = new
            HashSet<org.candlepin.model.Arch>();
        for (String archLabel : arches) {
            Arch productArch = archCurator.lookupByLabel(archLabel);
            if (productArch != null) {
                archSet.add(productArch);
            }
            else {
                log.debug("The arch label " + archLabel +
                    " not found by the archCurator");
            }
        }
        return archSet;
    }

    /*
     * remove content sets that do not match the consumers arch
     */
    public Set<ProductContent> filterContentByContentArch(
        Set<ProductContent> pcSet, Consumer consumer, Set<Arch> productArchSet,
        ArchCurator archCurator) {
        Set<ProductContent> filtered = new HashSet<ProductContent>();

        /* FIXME: make this a feature flag in the config */
        boolean enabledContentArchFiltering = true;
        if (!enabledContentArchFiltering) {
            return pcSet;
        }

        String consumerArchLabel = consumer.getFact(ARCH_FACT);
        log.debug("_ca_ consumerArchLabel: " + consumerArchLabel);

        if (consumerArchLabel == null) {
            log.debug("_ca_ consumer: " + consumer.getId() + " has no " +
                ARCH_FACT + " attribute.");
            log.debug("_ca_ not filtering by arch");
            return pcSet;
        }

        Arch consumerArch = archCurator.lookupByLabel(consumerArchLabel);
        if (consumerArch == null) {
            log.debug("_ca_ consumer arch: " + consumerArchLabel +
                " is not a known arch");
            log.debug("_ca_ not filtering by arch");
            return pcSet;
        }

        log.debug("_ca_ consumerArch: " + consumerArch);
        log.debug("_ca_ productArchSet " + productArchSet.toString());

        for (ProductContent pc : pcSet) {
            boolean canUse = false;
            Set<Arch> arches = pc.getContent().getArches();

            log.debug("_ca_ product_content arch list for " +
                pc.getContent().getLabel());
            for (Arch logArch : pc.getContent().getArches()) {
                log.debug("_ca_ \t arch: " + logArch.toString());
            }

            if (arches.isEmpty()) {
                Product product = pc.getProduct();
                // no arches specified
                log.debug("_ca_ content set " + pc.getContent().getLabel() +
                    " does not specific content arches");

                // so use the arches from the product
                arches.addAll(productArchSet);
                log.debug("_ca_so using the arches from the product " +
                    product.toString());
                for (Arch productArch : arches) {
                    log.debug("_ca_ \t arch from product: " +
                        productArch.toString());
                }
            }

            for (Arch contentArch : arches) {
                log.debug("_ca_ Checking consumerArch " +
                    consumerArch.getLabel() + " can use content for " +
                    contentArch.getLabel());
                log.debug("_ca_ consumerArch.usesContentFor(contentArch) " +
                    consumerArch.usesContentFor(contentArch));
                if (consumerArch.usesContentFor(contentArch)) {
                    log.debug("_ca_ CAN use content " +
                        pc.getContent().getLabel() + " for arch " +
                        contentArch.getLabel());
                    // filtered.add(pc);
                    canUse = true;
                }
                else {
                    log.debug("_ca_ CAN NOT use content " +
                        pc.getContent().getLabel() + " for arch " +
                        contentArch.getLabel());
                }
            }

            // if we found a workable arch for this content, include it
            if (canUse) {
                filtered.add(pc);
                log.debug("_ca_ Including content " +
                    pc.getContent().getLabel());
            }
            else {
                log.debug("_ca_ Skipping content " + pc.getContent().getLabel());
            }

        }
        log.debug("_ca_ arch approriate content for " +
            consumerArch.getLabel() + " includes: ");
        for (ProductContent apc : filtered) {
            log.debug("_ca_ \t " + apc.toString());
        }
        return filtered;
    }

}
