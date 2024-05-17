/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import java.util.ArrayList;
import java.util.List;

import javax.inject.Singleton;
import javax.persistence.NoResultException;

/**
 * DistributorVersionCurator
 */
@Singleton
public class DistributorVersionCurator
    extends AbstractHibernateCurator<DistributorVersion> {

    public DistributorVersionCurator() {
        super(DistributorVersion.class);
    }

    /**
     * Retrieves a {@link DistributorVersion} based on the provided name.
     *
     * @param name
     *  the name of the {@link DistributorVersion} to retrieve
     *
     * @return the distributor version based on the provided name, or null if one does not exist
     */
    public DistributorVersion findByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        String query = "SELECT d FROM DistributorVersion d WHERE d.name = :name";

        try {
            return this.entityManager.get()
                .createQuery(query, DistributorVersion.class)
                .setParameter("name", name)
                .getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Retrieves a list of {@link DistributorVersion}s that has a name that equals or has a substring of
     * the provided name.
     *
     * @param name
     *  the name of the {@link DistributorVersion}s to retrieve
     *
     * @return a list of distributor versions that has a name equal to or has a substring of the provided
     *  name
     */
    public List<DistributorVersion> findByNameSearch(String name) {
        if (name == null) {
            return new ArrayList<>();
        }

        String query = "SELECT d FROM DistributorVersion d WHERE d.name LIKE :name";

        return this.entityManager.get()
            .createQuery(query, DistributorVersion.class)
            .setParameter("name", "%" + name + "%")
            .getResultList();
    }

    /**
     * Retrieves a list of {@link DistributorVersion} that has a {@link DistributorVersionCapability} with a
     * name that equals the provided capability name.
     *
     * @param capability
     *  the name of the {@link DistributorVersionCapability} to retrieve {@link DistributorVersion}s for
     *
     * @return a list of distributor versions that have a capability that matches the provided name
     */
    public List<DistributorVersion> findByCapability(String capability) {
        if (capability == null || capability.isBlank()) {
            return new ArrayList<>();
        }

        String query = "SELECT d FROM DistributorVersionCapability d WHERE d.name = :name";

        List<DistributorVersionCapability> distributorVersionCapabilities = this.entityManager.get()
            .createQuery(query, DistributorVersionCapability.class)
            .setParameter("name", capability)
            .getResultList();

        List<DistributorVersion> result = new ArrayList<>();
        for (DistributorVersionCapability dvc : distributorVersionCapabilities) {
            result.add(dvc.getDistributorVersion());
        }

        return result;
    }
}
