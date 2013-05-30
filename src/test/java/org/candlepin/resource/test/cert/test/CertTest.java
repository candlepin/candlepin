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
package org.candlepin.resource.test.cert.test;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Date;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.AttributeCertificateHolder;
import org.bouncycastle.x509.AttributeCertificateIssuer;
import org.bouncycastle.x509.X509Attribute;
import org.bouncycastle.x509.X509V2AttributeCertificate;
import org.bouncycastle.x509.X509V2AttributeCertificateGenerator;
import org.bouncycastle.x509.examples.AttrCertExample;
import org.junit.Before;
import org.junit.Test;


/**
 * CertTest
 * @version $Rev$
 */
@SuppressWarnings("deprecation")
public class CertTest {

    private RSAPublicKeySpec pubKeySpec;
    private RSAPrivateCrtKeySpec privKeySpec;
    private RSAPublicKeySpec caPubKeySpec;
    private RSAPrivateCrtKeySpec caPrivKeySpec;

    @Before
    public void setupKeys() {
        //
        // personal keys
        //
        pubKeySpec = new RSAPublicKeySpec(
            new BigInteger("b4a7e46170574f16a97082b22be58b6a2a629798419" +
                           "be12872a4bdba626cfae9900f76abfb12139dce5de5" +
                           "6564fab2b6543165a040c606887420e33d91ed7ed7", 16),
            new BigInteger("11", 16));

        privKeySpec = new RSAPrivateCrtKeySpec(
            new BigInteger("b4a7e46170574f16a97082b22be58b6a2a629798419" +
                           "be12872a4bdba626cfae9900f76abfb12139dce5de5" +
                           "6564fab2b6543165a040c606887420e33d91ed7ed7", 16),
            new BigInteger("11", 16),
            new BigInteger("9f66f6b05410cd503b2709e88115d55daced94d1a34" +
                           "d4e32bf824d0dde6028ae79c5f07b580f5dce240d71" +
                           "11f7ddb130a7945cd7d957d1920994da389f490c89", 16),
            new BigInteger("c0a0758cdf14256f78d4708c86becdead1b50ad4ad6" +
                           "c5c703e2168fbf37884cb", 16),
            new BigInteger("f01734d7960ea60070f1b06f2bb81bfac48ff192ae1" +
                           "8451d5e56c734a5aab8a5", 16),
            new BigInteger("b54bb9edff22051d9ee60f9351a48591b6500a31942" +
                           "9c069a3e335a1d6171391", 16),
            new BigInteger("d3d83daf2a0cecd3367ae6f8ae1aeb82e9ac2f816c6" +
                           "fc483533d8297dd7884cd", 16),
            new BigInteger("b8f52fc6f38593dabb661d3f50f8897f8106eee68b1" +
                           "bce78a95b132b4e5b5d19", 16));

        //
        // ca keys
        //
        caPubKeySpec = new RSAPublicKeySpec(
            new BigInteger("b259d2d6e627a768c94be36164c2d9fc79d97aab925" +
                           "3140e5bf17751197731d6f7540d2509e7b9ffee0a70" +
                           "a6e26d56e92d2edd7f85aba85600b69089f35f6bdbf" +
                           "3c298e05842535d9f064e6b0391cb7d306e0a2d20c4" +
                           "dfb4e7b49a9640bdea26c10ad69c3f05007ce2513ce" +
                           "e44cfe01998e62b6c3637d3fc0391079b26ee36d5", 16),
            new BigInteger("11", 16));

        caPrivKeySpec = new RSAPrivateCrtKeySpec(
            new BigInteger("b259d2d6e627a768c94be36164c2d9fc79d97aab925" +
                           "3140e5bf17751197731d6f7540d2509e7b9ffee0a70" +
                           "a6e26d56e92d2edd7f85aba85600b69089f35f6bdbf" +
                           "3c298e05842535d9f064e6b0391cb7d306e0a2d20c4" +
                           "dfb4e7b49a9640bdea26c10ad69c3f05007ce2513ce" +
                           "e44cfe01998e62b6c3637d3fc0391079b26ee36d5", 16),
            new BigInteger("11", 16),
            new BigInteger("92e08f83cc9920746989ca5034dcb384a094fb9c5a6" +
                           "288fcc4304424ab8f56388f72652d8fafc65a4b9020" +
                           "896f2cde297080f2a540e7b7ce5af0b3446e1258d1d" +
                           "d7f245cf54124b4c6e17da21b90a0ebd22605e6f45c" +
                           "9f136d7a13eaac1c0f7487de8bd6d924972408ebb58" +
                           "af71e76fd7b012a8d0e165f3ae2e5077a8648e619", 16),
            new BigInteger("f75e80839b9b9379f1cf1128f321639757dba514642" +
                           "c206bbbd99f9a4846208b3e93fbbe5e0527cc59b1d4" +
                           "b929d9555853004c7c8b30ee6a213c3d1bb7415d03", 16),
            new BigInteger("b892d9ebdbfc37e397256dd8a5d3123534d1f037262" +
                           "84743ddc6be3a709edb696fc40c7d902ed804c6eee7" +
                           "30eee3d5b20bf6bd8d87a296813c87d3b3cc9d7947", 16),
            new BigInteger("1d1a2d3ca8e52068b3094d501c9a842fec37f54db16" +
                           "e9a67070a8b3f53cc03d4257ad252a1a640eadd6037" +
                           "24d7bf3737914b544ae332eedf4f34436cac25ceb5", 16),
            new BigInteger("6c929e4e81672fef49d9c825163fec97c4b7ba7acb2" +
                           "6c0824638ac22605d7201c94625770984f78a56e6e2" +
                           "5904fe7db407099cad9b14588841b94f5ab498dded", 16),
            new BigInteger("dae7651ee69ad1d081ec5e7188ae126f6004ff39556" +
                           "bde90e0b870962fa7b926d070686d8244fe5a9aa709" +
                           "a95686a104614834b0ada4b10f53197a5cb4c97339", 16));
    }

 //   @Test
 //   public void testCertGenerator() {
 //       String cert = CertGenerator.getCertString();
//        System.out.println("Cert: " + cert);
 //   }

    @Test
    public void testCertExample() throws Exception {

        Security.addProvider(new BouncyCastleProvider());

        //
        // set up the keys
        //
        KeyFactory fact = KeyFactory.getInstance("RSA", "BC");
        PrivateKey caPrivKey = fact.generatePrivate(caPrivKeySpec);
        PublicKey caPubKey = fact.generatePublic(caPubKeySpec);
        //PrivateKey privKey =
        fact.generatePrivate(privKeySpec);
        PublicKey pubKey = fact.generatePublic(pubKeySpec);

        //
        // note in this case we are using the CA certificate for both the client
        // cetificate
        // and the attribute certificate. This is to make the vcode simpler to
        // read, in practice
        // the CA for the attribute certificate should be different to that of
        // the client certificate
        //
        X509Certificate caCert = AttrCertExample.createAcIssuerCert(caPubKey,
                caPrivKey);
        X509Certificate clientCert = AttrCertExample.createClientCert(pubKey,
                caPrivKey, caPubKey);
        // Instantiate a new AC generator
        X509V2AttributeCertificateGenerator acGen =
            new X509V2AttributeCertificateGenerator();

        acGen.reset();

        //
        // Holder: here we use the IssuerSerial form
        //
        acGen.setHolder(new AttributeCertificateHolder(clientCert));

        // set the Issuer
        acGen.setIssuer(new AttributeCertificateIssuer(caCert
                .getSubjectX500Principal()));

        //
        // serial number (as it's an example we don't have to keep track of the
        // serials anyway
        //
        acGen.setSerialNumber(BigInteger.ONE);

        // not Before
        acGen.setNotBefore(new Date(System.currentTimeMillis() - 50000));

        // not After
        acGen.setNotAfter(new Date(System.currentTimeMillis() + 50000));

        // signature Algorithmus
        acGen.setSignatureAlgorithm("SHA1WithRSAEncryption");

        // the actual attributes
        GeneralName roleName = new GeneralName(GeneralName.rfc822Name,
                "DAU123456789");
        ASN1EncodableVector roleSyntax = new ASN1EncodableVector();
        roleSyntax.add(roleName);

        // roleSyntax OID: 2.5.24.72
        X509Attribute attributes = new X509Attribute("2.5.24.72",
                new DERSequence(roleSyntax));

        acGen.addAttribute(attributes);

        // finally create the AC
        X509V2AttributeCertificate att = (X509V2AttributeCertificate) acGen
                .generate(caPrivKey, "BC");



        //String encoded = new String(att.getEncoded());
        //System.out.println("CERT CERT: " + encoded);
        //KeyStore store = KeyStore.getInstance("PKCS12");
        //String pass = "redhat";


        /*FileOutputStream fout = new FileOutputStream("/tmp/foo.file");
        store.load(null, null);
        store.store(fout, pass.toCharArray());
        X509CertificateObject ccert = new
            X509CertificateObject(new X509CertificateStructure(new DERSequence(att)));*/
        //
        // starting here, we parse the newly generated AC
        //

        // Holder

        AttributeCertificateHolder h = att.getHolder();
        if (h.match(clientCert)) {
            if (h.getEntityNames() != null) {
//                System.out.println(h.getEntityNames().length +
//                    " entity names found");
            }
            if (h.getIssuer() != null) {
//                System.out.println(h.getIssuer().length +
//                    " issuer names found, serial number " +
//                    h.getSerialNumber());
            }
//            System.out.println("Matches original client x509 cert");
        }

        // Issuer

        AttributeCertificateIssuer issuer = att.getIssuer();
        if (issuer.match(caCert)) {
            if (issuer.getPrincipals() != null) {
//                System.out.println(issuer.getPrincipals().length +
//                    " entity names found");
            }
//            System.out.println("Matches original ca x509 cert");
        }

        // Dates
//        System.out.println("valid not before: " + att.getNotBefore());
//        System.out.println("valid not before: " + att.getNotAfter());

        // check the dates, an exception is thrown in checkValidity()...

        try {
            att.checkValidity();
            att.checkValidity(new Date());
        }
        catch (Exception e) {
            System.out.println(e);
        }

        // verify

        try {
            att.verify(caPubKey, "BC");
        }
        catch (Exception e) {
            System.out.println(e);
        }

        // Attribute
        X509Attribute[] attribs = att.getAttributes();
//        System.out.println("cert has " + attribs.length + " attributes:");
        for (int i = 0; i < attribs.length; i++) {
            X509Attribute a = attribs[i];
//            System.out.println("OID: " + a.getOID());

            // currently we only check for the presence of a 'RoleSyntax'
            // attribute

            if (a.getOID().equals("2.5.24.72")) {
//                System.out.println("rolesyntax read from cert!");
            }
        }
    }
}
