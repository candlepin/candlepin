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
package org.fedoraproject.candlepin.client;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.HttpClientError;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;

/**
 * This is a combination of hte example from http client at
 * http://svn.apache.org
 * /viewvc/httpcomponents/oac.hc3x/trunk/src/contrib/org/apache
 * /commons/httpclient
 * /contrib/ssl/EasySSLProtocolSocketFactory.java?revision=661391&view=co as
 * well as the SSL w/o keystores examples at
 * http://www.mombu.com/programming/java
 * /t-ssl-for-java-without-keystores-1366416.html
 */
public class CustomSSLProtocolSocketFactory implements ProtocolSocketFactory {

    private SSLContext sslcontext = null;
    private String certificateFile;
    private String privateKeyFile;

    /**
     * Constructor for CustomSSLProtocolSocketFactory.
     */
    public CustomSSLProtocolSocketFactory(String certificateFile,
        String privateKeyFile) {
        super();
        this.certificateFile = certificateFile;
        this.privateKeyFile = privateKeyFile;
    }

    private static SSLContext createCustomSSLContext(String certificateFile,
        String privateKeyFile) {
        try {
            char[] passwd = "password".toCharArray();
            /* Load CA-Chain file */
            CertificateFactory cf = CertificateFactory.getInstance(Constants.X509);
            X509Certificate candlepinCert = (X509Certificate) cf
                .generateCertificate(new FileInputStream(
                    Constants.CANDLE_PIN_CERTIFICATE_FILE));

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(PemUtil.pemToKeystore(certificateFile, privateKeyFile,
                "password"), passwd);

            TrustManagerFactory tmf = TrustManagerFactory
                .getInstance("SunX509");
            KeyStore ks2 = KeyStore.getInstance(KeyStore.getDefaultType());
            ks2.load(null, null);
            ks2.load(
                new FileInputStream(Constants.KEY_STORE_FILE),
                passwd);
           // ks2.setCertificateEntry("candlepin_ca_crt", candlepinCert);
            tmf.init(ks2);

            /* and provide them for the SSLContext */
            SSLContext ctx = SSLContext.getInstance("TLS");
            // SSLContext ctx = SSLContext.getInstance("SSL");
            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(),
                new SecureRandom());
            
            return ctx;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new HttpClientError(e.getMessage());
        }
    }

    private SSLContext getSSLContext() {
        if (this.sslcontext == null) {
            this.sslcontext = createCustomSSLContext(certificateFile,
                privateKeyFile);
        }
        return this.sslcontext;
    }

    public Socket createSocket(String host, int port, InetAddress clientHost,
        int localPort) throws IOException, UnknownHostException {

        return getSSLContext().getSocketFactory().createSocket(host, port,
            clientHost, localPort);
    }

    public Socket createSocket(final String host, final int port,
        final InetAddress localAddress, final int localPort,
        final HttpConnectionParams params) throws IOException,
        UnknownHostException, ConnectTimeoutException {

        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        int timeout = params.getConnectionTimeout();
        SocketFactory socketfactory = getSSLContext().getSocketFactory();
        if (timeout == 0) {
            return socketfactory.createSocket(host, port, localAddress,
                localPort);
        }
        else {
            Socket socket = socketfactory.createSocket();
            SocketAddress localaddr = new InetSocketAddress(localAddress,
                localPort);
            SocketAddress remoteaddr = new InetSocketAddress(host, port);
            socket.bind(localaddr);
            socket.connect(remoteaddr, timeout);
            return socket;
        }
    }

    public Socket createSocket(String host, int port) throws IOException,
        UnknownHostException {

        return getSSLContext().getSocketFactory().createSocket(host, port);
    }

    public Socket createSocket(Socket socket, String host, int port,
        boolean autoClose) throws IOException, UnknownHostException {

        return getSSLContext().getSocketFactory().createSocket(socket, host,
            port, autoClose);
    }

    public boolean equals(Object obj) {
        return ((obj != null) && obj.getClass().equals(
            CustomSSLProtocolSocketFactory.class));
    }

    public int hashCode() {
        return CustomSSLProtocolSocketFactory.class.hashCode();
    }

}
