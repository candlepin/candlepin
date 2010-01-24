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
package org.fedoraproject.candlepin.policy.js.test;

import static org.junit.Assert.*;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.policy.ValidationResult;
import org.fedoraproject.candlepin.policy.js.Rules;
import org.fedoraproject.candlepin.policy.js.RuleExecutionException;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

public class RulesTest {

    Consumer c;
    Owner o;

    @Before
    public void setUp() {
        o = TestUtil.createOwner();
        c = TestUtil.createConsumer(o);
    }

    @Test(expected = RuleExecutionException.class)
    public void testRuleWithBadVariable() {
        Rules rules = new Rules("/rules/sample-rules.js");
        EntitlementPool pool = gimmeAPool("badVariableProduct");
        rules.validateProduct(c, pool);
    }

    @Test
    public void testValidateProduct() {
        Rules rules = new Rules("/rules/sample-rules.js");
        EntitlementPool pool = gimmeAPool("testProduct");
        ValidationResult result = rules.validateProduct(c, pool);
        assertTrue(result.isSuccessful());
    }

    private EntitlementPool gimmeAPool(String productLabel) {
        Product p = new Product(productLabel, productLabel);
        return new EntitlementPool(o, p, new Long(1000), TestUtil.createDate(2009, 11, 30),
                TestUtil.createDate(2015, 11, 30));
    }
}
