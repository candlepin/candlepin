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
package org.candlepin.spec.bootstrap.client.api;

import org.candlepin.dto.api.client.v1.CloudAuthenticationResultDTO;
import org.candlepin.dto.api.client.v1.CloudRegistrationDTO;
import org.candlepin.invoker.client.ApiClient;
import org.candlepin.resource.client.v1.CloudRegistrationApi;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Base64;


/**
 * Extension of generated {@link CloudRegistrationApi} to provide more convenient overrides of
 * generated methods.
 */
public class CloudRegistrationClient extends CloudRegistrationApi {

    private ObjectMapper mapper;

    public CloudRegistrationClient(ApiClient client, ObjectMapper mapper) {
        super(client);
        this.mapper = mapper;
    }

    /**
     * Verifies provided cloud registration data and returns an authentication token.
     *
     * @param metadata
     *  metadata provided by a cloud provider
     *
     * @param type
     *  the cloud provider's type
     *
     * @param signature
     *  the signature provided by a cloud provider used for authentication
     *
     * @return the authentication token
     */
    public String cloudAuthorize(String metadata, String type, String signature) {
        return super.cloudAuthorize(generateToken(metadata, type, signature), 1);
    }

    /**
     * Verifies provided cloud registration data and returns an authentication token using version two
     * logic.
     *
     * @param accountId
     *  the cloud account ID to authenticate
     *
     * @param instanceId
     *  the instance ID of the system to authenticate
     *
     * @param offeringId
     *  the offering ID of the cloud offering used to create the system
     *
     * @param type
     *  the cloud provider's type
     *
     * @param signature
     *  the signature provided by a cloud provider used for authentication
     *
     * @return the authentication token
     */
    public CloudAuthenticationResultDTO cloudAuthorizeV2(String accountId, String instanceId,
        String offeringId, String type, String signature) {
        String metadata = buildMetadataJson(accountId, instanceId, offeringId);
        String jsonString = super.cloudAuthorize(generateToken(metadata, type, signature), 2);
        if (jsonString == null || jsonString.isBlank()) {
            return null;
        }

        return mapper.readValue(jsonString, CloudAuthenticationResultDTO.class);
    }

    private String buildMetadataJson(String accountId, String instanceId, String offeringId) {
        ObjectNode objectNode = mapper.createObjectNode();
        if (accountId != null) {
            objectNode.put("accountId", accountId);
        }

        if (instanceId != null) {
            objectNode.put("instanceId", instanceId);
        }

        if (offeringId != null) {
            objectNode.put("cloudOfferingId", offeringId);
        }

        return Base64.getEncoder().encodeToString(objectNode.toString().getBytes());
    }

    private CloudRegistrationDTO generateToken(String metadata, String type, String signature) {
        return new CloudRegistrationDTO()
            .type(type)
            .metadata(metadata)
            .signature(signature);
    }

}
