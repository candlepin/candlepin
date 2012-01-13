/**
 * Copyright (c) 2009 Red Hat, Inc.
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

import java.util.List;

import org.hibernate.criterion.Restrictions;

/**
 * EnvironmentCurator
 */
public class EnvironmentCurator extends AbstractHibernateCurator<Environment> {

    protected EnvironmentCurator() {
        super(Environment.class);
    }

    public List<Environment> listForOwner(Owner o) {
        return this.currentSession().createCriteria(Environment.class)
            .add(Restrictions.eq("owner", o)).list();
    }

    public List<Environment> listForOwnerByName(Owner o, String envName) {
        return this.currentSession().createCriteria(Environment.class)
            .add(Restrictions.eq("owner", o)).add(Restrictions.eq("name", envName)).list();
    }

    public void evict(Environment e) {
        this.currentSession().evict(e);
    }

}
