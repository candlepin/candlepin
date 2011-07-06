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
package org.fedoraproject.candlepin.policy.js.export;

import com.google.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.policy.js.JsRules;
import org.fedoraproject.candlepin.policy.js.RuleExecutionException;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.mozilla.javascript.RhinoException;

/**
 *
 */
public class JsExportRules {
    private static Logger log = Logger.getLogger(JsExportRules.class);

    private JsRules jsRules;
    private ProductServiceAdapter productAdapter;

    @Inject
    public JsExportRules(JsRules jsRules, ProductServiceAdapter productAdapter) {
        this.jsRules = jsRules;
        this.productAdapter = productAdapter;
        jsRules.init("export_name_space");
    }

    public boolean canExport(Entitlement entitlement) {
        Pool pool = entitlement.getPool();
        Product product = this.productAdapter.getProductById(pool.getProductId());
        Map<String, String> allAttributes = jsRules.getFlattenedAttributes(product, pool);

        Map<String, Object> args = new HashMap<String, Object>();
        args.put("entitlement", entitlement);
        args.put("product", product);
        args.put("attributes", allAttributes);

        // just default to true if there are any errors
        Boolean canExport = true;
        try {
            canExport = jsRules.invokeMethod("can_export_entitlement", args);
        }
        catch (NoSuchMethodException e) {
            log.warn("No method found: can_export_entitlement");
        }
        catch (RhinoException e) {
            throw new RuleExecutionException(e);
        }

        return canExport;
    }

}
