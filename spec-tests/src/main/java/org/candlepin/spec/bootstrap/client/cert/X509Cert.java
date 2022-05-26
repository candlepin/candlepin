/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.spec.bootstrap.client.cert;

import org.candlepin.dto.api.v1.CertificateDTO;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A wrapper providing means for parsing and accessing components of {@link X509Certificate}
 */
public class X509Cert {

    public static X509Cert from(CertificateDTO certificate) {
        return new X509Cert(parseCertificate(certificate.getCert()));
    }

    public static X509Certificate parseCertificate(String certStr) {
        byte[] decoded = certStr.getBytes(StandardCharsets.UTF_8);

        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(decoded));
        }
        catch (CertificateException e) {
            throw new CertificateParsingFailedException(e);
        }
    }

    private final X509Certificate certificate;

    public X509Cert(X509Certificate certificate) {
        this.certificate = Objects.requireNonNull(certificate);
    }

    public String subject() {
        Principal subjectDN = certificate.getSubjectDN();
        return subjectDN.getName();
    }

    public LocalDateTime notBefore() {
        return toLocalDate(certificate.getNotBefore());
    }

    public String subjectAltNames() {
        return subjectAlternativeNames().stream()
            .map(objects -> (String) objects.get(1))
            .collect(Collectors.joining(","));
    }

    private Collection<List<?>> subjectAlternativeNames() {
        try {
            return certificate.getSubjectAlternativeNames();
        }
        catch (CertificateParsingException e) {
            throw new CertificateParsingFailedException(e);
        }
    }

    private LocalDateTime toLocalDate(Date dateToConvert) {
        return dateToConvert.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
    }

}
