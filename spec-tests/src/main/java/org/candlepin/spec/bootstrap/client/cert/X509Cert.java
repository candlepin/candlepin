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
package org.candlepin.spec.bootstrap.client.cert;

import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;

import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERUTF8String;

import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
import java.util.Set;
import java.util.stream.Collectors;


/**
 * A wrapper providing means for parsing and accessing components of {@link X509Certificate}
 */
public class X509Cert {

    public static X509Cert from(CertificateDTO certificate) {
        return new X509Cert(parseCertificate(certificate.getCert()));
    }

    public static X509Cert from(JsonNode certificate) {
        String cert = certificate.get("cert").asText();
        return new X509Cert(parseCertificate(cert));
    }

    public static X509Cert from(String cert) {
        return new X509Cert(parseCertificate(cert));
    }

    public static X509Cert fromEnt(JsonNode entitlement) {
        JsonNode certs = entitlement.get("certificates");
        String cert = certs.get(0).get("cert").asText();
        return new X509Cert(parseCertificate(cert));
    }

    /**
     * Parses the certificate data from the given entitlement and returns an X509Cert object
     * containing the parsed data. If the entitlement is null, has no certificates, or contains a
     * null certificate, this function returns null.
     * <p>
     * <strong>Warning:</strong> If the entitlement has multiple certificates, only the first will
     * be parsed and returned.
     *
     * @param entitlement
     *  the entitlement from which to pull a certificate to parse
     *
     * @return
     *  a X509Cert instance containing the parsed certificate data, or null if the entitlement had
     *  no certificate
     */
    public static X509Cert fromEnt(EntitlementDTO entitlement) {
        if (entitlement == null) {
            return null;
        }

        Set<CertificateDTO> certificates = entitlement.getCertificates();
        if (certificates == null || certificates.isEmpty()) {
            return null;
        }

        // This should only ever return one cert, probably.
        CertificateDTO certificate = certificates.iterator().next();
        if (certificate == null) {
            return null;
        }

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
        Principal subjectDN = this.certificate.getSubjectDN();
        return subjectDN.getName();
    }

    public LocalDateTime notBefore() {
        return toLocalDate(this.certificate.getNotBefore());
    }

    public LocalDateTime notAfter() {
        return toLocalDate(this.certificate.getNotAfter());
    }

    public String subjectAltNames() {
        return subjectAlternativeNames().stream()
            .map(objects -> (String) objects.get(1))
            .collect(Collectors.joining(","));
    }

    public ASN1Primitive extensionValue(String extensionId) {
        try {
            byte[] derValue = this.certificate.getExtensionValue(extensionId);
            if (derValue == null) {
                return null;
            }
            ASN1Primitive value = ASN1Primitive.fromByteArray(derValue);
            if (value instanceof DEROctetString) {
                byte[] octetString = ((DEROctetString) value).getOctets();
                return DEROctetString.fromByteArray(octetString);
            }
            else if (value instanceof DERUTF8String) {
                return value;
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public boolean hasExtension(String extensionId) {
        return this.extensionValue(extensionId) != null;
    }

    private Collection<List<?>> subjectAlternativeNames() {
        try {
            return this.certificate.getSubjectAlternativeNames();
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
