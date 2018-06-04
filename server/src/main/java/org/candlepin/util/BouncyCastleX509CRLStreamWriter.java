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

import static org.candlepin.util.DERUtil.*;

import org.candlepin.pki.impl.BouncyCastleProviderLoader;

import org.bouncycastle.asn1.ASN1BitString;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1GeneralizedTime;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.ASN1UTCTime;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERGeneralizedTime;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERUTCTime;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.CertificateList;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultAlgorithmNameFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CRLException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of X509CRLStreamWriter using BouncyCastle crypto provider.  This class <em>is not
 * thread-safe</em>.
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
public class BouncyCastleX509CRLStreamWriter extends AbstractX509CRLStreamWriter {
    public static final Logger log = LoggerFactory.getLogger(BouncyCastleX509CRLStreamWriter.class);

    private List<DERSequence> newEntries;
    private Set<BigInteger> deletedEntries;

    private AtomicInteger count;

    private int deletedEntriesLength;
    private RSAPrivateKey key;
    private AuthorityKeyIdentifier aki;

    private int oldSigLength;

    private boolean emptyCrl;

    private int extensionsDelta;
    private byte[] newExtensions;

    public BouncyCastleX509CRLStreamWriter(File crlToChange, RSAPrivateKey key, X509Certificate ca)
        throws IOException, NoSuchAlgorithmException, CertificateEncodingException {
        this(new BufferedInputStream(new FileInputStream(crlToChange)), key, ca);
    }

    public BouncyCastleX509CRLStreamWriter(InputStream crlToChange, RSAPrivateKey key, X509Certificate ca)
        throws NoSuchAlgorithmException, CertificateEncodingException {
        this(crlToChange, key, new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(ca));
    }

    public BouncyCastleX509CRLStreamWriter(File crlToChange, RSAPrivateKey key, RSAPublicKey pubKey)
        throws IOException, NoSuchAlgorithmException {
        this(new BufferedInputStream(new FileInputStream(crlToChange)), key, pubKey);
    }

    public BouncyCastleX509CRLStreamWriter(InputStream crlToChange, RSAPrivateKey key, RSAPublicKey pubKey)
        throws NoSuchAlgorithmException {
        this(crlToChange, key, new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(pubKey));
    }

    public BouncyCastleX509CRLStreamWriter(InputStream crlToChange, RSAPrivateKey key,
        AuthorityKeyIdentifier aki) {
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

        try (X509CRLEntryStream reaperStream = new BouncyCastleX509CRLEntryStream(crlToChange)) {
            if (!reaperStream.hasNext()) {
                emptyCrl = true;
                preScanned = true;
                return this;
            }

            while (reaperStream.hasNext()) {
                X509CRLEntry entry = reaperStream.next();
                if (validator != null && validator.shouldDelete(entry)) {
                    deletedEntries.add(entry.getSerialNumber());
                    deletedEntriesLength += entry.getEncoded().length;
                }
            }

            /* At this point, crlToChange is at the point where the crlExtensions would
             * be.  RFC 5280 says that "Conforming CRL issuers are REQUIRED to include
             * the authority key identifier (Section 5.2.1) and the CRL number (Section 5.2.3)
             * extensions in all CRLs issued.
             */
            byte[] oldExtensions = null;
            ASN1Primitive o;
            ASN1InputStream asn1In = new ASN1InputStream(crlToChange);
            while ((o = asn1In.readObject()) != null) {
                if (o instanceof ASN1Sequence) {
                    // Now we are at the signatureAlgorithm
                    ASN1Sequence seq = (ASN1Sequence) o;
                    if (seq.getObjectAt(0) instanceof ASN1ObjectIdentifier) {
                        // It's possible an algorithm has already been set using setSigningAlgorithm()
                        if (signingAlg == null) {
                            signingAlg = new DefaultAlgorithmNameFinder()
                                .getAlgorithmName(AlgorithmIdentifier.getInstance(seq));
                        }
                        this.signer = createContentSigner(signingAlg, key);
                    }
                }
                else if (o instanceof ASN1BitString) {
                    oldSigLength = o.getEncoded().length;
                }
                else {
                    if (oldExtensions != null) {
                        throw new IllegalStateException("Already read in CRL extensions.");
                    }
                    oldExtensions = o.getEncoded();
                }
            }

            if (oldExtensions == null) {
                /* v1 CRLs (defined in RFC 1422) don't require extensions but all new
                 * CRLs should be v2 (defined in RFC 5280).  In the extremely unlikely
                 * event that someone is working with a v1 CRL, we handle it here although
                 * we print a warning.
                 */
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
        catch (CRLException e) {
            throw new IOException("Could not build TBSCertList.CRLEntry", e);
        }
        preScanned = true;
        return this;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void add(BigInteger serial, Date date, int reason) throws IOException {
        if (locked) {
            throw new IllegalStateException("Cannot add to a locked stream.");
        }

        ASN1EncodableVector v = new ASN1EncodableVector();

        v.add(new ASN1Integer(serial));
        v.add(new Time(date));

        CRLReason crlReason = CRLReason.getInstance(new ASN1Enumerated(reason));
        ExtensionsGenerator generator = new ExtensionsGenerator();
        generator.addExtension(Extension.reasonCode, false, crlReason);
        v.add(generator.generate());

        newEntries.add(new DERSequence(v));
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
        try (ASN1InputStream asn1in = new ASN1InputStream(crlIn)) {
            ASN1Sequence certListSeq = (ASN1Sequence) asn1in.readObject();
            CertificateList certList = CertificateList.getInstance(certListSeq);
            X509CRLHolder oldCrl = new X509CRLHolder(certList);

            X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(oldCrl.getIssuer(), new Date());
            crlBuilder.addCRL(oldCrl);

            Date now = new Date();
            Date oldNextUpdate = certList.getNextUpdate().getDate();
            Date oldThisUpdate = certList.getThisUpdate().getDate();

            Date nextUpdate = new Date(now.getTime() + (oldNextUpdate.getTime() - oldThisUpdate.getTime()));
            crlBuilder.setNextUpdate(nextUpdate);

            for (Object o : oldCrl.getExtensionOIDs()) {
                ASN1ObjectIdentifier oid = (ASN1ObjectIdentifier) o;
                Extension ext = oldCrl.getExtension(oid);

                if (oid.equals(Extension.cRLNumber)) {
                    ASN1OctetString octet = ext.getExtnValue();
                    ASN1Integer currentNumber = (ASN1Integer) new ASN1InputStream(octet.getOctets())
                        .readObject();
                    ASN1Integer nextNumber = new ASN1Integer(currentNumber.getValue().add(BigInteger.ONE));

                    crlBuilder.addExtension(oid, ext.isCritical(), nextNumber);
                }
                else if (oid.equals(Extension.authorityKeyIdentifier)) {
                    crlBuilder.addExtension(oid, ext.isCritical(), aki.toASN1Primitive());
                }
                else {
                    crlBuilder.addExtension(ext.getExtnId(), ext.isCritical(), ext.getParsedValue());
                }
            }

            for (DERSequence entry : newEntries) {
                // XXX: This is all a bit messy considering the user already passed in the serial, date
                // and reason.
                BigInteger serial = ((ASN1Integer) entry.getObjectAt(0)).getValue();
                Date revokeDate = ((Time) entry.getObjectAt(1)).getDate();
                int reason = CRLReason.unspecified;
                if (entry.size() == 3) {
                    Extensions extensions = (Extensions) entry.getObjectAt(2);
                    Extension reasonExt = extensions.getExtension(Extension.reasonCode);

                    if (reasonExt != null) {
                        reason = ((ASN1Enumerated) reasonExt.getParsedValue()).getValue().intValue();
                    }
                }
                crlBuilder.addCRLEntry(serial, revokeDate, reason);
            }

            if (signingAlg == null) {
                signingAlg = new DefaultAlgorithmNameFinder()
                    .getAlgorithmName(oldCrl.toASN1Structure().getSignatureAlgorithm());
            }

            try {
                ContentSigner s = new JcaContentSignerBuilder(signingAlg).build(key);
                X509CRLHolder newCrl = crlBuilder.build(s);
                out.write(newCrl.getEncoded());
            }
            catch (OperatorCreationException e) {
                throw new IOException("Could not sign CRL", e);
            }
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

            // We only need the serial number and not the rest of the stuff in the entry
            ASN1Integer serial = (ASN1Integer) new ASN1InputStream(entryBytes).readObject();

            if (deletedEntriesLength == 0 || !deletedEntries.contains(serial.getValue())) {
                writeTag(out, tag, tagNo, signer);
                writeLength(out, length, signer);
                writeValue(out, entryBytes, signer);
            }
        }

        // Write the new entries into the new CRL
        for (ASN1Sequence entry : newEntries) {
            writeBytes(out, entry.getEncoded(), signer);
        }

        // Copy the old extensions over
        try {
            if (newExtensions != null) {
                out.write(newExtensions);
                signer.update(newExtensions, 0, newExtensions.length);
            }
            out.write(new DefaultSignatureAlgorithmIdentifierFinder().find(signingAlg).getEncoded());

            byte[] signature = signer.sign();
            ASN1BitString signatureBits = new DERBitString(signature);
            out.write(signatureBits.getEncoded());
        }
        catch (DataLengthException | SignatureException e) {
            throw new IOException("Could not sign", e);
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected byte[] updateExtensions(byte[] obj) throws IOException {
        ASN1TaggedObject taggedExts = (ASN1TaggedObject) new ASN1InputStream(obj).readObject();
        ASN1Sequence seq = (ASN1Sequence) taggedExts.getObject();
        ASN1EncodableVector modifiedExts = new ASN1EncodableVector();

        // Now we need to read the extensions and find the CRL number and increment it,
        // and determine if its length changed.
        Enumeration objs = seq.getObjects();
        while (objs.hasMoreElements()) {
            ASN1Sequence ext = (ASN1Sequence) objs.nextElement();
            ASN1ObjectIdentifier oid = (ASN1ObjectIdentifier) ext.getObjectAt(0);
            if (Extension.cRLNumber.equals(oid)) {
                ASN1OctetString s = (ASN1OctetString) ext.getObjectAt(1);
                ASN1Integer i = (ASN1Integer) new ASN1InputStream(s.getOctets()).readObject();
                ASN1Integer newCrlNumber = new ASN1Integer(i.getValue().add(BigInteger.ONE));

                Extension newNumberExt = new Extension(
                    Extension.cRLNumber, false, new DEROctetString(newCrlNumber.getEncoded()));

                ASN1EncodableVector crlNumber = new ASN1EncodableVector();
                crlNumber.add(Extension.cRLNumber);
                crlNumber.add(newNumberExt.getExtnValue());
                modifiedExts.add(new DERSequence(crlNumber));
            }
            else if (Extension.authorityKeyIdentifier.equals(oid)) {
                Extension newAuthorityKeyExt = new Extension(Extension.authorityKeyIdentifier, false, aki
                    .getEncoded());

                ASN1EncodableVector aki = new ASN1EncodableVector();
                aki.add(Extension.authorityKeyIdentifier);
                aki.add(newAuthorityKeyExt.getExtnValue());
                modifiedExts.add(new DERSequence(aki));
            }
            else {
                modifiedExts.add(ext);
            }
        }

        ASN1Sequence seqOut = new DERSequence(modifiedExts);
        ASN1TaggedObject out = new DERTaggedObject(true, 0, seqOut);
        return out.getEncoded();
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
        int newSigLength = new DERBitString(dummySig).getEncoded().length;

        int addedEntriesLength = 0;
        for (ASN1Sequence s : newEntries) {
            addedEntriesLength += s.getEncoded().length;
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
                AlgorithmIdentifier algorithmId =
                    new DefaultSignatureAlgorithmIdentifierFinder().find(signingAlg);
                readAndReplaceSignatureAlgorithm(temp, algorithmId.getEncoded());
                signatureUnchanged = false;
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
    protected void offsetNextUpdate(OutputStream out, int tagNo, Date oldThisUpdate)
        throws IOException {
        int originalLength = readLength(crlIn, null);
        byte[] oldBytes = new byte[originalLength];
        readFullyAndTrack(crlIn, oldBytes, null);

        ASN1Object oldTime;
        if (tagNo == UTC_TIME_TAG_NUM) {
            ASN1TaggedObject t = new DERTaggedObject(UTC_TIME_TAG_NUM, new DEROctetString(oldBytes));
            oldTime = ASN1UTCTime.getInstance(t, false);
        }
        else {
            ASN1TaggedObject t = new DERTaggedObject(GENERALIZED_TIME_TAG_NUM, new DEROctetString(oldBytes));
            oldTime = ASN1GeneralizedTime.getInstance(t, false);
        }

        /* Determine the time between the old thisUpdate and old nextUpdate and add it
        /* to the new nextUpdate. */
        Date oldNextUpdate = Time.getInstance(oldTime).getDate();
        long delta = oldNextUpdate.getTime() - oldThisUpdate.getTime();
        Date newNextUpdate = new Date(new Date().getTime() + delta);

        ASN1Object newTime;
        if (tagNo == UTC_TIME_TAG_NUM) {
            newTime = new DERUTCTime(newNextUpdate);
        }
        else {
            newTime = new DERGeneralizedTime(newNextUpdate);
        }
        writeNewTime(out, newTime.getEncoded(), originalLength);
    }

    @Override
    protected Date readAndReplaceTime(OutputStream out, int tagNo) throws IOException {
        int originalLength = readLength(crlIn, null);
        byte[] oldBytes = new byte[originalLength];
        readFullyAndTrack(crlIn, oldBytes, null);

        ASN1Object oldTime;
        ASN1Object newTime;
        if (tagNo == UTC_TIME_TAG_NUM) {
            ASN1TaggedObject t = new DERTaggedObject(UTC_TIME_TAG_NUM, new DEROctetString(oldBytes));
            oldTime = ASN1UTCTime.getInstance(t, false);
            newTime = new DERUTCTime(new Date());
        }
        else {
            ASN1TaggedObject t = new DERTaggedObject(GENERALIZED_TIME_TAG_NUM, new DEROctetString(oldBytes));
            oldTime = ASN1GeneralizedTime.getInstance(t, false);
            newTime = new DERGeneralizedTime(new Date());
        }

        writeNewTime(out, newTime.getEncoded(), originalLength);
        return Time.getInstance(oldTime).getDate();
    }

    @Override
    protected Signature createContentSigner(String signingAlg, PrivateKey key) throws
        IOException {
        try {
            Signature s = Signature.getInstance(signingAlg, BouncyCastleProviderLoader.BC_PROVIDER);
            s.initSign(key);
            return s;
        }
        catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IOException("Could not create Signature for " + signingAlg, e);
        }
    }
}
