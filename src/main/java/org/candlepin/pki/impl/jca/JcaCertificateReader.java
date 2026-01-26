/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.pki.impl.jca;

import org.candlepin.pki.CertificateReader;

import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Objects;

import javax.inject.Inject;



/**
 * Simple JCA-based implementation of the certificate reader
 */
public class JcaCertificateReader implements CertificateReader {
    private static final String X509_CERT_TYPE = "X.509";

    private final java.security.Provider securityProvider;

    @Inject
    public JcaCertificateReader(java.security.Provider securityProvider) {
        this.securityProvider = Objects.requireNonNull(securityProvider);
    }

    @Override
    public X509Certificate read(InputStream istream) throws CertificateException {
        return (X509Certificate) CertificateFactory.getInstance(X509_CERT_TYPE, this.securityProvider)
            .generateCertificate(istream);
    }

}
