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

import java.io.InputStream;

/**
 * This signer is responsible for cryptographic signing operations.
 */
public interface Signer {

    /**
     * Returns the cryptographic scheme used by this signer. This method should never return null.
     *
     * @return the {@link Scheme} representing the cryptographic algorithm and parameters used for signing
     *  operations
     */
    Scheme getCryptoScheme();

    /**
     * Signs the data read from the provided input stream using the signing algorithm based on this signer's
     * {@link Scheme}. This method should never return null.
     *
     * @param istream
     *  the input stream containing the data to be signed
     *
     * @throws SignatureException
     *  if unable to sign the data provided by the input stream
     *
     * @return a byte array containing the cryptographic signature
     */
    byte[] sign(InputStream istream);

    /**
     * Signs the provided byte array data using the signing algorithm based on this signer's {@link Scheme}.
     * This method should never return null.
     *
     * @param data
     *  the byte array containing the data to be signed
     *
     * @throws SignatureException
     *  if unable to sign the data provided by the byte array
     *
     * @return a byte array containing the cryptographic signature
     */
    byte[] sign(byte[] data);

}
