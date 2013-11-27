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
package org.candlepin.policy.js.pooltype;

import org.candlepin.model.Pool;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.RulesObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

/**
 * PoolTypeRules
 *
 * A class to use the rules to determine the type
 * of pool we are considering in order to display
 * for a consumer.
 */
public class PoolComplianceTypeRules {

    private JsRunner jsRules;
    private RulesObjectMapper mapper;
    private static Logger log = LoggerFactory.getLogger(PoolComplianceTypeRules.class);

    @Inject
    public PoolComplianceTypeRules(JsRunner jsRules) {
        this.jsRules = jsRules;

        mapper = RulesObjectMapper.instance();
        jsRules.init("pool_type_name_space");
    }

    public PoolComplianceType getPoolType(Pool p) {
        JsonJsContext args = new JsonJsContext(mapper);

        args.put("pool", p);
        args.put("log", log, false);

        String json = jsRules.runJsFunction(String.class, "get_pool_type", args);
        PoolComplianceType dto = mapper.toObject(json, PoolComplianceType.class);
        return dto;
    }
}
