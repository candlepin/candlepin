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
package org.canadianTenPin.policy.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.canadianTenPin.model.Consumer;
import org.canadianTenPin.model.ConsumerType;
import org.canadianTenPin.model.Entitlement;
import org.canadianTenPin.model.Pool;
import org.canadianTenPin.model.PoolAttribute;
import org.canadianTenPin.model.Product;
import org.canadianTenPin.model.ProductAttribute;
import org.canadianTenPin.model.Rules;
import org.canadianTenPin.model.RulesCurator;
import org.canadianTenPin.policy.js.export.ExportRules;
import org.canadianTenPin.util.Util;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.InputStream;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;


/**
 * JsExportRulesTest: Tests for the default rules.
 */
@RunWith(MockitoJUnitRunner.class)
public class ExportRulesTest {

    private ExportRules exportRules;

    @Mock private RulesCurator rulesCuratorMock;

    @Before
    public void setUp() {

        // Load the default production rules:
        InputStream is = this.getClass().getResourceAsStream(
            RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));

        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);

        exportRules = new ExportRules();
    }

    @Test
    public void cannotExportProduct() throws NoSuchMethodException {
        Entitlement entitlement = mock(Entitlement.class);
        Consumer consumer = mock(Consumer.class);
        ConsumerType type = mock(ConsumerType.class);
        Pool pool = new Pool();
        pool.setProductId("12345");
        pool.setAttribute("pool_derived", "true");

        when(entitlement.getPool()).thenReturn(pool);
        when(entitlement.getConsumer()).thenReturn(consumer);
        when(consumer.getType()).thenReturn(type);
        when(type.isManifest()).thenReturn(true);

        assertFalse(exportRules.canExport(entitlement));
    }

    @Test
    public void canExportProductConsumer() throws NoSuchMethodException {
        Entitlement entitlement = mock(Entitlement.class);
        Consumer consumer = mock(Consumer.class);
        ConsumerType consumerType = mock(ConsumerType.class);
        Pool pool = mock(Pool.class);
        Product product = mock(Product.class);
        Set<PoolAttribute> attributes = new HashSet<PoolAttribute>();
        attributes.add(new PoolAttribute("pool_derived", "true"));


        when(entitlement.getPool()).thenReturn(pool);
        when(entitlement.getConsumer()).thenReturn(consumer);
        when(pool.getProductId()).thenReturn("12345");
        when(product.getAttributes()).thenReturn(new HashSet<ProductAttribute>());
        when(pool.getAttributes()).thenReturn(attributes);
        when(consumer.getType()).thenReturn(consumerType);
        when(consumerType.getLabel()).thenReturn("system");

        assertTrue(exportRules.canExport(entitlement));
    }

    @Test
    public void canExportProductVirt() throws NoSuchMethodException {
        Entitlement entitlement = mock(Entitlement.class);
        Consumer consumer = mock(Consumer.class);
        ConsumerType consumerType = mock(ConsumerType.class);
        Pool pool = mock(Pool.class);
        Product product = mock(Product.class);
        Set<PoolAttribute> attributes = new HashSet<PoolAttribute>();

        when(entitlement.getPool()).thenReturn(pool);
        when(entitlement.getConsumer()).thenReturn(consumer);
        when(pool.getProductId()).thenReturn("12345");
        when(product.getAttributes()).thenReturn(new HashSet<ProductAttribute>());
        when(pool.getAttributes()).thenReturn(attributes);
        when(consumer.getType()).thenReturn(consumerType);
        when(consumerType.getLabel()).thenReturn("canadianTenPin");

        assertTrue(exportRules.canExport(entitlement));
    }
}
