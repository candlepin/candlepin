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
package org.candlepin.test.db;

import org.candlepin.model.ConsumerType;

import org.junit.Assert;

public class ConsumerTypeHelper {

    private ConsumerTypeHelper() {
        throw new UnsupportedOperationException("This class is a utility!");
    }

    public static String [] ignoredCols = new String [] {ConsumerType.CREATED_COLUMN,
        ConsumerType.UPDATED_COLUMN, ConsumerType.ID_COLUMN};

    public static String CONSUMER_TYPE_ID_VALUE_1 = "consumer-type-id-1";
    public static String CONSUMER_TYPE_LABEL_VALUE_1 = "label-1";
    public static boolean CONSUMER_TYPE_MANIFEST_1;

    public static ConsumerType newConsumerType1() {
        ConsumerType ct = new ConsumerType();
        ct.setId(CONSUMER_TYPE_ID_VALUE_1);
        ct.setLabel(CONSUMER_TYPE_LABEL_VALUE_1);
        ct.setManifest(CONSUMER_TYPE_MANIFEST_1);
        return ct;
    }

    public static void assertConsumerType1(ConsumerType ct) {
        Assert.assertEquals(CONSUMER_TYPE_LABEL_VALUE_1, ct.getLabel());
        Assert.assertEquals(CONSUMER_TYPE_MANIFEST_1, ct.isManifest());
    }
}
