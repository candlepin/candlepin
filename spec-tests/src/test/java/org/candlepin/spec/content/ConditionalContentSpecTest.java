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

import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.cert.X509Cert;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.OID;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.CertificateUtil;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@SpecTest
public class ConditionalContentSpecTest {

    @Test
    public void shouldIncludeConditionalContentSets() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO reqProd1 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO reqProd2 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO reqProd3 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO bundledProd = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng()
            .providedProducts(Set.of(reqProd1, reqProd2, reqProd3)));
        adminClient.owners().createPool(ownerKey, Pools.random(bundledProd));

        // Create our dependent provided product, which carries content sets -- each of which of which
        // requires one of the provided products above
        ProductDTO dependentProvProd = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ContentDTO conditionalContent1 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .id(randomContentId(5))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd1.getId())));
        ContentDTO conditionalContent2 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .id(randomContentId(5))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd2.getId())));
        ContentDTO conditionalContent3 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .id(randomContentId(5))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd3.getId())));
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, dependentProvProd.getId(), conditionalContent1.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, dependentProvProd.getId(), conditionalContent2.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, dependentProvProd.getId(), conditionalContent3.getId(), true);

        ProductDTO dependentProd = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng()
            .providedProducts(Set.of(dependentProvProd)));
        adminClient.owners().createPool(ownerKey, Pools.random(dependentProd));

        // Bind to the normal subscription first
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0")));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().bindProduct(consumer.getUuid(), bundledProd.getId());

        // Bind to the dependent subscription which requires the product(s) provided by the previously
        // bound subscription:
        JsonNode ents = consumerClient.consumers().bindProduct(consumer.getUuid(), dependentProd.getId());

        assertThatCert(X509Cert.fromEnt(ents.get(0)))
            .hasContentRepoType(conditionalContent1)
            .hasContentRepoType(conditionalContent2)
            .hasContentRepoType(conditionalContent3);
    }

    @Test
    public void shouldIncludeConditionalContentSetsSelectively() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO reqProd1 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO reqProd2 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO reqProd3 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO bundledProd1 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng()
            .providedProducts(Set.of(reqProd1, reqProd2, reqProd3)));
        adminClient.owners().createPool(ownerKey, Pools.random(bundledProd1));
        ProductDTO bundledProd2 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng()
            .providedProducts(Set.of(reqProd1, reqProd2)));
        adminClient.owners().createPool(ownerKey, Pools.random(bundledProd2));

        // Create our dependent provided product, which carries content sets -- each of which of which
        // requires one of the provided products above
        ProductDTO dependentProvProd = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ContentDTO conditionalContent1 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .id(randomContentId(5))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd1.getId())));
        ContentDTO conditionalContent2 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .id(randomContentId(5))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd2.getId())));
        ContentDTO conditionalContent3 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .id(randomContentId(5))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd3.getId())));
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, dependentProvProd.getId(), conditionalContent1.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, dependentProvProd.getId(), conditionalContent2.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, dependentProvProd.getId(), conditionalContent3.getId(), true);

        ProductDTO dependentProd = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.random()
            .providedProducts(Set.of(dependentProvProd)));
        adminClient.owners().createPool(ownerKey, Pools.random(dependentProd));

        // Bind to the normal subscription first
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0")));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().bindProduct(consumer.getUuid(), bundledProd2.getId());

        // Bind to the dependent subscription which requires the product(s) provided by the previously
        // bound subscription:
        JsonNode ents = consumerClient.consumers().bindProduct(consumer.getUuid(), dependentProd.getId());
        assertThatCert(X509Cert.fromEnt(ents.get(0)))
            .hasContentRepoType(conditionalContent1)
            .hasContentRepoType(conditionalContent2)
            .doesNotHaveContentRepoType(conditionalContent3);
    }

    @Test
    public void shouldNotIncludeConditionalContentWithoutTheRequiredProducts() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO reqProd1 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO reqProd2 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO reqProd3 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());

        // Create our dependent provided product, which carries content sets -- each of which of which
        // requires one of the provided products above
        ProductDTO dependentProvProd = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.random());
        ContentDTO conditionalContent1 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .modifiedProductIds(Set.of(reqProd1.getId())));
        ContentDTO conditionalContent2 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .modifiedProductIds(Set.of(reqProd2.getId())));
        ContentDTO conditionalContent3 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .modifiedProductIds(Set.of(reqProd3.getId())));
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, dependentProvProd.getId(), conditionalContent1.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, dependentProvProd.getId(), conditionalContent2.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, dependentProvProd.getId(), conditionalContent3.getId(), true);

        ProductDTO dependentProd = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.random()
            .providedProducts(Set.of(dependentProvProd)));
        adminClient.owners().createPool(ownerKey, Pools.random(dependentProd));

        // Bind to the dependent subscription without being entitled to any of the required products
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0")));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        JsonNode ents = consumerClient.consumers().bindProduct(consumer.getUuid(), dependentProd.getId());
        assertThatCert(X509Cert.fromEnt(ents.get(0)))
            .doesNotHaveContentRepoType(conditionalContent1)
            .doesNotHaveContentRepoType(conditionalContent2)
            .doesNotHaveContentRepoType(conditionalContent3);
    }

    @Test
    public void shouldRegenerateCertificateWhenConsumerReceivesAccessToARequiredProduct() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO reqProd1 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO reqProd2 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO reqProd3 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO bundledProd = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng()
            .providedProducts(Set.of(reqProd1, reqProd2, reqProd3)));
        adminClient.owners().createPool(ownerKey, Pools.random(bundledProd));

        ProductDTO dependentProvProd = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ContentDTO conditionalContent1 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .id(randomContentId(5))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd1.getId())));
        ContentDTO conditionalContent2 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .id(randomContentId(5))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd2.getId())));
        ContentDTO conditionalContent3 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .id(randomContentId(5))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd3.getId())));
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, dependentProvProd.getId(), conditionalContent1.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, dependentProvProd.getId(), conditionalContent2.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, dependentProvProd.getId(), conditionalContent3.getId(), true);

        ProductDTO dependentProd = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng()
            .providedProducts(Set.of(dependentProvProd)));
        adminClient.owners().createPool(ownerKey, Pools.random(dependentProd));

        // Bind to the dependent subscription without being entitled to any of the required products
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0")));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        JsonNode ents = consumerClient.consumers().bindProduct(consumer.getUuid(), dependentProd.getId());

        // Resulting dependent cert should not contain any of the conditional content sets
        assertThatCert(X509Cert.fromEnt(ents.get(0)))
            .doesNotHaveExtension(OID.contentRepoType(conditionalContent1))
            .doesNotHaveExtension(OID.contentRepoType(conditionalContent2))
            .doesNotHaveExtension(OID.contentRepoType(conditionalContent3));
        JsonNode dependentProdCerts = ents.get(0).get("certificates");
        Long dependentProdCertSerial = dependentProdCerts.get(0).get("serial").get("serial").asLong();

        // Bind to the required product...
        ents = consumerClient.consumers().bindProduct(consumer.getUuid(), bundledProd.getId());
        assertThat(ents)
            .isNotNull()
            .singleElement();
        JsonNode bundledProdCerts = ents.get(0).get("certificates");
        Long bundledProdSerial = bundledProdCerts.get(0).get("serial").get("serial").asLong();

        List<CertificateDTO> certs = adminClient.consumers().fetchCertificates(consumer.getUuid());

        // Old certificate should be gone
        Map<Long, String> serialToCert = getSerialToCert(certs);
        assertThat(serialToCert.keySet())
            .hasSize(2)
            .doesNotContain(dependentProdCertSerial)
            .contains(bundledProdSerial);

        // Remove the pre-existing serial and cert leaving the newly generated serial and cert
        serialToCert.remove(bundledProdSerial);
        String newCertValue = serialToCert.entrySet().iterator().next().getValue();
        // And it should have the conditional content set
        assertThatCert(X509Cert.from(newCertValue))
            .hasContentRepoType(conditionalContent1)
            .hasContentRepoType(conditionalContent2)
            .hasContentRepoType(conditionalContent3);
    }

    @Test
    public void shouldRegenerateWhenTheConsumerLosesAccessToRequiredProducts() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO reqProd1 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO reqProd2 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO reqProd3 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO bundledProd1 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng()
            .providedProducts(Set.of(reqProd1, reqProd2, reqProd3)));
        adminClient.owners().createPool(ownerKey, Pools.random(bundledProd1));
        ProductDTO bundledProd2 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng()
            .providedProducts(Set.of(reqProd1, reqProd2)));
        adminClient.owners().createPool(ownerKey, Pools.random(bundledProd2));

        // Create our dependent provided product, which carries content sets -- each of which of which
        // requires one of the provided products above
        ProductDTO dependentProvProd = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ContentDTO conditionalContent1 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .id(randomContentId(5))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd1.getId())));
        ContentDTO conditionalContent2 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .id(randomContentId(5))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd2.getId())));
        ContentDTO conditionalContent3 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .id(randomContentId(5))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd3.getId())));
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, dependentProvProd.getId(), conditionalContent1.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, dependentProvProd.getId(), conditionalContent2.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, dependentProvProd.getId(), conditionalContent3.getId(), true);

        ProductDTO dependentProd = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng()
            .providedProducts(Set.of(dependentProvProd)));
        adminClient.owners().createPool(ownerKey, Pools.random(dependentProd));

        // Bind to the dependent subscription without being entitled to any of the required products
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0")));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        JsonNode ents = consumerClient.consumers().bindProduct(consumer.getUuid(), dependentProd.getId());
        assertThat(ents).singleElement();
        String dependentProdEntId = ents.get(0).get("id").asText();

        // Verify that we don't have any content repos yet...
        assertThatCert(X509Cert.fromEnt(ents.get(0)))
            .doesNotHaveContentRepoType(conditionalContent1)
            .doesNotHaveContentRepoType(conditionalContent2)
            .doesNotHaveContentRepoType(conditionalContent3);

        // Bind to a normal subscription...
        ents = consumerClient.consumers().bindProduct(consumer.getUuid(), bundledProd2.getId());
        assertThat(ents).isNotNull().singleElement();
        String entId = ents.get(0).get("id").asText();
        List<String> entsToRevoke = new ArrayList<>();
        entsToRevoke.add(entId);

        // Re-fetch the modifier entitlement...
        EntitlementDTO entitlement = consumerClient.entitlements().getEntitlement(dependentProdEntId);
        assertThat(entitlement)
            .isNotNull()
            .extracting(EntitlementDTO::getCertificates, as(collection(CertificateDTO.class)))
            .singleElement();

        // Modifier certificate should now contain some conditional content...
        CertificateDTO entCert = entitlement.getCertificates().iterator().next();
        assertThatCert(X509Cert.from(entCert))
            .hasContentRepoType(conditionalContent1)
            .hasContentRepoType(conditionalContent2)
            .doesNotHaveContentRepoType(conditionalContent3);

        // Bind to another normal subscription...
        ents = consumerClient.consumers().bindProduct(consumer.getUuid(), bundledProd1.getId());
        assertThat(ents).isNotNull().singleElement();
        entId = ents.get(0).get("id").asText();
        entsToRevoke.add(entId);

        // Re-fetch the modifier entitlement...
        entitlement = consumerClient.entitlements().getEntitlement(dependentProdEntId);
        assertThat(entitlement)
            .isNotNull()
            .extracting(EntitlementDTO::getCertificates, as(collection(CertificateDTO.class)))
            .singleElement();

        // Modifier certificate should now contain all conditional content...
        entCert = entitlement.getCertificates().iterator().next();
        assertThatCert(X509Cert.from(entCert))
            .hasContentRepoType(conditionalContent1)
            .hasContentRepoType(conditionalContent2)
            .hasContentRepoType(conditionalContent3);

        // Unbind the pools to revoke our entitlements...
        revokeEnts(consumerClient, consumer.getUuid(), entsToRevoke);

        // Re-fetch the modifier entitlement...
        entitlement = consumerClient.entitlements().getEntitlement(dependentProdEntId);
        assertThat(entitlement)
            .isNotNull()
            .extracting(EntitlementDTO::getCertificates, as(collection(CertificateDTO.class)))
            .singleElement();

        // Verify that we don't have any content repos anymore
        entCert = entitlement.getCertificates().iterator().next();
        assertThatCert(X509Cert.from(entCert))
            .doesNotHaveContentRepoType(conditionalContent1)
            .doesNotHaveContentRepoType(conditionalContent2)
            .doesNotHaveContentRepoType(conditionalContent3);
    }

    @Test
    public void shouldRegenerateWhenTheRequiredProductSubscriptionDisappears() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO reqProd1 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO reqProd2 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO reqProd3 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO bundledProd1 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng()
            .providedProducts(Set.of(reqProd1, reqProd2, reqProd3)));
        PoolDTO bundledPool1 = adminClient.owners().createPool(ownerKey, Pools.random(bundledProd1));
        ProductDTO bundledProd2 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng()
            .providedProducts(Set.of(reqProd1, reqProd2)));
        PoolDTO bundledPool2 = adminClient.owners().createPool(ownerKey, Pools.random(bundledProd2));

        // Create our dependent provided product, which carries content sets -- each of which of which
        // requires one of the provided products above
        ProductDTO dependentProvProd = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ContentDTO conditionalContent1 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .id(randomContentId(5))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd1.getId())));
        ContentDTO conditionalContent2 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .id(randomContentId(5))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd2.getId())));
        ContentDTO conditionalContent3 = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .id(randomContentId(5))
            .type("yum")
            .modifiedProductIds(Set.of(reqProd3.getId())));
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, dependentProvProd.getId(), conditionalContent1.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, dependentProvProd.getId(), conditionalContent2.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, dependentProvProd.getId(), conditionalContent3.getId(), true);

        ProductDTO dependentProd = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng()
            .providedProducts(Set.of(dependentProvProd)));
        adminClient.owners().createPool(ownerKey, Pools.random(dependentProd));

        // Bind to the dependent subscription without being entitled to any of the required products
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0")));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        JsonNode ents = consumerClient.consumers().bindProduct(consumer.getUuid(), dependentProd.getId());
        assertThat(ents).singleElement();
        String dependentProdEntId = ents.get(0).get("id").asText();

        // Verify that we don't have any content repos yet...
        assertThatCert(X509Cert.fromEnt(ents.get(0)))
            .doesNotHaveContentRepoType(conditionalContent1)
            .doesNotHaveContentRepoType(conditionalContent2)
            .doesNotHaveContentRepoType(conditionalContent3);

        // Bind to a normal subscription...
        ents = consumerClient.consumers().bindProduct(consumer.getUuid(), bundledProd2.getId());
        assertThat(ents).isNotNull().singleElement();
        String entId = ents.get(0).get("id").asText();
        List<String> entsToRevoke = new ArrayList<>();
        entsToRevoke.add(entId);

        // Re-fetch the modifier entitlement...
        EntitlementDTO entitlement = consumerClient.entitlements().getEntitlement(dependentProdEntId);
        assertThat(entitlement)
            .isNotNull()
            .extracting(EntitlementDTO::getCertificates, as(collection(CertificateDTO.class)))
            .singleElement();

        // Modifier certificate should now contain some conditional content...
        CertificateDTO entCert = entitlement.getCertificates().iterator().next();
        assertThatCert(X509Cert.from(entCert))
            .hasContentRepoType(conditionalContent1)
            .hasContentRepoType(conditionalContent2)
            .doesNotHaveContentRepoType(conditionalContent3);

        // Bind to another normal subscription...
        ents = consumerClient.consumers().bindProduct(consumer.getUuid(), bundledProd1.getId());
        assertThat(ents).isNotNull().singleElement();
        entId = ents.get(0).get("id").asText();
        entsToRevoke.add(entId);

        // Re-fetch the modifier entitlement...
        entitlement = consumerClient.entitlements().getEntitlement(dependentProdEntId);
        assertThat(entitlement)
            .isNotNull()
            .extracting(EntitlementDTO::getCertificates, as(collection(CertificateDTO.class)))
            .singleElement();

        // Modifier certificate should now contain all conditional content...
        entCert = entitlement.getCertificates().iterator().next();
        assertThatCert(X509Cert.from(entCert))
            .hasContentRepoType(conditionalContent1)
            .hasContentRepoType(conditionalContent2)
            .hasContentRepoType(conditionalContent3);

        // Unbind the pools to revoke our entitlements...
        adminClient.pools().deletePool(bundledPool1.getId());
        adminClient.pools().deletePool(bundledPool2.getId());

        // Re-fetch the modifier entitlement...
        entitlement = consumerClient.entitlements().getEntitlement(dependentProdEntId);
        assertThat(entitlement)
            .isNotNull()
            .extracting(EntitlementDTO::getCertificates, as(collection(CertificateDTO.class)))
            .singleElement();

        // Verify that we don't have any content repos anymore
        entCert = entitlement.getCertificates().iterator().next();
        assertThatCert(X509Cert.from(entCert))
            .doesNotHaveContentRepoType(conditionalContent1)
            .doesNotHaveContentRepoType(conditionalContent2)
            .doesNotHaveContentRepoType(conditionalContent3);
    }

    @Test
    public void shouldIncludeConditionalContentInV3CertAfterAutoAttachThatEntitlesTheRequiredProduct() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();

        ProductDTO engProd1 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        String prod1Id = StringUtil.random("id-");
        ProductDTO prod1 = adminClient.ownerProducts().createProduct(ownerKey, Products.random()
            .id(prod1Id)
            .providedProducts(Set.of(engProd1))
            .multiplier(1L)
            .attributes(List.of(ProductAttributes.StackingId.withValue(prod1Id))));

        ProductDTO engProd2 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        String prod2Id = StringUtil.random("id-");
        ProductDTO prod2 = adminClient.ownerProducts().createProduct(ownerKey, Products.random()
            .id(prod2Id)
            .providedProducts(Set.of(engProd2))
            .multiplier(1L)
            .attributes(List.of(ProductAttributes.StackingId.withValue(prod2Id))));

        ContentDTO engProd2Content = adminClient.ownerContent().createContent(ownerKey, Contents.random());

        // Content that has a required/modified product 'engProd2' (this eng product needs to be entitled
        // to the consumer already, or otherwise this content will get filtered out during entitlement
        // cert generation)
        ContentDTO engProd1Content = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .modifiedProductIds(Set.of(engProd2.getId())));

        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, engProd2.getId(), engProd2Content.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, engProd1.getId(), engProd1Content.getId(), true);

        // Creating primary pool for prod2
        PoolDTO prod2Pool = adminClient.owners().createPool(ownerKey, Pools.random(prod2)
            .providedProducts(Set.of(Products.toProvidedProduct(engProd2))));

        // Create primary pool for prod1
        PoolDTO prod1Pool = adminClient.owners().createPool(ownerKey, Pools.random(prod1)
            .providedProducts(Set.of(Products.toProvidedProduct(engProd1))));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .installedProducts(Set.of(Products.toInstalled(engProd1), Products.toInstalled(engProd2))));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        // Auto-attach the system
        JsonNode ents = consumerClient.consumers().autoBind(consumer.getUuid());
        assertThat(ents).isNotNull().hasSize(2);

        // Verify each entitlement cert contains the appropriate content set
        String firstEntPoolId = ents.get(0).get("pool").get("id").asText();
        String prod1Cert = firstEntPoolId.equals(prod1Pool.getId()) ? getCertFromEnt(ents.get(0)) :
            getCertFromEnt(ents.get(1));
        String prod2Cert = firstEntPoolId.equals(prod2Pool.getId()) ? getCertFromEnt(ents.get(0)) :
            getCertFromEnt(ents.get(1));
        assertThat(prod1Cert).isNotNull();
        assertThat(prod2Cert).isNotNull();

        JsonNode prod1EntCert = CertificateUtil.decodeAndUncompressCertificate(prod1Cert, ApiClient.MAPPER);
        JsonNode prod2EntCert = CertificateUtil.decodeAndUncompressCertificate(prod2Cert, ApiClient.MAPPER);

        JsonNode actualProd2EntCertContent = prod2EntCert.get("products").get(0).get("content");
        assertThat(actualProd2EntCertContent).hasSize(1);
        assertThat(actualProd2EntCertContent.get(0).get("id").asText())
            .isEqualTo(engProd2Content.getId());

        JsonNode actualProd1EntCertContent = prod1EntCert.get("products").get(0).get("content");
        assertThat(actualProd1EntCertContent).hasSize(1);
        assertThat(actualProd1EntCertContent.get(0).get("id").asText())
            .isEqualTo(engProd1Content.getId());
    }

    @Test
    public void shouldIncludeConditionalContentInV1CertAfterAutoAttachThatEntitlesTheRequiredProduct() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();

        ProductDTO engProd1 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        String prod1Id = StringUtil.random("id-");
        ProductDTO prod1 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.random()
            .id(prod1Id)
            .providedProducts(Set.of(engProd1))
            .multiplier(1L)
            .attributes(List.of(ProductAttributes.StackingId.withValue(prod1Id))));

        ProductDTO engProd2 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        String prod2Id = StringUtil.random("id-");
        ProductDTO prod2 = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.random()
            .id(prod2Id)
            .providedProducts(Set.of(engProd2))
            .multiplier(1L)
            .attributes(List.of(ProductAttributes.StackingId.withValue(prod2Id))));

        // Note: for v1 certificates, we only support certain types of content type, like 'yum', so we
        // must set the type to yum here, and also only numeric ids
        ContentDTO engProd2Content = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .id(randomContentId(6))
            .type("yum"));

        // Content that has a required/modified product 'engProd2' (this eng product needs to be entitled
        // to the consumer already, or otherwise this content will get filtered out during entitlement
        // cert generation)
        ContentDTO engProd1Content = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .id(randomContentId(6))
            .type("yum")
            .modifiedProductIds(Set.of(engProd2.getId())));

        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, engProd2.getId(), engProd2Content.getId(), true);
        adminClient.ownerProducts()
            .addContentToProduct(ownerKey, engProd1.getId(), engProd1Content.getId(), true);

        // Creating primary pool for prod2
        PoolDTO prod2Pool = adminClient.owners().createPool(ownerKey, Pools.random(prod2)
            .providedProducts(Set.of(Products.toProvidedProduct(engProd2))));

        // Create primary pool for prod1
        PoolDTO prod1Pool = adminClient.owners().createPool(ownerKey, Pools.random(prod1)
            .providedProducts(Set.of(Products.toProvidedProduct(engProd1))));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0"))
            .installedProducts(Set.of(Products.toInstalled(engProd1), Products.toInstalled(engProd2))));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        // Auto-attach the system
        JsonNode ents = consumerClient.consumers().autoBind(consumer.getUuid());
        assertThat(ents).isNotNull().hasSize(2);

        // Verify each entitlement cert contains the appropriate content set
        String firstPoolId = ents.get(0).get("pool").get("id").asText();
        String prod1Cert = firstPoolId.equals(prod1Pool.getId()) ? getCertFromEnt(ents.get(0)) :
            getCertFromEnt(ents.get(1));
        String prod2Cert = firstPoolId.equals(prod2Pool.getId()) ? getCertFromEnt(ents.get(0)) :
            getCertFromEnt(ents.get(1));

        assertThatCert(X509Cert.from(prod1Cert))
            .hasContentRepoType(engProd1Content)
            .hasContentName(engProd1Content);
        assertThatCert(X509Cert.from(prod2Cert))
            .hasContentRepoType(engProd2Content)
            .hasContentName(engProd2Content);
    }

    private Map<Long, String> getSerialToCert(Collection<CertificateDTO> certs)  {
        Map<Long, String> serialToCert = new HashMap<>();
        certs.forEach(cert -> {
            if (cert.getSerial() != null) {
                serialToCert.put(cert.getSerial().getSerial(), cert.getCert());
            }
        });

        return serialToCert;
    }

    private void revokeEnts(ApiClient client, String consumerId, Collection<String> entIds) {
        for (String entId : entIds) {
            client.consumers().unbindByEntitlementId(consumerId, entId);
        }
    }

    private String getCertFromEnt(JsonNode ent) {
        JsonNode certs = ent.get("certificates");
        assertThat(certs).isNotNull().isNotEmpty();

        return certs.get(0).get("cert").asText();
    }

    private String randomContentId(int length) {
        return StringUtil.random("1", length - 1, StringUtil.CHARSET_NUMERIC);
    }
}
