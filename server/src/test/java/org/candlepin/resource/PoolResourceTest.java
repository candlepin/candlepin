/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.auth.Access;
import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.dto.api.v1.EntitlementDTO;
import org.candlepin.dto.api.v1.PoolDTO;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Date;
import java.util.List;

import javax.inject.Inject;


/**
 * PoolResourceTest
 */
public class PoolResourceTest extends DatabaseTestFixture {
    @Inject private CandlepinPoolManager poolManager;

    private Owner owner1;
    private Owner owner2;
    private Pool pool1;
    private Pool pool2;
    private Pool pool3;
    private Product product1;
    private Product product1Owner2;
    private Product product2;
    private PoolResource poolResource;
    private static final String PRODUCT_CPULIMITED = "CPULIMITED001";
    private Consumer failConsumer;
    private Consumer passConsumer;
    private Consumer foreignConsumer;
    private static final int START_YEAR = 2000;
    private static final int END_YEAR = 3000;
    private Principal adminPrincipal;

    @Mock private CalculatedAttributesUtil attrUtil;
    @Mock private PrincipalProvider principalProvider;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        owner1 = createOwner();
        owner2 = createOwner();
        ownerCurator.create(owner1);
        ownerCurator.create(owner2);

        product1 = this.createProduct(PRODUCT_CPULIMITED, PRODUCT_CPULIMITED, owner1);
        product1Owner2 = this.createProduct(PRODUCT_CPULIMITED, PRODUCT_CPULIMITED, owner2);
        product2 = this.createProduct(owner1);

        pool1 = this.createPool(owner1, product1, 500L,
             TestUtil.createDate(START_YEAR, 1, 1), TestUtil.createDate(END_YEAR, 1, 1));
        pool2 = this.createPool(owner1, product2, 500L,
             TestUtil.createDate(START_YEAR, 1, 1), TestUtil.createDate(END_YEAR, 1, 1));
        pool3 = this.createPool(owner2 , product1Owner2, 500L,
             TestUtil.createDate(START_YEAR, 1, 1), TestUtil.createDate(END_YEAR, 1, 1));

        // Run most of these tests as an owner admin:
        adminPrincipal = setupPrincipal(owner1, Access.ALL);

        poolResource = new PoolResource(consumerCurator, ownerCurator, i18n,
            poolManager, attrUtil, this.modelTranslator, principalProvider);

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
            poolResource.list(null, null, null, false, null)
        );
    }

    @Test
    public void testListAll() {
        when(this.principalProvider.get()).thenReturn(setupAdminPrincipal("superadmin"));

        List<PoolDTO> pools = poolResource.list(null, null, null, false, null);
        assertEquals(3, pools.size());
    }

    @Test
    public void testListForOrg() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        List<PoolDTO> pools = poolResource.list(owner1.getId(), null, null,
            false, null);
        assertEquals(2, pools.size());

        when(this.principalProvider.get()).thenReturn(setupPrincipal(owner2, Access.ALL));

        pools = poolResource.list(owner2.getId(), null, null, false, null);
        assertEquals(1, pools.size());
    }

    @Disabled
    @Test
    public void testListForProduct() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);

        List<PoolDTO> pools = poolResource.list(null, null, product1.getId(),
            false, null);
        assertEquals(2, pools.size());
        pools = poolResource.list(null, null, product2.getId(), false, null);
        assertEquals(1, pools.size());
    }

    @Test
    public void testListForOrgAndProduct() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);

        List<PoolDTO> pools = poolResource.list(owner1.getId(), null, product1.getId(), false,
            null);
        assertEquals(1, pools.size());
    }

    @Test
    public void testCannotListPoolsInAnotherOwner() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);

        assertThrows(NotFoundException.class, () ->
            poolResource.list(owner2.getId(), null, product2.getId(), false, null)
        );
    }

    @Test
    public void testListConsumerAndProductFiltering() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);

        List<PoolDTO> pools = poolResource.list(null, passConsumer.getUuid(),
            product1.getId(), false, null);
        assertEquals(1, pools.size());

        verify(attrUtil, times(1))
            .setCalculatedAttributes(argThat(x -> x.size() == 1), any(Date.class));
    }

    @Test
    public void testCannotListPoolsForConsumerInAnotherOwner() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        assertThrows(NotFoundException.class, () -> poolResource.list(null, failConsumer.getUuid(),
            product1.getId(), false, null)
        );
    }

    // Filtering by both a consumer and an owner makes no sense (we should use the
    // owner of that consumer), so make sure we error if someone tries.
    @Test
    public void testListBlocksConsumerOwnerFiltering() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        assertThrows(BadRequestException.class, () -> poolResource.list(owner1.getId(),
            passConsumer.getUuid(), product1.getId(), false, null)
        );
    }

    @Test
    public void testListConsumerFiltering() {
        when(this.principalProvider.get()).thenReturn(
            setupPrincipal(new ConsumerPrincipal(passConsumer, owner1)));
        List<PoolDTO> pools = poolResource.list(
            null, passConsumer.getUuid(), null, false, null);
        assertEquals(2, pools.size());

        verify(attrUtil, times(1))
            .setCalculatedAttributes(argThat(x -> x.size() == 2), any(Date.class));
    }

    @Test
    public void testListNoSuchOwner() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        assertThrows(NotFoundException.class, () ->
            poolResource.list("-1", null, null, false, null)
        );
    }

    @Test
    public void testListNoSuchConsumer() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        assertThrows(NotFoundException.class, () ->
            poolResource.list(null, "blah", null, false, null)
        );
    }

    @Test
    public void testListNoSuchProduct() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        assertEquals(0, poolResource.list(owner1.getId(), null, "boogity", false,
            null).size());
    }

    @Test
    public void ownerAdminCannotListAnotherOwnersPools() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        List<PoolDTO> pools = poolResource.list(owner1.getId(), null, null, false, null);
        assertEquals(2, pools.size());

        setupPrincipal(owner2, Access.ALL);
        securityInterceptor.enable();

        when(this.principalProvider.get()).thenReturn(setupPrincipal(owner2, Access.ALL));
        assertThrows(NotFoundException.class, () ->
            poolResource.list(owner1.getId(), null, null, false, null)
        );
    }

    @Test
    public void testConsumerCannotListPoolsForAnotherOwnersConsumer() {
        securityInterceptor.enable();
        when(this.principalProvider.get()).thenReturn(
            setupPrincipal(new ConsumerPrincipal(foreignConsumer, owner2)));
        assertThrows(NotFoundException.class, () ->
            poolResource.list(null, passConsumer.getUuid(), null, false, null)
        );
    }

    @Test
    public void consumerCannotListPoolsForAnotherOwner() {
        securityInterceptor.enable();
        when(this.principalProvider.get()).thenReturn(
            setupPrincipal(new ConsumerPrincipal(foreignConsumer, owner2)));
        assertThrows(NotFoundException.class, () ->
            poolResource.list(owner1.getId(), null, null, false, null)
        );
    }

    @Test
    public void consumerCanListOwnersPools() {
        securityInterceptor.enable();
        when(this.principalProvider.get()).thenReturn(
            setupPrincipal(new ConsumerPrincipal(passConsumer, owner1)));

        poolResource.list(owner1.getId(), null, null, false, null);
    }

    @Test
    public void testBadActiveOnDate() {
        when(this.principalProvider.get()).thenReturn(this.adminPrincipal);
        assertThrows(BadRequestException.class, () ->
            poolResource.list(owner1.getId(), null, null, false, "bc")
        );
    }

    @Test
    public void testActiveOnDate() {
        // Need to be a super admin to do this:
        String activeOn = Integer.toString(START_YEAR + 1);

        when(this.principalProvider.get()).thenReturn(setupAdminPrincipal("superadmin"));

        List<PoolDTO> pools = poolResource.list(null, null, null, false, activeOn);
        assertEquals(3, pools.size());

        activeOn = Integer.toString(START_YEAR - 1);
        pools = poolResource.list(owner1.getId(), null, null, false, activeOn);
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

        assertThrows(NotFoundException.class, () ->
            poolResource.getPool(pool1.getId(), passConsumer.getUuid(),
            null)
        );
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
