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

import org.candlepin.model.Consumer;
import org.candlepin.model.Product;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Test suite for FactValueCalculator.
 */
public class FactValueCalculatorTest {

    @Test
    public void testGetCoresFact() {
        Consumer consumer = new Consumer();
        consumer.setFact(Consumer.Facts.CORES, "2");
        consumer.setFact(Consumer.Facts.SOCKETS, "2");
        assertEquals(4, FactValueCalculator.getFact(Product.Attributes.CORES, consumer));
    }

    @Test
    public void testGetVCPUFactReturnsValueBasedOnCores() {
        Consumer consumer = new Consumer();
        consumer.setFact(Consumer.Facts.CORES, "2");
        assertEquals(2, FactValueCalculator.getFact(Product.Attributes.VCPU, consumer));
    }

    @Test
    public void testGetRAMFactIsInGBandRoundedUp() {
        Consumer consumer = new Consumer();
        consumer.setFact(Consumer.Facts.RAM, "1873480");
        assertEquals(2, FactValueCalculator.getFact(Product.Attributes.RAM, consumer));
    }

    @Test
    public void testGetSocketsFact() {
        Consumer consumer = new Consumer();
        consumer.setFact(Consumer.Facts.SOCKETS, "2");
        assertEquals(2, FactValueCalculator.getFact(Product.Attributes.SOCKETS, consumer));
    }

    @Test
    public void testGetStorageBandFact() {
        Consumer consumer = new Consumer();
        consumer.setFact(Consumer.Facts.STORAGE_BAND, "500");
        assertEquals(500, FactValueCalculator.getFact(Product.Attributes.STORAGE_BAND, consumer));
    }

    @Test
    public void testGetFactWhenUnsetReturnsOne() {
        Consumer consumer = new Consumer();
        consumer.setFacts(new HashMap<>());
        assertEquals(1, FactValueCalculator.getFact(Product.Attributes.CORES, consumer));
        assertEquals(1, FactValueCalculator.getFact(Product.Attributes.SOCKETS, consumer));
        assertEquals(1, FactValueCalculator.getFact(Product.Attributes.VCPU, consumer));
        assertEquals(1, FactValueCalculator.getFact(Product.Attributes.STORAGE_BAND, consumer));
    }

    @Test
    public void testGetRAMFactWhenUnsetReturnsZero() {
        Consumer consumer = new Consumer();
        consumer.setFacts(new HashMap<>());
        assertEquals(0, FactValueCalculator.getFact(Product.Attributes.RAM, consumer));
    }
}
