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
import org.candlepin.auth.SubResource;
import org.candlepin.model.Owner;
import org.hibernate.criterion.Criterion;

/**
 *
 */
public interface Permission {

    boolean canAccess(Object target, SubResource subResource, Access access);

    /**
     * Permissions have the ability to add restrictions to a hibernate queries which use
     * {@link AbstractHibernateCurator#createSecureCriteria createSecureCriteria}.
     *
     * This allows us to do things like limit the results from a database query based
     * on the principal, while still allowing the database to do the filtering and
     * use pagination.
     *
     * While you can just return null here in many cases, it is often a good idea to
     * explicitly include the objects you know you will be accessing with this permission.
     * The results of this method are or'd together for all permissions on the principal,
     * which could cause something to be hidden from you because another permission
     * filtered it out, but you specified nothing.
     *
     * @param entityClass Type of object being queried.
     * @return The modified Criteria query to be run.
     */
    Criterion getCriteriaRestrictions(Class entityClass);

    /**
     * @return an owner if this permission is specific to one, otherwise null
     */
    Owner getOwner();
}
