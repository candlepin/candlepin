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
package org.fedoraproject.candlepin.policy.test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.script.ScriptEngineManager;

import org.fedoraproject.candlepin.guice.RulesReaderProvider;
import org.fedoraproject.candlepin.guice.ScriptEngineProvider;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.ValidationResult;
import org.fedoraproject.candlepin.policy.js.RuleExecutionException;
import org.fedoraproject.candlepin.policy.js.entitlement.EntitlementRules;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.DateSourceForTesting;
import org.fedoraproject.candlepin.test.TestDateUtil;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EnforcerTest extends DatabaseTestFixture {

    @Mock private ProductServiceAdapter productAdapter;
    @Mock private RulesReaderProvider readerProvider;
    @Mock private ScriptEngineProvider engineProvider;
    private Enforcer enforcer;
    private Owner owner;
    private Consumer consumer;
    private static final String LONGEST_EXPIRY_PRODUCT = "LONGEST001";
    private static final String HIGHEST_QUANTITY_PRODUCT = "QUANTITY001";
    private static final String BAD_RULE_PRODUCT = "BADRULE001";
    private static final String PRODUCT_CPULIMITED = "CPULIMITED001";

    @Before
    public void createEnforcer() {
        MockitoAnnotations.initMocks(this);
        
        owner = createOwner();
        ownerCurator.create(owner);

        consumer = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(consumer.getType());
        consumerCurator.create(consumer);

        Reader reader 
            = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/rules/test-rules.js")));
        
        when(readerProvider.get()).thenReturn(reader);
        when(engineProvider.get()).thenReturn(
            new ScriptEngineManager().getEngineByName("JavaScript"));
        
        
        enforcer = new EntitlementRules(new DateSourceForTesting(2010, 1, 1),
            readerProvider, productAdapter,
            engineProvider, i18n);
    }
    
    @Test
    public void shouldParseValidMapping() {
        assertEquals(
            new EntitlementRules.Rule(
                "func1", 1, 
                new HashSet<String>() { { add("attr1"); add("attr2"); add("attr3"); } }
            ),
            ((EntitlementRules) enforcer).parseRule("func1:1:attr1:attr2:attr3"));
        
        assertEquals(
            new EntitlementRules.Rule(
                "func3", 3, 
                new HashSet<String>() { { add("attr4"); } }
            ),
            ((EntitlementRules) enforcer).parseRule("func3:3:attr4"));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void shouldFailParsingIfNoOderIsPresent() {
        ((EntitlementRules) enforcer).parseRule("func3:attr4");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void shouldFailParsingIfNotAllParametersArePresent() {
        ((EntitlementRules) enforcer).parseRule("func3:3");
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
        
        List<EntitlementRules.Rule> orderedAndFilteredRules = 
            ((EntitlementRules) enforcer).rulesForAttributes(
                new HashSet<String>() { { add("attr1"); } }, rules);
        
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
        Map<String, Set<EntitlementRules.Rule>> rules 
            = new HashMap<String, Set<EntitlementRules.Rule>>() {
                {
                    put("attr1",
                        rules(
                            rule("func5", 5, "attr1", "attr2", "attr3"),
                            rule("func1", 2, "attr1", "attr2"),
                            rule("func6", 4, "attr1", "attr2", "attr3", "attr4"))
                    );
                    put("attr3", rules(rule("func3", 3, "attr3")));
                } 
            };
        
        List<EntitlementRules.Rule> orderedAndFilteredRules = 
            ((EntitlementRules) enforcer).rulesForAttributes(
                new HashSet<String>() { 
                    { 
                        add("attr1"); add("attr2"); add("attr3"); 
                    } 
                }, rules);
        
        assertEquals(
            new LinkedList<EntitlementRules.Rule>() { 
                {
                    add(rule("func5", 5, "attr1", "attr2", "attr3"));
                    add(rule("func3", 3, "attr3"));
                    add(rule("func1", 2, "attr1", "attr2"));
                    add(rule("global", 0, new String[0]));
                } 
            },
            orderedAndFilteredRules
        );
    }

    // grrr. have to test two conditions atm: sufficient number of entitlements
    // *when* pool has not expired
    //
    // shouldPassValidationWhenSufficientNumberOfEntitlementsIsAvailableAndNotExpired
    @Test
    public void passValidationEnoughNumberOfEntitlementsIsAvailableAndNotExpired() {
        Product product = new Product("a-product", "A product for testing");
        productCurator.create(product);
        
        when(this.productAdapter.getProductById("a-product")).thenReturn(product);
        
        ValidationResult result = enforcer.preEntitlement(
            createConsumer(owner),
            entitlementPoolWithMembersAndExpiration(owner, product, 1, 2, 
                expiryDate(2010, 10, 10)),
            1).getResult();
        assertTrue(result.isSuccessful());
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void shouldFailValidationWhenNoEntitlementsAreAvailable() {
        Product product = new Product("a-product", "A product for testing");
        productCurator.create(product);
        
        when(this.productAdapter.getProductById("a-product")).thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(
            createConsumer(owner),
            entitlementPoolWithMembersAndExpiration(owner, product, 1, 1, 
                expiryDate(2010, 10, 10)),
            1).getResult();
        
        assertFalse(result.isSuccessful());
        assertTrue(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void shouldFailWhenEntitlementsAreExpired() {
        Product product = new Product("a-product", "A product for testing");
        productCurator.create(product);
        
        when(this.productAdapter.getProductById("a-product")).thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(
            createConsumer(owner),
            entitlementPoolWithMembersAndExpiration(owner, product, 1, 2,
                expiryDate(2000, 1, 1)), 1).getResult();
        assertFalse(result.isSuccessful());
        assertTrue(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    // This exception should mention wrapping a MissingFactException
    @Test(expected = RuleExecutionException.class)
    public void testRuleFailsWhenConsumerDoesntHaveFact() {
        Product product = new Product("a-product", "A product for testing");
        product.setAttribute(PRODUCT_CPULIMITED, "2");
        productCurator.create(product);
        
        when(this.productAdapter.getProductById("a-product")).thenReturn(product);
        
        ValidationResult result = enforcer.preEntitlement(
            TestUtil.createConsumer(), 
            entitlementPoolWithMembersAndExpiration(owner, product, 1, 2, 
                expiryDate(2000, 1, 1)),
            1).getResult();
        
        assertFalse(result.isSuccessful());
        assertTrue(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void testSelectBestPoolLongestExpiry() {
        Product product = new Product("a-product", "A product for testing");
        product.setAttribute(LONGEST_EXPIRY_PRODUCT, "");
        productCurator.create(product);

        Pool pool1 = createPoolAndSub(owner, product, 5L,
            TestUtil.createDate(2000, 02, 26), TestUtil
                .createDate(2050, 02, 26));
        Pool pool2 = createPoolAndSub(owner, product, 5L,
            TestUtil.createDate(2000, 02, 26), TestUtil
                .createDate(2051, 02, 26));
        Pool desired = createPoolAndSub(owner, product, 5L,
            TestUtil.createDate(2000, 02, 26), TestUtil
                .createDate(2060, 02, 26));
        Pool pool3 = createPoolAndSub(owner, product, 5L,
            TestUtil.createDate(2000, 02, 26), TestUtil
                .createDate(2055, 02, 26));
        
        when(this.productAdapter.getProductById("a-product"))
            .thenReturn(product);
        
        List<Pool> availablePools 
            = Arrays.asList(new Pool[] {pool1, pool2, desired, pool3});

        List<Pool> result = enforcer.selectBestPools(consumer, new String[] {"a-product"},
            availablePools);
        assertEquals(desired.getId(), result.get(0).getId());
    }

    @Test
    public void testSelectBestPoolMostAvailable() {
        Product product = new Product("a-product", "A product for testing");
        product.setAttribute(HIGHEST_QUANTITY_PRODUCT, "");
        productCurator.create(product);

        Pool pool1 = createPoolAndSub(owner, product, 5L,
            TestUtil.createDate(2000, 02, 26), TestUtil
                .createDate(2050, 02, 26));
        Pool desired = createPoolAndSub(owner, product, 500L,
            TestUtil.createDate(2000, 02, 26), TestUtil
                .createDate(2051, 02, 26));
        Pool pool2 = createPoolAndSub(owner, product, 5L,
            TestUtil.createDate(2000, 02, 26), TestUtil
                .createDate(2060, 02, 26));
        
        when(this.productAdapter.getProductById("a-product"))
            .thenReturn(product);
        
        List<Pool> availablePools 
            = Arrays.asList(new Pool[] {pool1, pool2, desired});

        List<Pool> result = enforcer.selectBestPools(consumer,
            new String[] {"a-product"}, availablePools);
        assertEquals(desired.getId(), result.get(0).getId());
    }
    
    @Test
    public void shouldUseHighestPriorityRule() {
        Product product = new Product("a-product", "A product for testing");
        product.setAttribute(HIGHEST_QUANTITY_PRODUCT, "");
        product.setAttribute(LONGEST_EXPIRY_PRODUCT, "");
        productCurator.create(product);
        
        Pool pool1 = createPoolAndSub(owner, product, 5L,
            TestUtil.createDate(2000, 02, 26), TestUtil.createDate(2050, 02, 26));
        Pool desired = createPoolAndSub(owner, product, 5L,
            TestUtil.createDate(2000, 02, 26), TestUtil.createDate(2051, 02, 26));
        Pool pool2 = createPoolAndSub(owner, product, 500L,
            TestUtil.createDate(2000, 02, 26), TestUtil.createDate(2020, 02, 26));
        
        when(this.productAdapter.getProductById("a-product")).thenReturn(product);

        List<Pool> availablePools = Arrays.asList(new Pool[] {pool1, pool2, desired});
        List<Pool> result = enforcer.selectBestPools(consumer, new String[] {"a-product"},
            availablePools);

        assertEquals(desired.getId(), result.get(0).getId());
    }

    @Test
    public void testSelectBestPoolNoPools() {
        when(this.productAdapter.getProductById(HIGHEST_QUANTITY_PRODUCT))
            .thenReturn(new Product(HIGHEST_QUANTITY_PRODUCT, HIGHEST_QUANTITY_PRODUCT));
        
        // There are no pools for the product in this case:
        List<Pool> result = enforcer.selectBestPools(consumer,
            new String[] {HIGHEST_QUANTITY_PRODUCT}, new LinkedList<Pool>());
        assertNull(result);
    }

    @Test(expected = RuleExecutionException.class)
    public void testSelectBestPoolBadRule() {
        Product product = new Product("a-product", "A product for testing");
        product.setAttribute(BAD_RULE_PRODUCT, "");
        productCurator.create(product);

        
        Pool pool1 = createPoolAndSub(owner, product, 5L, TestUtil
            .createDate(2000, 02, 26), TestUtil.createDate(2050, 02, 26));

        when(this.productAdapter.getProductById("a-product"))
            .thenReturn(product);

        enforcer.selectBestPools(consumer, new String[] {"a-product"}, 
            Collections.singletonList(pool1));
    }

    @Test
    public void testSelectBestPoolDefaultRule() {
        Product product = new Product("a-product", "A product for testing");
        productCurator.create(product);

        Pool pool1 = createPoolAndSub(owner, product, 5L, TestUtil
            .createDate(2000, 02, 26), TestUtil.createDate(2050, 02, 26));
        Pool pool2 = createPoolAndSub(owner, product, 5L, TestUtil
            .createDate(2000, 02, 26), TestUtil.createDate(2060, 02, 26));
        
        when(this.productAdapter.getProductById("a-product"))
            .thenReturn(product);
    
        List<Pool> availablePools 
            = Arrays.asList(new Pool[] {pool1, pool2});

        List<Pool> result = enforcer.selectBestPools(consumer,
            new String[] {product.getId()}, availablePools);
        assertEquals(pool1.getId(), result.get(0).getId());
    }
    
    private EntitlementRules.Rule rule(String name, int priority, String... attrs) {
        Set<String> attributes = new HashSet<String>();
        for (String attr : attrs) {
            attributes.add(attr);
        }
        return new EntitlementRules.Rule(name, priority, attributes);
    }
    
    private Set<EntitlementRules.Rule> rules(EntitlementRules.Rule... rules) {
        return new HashSet<EntitlementRules.Rule>(Arrays.asList(rules));
    }
    
    private Date expiryDate(int year, int month, int day) {
        return TestDateUtil.date(year, month, day);
    }

    private Pool entitlementPoolWithMembersAndExpiration(Owner theOwner, Product product,
        final int currentMembers, final int maxMembers, Date expiry) {
        Pool p = createPoolAndSub(theOwner, product, 
            Long.valueOf(maxMembers), new Date(), expiry);

        // alternatively 1 entitlement with quantity of currentMembers
        for (int i = 0; i < currentMembers; i++) {
            Entitlement e = mock(Entitlement.class);
            when(e.getQuantity()).thenReturn(1);
            p.getEntitlements().add(e);
        }
        return p;
    }
}
