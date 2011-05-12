/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.pki.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.asn1.misc.NetscapeCertType;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.x509.X509V2CRLGenerator;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.bouncycastle.x509.extension.AuthorityKeyIdentifierStructure;
import org.fedoraproject.candlepin.pki.PKIReader;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.fedoraproject.candlepin.pki.SubjectKeyIdentifierWriter;
import org.fedoraproject.candlepin.pki.X509CRLEntryWrapper;
import org.fedoraproject.candlepin.pki.X509ExtensionWrapper;
import org.fedoraproject.candlepin.util.Util;

import com.google.inject.Inject;

/**
 * The default {@link PKIUtility} for Candlepin.
 * This class implements methods to create X509 Certificates, X509 CRLs, encode
 * objects in PEM format (for saving to the db or sending to the client), and
 * decode raw ASN.1 DER values (as read from a Certificate/CRL).
 * 
 * All code that imports bouncycastle should live either in this module,
 * or in {@link BouncyCastlePKIReader}
 * 
 * (March 24, 2011) Notes on implementing a PKIUtility with NSS/JSS:
 * 
 * JSS provides classes and functions to generate X509Certificates (see CertificateInfo,
 * for example).
 * 
 * PEM encoding requires us to determine the object type (which we know), add the correct
 * header and footer to the output, base64 encode the DER for the object, and line wrap
 * the base64 encoding.
 * 
 * decodeDERValue should be simple, as JSS provides code to parse ASN.1, but I wasn't
 * able to get it to work.
 * 
 * The big one is CRL generation. JSS has no code to generate CRLs in any format. We'll
 * have to use the raw ASN.1 libraries to build up our own properly formatted CRL DER
 * representation, then PEM encode it. 
 * 
 * See also {@link BouncyCastlePKIReader} for more notes on using NSS/JSS, and a note
 * about not using bouncycastle as the JSSE provider.
 */
public class BouncyCastlePKIUtility extends PKIUtility {
    protected static Logger log = Logger.getLogger(BouncyCastlePKIUtility.class);

    @Inject
    public BouncyCastlePKIUtility(PKIReader reader,
        SubjectKeyIdentifierWriter subjectKeyWriter) {
        super(reader, subjectKeyWriter);
    }

    @Override
    public X509Certificate createX509Certificate(String dn,
        Set<X509ExtensionWrapper> extensions, Date startDate, Date endDate,
        KeyPair clientKeyPair, BigInteger serialNumber, String alternateName)
        throws GeneralSecurityException, IOException {

        X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();
        X509Certificate caCert = reader.getCACert();
        // set cert fields
        certGen.setSerialNumber(serialNumber);
        certGen.setIssuerDN(caCert.getSubjectX500Principal());
        certGen.setNotBefore(startDate);
        certGen.setNotAfter(endDate);

        X500Principal subjectPrincipal = new X500Principal(dn);
        certGen.setSubjectDN(subjectPrincipal);
        certGen.setPublicKey(clientKeyPair.getPublic());
        certGen.setSignatureAlgorithm(SIGNATURE_ALGO);

        // set key usage - required for proper x509 function
        KeyUsage keyUsage = new KeyUsage(KeyUsage.digitalSignature |
            KeyUsage.keyEncipherment | KeyUsage.dataEncipherment);

        // add SSL extensions - required for proper x509 function
        NetscapeCertType certType = new NetscapeCertType(
            NetscapeCertType.sslClient | NetscapeCertType.smime);

        certGen.addExtension(MiscObjectIdentifiers.netscapeCertType.toString(),
            false, certType);
        certGen.addExtension(X509Extensions.KeyUsage.toString(), false,
            keyUsage);

        certGen.addExtension(X509Extensions.AuthorityKeyIdentifier, false,
            new AuthorityKeyIdentifierStructure(caCert));
        certGen.addExtension(X509Extensions.SubjectKeyIdentifier, false,
              subjectKeyWriter.getSubjectKeyIdentifier(clientKeyPair, extensions));
        certGen.addExtension(X509Extensions.ExtendedKeyUsage, false,
            new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));

        // Add an alternate name if provided
        if (alternateName != null) {
            GeneralName name = new GeneralName(GeneralName.directoryName,
                "CN=" + alternateName);
            certGen.addExtension(X509Extensions.SubjectAlternativeName, false,
                new GeneralNames(name));
        }

        if (extensions != null) {
            for (X509ExtensionWrapper wrapper : extensions) {
                certGen.addExtension(wrapper.getOid(), wrapper.isCritical(),
                    new DERUTF8String(wrapper.getValue()));
            }
        }

        // Generate the certificate
        return certGen.generate(reader.getCaKey());
    }

    @Override
    public X509CRL createX509CRL(List<X509CRLEntryWrapper> entries, BigInteger crlNumber) {
        
        try {
            X509Certificate caCert = reader.getCACert();
            X509V2CRLGenerator generator = new X509V2CRLGenerator();
            generator.setIssuerDN(caCert.getIssuerX500Principal());
            generator.setThisUpdate(new Date());
            generator.setNextUpdate(Util.tomorrow());
            generator.setSignatureAlgorithm(SIGNATURE_ALGO);
            //add all the crl entries.
            for (X509CRLEntryWrapper entry : entries) {
                generator.addCRLEntry(entry.getSerialNumber(), entry.getRevocationDate(),
                    CRLReason.privilegeWithdrawn);
            }
            log.info("Completed adding CRL numbers to the certificate.");
            generator.addExtension(X509Extensions.AuthorityKeyIdentifier,
                false, new AuthorityKeyIdentifierStructure(caCert));
            generator.addExtension(X509Extensions.CRLNumber, false,
                new CRLNumber(crlNumber));
            return generator.generate(reader.getCaKey());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private byte[] getPemEncoded(Object obj) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        OutputStreamWriter oswriter = new OutputStreamWriter(byteArrayOutputStream);
        PEMWriter writer = new PEMWriter(oswriter);
        writer.writeObject(obj);
        writer.close();
        return byteArrayOutputStream.toByteArray();
    }
    
    @Override
    public byte[] getPemEncoded(X509Certificate cert) throws IOException {
        return getPemEncoded((Object) cert);
    }
    
    @Override    
    public byte[] getPemEncoded(Key key) throws IOException {
        return getPemEncoded((Object) key);
    }
    
    @Override
    public byte[] getPemEncoded(X509CRL crl) throws IOException {
        return getPemEncoded((Object) crl);
    }
    
    @Override
    public String decodeDERValue(byte[] value) {
        ASN1InputStream vis = null;
        ASN1InputStream decoded = null;
        try {
            vis = new ASN1InputStream(value);
            decoded = new ASN1InputStream(
                ((DEROctetString) vis.readObject()).getOctets());
    
            return decoded.readObject().toString();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            if (vis != null) {
                try {
                    vis.close();
                }
                catch (IOException e) {
                    log.warn("failed to close ASN1 stream", e);
                }
            }
    
            if (decoded != null) {
                try {
                    decoded.close();
                }
                catch (IOException e) {
                    log.warn("failed to close ASN1 stream", e);
                }
            }
        }
    }
}
