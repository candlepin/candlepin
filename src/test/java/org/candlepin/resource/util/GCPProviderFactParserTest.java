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

import static org.candlepin.model.CloudIdentifierFacts.GCP_INSTANCE_ID;
import static org.candlepin.model.CloudIdentifierFacts.GCP_LICENSE_CODES;
import static org.candlepin.model.CloudIdentifierFacts.GCP_PROJECT_ID;
import static org.candlepin.model.CloudIdentifierFacts.GCP_SHORT_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class GCPProviderFactParserTest {

    private static GCPProviderFactParser parser;

    @BeforeAll
    public static void setUp() {
        parser = new GCPProviderFactParser();
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

        assertEquals(GCP_SHORT_NAME, shortName);
    }

    @ParameterizedTest
    @MethodSource("provideTestData")
    void testIsSupportedIf(CloudProviderFactParserArgument argument) {
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
                        .withFact(GCP_PROJECT_ID.getValue(), "account123")
                        .withFact(GCP_INSTANCE_ID.getValue(), "instance123")
                        .withFact(GCP_LICENSE_CODES.getValue(), "offering123")
                        .asSupported()
                        .withOfferingId("offering123")
                        .build(),
                // valid facts with offerings provided as WS delimited string
                CloudProviderFactParserArgument.builder()
                        .withAccountId("account456")
                        .withInstanceId("instance456")
                        .withFact(GCP_PROJECT_ID.getValue(), "account456")
                        .withFact(GCP_INSTANCE_ID.getValue(), "instance456")
                        .withFact(GCP_LICENSE_CODES.getValue(), "offering123 offering789")
                        .asSupported()
                        .withOfferingId("offering123")
                        .withOfferingId("offering789")
                        .build()
        );
    }
}
