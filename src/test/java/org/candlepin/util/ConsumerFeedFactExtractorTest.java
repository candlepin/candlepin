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

package org.candlepin.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

public class ConsumerFeedFactExtractorTest {

    @Nested
    @DisplayName("Direct allowed keys")
    class AllowedKeyTests {
        @ParameterizedTest
        @ValueSource(strings = {
            "cpu.cpu(s)",
            "cpu.cpu_socket(s)",
            "cpu.thread(s)_per_core",
            "distribution.version",
            "network.fqdn",
            "bios.version",
            "dmi.bios.vendor",
            "network.hostname",
            "dmi.system.manufacturer",
            "dmi.system.product_name",
            "insights_id",
            "conversions.activity",
            "dmi.system.uuid",
            "uname.nodename",
            "distribution.name",
            "memory.memtotal",
            "cpu.core(s)_per_socket",
            "uname.machine",
            "network.ipv6_address",
            "dmi.chassis.asset_tag",
            "dmi.bios.version",
            "virt.is_guest",
            "band.storage.usage",
            "network.ipv4_address"
        })
        public void extractsDirectAllowedKey(String key) {
            Map<String, String> facts = Map.of(key, "xval");

            Map<String, String> result = ConsumerFeedFactExtractor.extractRelevantFacts(facts);

            assertEquals(1, result.size());
            assertEquals("xval", result.get(key));
        }
    }

    @Nested
    @DisplayName("Allowed regex keys")
    class AllowedRegexKeyTests {
        @ParameterizedTest
        @ValueSource(strings = {
            "net.interface.eth0.mac_address",
            "net.interface.eth9.ipv4_address",
            "net.interface.lo.ipv6_address.global",
            "net.interface.test.ipv6_address.link",
            "net.interface.eno1.ipv4_address_list",
            "net.interface.wlan0.ipv6_address.global_list",
            "net.interface.wlan0.ipv6_address.link_list",
            "ocm.units",
            "openshift.node",
            "azure_foo",
            "aws_123",
            "gcp_test"
        })
        public void extractsAllowedRegexKey(String key) {
            Map<String, String> facts = Map.of(key, "regexval");

            Map<String, String> result = ConsumerFeedFactExtractor.extractRelevantFacts(facts);

            assertEquals(1, result.size());
            assertEquals("regexval", result.get(key));
        }
    }

    @Test
    public void ignoresNonMatchingKey() {
        Map<String, String> facts = Map.of("unrelated.key", "nope");

        Map<String, String> result = ConsumerFeedFactExtractor.extractRelevantFacts(facts);

        assertTrue(result.isEmpty());
    }

    @Test
    public void doesNotDuplicateKeyWhenMatchesBothDirectAndRegex() {
        String key = "network.ipv4_address"; // direct match, but also matches regex
        Map<String, String> facts = Map.of(key, "foobar");

        Map<String, String> result = ConsumerFeedFactExtractor.extractRelevantFacts(facts);

        // It should appear exactly once, with the correct value
        assertEquals(1, result.size());
        assertEquals("foobar", result.get(key));
    }

    @Test
    public void preservesNullAndEmptyValues() {
        Map<String, String> facts = new HashMap<>();
        facts.put("distribution.version", null);
        facts.put("cpu.cpu_socket(s)", "");

        Map<String, String> result = ConsumerFeedFactExtractor.extractRelevantFacts(facts);

        assertEquals(2, result.size());
        assertNull(result.get("distribution.version"));
        assertEquals("", result.get("cpu.cpu_socket(s)"));
    }

    @Test
    public void handlesEmptyInput() {
        Map<String, String> facts = new HashMap<>();

        Map<String, String> result = ConsumerFeedFactExtractor.extractRelevantFacts(facts);

        assertTrue(result.isEmpty());
    }

    @Test
    public void mixedInput() {
        Map<String, String> facts = new HashMap<>();
        facts.put("cpu.cpu_socket(s)", "2"); // direct
        facts.put("net.interface.eth0.mac_address", "AB:CD:EF:00:11:22"); // regex
        facts.put("unrelated", "ignore"); // not allowed

        Map<String, String> result = ConsumerFeedFactExtractor.extractRelevantFacts(facts);

        assertEquals(2, result.size());
        assertEquals("2", result.get("cpu.cpu_socket(s)"));
        assertEquals("AB:CD:EF:00:11:22", result.get("net.interface.eth0.mac_address"));
        assertFalse(result.containsKey("unrelated"));
    }
}
