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
package org.candlepin.policy.js.pool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.candlepin.config.Config;
import org.candlepin.exceptions.IseException;
import org.candlepin.model.Consumer;
import org.candlepin.model.Pool;
import org.candlepin.policy.PoolFilter;
import org.candlepin.policy.js.JsRules;

import com.google.inject.Inject;

/**
 * JsPoolFilter
 */
public class JsPoolFilter implements PoolFilter {
    private static Logger log = Logger.getLogger(JsPoolFilter.class);
    protected Logger rulesLogger = null;

    private JsRules jsRules;
    private Config config;

    @Inject
    public JsPoolFilter(JsRules jsRules, Config config) {
        this.jsRules = jsRules;
        this.config = config;
        rulesLogger = Logger.getLogger(JsPoolFilter.class.getCanonicalName() + ".rules");
        jsRules.init("pool_filter_name_space");
    }

    /* (non-Javadoc)
     * @see org.candlepin.policy.PoolFilter#filterPools(java.util.List)
     */
    @Override
    public List<Pool> filterPools(Consumer consumer, List<Pool> pools) {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("pools", pools);
        args.put("standalone", config.standalone());
        args.put("log", rulesLogger);
        args.put("consumer", consumer);
        List<Pool> poolsFiltered = null;
        try {
            poolsFiltered = jsRules.invokeMethod("filterPools", args);
        }
        catch (NoSuchMethodException e) {
            log.error("Unable to find javascript method: filterPools");
            log.error(e);
            throw new IseException("Unable to filterPools.");
        }
        return poolsFiltered;
    }

}
