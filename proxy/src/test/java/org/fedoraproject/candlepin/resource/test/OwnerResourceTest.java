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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.resource.OwnerResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

/**
 * OwnerResourceTest
 */
public class OwnerResourceTest extends DatabaseTestFixture {

    private static final String OWNER_NAME = "Jar Jar Binks";

    private OwnerResource ownerResource;

    @Before
    public void setUp() {
        ownerResource = new OwnerResource(ownerCurator, poolCurator);
    }

    @Test
    public void testCreateOwner() {
        Owner toSubmit = new Owner(OWNER_NAME);

        Owner submitted = ownerResource.createOwner(toSubmit);

        assertNotNull(submitted);
        assertNotNull(ownerCurator.find(submitted.getId()));
        assertTrue(submitted.getEntitlementPools().size() == 0);
    }
    
    @Test    
    public void testSimpleDeleteOwner() {
        Owner owner = new Owner(OWNER_NAME);
        ownerCurator.create(owner);
        assertNotNull(owner.getId());
        Long id = owner.getId();
        ownerResource.deleteOwner(id);
        owner = ownerCurator.find(id);
        assertTrue(owner == null);
    }

}
