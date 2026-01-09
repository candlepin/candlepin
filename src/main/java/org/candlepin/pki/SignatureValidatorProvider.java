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
package org.candlepin.pki;

import org.candlepin.pki.impl.jca.JcaSignatureValidator;

import com.google.inject.Provider;

import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;



/**
 * Temporary provider for SignatureValidator instances. Remove once the CertificateAuthority interface and
 * implementations exist.
 */
@Singleton
public class SignatureValidatorProvider implements Provider<SignatureValidator> {

    private final CertificateReader certReader;

    @Inject
    public SignatureValidatorProvider(CertificateReader certReader) {
        this.certReader = Objects.requireNonNull(certReader);
    }

    @Override
    public SignatureValidator get() {
        Scheme scheme = new Scheme("rsa", this.certReader.getCACert(), "SHA256withRSA", "rsa");
        return new JcaSignatureValidator(scheme);
    }
}

