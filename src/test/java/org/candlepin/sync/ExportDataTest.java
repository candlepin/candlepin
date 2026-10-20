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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Principal;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
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
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.SCACertificate;
import org.candlepin.pki.certs.SCACertificateGenerator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

class ExportDataTest {
    private List<ProductContent> certificateContent;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testExportsOwnerPoolsAndProductGraph(boolean filterNamespaces) throws Exception {
        Product shared = new Product("shared", "Shared");
        Content globalContent = new Content("global-content");
        shared.addContent(globalContent, true);
        shared.addContent(new Content("private-content").setNamespace("private"), true);
        Product provided = new Product("provided", "Provided");
        provided.addProvidedProduct(shared);
        provided.addContent(globalContent, false);
        Product privateProduct = new Product("private-product", "Private").setNamespace("private");
        privateProduct.addContent(new Content("private-product-content"), false);
        provided.addProvidedProduct(privateProduct);
        Product derived = new Product("derived", "Derived");
        derived.addProvidedProduct(shared);
        Product root = new Product("root", "Root").setDerivedProduct(derived);
        root.addProvidedProduct(provided);
        Product secondRoot = new Product("second-root", "Second Root");
        secondRoot.addProvidedProduct(shared);
        Pool firstPool = new Pool()
            .setProduct(root)
            .setSubscriptionId("subscription-1")
            .setStartDate(new Date(1000L))
            .setEndDate(new Date(2000L));
        Pool secondPool = new Pool()
            .setProduct(secondRoot)
            .setSubscriptionId("subscription-2")
            .setStartDate(new Date(3000L))
            .setEndDate(new Date(4000L));

        ExportData data = this.createExportData(List.of(firstPool, secondPool),
            filterNamespaces ? List.of(root, derived, provided, shared, secondRoot) :
                List.of(root, derived, provided, shared, secondRoot, privateProduct), filterNamespaces);

        assertEquals(filterNamespaces ? Set.of("root", "derived", "provided", "shared", "second-root") :
            Set.of("root", "derived", "provided", "shared", "second-root", "private-product"),
            data.getProducts().keySet());
        assertEquals("derived", data.getProducts().get("root").getDerivedProduct());
        assertEquals(Set.of("provided"), data.getProducts().get("root").getProvidedProducts());
        assertEquals(filterNamespaces ? Set.of("shared") : Set.of("shared", "private-product"),
            data.getProducts().get("provided").getProvidedProducts());
        assertEquals(filterNamespaces ? Set.of("global-content") : Set.of("global-content", "private-content"),
            data.getProducts().get("shared").getProductContent().keySet());
        Set<String> selectedIds = this.certificateContent.stream()
            .map(link -> link.getContent().getId())
            .collect(Collectors.toSet());
        assertEquals(filterNamespaces ? Set.of("global-content") :
            Set.of("global-content", "private-content", "private-product-content"), selectedIds);
        assertEquals(Set.of(true, false), this.certificateContent.stream()
            .filter(link -> link.getContent().getId().equals(globalContent.getId()))
            .map(ProductContent::isEnabled)
            .collect(Collectors.toSet()));
        assertEquals(Set.of("subscription-1", "subscription-2"), data.getSubscriptions().keySet());
        assertEquals(firstPool.getStartDate(), data.getSubscriptions().get("subscription-1").getStartDate());
        assertEquals(firstPool.getEndDate(), data.getSubscriptions().get("subscription-1").getEndDate());
        assertEquals(secondPool.getStartDate(), data.getSubscriptions().get("subscription-2").getStartDate());
        assertEquals(secondPool.getEndDate(), data.getSubscriptions().get("subscription-2").getEndDate());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testEmptyOwnerPoolsProduceEmptyExport(boolean filterNamespaces) throws Exception {
        ExportData data = this.createExportData(List.of(), List.of(), filterNamespaces);

        assertTrue(data.getProducts().isEmpty());
        assertTrue(data.getSubscriptions().isEmpty());
        assertTrue(this.certificateContent.isEmpty());
    }

    @Test
    void testPoolWithoutProductIdDoesNotSupplyNullProductId() throws Exception {
        Pool pool = new Pool().setSubscriptionId("subscription");

        ExportData data = this.createExportData(List.of(pool), List.of(), true);

        assertTrue(data.getProducts().isEmpty());
        assertEquals(Set.of("subscription"), data.getSubscriptions().keySet());
        assertNull(data.getSubscriptions().get("subscription").getProductId());
    }

    private ExportData createExportData(List<Pool> pools, List<Product> products, boolean filterNamespaces) throws Exception {
        DevConfig configuration = TestConfig.defaults();
        configuration.setProperty(ConfigProperties.EXPORT_FILTER_NON_GLOBAL_NAMESPACE, Boolean.toString(filterNamespaces));
        StandardTranslator translator = new StandardTranslator(mock(ConsumerTypeCurator.class),
            mock(EnvironmentCurator.class), mock(OwnerCurator.class), configuration);
        Consumer consumer = new Consumer()
            .setOwner(new Owner().setId("owner-id").setKey("owner"))
            .setEntitlements(Set.of());
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("principal");
        PrincipalProvider principalProvider = mock(PrincipalProvider.class);
        when(principalProvider.get()).thenReturn(principal);

        List<String> productIds = pools.stream()
            .map(Pool::getProductId)
            .filter(Objects::nonNull)
            .toList();
        ProductCurator productCurator = mock(ProductCurator.class);
        PoolCurator poolCurator = mock(PoolCurator.class);
        when(poolCurator.listByOwnerForExportV2("owner-id", filterNamespaces, Pool.PoolType.NORMAL)).thenReturn(pools);
        when(productCurator.getProductsByIdsRecursiveForExportV2(filterNamespaces, productIds)).thenReturn(products);

        SCACertificateGenerator generator = mock(SCACertificateGenerator.class);
        SCACertificate certificate = new SCACertificate();
        when(generator.generateScaCertificate(eq(consumer), anyList())).thenAnswer(invocation -> {
            this.certificateContent = List.copyOf(invocation.getArgument(1));
            return certificate;
        });
        ExportData data = new ExportData(consumer, translator, configuration, poolCurator, productCurator,
            principalProvider, generator);

        assertSame(certificate, data.getScaCertificate());
        verify(generator).generateScaCertificate(eq(consumer), anyList());

        verify(poolCurator).listByOwnerForExportV2("owner-id", filterNamespaces, Pool.PoolType.NORMAL);
        verify(productCurator).getProductsByIdsRecursiveForExportV2(filterNamespaces, productIds);
        return data;
    }
}
