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

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.RulesCurator;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.ValidationError;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.util.DateSource;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import org.apache.log4j.Logger;

import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * Enforces the Javascript Rules definition.
 */
public class JavascriptEnforcer implements Enforcer {

    private static Logger log = Logger.getLogger(JavascriptEnforcer.class);
    private DateSource dateSource;

    private ProductServiceAdapter prodAdapter;

    private ScriptEngine jsEngine;

    private static final String PRE_PREFIX = "pre_";
    private static final String POST_PREFIX = "post_";
    private static final String SELECT_POOL_PREFIX = "select_pool_";
    private static final String GLOBAL_SELECT_POOL_FUNCTION = SELECT_POOL_PREFIX +
        "global";
    private static final String GLOBAL_PRE_FUNCTION = PRE_PREFIX + "global";
    private static final String GLOBAL_POST_FUNCTION = POST_PREFIX + "global";


    @Inject
    public JavascriptEnforcer(DateSource dateSource, @Named("RulesReader") Reader rulesReader,
        PreEntHelper preHelper, PostEntHelper postHelper,
        ProductServiceAdapter prodAdapter, ScriptEngine jsEngine) {
        this.dateSource = dateSource;

        this.prodAdapter = prodAdapter;
        this.jsEngine = jsEngine;
        
        if (jsEngine == null) {
            throw new RuntimeException("No Javascript engine");
        }

        try {
            this.jsEngine.eval(rulesReader);
        }
        catch (ScriptException ex) {
            throw new RuleParseException(ex);
        }
    }

    @Override
    public PreEntHelper pre(Consumer consumer, Pool entitlementPool) {

        PreEntHelper preHelper = runPre(consumer, entitlementPool);

        if (entitlementPool.isExpired(dateSource)) {
            preHelper.getResult().addError(
                new ValidationError("Entitlements for " +
                    entitlementPool.getProductId() + " expired on: " +
                    entitlementPool.getEndDate()));
            return preHelper;
        }

        return preHelper;
    }

    private PreEntHelper runPre(Consumer consumer, Pool pool) {
        Invocable inv = (Invocable) jsEngine;
        String productId = pool.getProductId();
        PreEntHelper preHelper = new PreEntHelper();

        // Provide objects for the script:
        jsEngine.put("consumer", new ReadOnlyConsumer(consumer));
        jsEngine.put("product", new ReadOnlyProduct(prodAdapter
            .getProductById(productId)));
        jsEngine.put("pool", new ReadOnlyEntitlementPool(pool));
        jsEngine.put("pre", preHelper);

        log.debug("Running pre-entitlement rules for: " + consumer.getUuid() +
            " product: " + pool.getProductId());
        try {
            inv.invokeFunction(PRE_PREFIX + productId);
            log.debug("Ran rule: " + PRE_PREFIX + productId);
        }
        catch (NoSuchMethodException e) {
            // No method for this product, try to find a global function, if
            // neither exists this is ok and we'll just carry on.
            try {
                inv.invokeFunction(GLOBAL_PRE_FUNCTION);
                log.debug("Ran rule: " + GLOBAL_PRE_FUNCTION);
            }
            catch (NoSuchMethodException ex) {
                // This is fine, I hope...
                log.warn("No default rule found: " + GLOBAL_PRE_FUNCTION);
            }
            catch (ScriptException ex) {
                throw new RuleExecutionException(ex);
            }
        }
        catch (ScriptException e) {
            throw new RuleExecutionException(e);
        }
        return preHelper;
    }

    @Override
    public PostEntHelper post(Entitlement ent) {
        PostEntHelper postHelper = new PostEntHelper();
        postHelper.init(ent);
        runPost(postHelper, ent);
        return postHelper;
    }

    private void runPost(PostEntHelper postHelper, Entitlement ent) {
        Invocable inv = (Invocable) jsEngine;
        Pool pool = ent.getPool();
        Consumer c = ent.getConsumer();
        String productId = pool.getProductId();

        // Provide objects for the script:
        jsEngine.put("consumer", new ReadOnlyConsumer(c));
        jsEngine.put("product", new ReadOnlyProduct(prodAdapter
            .getProductById(productId)));
        jsEngine.put("post", postHelper);
        jsEngine.put("entitlement", new ReadOnlyEntitlement(ent));

        log.debug("Running post-entitlement rules for: " + c.getUuid() +
            " product: " + pool.getProductId());

        try {
            inv.invokeFunction(POST_PREFIX + productId);
            log.debug("Ran rule: " + POST_PREFIX + productId);
        }
        catch (NoSuchMethodException e) {
            // No method for this product, try to find a global function, if
            // neither exists this is ok and we'll just carry on.
            try {
                inv.invokeFunction(GLOBAL_POST_FUNCTION);
            }
            catch (NoSuchMethodException ex) {
                // This is fine, I hope...
                log.warn("No default rule found: " + GLOBAL_POST_FUNCTION);
            }
            catch (ScriptException ex) {
                throw new RuleExecutionException(ex);
            }

        }
        catch (ScriptException e) {
            throw new RuleExecutionException(e);
        }
    }

    public Pool selectBestPool(Consumer consumer, String productId,
        List<Pool> pools) {

        Invocable inv = (Invocable) jsEngine;

        log.info("Selecting best entitlement pool for product: " + productId);
        List<ReadOnlyEntitlementPool> readOnlyPools = new LinkedList<ReadOnlyEntitlementPool>();
        for (Pool p : pools) {
            log.info("   " + p);
            readOnlyPools.add(new ReadOnlyEntitlementPool(p));
        }

        // Provide objects for the script:
        jsEngine.put("pools", readOnlyPools);

        ReadOnlyEntitlementPool result = null;
        try {
            result = (ReadOnlyEntitlementPool) inv
                .invokeFunction(SELECT_POOL_PREFIX + productId);
            log.info("Excuted javascript rule: " + SELECT_POOL_PREFIX +
                productId);
        }
        catch (NoSuchMethodException e) {
            // No method for this product, try to find a global function, if
            // neither exists this is ok and we'll just carry on.
            try {
                result = (ReadOnlyEntitlementPool) inv
                    .invokeFunction(GLOBAL_SELECT_POOL_FUNCTION);
                log.info("Excuted javascript rule: " +
                    GLOBAL_SELECT_POOL_FUNCTION);
            }
            catch (NoSuchMethodException ex) {
                log.warn("No default rule found: " +
                    GLOBAL_SELECT_POOL_FUNCTION);
                log.warn("Resorting to default pool selection behavior.");
                return selectBestPoolDefault(pools);
            }
            catch (ScriptException ex) {
                throw new RuleExecutionException(ex);
            }
        }
        catch (ScriptException e) {
            throw new RuleExecutionException(e);
        }

        if (pools.size() > 0 && result == null) {
            throw new RuleExecutionException(
                "Rule did not select a pool for product: " + productId);
        }

        for (Pool p : pools) {
            if (p.getId().equals(result.getId())) {
                log.debug("Best pool: " + p);
                return p;
            }
        }

        return null;
    }

    /**
     * Default behavior if no product specific and no global pool select rules
     * exist.
     * 
     * @param pools
     *            Pools to choose from.
     * @return First pool in the list. (default behavior)
     */
    private Pool selectBestPoolDefault(List<Pool> pools) {
        if (pools.size() > 0) {
            return pools.get(0);
        }
        return null;
    }
}
