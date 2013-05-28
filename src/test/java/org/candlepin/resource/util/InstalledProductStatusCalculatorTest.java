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
package org.candlepin.resource.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.compliance.StatusReasonMessageGenerator;
import org.candlepin.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

/**
 * InstalledProductStatusCalculatorTest
 */
@RunWith(MockitoJUnitRunner.class)
public class InstalledProductStatusCalculatorTest {
    private Owner owner;
    private ComplianceRules compliance;

    private static final String PRODUCT_1 = "product1";
    private static final String STACK_ID_1 = "my-stack-1";

    @Mock private EntitlementCurator entCurator;
    @Mock private RulesCurator rulesCuratorMock;
    private JsRunnerProvider provider;
    private I18n i18n;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // Load the default production rules:
        InputStream is = this.getClass().getResourceAsStream(
            RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));
        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);
        provider = new JsRunnerProvider(rulesCuratorMock);
        Locale locale = new Locale("en_US");
        i18n = I18nFactory.getI18n(getClass(), "org.candlepin.i18n.Messages", locale,
            I18nFactory.FALLBACK);
        compliance = new ComplianceRules(provider.get(),
            entCurator, new StatusReasonMessageGenerator(i18n));
        owner = new Owner("test");
    }

    @Test
    public void validRangeForSingleValidEnitlement() {
        Consumer c = mockConsumer(PRODUCT_1);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();

        DateRange entRange = rangeRelativeToDate(now, -6, 6);
        c.addEntitlement(mockEntitlement(c, PRODUCT_1, entRange, PRODUCT_1));

        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumer(eq(c))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, now);
        ConsumerInstalledProductEnricher calculator =
            new ConsumerInstalledProductEnricher(c, status, compliance);
        Product p = new Product(PRODUCT_1, "Awesome Product");
        DateRange validRange = calculator.getValidDateRange(p);
        assertEquals(entRange.getStartDate(), validRange.getStartDate());
        assertEquals(entRange.getEndDate(), validRange.getEndDate());
    }

    @Test
    public void validRangeIgnoresExpiredWithNoOverlap() {
        Consumer c = mockConsumer(PRODUCT_1);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        DateRange range1 = rangeRelativeToDate(now, -6, -3);
        DateRange range2 = rangeRelativeToDate(now, -1, 6);

        // Add current entitlement
        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range2, PRODUCT_1));
        // Add expired entitlement
        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range1, PRODUCT_1));

        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumer(eq(c))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, now);
        ConsumerInstalledProductEnricher calculator =
            new ConsumerInstalledProductEnricher(c, status, compliance);
        Product p = new Product(PRODUCT_1, "Awesome Product");
        DateRange validRange = calculator.getValidDateRange(p);
        assertEquals(range2.getStartDate(), validRange.getStartDate());
        assertEquals(range2.getEndDate(), validRange.getEndDate());
    }

    @Test
    public void validRangeIgnoresFutureWithNoOverlap() {
        Consumer c = mockConsumer(PRODUCT_1);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        DateRange range1 = rangeRelativeToDate(now, 6, 12);
        DateRange range2 = rangeRelativeToDate(now, -1, 4);

        // Add current entitlement
        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range2, PRODUCT_1));
        // Add future entitlement
        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range1, PRODUCT_1));

        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumer(eq(c))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, now);
        ConsumerInstalledProductEnricher calculator =
            new ConsumerInstalledProductEnricher(c, status, compliance);
        Product p = new Product(PRODUCT_1, "Awesome Product");
        DateRange validRange = calculator.getValidDateRange(p);
        assertEquals(range2.getStartDate(), validRange.getStartDate());
        assertEquals(range2.getEndDate(), validRange.getEndDate());
    }

    @Test
    public void enricherSetsArchVersion() {
        //test that the enricher sets the arch and version
        //when they are supplied as null
        Consumer c = mockConsumer(PRODUCT_1);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        DateRange range2 = rangeRelativeToDate(now, -1, 4);
        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range2, PRODUCT_1));
        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumer(eq(c))).thenReturn(ents);
        ComplianceStatus status = compliance.getStatus(c, now);
        status.addNonCompliantProduct(PRODUCT_1);
        ConsumerInstalledProduct cip = new ConsumerInstalledProduct();
        c.addInstalledProduct(cip);
        ConsumerInstalledProductEnricher calculator =
            new ConsumerInstalledProductEnricher(c, status, compliance);
        Product p = new Product(PRODUCT_1, "Awesome Product");
        p.setAttribute("version", "candlepin version");
        p.setAttribute("arch", "candlepin arch");
        calculator.enrich(cip, p);
        assertEquals("candlepin version", cip.getVersion());
        assertEquals("candlepin arch", cip.getArch());
    }

    @Test
    public void enricherDoesntSetArchVersion() {
        //test that the enricher does not set the arch and version
        //when they are supplied with values
        Consumer c = mockConsumer(PRODUCT_1);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        DateRange range2 = rangeRelativeToDate(now, -1, 4);
        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range2, PRODUCT_1));
        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumer(eq(c))).thenReturn(ents);
        ComplianceStatus status = compliance.getStatus(c, now);
        status.addNonCompliantProduct(PRODUCT_1);
        ConsumerInstalledProduct cip = new ConsumerInstalledProduct();
        cip.setArch("x86_64");
        cip.setVersion("4.5");
        c.addInstalledProduct(cip);
        ConsumerInstalledProductEnricher calculator =
            new ConsumerInstalledProductEnricher(c, status, compliance);
        Product p = new Product(PRODUCT_1, "Awesome Product");
        p.setAttribute("version", "candlepin version");
        p.setAttribute("arch", "candlepin arch");
        calculator.enrich(cip, p);
        assertEquals("4.5", cip.getVersion());
        assertEquals("x86_64", cip.getArch());
    }

    @Test
    public void validRangeIgnoresFutureWithOverlap() {
        Consumer c = mockConsumer(PRODUCT_1);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        DateRange future = rangeRelativeToDate(now, 12, 24);
        DateRange current = rangeRelativeToDate(now, 0, 13);

        // Add current entitlement
        c.addEntitlement(mockEntitlement(c, PRODUCT_1, current, PRODUCT_1));
        // Add future entitlement
        c.addEntitlement(mockEntitlement(c, PRODUCT_1, future, PRODUCT_1));
        // Add future entitlement
        c.addEntitlement(mockEntitlement(c, PRODUCT_1, future, PRODUCT_1));

        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumer(eq(c))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, now);
        ConsumerInstalledProductEnricher calculator =
            new ConsumerInstalledProductEnricher(c, status, compliance);
        Product p = new Product(PRODUCT_1, "Awesome Product");
        DateRange validRange = calculator.getValidDateRange(p);
        assertEquals(current.getStartDate(), validRange.getStartDate());
        assertEquals(future.getEndDate(), validRange.getEndDate());
    }

    @Test
    public void validRangeIgnoresFutureBackToBack() {
        Consumer c = mockConsumer(PRODUCT_1);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        DateRange future = rangeRelativeToDate(now, 12, 24);
        DateRange current = rangeRelativeToDate(now, 0, 12);

        // Add current entitlement
        c.addEntitlement(mockEntitlement(c, PRODUCT_1, current, PRODUCT_1));
        // Add future entitlement
        c.addEntitlement(mockEntitlement(c, PRODUCT_1, future, PRODUCT_1));
        // Add future entitlement
        c.addEntitlement(mockEntitlement(c, PRODUCT_1, future, PRODUCT_1));

        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumer(eq(c))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, now);
        ConsumerInstalledProductEnricher calculator =
            new ConsumerInstalledProductEnricher(c, status, compliance);
        Product p = new Product(PRODUCT_1, "Awesome Product");
        DateRange validRange = calculator.getValidDateRange(p);
        assertEquals(current.getStartDate(), validRange.getStartDate());
        assertEquals(future.getEndDate(), validRange.getEndDate());
    }

    @Test
    public void validRangeWithMultipleEntsWithOverlap() {
        Consumer c = mockConsumer(PRODUCT_1);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        DateRange range1 = rangeRelativeToDate(now, -4, 4);
        DateRange range2 = rangeRelativeToDate(now, 1, 8);

        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range2, PRODUCT_1));
        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range1, PRODUCT_1));

        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumer(eq(c))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, now);
        ConsumerInstalledProductEnricher calculator =
            new ConsumerInstalledProductEnricher(c, status, compliance);
        Product p = new Product(PRODUCT_1, "Awesome Product");
        DateRange validRange = calculator.getValidDateRange(p);
        assertEquals(range1.getStartDate(), validRange.getStartDate());
        assertEquals(range2.getEndDate(), validRange.getEndDate());
    }

    @Test
    public void validRangeWithMultipleWhereOneConsumesTheOthersSpan() {
        Consumer c = mockConsumer(PRODUCT_1);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        DateRange range1 = rangeRelativeToDate(now, -4, 4);
        DateRange range2 = rangeRelativeToDate(now, 0, 2);

        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range2, PRODUCT_1));
        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range1, PRODUCT_1));

        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumer(eq(c))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, now);
        ConsumerInstalledProductEnricher calculator =
            new ConsumerInstalledProductEnricher(c, status, compliance);
        Product p = new Product(PRODUCT_1, "Awesome Product");
        DateRange validRange = calculator.getValidDateRange(p);
        assertEquals(range1.getStartDate(), validRange.getStartDate());
        assertEquals(range1.getEndDate(), validRange.getEndDate());
    }

    @Test
    public void validRangeWithMultipleWhereFutureEntitlementOverlaps() {
        Consumer c = mockConsumer(PRODUCT_1);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        DateRange range1 = rangeRelativeToDate(now, -4, 2);
        DateRange range2 = rangeRelativeToDate(now, 2, 4);

        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range2, PRODUCT_1));
        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range1, PRODUCT_1));

        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumer(eq(c))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, now);
        ConsumerInstalledProductEnricher calculator =
            new ConsumerInstalledProductEnricher(c, status, compliance);
        Product p = new Product(PRODUCT_1, "Awesome Product");
        DateRange validRange = calculator.getValidDateRange(p);
        assertEquals(range1.getStartDate(), validRange.getStartDate());
        assertEquals(range2.getEndDate(), validRange.getEndDate());
    }

    @Test
    public void validRangeWithMultipleWhereExpiredEntitlementOverlaps() {
        Consumer c = mockConsumer(PRODUCT_1);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        DateRange range1 = rangeRelativeToDate(now, -4, 2);
        DateRange range2 = rangeRelativeToDate(now, -7, -3);

        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range2, PRODUCT_1));
        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range1, PRODUCT_1));

        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumer(eq(c))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, now);
        ConsumerInstalledProductEnricher calculator =
            new ConsumerInstalledProductEnricher(c, status, compliance);
        Product p = new Product(PRODUCT_1, "Awesome Product");
        DateRange validRange = calculator.getValidDateRange(p);
        assertEquals(range2.getStartDate(), validRange.getStartDate());
        assertEquals(range1.getEndDate(), validRange.getEndDate());
    }

    @Test
    public void validRangeIsNullWhenOnlyFutureEntitlementExists() {
        Consumer c = mockConsumer(PRODUCT_1);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        DateRange range1 = rangeRelativeToDate(now, 4, 2);

        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range1, PRODUCT_1));

        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, now);
        ConsumerInstalledProductEnricher calculator =
            new ConsumerInstalledProductEnricher(c, status, compliance);
        Product p = new Product(PRODUCT_1, "Awesome Product");
        DateRange validRange = calculator.getValidDateRange(p);
        assertNull(validRange);
    }

    @Test
    public void validRangeIsNullWhenOnlyExpiredEntitlementExists() {
        Consumer c = mockConsumer(PRODUCT_1);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        DateRange range1 = rangeRelativeToDate(now, -4, -2);

        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range1, PRODUCT_1));

        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, now);
        ConsumerInstalledProductEnricher calculator =
            new ConsumerInstalledProductEnricher(c, status, compliance);
        Product p = new Product(PRODUCT_1, "Awesome Product");
        DateRange validRange = calculator.getValidDateRange(p);
        assertNull(validRange);
    }

    // Stacking becomes involved here.
    @Test
    public void validRangeNullWhenOnlyPartialEntitlement() {
        Consumer c = mockConsumer(PRODUCT_1);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        DateRange range = rangeRelativeToDate(now, -4, 4);

        c.addEntitlement(mockStackedEntitlement(c, range, STACK_ID_1, PRODUCT_1, 1,
            PRODUCT_1));

        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, now);
        ConsumerInstalledProductEnricher calculator =
            new ConsumerInstalledProductEnricher(c, status, compliance);
        Product p = new Product(PRODUCT_1, "Awesome Product");
        DateRange validRange = calculator.getValidDateRange(p);
        assertNull(validRange);
    }

    @Test
    public void validRangeEndDateSetToFirstDateOfLosingValidStatus() {
        Consumer c = mockConsumer(PRODUCT_1);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        DateRange range1 = rangeRelativeToDate(now, -4, 4);
        DateRange range2 = rangeRelativeToDate(now, -2, 10);
        DateRange range3 = rangeRelativeToDate(now, -3, -1);
        DateRange range4 = rangeRelativeToDate(range1.getEndDate(), 0, 10);

        c.addEntitlement(mockStackedEntitlement(c, range1, STACK_ID_1, PRODUCT_1, 1,
            PRODUCT_1));
        c.addEntitlement(mockStackedEntitlement(c, range2, STACK_ID_1, PRODUCT_1, 1,
            PRODUCT_1));
        c.addEntitlement(mockStackedEntitlement(c, range3, STACK_ID_1, PRODUCT_1, 1,
            PRODUCT_1));
        c.addEntitlement(mockStackedEntitlement(c, range4, STACK_ID_1, PRODUCT_1, 1,
            PRODUCT_1));

        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumer(eq(c))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, now);
        ConsumerInstalledProductEnricher calculator =
            new ConsumerInstalledProductEnricher(c, status, compliance);
        Product p = new Product(PRODUCT_1, "Awesome Product");
        DateRange validRange = calculator.getValidDateRange(p);
        assertEquals(range3.getStartDate(), validRange.getStartDate());
        assertEquals(range2.getEndDate(), validRange.getEndDate());
    }

    @Test
    public void cannotStackFutureSubs() {
        Consumer c = mockConsumer(PRODUCT_1);
        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        DateRange range1 = rangeRelativeToDate(now, -4, 4);
        DateRange range2 = rangeRelativeToDate(range1.getEndDate(), 5, 6);
        c.addEntitlement(mockStackedEntitlement(c, range1, STACK_ID_1, PRODUCT_1, 1,
            PRODUCT_1));
        c.addEntitlement(mockStackedEntitlement(c, range2, STACK_ID_1, PRODUCT_1, 1,
            PRODUCT_1));
        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumer(eq(c))).thenReturn(ents);
        ComplianceStatus status = compliance.getStatus(c, now);
        assertEquals("partial", status.getStatus());
        assertTrue(status.getPartialStacks().containsKey(STACK_ID_1));
    }

    @Test
    public void validRangeConsidersInvalidGapBetweenNonStackedAndPartialEntitlement() {
        Consumer c = mockConsumer(PRODUCT_1);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        DateRange range1 = rangeRelativeToDate(now, -4, -2);
        DateRange range2 = rangeRelativeToDate(now, -3, 2);
        DateRange range3 = rangeRelativeToDate(now, -1, 4);

        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range1, PRODUCT_1));
        c.addEntitlement(mockStackedEntitlement(c, range3, STACK_ID_1, PRODUCT_1, 1,
            PRODUCT_1));
        c.addEntitlement(mockStackedEntitlement(c, range2, STACK_ID_1, PRODUCT_1, 1,
            PRODUCT_1));

        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumer(eq(c))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, now);
        ConsumerInstalledProductEnricher calculator =
            new ConsumerInstalledProductEnricher(c, status, compliance);
        Product p = new Product(PRODUCT_1, "Awesome Product");
        DateRange validRange = calculator.getValidDateRange(p);
        assertEquals(range3.getStartDate(), validRange.getStartDate());
        assertEquals(range2.getEndDate(), validRange.getEndDate());
    }

    @Test
    public void validRangeConsidersInvalidGapBetweenNonStackedEntitlement() {
        Consumer c = mockConsumer(PRODUCT_1);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        DateRange range1 = rangeRelativeToDate(now, -4, -2);
        DateRange range2 = rangeRelativeToDate(now, -3, 2);
        DateRange range3 = rangeRelativeToDate(now, -1, 4);

        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range1, PRODUCT_1));
        c.addEntitlement(mockStackedEntitlement(c, range2, STACK_ID_1, PRODUCT_1, 1,
            PRODUCT_1));
        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range3, PRODUCT_1));

        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumer(eq(c))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, now);
        ConsumerInstalledProductEnricher calculator =
            new ConsumerInstalledProductEnricher(c, status, compliance);
        Product p = new Product(PRODUCT_1, "Awesome Product");
        DateRange validRange = calculator.getValidDateRange(p);
        assertEquals(range3.getStartDate(), validRange.getStartDate());
        assertEquals(range3.getEndDate(), validRange.getEndDate());
    }

    @Test
    public void validRangeConsidersNonStackingEntNotCoveringMachineSocketsInvalid() {
        Consumer c = mockConsumer(PRODUCT_1);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        DateRange range1 = rangeRelativeToDate(now, -4, 4);
        DateRange range2 = rangeRelativeToDate(now, -2, 6);

        Entitlement ent = mockEntitlement(c, PRODUCT_1, range1, PRODUCT_1);
        ent.getPool().setProductAttribute("sockets", "2", PRODUCT_1);
        c.addEntitlement(ent);

        c.addEntitlement(mockEntitlement(c, PRODUCT_1, range2, PRODUCT_1));

        List<Entitlement> ents = new LinkedList<Entitlement>(c.getEntitlements());
        when(entCurator.listByConsumer(eq(c))).thenReturn(ents);

        ComplianceStatus status = compliance.getStatus(c, now);
        ConsumerInstalledProductEnricher calculator =
            new ConsumerInstalledProductEnricher(c, status, compliance);
        Product p = new Product(PRODUCT_1, "Awesome Product");
        DateRange validRange = calculator.getValidDateRange(p);
        assertEquals(range2.getStartDate(), validRange.getStartDate());
        assertEquals(range2.getEndDate(), validRange.getEndDate());
    }

    private Entitlement mockEntitlement(Consumer consumer, String productId,
        DateRange range, String ... providedProductIds) {

        Set<ProvidedProduct> provided = new HashSet<ProvidedProduct>();
        for (String pid : providedProductIds) {
            provided.add(new ProvidedProduct(pid, pid));
        }
        Pool p = new Pool(owner, productId, productId, provided,
            new Long(1000), range.getStartDate(), range.getEndDate(), "1000", "1000",
            "1000");
        Entitlement e = new Entitlement(p, consumer, p.getStartDate(), p.getEndDate(), 1);

        Random gen = new Random();
        int id = gen.nextInt(Integer.MAX_VALUE);
        e.setId(String.valueOf(id));

        return e;
    }

    private Consumer mockConsumer(String ... installedProducts) {
        Consumer c = new Consumer();
        c.setType(new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM));
        for (String pid : installedProducts) {
            c.addInstalledProduct(new ConsumerInstalledProduct(pid, pid));
        }
        c.setFact("cpu.cpu_socket(s)", "4");
        return c;
    }

    private Entitlement mockStackedEntitlement(Consumer consumer, DateRange range,
        String stackId, String productId, int quantity, String ... providedProductIds) {

        Entitlement e = mockEntitlement(consumer, productId, range, providedProductIds);
        e.setQuantity(quantity);
        Pool p = e.getPool();

        // Setup the attributes for stacking:
        p.addProductAttribute(new ProductPoolAttribute("stacking_id", stackId, productId));
        p.addProductAttribute(new ProductPoolAttribute("sockets", "2", productId));

        return e;
    }

    private DateRange rangeRelativeToDate(Date relativeTo, int startMonths, int endMonths) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(relativeTo);
        cal.add(Calendar.MONTH, startMonths);
        Date start = cal.getTime();

        cal.setTime(relativeTo);
        cal.add(Calendar.MONTH, endMonths);
        Date end = cal.getTime();
        return new DateRange(start, end);
    }
}
