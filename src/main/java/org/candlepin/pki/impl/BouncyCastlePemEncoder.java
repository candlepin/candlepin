/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.pki.impl;

import org.candlepin.config.Configuration;
import org.candlepin.model.Consumer;
import org.candlepin.model.KeyPairData;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.X509Extension;

import com.google.common.base.Charsets;
import com.google.inject.Inject;

import org.apache.commons.codec.binary.Base64OutputStream;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.asn1.misc.NetscapeCertType;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.Objects;
import java.util.Set;

import javax.inject.Provider;

public class BouncyCastlePemEncoder implements PemEncoder {
    private static final Logger log = LoggerFactory.getLogger(BouncyCastlePemEncoder.class);
    private static final byte[] LINE_SEPARATOR = String.format("%n").getBytes();
    private static final String PRIVATE_KEY_PEM_NAME = "PRIVATE KEY";

    private final Provider<BouncyCastleProvider> securityProvider;
    private final KeyPairDataCurator keypairDataCurator;

    @Inject
    public BouncyCastlePemEncoder(Provider<BouncyCastleProvider> securityProvider,
        KeyPairDataCurator keypairDataCurator) {
        this.keypairDataCurator = Objects.requireNonNull(keypairDataCurator);
        this.securityProvider = Objects.requireNonNull(securityProvider);
    }

    @Override
    public byte[] getPemEncoded(X509Certificate cert) throws IOException {
        return getPemEncoded((Object) cert);
    }

    @Override
    public byte[] getPemEncoded(PrivateKey key) throws IOException {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        try {
            byte[] encoded = key.getEncoded();
            return this.getPemEncoded(encoded, PRIVATE_KEY_PEM_NAME);
        }
        catch (Exception e) {
            throw new IOException("Could not encode key", e);
        }
    }

    private void writePemEncoded(Object obj, OutputStream out) throws IOException {
        OutputStreamWriter oswriter = new OutputStreamWriter(out);
        JcaPEMWriter writer = new JcaPEMWriter(oswriter);
        writer.writeObject(obj);
        writer.flush();
        // We're hoping close does nothing more than a flush and super.close() here
    }

    private byte[] getPemEncoded(Object obj) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        this.writePemEncoded(obj, out);

        byte[] output = out.toByteArray();
        out.close();

        return output;
    }

    private byte[] getPemEncoded(byte[] der, String type) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writePemEncoded(der, out, type);
            return out.toByteArray();
        }
    }

    private void writePemEncoded(byte[] der, OutputStream out, String type) throws IOException {
        out.write(("-----BEGIN " + type + "-----\n").getBytes(Charsets.UTF_8));

        // Write base64 encoded DER.  Does not close the underlying stream.
        Base64OutputStream b64Out = new Base64OutputStream(out, true, 64, LINE_SEPARATOR);
        b64Out.write(der);
        b64Out.eof();
        b64Out.flush();
        out.write(("-----END " + type + "-----\n").getBytes(Charsets.UTF_8));
    }

}
