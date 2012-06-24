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
package org.candlepin.client;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * PKCS12Util
 */
public class PKCS12Util {
    protected PKCS12Util() {

    }

    public static KeyStore createPKCS12Keystore(X509Certificate cert,
        PrivateKey pKey, String password) {
        try {
            Certificate[] certs = { cert };

            char[] pass = (password == null) ? null : password.toCharArray();
            KeyStore ks = KeyStore.getInstance("PKCS12", "BC");
            ks.load(null, null);
            ks.setCertificateEntry("certificate", cert);
            ks.setKeyEntry("privateKey", pKey, pass, certs);
            return ks;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
