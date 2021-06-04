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

import static org.candlepin.util.DERUtil.GENERALIZED_TIME_TAG_NUM;
import static org.candlepin.util.DERUtil.SEQUENCE_TAG_NUM;
import static org.candlepin.util.DERUtil.UTC_TIME_TAG_NUM;
import static org.candlepin.util.DERUtil.findHeaderBytesDelta;
import static org.candlepin.util.DERUtil.readFullyAndTrack;
import static org.candlepin.util.DERUtil.readLength;
import static org.candlepin.util.DERUtil.readTag;
import static org.candlepin.util.DERUtil.readTagNumber;
import static org.candlepin.util.DERUtil.writeBytes;
import static org.candlepin.util.DERUtil.writeLength;
import static org.candlepin.util.DERUtil.writeTag;
import static org.candlepin.util.DERUtil.writeValue;

import org.candlepin.pki.impl.JSSPKIUtility;

import com.google.common.io.ByteStreams;

import org.mozilla.jss.asn1.ASN1Util;
import org.mozilla.jss.asn1.ASN1Value;
import org.mozilla.jss.asn1.BIT_STRING;
import org.mozilla.jss.asn1.GeneralizedTime;
import org.mozilla.jss.asn1.INTEGER;
import org.mozilla.jss.asn1.InvalidBERException;
import org.mozilla.jss.asn1.OBJECT_IDENTIFIER;
import org.mozilla.jss.asn1.OCTET_STRING;
import org.mozilla.jss.asn1.SEQUENCE;
import org.mozilla.jss.asn1.UTCTime;
import org.mozilla.jss.netscape.security.util.DerInputStream;
import org.mozilla.jss.netscape.security.util.DerValue;
import org.mozilla.jss.netscape.security.x509.AlgorithmId;
import org.mozilla.jss.netscape.security.x509.AuthorityKeyIdentifierExtension;
import org.mozilla.jss.netscape.security.x509.CRLExtensions;
import org.mozilla.jss.netscape.security.x509.CRLNumberExtension;
import org.mozilla.jss.netscape.security.x509.CRLReasonExtension;
import org.mozilla.jss.netscape.security.x509.Extension;
import org.mozilla.jss.netscape.security.x509.PKIXExtensions;
import org.mozilla.jss.netscape.security.x509.RevocationReason;
import org.mozilla.jss.netscape.security.x509.RevokedCertImpl;
import org.mozilla.jss.netscape.security.x509.RevokedCertificate;
import org.mozilla.jss.netscape.security.x509.X500Name;
import org.mozilla.jss.netscape.security.x509.X509CRLImpl;
import org.mozilla.jss.netscape.security.x509.X509ExtensionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CRLException;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of X509CRLStreamWriter using JSS crypto provider.
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
public class JSSX509CRLStreamWriter extends AbstractX509CRLStreamWriter {
    public static final Logger log = LoggerFactory.getLogger(JSSX509CRLStreamWriter.class);

    private List<RevokedCertificate> newEntries;
    private Set<BigInteger> deletedEntries;

    private AtomicInteger count;

    private int deletedEntriesLength;
    private RSAPrivateKey key;
    private AuthorityKeyIdentifierExtension aki;

    private int oldSigLength;

    private boolean emptyCrl;

    private int extensionsDelta;
    private byte[] newExtensions;

    public JSSX509CRLStreamWriter(File crlToChange, RSAPrivateKey key, X509Certificate ca)
        throws IOException, InvalidBERException {
        this(new BufferedInputStream(new FileInputStream(crlToChange)), key, ca);
    }

    public JSSX509CRLStreamWriter(InputStream crlToChange, RSAPrivateKey key, X509Certificate ca)
        throws IOException, InvalidBERException {
        this(crlToChange, key, JSSPKIUtility.buildAuthorityKeyIdentifier(ca));
    }

    public JSSX509CRLStreamWriter(File crlToChange, RSAPrivateKey key, RSAPublicKey pubKey)
        throws IOException {
        this(new BufferedInputStream(new FileInputStream(crlToChange)), key, pubKey);
    }

    public JSSX509CRLStreamWriter(InputStream crlToChange, RSAPrivateKey key, RSAPublicKey pubKey)
        throws IOException {
        this(crlToChange, key, JSSPKIUtility.buildAuthorityKeyIdentifier(pubKey));
    }

    public JSSX509CRLStreamWriter(InputStream crlToChange,
        RSAPrivateKey key, AuthorityKeyIdentifierExtension aki) {
        this.deletedEntries = new HashSet<>();
        this.deletedEntriesLength = 0;

        this.newEntries = new LinkedList<>();
        this.crlIn = crlToChange;

        this.count = new AtomicInteger();

        this.key = key;
        this.aki = aki;
    }

    @Override
    public X509CRLStreamWriter preScan(InputStream crlToChange, CRLEntryValidator validator)
        throws IOException {

        if (locked) {
            throw new IllegalStateException("Cannot modify a locked stream.");
        }

        if (preScanned) {
            throw new IllegalStateException("preScan has already been run.");
        }

        try (X509CRLEntryStream reaperStream = new JSSX509CRLEntryStream(crlToChange)) {
            if (!reaperStream.hasNext()) {
                emptyCrl = true;
                preScanned = true;
                return this;
            }

            while (reaperStream.hasNext()) {
                X509CRLEntry entry = reaperStream.next();
                if (validator != null && validator.shouldDelete(entry)) {
                    try {
                        deletedEntries.add(entry.getSerialNumber());
                        deletedEntriesLength += entry.getEncoded().length;
                    }
                    catch (CRLException e) {
                        throw new IOException("Encoding failure", e);
                    }
                }
            }

            /* At this point, crlToChange is at the point where the crlExtensions would
             * be.  RFC 5280 says that "Conforming CRL issuers are REQUIRED to include
             * the authority key identifier (Section 5.2.1) and the CRL number (Section 5.2.3)
             * extensions in all CRLs issued.
             */
            byte[] oldExtensions = null;

            byte[] remainingCrl = ByteStreams.toByteArray(crlToChange);
            DerInputStream derIn = new DerInputStream(remainingCrl);

            while (derIn.available() > 0) {
                int tag = derIn.peekByte();
                if (tag == DerValue.tag_Sequence) {
                    /* Now we are at the signatureAlgorithm sequence.  The sequence can have 1 or 2 items.
                     * The first is a mandatory OID identifying the signing algorithm; the second is a blob
                     * of data comprising any parameters the algorithm needs.  We'll check the first item
                     * in the sequence to make sure it's an OID and then send the entire sequence into
                     * AlgorithmId.parse. */
                    DerValue o = derIn.getDerValue();
                    if (o.toDerInputStream().peekByte() == DerValue.tag_ObjectId) {
                        // It's possible an algorithm has already been set using setSigningAlgorithm()
                        if (signingAlg == null) {
                            signingAlg = AlgorithmId.parse(o).getName();
                        }

                        this.signer = createContentSigner(signingAlg, key);
                    }
                }
                else if (tag == DerValue.tag_BitString) {
                    oldSigLength = derIn.getDerValue().toByteArray().length;
                }
                else {
                    if (oldExtensions != null) {
                        throw new IllegalStateException("Already read in CRL extensions");
                    }
                    oldExtensions = derIn.getDerValue().toByteArray();
                }
            }

            if (oldExtensions == null) {
                /* v1 CRLs (defined in RFC 1422) don't require extensions but all new
                 * CRLs should be v2 (defined in RFC 5280).  In the extremely unlikely
                 * event that someone is working with a v1 CRL, we handle it here although
                 * we print a warning. */
                preScanned = true;
                newExtensions = null;
                extensionsDelta = 0;
                log.warn("The CRL you are modifying is a version 1 CRL." +
                    " Please investigate moving to a version 2 CRL by adding the CRL Number" +
                    " and Authority Key Identifier extensions.");
                return this;
            }
            newExtensions = updateExtensions(oldExtensions);
            // newExtension and oldExtensions have already been converted to DER so any difference
            // in the length of the L bytes will be accounted for in the overall difference between
            // the length of the two byte arrays.
            extensionsDelta = newExtensions.length - oldExtensions.length;
        }
        preScanned = true;
        return this;
    }


    @Override
    public void add(BigInteger serial, Date date, int reason) throws IOException {
        if (locked) {
            throw new IllegalStateException("Cannot add to a locked stream.");
        }

        CRLReasonExtension reasonExt = new CRLReasonExtension(RevocationReason.fromInt(reason));

        CRLExtensions exts = new CRLExtensions();
        exts.add(reasonExt);
        newEntries.add(new RevokedCertImpl(serial, date, exts));
    }

    @Override
    public void setSigningAlgorithm(String algorithm) {
        if (locked) {
            throw new IllegalStateException("This stream is already locked.");
        }

        if (!algorithm.toLowerCase().contains("rsa")) {
            throw new IllegalArgumentException("Only RSA is supported");
        }

        signingAlg = algorithm;
    }

    @Override
    public boolean hasChangesQueued() {
        return this.newEntries.size() > 0 || this.deletedEntries.size() > 0;
    }

    @Override
    protected void writeToEmptyCrl(OutputStream out) throws IOException {
        byte[] oldCrlbytes = ByteStreams.toByteArray(crlIn);
        Date nextUpdate;
        CRLExtensions newExts;
        X500Name issuer;

        try {
            X509CRLImpl oldCrl = new X509CRLImpl(oldCrlbytes);

            issuer = new X500Name(oldCrl.getIssuerX500Principal().getEncoded());

            if (signingAlg == null) {
                signingAlg = oldCrl.getSigAlgName();
            }

            Date now = new Date();
            Date oldNextUpdate = oldCrl.getNextUpdate();
            Date oldThisUpdate = oldCrl.getThisUpdate();

            nextUpdate = new Date(now.getTime() + (oldNextUpdate.getTime() - oldThisUpdate.getTime()));

            newExts = new CRLExtensions();
            for (Extension ext : oldCrl.getExtensions()) {
                if (ext.getExtensionId().equals(PKIXExtensions.CRLNumber_Id)) {
                    CRLNumberExtension crlNumExt = (CRLNumberExtension) ext;
                    BigInteger crlNum = (BigInteger) crlNumExt.get(CRLNumberExtension.NUMBER);
                    newExts.add(new CRLNumberExtension(ext.isCritical(), crlNum.add(BigInteger.ONE)));
                }
                else if (ext.getExtensionId().equals(PKIXExtensions.AuthorityKey_Id)) {
                    aki.setCritical(ext.isCritical());
                    newExts.add(aki);
                }
                else {
                    newExts.add(ext);
                }
            }
        }
        catch (GeneralSecurityException e) {
            throw new IOException("Could not decode old CRL", e);
        }

        try {
            X509CRLImpl newCrl = new X509CRLImpl(
                issuer,
                new Date(),
                nextUpdate,
                newEntries.toArray(new RevokedCertificate[] {}),
                newExts
            );

            newCrl.sign(key, signingAlg);
            out.write(newCrl.getEncoded());
        }
        catch (GeneralSecurityException e) {
            throw new IOException("Could not encode new CRL", e);
        }
    }

    @Override
    public void write(OutputStream out) throws IOException {
        if (!locked || !preScanned) {
            throw new IllegalStateException("The instance must be preScanned and locked before writing.");
        }

        if (emptyCrl) {
            /* An empty CRL is going to be missing the revokedCertificates sequence
             * and would require a lot of special casing during the streaming process.
             * Instead, it is easier to construct the CRL in the normal fashion using
             * BouncyCastle.  Performance should be acceptable as long as the number of
             * CRL entries being added are reasonable in number.  Something less than a
             * thousand or so should yield adequate performance.
             */
            writeToEmptyCrl(out);
            return;
        }

        Integer originalLength = handleHeader(out);

        int tag;
        int tagNo;
        int length;

        while (originalLength > count.get()) {
            tag = readTag(crlIn, count);
            tagNo = readTagNumber(crlIn, tag, count);
            length = readLength(crlIn, count);
            byte[] entryBytes = new byte[length];
            readFullyAndTrack(crlIn, entryBytes, count);

            // At this point we're in the V of the CRLEntries TLV.  We rebuild it and then read the first
            // sequence item which is the serial number.
            DerValue rebuiltEntry = new DerValue(DerValue.tag_Sequence, entryBytes);
            DerValue[] entryItems = new DerInputStream(rebuiltEntry.toByteArray()).getSequence(3);
            BigInteger serial = entryItems[0].getInteger().toBigInteger();

            if (deletedEntriesLength == 0 || !deletedEntries.contains(serial)) {
                writeTag(out, tag, tagNo, signer);
                writeLength(out, length, signer);
                writeValue(out, entryBytes, signer);
            }
        }

        // Write the new entries into the new CRL
        for (X509CRLEntry entry : newEntries) {
            try {
                writeBytes(out, entry.getEncoded(), signer);
            }
            catch (CRLException e) {
                throw new IOException("Could not encode entry with serial" + entry.getSerialNumber(), e);
            }
        }

        // Copy the old extensions over
        try {
            if (newExtensions != null) {
                out.write(newExtensions);
                signer.update(newExtensions, 0, newExtensions.length);
            }
            out.write(AlgorithmId.get(signingAlg).encode());

            byte[] signature = signer.sign();
            BIT_STRING signatureBits = new BIT_STRING(signature, 0);
            out.write(ASN1Util.encode(signatureBits));
        }
        catch (SignatureException | NoSuchAlgorithmException e) {
            throw new IOException("Could not sign", e);
        }
    }

    @Override
    protected byte[] updateExtensions(byte[] obj) throws IOException {
        DerInputStream derIn = new DerInputStream(obj);
        DerValue taggedValue = derIn.getDerValue();

        /* At this point we are at the
         * crlExtensions [0]  EXPLICIT Extensions OPTIONAL
         *     -- if present, version MUST be v2
         * }
         * portion of the ASN.1.  The [0] is a constructed, application-specific type.
         * If we don't find that byte (which is encoded as 0xA0) then abort.
         */
        if (!(taggedValue.isConstructed() && taggedValue.isContextSpecific((byte) 0))) {
            throw new IOException("Could not read CRL extensions");
        }
        SEQUENCE modifiedExts = new SEQUENCE();

        try {
            CRLExtensions exts = new CRLExtensions(taggedValue.data);
            for (Extension e : exts) {
                if (e.getExtensionId().equals(PKIXExtensions.CRLNumber_Id)) {
                    DerValue crlNum = new DerValue(e.getExtensionValue());
                    BigInteger newCrlNum = crlNum.getInteger().toBigInteger().add(BigInteger.ONE);

                    modifiedExts.addElement(new org.mozilla.jss.pkix.cert.Extension(
                        new OBJECT_IDENTIFIER(e.getExtensionId().toString()), e.isCritical(),
                        new OCTET_STRING(ASN1Util.encode(new INTEGER(newCrlNum)))));
                }
                else if (e.getExtensionId().equals(PKIXExtensions.AuthorityKey_Id)) {
                    modifiedExts.addElement(new org.mozilla.jss.pkix.cert.Extension(
                        new OBJECT_IDENTIFIER(e.getExtensionId().toString()),
                        e.isCritical(),
                        new OCTET_STRING(aki.getExtensionValue())));
                }
                else {
                    modifiedExts.addElement(new org.mozilla.jss.pkix.cert.Extension(
                        new OBJECT_IDENTIFIER(e.getExtensionId().toString()),
                        e.isCritical(),
                        new OCTET_STRING(e.getExtensionValue())));
                }
            }
        }
        catch (CRLException | X509ExtensionException e) {
            throw new IOException("Could not read CRL extensions", e);
        }

        DerValue wrappedExts = new DerValue((byte) 0xA0, ASN1Util.encode(modifiedExts));
        return wrappedExts.toByteArray();
    }

    @Override
    @SuppressWarnings("checkstyle:methodlength")
    protected int handleHeader(OutputStream out) throws IOException {
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
        int newSigLength = ASN1Util.encode(new BIT_STRING(dummySig, 0)).length;

        int addedEntriesLength = 0;
        for (X509CRLEntry s : newEntries) {
            try {
                addedEntriesLength += s.getEncoded().length;
            }
            catch (CRLException e) {
                throw new IOException("Unable to encode entry " + s.getSerialNumber(), e);
            }
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

        int tagNo;
        Date oldThisUpdate;
        boolean signatureUnchanged = true;
        while (true) {
            int tag = readTag(crlIn, null);
            tagNo = readTagNumber(crlIn, tag, null);

            // The signatureAlgorithm in TBSCertList is the first sequence.  We'll hit it, replace it, and
            // then not worry with other sequences.
            if (tagNo == SEQUENCE_TAG_NUM && signatureUnchanged) {
                try {
                    AlgorithmId algorithmId = AlgorithmId.get(signingAlg);
                    readAndReplaceSignatureAlgorithm(temp, algorithmId.encode());
                    signatureUnchanged = false;
                }
                catch (NoSuchAlgorithmException e) {
                    throw new IOException("Could not find algorithm: " + signingAlg, e);
                }
            }
            else if (tagNo == GENERALIZED_TIME_TAG_NUM || tagNo == UTC_TIME_TAG_NUM) {
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

        if (tagNo == GENERALIZED_TIME_TAG_NUM || tagNo == UTC_TIME_TAG_NUM) {
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
            revokedCertsHeaderBytesDelta +
            extensionsDelta;
        int newTbsLength = oldTbsLength + tbsCertListLengthDelta;
        int tbsHeaderBytesDelta = findHeaderBytesDelta(oldTbsLength, newTbsLength);

        // newSigLength represents a DER encoded signature so it already contains the header bytes delta.
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
        try {
            signer.update(header, 0, header.length);
        }
        catch (SignatureException e) {
            throw new IOException("Could not update signer", e);
        }

        writeLength(out, newRevokedCertsLength, signer);
        return oldRevokedCertsLength;
    }

    @Override
    protected void offsetNextUpdate(OutputStream out, int tagNo, Date oldThisUpdate) throws IOException {
        int originalLength = readLength(crlIn, null);
        byte[] oldBytes = new byte[originalLength];
        readFullyAndTrack(crlIn, oldBytes, null);

        Date oldNextUpdate;
        if (tagNo == UTC_TIME_TAG_NUM) {
            DerValue rebuiltOldTime = new DerValue(DerValue.tag_UtcTime, oldBytes);
            oldNextUpdate = new DerInputStream(rebuiltOldTime.toByteArray()).getUTCTime();
        }
        else {
            DerValue rebuiltOldTime = new DerValue(DerValue.tag_GeneralizedTime, oldBytes);
            oldNextUpdate = new DerInputStream(rebuiltOldTime.toByteArray()).getGeneralizedTime();
        }

        /* Determine the time between the old thisUpdate and old nextUpdate and add it
        /* to the new nextUpdate. */
        long delta = oldNextUpdate.getTime() - oldThisUpdate.getTime();
        Date newNextUpdate = new Date(new Date().getTime() + delta);

        ASN1Value newTime;
        if (tagNo == UTC_TIME_TAG_NUM) {
            newTime = new UTCTime(newNextUpdate);
        }
        else {
            newTime = new GeneralizedTime(newNextUpdate);
        }
        writeNewTime(out, ASN1Util.encode(newTime), originalLength);
    }

    @Override
    protected Date readAndReplaceTime(OutputStream out, int tagNo) throws IOException {
        int originalLength = readLength(crlIn, null);
        byte[] oldBytes = new byte[originalLength];
        readFullyAndTrack(crlIn, oldBytes, null);

        Date oldTime;
        ASN1Value newTime;
        if (tagNo == UTC_TIME_TAG_NUM) {
            /* Since we have already read the tag, we need to rebuild the time into a valid TLV.  Then we
             * need to parse that TLV and get the actual time from it.  Doing this is pretty clunky in JSS
             * but I couldn't find a more elegant way to do it with the APIs available. */
            DerValue rebuiltOldTime = new DerValue(DerValue.tag_UtcTime, oldBytes);
            oldTime = new DerInputStream(rebuiltOldTime.toByteArray()).getUTCTime();
            newTime = new UTCTime(new Date());  //new DERUTCTime(new Date());
        }
        else {
            DerValue rebuiltOldTime = new DerValue(DerValue.tag_GeneralizedTime, oldBytes);
            oldTime = new DerInputStream(rebuiltOldTime.toByteArray()).getGeneralizedTime();
            newTime = new GeneralizedTime(new Date());
        }

        writeNewTime(out, ASN1Util.encode(newTime), originalLength);

        return oldTime;
    }

    @Override
    protected Signature createContentSigner(String signingAlg, PrivateKey key) throws
        IOException {
        try {
            /* Don't use the JSS provider here.  It wants a org.mozilla.jss.crypto.PrivateKey which has to be
             * created through a token (PKCS11 or otherwise). */
            Signature s = Signature.getInstance(signingAlg);
            s.initSign(key);
            return s;
        }
        catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IOException("Could not create Signature for " + signingAlg, e);
        }
    }
}
