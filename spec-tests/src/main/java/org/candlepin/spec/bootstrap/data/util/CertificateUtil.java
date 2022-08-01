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

import org.candlepin.dto.api.v1.CertificateDTO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.codec.binary.Base64;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.Inflater;

public final class CertificateUtil {

    private CertificateUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * Extracts the decoded and uncompressed bodies of the entitlement certificates.
     *
     * @param jsonPayload - the json that contains the encoded and compressed entitlement certificates
     * @param mapper - used to parse the json payload.
     * @return the entitlement certificate bodies
     * @throws Exception if unable to decompress the body of the certificate or parse the json
     */
    public static List<JsonNode> extractEntitlementCertificatesFromPayload(Object jsonPayload,
        ObjectMapper mapper) throws Exception {
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
            .map(cert -> cert.getCert())
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        // Create json nodes for each decoded certificate
        ObjectMapper objectMapper = new ObjectMapper();
        List<JsonNode> jsonNodes = new ArrayList<>();
        for (String certificate : certificateBodies) {
            // Retrieve the compressed data body
            certificate = certificate.split("-----BEGIN ENTITLEMENT DATA-----\n")[1];
            certificate = certificate.split("-----END ENTITLEMENT DATA-----")[0];
            byte[] compressedBody = fromBase64(certificate.getBytes());

            // Decompress the data
            Inflater decompressor = new Inflater();
            decompressor.setInput(compressedBody);
            byte[] decompressedBody = new byte[1024];
            decompressor.inflate(decompressedBody);

            jsonNodes.add(objectMapper.readTree(new String(decompressedBody)));
        }

        return jsonNodes;
    }

    private static byte[] fromBase64(byte[] data) {
        Base64 base64 = new Base64();
        return base64.decode(data);
    }
}
