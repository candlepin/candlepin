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
package org.candlepin.spec.pools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@SpecTest
class PostBindBonusPoolSpecTest {
    private ApiClient adminClient;
    private OwnerDTO owner;
    private String ownerKey;

    @BeforeEach
    void setup() {
        adminClient = ApiClients.admin();
        owner = adminClient.owners().createOwner(Owners.random());
        ownerKey = owner.getKey();
    }

    @Test
    @OnlyInHosted
    void shouldNotCreateEntitlementDerivedPoolWithoutHostLimitedAttribute() throws Exception {
        ConsumerDTO systemUser = Consumers.random(owner, ConsumerTypes.System);
        systemUser.setUsername("admin");
        systemUser = adminClient.consumers().createConsumer(systemUser, "admin",
        owner.getKey(), null, true);
        ApiClient systemClient = ApiClients.ssl(systemUser);

        ProductDTO limitedVirtProd = createLimitedVirtProduct(adminClient, ownerKey, 4);
        PoolDTO limitedPrimaryPool = Pools.randomUpstream(limitedVirtProd);
        limitedPrimaryPool = adminClient.owners().createPool(ownerKey, limitedPrimaryPool);

        // does not create ent derived if host_limited != true
        verifyBindTimePoolCreation(systemClient, owner, limitedPrimaryPool, systemUser, false);
    }

    @Test
    @OnlyInStandalone
    void shouldCreateEntitlementDerivedPoolWithoutHostLimitedAttribute() throws Exception {
        ConsumerDTO systemUser = Consumers.random(owner, ConsumerTypes.System);
        systemUser.setUsername("admin");
        systemUser = adminClient.consumers().createConsumer(systemUser, "admin",
        owner.getKey(), null, true);
        ApiClient systemClient = ApiClients.ssl(systemUser);

        ProductDTO limitVirtProd = createLimitedVirtProduct(adminClient, ownerKey, 4);
        PoolDTO limitedPrimaryPool = Pools.randomUpstream(limitVirtProd);
        limitedPrimaryPool = adminClient.owners().createPool(ownerKey, limitedPrimaryPool);

        verifyBindTimePoolCreation(systemClient, owner, limitedPrimaryPool, systemUser, true);
    }

    @Test
    void shouldCreateEntitlementDerivedPoolWithHostLimitedAttribute() throws Exception {
        ConsumerDTO systemUser = Consumers.random(owner, ConsumerTypes.System);
        systemUser.setUsername("admin");
        systemUser = adminClient.consumers().createConsumer(systemUser, "admin",
        owner.getKey(), null, true);
        ApiClient systemClient = ApiClients.ssl(systemUser);

        ProductDTO hostLimitedProd = createHostLimitedProduct(adminClient, ownerKey);
        PoolDTO hostLimitedPrimaryPool = Pools.randomUpstream(hostLimitedProd);
        hostLimitedPrimaryPool = adminClient.owners().createPool(ownerKey, hostLimitedPrimaryPool);

        verifyBindTimePoolCreation(systemClient, owner, hostLimitedPrimaryPool, systemUser, true);
    }

    @Test
    @OnlyInStandalone
    void shouldCreateStackDerivedPoolWithoutHostLimitedAttribute() throws Exception {
        ConsumerDTO systemUser = Consumers.random(owner, ConsumerTypes.System);
        systemUser.setUsername("admin");
        systemUser = adminClient.consumers().createConsumer(systemUser, "admin",
        owner.getKey(), null, true);
        ApiClient systemClient = ApiClients.ssl(systemUser);

        ProductDTO virtLimitStackedProd = createVirtLimitStackedProduct(adminClient, ownerKey, 9);
        PoolDTO limitedPrimaryStackedPool = Pools.randomUpstream(virtLimitStackedProd);
        limitedPrimaryStackedPool = adminClient.owners().createPool(ownerKey, limitedPrimaryStackedPool);

        verifyBindTimePoolCreation(systemClient, owner, limitedPrimaryStackedPool, systemUser, true);
    }

    @Test
    @OnlyInStandalone
    void shouldCreateStackDerivedPoolWithHostLimitedAttribute() throws Exception {
        ConsumerDTO systemUser = Consumers.random(owner, ConsumerTypes.System);
        systemUser.setUsername("admin");
        systemUser = adminClient.consumers().createConsumer(systemUser, "admin",
        owner.getKey(), null, true);
        ApiClient systemClient = ApiClients.ssl(systemUser);

        ProductDTO hostLimitedStackedProd = createHostLimitedStackedProduct(adminClient, ownerKey, 9);
        PoolDTO hostLimitedPrimaryStackedPool = Pools.randomUpstream(hostLimitedStackedProd);
        hostLimitedPrimaryStackedPool = adminClient.owners()
            .createPool(ownerKey, hostLimitedPrimaryStackedPool);

        verifyBindTimePoolCreation(systemClient, owner, hostLimitedPrimaryStackedPool, systemUser, true);
    }

    @Test
    void shouldCreateEntitlementDerivedPoolForEveryBind() throws Exception {
        ConsumerDTO systemUser = Consumers.random(owner, ConsumerTypes.System);
        systemUser.setUsername("admin");
        systemUser = adminClient.consumers().createConsumer(systemUser, "admin",
        owner.getKey(), null, true);
        ApiClient systemClient = ApiClients.ssl(systemUser);

        ProductDTO hostLimitedProd = createHostLimitedProduct(adminClient, ownerKey);
        PoolDTO hostLimitedPrimaryPool = Pools.randomUpstream(hostLimitedProd);
        hostLimitedPrimaryPool = adminClient.owners().createPool(ownerKey, hostLimitedPrimaryPool);

        int beforeSize = adminClient.owners().listOwnerPools(ownerKey).size();
        systemClient.consumers().bindPool(systemUser.getUuid(), hostLimitedPrimaryPool.getId(), 1);
        systemClient.consumers().bindPool(systemUser.getUuid(), hostLimitedPrimaryPool.getId(), 1);
        systemClient.consumers().bindPool(systemUser.getUuid(), hostLimitedPrimaryPool.getId(), 1);

        assertEquals(beforeSize + 3, adminClient.owners().listOwnerPools(ownerKey).size());
    }

    @Test
    @OnlyInHosted
    void shouldDecrementBonusPoolQuantityWhenFiniteVirtLimitedPrimaryPoolIsPartiallyExported()
        throws Exception {
        ConsumerDTO cpUser = Consumers.random(owner, ConsumerTypes.Candlepin);
        cpUser = adminClient.consumers().createConsumer(cpUser);
        ApiClient candlepinClient = ApiClients.ssl(cpUser);

        int virtLimit = 4;
        ProductDTO limitVirtProd = createLimitedVirtProduct(adminClient, ownerKey, virtLimit);
        PoolDTO limitedPrimaryPool = Pools.randomUpstream(limitVirtProd);
        limitedPrimaryPool = adminClient.owners().createPool(ownerKey, limitedPrimaryPool);
        PoolDTO limitedBonusPool =
            getBonusPool(adminClient, ownerKey, limitedPrimaryPool.getSubscriptionId());
        int initialBonusPoolQuantity = limitedBonusPool.getQuantity().intValue();

        // reduce by quantity * virt_limit
        int consumptionQuantity = 2;
        candlepinClient.consumers()
            .bindPool(cpUser.getUuid(), limitedPrimaryPool.getId(), consumptionQuantity);
        limitedBonusPool = adminClient.pools().getPool(limitedBonusPool.getId(), null, null);
        int expectedQuantity = initialBonusPoolQuantity - (virtLimit * consumptionQuantity);
        assertEquals(expectedQuantity, limitedBonusPool.getQuantity());

        // now set to 0 when fully exported
        candlepinClient.consumers().bindPool(cpUser.getUuid(), limitedPrimaryPool.getId(), 8);
        limitedBonusPool = adminClient.pools().getPool(limitedBonusPool.getId(), null, null);
        assertEquals(0, limitedBonusPool.getQuantity());
    }

    @Test
    @OnlyInHosted
    void shouldUpdateBonusPoolQuantityWhenExportEntitlementQuantityIsUpdated() throws Exception {
        ConsumerDTO cpUser = Consumers.random(owner, ConsumerTypes.Candlepin);
        cpUser = adminClient.consumers().createConsumer(cpUser);
        ApiClient candlepinClient = ApiClients.ssl(cpUser);

        int virtLimit = 4;
        ProductDTO limitVirtProd = createLimitedVirtProduct(adminClient, ownerKey, virtLimit);
        PoolDTO limitedPrimaryPool = Pools.randomUpstream(limitVirtProd);
        limitedPrimaryPool = adminClient.owners().createPool(ownerKey, limitedPrimaryPool);
        PoolDTO limitedBonusPool =
            getBonusPool(adminClient, ownerKey, limitedPrimaryPool.getSubscriptionId());
        int initialBonusPoolQuantity = limitedBonusPool.getQuantity().intValue();

        // bonus pool quantity adjusted by quantity * virt_limit
        int consumptionQuantity = 2;
        JsonNode ent = candlepinClient.consumers()
            .bindPool(cpUser.getUuid(), limitedPrimaryPool.getId(), consumptionQuantity).get(0);
        limitedBonusPool = adminClient.pools().getPool(limitedBonusPool.getId(), null, null);
        int expectedQuantity = initialBonusPoolQuantity - (virtLimit * consumptionQuantity);
        assertEquals(expectedQuantity, limitedBonusPool.getQuantity());

        // bonus pool quantity updated after updating entitlement quantity
        EntitlementDTO entitlement  = ApiClient.MAPPER.convertValue(ent, EntitlementDTO.class);
        int newEntQuant = 1;
        entitlement.setQuantity(newEntQuant);
        adminClient.entitlements().updateEntitlement(entitlement.getId(), entitlement);
        limitedBonusPool = adminClient.pools().getPool(limitedBonusPool.getId(), null, null);
        assertEquals(initialBonusPoolQuantity - (virtLimit * newEntQuant), limitedBonusPool.getQuantity());

        newEntQuant = 3;
        entitlement.setQuantity(newEntQuant);
        adminClient.entitlements().updateEntitlement(entitlement.getId(), entitlement);
        limitedBonusPool = adminClient.pools().getPool(limitedBonusPool.getId(), null, null);
        assertEquals(initialBonusPoolQuantity - (virtLimit * newEntQuant), limitedBonusPool.getQuantity());
    }

    @Test
    @OnlyInHosted
    void shouldNotChangeBonusPoolQuantityWhenUnlimitedVirtLimitedPrimaryPoolIsPartiallyExported()
        throws Exception {
        ConsumerDTO cpUser = Consumers.random(owner, ConsumerTypes.Candlepin);
        cpUser = adminClient.consumers().createConsumer(cpUser);
        ApiClient candlepinClient = ApiClients.ssl(cpUser);

        ProductDTO unlimitedVirtProd = createUnlimitedVirtProduct(adminClient, ownerKey);
        PoolDTO unlimitedPrimaryPool = Pools.randomUpstream(unlimitedVirtProd);
        unlimitedPrimaryPool = adminClient.owners().createPool(ownerKey, unlimitedPrimaryPool);
        PoolDTO unlimitedBonusPool =
            getBonusPool(adminClient, ownerKey, unlimitedPrimaryPool.getSubscriptionId());

        candlepinClient.consumers().bindPool(cpUser.getUuid(), unlimitedPrimaryPool.getId(), 9);
        unlimitedBonusPool = adminClient.pools().getPool(unlimitedBonusPool.getId(), null, null);
        assertEquals(-1, unlimitedBonusPool.getQuantity());

        // once it is fully consumed, set to 0
        candlepinClient.consumers().bindPool(cpUser.getUuid(), unlimitedPrimaryPool.getId(), 1);
        unlimitedBonusPool = adminClient.pools().getPool(unlimitedBonusPool.getId(), null, null);
        assertEquals(0, unlimitedBonusPool.getQuantity());
    }

    @Test
    @OnlyInHosted
    void shouldChangeBonusPoolQuantityWhenUnlimitedVirtLimitedPrimaryPoolIsFullyExportedAndThenReduced()
        throws Exception {
        ConsumerDTO cpUser = Consumers.random(owner, ConsumerTypes.Candlepin);
        cpUser = adminClient.consumers().createConsumer(cpUser);
        ApiClient candlepinClient = ApiClients.ssl(cpUser);

        ProductDTO unlimitedVirtProd = createUnlimitedVirtProduct(adminClient, ownerKey);
        int primaryPoolQuantity = 10;
        PoolDTO unlimitedPrimaryPool = Pools.randomUpstream(unlimitedVirtProd)
            .quantity(Long.valueOf(primaryPoolQuantity));
        unlimitedPrimaryPool = adminClient.owners().createPool(ownerKey, unlimitedPrimaryPool);
        PoolDTO unlimitedBonusPool =
            getBonusPool(adminClient, ownerKey, unlimitedPrimaryPool.getSubscriptionId());

        JsonNode ent = candlepinClient.consumers()
            .bindPool(cpUser.getUuid(), unlimitedPrimaryPool.getId(), primaryPoolQuantity).get(0);
        unlimitedBonusPool = adminClient.pools().getPool(unlimitedBonusPool.getId(), null, null);
        assertEquals(0, unlimitedBonusPool.getQuantity());

        // reduce the entitlement quantity and the bonus pool should update to unlimited quantity. BZ 2078029
        EntitlementDTO entitlement  = ApiClient.MAPPER.convertValue(ent, EntitlementDTO.class);
        entitlement.setQuantity(primaryPoolQuantity - 1);
        adminClient.entitlements().updateEntitlement(entitlement.getId(), entitlement);
        unlimitedBonusPool = adminClient.pools().getPool(unlimitedBonusPool.getId(), null, null);
        assertEquals(-1, unlimitedBonusPool.getQuantity());
    }

    @Test
    void shouldNotChangeBonusPoolQuantityWhenUnlimitedVirtLimitedPrimaryPoolIsConsumedByNonManifestConsumer()
        throws Exception {
        ConsumerDTO guest = Consumers.random(owner, ConsumerTypes.System);
        guest.setUuid(StringUtil.random("guest"));
        guest.setFacts(Map.of("virt.is_guest", "true", "virt.uuid", guest.getUuid()));
        guest = adminClient.consumers().createConsumer(guest);
        ApiClient guestClient = ApiClients.ssl(guest);

        ConsumerDTO systemUser = Consumers.random(owner, ConsumerTypes.System);
        systemUser.setUsername("admin");
        systemUser = adminClient.consumers().createConsumer(systemUser, "admin",
        owner.getKey(), null, true);
        ApiClient systemClient = ApiClients.ssl(systemUser);

        ProductDTO unlimitedVirtProd = createUnlimitedVirtProduct(adminClient, ownerKey);
        PoolDTO unlimitedPrimaryPool = Pools.randomUpstream(unlimitedVirtProd);
        unlimitedPrimaryPool = adminClient.owners().createPool(ownerKey, unlimitedPrimaryPool);
        PoolDTO unlimitedBonusPool =
            getBonusPool(adminClient, ownerKey, unlimitedPrimaryPool.getSubscriptionId());

        guestClient.consumers().bindPool(guest.getUuid(), unlimitedPrimaryPool.getId(), 1);
        unlimitedBonusPool = adminClient.pools().getPool(unlimitedBonusPool.getId(), null, null);
        assertEquals(-1, unlimitedBonusPool.getQuantity());
        unlimitedPrimaryPool = adminClient.pools().getPool(unlimitedPrimaryPool.getId(), null, null);
        assertEquals(1, unlimitedPrimaryPool.getConsumed());
        assertEquals(0, unlimitedPrimaryPool.getExported());

        // even if one quantity was consumed but not exported, do not update quantity of the bonus pool
        systemClient.consumers().bindPool(systemUser.getUuid(), unlimitedPrimaryPool.getId(), 9);
        unlimitedPrimaryPool = adminClient.pools().getPool(unlimitedPrimaryPool.getId(), null, null);
        assertEquals(unlimitedPrimaryPool.getConsumed(), unlimitedPrimaryPool.getQuantity());
        assertNotEquals(unlimitedPrimaryPool.getExported(), unlimitedPrimaryPool.getConsumed());
        unlimitedBonusPool = adminClient.pools().getPool(unlimitedBonusPool.getId(), null, null);
        assertEquals(-1, unlimitedBonusPool.getQuantity());
    }

    @Test
    void shouldNotChangeHostlimitedEntitlementDerivedPoolQuantityWhenSourceEntitlementsQuantityIsReduced()
        throws Exception {
        ConsumerDTO systemUser = Consumers.random(owner, ConsumerTypes.System);
        systemUser.setUsername("admin");
        systemUser = adminClient.consumers().createConsumer(systemUser, "admin",
        owner.getKey(), null, true);
        ApiClient systemClient = ApiClients.ssl(systemUser);

        ProductDTO hostLimitedProd = createHostLimitedProduct(adminClient, ownerKey);
        PoolDTO hostLimitedPrimaryPool = Pools.randomUpstream(hostLimitedProd);
        hostLimitedPrimaryPool = adminClient.owners().createPool(ownerKey, hostLimitedPrimaryPool);

        int initialPoolSize = adminClient.owners().listOwnerPools(ownerKey).size();
        JsonNode ent = systemClient.consumers()
            .bindPool(systemUser.getUuid(), hostLimitedPrimaryPool.getId(), 4).get(0);
        List<PoolDTO> pools = adminClient.owners().listOwnerPools(ownerKey);
        assertEquals(initialPoolSize + 1, pools.size());

        List<PoolDTO> entDerivedPools = pools.stream()
            .filter(pool -> "ENTITLEMENT_DERIVED".equals(pool.getType()))
            .collect(Collectors.toList());
        assertEquals(1, entDerivedPools.size());
        PoolDTO entDerivedPool = entDerivedPools.get(0);
        assertEquals(-1, entDerivedPool.getQuantity());
        assertEquals(entDerivedPool.getSourceEntitlement().getId(), ent.get("id").asText());

        List<AttributeDTO> atts = entDerivedPool.getAttributes().stream()
            .filter(att -> att.getName().equals("requires_host"))
            .collect(Collectors.toList());
        assertEquals(1, atts.size());
        assertEquals(systemUser.getUuid(), atts.get(0).getValue());

        EntitlementDTO entitlement  = ApiClient.MAPPER.convertValue(ent, EntitlementDTO.class);
        entitlement.setQuantity(1);
        adminClient.entitlements().updateEntitlement(entitlement.getId(), entitlement);
        entDerivedPool = adminClient.pools().getPool(entDerivedPool.getId(), null, null);
        assertEquals(-1, entDerivedPool.getQuantity());
    }

    @Test
    @OnlyInStandalone
    void shouldChangeUnlimitedVirtlimitedEntitlementDerivedPoolQuantityWhenSourceEntQuantityIsReduced()
        throws Exception {
        ConsumerDTO systemUser = Consumers.random(owner, ConsumerTypes.System);
        systemUser.setUsername("admin");
        systemUser = adminClient.consumers().createConsumer(systemUser, "admin",
        owner.getKey(), null, true);
        ApiClient systemClient = ApiClients.ssl(systemUser);

        ProductDTO unlimitedVirtProd = createUnlimitedVirtProduct(adminClient, ownerKey);
        PoolDTO unlimitedPrimaryPool = Pools.randomUpstream(unlimitedVirtProd);
        unlimitedPrimaryPool = adminClient.owners().createPool(ownerKey, unlimitedPrimaryPool);

        int initialPoolSize = adminClient.owners().listOwnerPools(ownerKey).size();
        JsonNode ent = systemClient.consumers()
            .bindPool(systemUser.getUuid(), unlimitedPrimaryPool.getId(), 4).get(0);
        List<PoolDTO> pools = adminClient.owners().listOwnerPools(ownerKey);
        assertEquals(initialPoolSize + 1, pools.size());

        List<PoolDTO> entDerivedPools = pools.stream()
            .filter(pool -> "ENTITLEMENT_DERIVED".equals(pool.getType()))
            .collect(Collectors.toList());
        assertEquals(1, entDerivedPools.size());
        PoolDTO entDerivedPool = entDerivedPools.get(0);
        assertEquals(-1, entDerivedPool.getQuantity());
        assertEquals(entDerivedPool.getSourceEntitlement().getId(), ent.get("id").asText());

        List<AttributeDTO> atts = entDerivedPool.getAttributes().stream()
            .filter(att -> att.getName().equals("requires_host"))
            .collect(Collectors.toList());
        assertEquals(1, atts.size());
        assertEquals(systemUser.getUuid(), atts.get(0).getValue());

        EntitlementDTO entitlement  = ApiClient.MAPPER.convertValue(ent, EntitlementDTO.class);
        entitlement.setQuantity(1);
        adminClient.entitlements().updateEntitlement(entitlement.getId(), entitlement);
        entDerivedPool = adminClient.pools().getPool(entDerivedPool.getId(), null, null);
        assertEquals(-1, entDerivedPool.getQuantity());
    }

    @Test
    @OnlyInStandalone
    void shouldChangeLimitedVirtLimitedEntitlementDerivedPoolQuantityWhenSourceEntitlementsQuantityIsReduced()
        throws Exception {
        ConsumerDTO systemUser = Consumers.random(owner, ConsumerTypes.System);
        systemUser.setUsername("admin");
        systemUser = adminClient.consumers().createConsumer(systemUser, "admin",
        owner.getKey(), null, true);
        ApiClient systemClient = ApiClients.ssl(systemUser);

        int virtLimit = 4;
        ProductDTO limitedVirtProd = createLimitedVirtProduct(adminClient, ownerKey, virtLimit);
        PoolDTO limitedPrimaryPool = Pools.randomUpstream(limitedVirtProd);
        limitedPrimaryPool = adminClient.owners().createPool(ownerKey, limitedPrimaryPool);

        int initialPoolSize = adminClient.owners().listOwnerPools(ownerKey).size();
        JsonNode ent = systemClient.consumers()
            .bindPool(systemUser.getUuid(), limitedPrimaryPool.getId(), virtLimit).get(0);
        List<PoolDTO> pools = adminClient.owners().listOwnerPools(ownerKey);
        assertEquals(initialPoolSize + 1, pools.size());

        List<PoolDTO> entDerivedPools = pools.stream()
            .filter(pool -> "ENTITLEMENT_DERIVED".equals(pool.getType()))
            .collect(Collectors.toList());
        assertEquals(1, entDerivedPools.size());
        PoolDTO entDerivedPool = entDerivedPools.get(0);
        assertEquals(virtLimit, entDerivedPool.getQuantity());
        assertEquals(entDerivedPool.getSourceEntitlement().getId(), ent.get("id").asText());

        List<AttributeDTO> atts = entDerivedPool.getAttributes().stream()
            .filter(att -> att.getName().equals("requires_host"))
            .collect(Collectors.toList());
        assertEquals(1, atts.size());
        assertEquals(systemUser.getUuid(), atts.get(0).getValue());

        EntitlementDTO entitlement  = ApiClient.MAPPER.convertValue(ent, EntitlementDTO.class);
        entitlement.setQuantity(1);
        adminClient.entitlements().updateEntitlement(entitlement.getId(), entitlement);
        entDerivedPool = adminClient.pools().getPool(entDerivedPool.getId(), null, null);
        assertEquals(virtLimit, entDerivedPool.getQuantity());
    }

    @Test
    @OnlyInHosted
    void shouldRevokeExcessEntitlementsWhenFiniteVirtLimitedPrimaryPoolIsExported() throws Exception {
        ConsumerDTO cpUser = Consumers.random(owner, ConsumerTypes.Candlepin);
        cpUser = adminClient.consumers().createConsumer(cpUser);
        ApiClient candlepinClient = ApiClients.ssl(cpUser);

        ConsumerDTO guest = Consumers.random(owner, ConsumerTypes.System);
        guest.setUuid(StringUtil.random("guest"));
        guest.setFacts(Map.of("virt.is_guest", "true", "virt.uuid", guest.getUuid()));
        guest = adminClient.consumers().createConsumer(guest);
        ApiClient guestClient = ApiClients.ssl(guest);

        int virtLimit = 4;
        ProductDTO limitVirtProd = createLimitedVirtProduct(adminClient, ownerKey, virtLimit);
        PoolDTO limitedPrimaryPool = Pools.randomUpstream(limitVirtProd);
        limitedPrimaryPool = adminClient.owners().createPool(ownerKey, limitedPrimaryPool);
        PoolDTO limitedBonusPool =
            getBonusPool(adminClient, ownerKey, limitedPrimaryPool.getSubscriptionId());
        int initialBonusPoolQuantity = limitedBonusPool.getQuantity().intValue();

        JsonNode ent = guestClient.consumers().bindPool(guest.getUuid(), limitedBonusPool.getId(), 1).get(0);
        assertEquals(1, ent.get("quantity").asInt());

        // reduce by quantity * virt_limit
        int bindQuantity = 2;
        int expectedRemainingBonusPoolQuant = initialBonusPoolQuantity - (virtLimit * bindQuantity);
        candlepinClient.consumers().bindPool(cpUser.getUuid(), limitedPrimaryPool.getId(), bindQuantity);
        limitedBonusPool = adminClient.pools().getPool(limitedBonusPool.getId(), null, null);
        assertEquals(expectedRemainingBonusPoolQuant, limitedBonusPool.getQuantity());

        // if fully exported, bonus pool is set to 0 quantity
        int remainingBindQuant = expectedRemainingBonusPoolQuant / virtLimit;
        candlepinClient.consumers().bindPool(
            cpUser.getUuid(), limitedPrimaryPool.getId(), remainingBindQuant);
        limitedBonusPool = adminClient.pools().getPool(limitedBonusPool.getId(), null, null);
        assertEquals(0, limitedBonusPool.getQuantity());

        // and it's entitlement is revoked
        assertNotFound(() -> adminClient.entitlements().getEntitlement(ent.get("id").asText()));
    }

    @Test
    @OnlyInHosted
    void shouldRevokeOnlySufficientEntitlementsWhenFiniteVirtLimitedPrimaryPoolIsExported() throws Exception {
        ConsumerDTO cpUser = Consumers.random(owner, ConsumerTypes.Candlepin);
        cpUser = adminClient.consumers().createConsumer(cpUser);
        ApiClient candlepinClient = ApiClients.ssl(cpUser);

        ConsumerDTO guest = Consumers.random(owner, ConsumerTypes.System);
        guest.setUuid(StringUtil.random("guest"));
        guest.setFacts(Map.of("virt.is_guest", "true", "virt.uuid", guest.getUuid()));
        guest = adminClient.consumers().createConsumer(guest);
        ApiClient guestClient = ApiClients.ssl(guest);

        ConsumerDTO guest2 = Consumers.random(owner, ConsumerTypes.System);
        guest2.setUuid(StringUtil.random("guest"));
        guest2.setFacts(Map.of("virt.is_guest", "true", "virt.uuid", guest2.getUuid()));
        guest2 = adminClient.consumers().createConsumer(guest2);
        ApiClient guestClient2 = ApiClients.ssl(guest2);

        int virtLimit = 4;
        int primaryPoolQuant = 10;
        ProductDTO limitVirtProd = createLimitedVirtProduct(adminClient, ownerKey, virtLimit);
        PoolDTO limitedPrimaryPool = Pools.randomUpstream(limitVirtProd)
            .quantity(Long.valueOf(primaryPoolQuant));
        limitedPrimaryPool = adminClient.owners().createPool(ownerKey, limitedPrimaryPool);
        PoolDTO limitedBonusPool =
            getBonusPool(adminClient, ownerKey, limitedPrimaryPool.getSubscriptionId());
        assertEquals(virtLimit * primaryPoolQuant, limitedBonusPool.getQuantity());
        assertEquals(0, limitedBonusPool.getConsumed());

        int initialBonusPoolQuantity = limitedBonusPool.getQuantity().intValue();

        // create two ents of qty 4 each
        JsonNode ent1 = guestClient.consumers()
            .bindPool(guest.getUuid(), limitedBonusPool.getId(), 4).get(0);
        assertEquals(4, ent1.get("quantity").asInt());

        JsonNode ent2 = guestClient2.consumers()
            .bindPool(guest2.getUuid(), limitedBonusPool.getId(), 4).get(0);
        assertEquals(4, ent2.get("quantity").asInt());

        limitedBonusPool = adminClient.pools().getPool(limitedBonusPool.getId(), null, null);
        assertEquals(8, limitedBonusPool.getConsumed());

        // reduce by quantity * virt_limit
        int bindQuantity = 9;
        candlepinClient.consumers().bindPool(cpUser.getUuid(), limitedPrimaryPool.getId(), bindQuantity);
        limitedBonusPool = adminClient.pools().getPool(limitedBonusPool.getId(), null, null);
        assertEquals(initialBonusPoolQuantity - (virtLimit * bindQuantity), limitedBonusPool.getConsumed());

        // verify only one of the ents was revoked, and quantity is still 4
        boolean ent1Revoked = false;
        try {
            EntitlementDTO ent = adminClient.entitlements().getEntitlement(ent1.get("id").asText());
            assertEquals(4, ent.getQuantity());
        }
        catch (ApiException e) {
            assertThat(e).hasFieldOrPropertyWithValue("code", 404);
            ent1Revoked = true;
        }

        if (ent1Revoked) {
            EntitlementDTO ent = adminClient.entitlements().getEntitlement(ent2.get("id").asText());
            assertEquals(4, ent.getQuantity());
        }
        else {
            assertNotFound(() -> adminClient.entitlements().getEntitlement(ent2.get("id").asText()));
        }
    }

    @Test
    @OnlyInHosted
    void shouldRevokeSufficientEntitlementsWhenEntitlementQuantityIsUpdated() throws Exception {
        ConsumerDTO cpUser = Consumers.random(owner, ConsumerTypes.Candlepin);
        cpUser = adminClient.consumers().createConsumer(cpUser);
        ApiClient candlepinClient = ApiClients.ssl(cpUser);

        ConsumerDTO guest = Consumers.random(owner, ConsumerTypes.System);
        guest.setUuid(StringUtil.random("guest"));
        guest.setFacts(Map.of("virt.is_guest", "true", "virt.uuid", guest.getUuid()));
        guest = adminClient.consumers().createConsumer(guest);
        ApiClient guestClient = ApiClients.ssl(guest);

        int virtLimit = 4;
        int primaryPoolQuant = 10;
        ProductDTO limitVirtProd = createLimitedVirtProduct(adminClient, ownerKey, 4);
        PoolDTO limitedPrimaryPool = Pools.randomUpstream(limitVirtProd)
            .quantity(Long.valueOf(primaryPoolQuant));
        limitedPrimaryPool = adminClient.owners().createPool(ownerKey, limitedPrimaryPool);
        PoolDTO limitedBonusPool =
            getBonusPool(adminClient, ownerKey, limitedPrimaryPool.getSubscriptionId());
        int initialBonusPoolQuantity = limitedBonusPool.getQuantity().intValue();
        assertEquals(virtLimit * primaryPoolQuant, initialBonusPoolQuantity);
        assertEquals(0, limitedBonusPool.getConsumed());

        // create a bonus pool ent
        JsonNode bonusEnt = guestClient.consumers()
            .bindPool(guest.getUuid(), limitedBonusPool.getId(), 4).get(0);
        assertEquals(4, bonusEnt.get("quantity").asInt());
        limitedBonusPool = adminClient.pools().getPool(limitedBonusPool.getId(), null, null);
        assertEquals(4, limitedBonusPool.getConsumed());

        // reduce by quantity * virt_limit
        int bindQuantity = 9;
        JsonNode primaryEnt = candlepinClient.consumers()
            .bindPool(cpUser.getUuid(), limitedPrimaryPool.getId(), bindQuantity).get(0);
        limitedBonusPool = adminClient.pools().getPool(limitedBonusPool.getId(), null, null);
        assertEquals(initialBonusPoolQuantity - (virtLimit * bindQuantity), limitedBonusPool.getQuantity());

        // verify ent still exists
        adminClient.entitlements().getEntitlement(bonusEnt.get("id").asText());

        EntitlementDTO entitlement  = ApiClient.MAPPER.convertValue(primaryEnt, EntitlementDTO.class);
        int newEntitlementQuant = 10;
        entitlement.setQuantity(newEntitlementQuant);
        adminClient.entitlements().updateEntitlement(entitlement.getId(), entitlement);

        // verify bonus pool quantity was updated
        int expectedBonusPoolQuant = initialBonusPoolQuantity - (newEntitlementQuant * virtLimit);
        limitedBonusPool = adminClient.pools().getPool(limitedBonusPool.getId(), null, null);
        assertEquals(expectedBonusPoolQuant, limitedBonusPool.getQuantity());

        // verify the ent was revoked
        assertNotFound(() -> adminClient.entitlements().getEntitlement(bonusEnt.get("id").asText()));
    }

    @Test
    @OnlyInHosted
    void shouldNotRevokeExcessEntitlementsWhenUnlimitedVirtLimitedPrimaryPoolIsPartiallyExported()
        throws Exception {
        ConsumerDTO cpUser = Consumers.random(owner, ConsumerTypes.Candlepin);
        cpUser = adminClient.consumers().createConsumer(cpUser);
        ApiClient candlepinClient = ApiClients.ssl(cpUser);

        ConsumerDTO guest = Consumers.random(owner, ConsumerTypes.System);
        guest.setUuid(StringUtil.random("guest"));
        guest.setFacts(Map.of("virt.is_guest", "true", "virt.uuid", guest.getUuid()));
        guest = adminClient.consumers().createConsumer(guest);
        ApiClient guestClient = ApiClients.ssl(guest);

        ProductDTO unlimitedVirtProd = createUnlimitedVirtProduct(adminClient, ownerKey);
        PoolDTO unlimitedPrimaryPool = Pools.randomUpstream(unlimitedVirtProd);
        unlimitedPrimaryPool = adminClient.owners().createPool(ownerKey, unlimitedPrimaryPool);
        PoolDTO unlimitedBonusPool =
            getBonusPool(adminClient, ownerKey, unlimitedPrimaryPool.getSubscriptionId());

        JsonNode ent = guestClient.consumers()
            .bindPool(guest.getUuid(), unlimitedBonusPool.getId(), 10).get(0);
        assertEquals(10, ent.get("quantity").asInt());
        unlimitedBonusPool = adminClient.pools().getPool(unlimitedBonusPool.getId(), null, null);
        assertEquals(-1, unlimitedBonusPool.getQuantity());

        // not revoked until primary is completely consumed
        candlepinClient.consumers().bindPool(cpUser.getUuid(), unlimitedPrimaryPool.getId(), 9);
        unlimitedBonusPool = adminClient.pools().getPool(unlimitedBonusPool.getId(), null, null);
        assertEquals(-1, unlimitedBonusPool.getQuantity());
        adminClient.entitlements().getEntitlement(ent.get("id").asText());

        candlepinClient.consumers().bindPool(cpUser.getUuid(), unlimitedPrimaryPool.getId(), 1);
        unlimitedBonusPool = adminClient.pools().getPool(unlimitedBonusPool.getId(), null, null);
        assertEquals(0, unlimitedBonusPool.getQuantity());
        assertNotFound(() -> adminClient.entitlements().getEntitlement(ent.get("id").asText()));
    }

    @Test
    @OnlyInHosted
    void shouldNotRevokeExcessEntitlementsWhenUnlimitedVirtLimitedPrimaryPoolIsConsumedByNonManifestConsumer()
        throws Exception {
        ConsumerDTO cpUser = Consumers.random(owner, ConsumerTypes.Candlepin);
        cpUser = adminClient.consumers().createConsumer(cpUser);
        ApiClient candlepinClient = ApiClients.ssl(cpUser);

        ConsumerDTO guest = Consumers.random(owner, ConsumerTypes.System);
        guest.setUuid(StringUtil.random("guest"));
        guest.setFacts(Map.of("virt.is_guest", "true", "virt.uuid", guest.getUuid()));
        guest = adminClient.consumers().createConsumer(guest);
        ApiClient guestClient = ApiClients.ssl(guest);

        ConsumerDTO guest2 = Consumers.random(owner, ConsumerTypes.System);
        guest2.setUuid(StringUtil.random("guest"));
        guest2.setFacts(Map.of("virt.is_guest", "true", "virt.uuid", guest2.getUuid()));
        guest2 = adminClient.consumers().createConsumer(guest2);
        ApiClient guestClient2 = ApiClients.ssl(guest2);

        ProductDTO unlimitedVirtProd = createUnlimitedVirtProduct(adminClient, ownerKey);
        PoolDTO unlimitedPrimaryPool = Pools.randomUpstream(unlimitedVirtProd);
        unlimitedPrimaryPool = adminClient.owners().createPool(ownerKey, unlimitedPrimaryPool);
        PoolDTO unlimitedBonusPool =
            getBonusPool(adminClient, ownerKey, unlimitedPrimaryPool.getSubscriptionId());

        int entBindQuant = 10;
        JsonNode ent = guestClient.consumers()
            .bindPool(guest.getUuid(), unlimitedBonusPool.getId(), entBindQuant).get(0);
        assertEquals(10, ent.get("quantity").asInt());
        unlimitedBonusPool = adminClient.pools().getPool(unlimitedBonusPool.getId(), null, null);
        assertEquals(-1, unlimitedBonusPool.getQuantity());

        // consumer primary pool from non candlepin type consumer
        guestClient2.consumers().bindPool(guest2.getUuid(), unlimitedPrimaryPool.getId(), 1);
        unlimitedPrimaryPool = adminClient.pools().getPool(unlimitedPrimaryPool.getId(), null, null);
        assertEquals(1, unlimitedPrimaryPool.getConsumed());
        assertEquals(0, unlimitedPrimaryPool.getExported());

        // even if 1 qty is consumed but not exported, do not set bonus pool to qty 0
        candlepinClient.consumers().bindPool(cpUser.getUuid(), unlimitedPrimaryPool.getId(), 9);
        unlimitedBonusPool = adminClient.pools().getPool(unlimitedBonusPool.getId(), null, null);
        assertEquals(-1, unlimitedBonusPool.getQuantity());

        EntitlementDTO entitlement = adminClient.entitlements().getEntitlement(ent.get("id").asText());
        assertEquals(entBindQuant, entitlement.getQuantity());
    }

    @Test
    @OnlyInStandalone
    void shouldAllowUnlimitedConsumptionOfBonusPoolsForUnlimitedQuantityPrimaryPoolStandalone()
        throws Exception {
        ConsumerDTO guest = Consumers.random(owner, ConsumerTypes.System);
        guest.setUuid(StringUtil.random("guest"));
        guest.setFacts(Map.of("virt.is_guest", "true", "virt.uuid", guest.getUuid()));
        guest = adminClient.consumers().createConsumer(guest);
        ApiClient guestClient = ApiClients.ssl(guest);

        ProductDTO unlimitedProd = createUnlimitedProduct(adminClient, ownerKey, 4);
        PoolDTO unlimitedProdPrimaryPool = Pools.randomUpstream(unlimitedProd).quantity(-1L);
        unlimitedProdPrimaryPool = adminClient.owners().createPool(ownerKey, unlimitedProdPrimaryPool);
        PoolDTO unlimitedProdBonusPool =
            getBonusPool(adminClient, ownerKey, unlimitedProdPrimaryPool.getSubscriptionId());

        // Consume bonus pool
        JsonNode ents = guestClient.consumers().bindPool(guest.getUuid(), unlimitedProdBonusPool.getId(), 1);
        unlimitedProdBonusPool = adminClient.pools().getPool(unlimitedProdBonusPool.getId(), null, null);
        assertEquals(-1, unlimitedProdBonusPool.getQuantity());

        // primary pool quantity remains unchanged
        unlimitedProdPrimaryPool = adminClient.pools().getPool(unlimitedProdPrimaryPool.getId(), null, null);
        assertEquals(-1, unlimitedProdPrimaryPool.getQuantity());

        // unbind & refresh
        guestClient.consumers().unbindByEntitlementId(guest.getUuid(), ents.get(0).get("id").asText());
        unlimitedProdBonusPool = adminClient.pools().getPool(unlimitedProdBonusPool.getId(), null, null);
        assertEquals(-1, unlimitedProdBonusPool.getQuantity());
        unlimitedProdPrimaryPool = adminClient.pools().getPool(unlimitedProdPrimaryPool.getId(), null, null);
        assertEquals(-1, unlimitedProdPrimaryPool.getQuantity());
    }

    @Test
    @OnlyInStandalone
    void shouldAllowUnlimitedConsumptionOfPrimaryPoolWhenQuantityIsUnlimitedRegardlessOfVirtLimitStandalone()
        throws Exception {
        ConsumerDTO cpUser = Consumers.random(owner, ConsumerTypes.Candlepin);
        cpUser = adminClient.consumers().createConsumer(cpUser);
        ApiClient candlepinClient = ApiClients.ssl(cpUser);

        ProductDTO unlimitedProd = createUnlimitedProduct(adminClient, ownerKey, 4);
        PoolDTO unlimitedProdPrimaryPool = Pools.randomUpstream(unlimitedProd).quantity(-1L);
        unlimitedProdPrimaryPool = adminClient.owners().createPool(ownerKey, unlimitedProdPrimaryPool);
        assertEquals(-1, unlimitedProdPrimaryPool.getQuantity());

        PoolDTO unlimitedProductBonusPool =
            getBonusPool(adminClient, ownerKey, unlimitedProdPrimaryPool.getSubscriptionId());
        assertEquals(-1, unlimitedProductBonusPool.getQuantity());

        // consume primary pool in any quantity
        JsonNode ents = candlepinClient.consumers()
            .bindPool(cpUser.getUuid(), unlimitedProdPrimaryPool.getId(), 1000);
        unlimitedProdPrimaryPool = adminClient.pools().getPool(unlimitedProdPrimaryPool.getId(), null, null);
        assertEquals(-1, unlimitedProdPrimaryPool.getQuantity());
        unlimitedProductBonusPool = adminClient.pools()
            .getPool(unlimitedProductBonusPool.getId(), null, null);
        assertEquals(-1, unlimitedProductBonusPool.getQuantity());

        // entitlement unbind
        candlepinClient.consumers().unbindByEntitlementId(cpUser.getUuid(), ents.get(0).get("id").asText());
        unlimitedProdPrimaryPool = adminClient.pools().getPool(unlimitedProdPrimaryPool.getId(), null, null);
        assertEquals(-1, unlimitedProdPrimaryPool.getQuantity());
        unlimitedProductBonusPool = adminClient.pools()
            .getPool(unlimitedProductBonusPool.getId(), null, null);
        assertEquals(-1, unlimitedProductBonusPool.getQuantity());
    }

    @Test
    void shouldAllowUnlimitedConsumptionOfUnmappedGuestPoolForUnlimitedVirtLimit() throws Exception {
        ConsumerDTO guest = Consumers.random(owner, ConsumerTypes.System);
        guest.setUuid(StringUtil.random("guest"));
        guest.setFacts(Map.of("virt.is_guest", "true", "virt.uuid", guest.getUuid()));
        guest = adminClient.consumers().createConsumer(guest);
        ApiClient guestClient = ApiClients.ssl(guest);

        ProductDTO hostLimitedUnlimitedVirtProduct =
            createHostLimitedUnlimitedVirtProduct(adminClient, ownerKey);
        PoolDTO hostLimitedUnlimitedVirtPrimaryPool = adminClient.owners()
            .createPool(ownerKey, Pools.randomUpstream(hostLimitedUnlimitedVirtProduct));
        assertEquals(10, hostLimitedUnlimitedVirtPrimaryPool.getQuantity());

        // unmapped_guest pool quantity is expected to be unlimited
        PoolDTO hostLimitedUnlimitedVirtBonusPool =
            getBonusPool(adminClient, ownerKey, hostLimitedUnlimitedVirtPrimaryPool.getSubscriptionId());
        assertEquals(-1, hostLimitedUnlimitedVirtBonusPool.getQuantity());

        guestClient.consumers().bindPool(guest.getUuid(), hostLimitedUnlimitedVirtBonusPool.getId(), 500);
        hostLimitedUnlimitedVirtBonusPool = adminClient.pools()
            .getPool(hostLimitedUnlimitedVirtBonusPool.getId(), null, null);
        assertEquals(-1, hostLimitedUnlimitedVirtBonusPool.getQuantity());
    }

    @Test
    @OnlyInStandalone
    void shouldAllowUnlimitedConsumptionOfUnmappedGuestPoolForUnlimitedPrimaryPoolQuantity()
        throws Exception {
        ConsumerDTO cpUser = Consumers.random(owner, ConsumerTypes.Candlepin);
        cpUser = adminClient.consumers().createConsumer(cpUser);
        ApiClient candlepinClient = ApiClients.ssl(cpUser);

        ConsumerDTO guest = Consumers.random(owner, ConsumerTypes.System);
        guest.setUuid(StringUtil.random("guest"));
        guest.setFacts(Map.of("virt.is_guest", "true", "virt.uuid", guest.getUuid()));
        guest = adminClient.consumers().createConsumer(guest);
        ApiClient guestClient = ApiClients.ssl(guest);

        ProductDTO hostLimitedUnlimitedVirtProd =
            createHostLimitedUnlimitedVirtProduct(adminClient, ownerKey);
        PoolDTO hostAndVirtLimitedProdPrimaryPool = Pools.randomUpstream(hostLimitedUnlimitedVirtProd)
            .quantity(-1L);
        hostAndVirtLimitedProdPrimaryPool = adminClient.owners()
            .createPool(ownerKey, hostAndVirtLimitedProdPrimaryPool);
        PoolDTO hostLimitedUnlimitedVirtBonusPool =
            getBonusPool(adminClient, ownerKey, hostAndVirtLimitedProdPrimaryPool.getSubscriptionId());
        hostAndVirtLimitedProdPrimaryPool = adminClient.pools()
            .getPool(hostAndVirtLimitedProdPrimaryPool.getId(), null, null);
        assertEquals(-1, hostAndVirtLimitedProdPrimaryPool.getQuantity());

        ProductDTO unlimitedProd = createUnlimitedProduct(adminClient, ownerKey, 4);
        PoolDTO unlimitedPrimaryPool = adminClient.owners()
            .createPool(ownerKey, Pools.randomUpstream(unlimitedProd).quantity(-1L));

        // consume primary pool in any quantity
        candlepinClient.consumers().bindPool(cpUser.getUuid(), unlimitedPrimaryPool.getId(), 1000);
        unlimitedPrimaryPool = adminClient.pools().getPool(unlimitedPrimaryPool.getId(), null, null);
        assertEquals(-1, unlimitedPrimaryPool.getQuantity());

        // unmapped_guest pool quantity is expected to be unlimited
        hostLimitedUnlimitedVirtBonusPool = adminClient.pools()
            .getPool(hostLimitedUnlimitedVirtBonusPool.getId(), null, null);
        assertEquals(-1, hostLimitedUnlimitedVirtBonusPool.getQuantity());

        guestClient.consumers().bindPool(guest.getUuid(), hostLimitedUnlimitedVirtBonusPool.getId(), 500);

        hostLimitedUnlimitedVirtBonusPool = adminClient.pools()
            .getPool(hostLimitedUnlimitedVirtBonusPool.getId(), null, null);
        assertEquals(-1, hostLimitedUnlimitedVirtBonusPool.getQuantity());
    }

    @Nested
    @OnlyInHosted
    public class LockedEntityTests {
        private PoolDTO unlimitedProdPrimaryPool;
        private PoolDTO hostAndVirtLimitedProdPrimaryPool;

        @BeforeEach
        void setup() throws Exception {
            SubscriptionDTO unlimitedProdSub = createUnlimitedProdSub();
            SubscriptionDTO limitedHostVirtSub = createLimitedHostVirtSub();

            AsyncJobStatusDTO job = adminClient.owners().refreshPools(ownerKey, false);
            AsyncJobStatusDTO status = adminClient.jobs().waitForJob(job.getId());
            assertEquals("FINISHED", status.getState());

            unlimitedProdPrimaryPool = getPoolBySubId(unlimitedProdSub.getId());
            assertEquals(-1, unlimitedProdPrimaryPool.getQuantity());

            hostAndVirtLimitedProdPrimaryPool = getPoolBySubId(limitedHostVirtSub.getId());
            assertEquals(-1, hostAndVirtLimitedProdPrimaryPool.getQuantity());
        }

        @Test
        void shouldAllowUnlimitedConsumptionOfBonusPoolsForUnlimitedQuantityPrimaryPool() throws Exception {
            ConsumerDTO guest = Consumers.random(owner, ConsumerTypes.System);
            guest.setUuid(StringUtil.random("guest"));
            guest.setFacts(Map.of("virt.is_guest", "true", "virt.uuid", guest.getUuid()));
            guest = adminClient.consumers().createConsumer(guest);
            ApiClient guestClient = ApiClients.ssl(guest);

            PoolDTO unlimitedProdBonusPool =
                getBonusPool(adminClient, ownerKey, unlimitedProdPrimaryPool.getSubscriptionId());
            assertEquals(-1, unlimitedProdBonusPool.getQuantity());

            // Consume bonus pool
            JsonNode ents = guestClient.consumers()
                .bindPool(guest.getUuid(), unlimitedProdBonusPool.getId(), 1);

            AsyncJobStatusDTO job = adminClient.owners().refreshPools(ownerKey, false);
            AsyncJobStatusDTO status = adminClient.jobs().waitForJob(job.getId());
            assertEquals("FINISHED", status.getState());

            unlimitedProdBonusPool = adminClient.pools().getPool(unlimitedProdBonusPool.getId(), null, null);
            assertEquals(-1, unlimitedProdBonusPool.getQuantity());

            // primary pool quantity remains unchanged
            unlimitedProdPrimaryPool = adminClient.pools()
                .getPool(unlimitedProdPrimaryPool.getId(), null, null);
            assertEquals(-1, unlimitedProdPrimaryPool.getQuantity());

            // unbind & refresh
            guestClient.consumers().unbindByEntitlementId(guest.getUuid(), ents.get(0).get("id").asText());

            job = adminClient.owners().refreshPools(ownerKey, false);
            status = adminClient.jobs().waitForJob(job.getId());
            assertEquals("FINISHED", status.getState());

            unlimitedProdBonusPool = adminClient.pools().getPool(unlimitedProdBonusPool.getId(), null, null);
            assertEquals(-1, unlimitedProdBonusPool.getQuantity());
            unlimitedProdPrimaryPool = adminClient.pools()
                .getPool(unlimitedProdPrimaryPool.getId(), null, null);
            assertEquals(-1, unlimitedProdPrimaryPool.getQuantity());
        }

        @Test
        void shouldAllowUnlimitedConsumptionOfPrimaryPoolWhenQuantityIsUnlimitedRegardlessOfVirtLimit()
            throws Exception {
            ConsumerDTO cpUser = Consumers.random(owner, ConsumerTypes.Candlepin);
            cpUser = adminClient.consumers().createConsumer(cpUser);
            ApiClient candlepinClient = ApiClients.ssl(cpUser);

            PoolDTO unlimitedProductBonusPool =
                getBonusPool(adminClient, ownerKey, unlimitedProdPrimaryPool.getSubscriptionId());
            assertEquals(-1, unlimitedProductBonusPool.getQuantity());

            // consume primary pool in any quantity
            JsonNode ents = candlepinClient.consumers()
                .bindPool(cpUser.getUuid(), unlimitedProdPrimaryPool.getId(), 1000);
            unlimitedProdPrimaryPool = adminClient.pools()
                .getPool(unlimitedProdPrimaryPool.getId(), null, null);
            assertEquals(-1, unlimitedProdPrimaryPool.getQuantity());
            unlimitedProductBonusPool = adminClient.pools()
                .getPool(unlimitedProductBonusPool.getId(), null, null);
            assertEquals(-1, unlimitedProductBonusPool.getQuantity());

            AsyncJobStatusDTO job = adminClient.owners().refreshPools(ownerKey, false);
            AsyncJobStatusDTO status = adminClient.jobs().waitForJob(job.getId());
            assertEquals("FINISHED", status.getState());

            unlimitedProdPrimaryPool = adminClient.pools()
                .getPool(unlimitedProdPrimaryPool.getId(), null, null);
            assertEquals(-1, unlimitedProdPrimaryPool.getQuantity());
            unlimitedProductBonusPool = adminClient.pools()
                .getPool(unlimitedProductBonusPool.getId(), null, null);
            assertEquals(-1, unlimitedProductBonusPool.getQuantity());

            // entitlement unbind
            candlepinClient.consumers()
                .unbindByEntitlementId(cpUser.getUuid(), ents.get(0).get("id").asText());
            unlimitedProdPrimaryPool = adminClient.pools()
                .getPool(unlimitedProdPrimaryPool.getId(), null, null);
            assertEquals(-1, unlimitedProdPrimaryPool.getQuantity());
            unlimitedProductBonusPool = adminClient.pools()
                .getPool(unlimitedProductBonusPool.getId(), null, null);
            assertEquals(-1, unlimitedProductBonusPool.getQuantity());
        }

        @Test
        void shouldAllowUnlimitedConsumptionOfUnmappedGuestPoolForUnlimitedPrimaryPoolQuantity()
            throws Exception {
            ConsumerDTO cpUser = Consumers.random(owner, ConsumerTypes.Candlepin);
            cpUser = adminClient.consumers().createConsumer(cpUser);
            ApiClient candlepinClient = ApiClients.ssl(cpUser);

            ConsumerDTO guest = Consumers.random(owner, ConsumerTypes.System);
            guest.setUuid(StringUtil.random("guest"));
            guest.setFacts(Map.of("virt.is_guest", "true", "virt.uuid", guest.getUuid()));
            guest = adminClient.consumers().createConsumer(guest);
            ApiClient guestClient = ApiClients.ssl(guest);

            PoolDTO hostLimitedUnlimitedVirtBonusPool =
                getBonusPool(adminClient, ownerKey, hostAndVirtLimitedProdPrimaryPool.getSubscriptionId());
            hostAndVirtLimitedProdPrimaryPool = adminClient.pools()
                .getPool(hostAndVirtLimitedProdPrimaryPool.getId(), null, null);

            ProductDTO unlimitedProd = createUnlimitedProduct(adminClient, ownerKey, 4);
            PoolDTO unlimitedPrimaryPool = adminClient.owners()
                .createPool(ownerKey, Pools.randomUpstream(unlimitedProd).quantity(-1L));

            // consume primary pool in any quantity
            candlepinClient.consumers().bindPool(cpUser.getUuid(), unlimitedPrimaryPool.getId(), 1000);
            unlimitedPrimaryPool = adminClient.pools().getPool(unlimitedPrimaryPool.getId(), null, null);
            assertEquals(-1, unlimitedPrimaryPool.getQuantity());

            // unmapped_guest pool quantity is expected to be unlimited
            hostLimitedUnlimitedVirtBonusPool = adminClient.pools()
                .getPool(hostLimitedUnlimitedVirtBonusPool.getId(), null, null);
            assertEquals(-1, hostLimitedUnlimitedVirtBonusPool.getQuantity());

            guestClient.consumers().bindPool(guest.getUuid(), hostLimitedUnlimitedVirtBonusPool.getId(), 500);

            AsyncJobStatusDTO job = adminClient.owners().refreshPools(ownerKey, false);
            AsyncJobStatusDTO status = adminClient.jobs().waitForJob(job.getId());
            assertEquals("FINISHED", status.getState());

            hostLimitedUnlimitedVirtBonusPool = adminClient.pools()
                .getPool(hostLimitedUnlimitedVirtBonusPool.getId(), null, null);
            assertEquals(-1, hostLimitedUnlimitedVirtBonusPool.getQuantity());
        }

        private PoolDTO getPoolBySubId(String subscriptionId) throws ApiException {
            List<PoolDTO> foundPools = adminClient.owners().listOwnerPools(ownerKey).stream()
                .filter(pool -> pool.getSubscriptionId().equals(subscriptionId) &&
                pool.getType().equals("NORMAL"))
                .collect(Collectors.toList());
            assertEquals(1, foundPools.size());

            return foundPools.get(0);
        }

    }

    private PoolDTO getBonusPool(ApiClient client, String ownerKey, String subscriptionId)
        throws ApiException {
        List<PoolDTO> pools = client.owners().listOwnerPools(ownerKey).stream()
            .filter(pool -> pool.getSubscriptionId().equals(subscriptionId) &&
            !pool.getType().equals("NORMAL"))
            .collect(Collectors.toList());

        assertEquals(1, pools.size());

        return pools.get(0);
    }

    private void verifyBindTimePoolCreation(ApiClient client, OwnerDTO owner, PoolDTO pool,
        ConsumerDTO consumer, boolean shouldCreate) throws ApiException {
        List<String> poolIdsBefore = client.owners().listOwnerPools(owner.getKey())
            .stream().map(ownerPool -> ownerPool.getId())
            .collect(Collectors.toList());

        client.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        List<String> poolIdsAfter = client.owners().listOwnerPools(owner.getKey())
            .stream().map(ownerPool -> ownerPool.getId())
            .collect(Collectors.toList());

        if (shouldCreate) {
            assertEquals(poolIdsBefore.size() + 1, poolIdsAfter.size());
        }
        else {
            assertEquals(poolIdsBefore.size(), poolIdsAfter.size());
        }

        long newlyAddedPoolCount = poolIdsAfter.stream()
            .filter(id -> !poolIdsBefore.contains(id))
            .count();

        if (shouldCreate) {
            assertEquals(1, newlyAddedPoolCount);
        }
        else {
            assertEquals(0, newlyAddedPoolCount);
        }
    }

    private ProductDTO createLimitedVirtProduct(ApiClient client, String ownerKey, int virtLimit)
        throws ApiException {
        ProductDTO limitedVirtProd = Products.withAttributes(
            ProductAttributes.VirtualLimit.withValue(String.valueOf(virtLimit)),
            ProductAttributes.MultiEntitlement.withValue("yes"),
            ProductAttributes.Type.withValue("SVC"));

        return client.ownerProducts().createProduct(ownerKey, limitedVirtProd);
    }

    private SubscriptionDTO createLimitedHostVirtSub() {
        ProductDTO hostAndVirtLimitedProd = Products.withAttributes(
            ProductAttributes.VirtualLimit.withValue(String.valueOf(4)),
            ProductAttributes.MultiEntitlement.withValue("yes"),
            ProductAttributes.HostLimited.withValue("true"),
            ProductAttributes.Type.withValue("SVC"));
        hostAndVirtLimitedProd.setMultiplier(1L);
        hostAndVirtLimitedProd = adminClient.hosted().createProduct(hostAndVirtLimitedProd);
        SubscriptionDTO subscription = Subscriptions.random(owner, hostAndVirtLimitedProd);
        subscription.quantity(-1L);

        return adminClient.hosted().createSubscription(subscription);
    }

    private ProductDTO createUnlimitedVirtProduct(ApiClient client, String ownerKey) throws ApiException {
        ProductDTO unlimitedVirtProd = Products.withAttributes(
            ProductAttributes.VirtualLimit.withValue("unlimited"),
            ProductAttributes.MultiEntitlement.withValue("yes"));

        return client.ownerProducts().createProduct(ownerKey, unlimitedVirtProd);
    }

    private ProductDTO createUnlimitedProduct(ApiClient client, String ownerKey, int virtLimit)
        throws ApiException {
        ProductDTO unlimitedProd = Products.withAttributes(
            ProductAttributes.VirtualLimit.withValue(String.valueOf(virtLimit)),
            ProductAttributes.MultiEntitlement.withValue("yes"),
            ProductAttributes.Unlimited.withValue("true"));

        return client.ownerProducts().createProduct(ownerKey, unlimitedProd);
    }

    private SubscriptionDTO createUnlimitedProdSub() {
        ProductDTO unlimitedProd = Products.withAttributes(
            ProductAttributes.VirtualLimit.withValue(String.valueOf(4)),
            ProductAttributes.MultiEntitlement.withValue("yes"),
            ProductAttributes.Unlimited.withValue("true"));
        unlimitedProd = adminClient.hosted().createProduct(unlimitedProd);
        SubscriptionDTO unlimitedProdSub = Subscriptions.random(owner, unlimitedProd);
        unlimitedProdSub.quantity(-1L);

        return adminClient.hosted().createSubscription(unlimitedProdSub);
    }

    private ProductDTO createHostLimitedProduct(ApiClient client, String ownerKey) throws ApiException {
        ProductDTO hostLimitedProd = Products.withAttributes(
            ProductAttributes.VirtualLimit.withValue("unlimited"),
            ProductAttributes.MultiEntitlement.withValue("yes"),
            ProductAttributes.HostLimited.withValue("true"));

        return client.ownerProducts().createProduct(ownerKey, hostLimitedProd);
    }

    private ProductDTO createVirtLimitStackedProduct(ApiClient client, String ownerKey, int virtLimit)
        throws ApiException {
        ProductDTO virtLimitStackedProd = Products.withAttributes(
            ProductAttributes.VirtualLimit.withValue(String.valueOf(virtLimit)),
            ProductAttributes.MultiEntitlement.withValue("yes"),
            ProductAttributes.StackingId.withValue(StringUtil.random("stack-")));

        return client.ownerProducts().createProduct(ownerKey, virtLimitStackedProd);
    }

    private ProductDTO createHostLimitedStackedProduct(ApiClient client, String ownerKey, int virtLimit)
        throws ApiException {
        ProductDTO hostLimitedStackedProd = Products.withAttributes(
            ProductAttributes.VirtualLimit.withValue(String.valueOf(virtLimit)),
            ProductAttributes.MultiEntitlement.withValue("yes"),
            ProductAttributes.StackingId.withValue(StringUtil.random("stack-")),
            ProductAttributes.HostLimited.withValue("true"));

        return client.ownerProducts().createProduct(ownerKey, hostLimitedStackedProd);
    }

    private ProductDTO createHostLimitedUnlimitedVirtProduct(ApiClient client, String ownerKey)
        throws ApiException {
        ProductDTO hostLimitedUnlimitedVirtProd = Products.withAttributes(
            ProductAttributes.VirtualLimit.withValue("unlimited"),
            ProductAttributes.MultiEntitlement.withValue("yes"),
            ProductAttributes.HostLimited.withValue("true"));

        return client.ownerProducts().createProduct(ownerKey, hostLimitedUnlimitedVirtProd);
    }

}
