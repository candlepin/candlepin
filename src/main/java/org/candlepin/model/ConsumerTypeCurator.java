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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.candlepin.exceptions.BadRequestException;
import org.hibernate.criterion.Restrictions;

/**
 * ConsumerTypeCurator
 */
public class ConsumerTypeCurator extends AbstractHibernateCurator<ConsumerType> {

    protected ConsumerTypeCurator() {
        super(ConsumerType.class);
    }

    /**
     * lookup the ConsumerType by its label.
     *
     * @param label
     *            type to lookup
     * @return ConsumerType whose label matches the given label.
     */
    public ConsumerType lookupByLabel(String label) {
        return (ConsumerType) currentSession().createCriteria(
            ConsumerType.class).add(Restrictions.eq("label", label))
            .uniqueResult();
    }

    /**
     * look up consumer types by their labels
     * @param labels
     * @return all types matching the specified labels;
     */
    @SuppressWarnings("unchecked")
    public List<ConsumerType> lookupByLabels(Collection<String> labels) {
        return (List<ConsumerType>) currentSession().createCriteria(ConsumerType.class)
            .add(Restrictions.in("label", labels)).list();
    }

    public List<ConsumerType> lookupConsumerTypes(Set<String> labels) {
        List<ConsumerType> types = this.lookupByLabels(labels);
        // Since the type labels are unique, our sizes must match.
        if (labels.size() != types.size()) {
            List<String> invalidLabels = new ArrayList<String>(labels);
            for (ConsumerType type : types) {
                String label = type.getLabel();
                if (labels.contains(label)) {
                    invalidLabels.remove(label);
                }
            }
            throw new BadRequestException(i18n.tr("No such unit type(s): {0}",
                StringUtils.join(invalidLabels, ", ")));
        }
        return types;
    }
}
