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

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.hibernate.criterion.Restrictions;


/**
 * Subscription manager.
 */
public class CdnCurator
    extends AbstractHibernateCurator<Cdn> {

    @Inject
    public CdnCurator() {
        super(Cdn.class);
    }

    /**
     * Return CDN for the given label.
     * @param label CDN label
     * @return CDN whose label matches the given value.
     */
    public Cdn lookupByLabel(String label) {
        return (Cdn) currentSession()
            .createCriteria(Cdn.class)
            .add(Restrictions.eq("label", label)).uniqueResult();
    }

    /**
     * Updates the specified {@link Cdn}.
     *
     * @param cdn the {@link Cdn} to update.
     */
    public void update(Cdn cdn) {
        save(cdn);
    }

    @Transactional
    public void delete(Cdn toDelete) {
        getEntityManager().remove(toDelete);
    }
}
