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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;



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
     * @param registration
     *     the registration information that needs to be authenticated
     *
     * @return the authentication token
     */
    public String cloudAuthorize(CloudRegistrationDTO registration) {
        return super.cloudAuthorize(registration, 1);
    }

    /**
     * Verifies provided cloud registration data and returns an authentication token using version two
     * logic.
     *
     * @param registration
     *     the registration information that needs to be authenticated
     *
     * @return the cloud authentication results which includes the authentication token, or null if the authentication results can not be created
     */
    public CloudAuthenticationResultDTO cloudAuthorizeV2(CloudRegistrationDTO registration) {
        String jsonString = super.cloudAuthorize(registration, 2);
        if (jsonString == null || jsonString.isBlank()) {
            return null;
        }

        try {
            return mapper.readValue(jsonString, CloudAuthenticationResultDTO.class);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
