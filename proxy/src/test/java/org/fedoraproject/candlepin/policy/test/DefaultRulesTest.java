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
package org.fedoraproject.candlepin.policy.test;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.js.JavascriptEnforcer;
import org.fedoraproject.candlepin.policy.js.PostEntHelper;
import org.fedoraproject.candlepin.policy.js.PreEntHelper;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.util.DateSource;

import org.junit.Before;
import org.junit.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.script.ScriptEngineManager;

import junit.framework.Assert;

/**
 * DefaultRulesTest
 */
public class DefaultRulesTest {
    private Enforcer enforcer;
    private DateSource dateSource;
    private PreEntHelper preHelper;
    private PostEntHelper postHelper;
    private ProductServiceAdapter prodAdapter;
    private Consumer consumer;
    private List<Pool> pools;
    private static final String RULES_JS = "" + 
        "function select_pool_global() { return pools.getFirst();} \n"        + 
        "function select_pool_monitoring() {                       \n"        + 
        "    var poolItr; var pool;                                \n"        + 
        "    for (poolItr = pools.iterator(); poolItr.hasNext();){ \n"        + 
        "        pool = poolItr.next();                            \n"        + 
        "        if (pool.getProductId() == \"monitoring\"){       \n"        + 
        "            return pool;                                  \n"        + 
        "       }                                                  \n"        + 
        "    }                                                     \n"        + 
        "    return pools.getFirst();                              \n"        + 
        "}                                                         \n";

    @Before
    public void createEnforcer() {

        // If you wish to test against the default rules file,
        // use the following code to load it in from the classpath
        /*
         * URL url = this.getClass().getClassLoader().getResource(
         * "rules/default-rules.js"); InputStreamReader inputStreamReader = new
         * InputStreamReader(url .openStream());
         */
        StringReader inputStreamReader = new StringReader(RULES_JS);

        enforcer = new JavascriptEnforcer(dateSource, inputStreamReader,
            preHelper, postHelper, prodAdapter, new ScriptEngineManager()
                .getEngineByName("JavaScript"));
    }

    @Before
    public void createPools() {

        pools = new ArrayList<Pool>();
        consumer = new Consumer();

        Pool pool = new Pool();
        pool.setId(new Long(0));
        pool.setProductId("default");
        pools.add(pool);

        pool = new Pool();
        pool.setId(new Long(1));
        pool.setProductId("monitoring");
        pools.add(pool);
    }

    @Test
    public void runDefaultRules() {
        Pool selected = enforcer.selectBestPool(consumer, "Shampoo", pools);
        Assert.assertNotNull(selected);
        Assert.assertEquals("default", selected.getProductId());
    }

    @Test
    public void runMonitoringRules() {
        final String productId = "monitoring";
        Pool selected = enforcer.selectBestPool(consumer, productId, pools);
        Assert.assertNotNull(selected);
        Assert.assertEquals(productId, selected.getProductId());
    }

}
