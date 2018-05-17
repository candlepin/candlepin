/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.Date;

/**
 * Interface to adding entries to an X.509 CRL in a memory-efficient manner.
 */
public interface X509CRLStreamWriter {

    X509CRLStreamWriter preScan(File crlToChange) throws IOException;

    X509CRLStreamWriter preScan(File crlToChange, CRLEntryValidator validator) throws IOException;

    X509CRLStreamWriter preScan(InputStream crlToChange) throws IOException;

    X509CRLStreamWriter preScan(InputStream crlToChange, CRLEntryValidator validator) throws IOException;

    /**
     * Create an entry to be added to the CRL.
     *
     * @param serial
     * @param date
     * @param reason
     * @throws IOException if an entry fails to generate
     */
    void add(BigInteger serial, Date date, int reason) throws IOException;

    /**
     * Allow the user to change the signing algorithm used.  Only RSA based algorithms are supported.
     */
    void setSigningAlgorithm(String algorithm);

    /**
     * Locks the stream to prepare it for writing.
     *
     * @return itself
     */
    X509CRLStreamWriter lock();

    boolean hasChangesQueued();

    /**
     * Write a modified CRL to the given output stream.  This method will add each entry provided
     * via the add() method.
     *
     * @param out OutputStream to write to
     * @throws IOException if something goes wrong
     */
    void write(OutputStream out) throws IOException;
}
