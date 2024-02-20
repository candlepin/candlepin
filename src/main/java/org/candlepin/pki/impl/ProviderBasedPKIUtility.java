/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.pki.impl;

import org.candlepin.config.Configuration;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.X509Extension;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Objects;
import java.util.Set;


/**
 * ProviderBasedPKIUtility is an abstract class implementing functionality in PKIUtility that only relies
 * on JCA classes and interfaces.  Any method that requires access to an underlying cryptographic provider
 * is declared abstract.  If we ever switch cryptographic providers again, it should just be a matter of
 * extending this class, implementing required methods with the new provider, and changing some Guice
 * bindings.
 */
public abstract class ProviderBasedPKIUtility implements PKIUtility {
    protected CertificateReader reader;
    protected SubjectKeyIdentifierWriter subjectKeyWriter;
    protected Configuration config;

    public ProviderBasedPKIUtility(CertificateReader reader, SubjectKeyIdentifierWriter writer,
        Configuration config) {
        this.reader = Objects.requireNonNull(reader);
        this.subjectKeyWriter = Objects.requireNonNull(writer);
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public abstract X509Certificate createX509Certificate(DistinguishedName dn,
        Set<X509Extension> extensions,
        Date startDate, Date endDate, KeyPair clientKeyPair, BigInteger serialNumber, String alternateName)
        throws GeneralSecurityException, IOException;

}
