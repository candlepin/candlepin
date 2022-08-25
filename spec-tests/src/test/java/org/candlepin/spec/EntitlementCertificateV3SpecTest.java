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

package org.candlepin.spec;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.AttributeDTO;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.PoolDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.resource.OwnerApi;
import org.candlepin.resource.OwnerContentApi;
import org.candlepin.resource.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.CertificateUtil;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.X509HuffmanDecodeUtil;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;


@SpecTest
public class EntitlementCertificateV3SpecTest {

    private static ApiClient client;
    private static OwnerDTO owner;
    private OwnerApi ownerApi;
    private OwnerContentApi ownerContentApi;
    private OwnerProductApi ownerProductApi;
    private ConsumerClient consumerApi;

    @BeforeAll
    public void beforeAll() throws ApiException {
        client = ApiClients.admin();
        ownerApi = client.owners();
        owner = ownerApi.createOwner(Owners.random());
        ownerContentApi = client.ownerContent();
        ownerProductApi = client.ownerProducts();
        consumerApi = client.consumers();
    }

    @Test
    @DisplayName("should encode many content urls")
    public void shouldEncodeManyContentUrls() throws Exception {
        ProductDTO product = Products.randomEng();
        product.setName(StringUtil.random("Test Product"));
        product.addAttributesItem(new AttributeDTO().name("version").value("6.4"));
        product.addAttributesItem(new AttributeDTO().name("arch").value("i386, x86_64"));
        product.addAttributesItem(new AttributeDTO().name("sockets").value("4"));
        product.addAttributesItem(new AttributeDTO().name("cores").value("8"));
        product.addAttributesItem(new AttributeDTO().name("ram").value("16"));
        product.addAttributesItem(new AttributeDTO().name("usage").value("Disaster Recovery"));
        product.addAttributesItem(new AttributeDTO().name("roles")
            .value("Red Hat Enterprise Linux Server, Red Hat Enterprise Linux Workstation"));
        product.addAttributesItem(new AttributeDTO().name("addons")
            .value("my_server_addon, my_workstation_addon"));
        product.addAttributesItem(new AttributeDTO().name("warning_period").value("15"));
        product.addAttributesItem(new AttributeDTO().name("management_enabled").value("true"));
        product.addAttributesItem(new AttributeDTO().name("stacking_id").value("8888"));
        product.addAttributesItem(new AttributeDTO().name("virt_only").value("false"));
        product.addAttributesItem(new AttributeDTO().name("support_level").value("standard"));
        product.addAttributesItem(new AttributeDTO().name("support_type").value("excellent"));
        product = ownerProductApi.createProductByOwner(owner.getKey(), product);

        for (int i = 0; i < 100; i++) {
            ContentDTO content = Contents.random();
            content.setContentUrl(
                String.format("/content/dist/rhel/$releasever-%s/$basearch-%s/debug-%s", i, i, i));
            content = ownerContentApi.createContent(owner.getKey(), content);
            product = ownerProductApi.addContent(owner.getKey(), product.getId(), content.getId(), true);
        }

        PoolDTO pool = Pools.random();
        pool.setProductId(product.getId());
        pool.setProductName(product.getName());
        pool = ownerApi.createPool(owner.getKey(), pool);

        ConsumerDTO consumer = client.consumers().register(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.trustedConsumer(consumer.getUuid());
        consumerApi = consumerClient.consumers();
        JsonNode bindResult = consumerApi.bindPool(consumer.getUuid(), pool.getId(), 1);

        // confirm content count to cross-check
        List<JsonNode> jsonNodes = consumerApi.exportCertificates(consumer.getUuid(), null);
        assertEquals(1, jsonNodes.size());
        JsonNode jsonBody = jsonNodes.get(0);
        assertThat(jsonBody.get("products")).hasSize(1);
        assertThat(jsonBody.get("products").get(0).get("content")).hasSize(100);

        // confirm encoded urls
        byte[] value = CertificateUtil.extensionFromCert(bindResult.get(0).get("certificates")
            .get(0).get("cert").toString(), "1.3.6.1.4.1.2312.9.7");
        X509HuffmanDecodeUtil decode = new X509HuffmanDecodeUtil();
        List<String> urls = decode.hydrateContentPackage(value);
        assertThat(urls).hasSize(100);

        // spot check the data
        assertThat(urls).containsAll(List.of("/content/dist/rhel/$releasever-0/$basearch-0/debug-0",
            "/content/dist/rhel/$releasever-29/$basearch-29/debug-29",
            "/content/dist/rhel/$releasever-41/$basearch-41/debug-41",
            "/content/dist/rhel/$releasever-75/$basearch-75/debug-75",
            "/content/dist/rhel/$releasever-99/$basearch-99/debug-99"));
    }

}
