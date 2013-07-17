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
package org.candlepin.policy.js.entitlement.test;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Locale;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.Subscription;
import org.candlepin.policy.js.AttributeHelper;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.ProductCache;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.TestDateUtil;
import org.candlepin.test.TestUtil;
import org.candlepin.util.DateSourceImpl;
import org.candlepin.util.Util;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xnap.commons.i18n.I18nFactory;

public class EntitlementRulesTestFixture {
    protected Enforcer enforcer;
    @Mock
    protected RulesCurator rulesCurator;
    @Mock
    protected ProductServiceAdapter prodAdapter;
    @Mock
    protected Config config;
    @Mock
    protected ConsumerCurator consumerCurator;
    @Mock
    protected ComplianceStatus compliance;
    @Mock
    protected PoolManager poolManagerMock;

    @Mock
    protected PoolCurator poolCurator;

    protected Owner owner;
    protected Consumer consumer;
    protected String productId = "a-product";
    protected PoolRules poolRules;
    protected ProductCache productCache;
    protected AttributeHelper attrHelper;

    @Before
    public void createEnforcer() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(config.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(
            100);
        this.productCache = new ProductCache(config, this.prodAdapter);

        InputStream is = this.getClass().getResourceAsStream(
            RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));

        when(rulesCurator.getRules()).thenReturn(rules);
        when(rulesCurator.getUpdated()).thenReturn(
            TestDateUtil.date(2010, 1, 1));

        JsRunner jsRules = new JsRunnerProvider(rulesCurator).get();
        enforcer = new EntitlementRules(new DateSourceImpl(), jsRules,
            productCache, I18nFactory.getI18n(getClass(), Locale.US,
                I18nFactory.FALLBACK), config, consumerCurator, poolCurator);

        owner = new Owner();
        consumer = new Consumer("test consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));

        attrHelper = new AttributeHelper();

        poolRules = new PoolRules(poolManagerMock, productCache, config);
    }

    protected Subscription createVirtLimitSub(String productId, int quantity,
        String virtLimit) {
        Product product = new Product(productId, productId);
        product.setAttribute("virt_limit", virtLimit);
        when(prodAdapter.getProductById(productId)).thenReturn(product);
        Subscription s = TestUtil.createSubscription(product);
        s.setQuantity(new Long(quantity));
        s.setId("subId");
        return s;
    }

    protected Pool createPool(Owner owner, Product product) {
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("fakeid" + TestUtil.randomInt());
        return pool;
    }

    protected Pool setupVirtLimitPool() {
        Product product = new Product(productId, "A virt_limit product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.addAttribute(new PoolAttribute("virt_limit", "10"));
        pool.setId("fakeid" + TestUtil.randomInt());
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);
        return pool;
    }
}
