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

import com.google.common.collect.Iterables;

import org.hibernate.criterion.Restrictions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Singleton;
import javax.persistence.Query;


/**
 * EnvironmentContentCurator
 */
@Singleton
public class EnvironmentContentCurator extends AbstractHibernateCurator<EnvironmentContent> {
    public EnvironmentContentCurator() {
        super(EnvironmentContent.class);
    }

    public EnvironmentContent getByEnvironmentAndContent(Environment e, String contentId) {
        return (EnvironmentContent) this.currentSession().createCriteria(EnvironmentContent.class)
            .createAlias("content", "content")
            .add(Restrictions.eq("environment", e))
            .add(Restrictions.eq("content.id", contentId))
            .uniqueResult();
    }

    public EnvironmentContent getByEnvironmentAndContent(Environment e, Content content) {
        return (EnvironmentContent) this.currentSession().createCriteria(EnvironmentContent.class)
            .add(Restrictions.eq("environment", e))
            .add(Restrictions.eq("content", content))
            .uniqueResult();
    }

    public List<EnvironmentContent> getByContent(Owner owner, String contentId) {
        return this.currentSession().createCriteria(EnvironmentContent.class)
            .createAlias("environment", "environment")
            .createAlias("content", "content")
            .add(Restrictions.eq("environment.owner", owner))
            .add(Restrictions.eq("content.id", contentId))
            .list();
    }

    /**
     * Returns the map of environment ID & its respective content UUIDs
     *
     * @params environmentIds
     *  List of environment IDs
     *
     * @return
     *  Map of environmentIds & respective contentUUIDs
     */
    public Map<String, List<String>> getEnvironmentContentUUIDs(Iterable<String> environmentIds) {
        Map<String, List<String>> contentUUIDMap = new HashMap<>();

        String jpql = "SELECT e.id, ec.content.uuid FROM EnvironmentContent ec " +
            "JOIN Environment e ON ec.environment.id = e.id " +
            "WHERE e.id IN (:envIDs)";

        Query query = this.getEntityManager().createQuery(jpql, Object[].class);
        int blockSize = Math.min(this.getInBlockSize(), this.getQueryParameterLimit() - 1);

        for (List<String> block : Iterables.partition(environmentIds, blockSize)) {
            query.setParameter("envIDs", block);
            processData(contentUUIDMap, query.getResultList());
        }

        return contentUUIDMap;
    }

    /**
     * Process the result list (row) which contains entitlement ID & content UUID to
     * build a map.
     */
    private void processData(Map<String, List<String>> contentUUIDMap, List<Object[]> resultList) {
        for (Object[] result : resultList) {
            String envId = result[0].toString();
            List<String> ids = contentUUIDMap.getOrDefault(envId, new ArrayList<>());
            ids.add(result[1].toString());
            contentUUIDMap.put(envId, ids);
        }
    }
}
