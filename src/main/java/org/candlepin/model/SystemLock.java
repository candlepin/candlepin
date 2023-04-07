/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;



/**
 * The SystemLock entity is used to simplify queries surrounding the system lock.
 */
@Entity
@Table(name = SystemLock.DB_TABLE)
public class SystemLock {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_system_locks";

    @Id
    private String id;

    public SystemLock() {

    }

    public SystemLock setId(String id) {
        this.id = id;
        return this;
    }

    public String getId() {
        return this.id;
    }

}
