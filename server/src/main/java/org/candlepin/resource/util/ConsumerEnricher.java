/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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

import org.candlepin.dto.api.v1.DateRange;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * The ConsumerEnricher populates the transient fields of a consumer and its related objects.
 */
public class ConsumerEnricher {
    private static Logger log = LoggerFactory.getLogger(ConsumerEnricher.class);

    private static final String RED_STATUS = "red";
    private static final String YELLOW_STATUS = "yellow";
    private static final String GREEN_STATUS = "green";
    private static final String GRAY_STATUS = "gray";

    private ComplianceRules complianceRules;
    private OwnerProductCurator ownerProductCurator;

    @Inject
    public ConsumerEnricher(ComplianceRules complianceRules, OwnerProductCurator ownerProductCurator) {
        this.complianceRules = complianceRules;
        this.ownerProductCurator = ownerProductCurator;
    }

    public void enrich(Consumer consumer) {
        if (consumer == null || CollectionUtils.isEmpty(consumer.getInstalledProducts())) {
            // No consumer or the consumer doesn't have any installed products -- nothing to do here.
            return;
        }

        ComplianceStatus status = this.complianceRules.getStatus(consumer, null, null, false, true,
            true, true);
        Map<String, DateRange> ranges = status.getProductComplianceDateRanges();

        // Compile the product IDs for the products we're going to be enriching
        Set<String> productIds = new HashSet<>();
        Map<String, Product> productMap = new HashMap<>();

        for (ConsumerInstalledProduct cip : consumer.getInstalledProducts()) {
            productIds.add(cip.getProductId());
        }

        for (Product product : this.ownerProductCurator.getProductsByIds(consumer.getOwnerId(), productIds)) {
            productMap.put(product.getId(), product);
        }

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
