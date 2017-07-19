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
package org.candlepin.policy.js.entitlement;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.jackson.ProductCachedSerializationModule;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.JsRunnerRequestCache;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.MockResultIterator;
import org.candlepin.test.TestDateUtil;
import org.candlepin.test.TestUtil;
import org.candlepin.util.DateSourceImpl;
import org.candlepin.util.Util;

import com.google.inject.Provider;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18nFactory;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class EntitlementRulesTestFixture {
    protected Enforcer enforcer;

    @Mock
    protected RulesCurator rulesCurator;
    @Mock
    protected ProductServiceAdapter prodAdapter;
    @Mock
    protected Configuration config;
    @Mock
    protected ConsumerCurator consumerCurator;
    @Mock
    protected ComplianceStatus compliance;
    @Mock
    protected PoolManager poolManagerMock;
    @Mock
    protected EntitlementCurator entCurMock;
    @Mock
    protected OwnerProductCurator ownerProductCuratorMock;
    @Mock
    private Provider<JsRunnerRequestCache> cacheProvider;
    @Mock
    private JsRunnerRequestCache cache;
    @Mock private ProductCurator productCurator;

    @Mock
    protected PoolCurator poolCurator;

    protected Owner owner;
    protected Consumer consumer;
    protected String productId = "a-product";
    protected PoolRules poolRules;

    protected Map<String, Product> mockedProductMap;

    @Before
    public void createEnforcer() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(config.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);

        InputStream is = this.getClass().getResourceAsStream(
            RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));

        when(rulesCurator.getRules()).thenReturn(rules);
        when(rulesCurator.getUpdated()).thenReturn(
            TestDateUtil.date(2010, 1, 1));
        when(cacheProvider.get()).thenReturn(cache);

        JsRunner jsRules = new JsRunnerProvider(rulesCurator, cacheProvider).get();
        enforcer = new EntitlementRules(
            new DateSourceImpl(),
            jsRules,
            I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK),
            config,
            consumerCurator,
            poolCurator,
            productCurator,
            new RulesObjectMapper(new ProductCachedSerializationModule(productCurator))
        );

        owner = new Owner();
        consumer = new Consumer("test consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));

        poolRules = new PoolRules(poolManagerMock, config, entCurMock, ownerProductCuratorMock,
                productCurator);

        this.mockedProductMap = new HashMap<String, Product>();
    }

    protected Subscription createVirtLimitSub(String productId, int quantity,
        String virtLimit) {
        Product product = TestUtil.createProduct(productId, productId);
        product.setAttribute(Product.Attributes.VIRT_LIMIT, virtLimit);
        this.mockProducts(owner, product);
        Subscription s = TestUtil.createSubscription(owner, product);
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
        Product product = TestUtil.createProduct(productId, "A virt_limit product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.setAttribute(Product.Attributes.VIRT_LIMIT, "10");
        pool.setId("fakeid" + TestUtil.randomInt());
        return pool;
    }

    protected void mockProducts(Owner owner, Map<String, Product> products) {
        final Map<String, Product> productMap = this.mockedProductMap;
        productMap.putAll(products);

        when(ownerProductCuratorMock.getProductById(eq(owner), any(String.class)))
            .thenAnswer(new Answer<Product>() {
                @Override
                public Product answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    String pid = (String) args[1];

                    return productMap.get(pid);
                }
            });

        when(ownerProductCuratorMock.getProductsByIds(eq(owner), any(Collection.class)))
            .thenAnswer(new Answer<CandlepinQuery<Product>>() {
                @Override
                public CandlepinQuery<Product> answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    Collection<String> pids = (Collection<String>) args[1];
                    List<Product> output = new LinkedList<Product>();

                    for (String pid : pids) {
                        Product product = productMap.get(pid);

                        if (product != null) {
                            output.add(product);
                        }
                    }

                    CandlepinQuery cqmock = mock(CandlepinQuery.class);
                    when(cqmock.list()).thenReturn(output);
                    when(cqmock.iterator()).thenReturn(output.iterator());
                    when(cqmock.iterate()).thenReturn(new MockResultIterator(output.iterator()));

                    return cqmock;
                }
            });
    }

    protected void mockProducts(Owner owner, Product... products) {
        Map<String, Product> productMap = new HashMap<String, Product>();

        for (Product product : products) {
            productMap.put(product.getId(), product);
        }

        this.mockProducts(owner, productMap);
    }

    protected void mockProducts(Owner owner, Collection<Product> products) {
        Map<String, Product> productMap = new HashMap<String, Product>();

        for (Product product : products) {
            productMap.put(product.getId(), product);
        }

        this.mockProducts(owner, productMap);
    }
}
