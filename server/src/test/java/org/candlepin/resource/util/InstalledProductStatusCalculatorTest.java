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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.audit.EventSink;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.jackson.ProductCachedSerializationModule;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
//import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.JsRunnerFactory;
import org.candlepin.policy.js.JsRunnerRequestCache;
import org.candlepin.policy.js.JsRunnerRequestCacheFactory;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.compliance.StatusReasonMessageGenerator;
import org.candlepin.test.MockResultIterator;
import org.candlepin.test.TestUtil;
import org.candlepin.util.DateRange;
import org.candlepin.util.Util;

import com.google.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;



/**
 * InstalledProductStatusCalculatorTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class InstalledProductStatusCalculatorTest {
    private ComplianceRules complianceRules;

    @Mock private ConsumerCurator consumerCurator;
    @Mock private ConsumerTypeCurator consumerTypeCurator;
    @Mock private EntitlementCurator entCurator;
    @Mock private EnvironmentCurator environmentCurator;
    @Mock private RulesCurator rulesCuratorMock;
    @Mock private EventSink eventSink;
    //@Mock private Provider<JsRunnerRequestCache> cacheProvider;
    @Mock private JsRunnerRequestCacheFactory cacheProvider;
    @Mock private JsRunnerRequestCache cache;
    @Mock private PoolCurator poolCurator;
    @Mock private ProductCurator productCurator;
    @Mock private OwnerProductCurator ownerProductCurator;
    @Mock private OwnerCurator ownerCurator;

    private ModelTranslator translator;
//    private JsRunnerProvider provider;
    private JsRunnerFactory provider;
    private I18n i18n;
    private ConsumerEnricher consumerEnricher;

    @BeforeEach
    public void setUp() {
        translator = new StandardTranslator(this.consumerTypeCurator,
            this.environmentCurator,
            this.ownerCurator);

        // Load the default production rules:
        InputStream is = this.getClass().getResourceAsStream(RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));
        Locale locale = new Locale("en_US");

        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);
        when(cacheProvider.getObject()).thenReturn(cache);

        //this.provider = new JsRunnerProvider(rulesCuratorMock, cacheProvider);
        this.provider = new JsRunnerFactory(rulesCuratorMock, cacheProvider);
        i18n = I18nFactory.getI18n(getClass(), "org.candlepin.i18n.Messages", locale, I18nFactory.FALLBACK);

        RulesObjectMapper objectMapper =
            new RulesObjectMapper(new ProductCachedSerializationModule(productCurator));

        this.complianceRules = new ComplianceRules(provider.getObject(), this.entCurator,
            new StatusReasonMessageGenerator(i18n), eventSink, this.consumerCurator, this.consumerTypeCurator,
            objectMapper, translator);

        this.consumerEnricher = new ConsumerEnricher(this.complianceRules, this.ownerProductCurator);
    }

    @Test
    public void validRangeForSingleValidEnitlement() {
        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);

        DateRange range = this.rangeRelativeToDate(new Date(), -6, 6);
        Entitlement entitlement = this.mockEntitlement(owner, consumer, product, range, product);
        consumer.addEntitlement(entitlement);

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);

        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range.getStartDate(), cip.getStartDate());
        assertEquals(range.getEndDate(), cip.getEndDate());
    }

    @Test
    public void validRangeForUnmappedGuestEntitlement() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range = this.rangeRelativeToDate(now, -6, 6);
        Entitlement entitlement = this.mockUnmappedGuestEntitlement(owner, consumer, product, range, product);
        consumer.addEntitlement(entitlement);

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);

        Date expectedEnd = new Date(now.getTime() + (24 * 60 * 60 * 1000));

        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range.getStartDate(), cip.getStartDate());
        assertEquals(expectedEnd, cip.getEndDate());
    }

    @Test
    public void validRangeIgnoresExpiredWithNoOverlap() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, -6, -3);
        DateRange range2 = this.rangeRelativeToDate(now, -1, 6);
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range2, product));
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range1, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);

        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range2.getStartDate(), cip.getStartDate());
        assertEquals(range2.getEndDate(), cip.getEndDate());
    }

    @Test
    public void validRangeIgnoresFutureWithNoOverlap() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, 6, 12);
        DateRange range2 = this.rangeRelativeToDate(now, -1, 4);
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range2, product));
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range1, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);
        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range2.getStartDate(), cip.getStartDate());
        assertEquals(range2.getEndDate(), cip.getEndDate());
    }

    @Test
    public void enricherSetsArchVersion() {
        //test that the enricher sets the arch and version when they are null in the CIP

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);

        DateRange range = this.rangeRelativeToDate(new Date(), -1, 4);
        Entitlement entitlement = this.mockEntitlement(owner, consumer, product, range, product);
        consumer.addEntitlement(entitlement);

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());

        ConsumerInstalledProduct cip = new ConsumerInstalledProduct();
        cip.setProductId(product.getId());
        consumer.addInstalledProduct(cip);

        this.mockOwnerProducts(owner, Arrays.asList(product));

        product.setAttribute(Product.Attributes.ARCHITECTURE, "candlepin arch");
        product.setAttribute(Product.Attributes.VERSION, "candlepin version");

        this.consumerEnricher.enrich(consumer);
        assertEquals("candlepin arch", cip.getArch());
        assertEquals("candlepin version", cip.getVersion());
    }

    @Test
    public void enricherDoesntSetArchVersion() {
        //test that the enricher does not set the arch and version when they are populated
        // in the CIP
        Owner owner = TestUtil.createOwner();
        owner.setId(TestUtil.randomString());
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);

        DateRange range = this.rangeRelativeToDate(new Date(), -1, 4);
        Entitlement entitlement = this.mockEntitlement(owner, consumer, product, range, product);
        consumer.addEntitlement(entitlement);

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());

        ConsumerInstalledProduct cip = new ConsumerInstalledProduct();
        cip.setProductId(product.getId());
        consumer.addInstalledProduct(cip);

        this.mockOwnerProducts(owner, Arrays.asList(product));

        // Set these to non-null values to show they aren't overwritten
        cip.setArch("original arch");
        cip.setVersion("original version");

        product.setAttribute(Product.Attributes.ARCHITECTURE, "candlepin arch");
        product.setAttribute(Product.Attributes.VERSION, "candlepin version");

        this.consumerEnricher.enrich(consumer);

        assertEquals("original arch", cip.getArch());
        assertEquals("original version", cip.getVersion());
    }

    @Test
    public void validRangeIgnoresFutureWithOverlap() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, 12, 24);
        DateRange range2 = this.rangeRelativeToDate(now, 0, 13);
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range2, product));
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range1, product));
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range1, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);
        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range2.getStartDate(), cip.getStartDate());
        assertEquals(range1.getEndDate(), cip.getEndDate());
    }

    @Test
    public void validRangeIgnoresFutureBackToBack() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, 12, 24);
        DateRange range2 = this.rangeRelativeToDate(now, 0, 12);
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range2, product));
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range1, product));
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range1, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);
        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range2.getStartDate(), cip.getStartDate());
        assertEquals(range1.getEndDate(), cip.getEndDate());
    }

    @Test
    public void validRangeWithMultipleEntsWithOverlap() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, -4, 4);
        DateRange range2 = this.rangeRelativeToDate(now, 1, 8);
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range2, product));
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range1, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);
        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range1.getStartDate(), cip.getStartDate());
        assertEquals(range2.getEndDate(), cip.getEndDate());
    }

    @Test
    public void validRangeWithMultipleWhereOneConsumesTheOthersSpan() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, -4, 4);
        DateRange range2 = this.rangeRelativeToDate(now, 0, 2);
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range2, product));
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range1, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);
        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range1.getStartDate(), cip.getStartDate());
        assertEquals(range1.getEndDate(), cip.getEndDate());
    }

    @Test
    public void validRangeWithMultipleWhereFutureEntitlementOverlaps() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, -4, 2);
        DateRange range2 = this.rangeRelativeToDate(now, 2, 4);
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range2, product));
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range1, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);
        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range1.getStartDate(), cip.getStartDate());
        assertEquals(range2.getEndDate(), cip.getEndDate());
    }

    @Test
    public void validRangeWithMultipleWhereExpiredEntitlementOverlaps() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, -4, 2);
        DateRange range2 = this.rangeRelativeToDate(now, -7, -3);
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range2, product));
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range1, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);
        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range2.getStartDate(), cip.getStartDate());
        assertEquals(range1.getEndDate(), cip.getEndDate());
    }

    @Test
    public void validRangeIsNullWhenOnlyFutureEntitlementExists() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, 4, 2);
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range1, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);
        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(null, cip.getStartDate());
        assertEquals(null, cip.getEndDate());
    }

    @Test
    public void validRangeIsNullWhenOnlyExpiredEntitlementExists() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, -4, -2);
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range1, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);
        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(null, cip.getStartDate());
        assertEquals(null, cip.getEndDate());
    }

    // Stacking becomes involved here.
    @Test
    public void validRangeNotNullWhenOnlyPartialEntitlement() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, -4, 4);
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range1, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);
        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range1.getStartDate(), cip.getStartDate());
        assertEquals(range1.getEndDate(), cip.getEndDate());
    }

    @Test
    public void validRangeCorrectPartialEntitlementNoGap() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, -4, 4);
        DateRange range2 = this.rangeRelativeToDate(now, 4, 9);
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range1, product));
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range2, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);

        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range1.getStartDate(), cip.getStartDate());
        assertEquals(range2.getEndDate(), cip.getEndDate());
    }

    @Test
    public void validRangeCorrectPartialEntitlementGap() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, -4, 4);
        DateRange range2 = this.rangeRelativeToDate(now, 5, 9);
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range1, product));
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range2, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);
        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range1.getStartDate(), cip.getStartDate());
        assertEquals(range1.getEndDate(), cip.getEndDate());
    }

    @Test
    public void multiEntGreenNowYellowFutureWithOverlap() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, -4, 12);
        DateRange range2 = this.rangeRelativeToDate(now, 11, 24);
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range1, product));
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range1, product));
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range2, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);
        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range1.getStartDate(), cip.getStartDate());
        assertEquals(range1.getEndDate(), cip.getEndDate());
    }

    @Test
    public void multiEntGreenNowInnerDatesYellowFutureWithOverlap() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, -4, 12);
        DateRange range2 = this.rangeRelativeToDate(now, -3, 10);
        DateRange range3 = this.rangeRelativeToDate(now, 11, 24);
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range1, product));
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range2, product));
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range3, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);
        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range2.getStartDate(), cip.getStartDate());
        assertEquals(range2.getEndDate(), cip.getEndDate());
    }

    // Test valid range with a full stack where one stacked entitlement provides the product
    @Test
    public void validRangeWhenStackedButOneProvides() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Product product2 = TestUtil.createProduct("p2", "product2");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, -4, 4);

        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range1, product));
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product2, 1, range1, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);
        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range1.getStartDate(), cip.getStartDate());
        assertEquals(range1.getEndDate(), cip.getEndDate());
    }

    @Test
    public void validRangeEndDateSetToFirstDateOfLosingValidStatus() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, -4, 4);
        DateRange range2 = this.rangeRelativeToDate(now, -2, 10);
        DateRange range3 = this.rangeRelativeToDate(now, -3, -1);
        DateRange range4 = this.rangeRelativeToDate(range1.getEndDate(), 0, 10);

        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range1, product));
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range2, product));
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range3, product));
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range4, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);
        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range3.getStartDate(), cip.getStartDate());
        assertEquals(range2.getEndDate(), cip.getEndDate());
    }

    @Test
    public void cannotStackFutureSubs() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, -4, 12);
        DateRange range2 = this.rangeRelativeToDate(range1.getEndDate(), 5, 6);

        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range1, product));
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range2, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        ComplianceStatus status = complianceRules.getStatus(consumer, now);
        assertEquals("partial", status.getStatus());
        assertTrue(status.getPartialStacks().containsKey("stack_id_1"));
    }

    @Test
    public void validRangeConsidersInvalidGapBetweenNonStackedAndPartialEntitlement() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, -4, -2);
        DateRange range2 = this.rangeRelativeToDate(now, -3, 2);
        DateRange range3 = this.rangeRelativeToDate(now, -1, 4);

        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range1, product));
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range2, product));
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 1, range3, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);
        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range3.getStartDate(), cip.getStartDate());
        assertEquals(range2.getEndDate(), cip.getEndDate());
    }

    @Test
    public void validRangeConsidersInvalidGapBetweenNonStackedEntitlement() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Product stacked = TestUtil.createProduct("p1_stack", "product1-stacked");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, -4, -2);
        DateRange range2 = this.rangeRelativeToDate(now, -3, 2);
        DateRange range3 = this.rangeRelativeToDate(now, -1, 4);

        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range1, product));
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", stacked, 1, range2, product));
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range3, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);
        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range3.getStartDate(), cip.getStartDate());
        assertEquals(range3.getEndDate(), cip.getEndDate());
    }

    @Test
    public void validRangeConsidersNonStackingEntNotCoveringMachineSocketsInvalid() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        Product sockets = TestUtil.createProduct("socketed", "socketed_product");
        sockets.setAttribute(Product.Attributes.SOCKETS, "2");
        Consumer consumer = this.mockConsumer(owner, product);
        consumer.setCreated(now);

        DateRange range1 = this.rangeRelativeToDate(now, -4, 4);
        DateRange range2 = this.rangeRelativeToDate(now, -2, 6);

        consumer.addEntitlement(this.mockEntitlement(owner, consumer, sockets, range1, product));
        consumer.addEntitlement(this.mockEntitlement(owner, consumer, product, range2, product));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product));

        this.consumerEnricher.enrich(consumer);
        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range2.getStartDate(), cip.getStartDate());
        assertEquals(range2.getEndDate(), cip.getEndDate());
    }

    @Test
    public void validRangeWhenGuestLimitOverridden() {
        Date now = new Date();

        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct("p1", "product1");
        product.setAttribute(Product.Attributes.GUEST_LIMIT, "2");

        Product product2 = TestUtil.createProduct("p2", "product2");
        product2.setAttribute(Product.Attributes.GUEST_LIMIT, "-1");

        Product product3 = TestUtil.createProduct("p3", "product3");

        Consumer consumer = this.mockConsumer(owner, product);
        for (int i = 0; i < 5; i++) {
            consumer.addGuestId(new GuestId(String.valueOf(i), consumer, this.getActiveGuestAttrs()));
        }

        DateRange range1 = this.rangeRelativeToDate(now, -4, 4);
        DateRange range2 = this.rangeRelativeToDate(now, -2, 2);

        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_1", product, 10, range1, product));
        consumer.addEntitlement(
            this.mockStackedEntitlement(owner, consumer, "stack_id_2", product2, 10, range2, product3));

        this.mockConsumerEntitlements(consumer, consumer.getEntitlements());
        this.mockOwnerProducts(owner, Arrays.asList(product, product2, product3));

        this.consumerEnricher.enrich(consumer);

        ConsumerInstalledProduct cip = this.getInstalledProduct(consumer, product);
        assertEquals(range2.getStartDate(), cip.getStartDate());
        assertEquals(range2.getEndDate(), cip.getEndDate());
    }

    private static int lastPoolId = 1;
    private Entitlement mockEntitlement(Owner owner, Consumer consumer, Product product, DateRange range,
        Product... providedProducts) {

        Set<Product> provided = new HashSet<>();
        for (Product pp : providedProducts) {
            provided.add(pp);
        }

        final Pool p = new Pool(
            owner,
            product,
            provided,
            new Long(1000),
            range.getStartDate(),
            range.getEndDate(),
            "1000",
            "1000",
            "1000"
        );

        p.setId("" + lastPoolId++);
        Entitlement e = new Entitlement(p, consumer, owner, 1);

        when(poolCurator.provides(p, product.getId())).thenReturn(true);

        for (Product pp : providedProducts) {
            when(poolCurator.provides(p, pp.getId())).thenReturn(true);
        }

        Random gen = new Random();
        int id = gen.nextInt(Integer.MAX_VALUE);
        e.setId(String.valueOf(id));

        return e;
    }

    private Entitlement mockUnmappedGuestEntitlement(Owner owner, Consumer consumer, Product product,
        DateRange range, Product ... providedProducts) {

        consumer.setFact("virt.is_guest", "True");
        Entitlement e = mockEntitlement(owner, consumer, product, range, providedProducts);
        Pool p = e.getPool();
        Date endDateOverride = new Date(consumer.getCreated().getTime() + (24 * 60 * 60 * 1000));
        e.setEndDateOverride(endDateOverride);

        // Setup the attributes for stacking:
        p.setAttribute("virt_only", "true");
        p.setAttribute("unmapped_guests_only", "true");
        product.setAttribute("virt_limit", "unlimited");

        return e;
    }

    private Consumer mockConsumer(Owner owner, Product... installedProducts) {
        ConsumerType ctype = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        ctype.setId("test-ctype-" + TestUtil.randomInt());

        Consumer consumer = new Consumer();
        consumer.setType(ctype);
        consumer.setOwner(owner);

        for (Product product : installedProducts) {
            consumer.addInstalledProduct(new ConsumerInstalledProduct(consumer, product));
        }

        consumer.setFact("cpu.cpu_socket(s)", "4");

        when(this.consumerTypeCurator.get(eq(ctype.getId()))).thenReturn(ctype);
        when(this.consumerTypeCurator.getConsumerType(eq(consumer))).thenReturn(ctype);

        return consumer;
    }

    private Entitlement mockStackedEntitlement(Owner owner, Consumer consumer, String stackId,
        Product product, int quantity, DateRange range, Product ... providedProducts) {

        Entitlement entitlement = this.mockEntitlement(owner, consumer, product, range, providedProducts);
        entitlement.setQuantity(quantity);

        Pool pool = entitlement.getPool();

        // Setup the attributes for stacking:
        pool.getProduct().setAttribute("stacking_id", stackId);
        pool.getProduct().setAttribute("sockets", "2");

        return entitlement;
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

    private void mockConsumerEntitlements(Consumer consumer, Collection<Entitlement> entitlements) {
        List<Entitlement> entList = new LinkedList(entitlements);

        CandlepinQuery mockCPQuery = mock(CandlepinQuery.class);
        when(mockCPQuery.list()).thenReturn(entList);
        when(mockCPQuery.iterator()).thenReturn(entitlements.iterator());

        when(entCurator.listByConsumer(eq(consumer))).thenReturn(entList);
        when(entCurator.listByConsumerAndDate(eq(consumer), any(Date.class))).thenReturn(mockCPQuery);
    }

    private Map<String, String> getActiveGuestAttrs() {
        Map<String, String> activeGuestAttrs = new HashMap<>();
        activeGuestAttrs.put("virtWhoType", "libvirt");
        activeGuestAttrs.put("active", "1");

        return activeGuestAttrs;
    }

    private void mockOwnerProducts(Owner owner, Collection<Product> products) {
        final Map<String, Product> productMap = new HashMap<>();
        for (Product product : products) {
            productMap.put(product.getId(), product);
        }

        doAnswer(new Answer<CandlepinQuery<Product>>() {
            @Override
            public CandlepinQuery<Product> answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                Collection<String> productIds = (Collection<String>) args[1];

                Collection<Product> products = new LinkedList<>();
                for (String productId : productIds) {
                    Product product = productMap.get(productId);

                    if (product != null) {
                        products.add(product);
                    }
                }

                CandlepinQuery cqmock = mock(CandlepinQuery.class);
                when(cqmock.iterator()).thenReturn(products.iterator());
                when(cqmock.iterate()).thenReturn(new MockResultIterator(products.iterator()));

                return cqmock;
            }
        }).when(this.ownerProductCurator).getProductsByIds(eq(owner.getId()), anyCollection());
    }

    private ConsumerInstalledProduct getInstalledProduct(Consumer consumer, Product product) {
        for (ConsumerInstalledProduct cip : consumer.getInstalledProducts()) {
            if (cip.getProductId().equals(product.getId())) {
                return cip;
            }
        }

        return null;
    }
}
