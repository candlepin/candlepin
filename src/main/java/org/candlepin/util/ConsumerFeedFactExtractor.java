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
    );

    // List of allowed patterns (regular expressions)
    private static final List<Pattern> ALLOWED_PATTERNS = List.of(
        Pattern.compile("^net\\.interface\\..+?\\.mac_address$"),
        Pattern.compile("^net\\.interface\\..+?\\.ipv4_address$"),
        Pattern.compile("^net\\.interface\\..+?\\.ipv6_address.global$"),
        Pattern.compile("^net\\.interface\\..+?\\.ipv6_address.link$"),
        Pattern.compile("^net\\.interface\\..+?\\.ipv4_address_list$"),
        Pattern.compile("^net\\.interface\\..+?\\.ipv6_address.global_list$"),
        Pattern.compile("^net\\.interface\\..+?\\.pv6_address.link_list$"),
        Pattern.compile("^ocm\\."),
        Pattern.compile("^openshift\\."),
        Pattern.compile("^azure_"),
        Pattern.compile("^aws_"),
        Pattern.compile("^gcp_")
    );

    /**
     * Extracts only the relevant facts from the given map, based on allowed keys and patterns.
     *
     * @param allFacts a map of all available facts (key -> value)
     * @return a filtered map containing only the allowed facts
     */
    public static Map<String, String> extractRelevantFacts(Map<String, String> allFacts) {
        Map<String, String> result = new HashMap<>();
        if (allFacts == null) {
            return result;
        }
        for (Map.Entry<String, String> entry : allFacts.entrySet()) {
            String key = entry.getKey();

            // Check for an exact allowed key
            if (ALLOWED_FACTS.contains(key)) {
                result.put(key, entry.getValue());
                continue;
            }

            // Check all allowed regular expression patterns
            for (Pattern pattern : ALLOWED_PATTERNS) {
                if (pattern.matcher(key).find()) {
                    result.put(key, entry.getValue());
                    break;
                }
            }
        }
        return result;
    }
}
