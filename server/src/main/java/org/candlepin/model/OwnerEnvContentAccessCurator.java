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

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Query;



/**
 * The OwnerContentCurator provides functionality for managing the mapping between owners and
 * content.
 */
public class OwnerEnvContentAccessCurator extends AbstractHibernateCurator<OwnerEnvContentAccess> {
    private static Logger log = LoggerFactory.getLogger(OwnerEnvContentAccessCurator.class);

    /**
     * Default constructor
     */
    public OwnerEnvContentAccessCurator() {
        super(OwnerEnvContentAccess.class);
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public OwnerEnvContentAccess getContentAccess(String ownerId, String environmentId) {
        OwnerEnvContentAccess result = null;
        String hql = "";
        List<OwnerEnvContentAccess> resultList = null;
        if (environmentId != null) {
            hql = "SELECT oeca" +
                "    FROM OwnerEnvContentAccess oeca" +
                "     JOIN oeca.owner o" +
                "     JOIN oeca.environment e" +
                "    WHERE" +
                "       o.id=:ownerId" +
                "    AND" +
                "       e.id=:enviromentId";
            Query query = this.getEntityManager().createQuery(hql);

            resultList = (List<OwnerEnvContentAccess>) query
                .setParameter("ownerId", ownerId)
                .setParameter("enviromentId",  environmentId)
                .getResultList();

        }
        else {
            hql = "SELECT oeca" +
                "    FROM OwnerEnvContentAccess oeca" +
                "     JOIN oeca.owner o" +
                "    WHERE" +
                "       o.id=:ownerId" +
                "    AND" +
                "       oeca.environment is null";
            Query query = this.getEntityManager().createQuery(hql);

            resultList = (List<OwnerEnvContentAccess>) query
                .setParameter("ownerId", ownerId)
                .getResultList();
            if (resultList.isEmpty()) {
                result = null;
            }
            else {
                result = resultList.get(0);
            }
        }
        if (resultList.isEmpty()) {
            result = null;
        }
        else {
            result = resultList.get(0);
        }
        return result;
    }

    public void removeAllForOwner(String ownerId) {
        this.currentSession().createQuery(
            "delete from OwnerEnvContentAccess where owner_id = :ownerId")
                .setParameter("ownerId", ownerId)
                .executeUpdate();
    }

    public void saveOrUpdate(OwnerEnvContentAccess ownerEnvContentAccess) {
        Set<OwnerEnvContentAccess> entities = new HashSet<OwnerEnvContentAccess>();
        entities.add(ownerEnvContentAccess);
        saveOrUpdateAll(entities, false, false);
    }
}
