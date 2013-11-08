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

import org.apache.log4j.Logger;
import org.candlepin.model.Pool;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.RulesObjectMapper;

import com.google.inject.Inject;

/**
 * PoolTypeRules
 */
public class PoolTypeRules {
    private JsRunner jsRules;
    private RulesObjectMapper mapper;
    private static Logger log = Logger.getLogger(PoolTypeRules.class);

    @Inject
    public PoolTypeRules(JsRunner jsRules) {
        this.jsRules = jsRules;

        mapper = RulesObjectMapper.instance();
        jsRules.init("pool_type_name_space");
    }

    public PoolType getPoolType(Pool p) {
        JsonJsContext args = new JsonJsContext(mapper);

        args.put("pool", p);
        args.put("log", log, false);

        String json = jsRules.runJsFunction(String.class, "get_pool_type", args);
        PoolType dto = mapper.toObject(json, PoolType.class);
        return dto;
    }
}
