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
package org.fedoraproject.candlepin.policy.test;

import static org.junit.Assert.*;

import java.util.Date;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.ValidationResult;
import org.fedoraproject.candlepin.policy.java.JavaEnforcer;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.DateSourceForTesting;
import org.fedoraproject.candlepin.test.TestDateUtil;
import org.junit.Before;
import org.junit.Test;

public class EnforcerTest extends DatabaseTestFixture {

    private Enforcer enforcer;

    @Before
    public void createEnforcer() {
        enforcer = new JavaEnforcer(new DateSourceForTesting(2010, 1, 1),
                entitlementPoolCurator, productCurator);
    }
    
    // grrr. have to test two conditions atm: sufficient number of entitlements *when* pool has not expired
    @Test
    public void shouldPassValidationWhenSufficientNumberOfEntitlementsIsAvailableAndNotExpired() {
        ValidationResult result = enforcer.validate(new Consumer(),
                entitlementPoolWithMembersAndExpiration(1, 2, expiryDate(2010, 10, 10)));
        assertTrue(result.isSuccessful());
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }
    
    @Test
    public void shouldFailValidationWhenNoEntitlementsAreAvailable() {
        ValidationResult result = enforcer.validate(new Consumer(),
                entitlementPoolWithMembersAndExpiration(1, 1, expiryDate(2010, 10, 10)));
        assertFalse(result.isSuccessful());
        assertTrue(result.hasErrors());
        assertFalse(result.hasWarnings());
    }
    
    @Test
    public void shouldFailWhenEntitlementsAreExpired() {
        ValidationResult result = enforcer.validate(new Consumer(),
                entitlementPoolWithMembersAndExpiration(1, 2, expiryDate(2000, 1, 1)));
        assertFalse(result.isSuccessful());
        assertTrue(result.hasErrors());
        assertFalse(result.hasWarnings());
    }
    
    private Date expiryDate(int year, int month, int day) {
        return TestDateUtil.date(year, month, day);
    }
    
    private EntitlementPool entitlementPoolWithMembersAndExpiration(
            final int currentMembers, final int maxMembers, Date expiry) {
        return new EntitlementPool(new Owner(), new Product("label", "name"), 
                new Long(maxMembers), new Date(), expiry) {{
            setCurrentMembers(currentMembers);
        }};
    }    
}
