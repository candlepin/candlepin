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
package org.candlepin.resource.util;

import org.candlepin.dto.api.server.v1.DateRange;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.util.Util;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;



/**
 * The ConsumerEnricher populates the transient fields of a consumer and its related objects.
 */
public class ConsumerEnricher {
    private static final Logger log = LoggerFactory.getLogger(ConsumerEnricher.class);

    private static final String RED_STATUS = "red";
    private static final String YELLOW_STATUS = "yellow";
    private static final String GREEN_STATUS = "green";
    private static final String GRAY_STATUS = "gray";

    private final ComplianceRules complianceRules;
    private final ProductCurator productCurator;

    @Inject
    public ConsumerEnricher(ComplianceRules complianceRules, ProductCurator productCurator) {
        this.complianceRules = Objects.requireNonNull(complianceRules);
        this.productCurator = Objects.requireNonNull(productCurator);
    }

    public void enrich(Consumer consumer) {
        if (consumer == null || CollectionUtils.isEmpty(consumer.getInstalledProducts())) {
            // No consumer or the consumer doesn't have any installed products -- nothing to do here.
            return;
        }

        ComplianceStatus status = this.complianceRules.getStatus(consumer, null, null, false, true,
            true, true);
        Map<String, DateRange> ranges = status.getProductComplianceDateRanges();

        // Compile and prefetch the IDs of the products we're going to be enriching
        String namespace = consumer.getOwner().getKey();

        List<String> productIds = consumer.getInstalledProducts().stream()
            .map(ConsumerInstalledProduct::getProductId)
            .toList();

        Map<String, Product> productMap = this.productCurator.resolveProductIds(namespace, productIds);

        // Perform enrichment of the consumer's installed products
        for (ConsumerInstalledProduct cip : consumer.getInstalledProducts()) {
            String pid = cip.getProductId();
            DateRange range = ranges != null ? ranges.get(pid) : null;

            // Impl note:
            // Due to the nature of our compliance check, installed products which don't exist in
            // CP get marked as non-compliant/red. We could possibly change this to "unknown," but
            // this should be okay for now.

            // The hash lookups are likely faster than the linear search through an array, so we'll
            // do those first.
            if (status.isDisabled()) {
                cip.setStatus(GRAY_STATUS);
            }
            if (status.getCompliantProducts().containsKey(pid)) {
                cip.setStatus(GREEN_STATUS);
            }
            else if (status.getPartiallyCompliantProducts().containsKey(pid)) {
                cip.setStatus(YELLOW_STATUS);
            }
            else if (status.getNonCompliantProducts().contains(pid)) {
                cip.setStatus(RED_STATUS);
            }

            // Set the compliance date range if we have it
            if (range != null) {
                cip.setStartDate(Util.toDate(range.getStartDate()));
                cip.setEndDate(Util.toDate(range.getEndDate()));
            }

            // Fetch missing product information from the actual product
            Product product = productMap.get(pid);
            if (product != null) {
                if (cip.getVersion() == null) {
                    cip.setVersion(product.getAttributeValue(Product.Attributes.VERSION));
                }

                if (cip.getArch() == null) {
                    cip.setArch(product.getAttributeValue(Product.Attributes.ARCHITECTURE));
                }
            }
        }
    }

}
