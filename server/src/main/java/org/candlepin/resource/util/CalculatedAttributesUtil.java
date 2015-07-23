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
import org.candlepin.model.Pool.PoolComplianceType;
import org.candlepin.policy.js.quantity.QuantityRules;
import org.candlepin.policy.js.quantity.SuggestedQuantity;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



/**
 * CalculatedAttributesUtil
 */
public class CalculatedAttributesUtil {

    private QuantityRules quantityRules;
    private I18n i18n;

    @Inject
    public CalculatedAttributesUtil(QuantityRules quantityRules, I18n i18n) {
        this.i18n = i18n;
        this.quantityRules = quantityRules;
    }

    public Map<String, String> buildCalculatedAttributes(Pool pool, Date date) {
        Map<String, String> attrMap = new HashMap<String, String>();
        PoolComplianceType type = pool.getComplianceType();

        // TODO: Check that this doesn't break our translation stuff. We may need to have the
        // description strings translated instead.
        attrMap.put("compliance_type",
            i18n.tr("{0}{1}", type.getDescription(), (pool.isUnmappedGuestPool() ? " (Temporary)" : ""))
        );

        return attrMap;
    }

    public void setCalculatedAttributes(List<Pool> poolList, Date date) {
        for (Pool pool : poolList) {
            Map<String, String> attrMap = pool.getCalculatedAttributes();
            if (attrMap == null) {
                attrMap = new HashMap<String, String>();
                pool.setCalculatedAttributes(attrMap);
            }
            attrMap.putAll(this.buildCalculatedAttributes(pool, date));
        }
    }

    public void setQuantityAttributes(Pool pool, Consumer c, Date date) {
        List<Pool> poolList = new ArrayList<Pool>();
        poolList.add(pool);
        setQuantityAttributes(poolList, c, date);
    }

    public void setQuantityAttributes(List<Pool> poolList, Consumer c, Date date) {
        if (c == null) {
            return;
        }
        Map<String, SuggestedQuantity> results = quantityRules.getSuggestedQuantities(
                poolList, c, date);

        for (Pool p : poolList) {
            SuggestedQuantity suggested = results.get(p.getId());

            Map<String, String> attrMap = p.getCalculatedAttributes();
            if (attrMap == null) {
                attrMap = new HashMap<String, String>();
                p.setCalculatedAttributes(attrMap);
            }

            attrMap.put("suggested_quantity",
                String.valueOf(suggested.getSuggested()));
            attrMap.put("quantity_increment",
                String.valueOf(suggested.getIncrement()));
        }
    }
}
