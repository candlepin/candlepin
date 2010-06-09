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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.io.InputStreamReader;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import javax.script.ScriptEngineManager;

import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.ValidationResult;
import org.fedoraproject.candlepin.policy.js.JavascriptEnforcer;
import org.fedoraproject.candlepin.policy.js.PostEntHelper;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.test.TestUtil;
import org.fedoraproject.candlepin.util.DateSourceImpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xnap.commons.i18n.I18nFactory;

/**
 * DefaultRulesTest
 */
public class DefaultRulesTest {
    private Enforcer enforcer;
    @Mock private ProductServiceAdapter prodAdapter;
    private Owner owner;
    private Consumer consumer;
    @Mock private PostEntHelper postEntHelper;

    @Before
    public void createEnforcer() throws Exception {
        MockitoAnnotations.initMocks(this);

        URL url = this.getClass().getClassLoader().getResource("rules/default-rules.js");
        InputStreamReader inputStreamReader = new InputStreamReader(url.openStream());

        enforcer = new JavascriptEnforcer(new DateSourceImpl(), inputStreamReader,
            prodAdapter, new ScriptEngineManager().getEngineByName("JavaScript"), 
                I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK));

        owner = new Owner();
        consumer = new Consumer("test consumer", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));
    }
    
    @Test
    public void testBindForSameProductNotAllowed() {
        Product product = new Product("a-product", "A product for testing");
        Pool pool = new Pool(owner, product.getId(), new Long(5),
            TestUtil.createDate(200, 02, 26), TestUtil.createDate(2050, 02, 26));

        Entitlement e = new Entitlement(pool, consumer, new Date(), new Integer("1"));
        consumer.addEntitlement(e);
        
        when(this.prodAdapter.getProductById("a-product")).thenReturn(product);

        ValidationResult result = enforcer.pre(consumer, pool, new Integer(1)).getResult();

        assertTrue(result.hasErrors());
        assertFalse(result.isSuccessful());
    }

    @Test
    public void testBindFromSameProductAllowedWithMultiEntitlementAttribute() {
        Product product = new Product("a-product", "A product for testing");
        product.addAttribute(new Attribute("multi-entitlement", "yes"));
        Pool pool = new Pool(owner, product.getId(), new Long(5),
            TestUtil.createDate(200, 02, 26), TestUtil.createDate(2050, 02, 26));

        Entitlement e = new Entitlement(pool, consumer, new Date(), new Integer("1"));
        consumer.addEntitlement(e);
        
        when(this.prodAdapter.getProductById("a-product")).thenReturn(product);
        
        ValidationResult result = enforcer.pre(consumer, pool, new Integer(1)).getResult();

        assertTrue(result.isSuccessful());
        assertFalse(result.hasErrors());
        assertFalse(result.hasErrors());
    }
    
    @Test
    public void bindFromExhaustedPoolShouldFail() {
        Product product = new Product("a-product", "A product for testing");
        Pool pool = new Pool(owner, product.getId(), new Long(0),
            TestUtil.createDate(200, 02, 26), TestUtil.createDate(2050, 02, 26));

        Entitlement e = new Entitlement(pool, consumer, new Date(), new Integer("1"));
        consumer.addEntitlement(e);
        
        when(this.prodAdapter.getProductById("a-product")).thenReturn(product);

        ValidationResult result = enforcer.pre(consumer, pool, new Integer(1)).getResult();
        
        assertTrue(result.hasErrors());
        assertFalse(result.isSuccessful());
    }
    
    @Test
    public void architectureALLShouldNotGenerateWarnings() {
        Pool pool = setupTest("architecture", "ALL", null, "i686");
        
        ValidationResult result = enforcer.pre(consumer, pool, new Integer(1)).getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }
    
    @Test
    public void architectureMismatchShouldGenerateWarning() {
        Pool pool = setupTest("architecture", "x86_64", "cpu.architecture", "i686");
        
        ValidationResult result = enforcer.pre(consumer, pool, new Integer(1)).getResult();
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }
    
    @Test
    public void missingConsumerArchitectureShouldGenerateWarning() {
        Pool pool = setupTest("architecture", "x86_64", "cpu.architecture", "x86_64");
        
        // Get rid of the facts that setupTest set.
        consumer.setFacts(new HashMap<String, String>());
        
        ValidationResult result = enforcer.pre(consumer, pool, new Integer(1)).getResult();
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }
    
    @Test
    public void fewerThanMaximumNumberOfSocketsShouldNotGenerateWarning() {
        Pool pool = setupTest("sockets", "128", "cpu.cpu_socket(s)", "2");
        
        ValidationResult result = enforcer.pre(consumer, pool, new Integer(1)).getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }
    
    @Test
    public void matchingNumberOfSocketsShouldNotGenerateWarning() {
        Pool pool = setupTest("sockets", "2", "cpu.cpu_socket(s)", "2");
        
        ValidationResult result = enforcer.pre(consumer, pool, new Integer(1)).getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void missingConsumerSocketsShouldGenerateWarning() {
        Pool pool = setupTest("sockets", "2", "cpu.cpu_socket(s)", "2");
        
        // Get rid of the facts that setupTest set.
        consumer.setFacts(new HashMap<String, String>());
        
        ValidationResult result = enforcer.pre(consumer, pool, new Integer(1)).getResult();
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }
    
    private Pool setupTest(
            final String attributeName, String attributeValue,
            final String factName, final String factValue) {
        Product product = new Product("a-product", "A product for testing");
        product.addAttribute(new Attribute(attributeName, attributeValue));
        Pool pool = new Pool(owner, product.getId(), new Long(5),
            TestUtil.createDate(200, 02, 26), TestUtil.createDate(2050, 02, 26));
        
        consumer.setFacts(new HashMap<String, String>() {
            { put(factName, factValue); }
        });
        
        when(this.prodAdapter.getProductById("a-product")).thenReturn(product);
        return pool;
    }
    
    @Test
    public void exceedingNumberOfSocketsShouldGenerateWarning() {
        Pool pool = setupTest("sockets", "2", "cpu.cpu_sockets", "4");
        
        ValidationResult result = enforcer.pre(consumer, pool, new Integer(1)).getResult();
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }
    
    @Test
    public void correctConsumerTypeShouldNotGenerateError() {
        Pool pool = setupProductWithRequiresConsumerTypeAttribute();
        consumer.setType(new ConsumerType(ConsumerTypeEnum.DOMAIN));
        
        ValidationResult result = enforcer.pre(consumer, pool, new Integer(1)).getResult();
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }
    
    @Test
    public void mismatchingConsumerTypeShouldGenerateError() {
        Pool pool = setupProductWithRequiresConsumerTypeAttribute();
        consumer.setType(new ConsumerType(ConsumerTypeEnum.PERSON));
        
        ValidationResult result = enforcer.pre(consumer, pool, new Integer(1)).getResult();
        assertTrue(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    private Pool setupProductWithRequiresConsumerTypeAttribute() {
        Product product = new Product("a-product", "A product for testing");
        product.addAttribute(
            new Attribute("requires_consumer_type", ConsumerTypeEnum.DOMAIN.toString()));
        Pool pool = new Pool(owner, product.getId(), new Long(5),
            TestUtil.createDate(200, 02, 26), TestUtil.createDate(2050, 02, 26));
        when(this.prodAdapter.getProductById("a-product")).thenReturn(product);
        return pool;
    }
}
