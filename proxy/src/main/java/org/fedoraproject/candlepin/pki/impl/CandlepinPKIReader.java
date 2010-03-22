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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.pki.PKIReader;

import com.google.inject.Inject;

public class CandlepinPKIReader implements PKIReader {

    private CertificateFactory certFactory;
    private Config config;

    @Inject
    public CandlepinPKIReader(Config config) throws CertificateException {
        this.config = config;
        this.certFactory = CertificateFactory.getInstance("X.509");
    }
    
    @Override
    public X509Certificate getCACert() throws IOException, CertificateException {
        // TODO Auto-generated method stub
        return null;
    }

    
    @Override
    public PrivateKey getCaKey() throws IOException, GeneralSecurityException {
        // TODO Auto-generated method stub
        return null;
    }

}
