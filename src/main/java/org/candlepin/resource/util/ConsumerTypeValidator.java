/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.resource.util;

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;

import com.google.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
/**
 *
 * ConsumerTypeValidator validate consumer type in REST API
 */
public class ConsumerTypeValidator {

    private I18n i18n;
    private ConsumerTypeCurator consumerTypeCurator;


    @Inject
    public ConsumerTypeValidator(ConsumerTypeCurator consumerTypeCurator, I18n i18n) {
        super();
        this.i18n = i18n;
        this.consumerTypeCurator = consumerTypeCurator;
    }

    public List<ConsumerType> findAndValidateTypeLabels(Set<String> labels) {
        if (labels != null && !labels.isEmpty()) {
            List<ConsumerType> types = consumerTypeCurator.getByLabels(labels);
            validate(types, labels);
            return types;
        }
        return null;
    }

    private void validate(List<ConsumerType> types, Set<String> labels) {
        // Since the type labels are unique, our sizes must match.
        if (labels.size() != types.size()) {
            List<String> invalidLabels = findInvalidLabels(labels, types);
            throw new BadRequestException(i18n.tr("No such unit type(s): {0}",
                StringUtils.join(invalidLabels, ", ")));
        }
    }

    private List<String> findInvalidLabels(Set<String> labels,
        List<ConsumerType> types) {
        List<String> invalidLabels = new ArrayList<>(labels);
        for (ConsumerType type : types) {
            String label = type.getLabel();
            if (labels.contains(label)) {
                invalidLabels.remove(label);
            }
        }
        return invalidLabels;
    }
}
