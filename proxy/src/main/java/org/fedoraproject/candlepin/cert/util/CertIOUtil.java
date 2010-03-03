package org.fedoraproject.candlepin.cert.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;


public class CertIOUtil {
    public static X509Certificate getCACert() throws IOException, GeneralSecurityException {
        InputStream inStream = new FileInputStream(getCACertPath());
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(inStream);
        inStream.close();
        
        return cert;
    }
    
    public static PrivateKey getCaKey() throws IOException, GeneralSecurityException {
        final char[] password = getCAKeyPass().toCharArray();
        InputStreamReader inStream = new InputStreamReader(new FileInputStream(getCAKeyPath()));
        // PEMreader requires a password finder to decrypt the password
        PasswordFinder pfinder = new PasswordFinder() {public char[] getPassword() { return password; }};
        PEMReader reader = new PEMReader(inStream, pfinder);
        
        KeyPair keyPair = (KeyPair) reader.readObject();
        reader.close();
        
        if(keyPair == null) { 
            throw new GeneralSecurityException("Reading CA private key failed");
        }
         
        return keyPair.getPrivate();
    }
    
    // TODO : change these for your configuration
    public static String getCAKeyPath() {
        // this should be puppetized
        String certDir = System.getProperty("cakeypath");
        // eclipse and older versions of maven to not have access to maven properties
        if (certDir == null || certDir.equals("null") || certDir.equals("${project.build.directory}")) {
            URL url = CertIOUtil.class.getClassLoader().getResource("keyout.pem");
            certDir = url.getPath();
            if (certDir == null){
                certDir = "/tmp/keyout.pem";
            }
        }
        
        return certDir;
    }
    
    public static String getCACertPath() {
        // this should be puppetized
        String certDir = System.getProperty("cacertpath");
        // eclipse and older versions of maven to not have access to maven properties
        if (certDir == null || certDir.equals("null") || certDir.equals("${project.build.directory}")) {
            //check to see if the ca-cert is in the resources directory
            URL url = CertIOUtil.class.getClassLoader().getResource("ca-cert.pem");
            certDir = url.getPath();
            if (certDir == null){
                certDir = "/tmp/ca-cert.pem";
            }
        }
        
        return certDir;
    }
    
    public static String getCAKeyPass() { 
        // this should be puppetized
        String caPass = System.getProperty("capassword");
        // eclipse and older versions of maven to not have access to maven properties
        if (caPass == null || caPass.equals("null")) { 
            caPass = "redhat";
        }
        
        return caPass;
    }
}
