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
package org.candlepin.auth.permissions;

import org.candlepin.auth.Access;
import org.hibernate.criterion.Criterion;

/**
 *
 */
public interface Permission {

    boolean canAccess(Object target, Access access);

    /**
     * Permissions have the ability to add restrictions to a hibernate query. This allows
     * us to do things like limit the results from a database query based on the principal,
     * while still allowing the database to do the filtering and use pagination.
     * @param entity Type of object being queried.
     * @return Restrictions to be added to the criteria. This is handled
     * in the abstract hibernate curator.
     */
    Criterion getCriteriaRestrictions(Class entityClass);
}
