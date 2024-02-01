/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import java.security.PrivateKey;
import java.security.cert.X509Certificate;


/**
 * Contract for PEM encoding of certificates and private keys.
 */
public interface PemEncoder {

    /**
     * Takes an {@link X509Certificate} object and returns a PEM encoded byte[] of the certificate.
     *
     * @param certificate
     *  A certificate to be encoded.
     * @return
     *  PEM-encoded bytes of the certificate
     * @throws PemEncodingException
     *  If there is a problem with encoding
     */
    byte[] encodeAsBytes(X509Certificate certificate);

    /**
     * Takes an {@link PrivateKey} object and returns a PEM encoded byte[] of the key.
     *
     * @param privateKey
     *  A key to be encoded.
     * @return
     *  PEM-encoded bytes of the key
     * @throws PemEncodingException
     *  If there is a problem with encoding
     */
    byte[] encodeAsBytes(PrivateKey privateKey);

    /**
     * Takes an {@link X509Certificate} object and returns a PEM encoded string of the certificate.
     *
     * @param certificate
     *  A certificate to be encoded.
     * @return
     *  PEM-encoded string of the certificate
     * @throws PemEncodingException
     *  If there is a problem with encoding
     */
    String encodeAsString(X509Certificate certificate);

    /**
     * Takes an {@link PrivateKey} object and returns a PEM encoded string of the key.
     *
     * @param privateKey
     *  A key to be encoded.
     * @return
     *  PEM-encoded string of the key
     * @throws PemEncodingException
     *  If there is a problem with encoding
     */
    String encodeAsString(PrivateKey privateKey);

}
