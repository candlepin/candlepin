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

import org.fedoraproject.candlepin.auth.Access;
import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.resource.OwnerResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

/**
 * OwnerAccessTest
 */
public class OwnerAccessControlTest extends DatabaseTestFixture {

    private OwnerResource resource;
    private Owner owner;

    @Before
    @Override
    public void init() {
        super.init();

        this.resource = this.injector.getInstance(OwnerResource.class);
        this.owner = createOwner();
    }

    @Test
    public void superAdminCanCreateAnOwner() {
        setupAdminPrincipal("dude");
        securityInterceptor.enable();

        resource.createOwner(new Owner("Test Owner"));
        assertNotNull(ownerCurator.find(owner.getId()));
    }

    @Test(expected = ForbiddenException.class)
    public void ownerAdminCannotCreateAnOwner() {
        setupPrincipal(owner, Access.ALL);
        securityInterceptor.enable();

        resource.createOwner(new Owner("Test Owner"));
    }

    @Test(expected = ForbiddenException.class)
    public void consumerCannotCreateAnOwner() {
        Consumer consumer = createConsumer(owner);
        setupPrincipal(new ConsumerPrincipal(consumer));
        securityInterceptor.enable();

        resource.createOwner(new Owner("Test Owner"));
    }
}
