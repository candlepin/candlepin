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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Singleton;
import javax.persistence.NoResultException;
import javax.persistence.Query;



/**
 * EnvironmentContentCurator
 */
@Singleton
public class EnvironmentContentCurator extends AbstractHibernateCurator<EnvironmentContent> {
    public EnvironmentContentCurator() {
        super(EnvironmentContent.class);
    }

    public EnvironmentContent getByEnvironmentAndContent(Environment env, String contentId) {
        if (env == null || contentId == null) {
            return null;
        }

        try {
            String jpql = "SELECT ec FROM Environment env JOIN env.environmentContent ec " +
                "WHERE env.id = :env_id AND ec.contentId = :content_id";

            return this.getEntityManager()
                .createQuery(jpql, EnvironmentContent.class)
                .setParameter("env_id", env.getId())
                .setParameter("content_id", contentId)
                .getSingleResult();
        }
        catch (NoResultException e) {
            // intentionally left empty
        }

        return null;
    }

    public EnvironmentContent getByEnvironmentAndContent(Environment env, Content content) {
        if (env == null || content == null) {
            return null;
        }

        return this.getByEnvironmentAndContent(env, content.getId());
    }

    public List<EnvironmentContent> getByContent(Owner owner, String contentId) {
        String jpql = "SELECT ec FROM Environment env JOIN env.environmentContent ec " +
            "WHERE env.owner.id = :owner_id AND ec.contentId = :content_id";

        return this.getEntityManager()
            .createQuery(jpql, EnvironmentContent.class)
            .setParameter("owner_id", owner.getId())
            .setParameter("content_id", contentId)
            .getResultList();
    }

    /**
     * Returns a mapping of environment IDs to the content IDs promoted to the environment. If none
     * of the specified environments exist or none have content, this method returns an empty map.
     *
     * @param environmentIds
     *  a collection of environment IDs for which to fetch content IDs
     *
     * @return
     *  a mapping of environment IDs to content IDs
     */
    public Map<String, Set<String>> getEnvironmentContentIdMap(Iterable<String> environmentIds) {
        Map<String, Set<String>> envContentIdMap = new HashMap<>();

        if (environmentIds != null) {
            String jpql = "SELECT ec.environmentId, ec.contentId FROM EnvironmentContent ec " +
                "WHERE ec.environmentId IN (:env_ids)";

            Query query = this.getEntityManager()
                .createQuery(jpql);

            for (List<String> block : this.partition(environmentIds)) {
                List<Object[]> rows = query.setParameter("env_ids", block)
                    .getResultList();

                for (Object[] row : rows) {
                    envContentIdMap.computeIfAbsent((String) row[0], (key) -> new HashSet<>())
                        .add((String) row[1]);
                }
            }
        }

        return envContentIdMap;
    }

}
