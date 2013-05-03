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

import org.hibernate.criterion.Restrictions;

/**
 * ArchCurator
 */
public class ArchCurator extends AbstractHibernateCurator<Arch> {

    protected ArchCurator() {
        super(Arch.class);
    }

    /**
     * lookup the Arch by its label.
     *
     * @param label
     *            type to lookup
     * @return Arch whose label matches the given label.
     */
    public Arch lookupByLabel(String label) {
        return (Arch) currentSession().createCriteria(
            Arch.class).add(Restrictions.eq("label", label))
            .uniqueResult();
    }
}
