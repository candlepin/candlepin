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
package org.candlepin.spec.environments;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.assertj.core.api.InstanceOfAssertFactories.iterable;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.ContentToPromoteDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.EnvironmentDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Environments;
import org.candlepin.spec.bootstrap.data.builder.Facts;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.CertificateUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Set;


@SpecTest
public class EnvironmentCertV3SpecTest {

    private static final String ENT_VERSION_OID = "1.3.6.1.4.1.2312.9.6";

    @Test
    public void shouldFilterContentNotPromotedToEnvironment() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();

        ApiClient orgAdmin = ApiClients.basic(UserUtil.createAdminUser(adminClient, owner));
        EnvironmentDTO env = orgAdmin.owners().createEnvironment(ownerKey, Environments.random());
        ConsumerDTO consumer = orgAdmin.consumers().createConsumer(Consumers.random(owner)
            .addEnvironmentsItem(env)
            .facts(Map.ofEntries(Facts.CertificateVersion.withValue("3.1"))));
        assertThat(consumer)
            .isNotNull()
            .extracting(ConsumerDTO::getEnvironments, as(collection(EnvironmentDTO.class)))
            .singleElement();

        ApiClient consumerClient = ApiClients.ssl(consumer);
        ProductDTO product = adminClient.ownerProducts().createProduct(ownerKey, Products.randomEng());
        ContentDTO promotedContent = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        ContentDTO notPromotedContent = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, product.getId(), promotedContent.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, product.getId(), notPromotedContent.getId(), true);
        promoteContentToEnvironment(orgAdmin, env.getId(), promotedContent, false);
        adminClient.owners().createPool(ownerKey, Pools.random(product));

        JsonNode ents = consumerClient.consumers().bindProduct(consumer.getUuid(), product.getId());

        assertThat(ents).singleElement();
        String cert = getCertFromEnt(ents.get(0));
        assertThat(CertificateUtil.getDerValueFromExtension(cert, ENT_VERSION_OID).toString())
            .isEqualTo("3.4");
        JsonNode entCert = CertificateUtil.decodeAndUncompressCertificate(cert, ApiClient.MAPPER);
        assertThat(entCert.get("products"))
            .singleElement()
            .extracting(node -> node.get("content"), as(iterable(JsonNode.class)))
            .singleElement()
            .returns(promotedContent.getId(), cont -> cont.get("id").asText());
    }

    @Test
    public void shouldRegenerateCertificatesAfterPromotingAndDemotingContent() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();

        ApiClient orgAdmin = ApiClients.basic(UserUtil.createAdminUser(adminClient, owner));
        EnvironmentDTO env = orgAdmin.owners().createEnvironment(ownerKey, Environments.random());
        ConsumerDTO consumer = orgAdmin.consumers().createConsumer(Consumers.random(owner)
            .addEnvironmentsItem(env)
            .facts(Map.ofEntries(Facts.CertificateVersion.withValue("3.1"))));
        assertThat(consumer)
            .isNotNull()
            .extracting(ConsumerDTO::getEnvironments, as(collection(EnvironmentDTO.class)))
            .singleElement();

        ApiClient consumerClient = ApiClients.ssl(consumer);
        ProductDTO product = adminClient.ownerProducts().createProduct(ownerKey, Products.randomEng());
        ContentDTO promotedContent = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        ContentDTO notPromotedContent = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, product.getId(), promotedContent.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, product.getId(), notPromotedContent.getId(), true);
        promoteContentToEnvironment(orgAdmin, env.getId(), promotedContent, true);
        adminClient.owners().createPool(ownerKey, Pools.random(product));

        JsonNode ents = consumerClient.consumers().bindProduct(consumer.getUuid(), product.getId());

        assertThat(ents).singleElement();
        String cert = getCertFromEnt(ents.get(0));
        assertThat(CertificateUtil.standardExtensionValueFromCert(cert, ENT_VERSION_OID))
            .isEqualTo("3.4");
        JsonNode entCert = CertificateUtil.decodeAndUncompressCertificate(cert, ApiClient.MAPPER);

        JsonNode products = entCert.get("products");
        assertThat(products)
            .singleElement()
            .extracting(node -> node.get("content"), as(iterable(JsonNode.class)))
            .singleElement()
            .returns(promotedContent.getId(), cont -> cont.get("id").asText());

        Long serial1 = ents.get(0).get("certificates").get(0).get("serial").get("serial").asLong();

        // Promote the other content set and make sure certs were regenerated:
        promoteContentToEnvironment(orgAdmin, env.getId(), notPromotedContent, true);
        EntitlementDTO ent = consumerClient.consumers().listEntitlementsWithRegen(consumer.getUuid()).get(0);
        entCert = CertificateUtil.decodeAndUncompressCertificate(ent.getCertificates()
            .iterator().next().getCert(), ApiClient.MAPPER);

        products = entCert.get("products");
        assertThat(products)
            .singleElement()
            .extracting(node -> node.get("content"), as(iterable(JsonNode.class)))
            .hasSize(2)
            .extracting(cont -> cont.get("id").asText())
            .containsExactlyInAnyOrder(promotedContent.getId(), notPromotedContent.getId());

        Long serial2 = ent.getCertificates().iterator().next().getSerial().getSerial();
        assertThat(serial2).isNotEqualTo(serial1);

        // Demote content and check again:
        demoteContentFromEnvironment(orgAdmin, env.getId(), notPromotedContent);
        ent = consumerClient.consumers().listEntitlementsWithRegen(consumer.getUuid()).get(0);
        entCert = CertificateUtil.decodeAndUncompressCertificate(ent.getCertificates()
            .iterator().next().getCert(), ApiClient.MAPPER);

        products = entCert.get("products");
        assertThat(products)
            .singleElement()
            .extracting(node -> node.get("content"), as(iterable(JsonNode.class)))
            .singleElement()
            .returns(promotedContent.getId(), cont -> cont.get("id").asText());

        Long serial3 = ent.getCertificates().iterator().next().getSerial().getSerial();
        assertThat(serial3).isNotEqualTo(serial2);
    }

    @Test
    public void shouldRegenerateCertificatesAfterPromotingAndDemotingContentForProvidedProduct() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();

        ApiClient orgAdmin = ApiClients.basic(UserUtil.createAdminUser(adminClient, owner));
        EnvironmentDTO env = orgAdmin.owners().createEnvironment(ownerKey, Environments.random());
        ConsumerDTO consumer = orgAdmin.consumers().createConsumer(Consumers.random(owner)
            .addEnvironmentsItem(env)
            .facts(Map.ofEntries(Facts.CertificateVersion.withValue("3.1"))));
        assertThat(consumer)
            .isNotNull()
            .extracting(ConsumerDTO::getEnvironments, as(collection(EnvironmentDTO.class)))
            .singleElement();

        ApiClient consumerClient = ApiClients.ssl(consumer);
        ProductDTO provProd = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng().providedProducts(Set.of(provProd)));
        ContentDTO promotedContent = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        ContentDTO notPromotedContent = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        ContentDTO notPromotedProvContent = adminClient.ownerContent()
            .createContent(ownerKey, Contents.random());

        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, product.getId(), promotedContent.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, product.getId(), notPromotedContent.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, provProd.getId(), notPromotedProvContent.getId(), true);
        promoteContentToEnvironment(orgAdmin, env.getId(), promotedContent, true);
        adminClient.owners().createPool(ownerKey, Pools.random(product));

        JsonNode ents = consumerClient.consumers().bindProduct(consumer.getUuid(), product.getId());

        assertThat(ents).singleElement();
        String cert = getCertFromEnt(ents.get(0));
        assertThat(CertificateUtil.getDerValueFromExtension(cert, ENT_VERSION_OID).toString())
            .isEqualTo("3.4");

        JsonNode entCert = CertificateUtil.decodeAndUncompressCertificate(cert, ApiClient.MAPPER);
        JsonNode products = entCert.get("products");
        assertThat(products).hasSize(2);

        JsonNode actualProd = product.getId().equals(products.get(0).get("id").asText()) ?
            products.get(0) : products.get(1);
        assertThat(actualProd.get("content"))
            .singleElement()
            .returns(promotedContent.getId(), cont -> cont.get("id").asText());

        Long serial1 = ents.get(0).get("certificates").get(0).get("serial").get("serial").asLong();

        // Promote content on the provided product and check
        promoteContentToEnvironment(orgAdmin, env.getId(), notPromotedProvContent, true);
        EntitlementDTO ent = consumerClient.consumers().listEntitlementsWithRegen(consumer.getUuid()).get(0);
        entCert = CertificateUtil.decodeAndUncompressCertificate(ent.getCertificates()
            .iterator().next().getCert(), ApiClient.MAPPER);
        products = entCert.get("products");
        assertThat(products).hasSize(2);
        JsonNode actualProvidedProd = null;
        if (product.getId().equals(products.get(0).get("id").asText())) {
            actualProd = products.get(0);
            actualProvidedProd = products.get(1);
        }
        else {
            actualProvidedProd = products.get(0);
            actualProd = products.get(1);
        }

        assertThat(actualProd.get("content"))
            .singleElement()
            .returns(promotedContent.getId(), cont -> cont.get("id").asText());
        assertThat(actualProvidedProd.get("content"))
            .singleElement()
            .returns(notPromotedProvContent.getId(), cont -> cont.get("id").asText());

        Long serial2 = ent.getCertificates().iterator().next().getSerial().getSerial();
        assertThat(serial2).isNotEqualTo(serial1);

        // Demote content on the provided product and check
        demoteContentFromEnvironment(orgAdmin, env.getId(), notPromotedProvContent);
        ent = consumerClient.consumers().listEntitlementsWithRegen(consumer.getUuid()).get(0);
        entCert = CertificateUtil.decodeAndUncompressCertificate(ent.getCertificates()
            .iterator().next().getCert(), ApiClient.MAPPER);
        products = entCert.get("products");
        assertThat(products).hasSize(2);
        if (product.getId().equals(products.get(0).get("id").asText())) {
            actualProd = products.get(0);
            actualProvidedProd = products.get(1);
        }
        else {
            actualProvidedProd = products.get(0);
            actualProd = products.get(1);
        }

        assertThat(actualProd.get("content"))
            .singleElement()
            .returns(promotedContent.getId(), cont -> cont.get("id").asText());
        assertThat(actualProvidedProd.get("content"))
            .isEmpty();

        Long serial3 = ent.getCertificates().iterator().next().getSerial().getSerial();
        assertThat(serial3).isNotEqualTo(serial2);
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

    private void demoteContentFromEnvironment(ApiClient client, String envId, ContentDTO content) {
        AsyncJobStatusDTO job = client.environments().demoteContent(envId, List.of(content.getId()), true);
        job = client.jobs().waitForJob(job);
        assertThatJob(job).isFinished();
    }

    private String getCertFromEnt(JsonNode ent) {
        JsonNode certs = ent.get("certificates");
        assertThat(certs).isNotNull().isNotEmpty();

        return certs.get(0).get("cert").asText();
    }

}
