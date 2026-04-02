/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.pki.impl.jca;

import org.candlepin.pki.OidUtil;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import javax.inject.Singleton;

// TODO: Replace the logic here with something more dynamic so we aren't tied down to only these algorithms.

/**
 * A simple, native Java implementation of the OidUtil. This implementation hard-codes its mappings from known
 * algorithm names to OIDs rather than determining OIDs from any particular security provider or through JCA
 * interfaces.
 */
@Singleton
public class JcaOidUtil implements OidUtil {
    /**
     * A mapping of supported key algorithm names to their respective OIDs. Add additional names here as
     * necessary. Entries should be added as uppercase algorithm names as the map key, with well-formed OID
     * strings as the value.
     */
    private static final Map<String, String> KEY_ALGO_NAME_TO_OID = Map.ofEntries(
        Map.entry("ML-DSA-44",              "2.16.840.1.101.3.4.3.17"),
        Map.entry("ML-DSA-65",              "2.16.840.1.101.3.4.3.18"),
        Map.entry("ML-DSA-87",              "2.16.840.1.101.3.4.3.19"),
        Map.entry("RSA",                    "1.2.840.113549.1.1.1")
    );

    /**
     * A mapping of supported signature algorithm names to their respective OIDs. Add additional names here as
     * necessary. Entries should be added as uppercase algorithm names as the map key, with well-formed OID
     * strings as the value.
     */
    private static final Map<String, String> SIG_ALGO_NAME_TO_OID = Map.ofEntries(
        Map.entry("ML-DSA-44",              "2.16.840.1.101.3.4.3.17"),
        Map.entry("ML-DSA-65",              "2.16.840.1.101.3.4.3.18"),
        Map.entry("ML-DSA-87",              "2.16.840.1.101.3.4.3.19"),
        Map.entry("SHA224WITHRSA",          "1.2.840.113549.1.1.14"),
        Map.entry("SHA256WITHRSA",          "1.2.840.113549.1.1.11"),
        Map.entry("SHA384WITHRSA",          "1.2.840.113549.1.1.12"),
        Map.entry("SHA512WITHRSA",          "1.2.840.113549.1.1.13")
    );

    @Override
    public Optional<String> getKeyAlgorithmOid(String algorithmName) {
        if (algorithmName == null || algorithmName.isBlank()) {
            throw new IllegalArgumentException("algorithmName is null or empty");
        }

        String key = algorithmName.toUpperCase();

        // Check if the mapping exists
        Optional<String> output = Optional.ofNullable(KEY_ALGO_NAME_TO_OID.get(key));
        if (output.isPresent()) {
            return output;
        }

        // If not, check if the algorithm name *is* an OID, and we've mapped it
        return Optional.of(key)
            .filter(KEY_ALGO_NAME_TO_OID::containsValue);
    }

    @Override
    public Optional<String> getKeyAlgorithmName(String algorithmOid) {
        if (algorithmOid == null || algorithmOid.isBlank()) {
            throw new IllegalArgumentException("algorithmOid is null or empty");
        }

        return KEY_ALGO_NAME_TO_OID.entrySet()
            .stream()
            .filter(entry -> algorithmOid.equals(entry.getValue()))
            .findAny()
            .map(Map.Entry::getKey);
    }

    @Override
    public Optional<String> getSignatureAlgorithmOid(String algorithmName) {
        if (algorithmName == null || algorithmName.isBlank()) {
            throw new IllegalArgumentException("algorithmName is null or empty");
        }

        String key = algorithmName.toUpperCase();

        // Check if the mapping exists
        Optional<String> output = Optional.ofNullable(SIG_ALGO_NAME_TO_OID.get(key));
        if (output.isPresent()) {
            return output;
        }

        // If not, check if the algorithm name *is* an OID, and we've mapped it
        return Optional.of(key)
            .filter(SIG_ALGO_NAME_TO_OID::containsValue);
    }

    @Override
    public Optional<String> getSignatureAlgorithmName(String algorithmOid) {
        if (algorithmOid == null || algorithmOid.isBlank()) {
            throw new IllegalArgumentException("algorithmOid is null or empty");
        }

        return SIG_ALGO_NAME_TO_OID.entrySet()
            .stream()
            .filter(entry -> algorithmOid.equals(entry.getValue()))
            .findAny()
            .map(Map.Entry::getKey);
    }

    @Override
    public boolean isAlgorithmSupported(Collection<String> supportedAlgorithmOids, String algorithmOid) {
        if (supportedAlgorithmOids == null) {
            throw new IllegalArgumentException("supportedAlgorithmOids is null");
        }

        if (algorithmOid == null || algorithmOid.isBlank()) {
            throw new IllegalArgumentException("algorithmOid is null or empty");
        }

        // The initial implementation here only needs to check for a literal contains. If we want to add
        // support for algorithm families or OID hierachies, we can do so here without having to change a
        // bunch of rogue .contains checks.

        return supportedAlgorithmOids.contains(algorithmOid);
    }

}
