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
package org.candlepin.resource.util;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Pool;
import org.candlepin.policy.js.quantity.QuantityRules;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

/**
 * CalculatedAttributesUtil
 */
public class CalculatedAttributesUtil {
    private I18n i18n;
    private ConsumerCurator consumerCurator;
    private QuantityRules quantityRules;

    @Inject
    public CalculatedAttributesUtil(I18n i18n, ConsumerCurator consumerCurator,
        QuantityRules quantityRules) {
        this.i18n = i18n;
        this.consumerCurator = consumerCurator;
        this.quantityRules = quantityRules;
    }

    public Pool addCalculatedAttributes(Pool p, Consumer c) {
        if (c == null) {
            return p;
        }

        p.addCalculatedAttribute("suggested_quantity",
            String.valueOf(quantityRules.getSuggestedQuantity(p, c)));

        if (p.hasProductAttribute("instance_multiplier")) {
            p.addCalculatedAttribute("quantity_increment",
                p.getProductAttribute("instance_multiplier").getValue());
        }
        return p;
    }
}
