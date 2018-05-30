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

import static org.candlepin.util.DERUtil.*;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract class with DER utility methods for X509CRLStreamWriter.
 */
public abstract class AbstractX509CRLStreamWriter implements X509CRLStreamWriter {
    protected InputStream crlIn;
    protected Signature signer;
    protected boolean locked = false;
    protected boolean preScanned = false;
    protected String signingAlg;

    /**
     * Echo tag without tracking and without signing.
     *
     * @param out
     * @return tag value
     * @throws IOException
     */
    protected int echoTag(OutputStream out) throws IOException {
        return echoTag(out, null, null);
    }

    /**
     * Echo tag and sign with existing RSADigestSigner.
     *
     * @param out
     * @param i optional value to increment by the number of bytes read
     * @return tag value
     * @throws IOException
     */
    protected int echoTag(OutputStream out, AtomicInteger i) throws IOException {
        return echoTag(out, i, signer);
    }

    protected int echoTag(OutputStream out, AtomicInteger i, Signature s) throws IOException {
        int tag = readTag(crlIn, i);
        int tagNo = readTagNumber(crlIn, tag, i);
        writeTag(out, tag, tagNo, s);
        return tagNo;
    }

    /**
     * Echo length without tracking and without signing.
     *
     * @param out
     * @return length value
     * @throws IOException
     */
    protected int echoLength(OutputStream out) throws IOException {
        return echoLength(out, null, null);
    }

    /**
     * Echo length and sign with existing RSADigestSigner.
     *
     * @param out
     * @param i optional value to increment by the number of bytes read
     * @return length value
     * @throws IOException
     */
    protected int echoLength(OutputStream out, AtomicInteger i) throws IOException {
        return echoLength(out, i, signer);
    }

    protected int echoLength(OutputStream out, AtomicInteger i, Signature s) throws IOException {
        int length = readLength(crlIn, i);
        writeLength(out, length, s);
        return length;
    }

    /**
     * Echo value without tracking and without signing.
     *
     * @param out
     * @param length
     * @throws IOException
     */
    protected void echoValue(OutputStream out, int length) throws IOException {
        echoValue(out, length, null, null);
    }

    /**
     * Echo value and sign with existing RSADigestSigner.
     *
     * @param out
     * @param length
     * @param i optional value to increment by the number of bytes read
     * @throws IOException
     */
    protected void echoValue(OutputStream out, int length, AtomicInteger i) throws IOException {
        echoValue(out, length, i, signer);
    }

    protected void echoValue(OutputStream out, int length, AtomicInteger i, Signature s)
        throws IOException {
        byte[] item = new byte[length];
        readFullyAndTrack(crlIn, item, i);
        writeValue(out, item, s);
    }

    protected Signature createContentSigner(String signingAlg, PrivateKey key) throws
        IOException {
        try {
            Signature s = Signature.getInstance(signingAlg, BouncyCastleProvider.PROVIDER_NAME);
            s.initSign(key);
            return s;
        }
        catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IOException("Could not create Signature for " + signingAlg, e);
        }
    }

    @Override
    public X509CRLStreamWriter preScan(File crlToChange) throws IOException {
        return preScan(crlToChange, null);
    }

    @Override
    public X509CRLStreamWriter preScan(File crlToChange, CRLEntryValidator validator)
        throws IOException {
        return preScan(new BufferedInputStream(new FileInputStream(crlToChange)), validator);
    }

    @Override
    public X509CRLStreamWriter preScan(InputStream crlToChange) throws IOException {
        return preScan(crlToChange, null);
    }

    @Override
    public X509CRLStreamWriter lock() {
        if (locked) {
            throw new IllegalStateException("This stream is already locked.");
        }

        locked = true;
        return this;
    }

    protected abstract void writeToEmptyCrl(OutputStream out) throws IOException;

    /**
     * This method increments the crlNumber and updates the authorityKeyIdentifier extensions.  Any
     * other extensions are copied over unchanged.
     * @param obj a byte array of DER encoded extensions
     * @return a byte array of DER encoded extensions with the crlNumber and authorityKeyIdentifier updated
     * @throws IOException in the event of a DER encoding error
     */
    protected abstract byte[] updateExtensions(byte[] obj) throws IOException;

    /**
     * Updates and writes the header (elements in TBSCertList preceding the revokedCertificates sequence.
     * @param out the OutputStream to write the new CRL data to
     * @return the length of the old revokedCertificates sequence
     * @throws IOException in the event of a DER encoding error
     */
    protected abstract int handleHeader(OutputStream out) throws IOException;

    protected void readAndReplaceSignatureAlgorithm(OutputStream out, byte[] algorithmId) throws IOException {
        int originalLength = readLength(crlIn, null);
        byte[] oldBytes = new byte[originalLength];
        readFullyAndTrack(crlIn, oldBytes, null);

        try (InputStream algIn = new ByteArrayInputStream(algorithmId)) {
            // We're already at the V portion of the AlgorithmIdentifier TLV, so we need to get to the V
            // portion of our new AlgorithmIdentifier and compare it with the old V.
            int newTag = readTag(algIn, null);
            readTagNumber(algIn, newTag, null);
            int newLength = readLength(algIn, null);
            byte[] newBytes = new byte[newLength];
            readFullyAndTrack(algIn, newBytes, null);

            /* If the signing algorithm has changed dramatically, give up.  For our use case we will always
            have <something>WithRSA, which will yield AlgorithmIdentifiers of equal length.  If we had to
            worry about going from SHA1WithRSA to SHA256WithECDSA or something like that, we would need to do
            a lot more work to get everything lined up right since the ECDSA identifiers carry the name of the
            elliptic curve used and other parameters while RSA has no parameters. */
            if (originalLength != newLength) {
                throw new IllegalStateException(
                    "AlgorithmIdentifier has changed lengths. DER corruption would result.");
            }
        }

        writeBytes(out, algorithmId);
    }

    /**
     * Write a new nextUpdate time that is the same amount of time ahead of the new thisUpdate
     * time as the old nextUpdate was from the old thisUpdate.
     *
     * @param out the OutputStream to write the new CRL data to
     * @param tagNo the tag number for this TLV to determine use of UTCTime or GeneralizedTime
     * @param oldThisUpdate the previous value of the nextUpdate
     * @throws IOException in the event of a DER encoding error
     */
    protected abstract void offsetNextUpdate(OutputStream out, int tagNo, Date oldThisUpdate)
        throws IOException;

    /**
     * Replace a time in the ASN1 with the current time.
     *
     * @param out
     * @param tagNo
     * @return the time that was replaced
     * @throws IOException in the event of a DER encoding error
     */
    protected abstract Date readAndReplaceTime(OutputStream out, int tagNo) throws IOException;

    protected void writeNewTime(OutputStream out, byte[] newEncodedTime, int originalLength)
        throws IOException {

        try (InputStream timeIn = new ByteArrayInputStream(newEncodedTime)) {
            int newTag = readTag(timeIn, null);
            readTagNumber(timeIn, newTag, null);
            int newLength = readLength(timeIn, null);

            /* If the length changes, it's going to create a discrepancy with the length
             * reported in the TBSCertList sequence.  The length could change with the addition
             * or removal of time zone information for example. */
            if (newLength != originalLength) {
                throw new IllegalStateException("Length of generated time does not match " +
                    "the original length. DER corruption would result.");
            }
        }

        writeBytes(out, newEncodedTime);
    }
}
