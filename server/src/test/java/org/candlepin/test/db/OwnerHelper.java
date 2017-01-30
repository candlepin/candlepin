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

import org.candlepin.model.Owner;

import org.junit.Assert;

/**
 * This helper serve in DB layer testing to expose values from dataset files
 * to improve maintenance
 */
public final class OwnerHelper {

    private OwnerHelper() {
        throw new UnsupportedOperationException("This class is a utility!");
    }

    public final static String [] ignoredCols = new String[] {Owner.ID_COLUMN,
        Owner.CREATED_COLUMN, Owner.UPDATED_COLUMN};

    public final static String OWNER_ID_VALUE_1 = "owner-uuid-1";
    public final static String OWNER_DISPLAY_NAME_VALUE_1 = "display-name-1";
    public final static String OWNER_KEY_VALUE_1 = "key-1";

    public static Owner newOwner1() {
         Owner o = new Owner(OWNER_KEY_VALUE_1, OWNER_DISPLAY_NAME_VALUE_1);
         o.setId(OWNER_ID_VALUE_1);
         return o;
    }

    public static void assertOwner1(Owner owner) {
        Assert.assertNotNull(owner);
        Assert.assertEquals(OWNER_KEY_VALUE_1, owner.getKey());
        Assert.assertEquals(OWNER_DISPLAY_NAME_VALUE_1, owner.getDisplayName());
    }

    // ------------------------------------------------------------------

    public final static String OWNER_ID_VALUE_2 = "owner-uuid-2";
    public final static String OWNER_DISPLAY_NAME_VALUE_2 = "display-name-2";
    public final static String OWNER_KEY_VALUE_2 = "key-2";

    public static Owner newOwner2() {
        Owner o = new Owner(OWNER_KEY_VALUE_2, OWNER_DISPLAY_NAME_VALUE_2);
        o.setId(OWNER_ID_VALUE_2);
        return o;
   }

   public static void assertOwner2(Owner owner) {
       Assert.assertNotNull(owner);
       Assert.assertEquals(OWNER_KEY_VALUE_2, owner.getKey());
       Assert.assertEquals(OWNER_DISPLAY_NAME_VALUE_2, owner.getDisplayName());
   }

}
