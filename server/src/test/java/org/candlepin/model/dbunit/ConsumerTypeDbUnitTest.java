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

import static org.candlepin.test.db.ConsumerTypeHelper.CONSUMER_TYPE_ID_VALUE_1;
import static org.candlepin.test.db.ConsumerTypeHelper.assertConsumerType1;
import static org.candlepin.test.db.ConsumerTypeHelper.ignoredCols;
import static org.candlepin.test.db.ConsumerTypeHelper.newConsumerType1;

import org.candlepin.model.ConsumerType;
import org.candlepin.test.DatabaseTestFixture;

import org.dbunit.Assertion;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.operation.DatabaseOperation;
import org.junit.Test;

public class ConsumerTypeDbUnitTest extends DatabaseTestFixture{

    @Test
    public void shouldCreateConsumerType() throws Exception {
        ConsumerType ct = newConsumerType1();
        ct.setId(null);

        consumerTypeCurator.create(ct);

        ITable expected = getDataSet("consumerTypes.xml").getTable(ConsumerType.DB_TABLE);
        ITable actual = dbunitConnection.createTable(ConsumerType.DB_TABLE);
        Assertion.assertEqualsIgnoreCols(expected, actual, ignoredCols);
    }

    @Test
    public void shouldFindConsumerType() throws Exception {
        IDataSet dataSet = getDataSet("consumerTypes.xml");
        DatabaseOperation.INSERT.execute(dbunitConnection, dataSet);

        ConsumerType ct = consumerTypeCurator.find(CONSUMER_TYPE_ID_VALUE_1);

        assertConsumerType1(ct);
    }
}
