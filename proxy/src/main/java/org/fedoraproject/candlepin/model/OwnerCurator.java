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

/**
 * OwnerCurator
 */
public class OwnerCurator extends AbstractHibernateCurator<Owner> {
    
    protected OwnerCurator() {
        super(Owner.class);
    }

    /**
     * @param name owner's name to lookup.
     * @return the owner whose name matches the one given.
     */
    public Owner lookupByKey(String key) {
        return (Owner) currentSession().createCriteria(Owner.class)
        .add(Restrictions.eq("key", key))
        .uniqueResult();
    }

    /**
     * @return list of known owners.
     */
    @SuppressWarnings("unchecked")
    public List<Owner> listAll() {
        List<Owner> results = currentSession().createCriteria(Owner.class).list();
        if (results == null) {
            return new LinkedList<Owner>();
        }
        else {
            return results;
        }
    }
}
