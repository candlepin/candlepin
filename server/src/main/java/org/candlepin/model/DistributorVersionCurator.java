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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * DistributorVersionCurator
 */
@Component
public class DistributorVersionCurator
    extends AbstractHibernateCurator<DistributorVersion> {

    public DistributorVersionCurator() {
        super(DistributorVersion.class);
    }

    @SuppressWarnings("unchecked")
    public DistributorVersion findByName(String name) {
        List<DistributorVersion> dvList = currentSession()
            .createCriteria(DistributorVersion.class)
            .add(Restrictions.eq("name", name)).list();
        if (!dvList.isEmpty()) {
            return dvList.get(0);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<DistributorVersion> findByNameSearch(String name) {
        return currentSession()
            .createCriteria(DistributorVersion.class)
            .add(Restrictions.like("name", "%" + name + "%")).list();
    }

    @SuppressWarnings("unchecked")
    public List<DistributorVersion> findByCapability(String capability) {
        List<DistributorVersionCapability> caps = currentSession()
            .createCriteria(DistributorVersionCapability.class)
            .add(Restrictions.eq("name", capability)).list();

        List<DistributorVersion> distVers = new ArrayList<>();

        for (DistributorVersionCapability dvc : caps) {
            distVers.add(dvc.getDistributorVersion());
        }

        return distVers;
    }

    @SuppressWarnings("unchecked")
    public DistributorVersion findById(String id) {
        List<DistributorVersion> dvList = currentSession()
            .createCriteria(DistributorVersion.class)
            .add(Restrictions.eq("id", id)).list();
        if (!dvList.isEmpty()) {
            return dvList.get(0);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<DistributorVersion> findAll() {
        return (List<DistributorVersion>) currentSession()
            .createCriteria(DistributorVersion.class).list();
    }

    @SuppressWarnings("unchecked")
    public Set<DistributorVersionCapability> findCapabilitiesByDistVersion(String distVersion) {
        List<DistributorVersion> dvList = currentSession()
            .createCriteria(DistributorVersion.class)
            .add(Restrictions.eq("name", distVersion)).list();
        if (!dvList.isEmpty()) {
            return ((DistributorVersion) dvList.get(0)).getCapabilities();
        }
        return null;
    }


}
