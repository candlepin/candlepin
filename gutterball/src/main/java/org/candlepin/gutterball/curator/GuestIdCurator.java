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
package org.candlepin.gutterball.curator;

import com.google.inject.Inject;
import com.mongodb.DB;
import com.mongodb.DBObject;

/**
 * A curator that manages DB operations on the 'guestids' collection.
 */
public class GuestIdCurator extends MongoDBCurator<DBObject> {
    public static final String COLLECTION = "guestids";

    @Inject
    public GuestIdCurator(DB database) {
        super(DBObject.class, database);
    }

    @Override
    public String getCollectionName() {
        return COLLECTION;
    }
}
