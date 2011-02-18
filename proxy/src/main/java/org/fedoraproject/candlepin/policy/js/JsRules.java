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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.guice.RulesReaderProvider;
import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;

/**
 *
 */
public abstract class JsRules {
    private Logger log = Logger.getLogger(JsRules.class);

    private RulesReaderProvider rulesReaderProvider;
    private Object rulesNameSpace;
    private String namespace;
    
    private Context context;
    private Script script;
    private ScriptableObject scope;

    private boolean initialized = false;
    
    public JsRules(RulesReaderProvider rulesReaderProvider,
        String namespace) {
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

            try {
                context = Context.enter();
                script = context.compileReader(rulesReaderProvider.get(), "rules", 1, null);
                scope = context.initStandardObjects();
                script.exec(context, scope);
                Object func = scope.getProperty(scope, this.namespace);
                this.rulesNameSpace = unwrapReturnValue(((Function) func).call(context,
                    scope, scope, Context.emptyArgs));
                
                this.initialized = true;
                this.rulesInit();
            }
            catch (RhinoException ex) {
                this.initialized = false;
                throw new RuleParseException(ex);
            }
            catch (NoSuchMethodException ex) {
                this.initialized = false;
                throw new RuleParseException(ex);
            }
            catch (IOException ex) {
                this.initialized = false;
                throw new RuleParseException(ex);
            }
        }
    }

    /**
     * This is for subclasses to perform whatever initialization they need.
     */
    protected void rulesInit() throws NoSuchMethodException, RhinoException { }
    
    Object unwrapReturnValue(Object result) {
        if (result instanceof Wrapper) {
            result = ((Wrapper) result).unwrap();
        }

        return result instanceof Undefined ? null : result;
    }

    protected <T> T invokeMethod(String method) throws NoSuchMethodException,
            RhinoException {
        this.init();

        Scriptable localScope = Context.toObject(this.rulesNameSpace, scope);
        Object func = scope.getProperty(localScope, method);
        if (!(func instanceof Function)) {
            throw new NoSuchMethodException("no such javascript method: " + method); 
        }
        return (T) unwrapReturnValue(((Function) func).call(context, scope, localScope,
            Context.emptyArgs));
    }

    protected <T> T invokeMethod(String method, Map<String, Object> args)
        throws NoSuchMethodException, RhinoException {
        for (String key : args.keySet()) {
            scope.put(key, scope, args.get(key));
        }
        return invokeMethod(method);
    }

    protected void invokeRule(String ruleName) {
        log.debug("Running rule: " + ruleName + " in namespace: " + namespace);

        try {
            this.invokeMethod(ruleName);
        }
        catch (NoSuchMethodException ex) {
            log.warn("No rule found: " + ruleName + " in namespace: " + namespace);
        }
        catch (RhinoException ex) {
            throw new RuleExecutionException(ex);
        }
    }
    
    protected void invokeRule(String ruleName, Map<String, Object> args) {
        for (String key : args.keySet()) {
            scope.put(key, scope, args.get(key));
        }
        invokeRule(ruleName);
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
    
    protected ReadOnlyPool[] convertArray(Object output) {
        return (ReadOnlyPool[]) Context.jsToJava(output, ReadOnlyPool[].class);
    }
}
