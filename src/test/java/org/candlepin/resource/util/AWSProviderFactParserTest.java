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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.candlepin.model.CloudIdentifierFacts.AWS_ACCOUNT_ID;
import static org.candlepin.model.CloudIdentifierFacts.AWS_BILLING_PRODUCTS;
import static org.candlepin.model.CloudIdentifierFacts.AWS_INSTANCE_ID;
import static org.candlepin.model.CloudIdentifierFacts.AWS_MARKETPLACE_PRODUCT_CODES;
import static org.candlepin.model.CloudIdentifierFacts.AWS_SHORT_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AWSProviderFactParserTest {

    private static AWSProviderFactParser parser;

    @BeforeAll
    public static void setUp() {
        parser = new AWSProviderFactParser();
    }

    @ParameterizedTest
    @MethodSource("provideTestData")
    void testGetAccountId(CloudProviderFactParserArgument argument) {
        Optional<String> accountId = parser.getAccountId(argument.facts());

        assertEquals(argument.accountId(), accountId.orElse(null));
    }

    @ParameterizedTest
    @MethodSource("provideTestData")
    void testGetInstanceId(CloudProviderFactParserArgument argument) {
        Optional<String> instanceId = parser.getInstanceId(argument.facts());

        assertEquals(argument.instanceId(), instanceId.orElse(null));
    }

    @ParameterizedTest
    @MethodSource("provideTestData")
    void testGetOfferingIds(CloudProviderFactParserArgument argument) {
        Optional<List<String>> offeringIds = parser.getOfferingIds(argument.facts());

        assertEquals(argument.offeringIds(), offeringIds.orElse(null));
    }

    @Test
    public void testGetShortName() {
        String shortName = parser.getShortName();

        assertEquals(AWS_SHORT_NAME, shortName);
    }

    @ParameterizedTest
    @MethodSource("provideTestData")
    void testIsSupported(CloudProviderFactParserArgument argument) {
        assertEquals(argument.supported(), parser.isSupported(argument.facts()));
    }

    static Stream<CloudProviderFactParserArgument> provideTestData() {
        return Stream.of(
                // facts provider as null
                CloudProviderFactParserArgument.builder().build(),
                // facts provider as empty map
                CloudProviderFactParserArgument.builder()
                        .withEmptyFacts()
                        .build(),
                // facts with random keys
                CloudProviderFactParserArgument.builder()
                        .withFact("randomKey", "randomValue")
                        .asNotSupported()
                        .build(),
                // valid facts with single item offerings
                CloudProviderFactParserArgument.builder()
                        .withAccountId("account123")
                        .withInstanceId("instance123")
                        .withFact(AWS_ACCOUNT_ID.getValue(), "account123")
                        .withFact(AWS_INSTANCE_ID.getValue(), "instance123")
                        .withFact(AWS_MARKETPLACE_PRODUCT_CODES.getValue(), "offering123")
                        .withFact(AWS_BILLING_PRODUCTS.getValue(), "offering456")
                        .asSupported()
                        .withOfferingId("offering123")
                        .withOfferingId("offering456")
                        .build(),
                // valid facts with offerings provided as WS delimited string
                CloudProviderFactParserArgument.builder()
                        .withAccountId("account456")
                        .withInstanceId("instance456")
                        .withFact(AWS_ACCOUNT_ID.getValue(), "account456")
                        .withFact(AWS_INSTANCE_ID.getValue(), "instance456")
                        .withFact(AWS_MARKETPLACE_PRODUCT_CODES.getValue(), "offering123 offering789")
                        .withFact(AWS_BILLING_PRODUCTS.getValue(), " offering456 offering0")
                        .asSupported()
                        .withOfferingId("offering123")
                        .withOfferingId("offering789")
                        .withOfferingId("offering456")
                        .withOfferingId("offering0")
                        .build()
        );
    }
}

