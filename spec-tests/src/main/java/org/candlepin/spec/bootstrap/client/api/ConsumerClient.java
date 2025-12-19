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

import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.ComplianceStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.invoker.client.ApiClient;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.resource.client.v1.ConsumerApi;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.util.CertificateUtil;

import org.assertj.core.util.Files;
import org.jetbrains.annotations.NotNull;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Extension of generated {@link ConsumerApi} to provide more convenient overrides of generated methods.
 */
public class ConsumerClient extends ConsumerApi {

    private final ObjectMapper mapper;

    public ConsumerClient(ApiClient client, ObjectMapper mapper) {
        super(client);
        this.mapper = mapper;
    }

    public List<CertificateDTO> fetchCertificates(String consumerUuid) {
        return this.fetchCertificates(consumerUuid, "");
    }

    public List<CertificateDTO> fetchCertificates(String consumerUuid, String serials) {
        // Impl note: We cannot use the OpenApi generated ApiClient as it uses a static Gson object
        // for deserialization that has a ToNumberPolicy that defaults to a double data type. This
        // causes this causes inaccuracies to the serial value on the certificates.
        Request request = Request.from(new org.candlepin.spec.bootstrap.client.ApiClient(this.getApiClient()))
            .setPath("/consumers/{consumer_uuid}/certificates")
            .setPathParam("consumer_uuid", consumerUuid)
            .addHeader("accept", "application/json");

        if (serials != null) {
            request.addQueryParam("serials", serials);
        }

        Response response = request.execute();
        if (!response.wasSuccessful()) {
            throw new ApiException(response.getMessage(), response.getCode(), response.getHeaders(),
                response.getBodyAsString());
        }
        return response.deserialize(new TypeReference<>() {});
    }

    public File exportCertificatesInZipFormat(String consumerUuid, String serials) throws IOException {
        Request request = Request.from(new org.candlepin.spec.bootstrap.client.ApiClient(this.getApiClient()))
            .setPath("/consumers/{consumer_uuid}/certificates")
            .setPathParam("consumer_uuid", consumerUuid)
            .addHeader("accept", "application/zip");

        if (serials != null) {
            request.addQueryParam("serials", serials);
        }

        Response response = request.execute();
        if (!response.wasSuccessful()) {
            throw new ApiException(response.getMessage(), response.getCode(), response.getHeaders(),
                response.getBodyAsString());
        }
        File export = Files.newTemporaryFile();
        export.deleteOnExit();
        try (FileOutputStream os = new FileOutputStream(export)) {
            os.write(response.getBody());
        }

        return export;
    }

    public ConsumerDTO createConsumer(ConsumerDTO consumer) {
        String ownerKey = consumer.getOwner() != null ? consumer.getOwner().getKey() : null;
        return super.createConsumer(consumer, null, ownerKey, null, true);
    }

    public ConsumerDTO createPersonConsumer(ConsumerDTO consumer, String username) {
        return super.createConsumer(consumer, username, consumer.getOwner().getKey(), null, true);
    }

    public ConsumerDTO createConsumerWithoutOwner(ConsumerDTO consumer) {
        return super.createConsumer(consumer, null, null, null, true);
    }

    public JsonNode bindPool(String consumerUuid, String poolId, Integer quantity) {
        return getJsonNode(super.bind(consumerUuid, poolId, null, quantity, "",
            "", false, null, new ArrayList<>()));
    }

    public List<EntitlementDTO> bindPoolSync(String consumerUuid, String poolId, Integer quantity) {
        return parseEntitlements(super.bind(consumerUuid, poolId, null, quantity, "",
            "", false, null, new ArrayList<>()));
    }

    private JsonNode getJsonNode(String consumerUuid) {
        return mapper.readTree(consumerUuid);
    }

    private List<EntitlementDTO> parseEntitlements(String json) {
        return this.mapper.readValue(json, new TypeReference<>() {});
    }

    public JsonNode bindProduct(String consumerUuid, @NotNull String productId) {
        return getJsonNode(super.bind(consumerUuid, null, List.of(productId), null,
            "", "", false, null, new ArrayList<>()));
    }

    public JsonNode bindProduct(String consumerUuid, @NotNull ProductDTO product) {
        return bindProduct(consumerUuid, product.getId());
    }

    public List<EntitlementDTO> bindProductSync(String consumerUuid, @NotNull ProductDTO product) {
        return parseEntitlements(super.bind(consumerUuid, null, List.of(product.getId()), null,
            "", "", false, null, new ArrayList<>()));
    }

    public JsonNode autoBind(String consumerUuid) {
        return getJsonNode(super.bind(consumerUuid, null, null, null,
            "", "", false, null, new ArrayList<>()));
    }

    public List<EntitlementDTO> autoBindSync(String consumerUuid) {
        return parseEntitlements(super.bind(consumerUuid, null, null, null,
            "", "", false, null, new ArrayList<>()));
    }

    @Override
    public List<JsonNode> exportCertificates(String consumerUuid, String serials) {
        Object jsonPayload = super.exportCertificates(consumerUuid, serials);
        try {
            return CertificateUtil.extractEntitlementCertificatesFromPayload(jsonPayload, mapper);
        }
        catch (Exception e) {
            throw new ApiException(e);
        }
    }

    public JsonNode getContentAccessBodyJson(String consumerUuid, String ifModifiedSince) {
        String contentAccessBody = super.getContentAccessBody(consumerUuid, ifModifiedSince);
        return mapper.readTree(contentAccessBody);
    }

    public List<EntitlementDTO> listEntitlements(String consumerUuid) {
        return super.listEntitlements(consumerUuid, null, false, null, null, null, null, null);
    }

    public List<EntitlementDTO> listEntitlementsWithRegen(String consumerUuid) {
        return super.listEntitlements(consumerUuid, null, true, null, null, null, null, null);
    }

    public List<EntitlementDTO> listEntitlements(String consumerUuid, String productId) {
        return super.listEntitlements(consumerUuid, productId, false, null, null, null, null, null);
    }

    public List<EntitlementDTO> listEntitlementsWithRegen(String consumerUuid, String productId) {
        return super.listEntitlements(consumerUuid, productId, true, null, null, null, null, null);
    }

    public ComplianceStatusDTO getComplianceStatus(String consumerUuid) throws ApiException {
        return super.getComplianceStatus(consumerUuid, null);
    }
}
