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

import org.candlepin.auth.Principal;
import org.candlepin.auth.SystemPrincipal;
import org.candlepin.model.RulesCurator;

import com.google.inject.Inject;
import com.google.inject.Provider;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Reads/compiles our javascript rules and the standard js objects only
 * once across the JVM lifetime (and whenever the rules require a recompile), and creates
 * lightweight execution scopes per thread/request.
 */
public class JsRunnerProvider implements Provider<JsRunner> {
    private static Logger log = LoggerFactory.getLogger(JsRunnerProvider.class);

    private RulesCurator rulesCurator;

    private Script script;
    private Scriptable scope;
    private Date updated;
    // Use this lock to access script, scope and updated
    private ReadWriteLock scriptLock = new ReentrantReadWriteLock();

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
    public JsRunnerProvider(RulesCurator rulesCurator) {
        this.rulesCurator = rulesCurator;

        log.debug("Compiling rules for initial load");
        this.rulesCurator.updateDbRules();
        this.compileRules(this.rulesCurator);
    }

    /**
     * These are the expensive operations (initStandardObjects and compileReader/exec).
     *  We do them once here, and define this provider as a singleton, so it's only
     *  done at provider creation or whenever rules are refreshed.
     *
     * @param rulesCurator
     */
    private void compileRules(RulesCurator rulesCurator) {
        scriptLock.writeLock().lock();
        try {
            // XXX: we need a principal to access the rules,
            // but pushing and popping system principal could be a bad idea
            Principal systemPrincipal = new SystemPrincipal();
            ResteasyProviderFactory.pushContext(Principal.class, systemPrincipal);
            // Check to see if we need to recompile. we do this inside the write lock
            // just to avoid race conditions where we might double compile
            Date newUpdated = rulesCurator.getUpdated();
            if (newUpdated.equals(this.updated)) {
                return;
            }

            log.debug("Recompiling rules with timestamp: " + newUpdated);

            Context context = Context.enter();
            context.setOptimizationLevel(9);
            scope = context.initStandardObjects(null, true);
            try {
                script = context.compileString(
                    rulesCurator.getRules().getRules(), "rules", 1, null);
                script.exec(context, scope);
                ((ScriptableObject) scope).sealObject();
                this.updated = newUpdated;
            }
            finally {
                Context.exit();
            }
        }
        finally {
            ResteasyProviderFactory.popContextData(Principal.class);
            scriptLock.writeLock().unlock();
        }
    }

    public JsRunner get() {
        /*
         * Create a new thread/request local javascript scope for the JsRules,
         * based on the preinitialized global one (which contains our js rules).
         */
        // Avoid a write lock if we can
        if (!rulesCurator.getUpdated().equals(this.updated)) {
            compileRules(this.rulesCurator);
        }
        Scriptable rulesScope;
        scriptLock.readLock().lock();
        try {
            Context context = Context.enter();
            rulesScope = context.newObject(scope);
            rulesScope.setPrototype(scope);
            rulesScope.setParentScope(null);
            Context.exit();
        }
        finally {
            scriptLock.readLock().unlock();
        }

        return new JsRunner(rulesScope);
    }

}
