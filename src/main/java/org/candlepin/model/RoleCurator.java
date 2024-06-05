/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import javax.inject.Singleton;
import javax.persistence.NoResultException;

/**
 * RoleCurator
 */
@Singleton
public class RoleCurator extends AbstractHibernateCurator<Role> {

    public RoleCurator() {
        super(Role.class);
    }

    public List<Role> listForOwner(Owner owner) {
        String jpql = "SELECT r FROM PermissionBlueprint pb " +
            "JOIN Role r ON r.id = pb.role.id " +
            "WHERE pb.owner.id = :owner_id";

        return this.getEntityManager()
            .createQuery(jpql, Role.class)
            .setParameter("owner_id", owner.getId())
            .getResultList();
    }

    /**
     * @param name
     *     role's unique name to lookup.
     * @return the role whose name matches the one given.
     */
    public Role getByName(String name) {
        String jpql = "SELECT r FROM Role r WHERE r.name = :name";

        try {
            return getEntityManager().createQuery(jpql, Role.class)
                .setParameter("name", name)
                .getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

}
