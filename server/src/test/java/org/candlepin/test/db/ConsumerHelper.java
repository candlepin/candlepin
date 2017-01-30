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

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Owner;

import org.junit.Assert;

public final class ConsumerHelper {

    private ConsumerHelper() {
        throw new UnsupportedOperationException("This class is a utility!");
    }

    public static final String [] ignoredCols = new String [] {Consumer.CREATED_COLUMN,
        Consumer.UPDATED_COLUMN, Consumer.ID_COLUMN};

    public static final String ID_VALUE = "consumer-id-1";
    public static final String UUID_VALUE = "consumer-uuid-1";
    public static final String NAME_VALUE = "consumer-name-1";

    private static  String TYPE_VALUE = "consumer-type-id-1";
    private static String OWNER_ID_VALUE = "owner-uuid-1";

    public static Consumer newConsumer1(Owner o, ConsumerType ct) {
        Consumer c = new Consumer();
        c.setId(ID_VALUE);
        c.setUuid(UUID_VALUE);
        c.setName(NAME_VALUE);
        c.setOwner(o);
        c.setType(ct);
        return c;
    }

    public static void assertConsumer1(Consumer c) {
        Assert.assertEquals(UUID_VALUE, c.getUuid());
        Assert.assertEquals(NAME_VALUE, c.getName());
    }
}
