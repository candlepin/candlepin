/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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


package org.candlepin.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Principal;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Content;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolExportProjection;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;

class ExportDataAggregatorTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testExportsOwnerPoolsAndProductGraph(boolean filterNamespaces) {
        Product shared = new Product("shared", "Shared").setNamespace("");
        shared.addContent(new Content("global-content").setNamespace(""), true);
        Product provided = new Product("provided", "Provided").setNamespace("");
        provided.addProvidedProduct(shared);
        Product derived = new Product("derived", "Derived").setNamespace("");
        derived.addProvidedProduct(shared);
        Product root = new Product("root", "Root")
            .setUuid("root-uuid")
            .setNamespace("")
            .setDerivedProduct(derived);
        root.addProvidedProduct(provided);
        Product secondRoot = new Product("second-root", "Second Root")
            .setUuid("second-root-uuid")
            .setNamespace("");
        secondRoot.addProvidedProduct(shared);
        Product privateProvided = new Product("private-provided", "Private Provided").setNamespace("private");
        privateProvided.addContent(new Content("private-content").setNamespace("private"), true);
        Product privateDerived = new Product("private-derived", "Private Derived").setNamespace("private");
        privateDerived.addProvidedProduct(privateProvided);
        Product privateRoot = new Product("private-root", "Private Root")
            .setUuid("private-root-uuid")
            .setNamespace("private")
            .setDerivedProduct(privateDerived);
        privateRoot.addProvidedProduct(privateProvided);
        Pool firstPool = new Pool()
            .setId("pool-1")
            .setProduct(root)
            .setSubscriptionId("subscription-1")
            .setContractNumber("contract")
            .setAccountNumber("account")
            .setStartDate(new Date(1000L))
            .setEndDate(new Date(2000L));
        Pool secondPool = new Pool()
            .setId("pool-2")
            .setProduct(secondRoot)
            .setSubscriptionId("subscription-1")
            .setStartDate(new Date(3000L))
            .setEndDate(new Date(4000L));

        Pool privatePool = new Pool()
            .setId("private-pool")
            .setProduct(privateRoot)
            .setSubscriptionId("private-subscription");

        ExportData data = this.createExportData(
            filterNamespaces ? List.of(firstPool, secondPool) : List.of(firstPool, secondPool, privatePool),
            filterNamespaces ? List.of(root, derived, provided, shared, secondRoot) :
                List.of(root, derived, provided, shared, secondRoot, privateRoot, privateDerived, privateProvided),
            filterNamespaces);

        assertEquals(filterNamespaces ? Set.of("root", "derived", "provided", "shared", "second-root") :
            Set.of("root", "derived", "provided", "shared", "second-root", "private-root", "private-derived",
                "private-provided"), data.getProducts().keySet());
        assertEquals("derived", data.getProducts().get("root").getDerivedProduct());
        assertEquals(Set.of("shared"), data.getProducts().get("derived").getProvidedProducts());
        assertEquals(Set.of("shared"), data.getProducts().get("second-root").getProvidedProducts());
        assertEquals(Set.of("provided"), data.getProducts().get("root").getProvidedProducts());
        assertEquals(Set.of("shared"), data.getProducts().get("provided").getProvidedProducts());
        assertEquals(Set.of("global-content"), data.getProducts().get("shared").getProductContent().keySet());
        assertEquals(filterNamespaces ? Set.of("pool-1", "pool-2") : Set.of("pool-1", "pool-2", "private-pool"),
            data.getSubscriptions().keySet());
        if (!filterNamespaces) {
            assertEquals("private-derived", data.getProducts().get("private-root").getDerivedProduct());
            assertEquals(Set.of("private-provided"), data.getProducts().get("private-root").getProvidedProducts());
            assertEquals(Set.of("private-provided"), data.getProducts().get("private-derived").getProvidedProducts());
            assertEquals(Set.of("private-content"),
                data.getProducts().get("private-provided").getProductContent().keySet());
            assertEquals("private-root", data.getSubscriptions().get("private-pool").getProductId());
            assertEquals("private-subscription", data.getSubscriptions().get("private-pool").getSubscriptionId());
        }
        assertEquals("subscription-1", data.getSubscriptions().get("pool-1").getSubscriptionId());
        assertEquals("subscription-1", data.getSubscriptions().get("pool-2").getSubscriptionId());
        assertEquals("root", data.getSubscriptions().get("pool-1").getProductId());
        assertEquals("contract", data.getSubscriptions().get("pool-1").getContractNumber());
        assertEquals("account", data.getSubscriptions().get("pool-1").getAccountNumber());
        assertEquals(firstPool.getStartDate(), data.getSubscriptions().get("pool-1").getStartDate());
        assertEquals(firstPool.getEndDate(), data.getSubscriptions().get("pool-1").getEndDate());
        assertEquals(secondPool.getStartDate(), data.getSubscriptions().get("pool-2").getStartDate());
        assertEquals(secondPool.getEndDate(), data.getSubscriptions().get("pool-2").getEndDate());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testEmptyOwnerPoolsProduceEmptyExport(boolean filterNamespaces) {
        ExportData data = this.createExportData(List.of(), List.of(), filterNamespaces);

        assertTrue(data.getProducts().isEmpty());
        assertTrue(data.getSubscriptions().isEmpty());
    }

    @Test
    void testPoolWithoutProductIdDoesNotSupplyNullProductId() {
        Pool pool = new Pool().setId("pool").setSubscriptionId("subscription");

        ExportData data = this.createExportData(List.of(pool), List.of(), true);

        assertTrue(data.getProducts().isEmpty());
        assertEquals(Set.of("pool"), data.getSubscriptions().keySet());
        assertNull(data.getSubscriptions().get("pool").getProductId());
    }

    @Test
    void testRejectsNullConsumer() {
        ExportDataAggregator aggregator = new ExportDataAggregator(mock(ModelTranslator.class), TestConfig.defaults(),
            mock(PoolCurator.class), mock(ProductCurator.class), mock(PrincipalProvider.class));

        assertThrows(NullPointerException.class, () -> aggregator.aggregate(null));
    }

    @Test
    void testRepeatedAggregationKeepsExportDataSeparate() {
        DevConfig configuration = TestConfig.defaults();
        configuration.setProperty(ConfigProperties.EXPORT_FILTER_NON_GLOBAL_NAMESPACE, "true");
        configuration.setProperty(ConfigProperties.SYNC_WORK_DIR, "/first-export");
        configuration.setProperty(ConfigProperties.PREFIX_APIURL, "https://first.example.com/api");
        configuration.setProperty(ConfigProperties.PREFIX_WEBURL, "https://first.example.com");
        StandardTranslator translator = new StandardTranslator(mock(ConsumerTypeCurator.class),
            mock(EnvironmentCurator.class), mock(OwnerCurator.class));
        Principal firstPrincipal = mock(Principal.class);
        Principal secondPrincipal = mock(Principal.class);
        when(firstPrincipal.getName()).thenReturn("first-principal");
        when(secondPrincipal.getName()).thenReturn("second-principal");
        PrincipalProvider principalProvider = mock(PrincipalProvider.class);
        when(principalProvider.get()).thenReturn(firstPrincipal, secondPrincipal);
        PoolCurator poolCurator = mock(PoolCurator.class);
        ProductCurator productCurator = mock(ProductCurator.class);
        Product product = new Product("product", "Product").setUuid("product-uuid");
        Pool pool = new Pool().setId("pool").setProduct(product).setSubscriptionId("subscription");
        when(poolCurator.listByOwnerTypeWithNamespaceFilter("first-owner", true, Pool.PoolType.NORMAL))
            .thenReturn(List.of(this.projectPool(pool)));
        when(poolCurator.listByOwnerTypeWithNamespaceFilter("second-owner", true, Pool.PoolType.NORMAL))
            .thenReturn(List.of());
        when(productCurator.getProductsByIdsRecursive(true, List.of("product-uuid")))
            .thenReturn(List.of(product));
        when(productCurator.getProductsByIdsRecursive(true, List.of())).thenReturn(List.of());
        ExportDataAggregator aggregator = new ExportDataAggregator(translator, configuration, poolCurator,
            productCurator, principalProvider);

        ExportData first = aggregator.aggregate(new Consumer().setUuid("first-consumer")
            .setOwner(new Owner().setId("first-owner")).setEntitlements(Set.of()));
        configuration.setProperty(ConfigProperties.SYNC_WORK_DIR, "/second-export");
        configuration.setProperty(ConfigProperties.PREFIX_APIURL, "https://second.example.com/api");
        configuration.setProperty(ConfigProperties.PREFIX_WEBURL, "https://second.example.com");
        ExportData second = aggregator.aggregate(new Consumer().setUuid("second-consumer")
            .setOwner(new Owner().setId("second-owner")).setEntitlements(Set.of()));

        assertEquals(Set.of("product"), first.getProducts().keySet());
        assertEquals(Set.of("pool"), first.getSubscriptions().keySet());
        assertTrue(second.getProducts().isEmpty());
        assertTrue(second.getSubscriptions().isEmpty());
        assertNotSame(first.getProducts(), second.getProducts());
        assertNotSame(first.getSubscriptions(), second.getSubscriptions());
        assertEquals("first-consumer", first.getConsumerDTO().getUuid());
        assertEquals("second-consumer", second.getConsumerDTO().getUuid());
        assertEquals("first-principal", first.getMetaDTO().getPrincipalName());
        assertEquals("second-principal", second.getMetaDTO().getPrincipalName());
        assertEquals("https://first.example.com/api", first.getConsumerDTO().getUrlApi());
        assertEquals("https://second.example.com/api", second.getConsumerDTO().getUrlApi());
        assertEquals("https://first.example.com", first.getConsumerDTO().getUrlWeb());
        assertEquals("https://second.example.com", second.getConsumerDTO().getUrlWeb());
        assertEquals(Paths.get("/first-export"), first.getWorkDirectory());
        assertEquals(Paths.get("/second-export"), second.getWorkDirectory());
    }

    private PoolExportProjection projectPool(Pool pool) {
        return new PoolExportProjection(pool.getId(), pool.getProductUuid(), pool.getProductId(),
            pool.getSubscriptionId(), pool.getCreated(), pool.getUpdated(), pool.getStartDate(), pool.getEndDate(),
            pool.getContractNumber(), pool.getAccountNumber());
    }

    private ExportData createExportData(List<Pool> pools, List<Product> products, boolean filterNamespaces) {
        DevConfig configuration = TestConfig.defaults();
        configuration.setProperty(ConfigProperties.EXPORT_FILTER_NON_GLOBAL_NAMESPACE, Boolean.toString(filterNamespaces));
        StandardTranslator translator = new StandardTranslator(mock(ConsumerTypeCurator.class),
            mock(EnvironmentCurator.class), mock(OwnerCurator.class));
        Consumer consumer = new Consumer()
            .setOwner(new Owner().setId("owner-id").setKey("owner"))
            .setEntitlements(Set.of());
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("principal");
        PrincipalProvider principalProvider = mock(PrincipalProvider.class);
        when(principalProvider.get()).thenReturn(principal);

        List<String> productUuids = pools.stream()
            .map(Pool::getProductUuid)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        ProductCurator productCurator = mock(ProductCurator.class);
        PoolCurator poolCurator = mock(PoolCurator.class);
        when(poolCurator.listByOwnerTypeWithNamespaceFilter("owner-id", filterNamespaces, Pool.PoolType.NORMAL))
            .thenReturn(pools.stream().map(this::projectPool).toList());
        when(productCurator.getProductsByIdsRecursive(filterNamespaces, productUuids)).thenReturn(products);

        ExportData data = new ExportDataAggregator(translator, configuration, poolCurator, productCurator, principalProvider)
            .aggregate(consumer);

        verify(poolCurator).listByOwnerTypeWithNamespaceFilter("owner-id", filterNamespaces, Pool.PoolType.NORMAL);
        verify(productCurator).getProductsByIdsRecursive(filterNamespaces, productUuids);
        return data;
    }
}
