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

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.TypedQuery;

/**
 * Subscription manager.
 */
@Singleton
public class CdnCurator extends AbstractHibernateCurator<Cdn> {

    @Inject
    public CdnCurator() {
        super(Cdn.class);
    }

    /**
     * Return CDN for the given label.
     * @param label CDN label
     * @return CDN whose label matches the given value.
     */
    public Cdn getByLabel(String label) {
        // TODO: This is dangerous. We're expecting a unique result, but there is no guarantee the
        // label will be unique. The DB schema should be updated to prevent this on the input side.

        String jpql = "SELECT c FROM Cdn c WHERE c.label = :label";
        TypedQuery<Cdn> query = getEntityManager().createQuery(jpql, Cdn.class)
            .setParameter("label", label);

        try {
            return query.getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
        catch (NonUniqueResultException e) {
            throw new IllegalStateException("Multiple CDN instances found with the same label: " + label, e);
        }
    }

}
