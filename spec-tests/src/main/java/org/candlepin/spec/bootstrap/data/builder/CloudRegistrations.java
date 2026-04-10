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
package org.candlepin.spec.bootstrap.data.builder;

import org.candlepin.dto.api.client.v1.CloudRegistrationDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;



/**
 * Factory class for creating CloudRegistrationDTO instances
 */
public final class CloudRegistrations {

    public static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
        .build();

    private CloudRegistrations() {
        throw new UnsupportedOperationException();
    }

    /**
     * Builds a base64-encoded cloud registration metadata string from the provided offering information.
     *
     * @param accountId
     *  the account ID to encode in the metadata
     *
     * @param instanceId
     *  the instance ID to encode in the metadata
     *
     * @param offeringId
     *  the offering ID to encode in the metadata
     *
     * @return
     *  a base64-encoded cloud registration metadata string from the provided offering information
     */
    public static String buildRegistrationMetadata(String accountId, String instanceId, String offeringId) {
        Map<String, String> properties = new HashMap<>();

        if (accountId != null) {
            properties.put("accountId", accountId);
        }

        if (instanceId != null) {
            properties.put("instanceId", instanceId);
        }

        if (offeringId != null) {
            properties.put("cloudOfferingId", offeringId);
        }

        String json = JSON_MAPPER.writeValueAsString(properties);

        return Base64.getEncoder()
            .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Builds a CloudRegistrationDTO for a given account and offering, with properly encoded metadata, but no
     * value defined for the signature, nor cryptographic capabilities.
     *
     * @param type
     *  the type of account this DTO will represent (e.g. aws, azure, etc.); cannot be null
     *
     * @param accountId
     *  the account ID to encode in the metadata
     *
     * @param instanceId
     *  the instance ID to encode in the metadata
     *
     * @param offeringId
     *  the offering ID to encode in the metadata
     *
     * @return
     *  a CloudRegistrationDTO with populated type and metadata fields based on the values provided
     */
    public static CloudRegistrationDTO forOffering(String type, String accountId, String instanceId,
        String offeringId) {

        String metadata = buildRegistrationMetadata(accountId, instanceId, offeringId);

        return new CloudRegistrationDTO()
            .type(type)
            .metadata(metadata);
    }

    /**
     * Builds a CloudRegistrationDTO with randomly generated values for the test type and values encoded into
     * the metadata, a placeholder value for the signature, and no value defined for the cryptographic
     * capabilities.
     *
     * @return
     *  a CloudRegistrationDTO with randomly generated values
     */
    public static CloudRegistrationDTO random() {
        String suffix = StringUtil.random(8, StringUtil.CHARSET_NUMERIC_HEX);

        String type = "test_type";
        String accountId = "cloud-account-id-" + suffix;
        String instanceId = "cloud-instance-id-" + suffix;
        String offeringId = "cloud-offer-" + suffix;

        return forOffering(type, accountId, instanceId, offeringId)
            .signature("test_signature");
    }

}
