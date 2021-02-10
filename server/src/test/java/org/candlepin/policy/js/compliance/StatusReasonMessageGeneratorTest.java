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

import static org.junit.Assert.assertEquals;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

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
        ConsumerType ctype = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        ctype.setId("test-ctype");
        consumer = new Consumer();
        consumer.setType(ctype);
        ent1 = mockEntitlement(consumer, TestUtil.createProduct("id1", "Nonstacked Product"));
        ent1.setId("ent1");
        entStacked1 = mockBaseStackedEntitlement(consumer, "stack",
            TestUtil.createProduct("Stacked Product", "Stack Subscription One"));
        entStacked2 = mockBaseStackedEntitlement(consumer, "stack",
            TestUtil.createProduct("Stacked Product", "Stack Subscription Two"));
        consumer.addEntitlement(ent1);
        consumer.addEntitlement(entStacked1);
        consumer.addEntitlement(entStacked2);
    }

    @Test
    public void testSocketsMessage() {
        ComplianceReason reason = buildReason("SOCKETS", buildGeneralAttributes("8", "4"));
        generator.setMessage(consumer, reason, new Date());
        assertEquals("Only supports 4 of 8 sockets.", reason.getMessage());
    }

    @Test
    public void testStackedSubs() {
        ComplianceReason reason = buildReason("SOCKETS", buildStackedAttributes("8", "4"));
        generator.setMessage(consumer, reason, new Date());
        String message = reason.getMessage();
        assertEquals("Only supports 4 of 8 sockets.", message);
        String[] names = reason.getAttributes().get("name").split("/");
        Arrays.sort(names);
        assertEquals("Stack Subscription One", names[0]);
        assertEquals("Stack Subscription Two", names[1]);
    }

    @Test
    public void testArchMessage() {
        ComplianceReason reason = buildReason("ARCH",
            buildGeneralAttributes("x86_64", "ppc64"));
        generator.setMessage(consumer, reason, new Date());
        assertEquals(
            "Supports architecture ppc64 but" +
            " the system is x86_64.", reason.getMessage());
    }

    @Test
    public void testRamMessage() {
        ComplianceReason reason = buildReason("RAM", buildGeneralAttributes("8", "4"));
        generator.setMessage(consumer, reason, new Date());
        assertEquals(
            "Only supports 4GB of 8GB of RAM.",
            reason.getMessage());
    }

    @Test
    public void testVirtLimitMessage() {
        ComplianceReason reason = buildReason("GUEST_LIMIT",
            buildGeneralAttributes("8", "4"));
        generator.setMessage(consumer, reason, new Date());
        assertEquals(
            "Only supports 4 of 8 virtual guests.",
            reason.getMessage());
    }

    @Test
    public void testCoresMessage() {
        ComplianceReason reason = buildReason("CORES", buildGeneralAttributes("8", "4"));
        generator.setMessage(consumer, reason, new Date());
        assertEquals(
            "Only supports 4 of 8 cores.",
            reason.getMessage());
    }

    @Test
    public void testVcpuMessage() {
        ComplianceReason reason = buildReason("VCPU", buildGeneralAttributes("8", "4"));
        generator.setMessage(consumer, reason, new Date());
        assertEquals(
            "Only supports 4 of 8 vCPUs.",
            reason.getMessage());
    }

    @Test
    public void testDefaultMessage() {
        ComplianceReason reason = buildReason("NOT_A_KEY", buildGeneralAttributes("8", "4"));
        generator.setMessage(consumer, reason, new Date());
        assertEquals("NOT_A_KEY COVERAGE PROBLEM.  Supports 4 of 8", reason.getMessage());
    }

    @Test
    public void testNonInstalled() {
        HashMap<String, String> attrs = new HashMap<>();
        attrs.put("product_id", "prod1");
        ComplianceReason reason = buildReason("NOTCOVERED", attrs);

        Owner owner = new Owner("test");
        Product product = TestUtil.createProduct("prod1", "NonCovered Product");
        ConsumerInstalledProduct installed = new ConsumerInstalledProduct(product.getId(),
            product.getName());

        consumer.addInstalledProduct(installed);
        generator.setMessage(consumer, reason, new Date());
        assertEquals("Not supported by a valid subscription.", reason.getMessage());
    }

    private ComplianceReason buildReason(String key, Map<String, String> attributes) {
        ComplianceReason reason = new ComplianceReason();
        reason.setKey(key);
        reason.setMessage("");
        reason.setAttributes(attributes);
        return reason;
    }

    private Map<String, String> buildGeneralAttributes(String has, String covered) {
        HashMap<String, String> result = new HashMap<>();
        result.put("entitlement_id", "ent1");
        result.put("has", has);
        result.put("covered", covered);
        return result;
    }

    private Map<String, String> buildStackedAttributes(String has, String covered) {
        HashMap<String, String> result = new HashMap<>();
        result.put("stack_id", "stack");
        result.put("has", has);
        result.put("covered", covered);
        return result;
    }

    private Entitlement mockBaseStackedEntitlement(Consumer consumer, String stackId, Product product) {
        Entitlement e = mockEntitlement(consumer, product);
        Random gen = new Random();
        int id = gen.nextInt(Integer.MAX_VALUE);
        e.setId(String.valueOf(id));
        Pool p = e.getPool();
        // Setup the attributes for stacking:
        p.getProduct().setAttribute(Product.Attributes.STACKING_ID, stackId);
        return e;
    }

    private Entitlement mockEntitlement(Consumer consumer, Product product) {
        Pool p = new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(1000L)
            .setStartDate(TestUtil.createDate(2000, 1, 1))
            .setEndDate(TestUtil.createDate(2050, 1, 1))
            .setContractNumber("1000")
            .setAccountNumber("1000")
            .setOrderNumber("1000");

        Entitlement e = new Entitlement(p, consumer, owner, 1);
        return e;
    }
}
