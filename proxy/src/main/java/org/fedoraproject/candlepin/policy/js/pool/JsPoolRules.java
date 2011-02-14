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

import org.fedoraproject.candlepin.policy.PoolRules;
import com.google.inject.Inject;
import java.util.Map;
import org.fedoraproject.candlepin.controller.PoolManager;
import org.fedoraproject.candlepin.guice.RulesReaderProvider;
import org.fedoraproject.candlepin.guice.ScriptEngineProvider;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.policy.js.JsRules;
import org.fedoraproject.candlepin.policy.js.ReadOnlyPool;
import org.fedoraproject.candlepin.policy.js.ReadOnlyProduct;
import org.fedoraproject.candlepin.policy.js.ReadOnlyProductCache;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;

/**
 *
 */
public class JsPoolRules extends JsRules implements PoolRules {

    private PoolManager poolManager;
    private ProductServiceAdapter productAdapter;

    @Inject
    public JsPoolRules(RulesReaderProvider rulesReaderProvider,
        ScriptEngineProvider jsEngineProvider, PoolManager poolManager,
        ProductServiceAdapter productAdapter) {
        super(rulesReaderProvider, jsEngineProvider, "pool_name_space");

        this.poolManager = poolManager;
        this.productAdapter = productAdapter;
    }

    @Override
    public void onCreatePool(Pool pool) {
        this.init();

        Product product = this.productAdapter.getProductById(pool.getProductId());
        ReadOnlyProductCache cache = new ReadOnlyProductCache(productAdapter);

        Map<String, String> allAttributes = getFlattenedAttributes(product, pool);

        jsEngine.put("pool", new ReadOnlyPool(pool, cache));
        jsEngine.put("product", new ReadOnlyProduct(product));
        jsEngine.put("helper", new PoolHelper(this.poolManager,
                this.productAdapter, pool, null));
        jsEngine.put("attributes", allAttributes);

        invokeRule("global");
    }

}
