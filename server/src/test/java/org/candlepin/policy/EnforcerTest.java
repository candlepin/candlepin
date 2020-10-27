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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.ProductManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerProductCurator;
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
import org.candlepin.policy.js.entitlement.EntitlementRules.Rule;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.DateSourceForTesting;
import org.candlepin.test.TestUtil;

import com.google.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xnap.commons.i18n.I18n;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

public class EnforcerTest extends DatabaseTestFixture {
    @Inject private I18n i18n;

    @Mock private ProductServiceAdapter productAdapter;
    @Mock private RulesCurator rulesCurator;
    @Mock private Configuration config;
    @Mock private Provider<JsRunnerRequestCache> cacheProvider;
    @Mock private JsRunnerRequestCache cache;
    @Mock private ProductCurator mockProductCurator;
    @Mock private OwnerCurator mockOwnerCurator;
    @Mock private OwnerProductCurator mockOwnerProductCurator;
    @Mock private EnvironmentCurator mockEnvironmentCurator;
    @Mock private ProductManager mockProductManager;
    @Mock private EventSink mockEventSink;
    @Mock private EventFactory mockEventFactory;

    @Inject private ModelTranslator translator;
    private Enforcer enforcer;
    private Owner owner;
    private Consumer consumer;

    private static final String PRODUCT_CPULIMITED = "CPULIMITED001";

    @BeforeEach
    public void createEnforcer() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(config.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);

        owner = createOwner();
        ownerCurator.create(owner);

        consumer = this.createConsumer(owner);

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(getClass().getResourceAsStream("/rules/test-rules.js")));
        StringBuilder builder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            builder.append(line + "\n");
        }
        reader.close();

        Rules rules = mock(Rules.class);
        when(rules.getRules()).thenReturn(builder.toString());
        when(rulesCurator.getRules()).thenReturn(rules);
        when(rulesCurator.getUpdated()).thenReturn(TestUtil.createDate(2010, 1, 1));
        when(cacheProvider.get()).thenReturn(cache);

        JsRunner jsRules = new JsRunnerProvider(rulesCurator, cacheProvider).get();

        translator = new StandardTranslator(consumerTypeCurator, mockEnvironmentCurator, mockOwnerCurator);

        enforcer = new EntitlementRules(
            new DateSourceForTesting(2010, 1, 1), jsRules, i18n, config, consumerCurator, consumerTypeCurator,
            mockProductCurator, new RulesObjectMapper(), mockOwnerCurator, mockOwnerProductCurator,
            mockProductManager, mockEventSink, mockEventFactory, translator
        );
    }

    @Test
    public void shouldParseValidMapping() {
        Rule func1rule = new EntitlementRules.Rule(
            "func1", 1, new HashSet<String>() { { add("attr1"); add("attr2"); add("attr3"); } });
        assertEquals(func1rule,
            ((EntitlementRules) enforcer).parseRule("func1:1:attr1:attr2:attr3"));

        assertEquals(new EntitlementRules.Rule("func3", 3, new HashSet<String>() { { add("attr4"); } }),
            ((EntitlementRules) enforcer).parseRule("func3:3:attr4"));
    }

    @Test
    public void shouldFailParsingIfNoOderIsPresent() {
        assertThrows(IllegalArgumentException.class, () ->
            ((EntitlementRules) enforcer).parseRule("func3:attr4")
        );
    }

    @Test
    public void shouldFailParsingIfNotAllParametersArePresent() {
        assertThrows(IllegalArgumentException.class, () ->
            ((EntitlementRules) enforcer).parseRule("func3:3")
        );
    }

    @Test
    public void shouldCreateMappingBetweenAttributesAndFunctions() {
        String attributesAndRules =
            "func1:1:attr1:attr2:attr3, func2:2:attr1, func3:3:attr4, func5:5:attr1:attr4";

        Map<String, Set<EntitlementRules.Rule>> parsed =
            ((EntitlementRules) enforcer).parseAttributeMappings(attributesAndRules);

        assertTrue(parsed.get("attr1").contains(
            rule("func1", 1, "attr1", "attr2", "attr3")));
        assertTrue(parsed.get("attr1").contains(rule("func2", 2, "attr1")));
        assertTrue(parsed.get("attr4").contains(rule("func3", 3, "attr4")));
        assertTrue(parsed.get("attr4").contains(rule("func5", 5, "attr1", "attr4")));
        assertTrue(parsed.get("attr1").contains(rule("func5", 5, "attr1", "attr4")));
    }

    @Test
    public void shouldSelectAllRulesMappedToSingleAttribute() {
        Map<String, Set<EntitlementRules.Rule>> rules =
            new HashMap<String, Set<EntitlementRules.Rule>>() {
                {
                    put("attr1", rules(rule("func5", 5, "attr1"), rule("func1", 2,
                        "attr1")));
                    put("attr3", rules(rule("func3", 2, "attr3")));
                }
            };

        List<EntitlementRules.Rule> orderedAndFilteredRules = ((EntitlementRules) enforcer)
            .rulesForAttributes(new HashSet<String>() { { add("attr1"); } }, rules);

        assertEquals(
            new LinkedList<EntitlementRules.Rule>() {
                {
                    add(rule("func5", 5, "attr1"));
                    add(rule("func1", 2, "attr1"));
                    add(rule("global", 0, new String[0]));
                }
            },
            orderedAndFilteredRules
        );
    }

    @Test
    public void shouldSelectAllRulesMappedToMultipleAttributes() {
        Map<String, Set<EntitlementRules.Rule>> rules = new HashMap<String, Set<EntitlementRules.Rule>>() {
            {
                put("attr1", rules(
                    rule("func5", 5, "attr1", "attr2", "attr3"),
                    rule("func1", 2, "attr1", "attr2"),
                    rule("func6", 4, "attr1", "attr2", "attr3", "attr4"))
                );
                put("attr3", rules(rule("func3", 3, "attr3")));
            }
        };

        List<EntitlementRules.Rule> orderedAndFilteredRules = ((EntitlementRules) enforcer)
            .rulesForAttributes(new HashSet<String>() {
                {
                    add("attr1"); add("attr2"); add("attr3");
                }}, rules);

        assertEquals(new LinkedList<EntitlementRules.Rule>() {
            {
                add(rule("func5", 5, "attr1", "attr2", "attr3"));
                add(rule("func3", 3, "attr3"));
                add(rule("func1", 2, "attr1", "attr2"));
                add(rule("global", 0, new String[0]));
            }}, orderedAndFilteredRules);
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

    private EntitlementRules.Rule rule(String name, int priority, String... attrs) {
        Set<String> attributes = new HashSet<>();
        for (String attr : attrs) {
            attributes.add(attr);
        }
        return new EntitlementRules.Rule(name, priority, attributes);
    }

    private Set<EntitlementRules.Rule> rules(EntitlementRules.Rule... rules) {
        return new HashSet<>(Arrays.asList(rules));
    }

    private Date expiryDate(int year, int month, int day) {
        return TestUtil.createDate(year, month, day);
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
