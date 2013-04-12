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
package org.candlepin.version;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.candlepin.model.Consumer;
import org.candlepin.model.ProductAttribute;
import org.junit.Before;
import org.junit.Test;

/**
 * ProductVersionValidatorTests
 */
public class ProductVersionValidatorTests {

    private Consumer consumer;
    private Set<ProductAttribute> attrs;

    @Before
    public void setUp() {
        consumer = new Consumer();
        consumer.setFacts(new HashMap<String, String>());
        attrs = new HashSet<ProductAttribute>();
        // RAM requires version certificate_version 3.1
        attrs.add(new ProductAttribute("ram", "2"));
    }

    @Test
    public void verifyClientSupportWhenProductRequires3Dot1() {
        consumer.setFact("system.certificate_version", "3.1");
        assertTrue(ProductVersionValidator.verifyClientSupport(consumer, attrs));
    }

    @Test
    public void verifyClientSupportWhenConsumerVersionGreaterThanProductVersion() {
        consumer.setFact("system.certificate_version", "3.2");
        assertTrue(ProductVersionValidator.verifyClientSupport(consumer, attrs));

        consumer.setFact("system.certificate_version", "3.1.1");
        assertTrue(ProductVersionValidator.verifyClientSupport(consumer, attrs));

        consumer.setFact("system.certificate_version", "3.1.0");
        assertTrue(ProductVersionValidator.verifyClientSupport(consumer, attrs));
    }

    @Test
    public void verifyClientSupportWhenConsumerVersionLessThanProductVersion() {
        consumer.setFact("system.certificate_version", "1.0");
        assertFalse(ProductVersionValidator.verifyClientSupport(consumer, attrs));

        consumer.setFact("system.certificate_version", "3.0");
        assertFalse(ProductVersionValidator.verifyClientSupport(consumer, attrs));

        consumer.setFact("system.certificate_version", "2.9.99");
        assertFalse(ProductVersionValidator.verifyClientSupport(consumer, attrs));
    }

    @Test
    public void verifyConsumerVersion1Point0WhenConsumerVersionIsNull() {
        consumer.setFact("system.certificate_version", null);
        assertFalse(ProductVersionValidator.verifyClientSupport(consumer, attrs));
    }

    @Test
    public void verifyConsumerVersion1Point0WhenConsumerVersionIsEmpty() {
        consumer.setFact("system.certificate_version", "");
        assertFalse(ProductVersionValidator.verifyClientSupport(consumer, attrs));
    }

    @Test
    public void verifyConsumerVersion1Point0WhenConsumerVersionIsNotSet() {
        consumer.setFacts(new HashMap<String, String>());
        assertFalse(ProductVersionValidator.verifyClientSupport(consumer, attrs));
    }
}
