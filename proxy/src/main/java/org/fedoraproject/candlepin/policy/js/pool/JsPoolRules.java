/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.policy.js.pool;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.policy.PoolRules;
import com.google.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.fedoraproject.candlepin.controller.PoolManager;
import org.fedoraproject.candlepin.exceptions.IseException;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.policy.js.JsRules;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;

/**
 *
 */
public class JsPoolRules implements PoolRules {

    private static Logger log = Logger.getLogger(JsPoolRules.class);

    private JsRules jsRules;
    private PoolManager poolManager;
    private ProductServiceAdapter productAdapter;

    @Inject
    public JsPoolRules(JsRules jsRules, PoolManager poolManager,
        ProductServiceAdapter productAdapter) {
        this.jsRules = jsRules;
        this.poolManager = poolManager;
        this.productAdapter = productAdapter;
        jsRules.init("pool_name_space");
    }

    @Override
    public List<Pool> createPool(Subscription sub) {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("sub", sub);
        args.put("attributes", jsRules.getFlattenedAttributes(sub.getProduct(), null));
        args.put("helper", new PoolHelper(this.poolManager,
            this.productAdapter, null));
        List<Pool> poolsCreated = null;
        try {
            poolsCreated = jsRules.invokeMethod("createPools", args);
        }
        catch (NoSuchMethodException e) {
            log.error("Unable to find javascript method: createPools");
            log.error(e);
            throw new IseException("Unable to create pool.");
        }
        return poolsCreated;
    }

}
