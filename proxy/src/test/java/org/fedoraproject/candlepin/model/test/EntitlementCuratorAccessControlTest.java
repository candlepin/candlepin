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
package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.*;

import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.hibernate.criterion.DetachedCriteria;
import org.junit.Before;
import org.junit.Test;

/**
 * EntitlementCuratorAccessControlTest
 */
public class EntitlementCuratorAccessControlTest extends DatabaseTestFixture {
    private Owner owner;
    private Consumer consumer;

    @Before
    public void setUp() {
        owner = createOwner();
        ownerCurator.create(owner);
        
        consumer = createConsumer(owner);
        consumerCurator.create(consumer);
        
        Pool firstPool = createPoolAndSub(
            owner, "some_product", 1L, dateSource.currentDate(), dateSource.currentDate());
        poolCurator.create(firstPool);
        
        Entitlement firstEntitlement = 
            createEntitlement(owner, consumer, firstPool, null);
        entitlementCurator.create(firstEntitlement);
        
        Pool secondPool = createPoolAndSub(
            owner, "other_product", 1L, dateSource.currentDate(), dateSource.currentDate());
        poolCurator.create(secondPool);
        
        Entitlement secondEntitlement = 
            createEntitlement(owner, consumer, secondPool, null);
        entitlementCurator.create(secondEntitlement);        
    }
    
    @Test
    public void ownerCanGetConsumersEntitlementsUsingListByCriteria() {
        assertEquals(2, entitlementCurator.listAll().size());

        crudInterceptor.enable();
        setupPrincipal(owner, Role.OWNER_ADMIN);
        
        assertEquals(2, entitlementCurator.listByCriteria(
            DetachedCriteria.forClass(Entitlement.class)).size());
    }
    
    @Test
    public void ownerCannotGetOtherOwnersConsumersEntitlementsUsingListByCriteria() {
        assertEquals(2, entitlementCurator.listAll().size());

        Owner evilOwner = ownerCurator.create(new Owner("another-owner"));
        ownerCurator.create(evilOwner);

        crudInterceptor.enable();
        setupPrincipal(evilOwner, Role.OWNER_ADMIN);
        
        assertEquals(0, entitlementCurator.listByCriteria(
            DetachedCriteria.forClass(Entitlement.class)).size());
    }
    
    @Test
    public void consumerCanGetOwnEntitlementsUsingListByCriteria() {
        assertEquals(2, entitlementCurator.listAll().size());
        
        setupPrincipal(new ConsumerPrincipal(consumer));
        crudInterceptor.enable();
        
        assertEquals(2, entitlementCurator.listByCriteria(
            DetachedCriteria.forClass(Entitlement.class)).size());
    }
    
    @Test
    public void consumerCannotGetOtherConsumersEntitlementsUsingListByCriteria() {
        assertEquals(2, entitlementCurator.listAll().size());
        
        Consumer evilConsumer = createConsumer(owner);
        setupPrincipal(new ConsumerPrincipal(evilConsumer));
        crudInterceptor.enable();
        
        assertEquals(0, entitlementCurator.listByCriteria(
            DetachedCriteria.forClass(Entitlement.class)).size());
    }
}
