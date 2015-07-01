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

import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolComplianceType;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



/**
 * CalculatedAttributesUtil
 */
public class CalculatedAttributesUtil {

    private I18n i18n;

    @Inject
    public CalculatedAttributesUtil(I18n i18n) {
        this.i18n = i18n;
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
            pool.setCalculatedAttributes(this.buildCalculatedAttributes(pool, date));
        }
    }
}
