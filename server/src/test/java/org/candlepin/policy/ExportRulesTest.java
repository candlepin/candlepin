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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.js.export.ExportRules;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;



/**
 * JsExportRulesTest: Tests for the default rules.
 */
@RunWith(MockitoJUnitRunner.class)
public class ExportRulesTest {

    private ExportRules exportRules;

    @Mock private ConsumerTypeCurator consumerTypeCuratorMock;
    @Mock private RulesCurator rulesCuratorMock;

    @Before
    public void setUp() {

        // Load the default production rules:
        InputStream is = this.getClass().getResourceAsStream(RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));

        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);

        exportRules = new ExportRules(consumerTypeCuratorMock);
    }

    @Test
    public void cannotExportProduct() throws NoSuchMethodException {
        Entitlement entitlement = mock(Entitlement.class);

        Consumer consumer = mock(Consumer.class);
        ConsumerType consumerType = new ConsumerType("system");
        consumerType.setId("consumer_type");
        consumerType.setManifest(true);

        Product p = TestUtil.createProduct("12345");
        Pool pool = new Pool();
        pool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");

        when(entitlement.getPool()).thenReturn(pool);
        when(entitlement.getConsumer()).thenReturn(consumer);

        when(consumer.getTypeId()).thenReturn(consumerType.getId());
        when(consumerTypeCuratorMock.get(eq(consumerType.getId()))).thenReturn(consumerType);
        when(consumerTypeCuratorMock.getConsumerType(eq(consumer))).thenReturn(consumerType);

        assertFalse(exportRules.canExport(entitlement));
    }

    @Test
    public void canExportProductConsumer() throws NoSuchMethodException {
        Entitlement entitlement = mock(Entitlement.class);

        Consumer consumer = mock(Consumer.class);
        ConsumerType consumerType = new ConsumerType("system");
        consumerType.setId("consumer_type");

        Pool pool = mock(Pool.class);
        Product product = mock(Product.class);

        Map<String, String> attributes = new HashMap<>();
        attributes.put(Pool.Attributes.DERIVED_POOL, "true");

        when(entitlement.getPool()).thenReturn(pool);
        when(entitlement.getConsumer()).thenReturn(consumer);
        when(pool.getProductId()).thenReturn("12345");
        when(product.getAttributes()).thenReturn(new HashMap<>());
        when(pool.getAttributes()).thenReturn(attributes);

        when(consumer.getTypeId()).thenReturn(consumerType.getId());
        when(consumerTypeCuratorMock.get(eq(consumerType.getId()))).thenReturn(consumerType);
        when(consumerTypeCuratorMock.getConsumerType(eq(consumer))).thenReturn(consumerType);

        assertTrue(exportRules.canExport(entitlement));
    }

    @Test
    public void canExportProductVirt() throws NoSuchMethodException {
        Entitlement entitlement = mock(Entitlement.class);

        Consumer consumer = mock(Consumer.class);
        ConsumerType consumerType = new ConsumerType("candlepin");
        consumerType.setId("consumer_type");

        Pool pool = mock(Pool.class);
        Product product = mock(Product.class);

        when(entitlement.getPool()).thenReturn(pool);
        when(entitlement.getConsumer()).thenReturn(consumer);
        when(pool.getProductId()).thenReturn("12345");
        when(product.getAttributes()).thenReturn(new HashMap<>());
        when(pool.getAttributes()).thenReturn(new HashMap<>());

        when(consumer.getTypeId()).thenReturn(consumerType.getId());
        when(consumerTypeCuratorMock.get(eq(consumerType.getId()))).thenReturn(consumerType);
        when(consumerTypeCuratorMock.getConsumerType(eq(consumer))).thenReturn(consumerType);

        assertTrue(exportRules.canExport(entitlement));
    }
}
