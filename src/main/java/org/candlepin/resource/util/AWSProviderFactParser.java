/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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

import static org.candlepin.model.CloudIdentifierFacts.AWS_ACCOUNT_ID;
import static org.candlepin.model.CloudIdentifierFacts.AWS_BILLING_PRODUCTS;
import static org.candlepin.model.CloudIdentifierFacts.AWS_INSTANCE_ID;
import static org.candlepin.model.CloudIdentifierFacts.AWS_MARKETPLACE_PRODUCT_CODES;
import static org.candlepin.model.CloudIdentifierFacts.AWS_SHORT_NAME;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class AWSProviderFactParser implements CloudProviderFactParser {

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> getAccountId(Map<String, String> facts) {
        if (facts == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(facts.get(AWS_ACCOUNT_ID.getValue()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<List<String>> getOfferingIds(Map<String, String> facts) {
        if (facts == null) {
            return Optional.empty();
        }

        List<String> offeringIds = Stream.of(AWS_MARKETPLACE_PRODUCT_CODES.getValue(),
                AWS_BILLING_PRODUCTS.getValue())
            .map(facts::get)
            .filter(Objects::nonNull)
            .toList();
        return offeringIds.isEmpty() ? Optional.empty() : Optional.of(offeringIds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> getInstanceId(Map<String, String> facts) {
        if (facts == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(facts.get(AWS_INSTANCE_ID.getValue()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getShortName() {
        return AWS_SHORT_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSupported(Map<String, String> facts) {
        if (facts == null) {
            return false;
        }

        return facts.containsKey(AWS_ACCOUNT_ID.getValue()) ||
            facts.containsKey(AWS_INSTANCE_ID.getValue()) ||
            facts.containsKey(AWS_MARKETPLACE_PRODUCT_CODES.getValue()) ||
            facts.containsKey(AWS_BILLING_PRODUCTS.getValue());
    }
}
