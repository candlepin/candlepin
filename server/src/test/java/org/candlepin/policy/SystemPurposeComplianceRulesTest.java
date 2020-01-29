/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.candlepin.audit.EventSink;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tests for SystemPurposeComplianceRules
 */
public class SystemPurposeComplianceRulesTest {

    private SystemPurposeComplianceRules complianceRules;

    private Owner owner;
    @Mock private EventSink eventSink;
    @Mock private ConsumerCurator consumerCurator;
    @Mock private ConsumerTypeCurator consumerTypeCurator;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Locale locale = new Locale("en_US");
        I18n i18n = I18nFactory.getI18n(getClass(), "org.candlepin.i18n.Messages", locale,
            I18nFactory.FALLBACK);
        complianceRules = new SystemPurposeComplianceRules(eventSink, consumerCurator, consumerTypeCurator,
                i18n);
        owner = new Owner("test");
        owner.setId(TestUtil.randomString());
    }

    private Entitlement mockEntitlement(Consumer consumer, Product product, Product ... providedProducts) {
        // Make the end date relative to now so it won't outrun it over time.
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, 50);

        return mockEntitlement(
            consumer,
            product,
            TestUtil.createDate(2000, 1, 1),
            cal.getTime(),
            providedProducts
        );
    }

    private Entitlement mockEntitlement(Consumer consumer, Product product, Date start, Date end,
        Product ... providedProducts) {
        product.setProvidedProducts(Arrays.asList(providedProducts));

        Pool pool = new Pool(
            owner,
            product,
            new HashSet<>(),
            1000L,
            start,
            end,
            "1000",
            "1000",
            "1000"
        );

        pool.setId("pool_" + TestUtil.randomInt());
        pool.setUpdated(new Date());
        pool.setCreated(new Date());
        Entitlement e = new Entitlement(pool, consumer, owner, 1);
        e.setId("ent_" + TestUtil.randomInt());
        e.setUpdated(new Date());
        e.setCreated(new Date());

        return e;
    }

    private Consumer mockConsumer(Product ... installedProducts) {
        ConsumerType ctype = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        ctype.setId("test-ctype-" + TestUtil.randomInt());

        Consumer consumer = new Consumer();
        consumer.setType(ctype);
        consumer.setOwner(owner);

        when(this.consumerTypeCurator.get(eq(ctype.getId()))).thenReturn(ctype);
        when(this.consumerTypeCurator.getConsumerType(eq(consumer))).thenReturn(ctype);

        for (Product product : installedProducts) {
            consumer.addInstalledProduct(new ConsumerInstalledProduct(product.getId(), product.getName()));
        }

        consumer.setFact("cpu.cpu_socket(s)", "8"); // 8 socket machine

        return consumer;
    }

    /*
     * Tests that a consumer is syspurpose compliant when their specified role value matches their
     * entitlement's role value case insensitively.
     */
    @Test
    public void testConsumerRoleIsCompliantCaseInsensitively() {
        Consumer consumer = mockConsumer();
        consumer.setRole("mY_rOlE");

        Product prod1 = new Product();
        prod1.setAttribute(Product.Attributes.ROLES, "my_role");
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockEntitlement(consumer, TestUtil.createProduct("Awesome Product"), prod1));

        SystemPurposeComplianceStatus status =
            complianceRules.getStatus(consumer, ents, null, false);

        assertTrue(status.isCompliant());
        assertNull(status.getNonCompliantRole());
        assertEquals(0, status.getReasons().size());
    }

    /*
     * Tests that a consumer is syspurpose compliant when their specified addon value matches their
     * entitlement's addon value case insensitively.
     */
    @Test
    public void testConsumerAddonIsCompliantCaseInsensitively() {
        Consumer consumer = mockConsumer();
        Set<String> addons = new HashSet<>();
        addons.add("mY_aDDoN");
        consumer.setAddOns(addons);

        Product prod1 = new Product();
        prod1.setAttribute(Product.Attributes.ADDONS, "my_addon");
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockEntitlement(consumer, TestUtil.createProduct("Awesome Product"), prod1));

        SystemPurposeComplianceStatus status =
            complianceRules.getStatus(consumer, ents, null, false);

        assertTrue(status.isCompliant());
        assertEquals(0, status.getNonCompliantAddOns().size());
        assertEquals(0, status.getReasons().size());
    }

    /*
     * Tests that a consumer is syspurpose compliant when their specified usage value matches their
     * entitlement's usage value case insensitively.
     */
    @Test
    public void testConsumerUsageIsCompliantCaseInsensitively() {
        Consumer consumer = mockConsumer();
        consumer.setUsage("mY_uSaGe");

        Product prod1 = new Product();
        prod1.setAttribute(Product.Attributes.USAGE, "my_usage");
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockEntitlement(consumer, TestUtil.createProduct("Awesome Product"), prod1));

        SystemPurposeComplianceStatus status =
            complianceRules.getStatus(consumer, ents, null, false);

        assertTrue(status.isCompliant());
        assertNull(status.getNonCompliantUsage());
        assertEquals(0, status.getReasons().size());
    }

    /*
     * Tests that a consumer is syspurpose compliant when their specified SLA value matches their
     * entitlement's SLA value case insensitively.
     */
    @Test
    public void testConsumerSLAIsCompliantCaseInsensitively() {
        Consumer consumer = mockConsumer();
        consumer.setServiceLevel("mY_sLA");

        Product prod1 = new Product();
        prod1.setAttribute(Product.Attributes.SUPPORT_LEVEL, "my_sla");
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockEntitlement(consumer, TestUtil.createProduct("Awesome Product"), prod1));

        SystemPurposeComplianceStatus status =
            complianceRules.getStatus(consumer, ents, null, false);

        assertTrue(status.isCompliant());
        assertNull(status.getNonCompliantSLA());
        assertEquals(0, status.getReasons().size());
    }

    /*
     * Tests that a consumer is syspurpose compliant when their specified role value matches their
     * entitlement's role value which happens to have trailing white space.
     */
    @Test
    public void testConsumerRoleIsCompliantAgainstPoolRoleWithTrailingSpaces() {
        Consumer consumer = mockConsumer();
        consumer.setRole("mY_rOlE");

        Product prod1 = new Product();
        prod1.setAttribute(Product.Attributes.ROLES, " my_role ");
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockEntitlement(consumer, TestUtil.createProduct("Awesome Product"), prod1));

        SystemPurposeComplianceStatus status =
            complianceRules.getStatus(consumer, ents, null, false);

        assertTrue(status.isCompliant());
        assertNull(status.getNonCompliantRole());
        assertEquals(0, status.getReasons().size());
    }

    /*
     * Tests that a consumer is syspurpose compliant when their specified addon value matches their
     * entitlement's addon value which happens to have trailing white space.
     */
    @Test
    public void testConsumerAddonIsCompliantAgainstPoolAddonWithTrailingSpaces() {
        Consumer consumer = mockConsumer();
        Set<String> addons = new HashSet<>();
        addons.add("mY_aDDoN");
        consumer.setAddOns(addons);

        Product prod1 = new Product();
        prod1.setAttribute(Product.Attributes.ADDONS, " random_addon , my_addon , another_addon ");
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockEntitlement(consumer, TestUtil.createProduct("Awesome Product"), prod1));

        SystemPurposeComplianceStatus status =
            complianceRules.getStatus(consumer, ents, null, false);

        assertTrue(status.isCompliant());
        assertEquals(0, status.getNonCompliantAddOns().size());
        assertEquals(0, status.getReasons().size());
    }

    /*
     * Tests that a consumer is syspurpose compliant when their specified role value (which has trailing
     * whitespaces) matches their entitlement's role value (which doesn't have trailing whitespaces).
     */
    @Test
    public void testConsumerRoleWithTrailingWhitespaceIsCompliant() {
        Consumer consumer = mockConsumer();
        consumer.setRole(" mY_rOlE ");

        Product prod1 = new Product();
        prod1.setAttribute(Product.Attributes.ROLES, "my_role");
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockEntitlement(consumer, TestUtil.createProduct("Awesome Product"), prod1));

        SystemPurposeComplianceStatus status =
            complianceRules.getStatus(consumer, ents, null, false);

        assertTrue(status.isCompliant());
        assertNull(status.getNonCompliantRole());
        assertEquals(0, status.getReasons().size());
    }

    /*
     * Tests that a consumer is syspurpose compliant when their specified addon value (which has trailing
     * whitespaces) matches their entitlement's addon value (which doesn't have trailing whitespaces).
     */
    @Test
    public void testConsumerAddonWithTrailingWhitespaceIsCompliant() {
        Consumer consumer = mockConsumer();
        Set<String> addons = new HashSet<>();
        addons.add(" mY_aDDoN ");
        consumer.setAddOns(addons);

        Product prod1 = new Product();
        prod1.setAttribute(Product.Attributes.ADDONS, "random_addon,my_addon,another_addon");
        List<Entitlement> ents = new LinkedList<>();
        ents.add(mockEntitlement(consumer, TestUtil.createProduct("Awesome Product"), prod1));

        SystemPurposeComplianceStatus status =
            complianceRules.getStatus(consumer, ents, null, false);

        assertTrue(status.isCompliant());
        assertEquals(0, status.getNonCompliantAddOns().size());
        assertEquals(0, status.getReasons().size());
    }
}
