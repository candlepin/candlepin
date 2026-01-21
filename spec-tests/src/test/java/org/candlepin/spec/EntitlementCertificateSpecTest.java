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
package org.candlepin.spec;


import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.resource.HostedTestApi;
import org.candlepin.resource.client.v1.EntitlementsApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.JobsClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.client.cert.X509Cert;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;


@SpecTest
public class EntitlementCertificateSpecTest {

    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static EntitlementsApi entitlementsApi;
    private static OwnerProductApi ownerProductApi;
    private static JobsClient jobApi;
    private OwnerDTO owner;
    private ConsumerClient consumerApi;
    private HostedTestApi hostedTestApi;
    private ProductDTO monitoring;
    private PoolDTO pool;
    private SubscriptionDTO subscription;
    private ConsumerDTO system;

    @BeforeAll
    public static void beforeAll() throws ApiException {
        client = ApiClients.admin();
        ownerApi = client.owners();
        entitlementsApi = client.entitlements();
        ownerProductApi = client.ownerProducts();
        jobApi = client.jobs();
    }

    @BeforeEach
    public void beforeEach() throws ApiException {
        owner = ownerApi.createOwner(Owners.random());
        monitoring = Products.randomEng();
        monitoring = ownerProductApi.createProduct(owner.getKey(), monitoring);
        pool = Pools.random(monitoring);
        pool = ownerApi.createPool(owner.getKey(), pool);

        system = client.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(system);
        consumerApi = consumerClient.consumers();
        consumerApi.bindProduct(system.getUuid(), monitoring.getId());
    }

    @Test
    public void shouldBeAvailableAfterConsumingAnEnt() throws Exception {
        List<CertificateDTO> certs = consumerApi.fetchCertificates(system.getUuid());
        assertThat(certs).hasSize(1);
    }

    @Test
    public void shouldBeManuallyRegeneratedForAConsumer() throws Exception {
        List<CertificateDTO> oldCerts = consumerApi.fetchCertificates(system.getUuid());
        consumerApi.regenerateEntitlementCertificates(system.getUuid(), null, false, false);
        List<CertificateDTO> newCerts = consumerApi.fetchCertificates(system.getUuid());
        assertThat(newCerts).hasSize(1);
        assertNotEquals(oldCerts.get(0).getSerial().getId(), newCerts.get(0).getSerial().getId());
    }

    @Test
    public void shouldRegenerateACertByEntId() throws Exception {
        List<CertificateDTO> oldCerts = consumerApi.fetchCertificates(system.getUuid());
        List<EntitlementDTO> ents = consumerApi.listEntitlements(system.getUuid());
        consumerApi.regenerateEntitlementCertificates(system.getUuid(), ents.get(0).getId(), false, false);
        List<CertificateDTO> newCerts = consumerApi.fetchCertificates(system.getUuid());
        assertThat(newCerts).hasSize(1);
        assertNotEquals(oldCerts.get(0).getSerial().getId(), newCerts.get(0).getSerial().getId());
    }

    @Test
    public void shouldNotAllowConsumerToRegenerateAnothersCertsByEntitlement() throws Exception {
        List<EntitlementDTO> ents = consumerApi.listEntitlements(system.getUuid());
        ConsumerDTO system2 = client.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(system2);
        ConsumerClient consumerApi2 = consumerClient.consumers();
        assertNotFound(() -> consumerApi2.regenerateEntitlementCertificates(
            system.getUuid(), ents.get(0).getId(), false, false));
    }

    @Test
    public void shouldNotAllowConsumerToRegenerateAnothersCerts() throws Exception {
        ConsumerDTO system2 = client.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(system2);
        ConsumerClient consumerApi2 = consumerClient.consumers();
        assertNotFound(() -> consumerApi2.regenerateEntitlementCertificates(
            system.getUuid(), null, false, false));
    }

    @Test
    public void shouldBeManuallyRegeneratedForAProduct() throws Exception {
        ProductDTO coolApp = Products.randomSKU();
        coolApp = ownerProductApi.createProduct(owner.getKey(), coolApp);
        pool = Pools.random(coolApp);
        pool = ownerApi.createPool(owner.getKey(), pool);
        consumerApi.bindPool(system.getUuid(), pool.getId(), 1);
        List<CertificateDTO> oldCerts = consumerApi.fetchCertificates(system.getUuid());
        assertThat(oldCerts).hasSize(2);
        AsyncJobStatusDTO result = entitlementsApi.regenerateEntitlementCertificatesForProduct(
            coolApp.getId(), false);
        if (result != null) {
            AsyncJobStatusDTO status = jobApi.waitForJob(result.getId());
            assertEquals("FINISHED", status.getState());
        }

        List<CertificateDTO> newCerts = consumerApi.fetchCertificates(system.getUuid());
        assertEquals(oldCerts.size(), newCerts.size());
        // System has two certs, but we only regenerated for one product, so the
        // other serial should have remained the same:
        assertThat(newCerts)
            .filteredOn(oldCerts::contains)
            .hasSize(1);
    }

    @Test
    public void shouldRegenerateEntCertsByProductForAllOwners() throws Exception {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner2 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner3 = ownerApi.createOwner(Owners.random());

        String prodId = StringUtil.random("test_prod");
        String safeProdId = StringUtil.random("safe_prod");

        ProductDTO prod = Products.randomEng().id(prodId);
        ProductDTO safeProd = Products.randomEng().id(safeProdId);

        ownerApi.createPool(owner1.getKey(), Pools.random(
            ownerProductApi.createProduct(owner1.getKey(), prod)));
        ownerApi.createPool(owner2.getKey(), Pools.random(
            ownerProductApi.createProduct(owner2.getKey(), prod)));
        ownerApi.createPool(owner3.getKey(), Pools.random(
            ownerProductApi.createProduct(owner3.getKey(), prod)));
        ownerApi.createPool(owner1.getKey(), Pools.random(
            ownerProductApi.createProduct(owner1.getKey(), safeProd)));
        ownerApi.createPool(owner2.getKey(), Pools.random(
            ownerProductApi.createProduct(owner2.getKey(), safeProd)));
        ownerApi.createPool(owner3.getKey(), Pools.random(
            ownerProductApi.createProduct(owner3.getKey(), safeProd)));

        ConsumerDTO system1 = client.consumers().createConsumer(Consumers.random(owner1));
        ApiClient consumerClient1 = ApiClients.ssl(system1);
        ConsumerClient consumerApi1 = consumerClient1.consumers();
        ConsumerDTO system2 = client.consumers().createConsumer(Consumers.random(owner2));
        ApiClient consumerClient2 = ApiClients.ssl(system2);
        ConsumerClient consumerApi2 = consumerClient2.consumers();
        ConsumerDTO system3 = client.consumers().createConsumer(Consumers.random(owner3));
        ApiClient consumerClient3 = ApiClients.ssl(system3);
        ConsumerClient consumerApi3 = consumerClient3.consumers();

        consumerApi1.bindProduct(system1.getUuid(), prodId);
        consumerApi2.bindProduct(system2.getUuid(), prodId);
        consumerApi3.bindProduct(system3.getUuid(), prodId);
        consumerApi1.bindProduct(system1.getUuid(), safeProdId);
        consumerApi2.bindProduct(system2.getUuid(), safeProdId);
        consumerApi3.bindProduct(system3.getUuid(), safeProdId);

        List<CertificateDTO> oldCerts1 = consumerApi1.fetchCertificates(system1.getUuid());
        assertThat(oldCerts1).hasSize(2);
        List<CertificateDTO> oldCerts2 = consumerApi2.fetchCertificates(system2.getUuid());
        assertThat(oldCerts2).hasSize(2);
        List<CertificateDTO> oldCerts3 = consumerApi3.fetchCertificates(system3.getUuid());
        assertThat(oldCerts3).hasSize(2);

        AsyncJobStatusDTO result = entitlementsApi.regenerateEntitlementCertificatesForProduct(prodId, false);
        if (result != null) {
            AsyncJobStatusDTO status = jobApi.waitForJob(result.getId());
            assertEquals("FINISHED", status.getState());
        }

        List<CertificateDTO> newCerts1 = consumerApi1.fetchCertificates(system1.getUuid());
        assertThat(oldCerts1).hasSize(2);
        List<CertificateDTO> newCerts2 = consumerApi2.fetchCertificates(system2.getUuid());
        assertThat(oldCerts2).hasSize(2);
        List<CertificateDTO> newCerts3 = consumerApi3.fetchCertificates(system3.getUuid());
        assertThat(oldCerts3).hasSize(2);

        // Cert IDs should have changed across the board for prod, but safe_prod should remain untouched.
        assertThat(newCerts1)
            .filteredOn(oldCerts1::contains)
            .hasSize(1);
        assertThat(newCerts2)
            .filteredOn(oldCerts2::contains)
            .hasSize(1);
        assertThat(newCerts3)
            .filteredOn(oldCerts3::contains)
            .hasSize(1);
    }

    /**
     * Needs a consumer that has not bound to the normal pool
      */
    @Nested
    public class EntitlementCertificateUnboundConsumerSpecTest {
        @BeforeEach
        public void beforeEach() throws ApiException {
            owner = ownerApi.createOwner(Owners.random());
            system = client.consumers().createConsumer(Consumers.random(owner));
            ApiClient consumerClient = ApiClients.ssl(system);
            consumerApi = consumerClient.consumers();

            ProductDTO prod = Products.randomEng().addAttributesItem(
                new AttributeDTO().name("multi-entitlement").value("yes"));
            prod = ownerProductApi.createProduct(owner.getKey(), prod);
            pool = Pools.random(prod);
            pool = ownerApi.createPool(owner.getKey(), pool);
        }

        @Test
        public void shouldDeleteSingleEntInExcessWhenSubQuantityIsDecreased() throws Exception {
            consumerApi.bindPool(system.getUuid(), pool.getId(), 6);
            assertThat(consumerApi.fetchCertificates(system.getUuid())).hasSize(1);
            pool.setQuantity(pool.getQuantity() - 5);
            ownerApi.updatePool(owner.getKey(), pool);
            assertThat(consumerApi.fetchCertificates(system.getUuid())).hasSize(0);
        }

        @Test
        public void shouldDeleteMultipleEntInExcessWhenSubQuantityIsDecreased() throws Exception {
            for (int i = 0; i < 5; i++) {
                consumerApi.bindPool(system.getUuid(), pool.getId(), 2);
            }
            assertThat(consumerApi.fetchCertificates(system.getUuid())).hasSize(5);
            pool.setQuantity(pool.getQuantity() - 5);
            ownerApi.updatePool(owner.getKey(), pool);
            assertThat(consumerApi.fetchCertificates(system.getUuid())).hasSize(2);
        }
    }

    @Test
    @OnlyInStandalone
    public void shouldBeRegeneratedWhenChangingExistingSubsEndDateStandalone() throws Exception {
        CertificateDTO oldCert = consumerApi.fetchCertificates(system.getUuid()).get(0);
        pool.setEndDate(pool.getEndDate().plusDays(2L));
        ownerApi.updatePool(owner.getKey(), pool);
        CertificateDTO newCert = consumerApi.fetchCertificates(system.getUuid()).get(0);
        assertNotEquals(oldCert.getSerial().getId(), newCert.getSerial().getId());
        EntitlementDTO ent = consumerApi.listEntitlements(system.getUuid()).get(0);
        assertEquals(ent.getEndDate(), pool.getEndDate());
    }

    @Test
    @OnlyInStandalone
    public void shouldRegenerateWhenSubsQuantityAndDatesAreChangedStandalone() throws Exception {
        CertificateDTO oldCert = consumerApi.fetchCertificates(system.getUuid()).get(0);
        changeDateAndQuantity(pool);
        CertificateDTO newCert = consumerApi.fetchCertificates(system.getUuid()).get(0);
        assertNotEquals(oldCert.getSerial().getId(), newCert.getSerial().getId());
    }

    @Test
    @OnlyInStandalone
    public void shouldRegenerateWithDateMatchingChangedSubStandalone() throws Exception {
        pool = changeDateAndQuantity(pool);
        CertificateDTO certificate = consumerApi.fetchCertificates(system.getUuid()).get(0);
        X509Cert cert = X509Cert.from(certificate);
        assertEquals(
            pool.getStartDate().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(),
            cert.notBefore());
        assertEquals(
            pool.getEndDate().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(),
            cert.notAfter());
    }

    @Nested
    @OnlyInHosted
    public class EntitlementCertificateHostedSpecTest {
        @BeforeEach
        public void beforeEach() throws ApiException {
            owner = ownerApi.createOwner(Owners.random());
            monitoring = Products.randomEng();
            hostedTestApi = client.hosted();
            hostedTestApi.createProduct(monitoring);
            subscription = Subscriptions.random(owner, monitoring);
            hostedTestApi.createSubscription(subscription);
            AsyncJobStatusDTO refresh = ownerApi.refreshPools(owner.getKey(), false);
            if (refresh != null) {
                jobApi.waitForJob(refresh.getId());
            }
            system = client.consumers().createConsumer(Consumers.random(owner));
            ApiClient consumerClient = ApiClients.ssl(system);
            consumerApi = consumerClient.consumers();
            consumerApi.bindProduct(system.getUuid(), monitoring.getId());
        }

        @Test
        public void shouldBeRegeneratedWhenChangingExistingSubsEndDateHosted() throws Exception {
            CertificateDTO oldCert = consumerApi.fetchCertificates(system.getUuid()).get(0);
            subscription.setEndDate(subscription.getEndDate().plusDays(2L));
            subscription = hostedTestApi.updateSubscription(subscription.getId(), subscription);
            AsyncJobStatusDTO refresh = ownerApi.refreshPools(owner.getKey(), false);
            if (refresh != null) {
                AsyncJobStatusDTO status = jobApi.waitForJob(refresh.getId());
                assertEquals("FINISHED", status.getState());
            }
            CertificateDTO newCert = consumerApi.fetchCertificates(system.getUuid()).get(0);
            assertNotEquals(oldCert.getSerial().getId(), newCert.getSerial().getId());
            EntitlementDTO ent = consumerApi.listEntitlements(system.getUuid()).get(0);
            assertEquals(ent.getEndDate(), subscription.getEndDate());
        }

        @Test
        public void shouldRegenerateWhenSubsQuantityAndDatesAreChangedHosted() throws Exception {
            CertificateDTO oldCert = consumerApi.fetchCertificates(system.getUuid()).get(0);
            changeDateAndQuantity(subscription);
            CertificateDTO newCert = consumerApi.fetchCertificates(system.getUuid()).get(0);
            assertNotEquals(oldCert.getSerial().getId(), newCert.getSerial().getId());
        }

        @Test
        public void shouldRegenerateWithDateMatchingChangedSubHosted() throws Exception {
            subscription = changeDateAndQuantity(subscription);
            CertificateDTO certificate = consumerApi.fetchCertificates(system.getUuid()).get(0);
            X509Cert cert = X509Cert.from(certificate);
            assertEquals(
                subscription.getStartDate().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(),
                cert.notBefore());
            assertEquals(
                subscription.getEndDate().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(),
                cert.notAfter());
        }
    }

    private SubscriptionDTO changeDateAndQuantity(SubscriptionDTO sub) throws ApiException {
        sub.setStartDate(sub.getStartDate().minusDays(10L));
        sub.setEndDate(sub.getEndDate().plusDays(10L));
        sub.setQuantity(sub.getQuantity() + 10);
        sub = hostedTestApi.updateSubscription(sub.getId(), sub);
        AsyncJobStatusDTO refresh = ownerApi.refreshPools(owner.getKey(), false);
        if (refresh != null) {
            AsyncJobStatusDTO status = jobApi.waitForJob(refresh.getId());
            assertEquals("FINISHED", status.getState());
        }
        return sub;
    }

    private PoolDTO changeDateAndQuantity(PoolDTO pool) throws ApiException {
        pool.setStartDate(pool.getStartDate().minusDays(10L));
        pool.setEndDate(pool.getEndDate().plusDays(10L));
        pool.setQuantity(pool.getQuantity() + 10);
        ownerApi.updatePool(owner.getKey(), pool);
        return pool;
    }
}
