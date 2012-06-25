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
package org.candlepin.model.test;

import javax.persistence.RollbackException;
import javax.persistence.PersistenceException;
import org.candlepin.model.Owner;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class OwnerCuratorTest extends DatabaseTestFixture {

    @Test
    public void basicImport() {
        Owner owner = new Owner("testing");
        owner.setId("testing-primary-key");

        this.ownerCurator.replicate(owner);

        Assert.assertEquals("testing",
                this.ownerCurator.find("testing-primary-key").getKey());
    }

    @Test(expected = RollbackException.class)
    public void primaryKeyCollision() {
        Owner owner = new Owner("dude");
        owner = this.ownerCurator.create(owner);

        Owner newOwner = new Owner("someoneElse");
        newOwner.setId(owner.getId());

        this.ownerCurator.replicate(newOwner);
    }

    @Test(expected = PersistenceException.class)
    public void upstreamUuidConstraint() {
        Owner owner1 = new Owner("owner1");
        owner1.setUpstreamUuid("sameuuid");
        Owner owner2 = new Owner("owner2");
        owner2.setUpstreamUuid("sameuuid");

        ownerCurator.create(owner1);
        ownerCurator.create(owner2);
    }
}
