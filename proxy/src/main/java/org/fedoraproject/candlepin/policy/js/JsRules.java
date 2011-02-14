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
package org.fedoraproject.candlepin.policy.js;

import java.util.HashMap;
import java.util.Map;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.guice.RulesReaderProvider;
import org.fedoraproject.candlepin.guice.ScriptEngineProvider;
import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;

/**
 *
 */
public abstract class JsRules {
    private Logger log = Logger.getLogger(JsRules.class);

    private ScriptEngineProvider jsEngineProvider;
    private RulesReaderProvider rulesReaderProvider;
    protected ScriptEngine jsEngine;
    protected Object rulesNameSpace;
    private String namespace;

    private boolean initialized = false;

    public JsRules(RulesReaderProvider rulesReaderProvider,
        ScriptEngineProvider jsEngineProvider, String namespace) {
        this.jsEngineProvider = jsEngineProvider;
        this.rulesReaderProvider = rulesReaderProvider;
        this.namespace = namespace;
    }

    /*
     * The init method allows the expensive creation of the rules engine
     * to be deferred until it is actually needed. All non constructor
     * methods must call this before doing any work.
     */
    protected final synchronized void init() {

        if (!initialized) {
            this.jsEngine = this.jsEngineProvider.get();

            if (this.jsEngine == null) {
                throw new RuntimeException("No Javascript engine");
            }

            try {
                this.jsEngine.eval(rulesReaderProvider.get());
                this.rulesNameSpace =
                    ((Invocable) this.jsEngine)
                        .invokeFunction(this.namespace);

                this.initialized = true;
                this.rulesInit();
            }
            catch (ScriptException ex) {
                this.initialized = false;
                throw new RuleParseException(ex);
            }
            catch (NoSuchMethodException ex) {
                this.initialized = false;
                throw new RuleParseException(ex);
            }
        }
    }

    /**
     * This is for subclasses to perform whatever initialization they need.
     */
    protected void rulesInit() throws NoSuchMethodException, ScriptException { }

    protected <T> T invokeMethod(String method) throws NoSuchMethodException,
            ScriptException {
        this.init();
        Invocable inv = (Invocable) jsEngine;
        
        return (T) inv.invokeMethod(this.rulesNameSpace, method);
    }

    protected void invokeRule(String ruleName) {
        log.debug("Running rule: " + ruleName + " in namespace: " + namespace);

        try {
            this.invokeMethod(ruleName);
        }
        catch (NoSuchMethodException ex) {
            log.warn("No rule found: " + ruleName + " in namespace: " + namespace);
        }
        catch (ScriptException ex) {
            throw new RuleExecutionException(ex);
        }
    }

    /**
     * Both products and pools can carry attributes, we need to trigger rules for each.
     * In this map, pool attributes will override product attributes, should the same
     * key be set for both.
     *
     * @param product Product
     * @param pool Pool can be null.
     * @return Map of all attribute names and values. Pool attributes have priority.
     */
    protected Map<String, String> getFlattenedAttributes(Product product, Pool pool) {
        Map<String, String> allAttributes = new HashMap<String, String>();
        for (Attribute a : product.getAttributes()) {
            allAttributes.put(a.getName(), a.getValue());
        }
        if (pool != null) {
            for (Attribute a : pool.getAttributes()) {
                allAttributes.put(a.getName(), a.getValue());
            }

        }
        return allAttributes;
    }
}
