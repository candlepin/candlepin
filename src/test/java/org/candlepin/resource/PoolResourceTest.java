/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
package org.candlepin.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Access;
import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.PoolService;
import org.candlepin.dto.api.server.v1.EntitlementDTO;
import org.candlepin.dto.api.server.v1.PoolDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.paging.PageRequest;
import org.candlepin.paging.PageRequest.Order;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.jboss.resteasy.core.ResteasyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;



public class PoolResourceTest extends DatabaseTestFixture {
    // private static final OffsetDateTime START_DATE = OffsetDateTime.now().minusYears(5);
    private static final OffsetDateTime START_DATE = OffsetDateTime
        .of(LocalDate.of(2000, 1, 1), LocalTime.MIDNIGHT, ZoneOffset.UTC);
    private static final OffsetDateTime END_DATE = START_DATE.plusYears(1000);
    private static final String PRODUCT_CPULIMITED = "CPULIMITED001";

    private PoolManager poolManager;
    @Inject
    private PoolService poolService;

    private Owner owner1;
    private Owner owner2;
    private Pool pool1;
    private Pool pool2;
    private Pool pool3;
    private Product product1;
    private Product product2;
    private PoolResource poolResource;
    private Consumer failConsumer;
    private Consumer passConsumer;
    private Consumer foreignConsumer;
    private Principal adminPrincipal;

    @Mock
    private CalculatedAttributesUtil attrUtil;
    @Mock
    private PrincipalProvider principalProvider;

    @BeforeEach
    public void setUp() {
        ResteasyContext.popContextData(PageRequest.class);
        poolManager = this.injector.getInstance(PoolManager.class);
        MockitoAnnotations.initMocks(this);

        owner1 = createOwner();
        owner2 = createOwner();
        ownerCurator.create(owner1);
        ownerCurator.create(owner2);

        product1 = this.createProduct(PRODUCT_CPULIMITED, PRODUCT_CPULIMITED);
        product2 = this.createProduct();

        pool1 = this.createPool(owner1, product1, 500L, Util.toDate(START_DATE), Util.toDate(END_DATE));
        pool2 = this.createPool(owner1, product2, 500L, Util.toDate(START_DATE), Util.toDate(END_DATE));
        pool3 = this.createPool(owner2, product1, 500L, Util.toDate(START_DATE), Util.toDate(END_DATE));

        // Run most of these tests as an owner admin:
        adminPrincipal = setupPrincipal(owner1, Access.ALL);

        poolResource = new PoolResource(consumerCurator, ownerCurator, poolCurator, i18n,
            poolManager, attrUtil, this.modelTranslator, principalProvider, poolService);

        // Consumer system with too many cpu cores:
        failConsumer = this.createConsumer(createOwner());
        failConsumer.setFact("cpu_cores", "4");
        this.consumerCurator.merge(failConsumer);

        // Consumer system with appropriate number of cpu cores:
        passConsumer = this.createConsumer(owner1);
        passConsumer.setFact("cpu_cores", "2");
        this.consumerCurator.merge(passConsumer);

        foreignConsumer = this.createConsumer(owner2);
        foreignConsumer.setFact("cpu_cores", "2");
        this.consumerCurator.merge(foreignConsumer);
    }

    @Test
    public void testUserCannotListAllPools() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);

        assertThrows(ForbiddenException.class, () ->
            poolResource.listPools(null, null, null, false, null, null, null, null, null)
        );
    }

    @Test
    public void testListAll() {
        when(this.principalProvider.get()).thenReturn(setupAdminPrincipal("superadmin"));

        List<PoolDTO> pools = poolResource.listPools(null, null, null, false, null, null, null, null, null);
        assertEquals(3, pools.size());
    }

    @Test
    public void testListForOrg() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        List<PoolDTO> pools = poolResource.listPools(owner1.getId(), null, null,
            false, null, null, null, null, null);
        assertEquals(2, pools.size());

        when(this.principalProvider.get()).thenReturn(setupPrincipal(owner2, Access.ALL));

        pools = poolResource.listPools(owner2.getId(), null, null, false, null, null, null, null, null);
        assertEquals(1, pools.size());
    }

    @Test
    public void testListForProduct() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);

        List<PoolDTO> pools = poolResource.listPools(owner1.getId(), null, product1.getId(),
            false, null, null, null, null, null);
        assertEquals(1, pools.size());

        pools = poolResource.listPools(owner1.getId(), null, product2.getId(),
            false, null, null, null, null, null);
        assertEquals(1, pools.size());
    }

    @Test
    public void testListForOrgAndProduct() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);

        List<PoolDTO> pools = poolResource.listPools(owner1.getId(), null, product1.getId(), false,
            null, null, null, null, null);
        assertEquals(1, pools.size());
    }

    @Test
    public void testCannotListPoolsInAnotherOwner() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);

        assertThrows(NotFoundException.class, () -> poolResource
            .listPools(owner2.getId(), null, product2.getId(), false, null, null, null, null, null));
    }

    @Test
    public void testListConsumerAndProductFiltering() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);

        List<PoolDTO> pools = poolResource.listPools(null, passConsumer.getUuid(),
            product1.getId(), false, null, null, null, null, null);
        assertEquals(1, pools.size());

        verify(attrUtil, times(1))
            .setCalculatedAttributes(argThat(x -> x.size() == 1), any(Date.class));
    }

    @Test
    public void testCannotListPoolsForConsumerInAnotherOwner() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        assertThrows(NotFoundException.class, () -> poolResource.listPools(null, failConsumer.getUuid(),
            product1.getId(), false, null, null, null, null, null)
        );
    }

    // Filtering by both a consumer and an owner makes no sense (we should use the
    // owner of that consumer), so make sure we error if someone tries.
    @Test
    public void testListBlocksConsumerOwnerFiltering() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        assertThrows(BadRequestException.class, () -> poolResource.listPools(owner1.getId(),
            passConsumer.getUuid(), product1.getId(), false, null, null, null, null, null)
        );
    }

    @Test
    public void testListConsumerFiltering() {
        when(this.principalProvider.get()).thenReturn(
            setupPrincipal(new ConsumerPrincipal(passConsumer, owner1)));
        List<PoolDTO> pools = poolResource.listPools(
            null, passConsumer.getUuid(), null, false, null, null, null, null, null);
        assertEquals(2, pools.size());

        verify(attrUtil, times(1))
            .setCalculatedAttributes(argThat(x -> x.size() == 2), any(Date.class));
    }

    @Test
    public void testListNoSuchOwner() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        assertThrows(NotFoundException.class, () ->
            poolResource.listPools("-1", null, null, false, null, null, null, null, null)
        );
    }

    @Test
    public void testListNoSuchConsumer() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        assertThrows(NotFoundException.class, () ->
            poolResource.listPools(null, "blah", null, false, null, null, null, null, null)
        );
    }

    @Test
    public void testListNoSuchProduct() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        assertEquals(0, poolResource.listPools(owner1.getId(), null, "boogity", false,
            null, null, null, null, null).size());
    }

    @Test
    public void ownerAdminCannotListAnotherOwnersPools() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        List<PoolDTO> pools = poolResource
            .listPools(owner1.getId(), null, null, false, null, null, null, null, null);
        assertEquals(2, pools.size());

        setupPrincipal(owner2, Access.ALL);
        securityInterceptor.enable();

        when(this.principalProvider.get()).thenReturn(setupPrincipal(owner2, Access.ALL));
        assertThrows(NotFoundException.class, () ->
            poolResource.listPools(owner1.getId(), null, null, false, null, null, null, null, null)
        );
    }

    @ParameterizedTest(name = "{displayName} {index}: {0} {1}")
    @ValueSource(strings = { "asc", "desc" })
    public void testListPoolsWithPaging(String order) {
        int numberOfPools = 9;
        int pageSize = 3;

        Owner owner = createOwner();

        doReturn(setupPrincipal(new ConsumerPrincipal(passConsumer, owner)))
            .when(principalProvider)
            .get();

        List<String> expectedPoolIds = new ArrayList<>();
        for (int i = 0; i < numberOfPools; i++) {
            Product product = this.createProduct();
            Pool pool = poolCurator.create(TestUtil.createPool(owner, product));
            expectedPoolIds.add(pool.getId());
        }

        Order pageOrder = null;
        if ("asc".equals(order)) {
            Collections.sort(expectedPoolIds);
            pageOrder = Order.ASCENDING;
        }
        else {
            Collections.reverse(expectedPoolIds);
            pageOrder = Order.DESCENDING;
        }

        for (int page = 1; page <= numberOfPools / pageSize; page++) {
            ResteasyContext.pushContext(PageRequest.class,
                new PageRequest()
                    .setPage(page)
                    .setPerPage(pageSize)
                    .setSortBy("id")
                    .setOrder(pageOrder));

            List<PoolDTO> actual = poolResource
                .listPools(owner.getId(), null, null, false, null, page, pageSize, order, "id");

            int pageIndex = (page - 1) * pageSize;

            assertThat(actual)
                .isNotNull()
                .extracting(PoolDTO::getId)
                .containsExactlyElementsOf(expectedPoolIds.subList(pageIndex, pageIndex + pageSize));
        }
    }

    @Test
    public void testConsumerCannotListPoolsForAnotherOwnersConsumer() {
        securityInterceptor.enable();
        when(this.principalProvider.get()).thenReturn(
            setupPrincipal(new ConsumerPrincipal(foreignConsumer, owner2)));
        assertThrows(NotFoundException.class, () -> poolResource.listPools(null, passConsumer.getUuid(), null,
            false, null, null, null, null, null));
    }

    @Test
    public void consumerCannotListPoolsForAnotherOwner() {
        securityInterceptor.enable();
        when(this.principalProvider.get()).thenReturn(
            setupPrincipal(new ConsumerPrincipal(foreignConsumer, owner2)));
        assertThrows(NotFoundException.class,
            () -> poolResource.listPools(owner1.getId(), null, null, false, null, null, null, null, null));
    }

    @Test
    public void consumerCanListOwnersPools() {
        securityInterceptor.enable();
        when(this.principalProvider.get()).thenReturn(
            setupPrincipal(new ConsumerPrincipal(passConsumer, owner1)));

        poolResource.listPools(owner1.getId(), null, null, false, null, null, null, null, null);
    }

    @Test
    public void testActiveOnDate() {
        // Need to be a super admin to do this:
        OffsetDateTime afterStartDate = START_DATE.plusYears(1);
        OffsetDateTime beforeStartDate = START_DATE.minusYears(1);

        when(this.principalProvider.get()).thenReturn(setupAdminPrincipal("superadmin"));

        List<PoolDTO> pools = poolResource
            .listPools(null, null, null, false, afterStartDate, null, null, null, null);
        assertEquals(3, pools.size());

        pools = poolResource.listPools(owner1.getId(), null, null, false,
            beforeStartDate, null, null, null, null);
        assertEquals(0, pools.size());
    }

    @Test
    public void testCalculatedAttributesEmpty() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        PoolDTO p = poolResource.getPool(pool1.getId(), null, null);
        assertTrue(p.getCalculatedAttributes().isEmpty());
    }

    @Test
    public void testUnauthorizedUserRequestingPool() {
        Owner owner2 = createOwner();
        ownerCurator.create(owner2);

        when(this.principalProvider.get()).thenReturn(setupPrincipal(owner2, Access.NONE));

        assertThrows(NotFoundException.class,
            () -> poolResource.getPool(pool1.getId(), passConsumer.getUuid(),
                null));
    }

    @Test
    public void testUnknownConsumerRequestingPool() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        assertThrows(NotFoundException.class, () ->
            poolResource.getPool(pool1.getId(), "xyzzy", null)
        );
    }

    @Test
    public void testEmptyEntitlementList() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        List<EntitlementDTO> ents = poolResource.getPoolEntitlements(pool1.getId());
        assertEquals(0, ents.size());
    }

    @Test
    public void testUnknownConsumerRequestingEntitlements() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        assertThrows(NotFoundException.class, () ->
            poolResource.getPoolEntitlements("xyzzy")
        );
    }

}
