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
package org.candlepin.resource.util;

import org.candlepin.dto.rules.v1.SuggestedQuantityDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolComplianceType;
import org.candlepin.policy.js.quantity.QuantityRules;

import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;



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
        Map<String, String> attrMap = new HashMap<>();
        PoolComplianceType type = pool.getComplianceType();

        String complianceType = this.i18n.tr(type.getDescription());
        if (pool.isUnmappedGuestPool()) {
            complianceType = this.i18n.tr("{0} (Temporary)", complianceType);
        }

        attrMap.put("compliance_type", complianceType);

        return attrMap;
    }

    public void setCalculatedAttributes(List<Pool> poolList, Date date) {
        for (Pool pool : poolList) {
            Map<String, String> attrMap = pool.getCalculatedAttributes();
            if (attrMap == null) {
                attrMap = new HashMap<>();
                pool.setCalculatedAttributes(attrMap);
            }
            attrMap.putAll(this.buildCalculatedAttributes(pool, date));
        }
    }

    public void setQuantityAttributes(Pool pool, Consumer c, Date date) {
        List<Pool> poolList = new ArrayList<>();
        poolList.add(pool);
        setQuantityAttributes(poolList, c, date);
    }

    public void setQuantityAttributes(List<Pool> poolList, Consumer c, Date date) {
        if (c == null) {
            return;
        }

        Map<String, SuggestedQuantityDTO> results = quantityRules.getSuggestedQuantities(poolList, c, date);

        for (Pool p : poolList) {
            SuggestedQuantityDTO suggested = results.get(p.getId());

            Map<String, String> attrMap = p.getCalculatedAttributes();
            if (attrMap == null) {
                attrMap = new HashMap<>();
                p.setCalculatedAttributes(attrMap);
            }

            attrMap.put("suggested_quantity",
                String.valueOf(suggested.getSuggested()));
            attrMap.put("quantity_increment",
                String.valueOf(suggested.getIncrement()));
        }
    }
}
