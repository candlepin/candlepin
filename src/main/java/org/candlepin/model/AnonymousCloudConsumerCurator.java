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

import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Singleton;



@Singleton
public class AnonymousCloudConsumerCurator extends AbstractHibernateCurator<AnonymousCloudConsumer> {

    public AnonymousCloudConsumerCurator() {
        super(AnonymousCloudConsumer.class);
    }

    /**
     * Retrieves an anonymous cloud consumer by the UUID.
     *
     * @param uuid
     *     the UUID that corresponds to an anonymous consumer
     *
     * @return the anonymous consumer based on the provided UUID, or null if no matching anonymous cloud
     * consumer is found
     */
    public AnonymousCloudConsumer getByUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }

        Criteria criteria = this.createSecureCriteria()
            .add(Restrictions.eq("uuid", uuid));

        return (AnonymousCloudConsumer) criteria.uniqueResult();
    }

    /**
     * Retrieves anonymous cloud consumers based on the provided UUIDs.
     *
     * @param uuids
     *  the UUIDs that corresponds to anonymous consumers
     *
     * @return the anonymous consumers based on the provided UUIDs, or empty list if none are found
     */
    public List<AnonymousCloudConsumer> getByUuids(Collection<String> uuids) {
        List<AnonymousCloudConsumer> consumers = new ArrayList<>();
        if (uuids == null || uuids.isEmpty()) {
            return consumers;
        }

        for (List<String> block : this.partition(uuids)) {
            Criteria criteria = this.createSecureCriteria()
                .add(Restrictions.in("uuid", block));

            consumers.addAll(criteria.list());
        }

        return consumers;
    }

    /**
     * Retrieves an anonymous cloud consumer using the provided cloud instance ID.
     *
     * @param instanceId
     *     the ID of the cloud instance that used to retrieve an anonymous cloud consumer
     *
     * @return the anonymous consumer based on the cloud instance ID, or null if no matching anonymous
     * cloud consumer is found
     */
    public AnonymousCloudConsumer getByCloudInstanceId(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return null;
        }

        Criteria criteria = this.createSecureCriteria()
            .add(Restrictions.eq("cloudInstanceId", instanceId));

        return (AnonymousCloudConsumer) criteria.uniqueResult();
    }

}
