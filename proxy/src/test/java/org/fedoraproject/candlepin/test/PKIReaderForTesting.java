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
package org.fedoraproject.candlepin.test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.fedoraproject.candlepin.pki.PKIReader;

/**
 * PKIReaderForTesting
 */
public class PKIReaderForTesting implements PKIReader {

    @Override
    public X509Certificate getCACert() throws IOException, CertificateException {
        return null;
    }

    @Override
    public PrivateKey getCaKey() throws IOException, GeneralSecurityException {
        return null;
    }

    @Override
    public X509Certificate getUpstreamCACert() throws IOException,
        CertificateException {
        return null;
    }

}
