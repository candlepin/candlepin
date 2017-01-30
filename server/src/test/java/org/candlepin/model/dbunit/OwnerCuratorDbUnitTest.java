/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.model.dbunit;

import static org.candlepin.test.db.OwnerHelper.OWNER_ID_VALUE_1;
import static org.candlepin.test.db.OwnerHelper.assertOwner1;
import static org.candlepin.test.db.OwnerHelper.newOwner1;
import static org.candlepin.test.db.OwnerHelper.newOwner2;
import static org.junit.Assert.assertNotNull;

import org.candlepin.model.Owner;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.db.OwnerHelper;

import org.dbunit.Assertion;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.operation.DatabaseOperation;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;

import javax.persistence.RollbackException;

public class OwnerCuratorDbUnitTest extends DatabaseTestFixture {


    @Test
    public void testFindOwnerById() throws Exception {
        IDataSet setupDataSet = getDataSet("owners.xml");
        DatabaseOperation.INSERT.execute(dbunitConnection, setupDataSet);

        Owner o = ownerCurator.find(OwnerHelper.OWNER_ID_VALUE_1);

        assertOwner1(o);
    }

    @Test
    public void testReplicateOwner() throws Exception {
        Owner o = newOwner1();

        o = ownerCurator.replicate(o);

        ITable expectedOwners = getDataSet("owners.xml").getTable(Owner.DB_TABLE);
        ITable actualOwners = dbunitConnection.createTable(Owner.DB_TABLE);
        Assertion.assertEquals(expectedOwners, actualOwners);
    }

    @Test
    public void shouldCreateOwner() throws Exception {
        ITable expectedOwners = getDataSet("owners.xml").getTable(Owner.DB_TABLE);
        Owner o = newOwner1();
        o.setId(null);    //otherwise create will fail
        String oldId = o.getId();
        Date created = o.getCreated();
        Date updated = o.getUpdated();

        o = ownerCurator.create(o);

        assertNotNull(o);
        Assert.assertNotEquals(oldId, o.getId());
        Assert.assertNotEquals(created, o.getCreated());
        Assert.assertNotEquals(updated, o.getUpdated());

        ITable actualOwners = dbunitConnection.createTable(Owner.DB_TABLE);
        Assertion.assertEqualsIgnoreCols(expectedOwners, actualOwners, OwnerHelper.ignoredCols);
    }

    @Test(expected = RollbackException.class)
    public void primaryKeyCollision() throws Exception {
        IDataSet setupDataSet = getDataSet("owners.xml");
        DatabaseOperation.INSERT.execute(dbunitConnection, setupDataSet);

        Owner ownerWithSameId = new Owner("otherOwner");
        ownerWithSameId.setId(OWNER_ID_VALUE_1);

        this.ownerCurator.replicate(ownerWithSameId);
    }

    @Test
    public void shouldCreateMoreOwners() throws Exception {
        Owner o1 = newOwner1();
        o1.setId(null);
        Owner o2 = newOwner2();
        o2.setId(null);

        ownerCurator.create(o1);
        ownerCurator.create(o2);

        ITable expectedOwners = getDataSet("owners2.xml").getTable(Owner.DB_TABLE);
        ITable actualOwners = dbunitConnection.createTable(Owner.DB_TABLE);
        Assertion.assertEqualsIgnoreCols(expectedOwners, actualOwners, OwnerHelper.ignoredCols);
    }

    //TODO more tests here

}
