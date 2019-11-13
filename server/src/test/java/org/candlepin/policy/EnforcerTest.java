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
package org.candlepin.policy;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.jackson.ProductCachedSerializationModule;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.JsRunnerRequestCache;
import org.candlepin.policy.js.RuleExecutionException;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.DateSourceForTesting;
import org.candlepin.test.TestDateUtil;
import org.candlepin.test.TestUtil;

import com.google.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xnap.commons.i18n.I18n;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Date;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EnforcerTest extends DatabaseTestFixture {
    @Inject private I18n i18n;

    @Mock private RulesCurator rulesCurator;
    @Mock private Configuration config;
    @Mock private Provider<JsRunnerRequestCache> cacheProvider;
    @Mock private JsRunnerRequestCache cache;
    @Mock private ProductCurator mockProductCurator;
    @Mock private OwnerCurator mockOwnerCurator;
    @Mock private EnvironmentCurator mockEnvironmentCurator;

    @Inject private ModelTranslator translator;
    private Enforcer enforcer;
    private Owner owner;

    private static final String PRODUCT_CPULIMITED = "CPULIMITED001";

    @BeforeEach
    public void createEnforcer() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(config.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);

        owner = createOwner();
        ownerCurator.create(owner);

        BufferedReader reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("/rules/test-rules.js")));
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
        when(cacheProvider.get()).thenReturn(cache);

        JsRunner jsRules = new JsRunnerProvider(rulesCurator, cacheProvider).get();

        translator = new StandardTranslator(consumerTypeCurator, mockEnvironmentCurator, mockOwnerCurator);

        enforcer = new EntitlementRules(
            new DateSourceForTesting(2010, 1, 1), jsRules, i18n, config, consumerCurator, consumerTypeCurator,
            mockProductCurator,
            new RulesObjectMapper(new ProductCachedSerializationModule(mockProductCurator)), translator);
    }

    // This exception should mention wrapping a MissingFactException
    @Test
    public void testRuleFailsWhenConsumerDoesntHaveFact() {
        Product product = TestUtil.createProduct("a-product", "A product for testing");
        product.setAttribute(PRODUCT_CPULIMITED, "2");
        product = this.createProduct(product, owner);

        Consumer consumer = this.createConsumer(owner);

        Product finalProduct = product;
        Pool entitlementPool = entitlementPoolWithMembersAndExpiration(
            owner, finalProduct, 1, 2, expiryDate(2000, 1, 1)
        );
        assertThrows(RuleExecutionException.class, () ->
            enforcer.preEntitlement(consumer, entitlementPool, 1)
        );
    }

    private Date expiryDate(int year, int month, int day) {
        return TestDateUtil.date(year, month, day);
    }

    private Pool entitlementPoolWithMembersAndExpiration(Owner theOwner, Product product,
        final int currentMembers, final int maxMembers, Date expiry) {
        Pool p = createPool(theOwner, product,
            Long.valueOf(maxMembers), new Date(), expiry);

        for (int i = 0; i < currentMembers; i++) {
            Consumer c = createConsumer(theOwner);
            Entitlement e = createEntitlement(theOwner, c, p, null);
            e.setQuantity(1);
            entitlementCurator.create(e);
            p.getEntitlements().add(e);
            poolCurator.merge(p);
        }
        poolCurator.refresh(p);
        return p;
    }
}
