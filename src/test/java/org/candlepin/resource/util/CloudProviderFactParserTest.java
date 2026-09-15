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
import static org.candlepin.model.CloudIdentifierFacts.AZURE_INSTANCE_ID;
import static org.candlepin.model.CloudIdentifierFacts.AZURE_OFFER;
import static org.candlepin.model.CloudIdentifierFacts.AZURE_SHORT_NAME;
import static org.candlepin.model.CloudIdentifierFacts.AZURE_SUBSCRIPTION_ID;
import static org.candlepin.model.CloudIdentifierFacts.GCP_INSTANCE_ID;
import static org.candlepin.model.CloudIdentifierFacts.GCP_LICENSE_CODES;
import static org.candlepin.model.CloudIdentifierFacts.GCP_PROJECT_ID;
import static org.candlepin.model.CloudIdentifierFacts.GCP_SHORT_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class CloudProviderFactParserTest {

    @ParameterizedTest
    @MethodSource("provideTestData")
    void testGetAccountId(CloudProviderFactParser parser, CloudProviderFactParserArgument argument) {
        Optional<String> accountId = parser.getAccountId(argument.facts());

        assertEquals(argument.accountId(), accountId.orElse(null));
    }

    @ParameterizedTest
    @MethodSource("provideTestData")
    void testGetInstanceId(CloudProviderFactParser parser, CloudProviderFactParserArgument argument) {
        Optional<String> instanceId = parser.getInstanceId(argument.facts());

        assertEquals(argument.instanceId(), instanceId.orElse(null));
    }

    @ParameterizedTest
    @MethodSource("provideTestData")
    void testGetOfferingIds(CloudProviderFactParser parser, CloudProviderFactParserArgument argument) {
        Optional<List<String>> offeringIds = parser.getOfferingIds(argument.facts());

        assertEquals(argument.offeringIds(), offeringIds.orElse(null));
    }

    @ParameterizedTest
    @MethodSource("provideShortNameTestData")
    public void testGetShortName(String expectedShortName, CloudProviderFactParser parser) {
        String shortName = parser.getShortName();

        assertEquals(expectedShortName, shortName);
    }

    @ParameterizedTest
    @MethodSource("provideTestData")
    void testIsSupported(CloudProviderFactParser parser, CloudProviderFactParserArgument argument) {
        assertEquals(argument.supported(), parser.isSupported(argument.facts()));
    }

    static Stream<Arguments> provideShortNameTestData() {
        return Stream.of(
            Arguments.arguments(AWS_SHORT_NAME, new AWSProviderFactParser()),
            Arguments.arguments(GCP_SHORT_NAME, new GCPProviderFactParser()),
            Arguments.arguments(AZURE_SHORT_NAME, new AzureProviderFactParser())
        );
    }

    static Stream<Arguments> provideGCPTestData() {
        GCPProviderFactParser gcpParser = new GCPProviderFactParser();
        return Stream.of(// GCP provider tests
            // facts provider as null
            Arguments.arguments(
                gcpParser,
                CloudProviderFactParserArgument.builder()
                    .build()),
            // facts provider as empty map
            Arguments.arguments(
                gcpParser,
                CloudProviderFactParserArgument.builder()
                    .withEmptyFacts()
                    .build()),
            // facts with random keys
            Arguments.arguments(
                gcpParser,
                CloudProviderFactParserArgument.builder()
                    .withFact("randomKey", "randomValue")
                    .asNotSupported()
                    .build()),
            // valid facts with single item offerings
            Arguments.arguments(
                gcpParser,
                CloudProviderFactParserArgument.builder()
                    .withAccountId("account123")
                    .withInstanceId("instance123")
                    .withFact(GCP_PROJECT_ID.getValue(), "account123")
                    .withFact(GCP_INSTANCE_ID.getValue(), "instance123")
                    .withFact(GCP_LICENSE_CODES.getValue(), "offering123")
                    .asSupported()
                    .withOfferingId("offering123")
                    .build()),
            // valid facts with offerings provided as WS delimited string
            Arguments.arguments(
                gcpParser,
                CloudProviderFactParserArgument.builder()
                    .withAccountId("account456")
                    .withInstanceId("instance456")
                    .withFact(GCP_PROJECT_ID.getValue(), "account456")
                    .withFact(GCP_INSTANCE_ID.getValue(), "instance456")
                    .withFact(GCP_LICENSE_CODES.getValue(), "offering123 offering789")
                    .asSupported()
                    .withOfferingId("offering123")
                    .withOfferingId("offering789")
                    .build()));
    }

    static Stream<Arguments> provideAWSTestData() {
        AWSProviderFactParser awsParser = new AWSProviderFactParser();
        return Stream.of(
            // AWS provider tests
            // facts provider as null
            Arguments.arguments(
                awsParser,
                CloudProviderFactParserArgument.builder()
                    .build()),
            // facts provider as empty map
            Arguments.arguments(
                awsParser,
                CloudProviderFactParserArgument.builder()
                    .withEmptyFacts()
                    .build()),
            // facts with random keys
            Arguments.arguments(
                awsParser,
                CloudProviderFactParserArgument.builder()
                    .withFact("randomKey", "randomValue")
                    .asNotSupported()
                    .build()),
            // valid facts with single item offerings
            Arguments.arguments(
                awsParser,
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
                    .build()),
            // valid facts with offerings provided as WS delimited string
            Arguments.arguments(
                awsParser,
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
                    .build()));
    }

    static Stream<Arguments> provideAzureTestData() {
        AzureProviderFactParser azureParser = new AzureProviderFactParser();
        return Stream.of(
            // Azure provider tests
            // facts provider as null
            Arguments.arguments(
                azureParser,
                CloudProviderFactParserArgument.builder()
                    .build()),
            // facts provider as empty map
            Arguments.arguments(
                azureParser,
                CloudProviderFactParserArgument.builder()
                    .withEmptyFacts()
                    .build()),
            // facts with random keys
            Arguments.arguments(
                azureParser,
                CloudProviderFactParserArgument.builder()
                    .withFact("randomKey", "randomValue")
                    .asNotSupported()
                    .build()),
            // valid facts with single item offerings
            Arguments.arguments(
                azureParser,
                CloudProviderFactParserArgument.builder()
                    .withAccountId("accountAzure123")
                    .withInstanceId("instanceAzure123")
                    .withFact(AZURE_SUBSCRIPTION_ID.getValue(), "accountAzure123")
                    .withFact(AZURE_INSTANCE_ID.getValue(), "instanceAzure123")
                    .withFact(AZURE_OFFER.getValue(), "offeringAzure123")
                    .asSupported()
                    .withOfferingId("offeringAzure123")
                    .build()),
            // valid facts with offerings provided as WS delimited string
            Arguments.arguments(
                azureParser,
                CloudProviderFactParserArgument.builder()
                    .withAccountId("accountAzure123")
                    .withInstanceId("instanceAzure123")
                    .withFact(AZURE_SUBSCRIPTION_ID.getValue(), "accountAzure123")
                    .withFact(AZURE_INSTANCE_ID.getValue(), "instanceAzure123")
                    .withFact(AZURE_OFFER.getValue(), "offeringAzure123 offeringAzure456")
                    .asSupported()
                    .withOfferingId("offeringAzure123")
                    .withOfferingId("offeringAzure456")
                    .build()));
    }

    static Stream<Arguments> provideTestData() {
        return Stream.concat(
            Stream.concat(
                provideGCPTestData(),
                provideAWSTestData()),
            provideAzureTestData());
    }
}

