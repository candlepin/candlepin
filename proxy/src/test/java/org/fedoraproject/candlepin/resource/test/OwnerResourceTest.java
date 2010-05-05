/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.resource.test;

import static org.junit.Assert.*;

import java.util.List;

import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.resource.OwnerResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

/**
 * OwnerResourceTest
 */
public class OwnerResourceTest extends DatabaseTestFixture {

    private static final String OWNER_NAME = "Jar Jar Binks";

    private OwnerResource ownerResource;
    private Owner owner;

    @Before
    public void setUp() {
        ownerResource = injector.getInstance(OwnerResource.class);
        owner = new Owner(OWNER_NAME);
        ownerCurator.create(owner);
    }

    @Test
    public void testCreateOwner() {
        Owner submitted = ownerResource.createOwner(owner);

        assertNotNull(submitted);
        assertNotNull(ownerCurator.find(submitted.getId()));
        assertTrue(submitted.getEntitlementPools().size() == 0);
    }
    
    @Test    
    public void testSimpleDeleteOwner() {
        assertNotNull(owner.getId());
        Long id = owner.getId();
        ownerResource.deleteOwner(id);
        owner = ownerCurator.find(id);
        assertTrue(owner == null);
    }

    @Test(expected = ForbiddenException.class)
    public void testConsumerRoleCannotGetOwner() {
        Consumer c = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c.getType());
        consumerCurator.create(c);
        setupPrincipal(new ConsumerPrincipal(c));

        ownerResource.getOwner(owner.getId());
    }

    @Test
    public void testOwnerAdminCanGetPools() {
        setupPrincipal(owner, Role.OWNER_ADMIN);

        Product p = TestUtil.createProduct();
        productCurator.create(p);
        Pool pool1 = TestUtil.createEntitlementPool(owner, p);
        Pool pool2 = TestUtil.createEntitlementPool(owner, p);
        poolCurator.create(pool1);
        poolCurator.create(pool2);

        List<Pool> pools = ownerResource.ownerEntitlementPools(owner.getId());
        assertEquals(2, pools.size());
    }

    // FIXME
//    @Test
//    public void testOwnerAdminCannotAccessAnotherOwnersPools() {
//        Owner evilOwner = new Owner("evilowner");
//        ownerCurator.create(evilOwner);
//        setupPrincipal(evilOwner, Role.OWNER_ADMIN);
//
//        Product p = TestUtil.createProduct();
//        productCurator.create(p);
//        Pool pool1 = TestUtil.createEntitlementPool(owner, p);
//        Pool pool2 = TestUtil.createEntitlementPool(owner, p);
//        poolCurator.create(pool1);
//        poolCurator.create(pool2);
//
//        // Filtering should just cause this to return no results:
//        List<Pool> pools = ownerResource.ownerEntitlementPools(owner.getId());
//        assertEquals(0, pools.size());
//    }

    @Test(expected = ForbiddenException.class)
    public void testOwnerAdminCannotListAllOwners() {
        setupPrincipal(owner, Role.OWNER_ADMIN);
        ownerResource.list();
    }

    @Test(expected = ForbiddenException.class)
    public void testOwnerAdminCannotDelete() {
        setupPrincipal(owner, Role.OWNER_ADMIN);
        ownerResource.deleteOwner(owner.getId());
    }

}
