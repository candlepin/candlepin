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
package org.candlepin.spec.bootstrap.data.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.spec.bootstrap.client.cert.X509Cert;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERUTF8String;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;


public final class CertificateUtil {

    private CertificateUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * Extracts the decoded and uncompressed bodies of the entitlement certificates.
     *
     * @param jsonPayload
     *  the json that contains the encoded and compressed list of entitlement certificates
     *
     * @param mapper
     *  used to parse the json payload
     *
     * @return
     *  the entitlement certificate bodies
     *
     * @throws IOException
     *  if unable to decompress the body of the certificate or parse the json
     *
     * @throws DataFormatException
     *  if unable to decompress the body of the certificate or parse the json
     */
    public static List<JsonNode> extractEntitlementCertificatesFromPayload(
        Object jsonPayload, ObjectMapper mapper) {
        if (jsonPayload == null) {
            return new ArrayList<>();
        }

        String json = String.valueOf(jsonPayload);
        if (json == null || json.isEmpty() || json.isBlank()) {
            return new ArrayList<>();
        }

        // Retrieves the certificate substrings from the json.
        List<String> certificateBodies = ((List<Map<String, String>>) jsonPayload).stream()
            .map(stringStringMap -> mapper.convertValue(stringStringMap, CertificateDTO.class))
            .filter(Objects::nonNull)
            .map(CertificateDTO::getCert)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        // Create json nodes for each decoded certificate
        List<JsonNode> jsonNodes = new ArrayList<>();
        for (String certificate : certificateBodies) {
            jsonNodes.add(decodeAndUncompressCertificate(certificate, mapper));
        }

        return jsonNodes;
    }

    /**
     * Extracts the encoded and compressed bodies of the entitlement certificates.
     *
     * @param jsonPayload
     *  the json object that contains the encoded and compressed entitlement certificates
     *
     * @return
     *  a list entitlement certificates in string format (still encoded and compressed)
     */
    public static List<String> extractEncodedCertsFromPayload(Object jsonPayload) {
        if (jsonPayload == null) {
            return new ArrayList<>();
        }

        String json = String.valueOf(jsonPayload);
        if (json == null || json.isEmpty() || json.isBlank()) {
            return new ArrayList<>();
        }

        List<String> certs = new ArrayList<>();
        ((List<Map<String, String>>) jsonPayload)
            .forEach(entry -> certs.add(entry.get("cert")));

        return certs;
    }

    /**
     * Decodes and uncompresses a certificate body into a {@link JsonNode}.
     * Note that this will fail if you forgot to make your consumer V3 capable
     *
     * @param certificate
     *  the encoded and compressed body of the certificate
     *
     * @param mapper
     *  used to parse the json
     *
     * @return
     *  the certificate json
     *
     * @throws IOException
     *  if unable to decompress the body of the certificate or parse the json
     *
     * @throws DataFormatException
     *  if unable to decompress the body of the certificate or parse the json
     */
    public static JsonNode decodeAndUncompressCertificate(String certificate, ObjectMapper mapper) {
        if (certificate == null || certificate.length() == 0) {
            return null;
        }

        certificate = certificate.replace("\"", "")
            .replace("\\n", Character.toString((char) 10));

        // Retrieve the compressed data body
        certificate = certificate.split("-----BEGIN ENTITLEMENT DATA-----")[1];
        certificate = certificate.split("-----END ENTITLEMENT DATA-----")[0];
        byte[] compressedBody = fromBase64(certificate.getBytes());

        // Decompress the data
        Inflater decompressor = new Inflater();
        decompressor.setInput(compressedBody);
        byte[] decompressedBody = new byte[48000];
        int decompressedSize;
        try {
            decompressedSize = decompressor.inflate(decompressedBody);
        }
        catch (DataFormatException e) {
            throw new RuntimeException(e);
        }
        decompressor.end();

        return mapper.readTree(new String(decompressedBody, 0, decompressedSize));
    }

    private static byte[] fromBase64(byte[] data) {
        Base64 base64 = new Base64();
        return base64.decode(data);
    }

    public static byte[] compressedContentExtensionValueFromCert(String certString, String extensionId)
        throws IOException {
        DEROctetString value = (DEROctetString) getDerValueFromExtension(certString, extensionId);

        return value != null ? value.getOctets() : null;
    }

    public static String standardExtensionValueFromCert(String certString, String extensionId) {
        DERUTF8String value = (DERUTF8String) getDerValueFromExtension(certString, extensionId);

        return value != null ? value.getString() : null;
    }

    public static ASN1Primitive getDerValueFromExtension(String certString, String extensionId) {
        certString = certString.replace("\"", "")
            .replace("\\n", Character.toString((char) 10));
        X509Certificate cert = X509Cert.parseCertificate(certString);

        try {
            byte[] derValue = cert.getExtensionValue(extensionId);
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

    /**
     * Retrieves not before date from the validity period of the provided certificate.
     * Returns null if the provided certificate is null or empty.
     *
     * @param certString
     *  certificate used to retrieve the not before date
     *
     * @return
     *  not before date from the validity period of the provided certificate
     */
    public static Date getCertNotBefore(String certString) {
        if (certString == null || certString.length() == 0) {
            return null;
        }

        certString = certString.replace("\"", "")
            .replace("\\n", Character.toString((char) 10));

        return X509Cert.parseCertificate(certString).getNotBefore();
    }

    /**
     * Retrieves a map of product ID to content IDs from a provided certificate {@link JsonNode}.
     *
     * @param certNode
     *  A certificate that contains products and content
     * @return
     *  a map of product ID to content IDs from the provided certificate
     */
    public static Map<String, List<String>> toProductContentIdMap(JsonNode certNode) {
        Map<String, List<String>> output = new HashMap<>();
        JsonNode products = certNode.get("products");
        assertNotNull(products);
        products.forEach(productNode -> {
            String productId = productNode.get("id").asText();

            List<String> contentIds = output.computeIfAbsent(productId, key -> new ArrayList<>());
            JsonNode content = productNode.get("content");
            if (content != null) {
                content.forEach(contentNode -> contentIds.add(contentNode.get("id").asText()));
            }
        });

        return output;
    }

}
