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
package org.candlepin.policy.js.compliance;

import static org.junit.Assert.*;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

/**
 * StatusReasonMessageGeneratorTest
 */
public class StatusReasonMessageGeneratorTest {

    private StatusReasonMessageGenerator generator;
    private Consumer consumer;
    private Owner owner;
    private Entitlement ent1;
    private Entitlement entStacked1, entStacked2;

    @Before
    public void setUp() {
        Locale locale = new Locale("en_US");
        I18n i18n = I18nFactory.getI18n(getClass(), "org.candlepin.i18n.Messages", locale,
            I18nFactory.FALLBACK);
        generator = new StatusReasonMessageGenerator(i18n);
        
        owner = new Owner("test");
        
        consumer = new Consumer();
        consumer.setType(new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM));
        ent1 = mockEntitlement(consumer, "id1", "Nonstacked Product");
        ent1.setId("ent1");
        entStacked1 = mockBaseStackedEntitlement(consumer, "stack", "Stacked Product", "Stack Subscription One");
        entStacked2 = mockBaseStackedEntitlement(consumer, "stack", "Stacked Product", "Stack Subscription Two");
        consumer.addEntitlement(ent1);
        consumer.addEntitlement(entStacked1);
        consumer.addEntitlement(entStacked2);
    }

    @Test
    public void testSocketsMessage() {
        ComplianceReason reason = buildReason("SOCKETS", buildGeneralAttributes());
        generator.setMessage(consumer, reason);
        assertEquals("Subscriptions for Nonstacked Product only cover 4 of 8 sockets.", reason.getMessage());
    }

    @Test
    public void testStackedSubs() {
        ComplianceReason reason = buildReason("SOCKETS", buildStackedAttributes());
        generator.setMessage(consumer, reason);
        String message = reason.getMessage();
        assertTrue(message.indexOf("Stack Subscription Two") > 0 && message.indexOf("Stack Subscription One") > 0);
    }

    @Test
    public void testArchMessage() {
        ComplianceReason reason = buildReason("ARCH", buildArchAttributes());
        generator.setMessage(consumer, reason);
        assertEquals("Subscriptions for Nonstacked Product cover architecture ppc64 but the system is x86_64.", reason.getMessage());
    }

    @Test
    public void testRamMessage() {
        ComplianceReason reason = buildReason("RAM", buildGeneralAttributes());
        generator.setMessage(consumer, reason);
        assertEquals("Subscriptions for Nonstacked Product only cover 4gb of systems 8gb of ram.", reason.getMessage());
    }

    @Test
    public void testCoresMessage() {
        ComplianceReason reason = buildReason("CORES", buildGeneralAttributes());
        generator.setMessage(consumer, reason);
        assertEquals("Subscriptions for Nonstacked Product only cover 4 of 8 cores.", reason.getMessage());
    }

    @Test
    public void testDefaultMessage() {
        ComplianceReason reason = buildReason("NOT_A_KEY", buildGeneralAttributes());
        generator.setMessage(consumer, reason);
        assertEquals("NOT_A_KEY COVERAGE PROBLEM.  Subscription for Nonstacked Product covers 4 of 8", reason.getMessage());
    }

    private ComplianceReason buildReason(String key, Map<String, String> attributes) {
        ComplianceReason reason = new ComplianceReason();
        reason.setKey(key);
        reason.setMessage("");
        reason.setAttributes(attributes);
        return reason;
    }
    
    private Map<String, String> buildGeneralAttributes() {
        return new HashMap<String, String>() {
            {
                put("entitlement_id", "ent1");
                put("has", "8");
                put("covered", "4");
            }
        };
    }

    private Map<String, String> buildArchAttributes() {
        return new HashMap<String, String>() {
            {
                put("entitlement_id", "ent1");
                put("has", "x86_64");
                put("covered", "ppc64");
            }
        };
    }

    private Map<String, String> buildStackedAttributes() {
        return new HashMap<String, String>() {
            {
                put("stack_id", "stack");
                put("has", "8");
                put("covered", "4");
            }
        };
    }

    private Entitlement mockBaseStackedEntitlement(Consumer consumer, String stackId,
        String productId, String name) {

        Entitlement e = mockEntitlement(consumer, productId, name);

        Random gen = new Random();
        int id = gen.nextInt(Integer.MAX_VALUE);
        e.setId(String.valueOf(id));

        Pool p = e.getPool();

        // Setup the attributes for stacking:
        p.addProductAttribute(new ProductPoolAttribute("stacking_id", stackId, productId));

        return e;
    }

    private Entitlement mockEntitlement(Consumer consumer, String productId, String name) {

        Pool p = new Pool(owner, productId, name, null,
            new Long(1000), TestUtil.createDate(2000, 1, 1),
            TestUtil.createDate(2050, 1, 1), "1000", "1000", "1000");
        Entitlement e = new Entitlement(p, consumer, p.getStartDate(), p.getEndDate(), 1);
        return e;
    }
}
