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
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.httpclient.HttpClientError;
import org.fedoraproject.candlepin.client.cmds.Utils;

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
public class CustomSSLProtocolSocketFactory extends AbstractSLLProtocolSocketFactory{

    private SSLContext sslcontext = null;
    private Configuration configuration;
    /**
     * Constructor for CustomSSLProtocolSocketFactory.
     */
    public CustomSSLProtocolSocketFactory(Configuration config) {
        super();
        this.configuration = config;
    }

    private SSLContext createCustomSSLContext() {
        try {
            char[] passwd = configuration.getKeyStorePassword().toCharArray();
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            String[] keyCert = FileUtil.readKeyAndCert(configuration
                .getConsumerIdentityFilePath());
            kmf.init(PemUtil.pemToKeyStore(keyCert[1], keyCert[0], "password"), passwd);
            /* and provide them for the SSLContext */
            SSLContext ctx = SSLContext.getInstance("TLS");
            if (configuration.isIgnoreTrustManagers()) {
                ctx.init(kmf.getKeyManagers(), Utils.DUMMY_TRUST_MGRS, new SecureRandom());
            }
            else {
                TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance("SunX509");
                KeyStore ks2 = KeyStore.getInstance(KeyStore.getDefaultType());
                ks2.load(null, null);

                ks2.load(
                    new FileInputStream(configuration.getKeyStoreFileLocation()),
                    passwd);
                tmf.init(ks2);
                ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
            }

            
            return ctx;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new HttpClientError(e.getMessage());
        }
    }

    protected SSLContext getSSLContext() {
        if (this.sslcontext == null) {
            this.sslcontext = createCustomSSLContext();
        }
        return this.sslcontext;
    }


}
