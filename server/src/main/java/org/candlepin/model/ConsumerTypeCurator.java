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

import org.candlepin.model.ConsumerType.ConsumerTypeEnum;

import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

import javax.persistence.NoResultException;



/**
 * ConsumerTypeCurator
 */
@Component
public class ConsumerTypeCurator extends AbstractHibernateCurator<ConsumerType> {

    public ConsumerTypeCurator() {
        super(ConsumerType.class);
    }

    /**
     * Fetches the ConsumerType for the specified consumer. If the consumer does not have a defined
     * type ID, or the type ID is invalid, this method throws an exception.
     *
     * @param consumer
     *  The consumer for which to fetch a ConsumerType object
     *
     * @throws IllegalArgumentException
     *  if consumer is null or does not have a defined type ID
     *
     * @throws IllegalStateException
     *  if the consumer's defined type ID is invalid
     *
     * @return
     *  A ConsumerType instance for the specified consumer
     */
    public ConsumerType getConsumerType(Consumer consumer) {
        if (consumer == null || consumer.getTypeId() == null) {
            throw new IllegalArgumentException("consumer is null or does not have a defined type ID");
        }

        ConsumerType type = this.get(consumer.getTypeId());

        if (type == null) {
            throw new IllegalStateException("consumer is not associated with a valid type: " + consumer);
        }

        return type;
    }

    /**
     * Attempts to fetch a ConsumerType by its label.
     *
     * @param label
     *  The label of the consumer type to fetch
     *
     * @return ConsumerType whose label matches the given label
     */
    public ConsumerType getByLabel(String label) {
        return this.getByLabel(label, false);
    }

    /**
     * Fetches the specified consumer type by label if it exists. If the type does not exist and the "create"
     * flag is set, the type will be created.
     *
     * @param label
     *  The label of the consumer type to fetch
     *
     * @param createIfAbsent
     *  Whether or not to create the consumer type if it does not yet exist
     *
     * @throws IllegalArgumentException
     *  if label is null or empty
     *
     * @return
     *  The consumer type with the specified label, or null if the label does not exist and the create flag
     *  is not set
     */
    public ConsumerType getByLabel(String label, boolean createIfAbsent) {
        if (label == null || label.isEmpty()) {
            throw new IllegalArgumentException("label is null or empty");
        }

        String jpql = "SELECT t FROM ConsumerType t WHERE t.label = :label";
        ConsumerType ctype = null;

        try {
            // Note: this will throw an exception if we return anything other than one result. In the case
            // of two or more results, we've got DB problems, so we don't catch that exception.
            ctype = this.getEntityManager()
                .createQuery(jpql, ConsumerType.class)
                .setParameter("label", label)
                .getSingleResult();
        }
        catch (NoResultException e) {
            if (createIfAbsent) {
                try {
                    ctype = new ConsumerType(ConsumerTypeEnum.valueOf(label));
                }
                catch (IllegalArgumentException e2) {
                    ctype = new ConsumerType(label);
                }

                ctype = this.create(ctype);
            }
        }

        return ctype;
    }

    /**
     * look up consumer types by their labels
     *
     * @param labels
     * @return all types matching the specified labels;
     */
    @SuppressWarnings("unchecked")
    public List<ConsumerType> getByLabels(Collection<String> labels) {
        return (List<ConsumerType>) currentSession().createCriteria(ConsumerType.class)
            .add(Restrictions.in("label", labels)).list();
    }

}
