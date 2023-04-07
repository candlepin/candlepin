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
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;

import org.candlepin.dto.api.client.v1.ActivationKeyDTO;
import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerActivationKeyDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.PoolQuantityDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ProvidedProductDTO;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ActivationKeys;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Facts;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SpecTest
public class AutobindDisabledForOwnerSpecTest {

    @Test
    public void shouldFailWhenAutobindIsDisabledOnOwner() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random().autobindDisabled(true));
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));

        assertBadRequest(() -> adminClient.consumers().bindProduct(consumer.getUuid(), "unknown"))
            .hasMessageContaining("Ignoring request to auto-attach. It is disabled for org");
    }

    @Test
    public void shouldNotAttachEntitlementsWhenOwnerIsInSCAMode() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        ProductDTO product = adminClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.random());

        consumerClient.consumers().bindProduct(consumer.getUuid(), product);

        assertThat(consumerClient.consumers().listEntitlements(consumer.getUuid()))
            .isEmpty();
    }

    @Test
    public void shouldFailWhenHypervisorAutobindIsDisabledOnOwner() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random()
            .autobindDisabled(false)
            .autobindHypervisorDisabled(true));
        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner, ConsumerTypes.Hypervisor));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        ProductDTO product = adminClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.random());

        assertBadRequest(() -> consumerClient.consumers().bindProduct(consumer.getUuid(), product))
            .hasMessageContaining("Ignoring request to auto-attach. It is disabled for org")
            .hasMessageContaining("because of the hypervisor autobind setting.");
    }

    @Test
    @OnlyInHosted
    public void shouldAllowDevConsumerToAutobindWhenDisabledOnOwner() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random()
            .autobindDisabled(true));

        ProductDTO activeProd = adminClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.random());
        adminClient.owners().createPool(owner.getKey(), Pools.randomUpstream(activeProd));
        ProductDTO provProd1 = adminClient.hosted().createProduct(Products.random());
        ProductDTO provProd2 = adminClient.hosted().createProduct(Products.random());
        ProductDTO devProd = adminClient.hosted().createProduct(Products.random()
            .attributes(List.of(ProductAttributes.ExpiresAfter.withValue("60")))
            .providedProducts(Set.of(provProd1, provProd2)));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.ofEntries(Facts.DevSku.withValue(devProd.getId())))
            .installedProducts(toInstalled(provProd1, provProd2)));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumerClient.consumers().bindProduct(consumer.getUuid(), devProd);

        List<EntitlementDTO> ents = consumerClient.consumers().listEntitlements(consumer.getUuid());
        assertThat(ents).singleElement();
        assertThat(ents.get(0).getPool())
            .isNotNull()
            .returns("DEVELOPMENT", PoolDTO::getType)
            .returns(devProd.getId(), PoolDTO::getProductId)
            .returns(toProvidedProducts(provProd1, provProd2), PoolDTO::getProvidedProducts);
    }

    @Test
    public void shouldRegisterWhenActivationKeyHasAutobindEnabled() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ActivationKeyDTO activationKey = adminClient.owners()
            .createActivationKey(owner.getKey(), ActivationKeys.random(owner));
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));

        userClient.consumers().createConsumer(Consumers.random(owner)
            .activationKeys(Set.of(toConsumerActivationKeyDTO(activationKey)))
            .facts(Map.ofEntries(Facts.CpuSockets.withValue("8"))));

        // No assertions. Registration should complete with no exceptions.
    }

    @Test
    @OnlyInHosted
    public void shouldRegisterWhenContentAccessSettingAndAutobindAreEnabledOnActivationKey() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        ActivationKeyDTO activationKey = adminClient.owners()
            .createActivationKey(owner.getKey(), ActivationKeys.random(owner));
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));

        userClient.consumers().createConsumer(Consumers.random(owner)
            .activationKeys(Set.of(toConsumerActivationKeyDTO(activationKey))));

        // No assertions. Registration should complete with no exceptions.
    }

    @Test
    @OnlyInHosted
    public void shouldFailToHealEntireOrg() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random()
            .autobindDisabled(true));

        AsyncJobStatusDTO job = adminClient.owners().healEntire(owner.getKey());
        job = adminClient.jobs().waitForJob(job);

        assertThatJob(job).isFailed();
        assertThatJob(job)
            .contains("org.candlepin.async.JobExecutionException: Auto-attach is disabled for owner");
    }

    @Test
    @OnlyInHosted
    public void shouldFailToHealEntireOrgIfSimpleContentAccessIsUsed() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());

        AsyncJobStatusDTO job = adminClient.owners().healEntire(owner.getKey());
        job = adminClient.jobs().waitForJob(job);

        assertThatJob(job).isFailed();
        assertThatJob(job)
            .contains("org.candlepin.async.JobExecutionException: Auto-attach is disabled for owner");
        assertThatJob(job).contains("because of the content access mode setting.");
    }

    @Test
    @OnlyInHosted
    public void shouldReturnSuccessForDryRunAutoAttachOnSCAMode() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca()
            .autobindDisabled(true));
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.ofEntries(Facts.CpuSockets.withValue("8"))));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        List<PoolQuantityDTO> poolQuant = consumerClient.consumers().dryBind(consumer.getUuid(), null);

        assertThat(poolQuant).isEmpty();
    }

    private ConsumerActivationKeyDTO toConsumerActivationKeyDTO(ActivationKeyDTO key) {
        return new ConsumerActivationKeyDTO()
            .activationKeyId(key.getId())
            .activationKeyName(key.getName());
    }

    private Set<ConsumerInstalledProductDTO> toInstalled(ProductDTO... products) {
        return Arrays.stream(products)
            .map(Products::toInstalled)
            .collect(Collectors.toSet());
    }

    private Set<ProvidedProductDTO> toProvidedProducts(ProductDTO... products) {
        return Arrays.stream(products)
            .map(Products::toProvidedProduct)
            .collect(Collectors.toSet());
    }

}
