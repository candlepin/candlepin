package org.fedoraproject.candlepin.pki;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.openssl.PasswordFinder;

public final class IOUtil {
    
    private static final String DEFAULT_CA_KEY_PATH = "keys/keyout.pem";
    private static final String DEFAULT_CA_CERT_PATH = "certs/ca-cert.pem";

    private IOUtil() { }
    
    public static X509Certificate getCACert() throws IOException, GeneralSecurityException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        InputStream is = getCACertStream();
        X509Certificate cert = (X509Certificate) cf.generateCertificate(getCACertStream());
        is.close();
        return cert;
    }
    
    public static PrivateKey getCaKey() throws IOException, GeneralSecurityException {
        final char[] password = getCAKeyPass().toCharArray();
        InputStreamReader inStream = new InputStreamReader(getCAKeyStream());
        // PEMreader requires a password finder to decrypt the password
        PasswordFinder pfinder = new PasswordFinder() {public char[] getPassword() { return password; }};
        PEMReader reader = new PEMReader(inStream, pfinder);
        KeyPair keyPair = (KeyPair) reader.readObject();
        inStream.close();
        reader.close();
        if(keyPair == null) { 
            throw new GeneralSecurityException("Reading CA private key failed");
        }
        return keyPair.getPrivate();
    }
    
    private static InputStream getStream(String propertyName, String defaultPath) throws IOException{
    	InputStream is = null;
        String certDir = System.getProperty(propertyName);
        if (certDir == null || certDir.equals("null") || certDir.equals("${project.build.directory}")) {
            is = IOUtil.class.getClassLoader().getResourceAsStream(defaultPath);
        	if (is == null) {
        		throw new IOException(propertyName + " not found in classpath or on filesystem");
        	}
        }else{
        	is = new FileInputStream(new File(certDir));
        }
        
        return is;
    }
    
    /**
     * Take an X509Certificate object and return a byte[] of the certificate,
     * PEM encoded
     * @param cert
     * @return PEM-encoded bytes of the certificate
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public static byte[] getPemEncoded(X509Certificate cert) throws GeneralSecurityException, IOException { 
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PEMWriter w =  new PEMWriter(new OutputStreamWriter(stream));
        w.writeObject(cert);
        return stream.toByteArray();
    }
    
    public static InputStream getCAKeyStream() throws IOException {
    	return getStream("cakeypath", DEFAULT_CA_KEY_PATH);
    }
    
    public static InputStream getCACertStream() throws IOException {
    	return getStream("cacertpath", DEFAULT_CA_CERT_PATH);
    }
    
    public static String getCAKeyPass() { 
        String caPass = System.getProperty("capassword");
        if (caPass == null || caPass.equals("null")) { 
            caPass = "redhat";
        }
        
        return caPass;
    }
}
