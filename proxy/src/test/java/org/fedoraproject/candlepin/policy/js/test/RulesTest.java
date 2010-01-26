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

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.RulesCurator;
import org.fedoraproject.candlepin.policy.ValidationResult;
import org.fedoraproject.candlepin.policy.js.RuleExecutionException;
import org.fedoraproject.candlepin.policy.js.Rules;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

public class RulesTest {

    Consumer c;
    Owner o;
    RulesCurator rulesCurator;

    @Before
    public void setUp() {
        o = TestUtil.createOwner();
        c = TestUtil.createConsumer(o);
    }

    public String fileToString(String path) {
        InputStream is = path.getClass().getResourceAsStream(path);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder builder = new StringBuilder();
        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                builder.append(line + "\n");
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return builder.toString();
    }
    
    @Test(expected = RuleExecutionException.class)
    public void testRuleWithBadVariable() {
        String buffer = fileToString("/rules/sample-rules.js");
        System.out.println(buffer);
        Rules rules = new Rules(buffer);
        EntitlementPool pool = gimmeAPool("badVariableProduct");
        rules.runPre(c, pool);
    }

    @Test
    public void testValidateProduct() {
        String buffer = fileToString("/rules/sample-rules.js");
        Rules rules = new Rules(buffer);
        EntitlementPool pool = gimmeAPool("testProduct");
        ValidationResult result = rules.runPre(c, pool);
        assertTrue(result.isSuccessful());
    }

    private EntitlementPool gimmeAPool(String productLabel) {
        Product p = new Product(productLabel, productLabel);
        return new EntitlementPool(o, p, new Long(1000), TestUtil.createDate(2009, 11, 30),
                TestUtil.createDate(2015, 11, 30));
    }
}
