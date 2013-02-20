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
package org.candlepin.policy.test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.ProductCache;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.entitlement.ManifestEntitlementRules;
import org.candlepin.policy.js.pool.PoolHelper;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.DateSourceForTesting;
import org.candlepin.test.TestDateUtil;
import org.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * CandlepinConsumerTypeEnforcerTest
 */
public class ManifestEntitlementRulesTest extends DatabaseTestFixture {

    @Mock private ProductServiceAdapter productAdapter;
    @Mock private RulesCurator rulesCurator;
    @Mock private Config config;
    @Mock private ComplianceStatus compliance;

    private ManifestEntitlementRules enforcer;
    private Owner owner;
    private Consumer consumer;
    private JsRunner jsRules;
    private ProductCache productCache;

    @Before
    public void createEnforcer() throws Exception {
        MockitoAnnotations.initMocks(this);

        owner = createOwner();
        ownerCurator.create(owner);

        consumer = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(consumer.getType());
        consumerCurator.create(consumer);

        BufferedReader reader
            = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/rules/rules.js")));
        StringBuilder builder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            builder.append(line + "\n");
        }
        reader.close();

        Rules rules = mock(Rules.class);
        when(rules.getRules()).thenReturn(builder.toString());
        when(rulesCurator.getRules()).thenReturn(rules);
        when(rulesCurator.getUpdated()).thenReturn(TestDateUtil.date(2010, 1, 1));

        jsRules = new JsRunnerProvider(rulesCurator).get();

        when(config.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);
        productCache = new ProductCache(config, productAdapter);

        enforcer = new ManifestEntitlementRules(new DateSourceForTesting(2010, 1, 1),
            jsRules, productCache, i18n, config, consumerCurator);

    }

    @Test
    public void postEntitlement() {
        Consumer c = mock(Consumer.class);
        PoolHelper ph = mock(PoolHelper.class);
        Entitlement e = mock(Entitlement.class);
        ConsumerType type = mock(ConsumerType.class);
        Pool pool = mock(Pool.class);
        Product product = mock(Product.class);

        when(e.getPool()).thenReturn(pool);
        when(e.getConsumer()).thenReturn(c);
        when(c.getType()).thenReturn(type);
        when(type.isManifest()).thenReturn(true);
        when(pool.getProductId()).thenReturn("testProd");
        when(productAdapter.getProductById(eq("testProd"))).thenReturn(product);
        when(product.getAttributes()).thenReturn(new HashSet<ProductAttribute>());
        when(pool.getAttributes()).thenReturn(new HashSet<PoolAttribute>());

        assertEquals(ph, enforcer.postEntitlement(c, ph, e));
    }

    @Test
    public void preEntitlementIgnoresAttributeChecking() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = TestUtil.createConsumer();
        c.setFact("cpu.socket(s)", "12");
        c.getType().setManifest(true);

        Product prod = TestUtil.createProduct();
        prod.setAttribute("sockets", "2");
        Pool p = TestUtil.createPool(prod);

        ValidationResult results = enforcer.preEntitlement(c, p, 1);
        assertNotNull(results);
        assertTrue(results.getErrors().isEmpty());
    }

    @Test
    public void preEntitlementShouldNotAllowConsumptionFromDerivedPools() {
        Consumer c = TestUtil.createConsumer();
        c.getType().setManifest(true);

        Product prod = TestUtil.createProduct();
        Pool p = TestUtil.createPool(prod);
        p.setAttribute("pool_derived", "true");

        ValidationResult results = enforcer.preEntitlement(c, p, 1);
        assertNotNull(results);
        assertEquals(1, results.getErrors().size());
        ValidationError error = results.getErrors().get(0);
        assertEquals("pool.not.available.to.manifest.consumers", error.getResourceKey());
    }

    @Test
    public void preEntitlementShouldNotAllowOverConsumptionOfEntitlements() {
        Consumer c = TestUtil.createConsumer();
        c.getType().setManifest(true);

        Product prod = TestUtil.createProduct();
        Pool p = TestUtil.createPool(prod);
        p.setQuantity(5L);

        ValidationResult results = enforcer.preEntitlement(c, p, 10);
        assertNotNull(results);
        assertEquals(1, results.getErrors().size());
        ValidationError error = results.getErrors().get(0);
        assertEquals("rulefailed.no.entitlements.available", error.getResourceKey());
    }

}
