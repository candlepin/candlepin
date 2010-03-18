package org.fedoraproject.candlepin.pki;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bouncycastle.asn1.DERUTF8String;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class IOTest {
    private static final long DAYS = 365;
    private static final long HOURS = 24;
    private static final long MINUTES = 60;
    private static final long SECONDS = 60;
    private static final long MILLISECOND = 1000;
    private static Date startDate = new Date(System.currentTimeMillis() - MILLISECOND * SECONDS * MINUTES * HOURS);
    private static Date expiryDate = new Date(System.currentTimeMillis() + DAYS * HOURS * MINUTES * SECONDS * MILLISECOND);
    private static final String SERVER_DN = "C=US, ST=NC, L=Raleigh, O=Red Hat, OU=IT, CN=Red Hat Inc";

    @Before
    public void setup() { 
        setPaths();
    }
    
    private void setPaths() { 
        // set the CA's private key to a valid one
        String path = this.getClass().getClassLoader().getResource("keys/keyout.pem").getPath();
        System.setProperty("cakeypath", path);
        // set the CA cert to a valid one
        path = this.getClass().getClassLoader().getResource("certs/ca-cert.pem").getPath();
        System.setProperty("cacertpath", path);
    }
    
    private List<X509ExtensionWrapper> getDefaultExtensions() { 
        X509ExtensionWrapper wrapper = new X509ExtensionWrapper("1.2.3.4.5", false, new DERUTF8String("INTERNET"));
        List<X509ExtensionWrapper> wrappers = new ArrayList<X509ExtensionWrapper>();
        wrappers.add(wrapper);
        return wrappers;
    }
    
    /**
     * test that an invalid ca key location will correctly fail
     * @throws Exception
     */
    @Test(expected=IOException.class)
    public void testFailedCAKeyRetrieval() throws Exception { 
        // set the cert location to somewhere that doesnt exist
        System.setProperty("cakeypath", "this/directory/does/not/exist");
        // generate new key, sn, and cert
        KeyPair keyPair = BouncyCastlePKIUtil.generateNewKeyPair();
        // create x509 cert for this data
        X509Certificate x509cert = BouncyCastlePKIUtil.createX509Certificate(getDefaultExtensions(), startDate, expiryDate, keyPair, SERVER_DN, new BigInteger("1"));
        Assert.assertNull("Cert should not be created", x509cert);
    }
    
    /**
     * test that an invalid ca key location will correctly fail
     * @throws Exception
     */
    @Test(expected=IOException.class)
    public void testFailedCACertRetrieval() throws Exception { 
        // set the cert location to somewhere that doesnt exist
        System.setProperty("cacertpath", "this/directory/does/not/exist");
        // generate new key, sn, and cert
        KeyPair keyPair = BouncyCastlePKIUtil.generateNewKeyPair();
        // create x509 cert for this data
        X509Certificate x509cert = BouncyCastlePKIUtil.createX509Certificate(getDefaultExtensions(), startDate, expiryDate, keyPair, SERVER_DN, new BigInteger("1"));
        Assert.assertNull("Cert should not be created", x509cert);
    }
    
    
    /**
     * test that a not des-3 encrypted private key will correctly fail
     * @throws Exception
     */
    @Test(expected=GeneralSecurityException.class)
    public void testIncorrectlyEncryptedCAKey() throws Exception { 
        // set the cert location to a known incorrectly encrypted key
        String path = this.getClass().getClassLoader().getResource("keys/cakey.pem").getPath();
        System.setProperty("cakeypath", path);
        // generate new key, sn, and cert
        KeyPair keyPair = BouncyCastlePKIUtil.generateNewKeyPair();
        // create x509 cert for this data
        X509Certificate x509cert = BouncyCastlePKIUtil.createX509Certificate(getDefaultExtensions(), startDate, expiryDate,  keyPair, SERVER_DN, new BigInteger("1"));
        Assert.assertNull("Cert should not be created", x509cert);
    }
    
    @Test
    public void testPemWriter() throws Exception { 
        KeyPair keyPair = BouncyCastlePKIUtil.generateNewKeyPair();
        X509Certificate x509cert = BouncyCastlePKIUtil.createX509Certificate(getDefaultExtensions(), startDate, expiryDate,  keyPair, SERVER_DN, new BigInteger("1"));
        Assert.assertNotNull("Cert should be created", x509cert);
        byte[] pemEncoded = IOUtil.getPemEncoded(x509cert);
        Assert.assertNotNull("pem byte[] fail", pemEncoded);
    }
    
}
