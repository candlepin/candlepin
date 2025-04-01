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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class AWSProviderFactParserTest {

    private static AWSProviderFactParser parser;
    private static final Map<String, String> PRESENT_FACTS = new HashMap<>();

    @BeforeAll
    public static void setUp() {
        parser = new AWSProviderFactParser();

        PRESENT_FACTS.put(AWS_ACCOUNT_ID.getValue(), "account123");
        PRESENT_FACTS.put(AWS_INSTANCE_ID.getValue(), "instance123");
        PRESENT_FACTS.put(AWS_MARKETPLACE_PRODUCT_CODES.getValue(), "offering123");
        PRESENT_FACTS.put(AWS_BILLING_PRODUCTS.getValue(), "offering456");
    }

    @Test
    public void testGetAccountIdIfFactPresent() {
        Optional<String> accountId = parser.getAccountId(PRESENT_FACTS);

        assertTrue(accountId.isPresent());
        assertEquals("account123", accountId.get());
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("provideTestData")
    void testGetAccountIdEmptyOptional(Map<String, String> facts) {
        Optional<String> accountId = parser.getAccountId(facts);

        assertTrue(accountId.isEmpty());
    }

    @Test
    public void testGetInstanceIdIfFactPresent() {
        Optional<String> instanceId = parser.getInstanceId(PRESENT_FACTS);

        assertTrue(instanceId.isPresent());
        assertEquals("instance123", instanceId.get());
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("provideTestData")
    void testGetInstanceIdEmptyOptional(Map<String, String> facts) {
        Optional<String> instanceId = parser.getInstanceId(facts);

        assertTrue(instanceId.isEmpty());
    }

    @Test
    public void testGetLicenseCodesIfFactPresent() {
        Optional<List<String>> licenseCodes = parser.getOfferingIds(PRESENT_FACTS);

        assertTrue(licenseCodes.isPresent());
        assertEquals(List.of("offering123", "offering456"), licenseCodes.get());
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("provideTestData")
    void testGetOfferingIdsEmptyOptional(Map<String, String> facts) {
        Optional<List<String>> offeringIds = parser.getOfferingIds(facts);

        assertTrue(offeringIds.isEmpty());
    }

    @Test
    public void testGetShortName() {
        String shortName = parser.getShortName();

        assertEquals(AWS_SHORT_NAME, shortName);
    }

    @Test
    public void testIsSupportedIfFactsPresent() {
        assertTrue(parser.isSupported(PRESENT_FACTS));
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("provideTestData")
    void testIsSupportedIfFactsNotPresent(Map<String, String> facts) {
        assertFalse(parser.isSupported(facts));
    }

    static Stream<Arguments> provideTestData() {
        return Stream.of(
            Arguments.of(Collections.emptyMap()),
            Arguments.of(Collections.singletonMap("randomKey", "randomValue"))
        );
    }
}

