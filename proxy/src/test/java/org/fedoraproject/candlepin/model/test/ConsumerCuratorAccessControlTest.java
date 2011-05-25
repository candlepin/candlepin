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

import java.util.List;

import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.auth.Access;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Test;

/**
 * ConsumerCuratorAccessControlTest
 */
public class ConsumerCuratorAccessControlTest extends DatabaseTestFixture {
    
    @Test
    public void superAdminCanCreateAConsumer() {
        setupPrincipal(null, Access.ALL);
        securityInterceptor.enable();
        crudInterceptor.enable();
        
        Consumer consumer = createConsumer(createOwner());
        assertNotNull(consumerCurator.find(consumer.getId()));
    }
    
    @Test
    public void ownerAdminCanCreateAConsumer() {
        Owner owner = createOwner();
        
        setupPrincipal(owner, Access.ALL);
        securityInterceptor.enable();
        crudInterceptor.enable();
        
        Consumer consumer = createConsumer(owner);
        assertNotNull(consumerCurator.find(consumer.getId()));
    }
    
    @Test(expected = ForbiddenException.class)
    public void consumerCannnotCreateAConsumer() {
        Owner owner = createOwner();
        
        setupPrincipal(owner, Access.ALL);
        securityInterceptor.enable();
        crudInterceptor.enable();
        
        createConsumer(owner);
    }
    
    @Test
    public void consumerCanOnlySeeItself() {
        Owner owner = createOwner();
        Consumer first = createConsumer(owner);
        createConsumer(owner); // make a 2nd one
        
        setupPrincipal(new ConsumerPrincipal(first));
        crudInterceptor.enable();
        
        List<Consumer> all = consumerCurator.listAll();
        assertEquals(1, all.size());
        assertEquals(first, all.get(0));
    }
    
    @Test
    public void ownerCanOnlySeeOwnConsumers() {
        Owner owner = createOwner();
        createConsumer(owner); // make a 1st
        createConsumer(owner); // make a 2nd
        
        Owner anotherOwner = createOwner();
        createConsumer(anotherOwner); // make a 1st
        createConsumer(anotherOwner); // make a 2nd
        
        setupPrincipal(owner, Access.ALL);
        crudInterceptor.enable();
        
        List<Consumer> all = consumerCurator.listAll();
        assertEquals(2, all.size());
    }
    
    @Test
    public void consumerCanFindItself() {
        Owner owner = createOwner();
        Consumer first = createConsumer(owner);

        setupPrincipal(new ConsumerPrincipal(first));
        crudInterceptor.enable();
        
        assertEquals(first.getUuid(), consumerCurator.find(first.getId()).getUuid());
    }
    
    @Test(expected = ForbiddenException.class)
    public void consumerCannotFindOtherConsumer() {
        Owner owner = createOwner();
        Consumer first = createConsumer(owner);
        Consumer second = createConsumer(owner);

        setupPrincipal(new ConsumerPrincipal(second));
        crudInterceptor.enable();
        
        consumerCurator.find(first.getId());
    }
}
