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
package org.candlepin.spec.content;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.candlepin.spec.bootstrap.assertions.CertificateAssert.assertThatCert;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotModified;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.CertificateSerialDTO;
import org.candlepin.dto.api.client.v1.ComplianceStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.ContentToPromoteDTO;
import org.candlepin.dto.api.client.v1.EnvironmentContentDTO;
import org.candlepin.dto.api.client.v1.EnvironmentDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ReleaseVerDTO;
import org.candlepin.dto.api.client.v1.SystemPurposeComplianceStatusDTO;
import org.candlepin.resource.client.v1.ConsumerApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.cert.X509Cert;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Environments;
import org.candlepin.spec.bootstrap.data.builder.Facts;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.CertificateUtil;
import org.candlepin.spec.bootstrap.data.util.ExportUtil;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import com.google.gson.internal.LinkedTreeMap;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


@SpecTest
@SuppressWarnings("indentation")
public class ContentAccessSpecTest {
    private static final String CONTENT_ACCESS_CERTS_PATH = "export/content_access_certificates/";

    private static final String CONTENT_ACCESS_OUTPUT_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    private static final String CONTENT_ACCESS_INPUT_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss z";

    @Test
    public void shouldFilterContentWithMismatchedArchitecture() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        // We expect this content to NOT be filtered out due to a match with the system's architecture
        ContentDTO content1 = adminClient.ownerContent()
            .createContent(ownerKey, Contents.random().arches("ppc64"));
        // We expect this content to be filtered out due to a mismatch with the system's architecture
        ContentDTO content2 = adminClient.ownerContent()
            .createContent(ownerKey, Contents.random().arches("x86_64"));
        // We expect this content to NOT be filtered out due it not specifying an architecture
        ContentDTO content3 = adminClient.ownerContent()
            .createContent(ownerKey, Contents.random().arches(""));
        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content1.getId(), true);
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content2.getId(), true);
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content3.getId(), true);

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
        verifyCertContentPath(owner.getContentPrefix(), content1.getContentUrl(), null,
            certContent1.get("path").asText());

        compareCertContent(content3, certContent3);
        verifyCertContentPath(owner.getContentPrefix(), content3.getContentUrl(), null,
            certContent3.get("path").asText());
    }

    @Test
    public void shouldAllowChangingTheContentAccessModeAndModeList() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners()
            .createOwner(Owners.random().contentAccessMode(Owners.SCA_ACCESS_MODE));
        String ownerKey = owner.getKey();

        owner.contentAccessMode(Owners.ENTITLEMENT_ACCESS_MODE);
        adminClient.owners().updateOwner(ownerKey, owner);
        assertThat(adminClient.owners().getOwner(ownerKey))
            .returns(Owners.ENTITLEMENT_ACCESS_MODE, OwnerDTO::getContentAccessMode)
            .returns(Owners.ACCESS_MODE_LIST_ALL, OwnerDTO::getContentAccessModeList);

        owner.contentAccessModeList(Owners.ENTITLEMENT_ACCESS_MODE);
        adminClient.owners().updateOwner(ownerKey, owner);
        assertThat(adminClient.owners().getOwner(ownerKey))
            .returns(Owners.ENTITLEMENT_ACCESS_MODE, OwnerDTO::getContentAccessMode)
            .returns(Owners.ENTITLEMENT_ACCESS_MODE, OwnerDTO::getContentAccessModeList);
    }

    @Test
    public void shouldAssignTheDefaultModeAndListWhenNoneIsSpecified() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = new OwnerDTO()
            .id(StringUtil.random("id-"))
            .key(StringUtil.random("key-"))
            .displayName(StringUtil.random("display-name-"));

        owner = adminClient.owners().createOwner(owner);

        assertThat(owner)
            .returns(Owners.SCA_ACCESS_MODE, OwnerDTO::getContentAccessMode)
            .returns(Owners.DEFAULT_ACCESS_MODE_LIST, OwnerDTO::getContentAccessModeList);
    }

    @Test
    public void shouldAssignTheDefaultModeAndListWhenEmptyStringsAreSpecified() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = Owners.random()
            .contentAccessMode("")
            .contentAccessModeList("");

        owner = adminClient.owners().createOwner(owner);

        assertThat(owner)
            .returns(Owners.SCA_ACCESS_MODE, OwnerDTO::getContentAccessMode)
            .returns(Owners.DEFAULT_ACCESS_MODE_LIST, OwnerDTO::getContentAccessModeList);
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
    public void shouldNotAllowModeThatDoesNotExistInContentAccessModeList() {
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
    public void shouldSetModeToDefaultWhenTheListIsUpdatedToNoLongerHaveTheOriginalModeValue() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO scaOwner = adminClient.owners().createOwner(Owners.randomSca());
        assertThat(scaOwner)
            .returns(Owners.SCA_ACCESS_MODE, OwnerDTO::getContentAccessMode)
            .returns(Owners.ACCESS_MODE_LIST_ALL, OwnerDTO::getContentAccessModeList);

        // If we remove SCA mode from the list, the mode should also be forced to the default (entitlement)
        scaOwner.contentAccessMode(null);
        scaOwner.contentAccessModeList(Owners.ENTITLEMENT_ACCESS_MODE);
        adminClient.owners().updateOwner(scaOwner.getKey(), scaOwner);

        assertThat(adminClient.owners().getOwner(scaOwner.getKey()))
            .returns(Owners.ENTITLEMENT_ACCESS_MODE, OwnerDTO::getContentAccessMode)
            .returns(Owners.ENTITLEMENT_ACCESS_MODE, OwnerDTO::getContentAccessModeList);
    }

    @Test
    public void shouldProduceAContentAccessCertificateForTheConsumerOnRegistration() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO ownerDTO = Owners.randomSca();
        OwnerDTO owner = adminClient.owners().createOwner(ownerDTO.contentPrefix("/" + ownerDTO.getKey()));
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
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
        verifyCertContentPath(owner.getContentPrefix(), content.getContentUrl(), null,
            certContent.get("path").asText());

        List<String> payloadCerts = extractCertsFromPayload(export);
        assertThat(payloadCerts).singleElement();

        assertThatCert(X509Cert.from(payloadCerts.get(0)))
            .hasEntitlementType("OrgLevel")
            .extractingEntitlementPayload()
            .containsOnly(
                contentUrl(ownerKey)
            );
    }

    @Test
    public void shouldNotReproduceAContentAccessCertificateWithV1ConsumerOnRegistration() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner).facts(Map.of("system.certificate_version", "1.0")));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        List<JsonNode> certs = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(certs).isEmpty();
    }

    @Test
    public void shouldNotEncodeEnvironmentNameSlashesInContentPaths() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        EnvironmentDTO env = Environments.random();
        env.setName(StringUtil.random("test/env/name"));
        env.setContentPrefix(StringUtil.random("/test/env/name"));
        env = adminClient.owners().createEnvironment(ownerKey, env);
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
        verifyCertContentPath(owner.getContentPrefix(), content.getContentUrl(), env,
            certContent.get("path").asText());

        List<String> payloadCerts = extractCertsFromPayload(export);
        assertThat(payloadCerts).singleElement();

        assertThatCert(X509Cert.from(payloadCerts.get(0)))
            .extractingEntitlementPayload()
            .containsOnly(contentUrl(ownerKey, env));
    }

    @Test
    public void shouldIncludeEnvironmentForTheContentAccessCertOnlyInStandaloneMode() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        EnvironmentDTO env = adminClient.owners().createEnvironment(ownerKey, Environments.random());
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
        verifyCertContentPath(owner.getContentPrefix(), content.getContentUrl(), env,
            certContent.get("path").asText());

        List<String> payloadCerts = extractCertsFromPayload(export);
        assertThat(payloadCerts).singleElement();
        assertThatCert(X509Cert.from(payloadCerts.get(0)))
            .extractingEntitlementPayload()
            .containsOnly(
                contentUrl(ownerKey, env)
            );
    }

    @Test
    public void shouldHandleMultipleEnvironmentsWithContentAccessCert() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));
        EnvironmentDTO env1 = adminClient.owners().createEnvironment(ownerKey, Environments.random());
        EnvironmentDTO env2 = adminClient.owners().createEnvironment(ownerKey, Environments.random());
        ContentDTO content2 = adminClient.ownerContent()
            .createContent(ownerKey, Contents.random().arches("x86_64"));
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content2.getId(), true);
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

        verifyCertContentPath(owner.getContentPrefix(), content.getContentUrl(), env1,
            certContent1.get("path").asText());
        verifyCertContentPath(owner.getContentPrefix(), content2.getContentUrl(), env2,
            certContent2.get("path").asText());

        List<String> payloadCerts = extractCertsFromPayload(export);
        assertThat(payloadCerts).singleElement();
        assertThatCert(X509Cert.from(payloadCerts.get(0)))
            .extractingEntitlementPayload()
            .containsOnly(
                contentUrl(ownerKey, env1),
                contentUrl(ownerKey, env2)
            );
    }

    @Test
    public void shouldShowEnvironmentContentChangeInContentAccessCert() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));
        EnvironmentDTO env = adminClient.owners().createEnvironment(ownerKey, Environments.random());
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

        // Need to wait a second because MySQL does not store millisecond precision and we need to gaurantee
        // the comparison between the content access payload timestamp and the environment content last update
        // is different
        Thread.sleep(1000);

        promoteContentToEnvironment(adminClient, env.getId(), content, true);

        env = adminClient.environments().getEnvironment(env.getId());
        assertThat(env.getEnvironmentContent())
            .singleElement()
            .returns(content.getId(), EnvironmentContentDTO::getContentId);

        certs = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(certs).singleElement();
        prodIdToContentIds = CertificateUtil.toProductContentIdMap(certs.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .hasSize(1)
            .containsExactly(content.getId());

        Thread.sleep(1000);

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
    public void shouldShowEnvironmentChangeInContentAccessCert() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO ownerDTO = Owners.randomSca();
        OwnerDTO owner = adminClient.owners().createOwner(ownerDTO.contentPrefix(ownerDTO.getKey()));
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));
        EnvironmentDTO env1 = adminClient.owners().createEnvironment(ownerKey, Environments.random());
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
        assertThatCert(X509Cert.from(payloadCerts.get(0)))
            .extractingEntitlementPayload()
            .containsOnly(
                contentUrl(ownerKey, env1)
            );

        EnvironmentDTO env2 = adminClient.owners().createEnvironment(ownerKey, Environments.random());
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
        assertThatCert(X509Cert.from(payloadCerts.get(0)))
            .extractingEntitlementPayload()
            .containsOnly(
                contentUrl(ownerKey, env2)
            );
    }

    @Test
    public void shouldCreateNewContentAccessCertWithRefreshCommand() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
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
        assertThatCert(X509Cert.from(payloadCerts.get(0)))
            .hasEntitlementType("OrgLevel");

        consumerClient.consumers().regenerateEntitlementCertificates(consumer.getUuid(), null, true, true);

        export = consumerApi.exportCertificates(consumer.getUuid(), null);
        List<JsonNode> updatedCerts = CertificateUtil
            .extractEntitlementCertificatesFromPayload(export, ApiClient.MAPPER);
        assertThat(updatedCerts).singleElement();
        payloadCerts = extractCertsFromPayload(export);
        assertThat(payloadCerts).singleElement();
        assertThatCert(X509Cert.from(payloadCerts.get(0)))
            .hasEntitlementType("OrgLevel");

        // verify certificate serials were updated.
        List<String> updatedSerialIds = getSerialIdsFromCertExport(export);
        assertThat(updatedSerialIds)
            .isNotEmpty()
            .doesNotContainAnyElementsOf(originalSerialIds);
    }

    @Test
    public void shouldRemoveTheContentAccessCertificateFromTheConsumerWhenOrgContentAccessModeIsRemoved() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
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
    public void shouldCreateTheContentAccessCertificateForTheConsumerWhenOrgContentAccessModeIsAdded() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
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
    public void shouldRetrieveTheContentAccessCertBodyForTheConsumer() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO ownerDTO = Owners.randomSca();
        OwnerDTO owner = adminClient.owners().createOwner(ownerDTO.contentPrefix("/" + ownerDTO.getKey()));
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        JsonNode contentAccessBody = consumerClient.consumers()
            .getContentAccessBodyJson(consumer.getUuid(), null);
        JsonNode contentListing = contentAccessBody.get("contentListing");
        JsonNode listings = contentListing.valueStream()
            .findFirst()
            .orElseThrow();

        JsonNode cert = CertificateUtil.decodeAndUncompressCertificate(listings.get(1).toString(),
            ApiClient.MAPPER);
        JsonNode certContent = cert.get("products").get(0).get("content").get(0);
        verifyCertContentPath(owner.getContentPrefix(), content.getContentUrl(), null,
            certContent.get("path").asText());

        assertThatCert(listings.get(0).asText())
            .extractingEntitlementPayload()
            .containsOnly(
                contentUrl(ownerKey)
            );
    }

    @Test
    public void shouldOnlyRegenerateContentPartOfContentAccessCertifcateWithContentChange() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        JsonNode contentAccessBody = consumerClient.consumers()
            .getContentAccessBodyJson(consumer.getUuid(), null);
        JsonNode contentListing = contentAccessBody.get("contentListing");
        JsonNode listings = contentListing.valueStream()
            .findFirst()
            .orElseThrow();
        String originalX509 = listings.get(0).asText();
        String originalContent = listings.get(1).asText();
        String originalLastUpdate = contentAccessBody.get("lastUpdate").asText();

        content.setName(StringUtil.random("name-"));
        adminClient.ownerContent().updateContent(ownerKey, content.getId(), content);

        // Sleep a bit to make sure the 'lastUpdate' has more than a second change
        Thread.sleep(1000);

        contentAccessBody = consumerClient.consumers().getContentAccessBodyJson(consumer.getUuid(), null);
        contentListing = contentAccessBody.get("contentListing");
        listings = contentListing.valueStream()
            .findFirst()
            .orElseThrow();
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

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        EnvironmentDTO env = adminClient.owners().createEnvironment(ownerKey, Environments.random());
        promoteContentToEnvironment(adminClient, env.getId(), content, true);

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        JsonNode contentAccessBody = consumerClient.consumers()
            .getContentAccessBodyJson(consumer.getUuid(), null);
        JsonNode contentListing = contentAccessBody.get("contentListing");
        JsonNode listings = contentListing.valueStream()
            .findFirst()
            .orElseThrow();
        String originalX509 = listings.get(0).asText();
        String originalContent = listings.get(1).asText();
        String originalLastUpdate = contentAccessBody.get("lastUpdate").asText();

        content.setName(StringUtil.random("name-"));
        adminClient.ownerContent().updateContent(ownerKey, content.getId(), content);

        // Sleep a bit to make sure the 'lastUpdate' has more than a second change
        Thread.sleep(1000);

        contentAccessBody = consumerClient.consumers().getContentAccessBodyJson(consumer.getUuid(), null);
        contentListing = contentAccessBody.get("contentListing");
        listings = contentListing.valueStream()
            .findFirst()
            .orElseThrow();
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

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
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
    public void shouldCorrectlyExportMultipleCertificatesArchivesInParallel() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer3 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer4 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer5 = adminClient.consumers().createConsumer(Consumers.random(owner));
        List<ConsumerDTO> consumers = List.of(consumer1, consumer2, consumer3, consumer4, consumer5);

        int maxNumberOfAttempts = 5;
        for (int i = 0; i < maxNumberOfAttempts; i++) {
            // Update the arch of all the consumers so that the content access payload key will be
            // different than the key used to store the content access payload previously. This causes the
            // regeneration of the content access payload which is what we need to cause the 429 response.
            String newArch = StringUtil.random("arch-");
            for (ConsumerDTO consumer : consumers) {
                consumer.putFactsItem(Facts.Arch.key(), newArch);
                adminClient.consumers().updateConsumer(consumer.getUuid(), consumer);
            }

            List<Callable<File>> tasks = List.of(
                () -> ApiClients.ssl(consumer1).consumers()
                    .exportCertificatesInZipFormat(consumer1.getUuid(), null),
                () -> ApiClients.ssl(consumer1).consumers()
                    .exportCertificatesInZipFormat(consumer1.getUuid(), null),
                () -> ApiClients.ssl(consumer1).consumers()
                    .exportCertificatesInZipFormat(consumer1.getUuid(), null),
                () -> ApiClients.ssl(consumer1).consumers()
                    .exportCertificatesInZipFormat(consumer1.getUuid(), null),
                () -> ApiClients.ssl(consumer1).consumers()
                    .exportCertificatesInZipFormat(consumer1.getUuid(), null));

            ExecutorService execService = Executors.newFixedThreadPool(consumers.size());
            List<Future<File>> futures = execService.invokeAll(tasks);
            execService.shutdown();
            execService.awaitTermination(10L, TimeUnit.SECONDS);

            for (Future<File> future : futures) {
                ZipFile archive = ExportUtil.getExportArchive(future.get());

                // Should not contain content access certs in exported zip file in Entitlement mode.
                List<? extends ZipEntry> caCerts = archive.stream()
                    .filter(e -> !e.isDirectory())
                    .filter(e -> e.getName().startsWith(CONTENT_ACCESS_CERTS_PATH))
                    .filter(e -> e.getName().lastIndexOf('/') == CONTENT_ACCESS_CERTS_PATH.length() - 1)
                    .toList();

                assertEquals(1, caCerts.size());

                byte[] bytes = ExportUtil.extractEntry(archive, caCerts.get(0));
                String certificate = new String(bytes, StandardCharsets.UTF_8);

                JsonNode root = CertificateUtil.decodeAndUncompressCertificate(certificate, ApiClient.MAPPER);
                JsonNode productNode = root.get("products").get(0);
                JsonNode contentNode = productNode.get("content").get(0);

                assertEquals(content.getId(), contentNode.get("id").textValue());
            }
        }
    }

    @Test
    public void shouldCorrectlyGenerateContentAccessPayloadInParallel() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO product = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, product.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(product));

        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer3 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer4 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer5 = adminClient.consumers().createConsumer(Consumers.random(owner));

        List<ConsumerDTO> consumers = List.of(consumer1, consumer2, consumer3, consumer4, consumer5);

        int maxNumberOfAttempts = 5;
         for (int i = 0; i < maxNumberOfAttempts; i++) {
            // Update the arch of all the consumers so that the content access payload key will be
            // different than the key used to store the content access payload previously. This causes the
            // regeneration of the content access payload which is what we need to cause the 429 response.
            String newArch = StringUtil.random("arch-");
            for (ConsumerDTO consumer : consumers) {
                consumer.putFactsItem(Facts.Arch.key(), newArch);
                adminClient.consumers().updateConsumer(consumer.getUuid(), consumer);
            }

            List<Callable<JsonNode>> tasks = List.of(
                () -> adminClient.consumers().getContentAccessBodyJson(consumer1.getUuid(), null),
                () -> adminClient.consumers().getContentAccessBodyJson(consumer2.getUuid(), null),
                () -> adminClient.consumers().getContentAccessBodyJson(consumer3.getUuid(), null),
                () -> adminClient.consumers().getContentAccessBodyJson(consumer4.getUuid(), null),
                () -> adminClient.consumers().getContentAccessBodyJson(consumer5.getUuid(), null));

            ExecutorService execService = Executors.newFixedThreadPool(tasks.size());
            List<Future<JsonNode>> futures = execService.invokeAll(tasks);
            execService.shutdown();
            execService.awaitTermination(10L, TimeUnit.SECONDS);

            for (Future<JsonNode> future : futures) {
                String contentAccessPayload = future.get()
                    .get("contentListing")
                    .valueStream()
                    .findFirst()
                    .orElseThrow()
                    .get(1)
                    .toString();

                // Impl note: the method name is misleading here, as it is extracting the JSON from the
                // payload from a given string which contains the content access payload. That string *may*
                // be a cert, but it is not required.
                JsonNode root = CertificateUtil.decodeAndUncompressCertificate(contentAccessPayload,
                    ApiClient.MAPPER);

                JsonNode productNode = root.get("products").get(0);
                JsonNode contentNode = productNode.get("content").get(0);

                assertEquals(content.getId(), contentNode.get("id").textValue());
            }
        }
    }

    @Test
    public void shouldCorrectlyExportCertificatesInParallel() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer3 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer4 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer5 = adminClient.consumers().createConsumer(Consumers.random(owner));

        List<ConsumerDTO> consumers = List.of(consumer1, consumer2, consumer3, consumer4, consumer5);

        int maxNumberOfAttempts = 5;
        for (int i = 0; i < maxNumberOfAttempts; i++) {
            // Update the arch of all the consumers so that the content access payload key will be
            // different than the key used to store the content access payload previously. This causes the
            // regeneration of the content access payload which is what we need to cause the 429 response.
            String newArch = StringUtil.random("arch-");
            for (ConsumerDTO consumer : consumers) {
                consumer.putFactsItem(Facts.Arch.key(), newArch);
                adminClient.consumers().updateConsumer(consumer.getUuid(), consumer);
            }

            List<Callable<List<JsonNode>>> tasks = List.of(
                () -> adminClient.consumers().exportCertificates(consumer1.getUuid(), null),
                () -> adminClient.consumers().exportCertificates(consumer2.getUuid(), null),
                () -> adminClient.consumers().exportCertificates(consumer3.getUuid(), null),
                () -> adminClient.consumers().exportCertificates(consumer4.getUuid(), null),
                () -> adminClient.consumers().exportCertificates(consumer5.getUuid(), null));

            ExecutorService execService = Executors.newFixedThreadPool(consumers.size());
            List<Future<List<JsonNode>>> futures = execService.invokeAll(tasks);
            execService.shutdown();
            execService.awaitTermination(10L, TimeUnit.SECONDS);

            for (Future<List<JsonNode>> future : futures) {
                List<JsonNode> certs = future.get();
                Map<String, List<String>> prodIdToContentIds =
                    CertificateUtil.toProductContentIdMap(certs.get(0));

                assertThat(prodIdToContentIds)
                    .hasSize(1)
                    .extractingByKey("content_access", as(collection(String.class)))
                    .containsExactly(content.getId());
            }
        }
    }

    @Test
    public void shouldUpdateExistingContentAccessCertContentWhenProductDataChanges() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
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

    @Test
    public void shouldUpdateSecondExistingContentAccessCertContentWhenProductDataChanges() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner));

        ConsumerApi consumerApi = new ConsumerApi(adminClient.getApiClient());
        Object consumer1Export = consumerApi.exportCertificates(consumer1.getUuid(), null);
        List<String> cons1Certs = extractCertsFromPayload(consumer1Export);
        assertThat(cons1Certs).singleElement();
        String cons1InitialCert = cons1Certs.get(0);
        Object consumer2Export = consumerApi.exportCertificates(consumer2.getUuid(), null);
        List<String> cons2Certs = extractCertsFromPayload(consumer2Export);
        assertThat(cons2Certs).singleElement();
        String cons2InitialCert = cons2Certs.get(0);

        String updatedName = StringUtil.random("name-");
        content.setName(updatedName);
        adminClient.ownerContent().updateContent(ownerKey, content.getId(), content);

        consumer1Export = consumerApi.exportCertificates(consumer1.getUuid(), null);
        cons1Certs = extractCertsFromPayload(consumer1Export);
        assertThat(cons1Certs).singleElement();
        String updatedCons1Cert = cons1Certs.get(0);
        consumer2Export = consumerApi.exportCertificates(consumer2.getUuid(), null);
        cons2Certs = extractCertsFromPayload(consumer2Export);
        assertThat(cons2Certs).singleElement();
        String updatedCons2Cert = cons2Certs.get(0);

        // The cert should have changed due to the content change, but both consumers should still have the
        // same cert
        assertThat(updatedCons1Cert).isNotEqualTo(cons1InitialCert);
        assertThat(updatedCons2Cert).isNotEqualTo(cons2InitialCert);

        List<JsonNode> consumer2Certs = CertificateUtil
            .extractEntitlementCertificatesFromPayload(consumer2Export, ApiClient.MAPPER);

        assertThat(consumer2Certs).singleElement();
        JsonNode consumer2ActualContent = consumer2Certs.get(0).get("products").get(0).get("content").get(0);
        assertThat(consumer2ActualContent.get("name").asText())
            .isNotNull()
            .isEqualTo(updatedName);
    }

    @Test
    public void shouldUpdateExistingContentAccessCertContentWhenOrgContentPrefixChanges() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        assertThat(consumerClient.consumers().exportCertificates(consumer.getUuid(), null))
            .singleElement();

        // Update the content prefix on the org, which should trigger a refresh of at least the
        // content payload of the SCA cert
        owner.setContentPrefix("content_prefix");
        adminClient.owners().updateOwner(owner.getKey(), owner);

        List<JsonNode> certs = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        Map<String, List<String>> prodIdToContentIds = CertificateUtil.toProductContentIdMap(certs.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .containsExactly(content.getId());

        JsonNode certContent = certs.get(0).get("products").get(0).get("content").get(0);
        assertThat(certContent.get("path").asText())
            .contains(owner.getContentPrefix());
    }

    @Test
    public void shouldNotUpdateExistingContentAccessCertContentWhenNoDataChanges() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        // We need to sleep here to ensure enough time has passes from the last content update to when
        // we fetch certificates.
        Thread.sleep(1000);

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        List<CertificateDTO> certs = consumerClient.consumers().fetchCertificates(consumer.getUuid());
        assertThat(certs)
            .singleElement()
            .extracting(CertificateDTO::getSerial)
            .isNotNull();
        Long expectedSerial = certs.get(0).getSerial().getSerial();
        OffsetDateTime expectedUpdateDate = certs.get(0).getSerial().getUpdated();

        certs = consumerClient.consumers().fetchCertificates(consumer.getUuid());
        assertThat(certs)
            .singleElement()
            .extracting(CertificateDTO::getSerial)
            .isNotNull();
        Long updatedSerial = certs.get(0).getSerial().getSerial();
        OffsetDateTime updatedUpdateDate = certs.get(0).getSerial().getUpdated();

        assertThat(updatedSerial).isEqualTo(expectedSerial);
        assertThat(updatedUpdateDate).isEqualTo(expectedUpdateDate);
    }

    @Test
    public void shouldIncludeTheContentAccessCertSerialInSerialList() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        // We need to sleep here to ensure enough time has passed from the last content update to when
        // we fetch certificates.
        Thread.sleep(1000);

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        List<CertificateDTO> certs = adminClient.consumers().fetchCertificates(consumer.getUuid());
        assertThat(certs)
            .singleElement()
            .doesNotReturn(null, CertificateDTO::getSerial);

        Long expectedSerial = certs.get(0).getSerial().getSerial();
        List<CertificateSerialDTO> certSerials = consumerClient.consumers()
            .getEntitlementCertificateSerials(consumer.getUuid());
        assertThat(certSerials)
            .singleElement()
            .returns(expectedSerial, CertificateSerialDTO::getSerial);
    }

    @Test
    public void shouldSetTheCorrectContentAccessModeForAManifestConsumer() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner, ConsumerTypes.Candlepin));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumer.contentAccessMode(Owners.ENTITLEMENT_ACCESS_MODE);
        consumer.setReleaseVer(new ReleaseVerDTO().releaseVer(""));
        consumerClient.consumers().updateConsumer(consumer.getUuid(), consumer);
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .isNotNull()
            .returns(Owners.ENTITLEMENT_ACCESS_MODE, ConsumerDTO::getContentAccessMode);

        consumer.contentAccessMode(Owners.SCA_ACCESS_MODE);
        consumer.setReleaseVer(new ReleaseVerDTO().releaseVer(""));
        consumerClient.consumers().updateConsumer(consumer.getUuid(), consumer);
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .isNotNull()
            .returns(Owners.SCA_ACCESS_MODE, ConsumerDTO::getContentAccessMode);

        ConsumerDTO invalidConsumer = consumer;
        invalidConsumer.contentAccessMode("unknown mode");
        consumer.setReleaseVer(new ReleaseVerDTO().releaseVer(""));
        assertBadRequest(() -> consumerClient.consumers()
            .updateConsumer(invalidConsumer.getUuid(), invalidConsumer));

        // We should be able to remove the content access mode with an empty string
        consumer.contentAccessMode("");
        consumer.setReleaseVer(new ReleaseVerDTO().releaseVer(""));
        consumerClient.consumers().updateConsumer(consumer.getUuid(), consumer);
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .isNotNull()
            .returns(null, ConsumerDTO::getContentAccessMode);

        // We should also be able to set the correct content access mode when registering
        ConsumerDTO consumer2 = adminClient.consumers()
            .createConsumer(Consumers.random(owner, ConsumerTypes.Candlepin)
            .contentAccessMode(Owners.SCA_ACCESS_MODE));
        assertThat(consumer2)
            .isNotNull()
            .returns(Owners.SCA_ACCESS_MODE, ConsumerDTO::getContentAccessMode);
    }

    @Test
    public void shouldNotSetThecontentAccessModeForARegularConsumer() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumer.setReleaseVer(new ReleaseVerDTO().releaseVer(""));

        consumer.contentAccessMode(Owners.ENTITLEMENT_ACCESS_MODE);

        assertBadRequest(() -> consumerClient.consumers().updateConsumer(consumer.getUuid(), consumer));
    }

    @Test
    public void shouldProduceAPredatedContentAccessCertificate() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        ConsumerApi consumerApi = new ConsumerApi(consumerClient.getApiClient());
        Object export = consumerApi.exportCertificates(consumer.getUuid(), null);
        List<String> certs = extractCertsFromPayload(export);
        assertThat(certs).singleElement();

        Date expectedBefore = Date.from(OffsetDateTime.now().minusHours(1).minusMinutes(1).toInstant());
        Date actualNotBefore = CertificateUtil.getCertNotBefore(certs.get(0));
        assertThat(actualNotBefore)
            .isAfter(expectedBefore);
    }

    @Test
    public void shouldNotAutoAttachWhenOrgEnvironmentIsSetForOwner() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ProductDTO prod1 = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        adminClient.owners().createPool(ownerKey, Pools.random(prod1));
        ProductDTO prod2 = adminClient.ownerProducts().createProduct(ownerKey, Products.random());

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumer.setReleaseVer(new ReleaseVerDTO().releaseVer(""));
        consumer.installedProducts(Set.of(Products.toInstalled(prod2)));
        consumerClient.consumers().updateConsumer(consumer.getUuid(), consumer);

        assertBadRequest(() -> consumerClient.consumers()
            .bind(consumer.getUuid(), null, List.of(), null, null, null, false, null, List.of()));

        // confirm that there is a content access cert
        // and only a content access cert
        List<JsonNode> certs = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(certs).singleElement();
        JsonNode certContent = certs.get(0).get("products").get(0).get("content");
        assertThat(certContent).singleElement();
        compareCertContent(content, certContent.get(0));
        verifyCertContentPath(owner.getContentPrefix(), content.getContentUrl(), null,
            certContent.get(0).get("path").asText());
    }

    @Test
    public void shouldRegenerateSCACertWhenEnvironmentContentChanges() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        EnvironmentDTO env = adminClient.owners().createEnvironment(ownerKey, Environments.random());
        promoteContentToEnvironment(adminClient, env.getId(), content, false);

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .environment(env));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        List<CertificateDTO> certs = consumerClient.consumers().fetchCertificates(consumer.getUuid());
        assertThat(certs).singleElement();
        String initialCert = certs.get(0).getCert();

        content.contentUrl("/updated/path");
        adminClient.ownerContent().updateContent(ownerKey, content.getId(), content);

        certs = consumerClient.consumers().fetchCertificates(consumer.getUuid());
        assertThat(certs).singleElement();
        String updatedCert = certs.get(0).getCert();

        assertThat(updatedCert).isNotEqualTo(initialCert);
    }

    @Test
    public void shouldHonourTheContentDefaultsForOwnerInSCAMode() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO modifiedProd = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.random());
        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO enabledContent = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .modifiedProductIds(Set.of(modifiedProd.getId())));
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), enabledContent.getId(), true);

        ContentDTO disabledContent = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .modifiedProductIds(Set.of(modifiedProd.getId())));
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, prod.getId(), disabledContent.getId(), false);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        List<JsonNode> entCerts = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(entCerts).singleElement();
        Map<String, List<String>> prodIdToContentIds = CertificateUtil.toProductContentIdMap(entCerts.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .containsExactlyInAnyOrder(enabledContent.getId(), disabledContent.getId());

        JsonNode content = entCerts.get(0).get("products").get(0).get("content");
        JsonNode certEnabledContent = enabledContent.getId().equals(content.get(0).get("id").asText()) ?
            content.get(0) : content.get(1);
        JsonNode certDisabledContent = disabledContent.getId().equals(content.get(0).get("id").asText()) ?
            content.get(0) : content.get(1);

        assertNull(certEnabledContent.get("enabled"));
        assertFalse(certDisabledContent.get("enabled").asBoolean());
    }

    @Test
    public void shouldFilterOutContentNotPromotedToEnvironmentWhenOwnerIsInSCAMode() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        EnvironmentDTO env = adminClient.owners().createEnvironment(ownerKey, Environments.random());
        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner).addEnvironmentsItem(env));
        assertThat(consumer.getEnvironments()).singleElement().returns(env.getId(), EnvironmentDTO::getId);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO promotedContent = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        ContentDTO notPromotedContent = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, prod.getId(), promotedContent.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, prod.getId(), notPromotedContent.getId(), true);
        promoteContentToEnvironment(adminClient, env.getId(), promotedContent, false);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

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
            .containsExactlyInAnyOrder(promotedContent.getId());

        JsonNode actual = certs.get(0).get("products").get(0).get("content").get(0);
        assertThat(actual.get("id").asText()).isEqualTo(promotedContent.getId());
        assertFalse(actual.get("enabled").asBoolean());

        List<String> payloadCerts = extractCertsFromPayload(export);
        assertThat(payloadCerts).singleElement();
        assertThatCert(X509Cert.from(payloadCerts.get(0)))
            .hasVersion("3.4");
    }

    @Test
    public void shouldHandleMixedEnabledmentOfContentForOwnerInSCAMode() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod1 = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ProductDTO prod2 = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO cont1 = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        ContentDTO cont2 = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        ContentDTO cont3 = adminClient.ownerContent().createContent(ownerKey, Contents.random());

        // Content enabled in both product
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod1.getId(), cont1.getId(), true);
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod2.getId(), cont1.getId(), true);

        // Mixed content enablement in both product
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod1.getId(), cont2.getId(), false);
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod2.getId(), cont2.getId(), true);

        // Content disabled in both product
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod1.getId(), cont3.getId(), false);
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod2.getId(), cont3.getId(), false);

        adminClient.owners().createPool(ownerKey, Pools.random(prod1));
        adminClient.owners().createPool(ownerKey, Pools.random(prod2));
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        List<JsonNode> entCerts = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(entCerts).singleElement();
        Map<String, List<String>> prodIdToContentIds = CertificateUtil.toProductContentIdMap(entCerts.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .hasSize(3)
            .containsExactlyInAnyOrder(cont1.getId(), cont2.getId(), cont3.getId());

        JsonNode content = entCerts.get(0).get("products").get(0).get("content");
        content.iterator().forEachRemaining(cont -> {
            if (cont1.getId().equals(cont.get("id").asText())) {
                assertNull(cont.get("enabled"));
            }

            if (cont2.getId().equals(cont.get("id").asText())) {
                assertNull(cont.get("enabled"));
            }

            if (cont3.getId().equals(cont.get("id").asText())) {
                assertFalse(cont.get("enabled").asBoolean());
            }
        });
    }

    @Test
    public void shouldOnlyAddContentFromActivePoolsOnTheSCACertificate() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO modifiedProd = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.random());
        ProductDTO prod1 = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ProductDTO prod2 = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO cont1 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .modifiedProductIds(Set.of(modifiedProd.getId())));
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod1.getId(), cont1.getId(), true);
        ContentDTO cont2 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .modifiedProductIds(Set.of(modifiedProd.getId())));
        adminClient.owners().createPool(ownerKey, Pools.random(prod2));
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod2.getId(), cont2.getId(), true);

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        // Make sure that content cont1 is not present in cert,
        // since pro1 does not have active pool
        List<JsonNode> entCerts = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(entCerts).singleElement();
        Map<String, List<String>> prodIdToContentIds = CertificateUtil.toProductContentIdMap(entCerts.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .hasSize(1)
            .containsOnly(cont2.getId());
    }

    @Test
    public void shouldIncludeContentFromAllProductsAssociatedWithActivePoolToSCACert() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO modifiedProd = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.random());
        ProductDTO provProd = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ProductDTO derivedProd = adminClient.ownerProducts().createProduct(ownerKey, Products.random()
            .providedProducts(Set.of(provProd)));
        ProductDTO engProd = adminClient.ownerProducts().createProduct(ownerKey, Products.randomEng());
        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random()
            .providedProducts(Set.of(engProd))
            .derivedProduct(derivedProd));

        ContentDTO cont1 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .modifiedProductIds(Set.of(modifiedProd.getId())));
        ContentDTO cont2 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .modifiedProductIds(Set.of(modifiedProd.getId())));
        ContentDTO cont3 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .modifiedProductIds(Set.of(modifiedProd.getId())));
        ContentDTO cont4 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .modifiedProductIds(Set.of(modifiedProd.getId())));

        adminClient.ownerProducts().addContentToProduct(ownerKey, engProd.getId(), cont1.getId(), true);
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), cont2.getId(), true);
        adminClient.ownerProducts().addContentToProduct(ownerKey, derivedProd.getId(), cont3.getId(), true);
        adminClient.ownerProducts().addContentToProduct(ownerKey, provProd.getId(), cont4.getId(), true);

        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        List<JsonNode> entCerts = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(entCerts).singleElement();
        Map<String, List<String>> prodIdToContentIds = CertificateUtil.toProductContentIdMap(entCerts.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .hasSize(4)
            .containsExactlyInAnyOrder(cont1.getId(), cont2.getId(), cont3.getId(), cont4.getId());
    }

    @Test
    public void shouldRegenerateSCACertWhenContentChangesAffectContentView() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        List<CertificateDTO> certs = consumerClient.consumers().fetchCertificates(consumer.getUuid());
        assertThat(certs).singleElement();
        String initialCert = certs.get(0).getCert();

        content.contentUrl("/updated/path");
        adminClient.ownerContent().updateContent(ownerKey, content.getId(), content);

        certs = consumerClient.consumers().fetchCertificates(consumer.getUuid());
        assertThat(certs).singleElement();
        String updatedCert = certs.get(0).getCert();

        assertThat(updatedCert).isNotEqualTo(initialCert);
    }

    @Test
    public void shouldRegenerateSCACertWhenProductChangesAffectContentView() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        List<CertificateDTO> certs = consumerClient.consumers().fetchCertificates(consumer.getUuid());
        assertThat(certs).singleElement();
        String initialCert = certs.get(0).getCert();

        adminClient.ownerProducts().removeContentFromProduct(ownerKey, prod.getId(), content.getId());

        certs = consumerClient.consumers().fetchCertificates(consumer.getUuid());
        assertThat(certs).singleElement();
        String updatedCert = certs.get(0).getCert();

        assertThat(updatedCert).isNotEqualTo(initialCert);
    }

    @Test
    public void shouldRegenerateSCACertWhenPoolChangesAffectContentView() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        PoolDTO pool = adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        List<CertificateDTO> certs = consumerClient.consumers().fetchCertificates(consumer.getUuid());
        assertThat(certs).singleElement();
        String initialCert = certs.get(0).getCert();

        pool.startDate(OffsetDateTime.now().plusDays(1));
        adminClient.owners().updatePool(ownerKey, pool);

        certs = consumerClient.consumers().fetchCertificates(consumer.getUuid());
        assertThat(certs).singleElement();
        String updatedCert = certs.get(0).getCert();

        assertThat(updatedCert).isNotEqualTo(initialCert);
    }

    @Test
    public void shouldHaveDisabledPurposeComplianceForOwnerInSCAMode() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        // System purpose status
        SystemPurposeComplianceStatusDTO status = consumerClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status)
            .isNotNull()
            .returns("disabled", SystemPurposeComplianceStatusDTO::getStatus);

        // compliance status
        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid(), null);
        assertThat(complianceStatus)
            .isNotNull()
            .returns("disabled", ComplianceStatusDTO::getStatus);
    }

    @Test
    public void shouldRevokeSCACertsUponUnRegistration() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        List<CertificateDTO> certs = adminClient.consumers().fetchCertificates(consumer.getUuid());
        assertThat(certs).singleElement();
        assertNotNull(certs.get(0).getSerial());
        Long expectedSerial = certs.get(0).getSerial().getSerial();
        boolean revoked = certs.get(0).getSerial().getRevoked();
        assertFalse(revoked);

        consumerClient.consumers().deleteConsumer(consumer.getUuid());
        CertificateSerialDTO serialAfterUnReg = adminClient.certificateSerial()
            .getCertificateSerial(expectedSerial);
        assertThat(serialAfterUnReg)
            .isNotNull()
            .returns(true, CertificateSerialDTO::getRevoked);
    }

    @Test
    public void shouldRegenerateSCACertificateOfAffectedConsumersWhenEnvIsDeleted() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);

        EnvironmentDTO env1 = adminClient.owners().createEnvironment(ownerKey, Environments.random());
        EnvironmentDTO env2 = adminClient.owners().createEnvironment(ownerKey, Environments.random());
        promoteContentToEnvironment(adminClient, env2.getId(), content, true);

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .environments(List.of(env1, env2)));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        List<CertificateDTO> oldCerts = consumerClient.consumers().fetchCertificates(consumer.getUuid());
        assertThat(oldCerts)
            .singleElement()
            .extracting(CertificateDTO::getSerial)
            .isNotNull();
        Long initialSerial = oldCerts.get(0).getSerial().getSerial();

        adminClient.environments().deleteEnvironment(env1.getId(), true);

        List<CertificateDTO> newCerts = consumerClient.consumers().fetchCertificates(consumer.getUuid());
        assertThat(newCerts)
            .singleElement()
            .extracting(CertificateDTO::getSerial)
            .isNotNull()
            .extracting(CertificateSerialDTO::getSerial)
            .isNotEqualTo(initialSerial);
    }

    private String contentUrl(String ownerKey, EnvironmentDTO env) {
        return "/" + ownerKey + "/" + env.getName();
    }

    private String contentUrl(String ownerKey) {
        return "/" + ownerKey;
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

    private void verifyCertContentPath(String ownerKey, String contentUrl, EnvironmentDTO env,
        String cerContentPath) {
        String ownerPrefix = ownerKey != null ? ownerKey : "";
        String envPrefix = env != null && env.getContentPrefix() != null ? env.getContentPrefix() : "";
        String expected = ownerPrefix + envPrefix + contentUrl;
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

    private String getUpdatedFromCertExport(Object export) {
        if (export == null) {
            return null;
        }

        List<Map<String, String>> certs = ((List<Map<String, String>>) export);
        if (certs.size() == 0) {
            return null;
        }

        return certs.get(0).get("updated");
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
