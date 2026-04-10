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
import org.candlepin.spec.bootstrap.data.builder.CloudRegistrations;

import tools.jackson.databind.ObjectMapper;

import java.util.Objects;



/**
 * Extension of generated {@link CloudRegistrationApi} to provide more convenient overrides of
 * generated methods.
 */
public class CloudRegistrationClient extends CloudRegistrationApi {

    private static final int CLOUDREG_V1 = 1;
    private static final int CLOUDREG_V2 = 2;

    private final ObjectMapper mapper;


    public CloudRegistrationClient(ApiClient client, ObjectMapper mapper) {
        super(client);

        this.mapper = Objects.requireNonNull(mapper);
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
        CloudRegistrationDTO dto = new CloudRegistrationDTO()
            .type(type)
            .metadata(metadata)
            .signature(signature);

        return super.cloudAuthorize(dto, CLOUDREG_V1);
    }

    /**
     * Verifies provided cloud registration data and returns an authentication token using version two
     * logic.
     *
     * @param registrationDto
     *  a CloudRegistrationDTO instance to send as input
     *
     * @return
     *  a CloudAuthenticationResultDTO containing the authentication token and related metadata
     */
    public CloudAuthenticationResultDTO cloudAuthorizeV2(CloudRegistrationDTO registrationDto) {
        String json = super.cloudAuthorize(registrationDto, CLOUDREG_V2);

        return json != null && !json.isBlank() ?
            this.mapper.readValue(json, CloudAuthenticationResultDTO.class) :
            null;
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

        CloudRegistrationDTO dto = CloudRegistrations.forOffering(type, accountId, instanceId, offeringId)
            .signature(signature);

        return this.cloudAuthorizeV2(dto);
    }



}
