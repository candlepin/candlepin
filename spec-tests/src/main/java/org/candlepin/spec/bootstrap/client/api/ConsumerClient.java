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

package org.candlepin.spec.bootstrap.client.api;

import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.invoker.client.ApiClient;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.resource.client.v1.ConsumerApi;
import org.candlepin.spec.bootstrap.data.util.CertificateUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Extension of generated {@link ConsumerApi} to provide more convenient overrides of generated methods.
 */
public class ConsumerClient extends ConsumerApi {

    private final ObjectMapper mapper;

    public ConsumerClient(ApiClient client, ObjectMapper mapper) {
        super(client);
        this.mapper = mapper;
    }

    public List<CertificateDTO> fetchCertificates(String consumerUuid) throws ApiException {
        return this.fetchCertificates(consumerUuid, "");
    }

    public List<CertificateDTO> fetchCertificates(String consumerUuid, String serials) throws ApiException {
        return ((List<Map<String, String>>) super.exportCertificates(consumerUuid, serials)).stream()
            .map(stringStringMap -> mapper.convertValue(stringStringMap, CertificateDTO.class))
            .collect(Collectors.toList());
    }

    public ConsumerDTO register(ConsumerDTO consumer) throws ApiException {
        return super.createConsumer(consumer, null, consumer.getOwner().getKey(), null, true);
    }

    public JsonNode bindPool(String consumerUuid, String poolId, Integer quantity)
        throws ApiException, JsonProcessingException {
        return mapper.readTree(super.bind(consumerUuid, poolId, null, quantity, "",
            "", false, "", new ArrayList<>()));
    }

    public JsonNode bindProduct(String consumerUuid, String productId)
        throws ApiException, JsonProcessingException {
        return mapper.readTree(super.bind(consumerUuid, null, List.of(productId), null,
            "", "", false, "", new ArrayList<>()));
    }

    public List<JsonNode> exportCertificates(String consumerUuid, String serials) throws ApiException {
        Object jsonPayload = super.exportCertificates(consumerUuid, serials);
        try {
            return CertificateUtil.extractEntitlementCertificatesFromPayload(jsonPayload, mapper);
        }
        catch (Exception e) {
            throw new ApiException(e);
        }
    }

    public List<EntitlementDTO> listEntitlements(String consumerUuid) throws ApiException {
        return super.listEntitlements(consumerUuid, null, false, null, null, null, null, null);
    }

}
