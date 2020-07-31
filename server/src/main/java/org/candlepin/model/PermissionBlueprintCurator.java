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
 * PermissionBlueprintCurator
 */
@Component
public class PermissionBlueprintCurator extends AbstractHibernateCurator<PermissionBlueprint> {

    @Autowired
    private CandlepinQueryFactory cpQueryFactory;

    public PermissionBlueprintCurator() {
        super(PermissionBlueprint.class);
    }

    public CandlepinQuery<PermissionBlueprint> findByOwner(Owner owner) {
        DetachedCriteria criteria = DetachedCriteria.forClass(PermissionBlueprint.class)
            .add(Restrictions.eq("owner", owner));

        return this.cpQueryFactory.<PermissionBlueprint>buildQuery(this.currentSession(), criteria);
    }
}
