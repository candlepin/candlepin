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
package org.fedoraproject.candlepin.model;

import java.util.LinkedList;
import java.util.List;

import org.hibernate.criterion.Restrictions;

public class OwnerCurator extends AbstractHibernateCurator<Owner> {
    
    protected OwnerCurator() {
        super(Owner.class);
    }

    public Owner lookupByName(String name) {
        return (Owner) currentSession().createCriteria(Owner.class)
        .add(Restrictions.like("name", name))
        .uniqueResult();
    }

    @SuppressWarnings("unchecked")
    public List<Owner> listAll() {
        List<Owner> results = (List<Owner>) currentSession()
            .createCriteria(Owner.class).list();
        if (results == null) {
            return new LinkedList<Owner>();
        }
        else {
            return results;
        }
    }
}
