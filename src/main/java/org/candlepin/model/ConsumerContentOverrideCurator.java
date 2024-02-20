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
import java.util.List;
import java.util.Map;

import javax.inject.Singleton;

/**
 * ConsumerContentOverrideCurator
 */
@Singleton
public class ConsumerContentOverrideCurator extends
    ContentOverrideCurator<ConsumerContentOverride, Consumer> {

    public ConsumerContentOverrideCurator() {
        super(ConsumerContentOverride.class, "consumer");
    }

    @Override
    protected ConsumerContentOverride createOverride() {
        return new ConsumerContentOverride();
    }

    public List<ContentOverride> getLayeredOverrideList(Consumer parent) {
        String query =
            "select id, content_label, name, value, created, updated, -1 as priority " +
            "from cp_content_override " +
            " where consumer_id=? " +
            "union select d.id, d.content_label, d.name, d.value, d.created, d.updated, " +
            " cast(e.priority as int) " +
            "from cp_content_override d " +
            "join cp_consumer_environments e on d.environment_id=e.environment_id " +
            " where e.cp_consumer_id=? " +
            "order by content_label, name, priority desc";
        List<ContentOverride> result = this.getEntityManager()
            .createNativeQuery(query, ContentOverride.class)
            .setParameter(1, parent.getId())
            .setParameter(2, parent.getId())
            .getResultList();

        Map<String, ContentOverride> theMap = new HashMap<>();
        for (ContentOverride co : result) {
            String name = co.getContentLabel() + "::" + co.getName();
            String value = co.getValue();
            if (value.equalsIgnoreCase("true") ||
                value.equalsIgnoreCase("false")) {
                ContentOverride existing = theMap.get(name);
                if (existing == null ||
                    existing.getValue().equalsIgnoreCase("false") ||
                    co instanceof ConsumerContentOverride) {
                    theMap.put(name, co);
                }
            }
            else {
                theMap.put(name, co);
            }
        }
        return theMap.values().stream().toList();
    }
}
