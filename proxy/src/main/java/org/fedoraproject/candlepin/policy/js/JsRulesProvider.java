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

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.guice.RulesReaderProvider;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;


import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * JsRulesProvider - reads/compiles our javascript rules and the standard js objects only
 * once across the jvm lifetime (and whenever the rules require a recompile), and creates
 * lightweight execution scopes per thread/request.
 */
public class JsRulesProvider implements Provider<JsRules> {
    private Logger log = Logger.getLogger(JsRulesProvider.class);

    private RulesReaderProvider rulesReaderProvider;
    
    private Script script;
    private Scriptable scope;

    /**
     * DynamicScopeContextFactory - replace the standard rhino context factory with one that
     * enables dynamic scopes. Dynamic scopes allow us to define a global var (ie pools) in
     * a thread/request local scope, and have the global scope (where our js code lives) be
     * able to read it.
     */
    static class DynamicScopeContextFactory extends ContextFactory {
        @Override
        protected boolean hasFeature(Context cx, int featureIndex) {
            if (featureIndex == Context.FEATURE_DYNAMIC_SCOPE) {
                return true;
            }
            return super.hasFeature(cx, featureIndex);
        }
    }

    static {
        ContextFactory.initGlobal(new DynamicScopeContextFactory());
    }
    
    @Inject
    public JsRulesProvider(RulesReaderProvider rulesReaderProvider) {
        this.rulesReaderProvider = rulesReaderProvider;

        /*
         * These are the expensive operations (initStandardObjects and compileReader/exec).
         *  We do them once here, and define this provider as a singleton, so it's only
         *  done at provider creation or whenever rules are refreshed.
         */
        Context context = Context.enter();
        context.setOptimizationLevel(9);
        scope = context.initStandardObjects(null, true);
        try {
            script = context.compileReader(rulesReaderProvider.get(), "rules", 1, null);
            script.exec(context, scope);
            ((ScriptableObject) scope).sealObject();
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        finally {
            Context.exit();
        }

    }
    
    public JsRules get() {
        /*
         * Create a new thread/request local javascript scope for the JsRules,
         * based on the preinitialized global one (which contains our js rules).
         */
        Context context = Context.enter();
        Scriptable rulesScope = context.newObject(scope);
        rulesScope.setPrototype(scope);
        rulesScope.setParentScope(null);
        Context.exit();    
        
        return new JsRules(rulesScope);
    }
    
}
