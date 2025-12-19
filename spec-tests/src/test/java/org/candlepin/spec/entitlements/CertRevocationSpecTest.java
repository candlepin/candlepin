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
package org.candlepin.spec.entitlements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.CdnDTO;
import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.CertificateSerialDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.resource.client.v1.CertificateRevocationListApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@SpecTest
class CertRevocationSpecTest {

    private static ApiClient client;
    private static OwnerProductApi ownerProductApi;
    private static OwnerClient ownerClient;
    private static ConsumerClient consumerClient;
    private static CertificateRevocationListApi certificateRevocationListApi;
    private OwnerDTO owner;
    private ConsumerDTO system;
    private ConsumerClient systemClient;
    private ProductDTO monitoring;
    private PoolDTO monitoringPool;
    private ProductDTO virtual;

    @BeforeAll
    static void beforeAll() {
        client = ApiClients.admin();
        ownerClient = client.owners();
        ownerProductApi = client.ownerProducts();
        consumerClient = client.consumers();
        certificateRevocationListApi = client.crl();
    }

    @BeforeEach
    void setUp() throws ApiException {
        owner = ownerClient.createOwner(Owners.random());

        monitoring = ownerProductApi.createProduct(owner.getKey(), Products.random()
            .name(StringUtil.random("monitoring"))
            .attributes(List.of(new AttributeDTO().name("variant").value("Satellite Starter Pack"))));
        virtual = ownerProductApi.createProduct(owner.getKey(), Products.random()
            .name(StringUtil.random("virtualization")));
        monitoringPool = ownerClient.createPool(owner.getKey(), Pools.random(monitoring));
        ownerClient.createPool(owner.getKey(), Pools.random(virtual));

        system = consumerClient.createConsumer(Consumers.random(owner));
        systemClient = ApiClients.ssl(system).consumers();
    }

    @Test
    public void shouldContainTheSerialOfEntitlmentsRevoked() {
        systemClient.bindProduct(system.getUuid(), monitoring.getId());
        Long serial = filterSerial(monitoring, system);
        systemClient.unbindAll(system.getUuid());
        assertTrue(certificateRevocationListApi.getCurrentCrl().contains(serial));
    }

    @Test
    public void shouldNotContainTheSerialOfValidEntitlment() {
        systemClient.bindProduct(system.getUuid(), monitoring.getId());
        Long serial = filterSerial(monitoring, system);
        assertFalse(certificateRevocationListApi.getCurrentCrl().contains(serial));
    }

    @Test
    public void shouldContainTheSerialOfRevokedEntitlementsAndNotTheUnrevokedEntitlments() {
        JsonNode virtBind = systemClient.bindProduct(system.getUuid(), virtual.getId());
        systemClient.bindProduct(system.getUuid(), monitoring.getId());
        Long serial = filterSerial(virtual, system);
        systemClient.unbindByEntitlementId(system.getUuid(), virtBind.get(0).get("id").asText());
        assertTrue(certificateRevocationListApi.getCurrentCrl().contains(serial));
    }

    @Test
    public void shouldNotContainTheSerialOfUnRevokedEntitlementsWhenEntitlmentsAreRevoked() {
        JsonNode monitoringBind = systemClient.bindProduct(system.getUuid(), monitoring.getId());
        systemClient.bindProduct(system.getUuid(), virtual.getId());
        Long serial = filterSerial(virtual, system);
        systemClient.unbindByEntitlementId(system.getUuid(), monitoringBind.get(0).get("id").asText());
        assertFalse(certificateRevocationListApi.getCurrentCrl().contains(serial));
    }

    @Test
    public void shouldPutRevokedCDNCertOnCrl() {
        String cdnLabel = StringUtil.random("cdn");
        CertificateSerialDTO serial = new CertificateSerialDTO().expiration(
            OffsetDateTime.now().plusYears(1L));
        CertificateDTO cert = new CertificateDTO()
            .key(StringUtil.random("key"))
            .cert(StringUtil.random("cert"))
            .serial(serial);
        CdnDTO cdn = client.cdns().createCdn(
            new CdnDTO()
            .label(cdnLabel)
            .name(StringUtil.random("CDN"))
            .url("https://cdn.test.com")
            .certificate(cert));
        assertNotNull(cdn.getId());
        client.cdns().deleteCdn(cdnLabel);
        assertTrue(certificateRevocationListApi.getCurrentCrl().contains(
            cdn.getCertificate().getSerial().getSerial()));
    }

    @Test
    public void shouldPutRevokedUberCertOnCrl() {
        long certSerial = ownerClient.createUeberCertificate(owner.getKey()).getSerial().getSerial();
        ownerClient.deleteOwner(owner.getKey(), false, false);
        assertTrue(certificateRevocationListApi.getCurrentCrl().contains(certSerial));
    }

    @Test
    public void shouldPutRevokedIdCertOnCrl() {
        CertificateDTO certificateSerial = system.getIdCert();
        systemClient.deleteConsumer(system.getUuid());
        assertTrue(certificateRevocationListApi.getCurrentCrl().contains(
            certificateSerial.getSerial().getSerial()));
    }

    @Test
    @OnlyInHosted
    public void shouldPutRevokedContentAccessCertOnCrl() {
        owner.setContentAccessModeList("org_environment,entitlement");
        owner.setContentAccessMode("org_environment");
        ownerClient.updateOwner(owner.getKey(), owner);

        ConsumerDTO newSystem = consumerClient.createConsumer(Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "3.2")));
        ConsumerClient newSystemClient = ApiClients.ssl(newSystem).consumers();

        List<CertificateSerialDTO> serials = newSystemClient.getEntitlementCertificateSerials(
            newSystem.getUuid());
        assertThat(serials).hasSize(1);
        Long serial = serials.get(0).getSerial();
        assertFalse(certificateRevocationListApi.getCurrentCrl().contains(serial));
        System.out.println("serial: " + serial);

        owner.setContentAccessMode("entitlement");
        ownerClient.updateOwner(owner.getKey(), owner);
        serials = newSystemClient.getEntitlementCertificateSerials(newSystem.getUuid());
        assertThat(serials).hasSize(0);
        List<Long> crl = certificateRevocationListApi.getCurrentCrl();
        System.out.println(crl);
        assertTrue(crl.contains(serial));
    }

    private Long filterSerial(ProductDTO product, ConsumerDTO consumer) {
        List<EntitlementDTO> ents = systemClient.listEntitlements(consumer.getUuid()).stream()
            .filter(x -> product.getId().equals(x.getPool().getProductId()))
            .collect(Collectors.toList());
        if (ents != null && ents.size() > 0) {
            return ents.get(0).getCertificates().iterator().next().getSerial().getSerial();
        }
        return null;
    }
}
