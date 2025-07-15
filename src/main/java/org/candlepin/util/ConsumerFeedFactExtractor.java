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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class ConsumerFeedFactExtractor {

    private ConsumerFeedFactExtractor() {
        // Intentionally left empty
    }

    // List of allowed fact keys (exact matches)
    private static final Set<String> ALLOWED_FACTS = Set.of(
        "band.storage.usage",
        "bios.version",
        "conversions.activity",
        "cpu.core(s)_per_socket",
        "cpu.cpu(s)",
        "cpu.cpu_socket(s)",
        "cpu.thread(s)_per_core",
        "distribution.name",
        "distribution.version",
        "dmi.bios.vendor",
        "dmi.bios.version",
        "dmi.chassis.asset_tag",
        "dmi.system.manufacturer",
        "dmi.system.product_name",
        "dmi.system.uuid",
        "insights_id",
        "memory.memtotal",
        "network.fqdn",
        "network.hostname",
        "network.ipv4_address",
        "network.ipv6_address",
        "uname.machine",
        "uname.nodename",
        "virt.is_guest"
    );

    // Cloud fact prefixes
    private static final List<String> CLOUD_FACT_PREFIXES = List.of(
        "azure_",
        "aws_",
        "ocm.",
        "openshift.",
        "gcp_"
    );

    // Combined regex for network interface facts
    private static final Pattern NET_INTERFACE_PATTERN =
        Pattern.compile(
            "^net\\.interface\\..+?\\.(?:mac_address|ipv[46]_address(?:\\.global|\\.link)?(?:_list)?)$");

    /**
     * Extracts only the relevant facts from the given map, based on allowed keys and patterns.
     *
     * @param allFacts a map of all available facts (key -> value)
     * @return a filtered map containing only the allowed facts
     */
    public static Map<String, String> extractRelevantFacts(Map<String, String> allFacts) {
        Map<String, String> result = new HashMap<>();
        if (allFacts == null || allFacts.isEmpty()) {
            return result;
        }

        for (Map.Entry<String, String> entry : allFacts.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (ALLOWED_FACTS.contains(key) || CLOUD_FACT_PREFIXES.stream().anyMatch(key::startsWith) ||
                NET_INTERFACE_PATTERN.matcher(key).matches()) {
                result.put(key, value);
            }
        }
        return result;
    }
}
