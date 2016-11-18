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
package org.candlepin.hibernate;

import org.hibernate.dialect.Oracle10gDialect;

/**
 * We make sure that our locking queries work on all supported database servers
 * and thus don't need follow on locking. The feature caused us trouble. Queries
 * that locked on other DBs were was not locking on Oracle.
 *
 * This dialect might cause some SQL syntax errors on Oracle. Some of the cases
 * are covered here:
 *
 * - https://www.mail-archive.com/hibernate-dev@lists.jboss.org/msg14520.html
 * - https://docs.oracle.com/database/121/SQLRF/statements_10002.htm#sthref7465
 *
 * I think its better to get SQL syntax error than silently fail to acquire
 * a lock.
 *
 * @author fnguyen
 *
 */
public class Oracle10gDialectFollowOnLockingOff extends Oracle10gDialect {

    @Override
    public boolean useFollowOnLocking() {
        return false;
    }
}
