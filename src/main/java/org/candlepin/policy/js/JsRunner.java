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
package org.candlepin.policy.js;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JsRunner - Responsible for running the javascript rules methods in all namespaces.
 * Used by the various "Rules" classes.
 */
public class JsRunner {
    private static Logger log = LoggerFactory.getLogger(JsRunner.class);

    private Object rulesNameSpace;
    private String namespace;
    private Scriptable scope;

    private boolean initialized = false;

    public JsRunner(Scriptable scope) {
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

    public void reinitTo(String namespace) {
        initialized = false;
        init(namespace);
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
    public <T> T invokeMethod(String method, JsContext context)
        throws NoSuchMethodException, RhinoException {
        context.applyTo(scope);
        return (T) invokeMethod(method);
    }

    public <T> T invokeRule(String ruleName) {
        log.debug("Running rule: " + ruleName + " in namespace: " + namespace);

        T returner = null;
        try {
            returner = this.invokeMethod(ruleName);
        }
        catch (NoSuchMethodException ex) {
            log.info("No rule found: " + ruleName + " in namespace: " + namespace);
        }
        catch (RhinoException ex) {
            throw new RuleExecutionException(ex);
        }
        return returner;
    }

    public <T> T invokeRule(String ruleName, JsContext context) {
        context.applyTo(scope);
        return invokeRule(ruleName);
    }

    public <T extends Object> T runJsFunction(Class<T> clazz, String function,
        JsContext context) {
        T returner = null;
        try {
            returner = invokeMethod(function, context);
        }
        catch (NoSuchMethodException e) {
            log.warn("No javascript method found: " + function);
        }
        catch (RhinoException e) {
            throw new RuleExecutionException(e);
        }
        return returner;
    }
}
