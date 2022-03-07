/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.policy.js.compliance.hash;

import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.model.Consumer;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Specifies consumer facts considered for compliance calculation.
 */
public enum ComplianceFacts {

    CPU_CORES_PERSOCKET("cpu.core(s)_per_socket"),
    CPU_SOCKETS("cpu.cpu_socket(s)"),
    BAND_STORAGE_USAGE("band.storage.usage"),
    MEMORY_MEMTOTAL("memory.memtotal"),
    UNAME_MACHINE("uname.machine"),
    VIRT_IS_GUEST("virt.is_guest");

    private static final Set<String> FACT_KEYS = Arrays.stream(values())
        .map(ComplianceFacts::getFactKey)
        .collect(Collectors.toSet());

    private final String factKey;

    ComplianceFacts(String factKey) {
        this.factKey = factKey;
    }

    public String getFactKey() {
        return factKey;
    }

    /**
     * Takes facts from a given consumer and filters out facts relevant for
     * compliance calculation.
     *
     * @param target consumer for whom to filter facts
     * @return facts relevant for compliance
     */
    public static Collection<Map.Entry<String, String>> of(Consumer target) {
        if (target == null) {
            return Collections.emptySet();
        }
        return filter(target.getFacts());
    }
    /**
     * Takes facts from a given consumer and filters out facts relevant for
     * compliance calculation.
     *
     * @param target consumer for whom to filter facts
     * @return facts relevant for compliance
     */
    public static Collection<Map.Entry<String, String>> of(ConsumerDTO target) {
        if (target == null) {
            return Collections.emptySet();
        }
        return filter(target.getFacts());
    }

    /**
     * Method filters out facts relevant for compliance calculation from the given facts.
     *
     * @param facts facts from which to filter compliance relevant ones
     * @return facts relevant for compliance
     */
    private static Collection<Map.Entry<String, String>> filter(Map<String, String> facts) {
        if (facts == null || facts.isEmpty()) {
            return Collections.emptySet();
        }
        return facts.entrySet().stream()
            .filter(fact -> FACT_KEYS.contains(fact.getKey()))
            .collect(Collectors.toSet());
    }

}
