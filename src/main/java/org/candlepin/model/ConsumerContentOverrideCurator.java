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

import org.hibernate.query.NativeQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
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

    /**
     * Fetches the layered content overrides applicable to the given consumer. This method will
     * return an unordered list of deduplicated content overrides attached to the specific consumer,
     * or any of the environments the consumer is in. The deduplication occurs using the following
     * rules:
     * <ul>
     *   <li>overrides tracked using the content label and attribute name fields</li>
     *   <li>if a consumer content override exists for a given label+name pairing, its value will be
     *      used even if one or more environment content overrides exist with the same pairing</li>
     *   <li>if multiple environment content overrides exist for a given label+name pairing, the
     *      value from the override coming from the environment with the highest priority</li>
     *   <li>if a single consumer or environment has one or more content overrides with a collision
     *      on the label+name, the value will be selected at random</li>
     * </ul>
     *
     * The above rules assume that the provided consumer reference is both valid and the consumer
     * has one or more content overrides from any source. If the consumer reference is not valid, or
     * the consumer does not have any applicable overrides, this method returns an empty list.
     *
     * @param consumerId
     *  the ID of the consumer for which to fetch layered content overrides
     *
     * @return
     *  a list of layered content overrides for the given consumer
     */
    public List<ContentOverride<?, ?>> getLayeredContentOverrides(String consumerId) {
        // Impl note:
        // This particular query cannot be performed with a left join on MySQL/MariaDB, as it will
        // refuse to use the table indexes on the resultant join, triggering a full table scan over
        // millions of rows. A union here works around the issue while still fetching the same set
        // of overrides.
        // Also note that we have to wrap the union to apply the ordering as hsqldb doesn't allow
        // applying the ordering in the way we're doing here to the union directly.
        String sql = "SELECT u.created, u.updated, u.content_label, u.name, u.value, u.priority FROM (" +
            "SELECT override.created, override.updated, override.content_label, override.name, " +
            " override.value, cenv.priority " +
            "  FROM cp_content_override override " +
            "    JOIN cp_consumer_environments cenv ON cenv.environment_id = override.environment_id " +
            "  WHERE cenv.cp_consumer_id = :consumer_id " +
            "UNION ALL " +
            "SELECT override.created, override.updated, override.content_label, override.name, " +
            " override.value, NULL AS priority  " +
            "  FROM cp_content_override override  " +
            "  WHERE override.consumer_id = :consumer_id) u " +
            "ORDER BY u.priority IS NOT NULL DESC, u.priority DESC ";

        Map<String, Map<String, ContentOverride<?, ?>>> labelMap = new HashMap<>();

        // Impl note:
        // While it would be nice to let Hibernate magic this away, it cannot. Not only will it not
        // do what we want it to do here, it crashes with a mysterious NPE deep in Hibernate's
        // result processor when it needs to populate a new instance. Worse, we can't even use
        // getReference to make well-formed override instances for some reason, because that
        // triggers a *different* exception within Hibernate's loading routine. Sadly, all this
        // means that we need to handle the ORM bits ourselves here. :/
        java.util.function.Consumer<Object[]> rowProcessor = (row) -> {
            String contentLabel = (String) row[2];
            String attribName = (String) row[3];
            String value = (String) row[4];

            ContentOverride<?, ?> override = new ContentOverride<>() {
                @Override
                public AbstractHibernateObject getParent() {
                    return null;
                }
            };

            override.setCreated((Date) row[0])
                .setUpdated((Date) row[1]);

            override.setContentLabel(contentLabel)
                .setName(attribName)
                .setValue(value);

            // Impl note: content labels are case sensitive, but attribute names are *not*. Weird.
            labelMap.computeIfAbsent(contentLabel, key -> new HashMap<>())
                .put(attribName.toLowerCase(), override);
        };

        this.getEntityManager()
            .createNativeQuery(sql)
            .unwrap(NativeQuery.class)
            .addSynchronizedEntityClass(Consumer.class)
            .addSynchronizedEntityClass(ConsumerContentOverride.class)
            .addSynchronizedQuerySpace("cp_consumer_environments")
            .setParameter("consumer_id", consumerId)
            .getResultList()
            .forEach(rowProcessor);

        return labelMap.values()
            .stream()
            .map(Map::values)
            .flatMap(Collection::stream)
            .toList();
    }

    /**
     * Fetches the layered content overrides applicable to the given consumer. This method will
     * return an unordered list of deduplicated content overrides attached to the specific consumer,
     * or any of the environments the consumer is in. The deduplication occurs using the following
     * rules:
     * <ul>
     *   <li>overrides tracked using the content label and attribute name fields</li>
     *   <li>if a consumer content override exists for a given label+name pairing, its value will be
     *      used even if one or more environment content overrides exist with the same pairing</li>
     *   <li>if multiple environment content overrides exist for a given label+name pairing, the
     *      value from the override coming from the environment with the highest priority</li>
     *   <li>if a single consumer or environment has one or more content overrides with a collision
     *      on the label+name, the value will be selected at random</li>
     * </ul>
     *
     * The above rules assume that the provided consumer reference is both valid and the consumer
     * has one or more content overrides from any source. If the consumer reference is not valid, or
     * the consumer does not have any applicable overrides, this method returns an empty list.
     *
     * @param consumer
     *  the consumer for which to fetch layered content overrides
     *
     * @return
     *  a list of layered content overrides for the given consumer
     */
    public List<ContentOverride<?, ?>> getLayeredContentOverrides(Consumer consumer) {
        if (consumer == null) {
            return new ArrayList<>();
        }

        return this.getLayeredContentOverrides(consumer.getId());
    }

}
