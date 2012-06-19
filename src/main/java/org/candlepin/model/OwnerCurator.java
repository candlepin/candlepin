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

import org.hibernate.criterion.Restrictions;

import com.google.inject.persist.Transactional;
import org.hibernate.ReplicationMode;

/**
 * OwnerCurator
 */
public class OwnerCurator extends AbstractHibernateCurator<Owner> {

    protected OwnerCurator() {
        super(Owner.class);
    }

    @Transactional
    public Owner replicate(Owner owner) {
        this.currentSession().replicate(owner, ReplicationMode.EXCEPTION);

        return owner;
    }

    @Transactional
    @Override
    public Owner create(Owner entity) {
        return super.create(entity);
    }

    /**
     * @param key owner's unique key to lookup.
     * @return the owner whose key matches the one given.
     */
    @Transactional
    public Owner lookupByKey(String key) {
        return (Owner) currentSession().createCriteria(Owner.class)
            .add(Restrictions.eq("key", key))
            .uniqueResult();
    }

    public Owner lookupWithUpstreamUuid(String upstreamUuid) {
        return (Owner) currentSession().createCriteria(Owner.class)
            .add(Restrictions.eq("upstreamUuid", upstreamUuid))
            .uniqueResult();
    }
}
