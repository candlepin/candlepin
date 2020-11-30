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

import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class JsRunnerFactory implements FactoryBean<JsRunner> {

    private static Logger log = LoggerFactory.getLogger(JsRunnerFactory.class);

    private RulesCurator rulesCurator;
    private JsRunnerRequestCacheFactory cacheProvider;
    private Script script;
    private Scriptable scope;
    /**
     * This date is basically a version of the rules that this
     * JSRunnerProvider compiled. Note that in clustered environment,
     * multiple nodes must compile same version of rules. Thats why
     * this JsRunnerProvider uses database to make sure it compiles and
     * uses the database dictated version.
     */
    private volatile Date currentRulesUpdated;

    // Store the version and source of the compiled rules:
    private String rulesVersion;
    private Rules.RulesSourceEnum rulesSource;

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
        ContextFactory.initGlobal(new JsRunnerFactory.DynamicScopeContextFactory());
    }

    @Autowired
    public JsRunnerFactory(RulesCurator rulesCurator, JsRunnerRequestCacheFactory cacheProvider) {
        this.rulesCurator = rulesCurator;
        this.cacheProvider = cacheProvider;

        log.debug("Compiling rules for initial load");
        this.rulesCurator.updateDbRules();
        this.compileRules();
        //initializeRules();
    }

//    @Transactional
//    public void initializeRules() {
//        this.rulesCurator.updateDbRules();
//        this.compileRules();
//    }

    /**
     * These are the expensive operations (initStandardObjects and compileReader/exec).
     *  We do them once here, and define this provider as a singleton, so it's only
     *  done at provider creation or whenever rules are refreshed.
     *
     */
    public void compileRules() {
        compileRules(false);
    }

    public void compileRules(boolean forceRefresh) {
        scriptLock.writeLock().lock();
        try {
            // Check to see if we need to recompile. we do this inside the write lock
            // just to avoid race conditions where we might double compile
            Date newUpdated = rulesCurator.getUpdated();
            if (!forceRefresh && newUpdated.equals(this.currentRulesUpdated)) {
                return;
            }

            log.info("Recompiling rules with timestamp: {}", newUpdated);

            Context context = Context.enter();
            context.setOptimizationLevel(9);
            scope = context.initStandardObjects(null, true);
            try {
                Rules rules = rulesCurator.getRules();
                rulesVersion = rules.getVersion();
                rulesSource = rules.getRulesSource();
                script = context.compileString(
                        rules.getRules(), "rules", 1, null);
                script.exec(context, scope);
                ((ScriptableObject) scope).sealObject();
                this.currentRulesUpdated = newUpdated;
            }
            finally {
                Context.exit();
            }
        }
        finally {
            scriptLock.writeLock().unlock();
        }
    }


    @Override
    public JsRunner getObject() {
        /**
         * Even though JsRunnerProvider is singleton, the
         * following cache is being retrieved fresh for
         * every new HTTP Request
         */
        JsRunnerRequestCache cache = null;
        try {
            cache = cacheProvider.getObject();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        Date updated = cache.getUpdated();
        if (updated == null) {
            updated = rulesCurator.getUpdated();
            cache.setUpdated(updated);
        }
        /*
         * Create a new thread/request local javascript scope for the JsRules,
         * based on the preinitialized global one (which contains our js rules).
         */
        // Avoid a write lock if we can
        if (!updated.equals(this.currentRulesUpdated)) {
            compileRules();
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
    public String getRulesVersion() {
        if (rulesVersion == null) {
            compileRules();
        }
        return rulesVersion;
    }

    public Rules.RulesSourceEnum getRulesSource() {
        if (rulesSource == null) {
            compileRules();
        }
        return rulesSource;
    }

    @Override
    public Class<?> getObjectType() {
        return JsRunner.class;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }
}
