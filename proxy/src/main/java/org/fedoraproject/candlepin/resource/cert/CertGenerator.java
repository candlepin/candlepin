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
package org.fedoraproject.candlepin.resource.cert;

import org.bouncycastle.asn1.DEREncodable;
import org.bouncycastle.asn1.DEREnumerated;
import org.bouncycastle.asn1.misc.NetscapeCertType;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509V3CertificateGenerator;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

/**
 * CertGenerator - util class for generating a cert
 * @version $Rev$
 */
public class CertGenerator {

    private static X509V3CertificateGenerator v3CertGen =
        new X509V3CertificateGenerator();

    
    protected CertGenerator() {
        // do nothing
    }

    
    
    /**
     * returns certificate string
     * @return certificate string
     */
    public static X509Certificate genCert() {
        X509Certificate retval = null;
        Security.addProvider(new BouncyCastleProvider());
        //
        // personal keys
        //
        RSAPublicKeySpec pubKeySpec = new RSAPublicKeySpec(
            new BigInteger("b4a7e46170574f16a97082b22be58b6a2a629798419be12872a4bdba626" +
                "cfae9900f76abfb12139dce5de56564fab2b6543165a040c606887420e33d91ed7ed7",
                16), 
            new BigInteger("11", 16));

        //
        // ca keys
        //
        RSAPublicKeySpec caPubKeySpec = new RSAPublicKeySpec(
            new BigInteger(
                "b259d2d6e627a768c94be36164c2d9fc79d97aab9253140e5bf17751197731d6f7" +
                "540d2509e7b9ffee0a70a6e26d56e92d2edd7f85aba85600b69089f35f6bdbf3c2" +
                "98e05842535d9f064e6b0391cb7d306e0a2d20c4dfb4e7b49a9640bdea26c10ad6" +
                "9c3f05007ce2513cee44cfe01998e62b6c3637d3fc0391079b26ee36d5",
                16), new BigInteger("11", 16));

        RSAPrivateCrtKeySpec caPrivKeySpec = new RSAPrivateCrtKeySpec(
            new BigInteger("b259d2d6e627a768c94be36164c2d9fc79d97aab9253140e5bf17751197" +
                "731d6f7540d2509e7b9ffee0a70a6e26d56e92d2edd7f85aba85600b69089f35f6" +
                "bdbf3c298e05842535d9f064e6b0391cb7d306e0a2d20c4dfb4e7b49a9640bdea2" +
                "6c10ad69c3f05007ce2513cee44cfe01998e62b6c3637d3fc0391079b26ee36d5", 16),
            new BigInteger("11", 16),
            new BigInteger("92e08f83cc9920746989ca5034dcb384a094fb9c5a6288fcc4304424ab8" +
                "f56388f72652d8fafc65a4b9020896f2cde297080f2a540e7b7ce5af0b3446e125" +
                "8d1dd7f245cf54124b4c6e17da21b90a0ebd22605e6f45c9f136d7a13eaac1c0f7" +
                "487de8bd6d924972408ebb58af71e76fd7b012a8d0e165f3ae2e5077a8648e619", 16),
            new BigInteger("f75e80839b9b9379f1cf1128f321639757dba514642c206bbbd99f9a484" +
                "6208b3e93fbbe5e0527cc59b1d4b929d9555853004c7c8b30ee6a213c3d1bb7415" +
                "d03", 16),
            new BigInteger("b892d9ebdbfc37e397256dd8a5d3123534d1f03726284743ddc6be3a709" +
                "edb696fc40c7d902ed804c6eee730eee3d5b20bf6bd8d87a296813c87d3b3cc9d7" +
                "947", 16),
            new BigInteger("1d1a2d3ca8e52068b3094d501c9a842fec37f54db16e9a67070a8b3f53c" +
                "c03d4257ad252a1a640eadd603724d7bf3737914b544ae332eedf4f34436cac25" +
                "ceb5", 16),
            new BigInteger("6c929e4e81672fef49d9c825163fec97c4b7ba7acb26c0824638ac22605" +
                "d7201c94625770984f78a56e6e25904fe7db407099cad9b14588841b94f5ab498d" +
                "ded", 16),
            new BigInteger("dae7651ee69ad1d081ec5e7188ae126f6004ff39556bde90e0b870962fa" +
                "7b926d070686d8244fe5a9aa709a95686a104614834b0ada4b10f53197a5cb4c97" +
                "339", 16));

        //
        // set up the keys
        //
        try {
            KeyFactory fact = KeyFactory.getInstance("RSA", "BC");
            PrivateKey caPrivKey = fact.generatePrivate(caPrivKeySpec);
            PublicKey caPubKey = fact.generatePublic(caPubKeySpec);
            PublicKey pubKey = fact.generatePublic(pubKeySpec);
    
            //
            // note in this case we are using the CA certificate for both the client
            // cetificate
            // and the attribute certificate. This is to make the vcode simpler to
            // read, in practice
            // the CA for the attribute certificate should be different to that of
            // the client certificate
            //
            X509Certificate clientCert = createClientCert("Tito Walker", 
                    "dev-null@fedoraproject.org", pubKey, caPrivKey, caPubKey);
            retval = clientCert;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        return retval;
    }
    
    public static X509Certificate createConsumerIdentityCert(String name, String email,
        PublicKey pubKey, PrivateKey caPrivKey, PublicKey caPubKey) throws Exception {
        
        // FIXME: replace with something we can generate a useful cert with
        return genCert();
    }
   
    /**
     * we generate a certificate signed by our CA's intermediate certficate
     * @param name Name on certificate
     * @param email on certificate
     * @param pubKey public key
     * @param caPrivKey ca private key
     * @param caPubKey ca public key
     * @throws Exception thrown in the event of an error.
     * @return X.509 Certificate
     */
    public static X509Certificate createClientCert(String name, String email,
            PublicKey pubKey, PrivateKey caPrivKey, PublicKey caPubKey) throws Exception {
        //
        // issuer
        //
        String issuer =
            "C=AU, O=The Players of Candlepin, OU=Candlepin Primary Certificate";

        //
        // subjects name table.
        //
        Hashtable attrs = new Hashtable();
        Vector order = new Vector();

        attrs.put(X509Principal.C, "US");
        attrs.put(X509Principal.O, "The Players of Candlepin");
        attrs.put(X509Principal.L, "Raleigh");
        attrs.put(X509Principal.CN, name);
        attrs.put(X509Principal.EmailAddress,
                email);

        order.addElement(X509Principal.C);
        order.addElement(X509Principal.O);
        order.addElement(X509Principal.L);
        order.addElement(X509Principal.CN);
        order.addElement(X509Principal.EmailAddress);

        //
        // create the certificate - version 3
        //
        v3CertGen.reset();

        v3CertGen.setSerialNumber(BigInteger.valueOf(20));
        v3CertGen.setIssuerDN(new X509Principal(issuer));
        v3CertGen.setNotBefore(new Date(System.currentTimeMillis() - 1000L *
                60 * 60 * 24 * 30));
        v3CertGen.setNotAfter(new Date(System.currentTimeMillis() +
                (1000L * 60 * 60 * 24 * 30)));
        v3CertGen.setSubjectDN(new X509Principal(order, attrs));
        v3CertGen.setPublicKey(pubKey);
        v3CertGen.setSignatureAlgorithm("SHA1WithRSAEncryption");

        //
        // add the extensions
        //
        NetscapeCertType type = new NetscapeCertType(NetscapeCertType.PRINTABLE_STRING);
        DEREncodable enc = new DEREnumerated(1);
        
        //v3CertGen.addExtension(MiscObjectIdentifiers.netscapeCertComment, 
        //        true, enc);
        
        //v3CertGen.addExtension(MiscObjectIdentifiers.netscapeCertType, false,
        //        type);
        
        GeneralNames altnames = new GeneralNames(
                new GeneralName(GeneralName.rfc822Name, "mmccune@redhat.com"));
        v3CertGen.addExtension(X509Extensions.SubjectAlternativeName, false, altnames);
        
        // v3CertGen.
        
        //v3CertGen.addExtension(MiscObjectIdentifiers.netscapeCertType, false,
        //        new NetscapeCertType(NetscapeCertType.objectSigning
        //                | NetscapeCertType.smime));

        X509Certificate cert = v3CertGen.generate(caPrivKey);
        
        cert.checkValidity(new Date());

        cert.verify(caPubKey);

        return cert;
    }

}
