package org.fedoraproject.candlepin.enforcer;

import static org.junit.Assert.*;

import java.util.Calendar;
import java.util.Date;

import org.fedoraproject.candlepin.enforcer.java.JavaEnforcer;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
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
                entitlementPoolCurator);
    }
    
    // grrr. have to test two conditions atm: sufficient number of entitlements *when* pool has not expired
    @Test
    public void shouldPassValidationWhenSufficientNumberOfEntitlementsIsAvailableAndNotExpired() {
        assertTrue(enforcer.validate(new Consumer(), 
                entitlementPoolWithMembersAndExpiration(1, 2, expiryDate(2010, 10, 10))));
        assertFalse(enforcer.hasErrors());
        assertFalse(enforcer.hasWarnings());
    }
    
    @Test
    public void shouldFailValidationWhenNoEntitlementsAreAvailable() {
        assertFalse(enforcer.validate(new Consumer(), 
                entitlementPoolWithMembersAndExpiration(1, 1, expiryDate(2010, 10, 10))));
        assertTrue(enforcer.hasErrors());
        assertFalse(enforcer.hasWarnings());
    }
    
    @Test
    public void shouldFailWhenEntitlementsAreExpired() {
        assertFalse(enforcer.validate(new Consumer(), 
                entitlementPoolWithMembersAndExpiration(1, 2, expiryDate(2000, 1, 1))));
        assertTrue(enforcer.hasErrors());
        assertFalse(enforcer.hasWarnings());
    }
    
    private Date expiryDate(int year, int month, int day) {
        return TestDateUtil.date(year, month, day);
    }
    
    private EntitlementPool entitlementPoolWithMembersAndExpiration(
            final int currentMembers, final int maxMembers, Date expiry) {
        return new EntitlementPool(new Owner(), new Product(), new Long(maxMembers), new Date(), expiry) {{
            setCurrentMembers(currentMembers);
        }};
    }    
}
