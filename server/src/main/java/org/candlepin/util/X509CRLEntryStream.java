/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import static org.bouncycastle.asn1.DERTags.*;
import static org.candlepin.util.DERUtil.*;

import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.TBSCertList.CRLEntry;
import org.bouncycastle.jce.provider.X509CRLEntryObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reads an X509 CRL in a stream and returns the serial number for a revoked certificate
 * with each call to the iterator's next() function.
 *
 * The schema for an X509 CRL is described in
 * <a href="https://tools.ietf.org/html/rfc5280#section-5">section 5 of RFC 5280</a>
 *
 * It is reproduced here for quick reference
 *
 * <pre>
 * {@code
 * CertificateList  ::=  SEQUENCE  {
 *      tbsCertList          TBSCertList,
 *      signatureAlgorithm   AlgorithmIdentifier,
 *      signatureValue       BIT STRING  }
 *
 * TBSCertList  ::=  SEQUENCE  {
 *      version                 Version OPTIONAL,
 *                                   -- if present, MUST be v2
 *      signature               AlgorithmIdentifier,
 *      issuer                  Name,
 *      thisUpdate              Time,
 *      nextUpdate              Time OPTIONAL,
 *      revokedCertificates     SEQUENCE OF SEQUENCE  {
 *           userCertificate         CertificateSerialNumber,
 *           revocationDate          Time,
 *           crlEntryExtensions      Extensions OPTIONAL
 *                                    -- if present, version MUST be v2
 *                                }  OPTIONAL,
 *      crlExtensions           [0]  EXPLICIT Extensions OPTIONAL
 *                                    -- if present, version MUST be v2
 *                                }
 *
 * Version, Time, CertificateSerialNumber, and Extensions
 * are all defined in the ASN.1 in Section 4.1
 *
 * AlgorithmIdentifier is defined in Section 4.1.1.2
 * }
 * </pre>
 *
 * ASN1 is based around the TLV (tag, length, value) concept.  Any piece of
 * data begins with a tag defining the data type, has a series of bytes to
 * indicate the data length, and then the data itself.
 *
 * <b>This class does not perform any signature checking</b>.  Checking the signature is
 * theoretically possible but we would be unable to check it until after the list of
 * entries had been exhausted (since each entry would have to be sent through the hasher).  At
 * that point, the user already has all the serial numbers.  The best approach would perhaps
 * be to check the signature in hasNext() once revokedSeqBytes hits zero and throw a
 * RuntimeException.  We would also need a RSA public key as a parameter to the constructor.
 *
 * See https://en.wikipedia.org/wiki/X.690 and http://luca.ntop.org/Teaching/Appunti/asn1.html
 * for reference on ASN1 and DER encoding.
 */
public class X509CRLEntryStream implements Closeable, Iterator<X509CRLEntryObject> {
    private InputStream crlStream;

    /* Long definite lengths can go up to 2^1008 - 1.  Integers max out at 2.1 billion
     * which translates to about 2GB.  A CRL with around 1.5 million entries is about
     * 90MB so if we end up with any sequences over 2GB, we are in real trouble. */
    private Integer revokedSeqBytes;
    private AtomicInteger count;

    /**
     * Construct a X509CRLStream.  <b>The underlying data in the stream parameter must
     * be in DER format</b>.  PEM format will not work because we need to operate
     * on the raw ASN1 of the DER.  Use Apache Common's Base64InputStream with the X509
     * header and footers stripped off if you need to use a PEM file.
     *
     * @param stream
     * @throws IOException if we can't read the provided File
     */
    public X509CRLEntryStream(InputStream stream) throws IOException {
        crlStream = stream;
        revokedSeqBytes = discardHeader(crlStream);
        count = new AtomicInteger();
    }

    /**
     * Construct a X509CRLStream.  <b>The crlFile parameter must be in DER format</b>.
     * PEM format will not work because we need to operate on the raw ASN1 of the DER.
     *
     * @param crlFile
     * @throws IOException if we can't read the provided File
     */
    public X509CRLEntryStream(File crlFile) throws IOException {
        this(new BufferedInputStream(new FileInputStream(crlFile)));
    }

    /**
     * Strip off the CRL meta-data and drill down to the sequence containing the
     * revokedCertificates objects.
     *
     * @return the length in bytes of the revokedCertificates sequence
     * @throws IOException
     */
    protected int discardHeader(InputStream s) throws IOException {
        // Strip the tag and length of the CertificateList sequence
        int tag = readTag(s, count);
        readTagNumber(s, tag, count);
        readLength(s, count);

        // At this point we are at the tag for the TBSCertList sequence and we need to
        // strip off the tag and length
        tag = readTag(s, count);
        readTagNumber(s, tag, count);
        readLength(s, count);

        // Now we are actually at the values within the TBSCertList sequence.
        // Read the CRL metadata and trash it.  We get to the thisUpdate item
        // and then break out.
        int tagNo = NULL;
        while (true) {
            tag = readTag(s, count);
            tagNo = readTagNumber(s, tag, count);
            int length = readLength(s, count);
            byte[] item = new byte[length];
            readFullyAndTrack(s, item, count);

            if (tagNo == GENERALIZED_TIME || tagNo == UTC_TIME) {
                break;
            }
        }

        tag = readTag(s, count);
        tagNo = readTagNumber(s, tag, count);

        // The nextUpdate item is optional.  If it's there, we trash it.
        if (tagNo == GENERALIZED_TIME || tagNo == UTC_TIME) {
            int length = readLength(s, count);
            byte[] item = new byte[length];
            readFullyAndTrack(s, item, count);
            tag = readTag(s, count);
            tagNo = readTagNumber(s, tag, count);
        }

        // Return the length of the revokedCertificates sequence.  We need to
        // track the bytes we read and read no more than this length to prevent
        // decoding errors.
        return readLength(s, count);
    }

    public X509CRLEntryObject next() {
        try {
            // Strip the tag for the revokedCertificate entry
            int tag = readTag(crlStream, count);
            readTagNumber(crlStream, tag, count);

            int entryLength = readLength(crlStream, count);

            byte[] entry = new byte[entryLength];
            readFullyAndTrack(crlStream, entry, count);

            ByteArrayOutputStream reconstructed = new ByteArrayOutputStream();
            // An ASN1 SEQUENCE tag is 0x30
            reconstructed.write(0x30);
            writeLength(reconstructed, entryLength);
            reconstructed.write(entry);

            /* NB: This BouncyCastle method is very slow.  If we just read the serial number
             * alone out of the sequence, we can loop through 2 million entries in 500 ms.
             * Using this method takes around 2300 ms.  But we need the entire
             * X509CRLEntryObject for the X509CRLStreamWriter, so we're kind of stuck
             * with it.
             */
            DERSequence obj = (DERSequence) DERSequence.fromByteArray(reconstructed.toByteArray());
            reconstructed.close();

            CRLEntry crlEntry = new CRLEntry(obj);

            return new X509CRLEntryObject(crlEntry);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasNext() {
        return revokedSeqBytes > count.get();
    }

    @Override
    public void close() throws IOException {
        crlStream.close();
    }
}
