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
import org.candlepin.model.Pool;
import org.candlepin.policy.js.pooltype.PoolComplianceType;
import org.candlepin.policy.js.pooltype.PoolComplianceTypeRules;
import org.candlepin.policy.js.quantity.QuantityRules;
import org.candlepin.policy.js.quantity.SuggestedQuantity;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * CalculatedAttributesUtil
 */
public class CalculatedAttributesUtil {

    private QuantityRules quantityRules;
    private PoolComplianceTypeRules poolTypeRules;
    private I18n i18n;

    @Inject
    public CalculatedAttributesUtil(QuantityRules quantityRules,
            PoolComplianceTypeRules poolTypeRules, I18n i18n) {
        this.quantityRules = quantityRules;
        this.poolTypeRules = poolTypeRules;
        this.i18n = i18n;
    }

    public Map<String, String> buildCalculatedAttributes(Pool p, Consumer c, Date date) {
        Map<String, String> attrMap = new HashMap<String, String>();

        PoolComplianceType type = poolTypeRules.getPoolType(p);
        type.translatePoolType(i18n);
        attrMap.put("compliance_type", type.getPoolType());

        if (c == null) {
            return attrMap;
        }

        SuggestedQuantity suggested = quantityRules.getSuggestedQuantity(p, c, date);

        attrMap.put("suggested_quantity",
            String.valueOf(suggested.getSuggested()));
        attrMap.put("quantity_increment",
            String.valueOf(suggested.getIncrement()));

        return attrMap;
    }
}
