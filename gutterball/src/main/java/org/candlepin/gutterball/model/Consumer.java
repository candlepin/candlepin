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
package org.candlepin.gutterball.model;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

import java.util.Date;

/**
 * Consumer master record to store created/deleted/owner info
 * to narrow our search space
 *
 * We should only add info here that cannot be modified
 */
public class Consumer extends BasicDBObject {

    private static final String UUID_FIELD = "uuid";
    private static final String CREATED_FIELD = "created";
    private static final String OWNER_FIELD = "owner";
    private static final String DELETED_FIELD = "deleted";

    /**
     * Required by Mongo Java Driver.
     */
    public Consumer() {
        super();
    }

    public Consumer(String uuid, Date created, DBObject owner) {
        put(UUID_FIELD, uuid);
        put(CREATED_FIELD, created);
        put(OWNER_FIELD, owner);
        put(DELETED_FIELD, null);
    }

    public String getUUID() {
        return getString(UUID_FIELD);
    }

    public Date getDeleted() {
        return getDate(DELETED_FIELD);
    }

}
