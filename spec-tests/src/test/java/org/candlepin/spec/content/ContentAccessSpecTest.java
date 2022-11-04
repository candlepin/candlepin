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
package org.candlepin.spec.content;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotModified;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.ContentToPromoteDTO;
import org.candlepin.dto.api.client.v1.EnvironmentContentDTO;
import org.candlepin.dto.api.client.v1.EnvironmentDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ReleaseVerDTO;
import org.candlepin.resource.client.v1.ConsumerApi;
import org.candlepin.spec.bootstrap.assertions.CandlepinMode;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Content;
import org.candlepin.spec.bootstrap.data.builder.Environment;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.CertificateUtil;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.X509HuffmanDecodeUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.internal.LinkedTreeMap;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SpecTest
public class ContentAccessSpecTest {
    private static final String CONTENT_ACCESS_OUTPUT_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    private static final String CONTENT_ACCESS_INPUT_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss z";
    private static final String ENT_DATA_OID = "1.3.6.1.4.1.2312.9.7";
    private static final String ENT_TYPE_OID = "1.3.6.1.4.1.2312.9.8";

    @Test
    public void shouldFilterContentWithMismatchedArchitecture() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        // We expect this content to NOT be filtered out due to a match with the system's architecture
        ContentDTO content1 = adminClient.ownerContent()
            .createContent(ownerKey, Content.random().arches("ppc64"));
        // We expect this content to be filtered out due to a mismatch with the system's architecture
        ContentDTO content2 = adminClient.ownerContent()
            .createContent(ownerKey, Content.random().arches("x86_64"));
        // We expect this content to NOT be filtered out due it not specifying an architecture
        ContentDTO content3 = adminClient.ownerContent()
            .createContent(ownerKey, Content.random().arches(""));
        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content1.getId(), true);
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content2.getId(), true);
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content3.getId(), true);

        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "3.3", "uname.machine", "ppc64")));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        List<JsonNode> certs = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(certs).singleElement();
        Map<String, List<String>> prodIdToContentIds = CertificateUtil.toProductContentIdMap(certs.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .hasSize(2)
            .containsExactlyInAnyOrder(content1.getId(), content3.getId());

        JsonNode content = certs.get(0).get("products").get(0).get("content");
        String certContentName = content.get(0).get("name").asText();
        assertNotNull(certContentName);
        JsonNode certContent1 = certContentName.equals(content1.getName()) ? content.get(0) : content.get(1);
        JsonNode certContent3 = certContentName.equals(content3.getName()) ? content.get(0) : content.get(1);

        compareCertContent(content1, certContent1);
        verifyCertContentPath(ownerKey, content1.getContentUrl(), null, certContent1.get("path").asText());

        compareCertContent(content3, certContent3);
        verifyCertContentPath(ownerKey, content3.getContentUrl(), null, certContent3.get("path").asText());
    }

    @Test
    public void shouldAllowChangingTheContentAccessModeAndModeList() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners()
            .createOwner(Owners.random().contentAccessMode(Owners.SCA_ACCESS_MODE));
        String ownerKey = owner.getKey();

        owner.contentAccessMode(Owners.ENTITLEMENT_ACCESS_MODE);
        adminClient.owners().updateOwner(ownerKey, owner);
        assertThat(adminClient.owners().getOwner(ownerKey))
            .returns(Owners.ENTITLEMENT_ACCESS_MODE, OwnerDTO::getContentAccessMode)
            .returns(Owners.ACCESS_MODE_LIST, OwnerDTO::getContentAccessModeList);

        owner.contentAccessModeList(Owners.ENTITLEMENT_ACCESS_MODE);
        adminClient.owners().updateOwner(ownerKey, owner);
        assertThat(adminClient.owners().getOwner(ownerKey))
            .returns(Owners.ENTITLEMENT_ACCESS_MODE, OwnerDTO::getContentAccessMode)
            .returns(Owners.ENTITLEMENT_ACCESS_MODE, OwnerDTO::getContentAccessModeList);
    }

    @Test
    public void shouldAssignTheDefaultModeAndListWhenNoneIsSpecified() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = new OwnerDTO()
            .id(StringUtil.random("id-"))
            .key(StringUtil.random("key-"))
            .displayName(StringUtil.random("display-name-"));

        owner = adminClient.owners().createOwner(owner);

        assertThat(owner)
            .returns(Owners.SCA_ACCESS_MODE, OwnerDTO::getContentAccessMode)
            .returns(Owners.ACCESS_MODE_LIST, OwnerDTO::getContentAccessModeList);
    }


    @Test
    public void shouldAssignTheDefaultModeAndListWhenEmptyStringsAreSpecified() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = Owners.random()
            .contentAccessMode("")
            .contentAccessModeList("");

        owner = adminClient.owners().createOwner(owner);

        assertThat(owner)
            .returns(Owners.SCA_ACCESS_MODE, OwnerDTO::getContentAccessMode)
            .returns(Owners.ACCESS_MODE_LIST, OwnerDTO::getContentAccessModeList);
    }

    @Test
    public void shouldLeaveModeAndListUnchangedWhenNullIsSpecified() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners()
            .createOwner(Owners.random().contentAccessMode(Owners.SCA_ACCESS_MODE));

        ObjectNode ownerNode = (ObjectNode) ApiClient.MAPPER.readTree(owner.toJson());
        ObjectNode nullNode = null;
        ownerNode.set("contentAccessModeList", nullNode);
        ownerNode.set("contentAccessMode", nullNode);

        Response response = Request.from(adminClient)
            .setPath("/owners/{owner_key}")
            .setPathParam("owner_key", owner.getKey())
            .setMethod("PUT")
            .setBody(ownerNode.toString().getBytes())
            .execute();

        OwnerDTO updatedOwner = ApiClient.MAPPER.readValue(response.getBody(), OwnerDTO.class);

        assertThat(updatedOwner)
            .returns(owner.getContentAccessMode(), OwnerDTO::getContentAccessMode)
            .returns(owner.getContentAccessModeList(), OwnerDTO::getContentAccessModeList);
    }

    @Test
    public void shouldNotAllowModeThatDoesNotExistInContentAccessModeList() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = Owners.random()
            .contentAccessMode(Owners.ENTITLEMENT_ACCESS_MODE)
            .contentAccessModeList(Owners.SCA_ACCESS_MODE);

        assertBadRequest(() -> adminClient.owners().createOwner(owner));

        OwnerDTO createdOwner = adminClient.owners().createOwner(Owners.random());

        createdOwner.contentAccessMode("invalid-mode");
        assertBadRequest(() -> adminClient.owners().updateOwner(createdOwner.getKey(), createdOwner));
    }

    @Test
    public void shouldSetModeToDefaultWhenTheListIsUpdatedToNoLongerHaveTheOriginalModeValue()
        throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO scaOwner = adminClient.owners().createOwner(Owners.randomSca());
        assertThat(scaOwner)
            .returns(Owners.SCA_ACCESS_MODE, OwnerDTO::getContentAccessMode)
            .returns(Owners.ACCESS_MODE_LIST, OwnerDTO::getContentAccessModeList);

        // If we remove SCA mode from the list, the mode should also be forced to the default (entitlement)
        scaOwner.contentAccessMode(null);
        scaOwner.contentAccessModeList(Owners.ENTITLEMENT_ACCESS_MODE);
        adminClient.owners().updateOwner(scaOwner.getKey(), scaOwner);

        assertThat(adminClient.owners().getOwner(scaOwner.getKey()))
            .returns(Owners.ENTITLEMENT_ACCESS_MODE, OwnerDTO::getContentAccessMode)
            .returns(Owners.ENTITLEMENT_ACCESS_MODE, OwnerDTO::getContentAccessModeList);
    }

    @Test
    public void shouldProduceAContentAccessCertificateForTheConsumerOnRegistration() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Content.random());
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));

        ConsumerApi consumerApi = new ConsumerApi(adminClient.getApiClient());
        Object export = consumerApi.exportCertificates(consumer.getUuid(), null);
        List<JsonNode> certs = CertificateUtil
            .extractEntitlementCertificatesFromPayload(export, ApiClient.MAPPER);
        assertThat(certs).singleElement();
        Map<String, List<String>> prodIdToContentIds = CertificateUtil.toProductContentIdMap(certs.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .hasSize(1)
            .containsExactly(content.getId());

        JsonNode certContent = certs.get(0).get("products").get(0).get("content").get(0);
        compareCertContent(content, certContent);
        verifyCertContentPath(ownerKey, content.getContentUrl(), null, certContent.get("path").asText());

        List<String> payloadCerts = extractCertsFromPayload(export);
        assertThat(payloadCerts).singleElement();
        byte[] value = CertificateUtil
            .compressedContentExtensionValueFromCert(payloadCerts.get(0), ENT_DATA_OID);
        X509HuffmanDecodeUtil decode = new X509HuffmanDecodeUtil();
        List<String> urls = decode.hydrateContentPackage(value);
        assertThat(urls).contains(CandlepinMode.isStandalone() ? "/" + ownerKey : "/sca/" + ownerKey);

        String extVal = CertificateUtil
            .standardExtensionValueFromCert(payloadCerts.get(0), ENT_TYPE_OID);
        assertThat(extVal).isEqualTo("OrgLevel");
    }

    @Test
    public void shouldNotReproduceAContentAccessCertificateWithV1ConsumerOnRegistration() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Content.random());
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner).facts(Map.of("system.certificate_version", "1.0")));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        List<JsonNode> certs = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(certs).isEmpty();
    }

    @Test
    public void shouldIncludeEnvironmentForTheContentAccessCertOnlyInStandaloneMode() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Content.random());
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        EnvironmentDTO env = adminClient.owners().createEnv(ownerKey, Environment.random());
        promoteContentToEnvironment(adminClient, env.getId(), content, false);

        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner).addEnvironmentsItem(env));
        assertThat(consumer.getEnvironments()).singleElement().returns(env.getId(), EnvironmentDTO::getId);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ConsumerApi consumerApi = new ConsumerApi(consumerClient.getApiClient());
        Object export = consumerApi.exportCertificates(consumer.getUuid(), null);
        List<JsonNode> certs = CertificateUtil
            .extractEntitlementCertificatesFromPayload(export, ApiClient.MAPPER);
        assertThat(certs).singleElement();
        Map<String, List<String>> prodIdToContentIds = CertificateUtil.toProductContentIdMap(certs.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .hasSize(1)
            .containsExactly(content.getId());

        JsonNode certContent = certs.get(0).get("products").get(0).get("content").get(0);
        assertEquals("false", certContent.get("enabled").asText());
        verifyCertContentPath(ownerKey, content.getContentUrl(), env.getName(),
            certContent.get("path").asText());

        List<String> payloadCerts = extractCertsFromPayload(export);
        assertThat(payloadCerts).singleElement();
        byte[] value = CertificateUtil
            .compressedContentExtensionValueFromCert(payloadCerts.get(0), ENT_DATA_OID);
        X509HuffmanDecodeUtil decode = new X509HuffmanDecodeUtil();
        List<String> urls = decode.hydrateContentPackage(value);
        boolean isStandalone = CandlepinMode.isStandalone();
        assertThat(urls)
            .contains(isStandalone ? "/" + ownerKey + "/" + env.getName() : "/sca/" + ownerKey);
    }

    @Test
    public void shouldHandleMultipleEnvironmentsWithContentAccessCert() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Content.random());
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));
        EnvironmentDTO env1 = adminClient.owners().createEnv(ownerKey, Environment.random());
        EnvironmentDTO env2 = adminClient.owners().createEnv(ownerKey, Environment.random());
        ContentDTO content2 = adminClient.ownerContent()
            .createContent(ownerKey, Content.random().arches("x86_64"));
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content2.getId(), true);
        promoteContentToEnvironment(adminClient, env1.getId(), content, true);
        promoteContentToEnvironment(adminClient, env2.getId(), content2, true);

        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner).environments(List.of(env1, env2)));
        assertThat(consumer.getEnvironments()).hasSize(2);

        ConsumerApi consumerApi = new ConsumerApi(adminClient.getApiClient());
        Object export = consumerApi.exportCertificates(consumer.getUuid(), null);
        List<JsonNode> certs = CertificateUtil
            .extractEntitlementCertificatesFromPayload(export, ApiClient.MAPPER);
        assertThat(certs).singleElement();
        Map<String, List<String>> prodIdToContentIds = CertificateUtil.toProductContentIdMap(certs.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .hasSize(2)
            .containsExactlyInAnyOrder(content.getId(), content2.getId());

        JsonNode certContentNode = certs.get(0).get("products").get(0).get("content");
        String certContentName = certContentNode.get(0).get("name").asText();
        assertNotNull(certContentName);
        JsonNode certContent1 = certContentName.equals(content.getName()) ? certContentNode.get(0) :
            certContentNode.get(1);
        JsonNode certContent2 = certContentName.equals(content2.getName()) ? certContentNode.get(0) :
            certContentNode.get(1);

        verifyCertContentPath(ownerKey, content.getContentUrl(), env1.getName(),
            certContent1.get("path").asText());
        verifyCertContentPath(ownerKey, content2.getContentUrl(), env2.getName(),
            certContent2.get("path").asText());

        List<String> payloadCerts = extractCertsFromPayload(export);
        assertThat(payloadCerts).singleElement();
        byte[] value = CertificateUtil
            .compressedContentExtensionValueFromCert(payloadCerts.get(0), ENT_DATA_OID);
        X509HuffmanDecodeUtil decode = new X509HuffmanDecodeUtil();
        List<String> urls = decode.hydrateContentPackage(value);
        boolean isStandalone = CandlepinMode.isStandalone();
        String expectedUrl1 = isStandalone ? "/" + ownerKey + "/" + env1.getName() : "/sca/" + ownerKey;
        String expectedUrl2 = isStandalone ? "/" + ownerKey + "/" + env1.getName() : "/sca/" + ownerKey;
        assertThat(urls).contains(expectedUrl1, expectedUrl2);
    }

    @Test
    public void shouldShowEnvironmentContentChangeInContentAccessCert() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Content.random());
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));
        EnvironmentDTO env = adminClient.owners().createEnv(ownerKey, Environment.random());
        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner).addEnvironmentsItem(env));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        List<JsonNode> certs = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(certs).singleElement();
        Map<String, List<String>> prodIdToContentIds = CertificateUtil.toProductContentIdMap(certs.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .isEmpty();

        promoteContentToEnvironment(adminClient, env.getId(), content, true);

        env = adminClient.environments().getEnvironment(env.getId());
        assertThat(env.getEnvironmentContent())
            .singleElement()
            .returns(content, EnvironmentContentDTO::getContent);
        certs = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(certs).singleElement();
        prodIdToContentIds = CertificateUtil.toProductContentIdMap(certs.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .hasSize(1)
            .containsExactly(content.getId());

        demoteContentFromEnvironment(adminClient, env.getId(), List.of(content.getId()));

        env = adminClient.environments().getEnvironment(env.getId());
        assertThat(env.getEnvironmentContent()).isEmpty();
        certs = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(certs).singleElement();
        prodIdToContentIds = CertificateUtil.toProductContentIdMap(certs.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .isEmpty();
    }

    @Test
    public void shouldShowEnvironmentChangeInContentAccessCert() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Content.random());
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));
        EnvironmentDTO env1 = adminClient.owners().createEnv(ownerKey, Environment.random());
        promoteContentToEnvironment(adminClient, env1.getId(), content, true);

        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner).environments(List.of(env1)));
        assertThat(consumer.getEnvironment()).isNotNull();
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ConsumerApi consumerApi = new ConsumerApi(adminClient.getApiClient());
        Object export = consumerApi.exportCertificates(consumer.getUuid(), null);
        List<JsonNode> certs = CertificateUtil
            .extractEntitlementCertificatesFromPayload(export, ApiClient.MAPPER);
        assertThat(certs).singleElement();
        Map<String, List<String>> prodIdToContentIds = CertificateUtil.toProductContentIdMap(certs.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .containsExactly(content.getId());

        List<String> payloadCerts = extractCertsFromPayload(export);
        assertThat(payloadCerts).singleElement();
        byte[] value = CertificateUtil
            .compressedContentExtensionValueFromCert(payloadCerts.get(0), ENT_DATA_OID);
        X509HuffmanDecodeUtil decode = new X509HuffmanDecodeUtil();
        List<String> urls = decode.hydrateContentPackage(value);
        boolean isStandalone = CandlepinMode.isStandalone();
        assertThat(urls).contains(isStandalone ? "/" + ownerKey + "/" + env1.getName() : "/sca/" + ownerKey);

        EnvironmentDTO env2 = adminClient.owners().createEnv(ownerKey, Environment.random());
        consumer.environments(List.of(env2));
        consumer.setReleaseVer(new ReleaseVerDTO().releaseVer(""));
        consumerClient.consumers().updateConsumer(consumer.getUuid(), consumer);
        assertThat(consumerClient.consumers().getConsumer(consumer.getUuid()))
            .isNotNull()
            .extracting(ConsumerDTO::getEnvironment)
            .returns(env2.getName(), EnvironmentDTO::getName);

        export = consumerApi.exportCertificates(consumer.getUuid(), null);
        payloadCerts = extractCertsFromPayload(export);
        assertThat(payloadCerts).singleElement();
        value = CertificateUtil
            .compressedContentExtensionValueFromCert(payloadCerts.get(0), ENT_DATA_OID);
        urls = decode.hydrateContentPackage(value);
        assertThat(urls).contains(isStandalone ? "/" + ownerKey + "/" + env2.getName() : "/sca/" + ownerKey);
    }

    @Test
    public void shouldCreateNewContentAccessCertWithRefreshCommand() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Content.random());
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ConsumerApi consumerApi = new ConsumerApi(consumerClient.getApiClient());
        Object export = consumerApi.exportCertificates(consumer.getUuid(), null);
        List<String> originalSerialIds = getSerialIdsFromCertExport(export);
        List<JsonNode> originalCerts = CertificateUtil
            .extractEntitlementCertificatesFromPayload(export, ApiClient.MAPPER);
        assertThat(originalCerts).singleElement();

        Map<String, List<String>> prodIdToContentIds = CertificateUtil
            .toProductContentIdMap(originalCerts.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .containsExactly(content.getId());

        List<String> payloadCerts = extractCertsFromPayload(export);
        assertThat(payloadCerts).singleElement();
        String extVal = CertificateUtil
            .standardExtensionValueFromCert(payloadCerts.get(0), "1.3.6.1.4.1.2312.9.8");
        assertThat(extVal).isEqualTo("OrgLevel");

        consumerClient.consumers().regenerateEntitlementCertificates(consumer.getUuid(), null, true);

        export = consumerApi.exportCertificates(consumer.getUuid(), null);
        List<JsonNode> updatedCerts = CertificateUtil
            .extractEntitlementCertificatesFromPayload(export, ApiClient.MAPPER);
        assertThat(updatedCerts).singleElement();
        payloadCerts = extractCertsFromPayload(export);
        assertThat(payloadCerts).singleElement();
        CertificateUtil.standardExtensionValueFromCert(payloadCerts.get(0), "1.3.6.1.4.1.2312.9.8");
        assertThat(extVal).isEqualTo("OrgLevel");

        // verify certificate serials were updated.
        List<String> updatedSerialIds = getSerialIdsFromCertExport(export);
        assertThat(updatedSerialIds)
            .isNotEmpty()
            .doesNotContainAnyElementsOf(originalSerialIds);
    }

    @Test
    public void shouldRemoveTheContentAccessCertificateFromTheConsumerWhenOrgContentAccessModeIsRemoved()
        throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Content.random());
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        List<JsonNode> certs = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(certs).singleElement();
        Map<String, List<String>> prodIdToContentIds = CertificateUtil.toProductContentIdMap(certs.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .containsExactly(content.getId());

        owner.contentAccessMode(Owners.ENTITLEMENT_ACCESS_MODE);
        adminClient.owners().updateOwner(ownerKey, owner);

        certs = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(certs).isEmpty();
    }

    @Test
    public void shouldCreateTheContentAccessCertificateForTheConsumerWhenOrgContentAccessModeIsAdded()
        throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Content.random());
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        owner.contentAccessMode(Owners.ENTITLEMENT_ACCESS_MODE);
        adminClient.owners().updateOwner(ownerKey, owner);
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        List<JsonNode> certs = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(certs).isEmpty();

        owner.contentAccessMode(Owners.SCA_ACCESS_MODE);
        adminClient.owners().updateOwner(ownerKey, owner);
        certs = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(certs).singleElement();
    }

    @Test
    public void shouldRetrieveTheContentAccessCertBodyForTheConsumer() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Content.random());
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        JsonNode contentAccessBody = consumerClient.consumers()
            .getContentAccessBodyJson(consumer.getUuid(), null);
        JsonNode contentListing = contentAccessBody.get("contentListing");
        JsonNode listings = contentListing.elements().next();

        JsonNode cert = CertificateUtil
            .decodeAndUncompressCertificate(listings.get(1).toString(), ApiClient.MAPPER);
        JsonNode certContent = cert.get("products").get(0).get("content").get(0);
        verifyCertContentPath(ownerKey, content.getContentUrl(), null, certContent.get("path").asText());

        byte[] value = CertificateUtil
            .compressedContentExtensionValueFromCert(listings.get(0).asText(), ENT_DATA_OID);
        X509HuffmanDecodeUtil decode = new X509HuffmanDecodeUtil();
        List<String> urls = decode.hydrateContentPackage(value);
        assertThat(urls).contains(CandlepinMode.isStandalone() ? "/" + ownerKey : "/sca/" + ownerKey);
    }

    @Test
    public void shouldOnlyRegenerateContentPartOfContentAccessCertifcateWithContentChange() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Content.random());
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        JsonNode contentAccessBody = consumerClient.consumers()
            .getContentAccessBodyJson(consumer.getUuid(), null);
        JsonNode contentListing = contentAccessBody.get("contentListing");
        JsonNode listings = contentListing.elements().next();
        String originalX509 = listings.get(0).asText();
        String originalContent = listings.get(1).asText();
        String originalLastUpdate = contentAccessBody.get("lastUpdate").asText();

        content.setName(StringUtil.random("name-"));
        adminClient.ownerContent().updateContent(ownerKey, content.getId(), content);

        // Sleep a bit to make sure the 'lastUpdate' has more than a second change
        Thread.sleep(1000);

        contentAccessBody = consumerClient.consumers().getContentAccessBodyJson(consumer.getUuid(), null);
        contentListing = contentAccessBody.get("contentListing");
        listings = contentListing.elements().next();
        String updatedX509 = listings.get(0).asText();
        String updatedContent = listings.get(1).asText();
        String updatedLastUpdate = contentAccessBody.get("lastUpdate").asText();

        assertEquals(originalX509, updatedX509);
        assertNotEquals(originalContent, updatedContent);
        assertNotEquals(originalLastUpdate, updatedLastUpdate);
    }

    @Test
    public void shouldOnlyRegenerateContentPartOfContentAccessCertificateWithEnvironmentWithContentChange()
        throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Content.random());
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        EnvironmentDTO env = adminClient.owners().createEnv(ownerKey, Environment.random());
        promoteContentToEnvironment(adminClient, env.getId(), content, true);

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        JsonNode contentAccessBody = consumerClient.consumers()
            .getContentAccessBodyJson(consumer.getUuid(), null);
        JsonNode contentListing = contentAccessBody.get("contentListing");
        JsonNode listings = contentListing.elements().next();
        String originalX509 = listings.get(0).asText();
        String originalContent = listings.get(1).asText();
        String originalLastUpdate = contentAccessBody.get("lastUpdate").asText();

        content.setName(StringUtil.random("name-"));
        adminClient.ownerContent().updateContent(ownerKey, content.getId(), content);

        // Sleep a bit to make sure the 'lastUpdate' has more than a second change
        Thread.sleep(1000);

        contentAccessBody = consumerClient.consumers().getContentAccessBodyJson(consumer.getUuid(), null);
        contentListing = contentAccessBody.get("contentListing");
        listings = contentListing.elements().next();
        String updatedX509 = listings.get(0).asText();
        String updatedContent = listings.get(1).asText();
        String updatedLastUpdate = contentAccessBody.get("lastUpdate").asText();

        assertEquals(originalX509, updatedX509);
        assertNotEquals(originalContent, updatedContent);
        assertNotEquals(originalLastUpdate, updatedLastUpdate);
    }

    @Test
    public void shouldReturnANotModifiedReturnCodeWhenTheDataHasNotBeenUpdatedSinceDate() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Content.random());
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        // Ensure enough time passes from the last content update to the cert fetching
        Thread.sleep(1000);

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        JsonNode contentAccessBody = consumerClient.consumers()
            .getContentAccessBodyJson(consumer.getUuid(), null);
        String lastUpdate = contentAccessBody.get("lastUpdate").textValue();
        assertThat(lastUpdate).isNotNull();

        ZonedDateTime inputZonedDateTime = ZonedDateTime
            .parse(lastUpdate, DateTimeFormatter.ofPattern(CONTENT_ACCESS_OUTPUT_DATE_FORMAT));
        OffsetDateTime since = inputZonedDateTime.toOffsetDateTime();
        String formatted = since.toZonedDateTime()
            .format(DateTimeFormatter.ofPattern(CONTENT_ACCESS_INPUT_DATE_FORMAT));

        assertNotModified(() -> adminClient.consumers()
            .getContentAccessBodyJson(consumer.getUuid(), formatted));
    }

    @Test
    public void shouldUpdateExistingContentAccessCertContentWhenProductDataChanges() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Content.random());
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        assertThat(consumerClient.consumers().exportCertificates(consumer.getUuid(), null))
            .singleElement();

        content.setName(StringUtil.random("name-"));
        adminClient.ownerContent().updateContent(ownerKey, content.getId(), content);

        List<JsonNode> certs = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        Map<String, List<String>> prodIdToContentIds = CertificateUtil.toProductContentIdMap(certs.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .containsExactly(content.getId());

        JsonNode certContent = certs.get(0).get("products").get(0).get("content").get(0);
        assertEquals(content.getName(), certContent.get("name").asText());
    }

    private void promoteContentToEnvironment(ApiClient client, String envId, ContentDTO content,
        boolean enabled) {
        ContentToPromoteDTO contentToPromote = new ContentToPromoteDTO()
            .contentId(content.getId())
            .environmentId(envId)
            .enabled(enabled);
        AsyncJobStatusDTO job = client.environments()
            .promoteContent(envId, List.of(contentToPromote), true);
        job = client.jobs().waitForJob(job);
        assertThatJob(job).isFinished();
    }

    private void demoteContentFromEnvironment(ApiClient client, String envId, List<String> contentIds) {
        AsyncJobStatusDTO job = client.environments().demoteContent(envId, contentIds, true);
        job = client.jobs().waitForJob(job);
        assertThatJob(job).isFinished();
    }

    private void compareCertContent(ContentDTO expectedContent, JsonNode certContent) {
        assertEquals(expectedContent.getType(), certContent.get("type").asText());
        assertEquals(expectedContent.getName(), certContent.get("name").asText());
        assertEquals(expectedContent.getLabel(), certContent.get("label").asText());
        assertEquals(expectedContent.getVendor(), certContent.get("vendor").asText());
        if (expectedContent.getArches() == null || expectedContent.getArches().isEmpty()) {
            assertEquals(0, certContent.get("arches").size());
        }
        else {
            assertEquals(expectedContent.getArches(), certContent.get("arches").get(0).asText());
        }
    }

    private void verifyCertContentPath(String ownerKey, String contentUrl, String envName,
        String cerContentPath) {
        envName = envName != null ? "/" + envName : "";
        String expected = CandlepinMode.isStandalone() ? "/" + ownerKey + envName + contentUrl : contentUrl;
        assertEquals(expected, cerContentPath);
    }

    private List<String> getSerialIdsFromCertExport(Object export) {
        List<Map<String, LinkedTreeMap>> certsToKeyVal = ((List<Map<String, LinkedTreeMap>>) export);
        List<String> serialIds = new ArrayList<>();
        certsToKeyVal.forEach(entry -> {
            LinkedTreeMap serial = entry.get("serial");
            if (serial != null) {
                serialIds.add(serial.get("id").toString());
            }
        });

        return serialIds;
    }

    private List<String> extractCertsFromPayload(Object jsonPayload) {
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

}
