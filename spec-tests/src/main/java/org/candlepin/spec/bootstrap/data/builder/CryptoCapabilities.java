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
package org.candlepin.spec.bootstrap.data.builder;

import org.candlepin.dto.api.client.v1.CryptographicCapabilitiesDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;


/**
 * Utility class for building or providing CryptographicCapabilitiesDTO instances.
 */
public final class CryptoCapabilities {

    private static final List<String> RSA_KEY_ALGORITHM_OIDS = List.of(
        "1.2.840.113549.1.1.1",
        "2.5.8.1.1");

    private static final List<String> RSA_SIGNATURE_ALGORITHM_OIDS = List.of(
        "1.2.840.113549.1.1.11",
        "1.2.840.113549.1.1.12",
        "1.2.840.113549.1.1.13",
        "1.2.840.113549.1.1.14",
        "2.5.8.1.1");

    private static final List<String> MLDSA_KEY_ALGORITHM_OIDS = List.of(
        "2.16.840.1.101.3.4.3.17",
        "2.16.840.1.101.3.4.3.18",
        "2.16.840.1.101.3.4.3.19");

    private static final List<String> MLDSA_SIGNATURE_ALGORITHM_OIDS = List.of(
        "2.16.840.1.101.3.4.3.17",
        "2.16.840.1.101.3.4.3.18",
        "2.16.840.1.101.3.4.3.19");

    /**
     * A list of "extraneous" key algorithm OIDs, typical of what may be reported by a client. Built by
     * running the following command and parsing the results: `openssl list -public-key-algorithms`
     * <p>
     * Note that this list <strong>does not</strong> include explicitly supported algorithm OIDs.
     *
     * Last run: 2026-03-19, OpenSSL 3.5.1 1 Jul 2025
     */
    private static final List<String> EXT_KEY_ALGORITHM_OIDS = List.of(
        "1.2.840.10040.4.1",
        "1.2.840.10045.2.1",
        "1.2.840.10046.2.1",
        "1.2.840.113549.1.1.10",
        "1.2.840.113549.1.3.1",
        "1.3.101.110",
        "1.3.101.111",
        "1.3.101.112",
        "1.3.101.113",
        "1.3.14.3.2.12",
        "1.3.6.1.4.1.11591.4.11",
        "2.16.840.1.101.3.4.3.20",
        "2.16.840.1.101.3.4.3.21",
        "2.16.840.1.101.3.4.3.22",
        "2.16.840.1.101.3.4.3.23",
        "2.16.840.1.101.3.4.3.24",
        "2.16.840.1.101.3.4.3.25",
        "2.16.840.1.101.3.4.3.26",
        "2.16.840.1.101.3.4.3.27",
        "2.16.840.1.101.3.4.3.28",
        "2.16.840.1.101.3.4.3.29",
        "2.16.840.1.101.3.4.3.30",
        "2.16.840.1.101.3.4.3.31",
        "2.16.840.1.101.3.4.4.1",
        "2.16.840.1.101.3.4.4.2",
        "2.16.840.1.101.3.4.4.3");

    /**
     * A list of "extraneous" signature algorithm OIDs, typical of what may be reported by a client. Built by
     * running the following command and parsing the results: `openssl list -signature-algorithms`
     * <p>
     * Note that this list <strong>does not</strong> include explicitly supported algorithm OIDs.
     *
     * Last run: 2026-03-19, OpenSSL 3.5.1 1 Jul 2025
     */
    private static final List<String> EXT_SIGNATURE_ALGORITHM_OIDS = List.of(
        "1.2.156.10197.1.504",
        "1.2.840.1.101.3.4.3.3",
        "1.2.840.1.101.3.4.3.4",
        "1.2.840.10040.4.1",
        "1.2.840.10040.4.3",
        "1.2.840.10045.4.1",
        "1.2.840.10045.4.3.1",
        "1.2.840.10045.4.3.2",
        "1.2.840.10045.4.3.3",
        "1.2.840.10045.4.3.4",
        "1.2.840.113549.1.1.1",
        "1.2.840.113549.1.1.15",
        "1.2.840.113549.1.1.16",
        "1.2.840.113549.1.1.5",
        "1.3.101.112",
        "1.3.101.113",
        "1.3.14.3.2.12",
        "1.3.14.3.2.27",
        "1.3.36.3.3.1.2",
        "2.16.840.1.101.3.4.3.1",
        "2.16.840.1.101.3.4.3.10",
        "2.16.840.1.101.3.4.3.11",
        "2.16.840.1.101.3.4.3.12",
        "2.16.840.1.101.3.4.3.13",
        "2.16.840.1.101.3.4.3.14",
        "2.16.840.1.101.3.4.3.15",
        "2.16.840.1.101.3.4.3.16",
        "2.16.840.1.101.3.4.3.2",
        "2.16.840.1.101.3.4.3.20",
        "2.16.840.1.101.3.4.3.21",
        "2.16.840.1.101.3.4.3.22",
        "2.16.840.1.101.3.4.3.23",
        "2.16.840.1.101.3.4.3.24",
        "2.16.840.1.101.3.4.3.25",
        "2.16.840.1.101.3.4.3.26",
        "2.16.840.1.101.3.4.3.27",
        "2.16.840.1.101.3.4.3.28",
        "2.16.840.1.101.3.4.3.29",
        "2.16.840.1.101.3.4.3.30",
        "2.16.840.1.101.3.4.3.31",
        "2.16.840.1.101.3.4.3.5",
        "2.16.840.1.101.3.4.3.6",
        "2.16.840.1.101.3.4.3.7",
        "2.16.840.1.101.3.4.3.8",
        "2.16.840.1.101.3.4.3.9");

    private static final List<String> STD_KEY_ALGORITHM_OIDS = Stream.of(
        // Explicitly supported algorithms
        RSA_KEY_ALGORITHM_OIDS,
        MLDSA_KEY_ALGORITHM_OIDS,

        // Extraneous algorithms
        EXT_KEY_ALGORITHM_OIDS)
        .flatMap(List::stream)
        .toList();

    private static final List<String> STD_SIGNATURE_ALGORITHM_OIDS = Stream.of(
        // Explicitly supported algorithms
        RSA_SIGNATURE_ALGORITHM_OIDS,
        MLDSA_SIGNATURE_ALGORITHM_OIDS,

        // Extraneous algorithms
        EXT_SIGNATURE_ALGORITHM_OIDS)
        .flatMap(List::stream)
        .toList();

    private CryptoCapabilities() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a CryptographicCapabilitiesDTO instance configured as a typical, standard client would report,
     * with a mix of our supported algorithm OIDs, as well as others which the system's cryptographic provider
     * and security policy happen to support.
     *
     * @return
     *  a new CryptographicCapbilitiesDTO configured with "standard" or typical algorithms
     */
    public static CryptographicCapabilitiesDTO standard() {
        return new CryptographicCapabilitiesDTO()
            .keyAlgorithms(STD_KEY_ALGORITHM_OIDS)
            .signatureAlgorithms(STD_SIGNATURE_ALGORITHM_OIDS);
    }

    /**
     * Returns a CryptographicCapabilitiesDTO instance configured with known, supported RSA algorithm OIDs.
     *
     * @return
     *  a new CryptographicCapabilitiesDTO instance configured with known RSA algorithm OIDs
     */
    public static CryptographicCapabilitiesDTO rsa() {
        return new CryptographicCapabilitiesDTO()
            .keyAlgorithms(RSA_KEY_ALGORITHM_OIDS)
            .signatureAlgorithms(RSA_SIGNATURE_ALGORITHM_OIDS);

    }

    /**
     * Returns a CryptographicCapabilitiesDTO instance configured with known, supported ML-DSA algorithm OIDs.
     *
     * @return
     *  a new CryptographicCapabilitiesDTO instance configured with known ML-DSA algorithm OIDs
     */
    public static CryptographicCapabilitiesDTO mldsa() {
        return new CryptographicCapabilitiesDTO()
            .keyAlgorithms(MLDSA_KEY_ALGORITHM_OIDS)
            .signatureAlgorithms(MLDSA_SIGNATURE_ALGORITHM_OIDS);
    }

    /**
     * Fetches a CryptographicCapabilitiesDTO instance configured with algorithm OIDs that are unsupported by
     * Candlepin, and should result in an error during negotiation or resolution.
     *
     * @return
     *  a new CryptographicCapabilitiesDTO instance configured with unsupported algorithms
     */
    public static CryptographicCapabilitiesDTO unsupported() {
        return new CryptographicCapabilitiesDTO()
            .keyAlgorithms(List.of("12.34.56.78"))
            .signatureAlgorithms(List.of("87.65.43.21"));
    }

    /**
     * Returns a list containing known supported cryptographic capabilities, including a null value, partially
     * populated capabilities, and the "standard" capabilities. The returned list is not guaranteed to be
     * mutable, and should be treated as an immutable list.
     *
     * @return
     *  a list of known, supported capabilities values, including a null value
     */
    public static List<CryptographicCapabilitiesDTO> getSupportedCapabilities() {
        List<CryptographicCapabilitiesDTO> capabilities = new ArrayList<>();

        capabilities.add(null);

        List<Supplier<CryptographicCapabilitiesDTO>> suppliers = List.of(
            CryptoCapabilities::standard,
            CryptoCapabilities::rsa,
            CryptoCapabilities::mldsa);

        for (Supplier<CryptographicCapabilitiesDTO> supplier : suppliers) {
            capabilities.add(supplier.get());

            // add partial variants with only key algos or sig algos populated
            capabilities.add(supplier.get().keyAlgorithms(null));
            capabilities.add(supplier.get().signatureAlgorithms(null));
        }

        return capabilities;
    }
}
