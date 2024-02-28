/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

package org.candlepin.pki.certs;

import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.SubjectKeyIdentifierWriter;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Provider;

public class X509CertificateBuilderProvider implements Provider<X509CertificateBuilder> {
    private final Provider<BouncyCastleProvider> securityProvider;
    private final CertificateReader certificateAuthority;
    private final SubjectKeyIdentifierWriter subjectKeyIdentifierWriter;

    @Inject
    public X509CertificateBuilderProvider(Provider<BouncyCastleProvider> securityProvider,
        CertificateReader certificateAuthority, SubjectKeyIdentifierWriter subjectKeyIdentifierWriter) {
        this.securityProvider = Objects.requireNonNull(securityProvider);
        this.certificateAuthority = Objects.requireNonNull(certificateAuthority);
        this.subjectKeyIdentifierWriter = Objects.requireNonNull(subjectKeyIdentifierWriter);
    }

    @Override
    public X509CertificateBuilder get() {
        return new X509CertificateBuilder(
            this.certificateAuthority, this.securityProvider, this.subjectKeyIdentifierWriter);
    }

}
