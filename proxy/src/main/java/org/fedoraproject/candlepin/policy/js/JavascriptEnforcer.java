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


import java.io.Reader;
import java.io.StringReader;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.DateSource;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.RulesCurator;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.ValidationError;

import com.google.inject.Inject;

public class JavascriptEnforcer implements Enforcer {
    
    private static Logger log = Logger.getLogger(JavascriptEnforcer.class);
    private DateSource dateSource;
    private RulesCurator rulesCurator;
    private PreEntHelper preHelper;
    private PostEntHelper postHelper;

    private ScriptEngine jsEngine;

    private static final String PRE_PREFIX = "pre_";
    private static final String POST_PREFIX = "post_";
    private static final String GLOBAL_PRE_FUNCTION = PRE_PREFIX + "global";
    private static final String GLOBAL_POST_FUNCTION = POST_PREFIX + "global";
    
    @Inject
    public JavascriptEnforcer(DateSource dateSource, 
            RulesCurator rulesCurator, PreEntHelper preHelper,
            PostEntHelper postHelper) {
        this.dateSource = dateSource;
        this.rulesCurator = rulesCurator;
        this.preHelper = preHelper;
        this.postHelper = postHelper;


        ScriptEngineManager mgr = new ScriptEngineManager();
        jsEngine = mgr.getEngineByName("JavaScript");
        if (jsEngine == null) {
            throw new RuntimeException("No Javascript engine found");
        }

        try {
            Reader reader = new StringReader(this.rulesCurator.getRules().getRules());
            jsEngine.eval(reader);
        }
        catch (ScriptException ex) {
            throw new RuleParseException(ex);
        }
    }


    @Override
    public PreEntHelper pre(Consumer consumer, EntitlementPool entitlementPool) {

        runPre(preHelper, consumer, entitlementPool);

        if (entitlementPool.isExpired(dateSource)) {
            preHelper.getResult().addError(new ValidationError("Entitlements for " +
                    entitlementPool.getProduct().getName() +
                    " expired on: " + entitlementPool.getEndDate()));
            return preHelper;
        }

        return preHelper;
    }

    private void runPre(PreEntHelper preHelper, Consumer consumer,
            EntitlementPool pool) {
        Invocable inv = (Invocable)jsEngine;
        Product p = pool.getProduct();

        // Provide objects for the script:
        jsEngine.put("consumer", new ReadOnlyConsumer(consumer));
        jsEngine.put("product", new ReadOnlyProduct(pool.getProduct()));
        jsEngine.put("pool", new ReadOnlyEntitlementPool(pool));
        jsEngine.put("pre", preHelper);

        try {
            inv.invokeFunction(PRE_PREFIX + p.getLabel());
        }
        catch (NoSuchMethodException e) {
            // No method for this product, try to find a global function, if neither exists
            // this is ok and we'll just carry on.
            try {
                inv.invokeFunction(GLOBAL_PRE_FUNCTION);
            }
            catch (NoSuchMethodException ex) {
                // This is fine.
            }
            catch (ScriptException ex) {
                throw new RuleExecutionException(ex);
            }
        }
        catch (ScriptException e) {
            throw new RuleExecutionException(e);
        }
    }

    @Override
    public PostEntHelper post(Entitlement ent) {
        postHelper.init(ent);
        runPost(postHelper, ent);
        return(postHelper);
    }

    private void runPost(PostEntHelper postHelper, Entitlement ent) {
        Invocable inv = (Invocable)jsEngine;
        EntitlementPool pool = ent.getPool();
        Consumer c = ent.getConsumer();
        Product p = pool.getProduct();

        // Provide objects for the script:
        jsEngine.put("consumer", new ReadOnlyConsumer(c));
        jsEngine.put("product", new ReadOnlyProduct(pool.getProduct()));
        jsEngine.put("post", postHelper);
        jsEngine.put("entitlement", new ReadOnlyEntitlement(ent));

        try {
            inv.invokeFunction(POST_PREFIX + p.getLabel());
        }
        catch (NoSuchMethodException e) {
            // No method for this product, try to find a global function, if neither exists
            // this is ok and we'll just carry on.
            try {
                inv.invokeFunction(GLOBAL_POST_FUNCTION);
            }
            catch (NoSuchMethodException ex) {
                // This is fine.
            }
            catch (ScriptException ex) {
                throw new RuleExecutionException(ex);
            }

        }
        catch (ScriptException e) {
            throw new RuleExecutionException(e);
        }
    }


}
