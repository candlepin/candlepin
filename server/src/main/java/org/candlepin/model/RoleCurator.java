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
package org.candlepin.model;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * RoleCurator
 */
@Component
public class RoleCurator extends AbstractHibernateCurator<Role> {

    @Autowired
    private CandlepinQueryFactory cpQueryFactory;

    public RoleCurator() {
        super(Role.class);
    }

    public CandlepinQuery<Role> listForOwner(Owner o) {
        DetachedCriteria criteria = DetachedCriteria.forClass(Role.class)
            .createCriteria("permissions")
            .add(Restrictions.eq("owner", o));

        return this.cpQueryFactory.<Role>buildQuery(this.currentSession(), criteria);
    }

    /**
     * @param name role's unique name to lookup.
     * @return the role whose name matches the one given.
     */
    public Role getByName(String name) {
        return (Role) currentSession().createCriteria(Role.class)
            .add(Restrictions.eq("name", name))
            .uniqueResult();
    }

}
