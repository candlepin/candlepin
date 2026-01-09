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
package org.candlepin.pki;

import java.security.cert.X509Certificate;

/**
 * A cryptographic algorithm scheme used for signature operations.
 *
 * @param name
 *  the name of the signature scheme
 *
 * @param certificate
 *  the certificate to use for cryptographic operations
 *
 * @param signatureAlgorithm
 *  the signature algorithm to use for cryptographic signing operations
 *
 * @param keyAlgorithm
 *  the algorithm used to generate key pairs under this scheme
 */
public record Scheme(
    String name,
    X509Certificate certificate,
    String signatureAlgorithm,
    String keyAlgorithm
) {
    public Scheme {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is null or empty");
        }

        if (certificate == null) {
            throw new IllegalArgumentException("certificate is null");
        }

        if (signatureAlgorithm == null || signatureAlgorithm.isBlank()) {
            throw new IllegalArgumentException("signatureAlgorithm is null or empty");
        }

        if (keyAlgorithm == null || keyAlgorithm.isBlank()) {
            throw new IllegalArgumentException("keyAlgorithm is null or empty");
        }
    }

}
