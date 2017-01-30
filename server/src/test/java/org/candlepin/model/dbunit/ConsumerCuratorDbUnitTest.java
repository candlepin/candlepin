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

import static org.candlepin.test.db.ConsumerHelper.assertConsumer1;
import static org.candlepin.test.db.ConsumerHelper.newConsumer1;
import static org.candlepin.test.db.ConsumerTypeHelper.CONSUMER_TYPE_ID_VALUE_1;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Owner;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.db.ConsumerHelper;
import org.candlepin.test.db.ConsumerTypeHelper;
import org.candlepin.test.db.OwnerHelper;

import org.dbunit.Assertion;
import org.dbunit.dataset.CompositeDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.Ignore;
import org.junit.Test;

public class ConsumerCuratorDbUnitTest extends DatabaseTestFixture{


    @Test
    public void shouldFindConsumer() throws Exception {
        IDataSet dataSet = getDataSet("consumers.xml");
        DatabaseOperation.INSERT.execute(dbunitConnection, dataSet);

        Consumer consumer = consumerCurator.find(ConsumerHelper.ID_VALUE);

        assertConsumer1(consumer);
    }

    @Test
    public void shouldCreateConsumer() throws Exception {
        IDataSet dataSet = getDataSet("consumers-setup.xml");
        DatabaseOperation.INSERT.execute(dbunitConnection, dataSet);
        createConsumerAndAssert();
    }

    private void createConsumerAndAssert()
        throws Exception {
        Owner o = entityManager().find(Owner.class, OwnerHelper.OWNER_ID_VALUE_1);
        ConsumerType ct = entityManager().find(ConsumerType.class, CONSUMER_TYPE_ID_VALUE_1);
        Consumer c = newConsumer1(o, ct);
        c.setId(null);

        consumerCurator.create(c);

        IDataSet actual = dbunitConnection.createDataSet();
        IDataSet expected = getDataSet("consumers.xml");
        Assertion.assertEqualsIgnoreCols(expected.getTable(Owner.DB_TABLE),
            actual.getTable(Owner.DB_TABLE), OwnerHelper.ignoredCols);
        Assertion.assertEqualsIgnoreCols(expected.getTable(ConsumerType.DB_TABLE),
            actual.getTable(ConsumerType.DB_TABLE), ConsumerTypeHelper.ignoredCols);
        Assertion.assertEqualsIgnoreCols(expected.getTable(Consumer.DB_TABLE),
            actual.getTable(Consumer.DB_TABLE), ConsumerHelper.ignoredCols);
    }

    @Ignore @Test
    public void shouldCreateConsumerWithCompositeDataset() throws Exception {
        IDataSet dataSet = new CompositeDataSet(getDataSet("owners.xml"),getDataSet("consumerTypes.xml"));
        DatabaseOperation.INSERT.execute(dbunitConnection, dataSet);

        createConsumerAndAssert();
    }
}
