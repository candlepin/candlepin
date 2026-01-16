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
package org.candlepin.spec.bootstrap.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.spec.bootstrap.client.cert.X509Cert;
import org.candlepin.spec.bootstrap.data.builder.OID;
import org.candlepin.spec.bootstrap.data.util.X509HuffmanDecodeUtil;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.ListAssert;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERUTF8String;

import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;


public class CertificateAssert extends AbstractAssert<CertificateAssert, X509Cert> {

    public CertificateAssert(X509Cert cert) {
        super(cert, CertificateAssert.class);
    }

    public static CertificateAssert assertThatCert(X509Cert actual) {
        return new CertificateAssert(actual);
    }

    public static CertificateAssert assertThatCert(CertificateDTO actual) {
        return assertThatCert(X509Cert.from(actual));
    }

    public static CertificateAssert assertThatCert(JsonNode actual) {
        return assertThatCert(X509Cert.from(actual));
    }

    public static CertificateAssert assertThatCert(String actual) {
        return assertThatCert(X509Cert.from(actual));
    }

    public CertificateAssert hasExtension(String extensionId) {
        if (!actual.hasExtension(extensionId)) {
            failWithMessage("Expected extension not found: %s", extensionId);
        }
        return this;
    }

    public CertificateAssert doesNotHaveExtension(String extensionId) {
        if (actual.hasExtension(extensionId)) {
            failWithMessage("Expected extension to be missing but was present: %s", extensionId);
        }
        return this;
    }

    public CertificateAssert hasExtensionValue(String extensionId, String value) {
        DERUTF8String derValue = (DERUTF8String) findExtensionValue(extensionId);
        String anObject = derValue == null ? null : derValue.getString();
        if (!value.equals(anObject)) {
            failWithMessage("Extension: %s with value %s does not match expected value: %s",
                extensionId, anObject, value);
        }
        return this;
    }

    public CertificateAssert hasContentRepoType(ContentDTO content) {
        return hasExtensionValue(OID.contentRepoType(content), content.getType());
    }

    public CertificateAssert doesNotHaveContentRepoType(ContentDTO content) {
        return doesNotHaveExtension(OID.contentRepoType(content));
    }

    public CertificateAssert hasContentName(ContentDTO content) {
        return hasExtensionValue(OID.contentName(content), content.getName());
    }

    public CertificateAssert hasVersion(String version) {
        return hasExtensionValue(OID.certificateVersion(), version);
    }

    public CertificateAssert hasEntitlementType(String type) {
        return hasExtensionValue(OID.entitlementType(), type);
    }

    public CertificateAssert hasEntitlementNamespace(String namespace) {
        return hasExtensionValue(OID.entitlementNamespace(), namespace);
    }

    public CertificateAssert hasContentRepoEnabled(ContentDTO content) {
        return hasExtensionValue(OID.contentRepoEnabled(content), "1");
    }

    public CertificateAssert hasContentRepoDisabled(ContentDTO content) {
        return hasExtensionValue(OID.contentRepoEnabled(content), "0");
    }

    public ListAssert<String> extractingEntitlementPayload() {
        DEROctetString derValue = (DEROctetString) findExtensionValue(OID.entitlementPayload());

        byte[] value = derValue == null ? new byte[0] : derValue.getOctets();
        X509HuffmanDecodeUtil decode = new X509HuffmanDecodeUtil();
        List<String> payload;
        try {
            payload = decode.hydrateContentPackage(value);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        return assertThat(payload);
    }

    private ASN1Primitive findExtensionValue(String extensionId) {
        ASN1Primitive derValue = actual.extensionValue(extensionId);
        if (derValue == null) {
            failWithMessage("Extension: %s not found", extensionId);
        }
        return derValue;
    }

}
