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

import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERGeneralizedTime;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERObject;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERTags;
import org.bouncycastle.asn1.DERUTCTime;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.asn1.x509.X509Extension;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.MD4Digest;
import org.bouncycastle.crypto.digests.MD5Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA224Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA384Digest;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.signers.RSADigestSigner;
import org.bouncycastle.jce.provider.X509CRLEntryObject;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.cert.CRLException;
import java.security.interfaces.RSAPrivateKey;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class for adding entries to an X509 in a memory-efficient manner.
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
 */
public class X509CRLStreamWriter {
    public static final Logger log = LoggerFactory.getLogger(X509CRLStreamWriter.class);

    private boolean locked = false;
    private boolean preScanned = false;

    private List<DERSequence> newEntries;
    private Set<BigInteger> deletedEntries;

    private InputStream crlIn;

    private Integer originalLength;
    private AtomicInteger count;

    private AlgorithmIdentifier signingAlg;
    private AlgorithmIdentifier digestAlg;

    private int deletedEntriesLength;
    private RSADigestSigner signer;
    private RSAPrivateKey key;
    private DERInteger newCrlNumber;
    private int crlNumberHeaderBytesDelta;

    private int newSigLength;
    private int oldSigLength;

    public X509CRLStreamWriter(File crlToChange, RSAPrivateKey key)
        throws CryptoException, IOException {
        this(new BufferedInputStream(new FileInputStream(crlToChange)), key);
    }

    public X509CRLStreamWriter(InputStream crlToChange, RSAPrivateKey key)
        throws CryptoException, IOException {
        this.deletedEntries = new HashSet<BigInteger>();
        this.deletedEntriesLength = 0;

        this.newEntries = new LinkedList<DERSequence>();
        this.crlIn = crlToChange;

        this.count = new AtomicInteger();

        /* The length of an RSA signature is padded out to the length of the modulus
         * in bytes.  See http://stackoverflow.com/questions/6658728/rsa-signature-size
         *
         * If the original CRL was signed with a 2048 bit key and someone sends in a
         * 4096 bit key, we need to account for the discrepancy.
         */
        int newSigBytes = key.getModulus().bitLength() / 8;

        /* Now we need a byte array to figure out how long the new signature will
         * be when encoded.
         */
        byte[] dummySig = new byte[newSigBytes];
        Arrays.fill(dummySig, (byte) 0x00);
        this.newSigLength = new DERBitString(dummySig).getDEREncoded().length;

        this.key = key;
    }

    public X509CRLStreamWriter preScan(File crlToChange) throws IOException {
        return preScan(crlToChange, null);
    }

    public X509CRLStreamWriter preScan(File crlToChange, CRLEntryValidator validator)
        throws IOException {
        return preScan(new BufferedInputStream(new FileInputStream(crlToChange)), validator);
    }

    public X509CRLStreamWriter preScan(InputStream crlToChange) throws IOException {
        return preScan(crlToChange, null);
    }

    @SuppressWarnings("rawtypes")
    public synchronized X509CRLStreamWriter preScan(InputStream crlToChange, CRLEntryValidator validator)
        throws IOException {
        if (locked) {
            throw new IllegalStateException("Cannot modify a locked stream.");
        }

        if (preScanned) {
            throw new IllegalStateException("preScan has already been run.");
        }

        X509CRLEntryStream reaperStream = null;
        ASN1InputStream asn1In = null;

        try {
            reaperStream = new X509CRLEntryStream(crlToChange);
            try {
                while (reaperStream.hasNext()) {
                    X509CRLEntryObject entry = reaperStream.next();
                    if (validator != null && validator.shouldDelete(entry)) {
                        deletedEntries.add(entry.getSerialNumber());
                        deletedEntriesLength += entry.getEncoded().length;
                    }
                }
            }
            catch (CRLException e) {
                throw new IOException("Could not read CRL entry", e);
            }

            /* At this point, crlToChange is at the point where the crlExtensions would
             * be.  RFC 5280 says that "Conforming CRL issuers are REQUIRED to include
             * the authority key identifier (Section 5.2.1) and the CRL number (Section 5.2.3)
             * extensions in all CRLs issued.
             */
            DERSequence extensions = null;
            DERObject o;
            asn1In = new ASN1InputStream(crlToChange);
            while ((o = asn1In.readObject()) != null) {
                if (o instanceof DERSequence) {
                    // Now we are at the signatureAlgorithm
                    DERSequence seq = (DERSequence) o;
                    if (seq.getObjectAt(0) instanceof DERObjectIdentifier) {
                        signingAlg = new AlgorithmIdentifier(seq);
                        digestAlg = new DefaultDigestAlgorithmIdentifierFinder().find(signingAlg);

                        try {
                            // Build the signer
                            this.signer = new RSADigestSigner(createDigest(digestAlg));
                            signer.init(true, new RSAKeyParameters(
                                true, key.getModulus(), key.getPrivateExponent()));
                        }
                        catch (CryptoException e) {
                            throw new IOException(
                                "Could not create RSADigest signer for " + digestAlg.getAlgorithm());
                        }
                    }
                }
                else if (o instanceof DERBitString) {
                    oldSigLength = o.getDEREncoded().length;
                }
                else {
                    if (extensions != null) {
                        throw new IllegalStateException("Already read in CRL extensions.");
                    }
                    DERTaggedObject taggedExts = (DERTaggedObject) o;
                    extensions = (DERSequence) taggedExts.getObject();
                }
            }

            if (extensions == null) {
                /* v1 CRLs (defined in RFC 1422) don't require extensions but all new
                 * CRLs should be v2 (defined in RFC 5280).  In the extremely unlikely
                 * event that someone is working with a v1 CRL, we handle it here although
                 * we print a warning.
                 */
                preScanned = true;
                newCrlNumber = null;
                crlNumberHeaderBytesDelta = 0;
                log.warn("The CRL you are modifying is a version 1 CRL." +
                    " Please investigate moving to a version 2 CRL by adding the CRL Number" +
                    " and Authority Key Identifier extensions.");
                return this;
            }

            // Now we need to read the extensions and find the CRL number and increment it,
            // and determine if its length changed.
            Enumeration objs = extensions.getObjects();
            while (objs.hasMoreElements()) {
                DERSequence ext = (DERSequence) objs.nextElement();
                DERObjectIdentifier oid = (DERObjectIdentifier) ext.getObjectAt(0);
                if (X509Extension.cRLNumber.equals(oid)) {
                    DEROctetString s = (DEROctetString) ext.getObjectAt(1);
                    DERInteger i = (DERInteger) DERTaggedObject.fromByteArray(s.getOctets());
                    newCrlNumber = new DERInteger(i.getValue().add(BigInteger.ONE));
                    crlNumberHeaderBytesDelta =
                        newCrlNumber.getDEREncoded().length - i.getDEREncoded().length;
                    break;
                }
            }
        }
        finally {
            if (reaperStream != null) {
                reaperStream.close();
            }
            IOUtils.closeQuietly(asn1In);
        }
        preScanned = true;
        return this;
    }

    /**
     * Create an entry to be added to the CRL.
     *
     * @param serial
     * @param date
     * @param reason
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void add(BigInteger serial, Date date, int reason) {
        if (locked) {
            throw new IllegalStateException("Cannot add to a locked stream.");
        }

        ASN1EncodableVector v = new ASN1EncodableVector();

        v.add(new DERInteger(serial));
        v.add(new Time(date));

        if (reason != 0) {
            CRLReason crlReason = new CRLReason(reason);
            Vector extOids = new Vector();
            Vector extValues = new Vector();
            extOids.addElement(X509Extension.reasonCode);
            extValues.addElement(new X509Extension(false, new DEROctetString(crlReason.getDEREncoded())));
            v.add(new X509Extensions(extOids, extValues));
        }

        newEntries.add(new DERSequence(v));
    }

    /**
     * Locks the stream to prepare it for writing.
     *
     * @return itself
     */
    public synchronized X509CRLStreamWriter lock() {
        if (locked) {
            throw new IllegalStateException("This stream is already locked.");
        }

        locked = true;
        return this;
    }

    public boolean hasChangesQueued() {
        return this.newEntries.size() > 0 || this.deletedEntries.size() > 0;
    }

    /**
     * Write a modified CRL to the given output stream.  This method will add each entry provided
     * via the add() method.
     *
     * @param out OutputStream to write to
     * @throws IOException if something goes wrong
     */
    public void write(OutputStream out) throws IOException {
        if (!locked || !preScanned) {
            throw new IllegalStateException("The instance must be preScanned and locked before writing.");
        }

        originalLength = handleHeader(out);

        int tag;
        int tagNo;
        int length;

        while (originalLength > count.get()) {
            tag = readTag(crlIn, count);
            tagNo = readTagNumber(crlIn, tag, count);
            length = readLength(crlIn, count);
            byte[] entryBytes = new byte[length];
            readFullyAndTrack(crlIn, entryBytes, count);

            DERInteger serial = (DERInteger) DERInteger.fromByteArray(entryBytes);

            if (deletedEntriesLength == 0 || !deletedEntries.contains(serial.getValue())) {
                writeTag(out, tag, tagNo, signer);
                writeLength(out, length, signer);
                writeValue(out, entryBytes, signer);
            }
        }

        ASN1InputStream asn1In = null;
        byte[] extensions = null;
        try {
            asn1In = new ASN1InputStream(crlIn);

            DERObject o;
            while ((o = asn1In.readObject()) != null) {
                if (o instanceof DERSequence) {
                    // Don't care about the old signatureAlorithm or signatureValue at this point
                    break;
                }
                else {
                    if (extensions != null) {
                        throw new IllegalStateException("Already read in CRL extensions.");
                    }
                    extensions = o.getDEREncoded();
                }
            }
        }
        finally {
            IOUtils.closeQuietly(asn1In);
        }

        // Write the new entries into the new CRL
        for (DERSequence entry : newEntries) {
            writeBytes(out, entry.getDEREncoded(), signer);
        }

        // Copy the old extensions over
        if (extensions != null) {
            byte[] newExtensions = incrementCrlNumber(extensions);
            out.write(newExtensions);
            signer.update(newExtensions, 0, newExtensions.length);
        }
        out.write(signingAlg.getDEREncoded());

        try {
            byte[] signature = signer.generateSignature();
            DERBitString signatureBits = new DERBitString(signature);
            out.write(signatureBits.getDEREncoded());
        }
        catch (DataLengthException e) {
            throw new IOException("Could not sign", e);
        }
        catch (CryptoException e) {
            throw new IOException("Could not sign", e);
        }
    }

    @SuppressWarnings("rawtypes")
    protected byte[] incrementCrlNumber(byte[] extensions) throws IOException {
        DERTaggedObject taggedExts = (DERTaggedObject) DERTaggedObject.fromByteArray(extensions);
        DERSequence seq = (DERSequence) taggedExts.getObject();
        ASN1EncodableVector modifiedExts = new ASN1EncodableVector();

        // Now we need to read the extensions and find the CRL number and increment it,
        // and determine if its length changed.
        Enumeration objs = seq.getObjects();
        while (objs.hasMoreElements()) {
            DERSequence ext = (DERSequence) objs.nextElement();
            DERObjectIdentifier oid = (DERObjectIdentifier) ext.getObjectAt(0);
            if (X509Extension.cRLNumber.equals(oid)) {
                X509Extension newNumberExt =
                    new X509Extension(false, new DEROctetString(newCrlNumber.getDEREncoded()));

                ASN1EncodableVector crlNumber = new ASN1EncodableVector();
                crlNumber.add(X509Extension.cRLNumber);
                crlNumber.add(newNumberExt.getValue());
                modifiedExts.add(new DERSequence(crlNumber));
            }
            else {
                modifiedExts.add(ext);
            }
        }

        DERSequence seqOut = new DERSequence(modifiedExts);
        DERTaggedObject out = new DERTaggedObject(true, 0, seqOut);
        return out.getDEREncoded();
    }

    protected int handleHeader(OutputStream out) throws IOException {
        int addedEntriesLength = 0;
        for (DERSequence s : newEntries) {
            addedEntriesLength += s.getDEREncoded().length;
        }

        int topTag = readTag(crlIn, null);
        int topTagNo = readTagNumber(crlIn, topTag, null);
        int oldTotalLength = readLength(crlIn, null);

        // Now we are in the TBSCertList
        int tbsTag = readTag(crlIn, null);
        int tbsTagNo = readTagNumber(crlIn, tbsTag, null);
        int oldTbsLength = readLength(crlIn, null);

        /* We may need to adjust the overall length of the tbsCertList
         * based on changes in the revokedCertificates sequence, so we
         * will cache the tbsCertList data in this temporary byte stream.
         */
        ByteArrayOutputStream temp = new ByteArrayOutputStream();

        int tagNo = DERTags.NULL;
        Date oldThisUpdate = null;
        while (true) {
            int tag = readTag(crlIn, null);
            tagNo = readTagNumber(crlIn, tag, null);

            if (tagNo == GENERALIZED_TIME || tagNo == UTC_TIME) {
                oldThisUpdate = readAndReplaceTime(temp, tagNo);
                break;
            }
            else {
                writeTag(temp, tag, tagNo);
                int length = echoLength(temp);
                echoValue(temp, length);
            }
        }

        // Now we have to deal with the potential for an optional nextUpdate field
        int tag = readTag(crlIn, null);
        tagNo = readTagNumber(crlIn, tag, null);

        if (tagNo == DERTags.GENERALIZED_TIME || tagNo == DERTags.UTC_TIME) {
            /* It would be possible to take in a desired nextUpdate in the constructor
             * but I'm not sure if the added complexity is worth it.
             */
            offsetNextUpdate(temp, tagNo, oldThisUpdate);
            echoTag(temp);
        }
        else {
            writeTag(temp, tag, tagNo);
        }

        /* Much like throwing a stone into a pond, as one sequence increases in
         * length the change can ripple out to parent sequences as more bytes are
         * required to encode the length.  For example, if we have a tbsCertList of
         * size 250 and a revokedCertificates list of size 100, the revokedCertificates
         * list size could increase by 6 with no change in the length bytes its sequence
         * requires.  However, 250 + 6 extra bytes equals a total length of 256 which
         * requires 2 bytes to encode instead of 1, thus changing the total length
         * of the CertificateList sequence.
         *
         * We account for these ripples with the xxxHeaderBytesDelta variables.
         */
        int revokedCertsLengthDelta = addedEntriesLength - deletedEntriesLength;
        int oldRevokedCertsLength = readLength(crlIn, null);
        int newRevokedCertsLength = oldRevokedCertsLength + revokedCertsLengthDelta;
        int revokedCertsHeaderBytesDelta = findHeaderBytesDelta(oldRevokedCertsLength, newRevokedCertsLength);

        int tbsCertListLengthDelta = revokedCertsLengthDelta +
            crlNumberHeaderBytesDelta +
            revokedCertsHeaderBytesDelta;
        int newTbsLength = oldTbsLength + tbsCertListLengthDelta;
        int tbsHeaderBytesDelta = findHeaderBytesDelta(oldTbsLength, newTbsLength);

        // Since the signature is in the top level sequence, we don't need to
        // calculate the header bytes delta.
        int sigLengthDelta = newSigLength - oldSigLength;

        int totalLengthDelta = tbsCertListLengthDelta + tbsHeaderBytesDelta + sigLengthDelta;
        int newTotalLength = oldTotalLength + totalLengthDelta;

        /* NB: The top level sequence isn't part of the signature so its tag and
         * length do not go through the signer.
         */
        writeTag(out, topTag, topTagNo);
        writeLength(out, newTotalLength);

        writeTag(out, tbsTag, tbsTagNo, signer);
        writeLength(out, newTbsLength, signer);

        byte[] header = temp.toByteArray();
        temp.close();
        out.write(header);
        signer.update(header, 0, header.length);

        writeLength(out, newRevokedCertsLength, signer);
        return oldRevokedCertsLength;
    }

    /**
     * Write a new nextUpdate time that is the same amount of time ahead of the new thisUpdate
     * time as the old nextUpdate was from the old thisUpdate.
     *
     * @param out
     * @param tagNo
     * @param oldThisUpdate
     * @throws IOException
     */
    protected void offsetNextUpdate(OutputStream out, int tagNo, Date oldThisUpdate)
        throws IOException {
        int originalLength = readLength(crlIn, null);
        byte[] oldBytes = new byte[originalLength];
        readFullyAndTrack(crlIn, oldBytes, null);

        DERObject oldTime = null;
        if (tagNo == UTC_TIME) {
            DERTaggedObject t = new DERTaggedObject(UTC_TIME, new DEROctetString(oldBytes));
            oldTime = DERUTCTime.getInstance(t, false);
        }
        else {
            DERTaggedObject t = new DERTaggedObject(GENERALIZED_TIME, new DEROctetString(oldBytes));
            oldTime = DERGeneralizedTime.getInstance(t, false);
        }

        /* Determine the time between the old thisUpdate and old nextUpdate and add it
        /* to the new nextUpdate. */
        Date oldNextUpdate = new Time(oldTime).getDate();
        long delta = oldNextUpdate.getTime() - oldThisUpdate.getTime();
        Date newNextUpdate = new Date(new Date().getTime() + delta);

        DERObject newTime = null;
        if (tagNo == UTC_TIME) {
            newTime = new DERUTCTime(newNextUpdate);
        }
        else {
            newTime = new DERGeneralizedTime(newNextUpdate);
        }
        writeNewTime(out, newTime, originalLength);
    }

    /**
     * Replace a time in the ASN1 with the current time.
     *
     * @param out
     * @param tagNo
     * @return the time that was replaced
     * @throws IOException
     */
    protected Date readAndReplaceTime(OutputStream out, int tagNo) throws IOException {
        int originalLength = readLength(crlIn, null);
        byte[] oldBytes = new byte[originalLength];
        readFullyAndTrack(crlIn, oldBytes, null);

        DERObject oldTime = null;
        DERObject newTime = null;
        if (tagNo == UTC_TIME) {
            DERTaggedObject t = new DERTaggedObject(UTC_TIME, new DEROctetString(oldBytes));
            oldTime = DERUTCTime.getInstance(t, false);
            newTime = new DERUTCTime(new Date());
        }
        else {
            DERTaggedObject t = new DERTaggedObject(GENERALIZED_TIME, new DEROctetString(oldBytes));
            oldTime = DERGeneralizedTime.getInstance(t, false);
            newTime = new DERGeneralizedTime(new Date());
        }

        writeNewTime(out, newTime, originalLength);
        return new Time(oldTime).getDate();
    }

    /**
     * Write a UTCTime or GeneralizedTime to an output stream.
     *
     * @param out
     * @param newTime
     * @param originalLength
     * @throws IOException
     */
    protected void writeNewTime(OutputStream out, DERObject newTime, int originalLength)
        throws IOException {
        byte[] newEncodedTime = newTime.getDEREncoded();

        InputStream timeIn = null;
        try {
            timeIn = new ByteArrayInputStream(newEncodedTime);
            int newTag = readTag(timeIn, null);
            readTagNumber(timeIn, newTag, null);
            int newLength = readLength(timeIn, null);

            /* If the length changes, it's going to create a discrepancy with the length
             * reported in the TBSCertList sequence.  The length could change with the addition
             * or removal of time zone information for example. */
            if (newLength != originalLength) {
                throw new IllegalStateException("Length of generated time does not match " +
                    "the original length. Corruption would result.");
            }
        }
        finally {
            IOUtils.closeQuietly(timeIn);
        }

        writeBytes(out, newEncodedTime);
    }

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

    protected int echoTag(OutputStream out, AtomicInteger i, RSADigestSigner s) throws IOException {
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

    protected int echoLength(OutputStream out, AtomicInteger i, RSADigestSigner s) throws IOException {
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

    protected void echoValue(OutputStream out, int length, AtomicInteger i, RSADigestSigner s)
        throws IOException {
        byte[] item = new byte[length];
        readFullyAndTrack(crlIn, item, i);
        writeValue(out, item, s);
    }

    protected static Digest createDigest(AlgorithmIdentifier digAlg) throws CryptoException {
        Digest dig;

        if (digAlg.getAlgorithm().equals(OIWObjectIdentifiers.idSHA1)) {
            dig = new SHA1Digest();
        }
        else if (digAlg.getAlgorithm().equals(NISTObjectIdentifiers.id_sha224)) {
            dig = new SHA224Digest();
        }
        else if (digAlg.getAlgorithm().equals(NISTObjectIdentifiers.id_sha256)) {
            dig = new SHA256Digest();
        }
        else if (digAlg.getAlgorithm().equals(NISTObjectIdentifiers.id_sha384)) {
            dig = new SHA384Digest();
        }
        else if (digAlg.getAlgorithm().equals(NISTObjectIdentifiers.id_sha512)) {
            dig = new SHA384Digest();
        }
        else if (digAlg.getAlgorithm().equals(PKCSObjectIdentifiers.md5)) {
            dig = new MD5Digest();
        }
        else if (digAlg.getAlgorithm().equals(PKCSObjectIdentifiers.md4)) {
            dig = new MD4Digest();
        }
        else {
            throw new CryptoException("Cannot recognize digest.");
        }

        return dig;
    }
}
