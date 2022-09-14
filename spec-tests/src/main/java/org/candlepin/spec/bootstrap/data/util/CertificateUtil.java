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
package org.candlepin.spec.bootstrap.data.util;

import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.spec.bootstrap.client.cert.X509Cert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.codec.binary.Base64;
import org.mozilla.jss.netscape.security.util.DerValue;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
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
    public static List<JsonNode> extractEntitlementCertificatesFromPayload(Object jsonPayload,
        ObjectMapper mapper) throws IOException, DataFormatException {
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
     * Decodes and uncompresses a certificate body into a {@link JsonNode}.
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
    public static JsonNode decodeAndUncompressCertificate(String certificate, ObjectMapper mapper)
        throws IOException, DataFormatException {
        if (certificate == null || certificate.length() == 0) {
            return null;
        }

        // Retrieve the compressed data body
        certificate = certificate.split("-----BEGIN ENTITLEMENT DATA-----\n")[1];
        certificate = certificate.split("-----END ENTITLEMENT DATA-----")[0];
        byte[] compressedBody = fromBase64(certificate.getBytes());

        // Decompress the data
        Inflater decompressor = new Inflater();
        decompressor.setInput(compressedBody);
        byte[] decompressedBody = new byte[48000];
        decompressor.inflate(decompressedBody);

        return mapper.readTree(new String(decompressedBody));
    }

    private static byte[] fromBase64(byte[] data) {
        Base64 base64 = new Base64();
        return base64.decode(data);
    }

    public static byte[] compressedContentExtensionValueFromCert(String certString, String extensionId)
        throws IOException {
        return getDerValueFromExtension(certString, extensionId).getOctetString();
    }

    public static String standardExtensionValueFromCert(String certString, String extensionId) {
        return getDerValueFromExtension(certString, extensionId).toString();
    }

    public static DerValue getDerValueFromExtension(String certString, String extensionId) {
        certString = certString.replace("\"", "")
            .replace("\\n", Character.toString((char) 10));
        X509Certificate cert = X509Cert.parseCertificate(certString);
        try {
            DerValue value = new DerValue(cert.getExtensionValue(extensionId));
            byte[] octetString = value.getOctetString();
            return new DerValue(octetString);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
