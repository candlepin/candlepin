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
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;

/**
 * JsRules - javascript runner
 */
public class JsRules {
    private static Logger log = Logger.getLogger(JsRules.class);

    private Object rulesNameSpace;
    private String namespace;
    private Scriptable scope;

    private boolean initialized = false;

    public JsRules(Scriptable scope) {
        this.scope = scope;
    }

    /**
     * initialize the javascript rules for the provided namespace. you must run this
     * before trying to run a javascript rule or method.
     *
     * @param namespace the javascript rules namespace containing the rules type you want
     */
    public void init(String namespace) {
        this.namespace = namespace;

        if (!initialized) {

            Context context = Context.enter();
            try {
                Object func = ScriptableObject.getProperty(scope, namespace);
                this.rulesNameSpace = unwrapReturnValue(((Function) func).call(context,
                    scope, scope, Context.emptyArgs));

                this.initialized = true;
            }
            catch (RhinoException ex) {
                this.initialized = false;
                throw new RuleParseException(ex);
            }
            finally {
                Context.exit();
            }
        }
    }

    Object unwrapReturnValue(Object result) {
        if (result instanceof Wrapper) {
            result = ((Wrapper) result).unwrap();
        }

        return result instanceof Undefined ? null : result;
    }

    @SuppressWarnings("unchecked")
    public <T> T invokeMethod(String method) throws NoSuchMethodException,
            RhinoException {
        Scriptable localScope = Context.toObject(this.rulesNameSpace, scope);
        Object func = ScriptableObject.getProperty(localScope, method);
        if (!(func instanceof Function)) {
            throw new NoSuchMethodException("no such javascript method: " + method);
        }
        Context context = Context.enter();
        try {
            return (T) unwrapReturnValue(((Function) func).call(context, scope, localScope,
                Context.emptyArgs));
        }
        finally {
            Context.exit();
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T invokeMethod(String method, Map<String, Object> args)
        throws NoSuchMethodException, RhinoException {
        for (Entry<String, Object> entry : args.entrySet()) {
            scope.put(entry.getKey(), scope, entry.getValue());
        }
        return (T) invokeMethod(method);
    }

    public void invokeRule(String ruleName) {
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

    public void invokeRule(String ruleName, Map<String, Object> args) {
        for (Entry<String, Object> entry : args.entrySet()) {
            scope.put(entry.getKey(), scope, entry.getValue());

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
    public Map<String, String> getFlattenedAttributes(Product product, Pool pool) {
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

    public ReadOnlyPool[] convertArray(Object output) {
        return (ReadOnlyPool[]) Context.jsToJava(output, ReadOnlyPool[].class);
    }

    public Map<ReadOnlyPool, Integer> convertMap(Object output) {
        Map<ReadOnlyPool, Integer> toReturn = new HashMap<ReadOnlyPool, Integer>();

        Map<ReadOnlyPool, Double> result =
            (Map<ReadOnlyPool, Double>) Context.jsToJava(output, Map.class);

        for (ReadOnlyPool pool : result.keySet()) {
            try {
                Integer count = (Integer) result.get(pool).intValue();
                toReturn.put(pool, count);
            }
            catch (ClassCastException e) {
                // this is safe, as we'll have javascript specific ids in here
                // that we can ignore
                log.debug("CONVERT id is not readonly pool, ignoring: " + e);
            }
        }

        if (toReturn.isEmpty()) {
            return null;
        }

        log.debug("CONVERT returning hashmap");
        return toReturn;
    }
}
