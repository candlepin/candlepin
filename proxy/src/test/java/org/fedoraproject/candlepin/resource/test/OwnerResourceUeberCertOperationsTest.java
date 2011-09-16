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
package org.fedoraproject.candlepin.resource.test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.auth.permissions.Permission;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Role;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.resource.OwnerResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

/**
 * OwnerResourceUeberCertOperationsTest
 */
public class OwnerResourceUeberCertOperationsTest extends DatabaseTestFixture {
    
    /**
     * 
     */
    private static final String UEBER_PRODUCT = "_ueber_product";

    private static final String OWNER_NAME = "Jar_Jar_Binks";
    
    private Owner owner;
    private OwnerResource or;

    private Principal principal;

    @Before
    public void setUp() {
        owner = ownerCurator.create(new Owner(OWNER_NAME));
        
        Role ownerAdminRole = createAdminRole(owner);
        roleCurator.create(ownerAdminRole);
        
        principal = new UserPrincipal("testing user", 
            new ArrayList<Permission>(ownerAdminRole.getPermissions()), false);
        setupPrincipal(principal);
        
        ConsumerType systemType = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        consumerTypeCurator.create(systemType);

        or = new OwnerResource(ownerCurator, poolCurator,
            null, null, consumerCurator, null, i18n, null, null, null,
            null, null, poolManager, null, null, null, subAdapter,
            null, consumerTypeCurator, productAdapter, contentCurator, 
            entCertCurator, entitlementCurator, uniqueIdGenerator);        
    }

    @Test
    public void testUeberProductIsCreated() throws Exception {
        or.createUeberCertificate(principal, owner.getKey());        
        assertNotNull(productCurator.lookupByName(owner.getKey() + UEBER_PRODUCT));
    }
    
    @Test
    public void testUeberSubscriptionIsCreated() throws Exception {
        or.createUeberCertificate(principal, owner.getKey());        
        List<Subscription> ueberSubscription = subAdapter.getSubscriptions(owner);
        
        assertTrue(ueberSubscription.size() == 1);
        assertTrue(ueberSubscription.get(0).getProduct() ==
            productCurator.lookupByName(owner.getKey() + UEBER_PRODUCT));
    }
    
    @Test
    public void testUeberConsumerIsCreated() throws Exception {
        or.createUeberCertificate(principal, owner.getKey());        
        assertNotNull(consumerCurator.findByName("ueber_cert_consumer"));        
    }
    
    @Test
    public void testUeberEntitlementIsGenerated() throws Exception {
        or.createUeberCertificate(principal, owner.getKey());
        Consumer c = consumerCurator.findByName("ueber_cert_consumer");
        
        assertTrue(poolCurator.listByConsumer(c).size() == 1);
    }
    
    @Test
    public void testUeberCertIsRegeneratedOnNextInvocation() throws Exception {
        EntitlementCertificate firstCert 
            = or.createUeberCertificate(principal, owner.getKey());
        Product firstProduct = productCurator.lookupByName(owner.getKey() + UEBER_PRODUCT);
        
        EntitlementCertificate secondCert 
            = or.createUeberCertificate(principal, owner.getKey());
        Product secondProduct = productCurator.lookupByName(owner.getKey() + UEBER_PRODUCT);

        //make sure we didn't regenerate the whole thing
        assertTrue(firstProduct.getId() == secondProduct.getId());
        // only the ueber cert
        assertFalse(firstCert.getId() == secondCert.getId()); 
    }
    
    @Test(expected = NotFoundException.class)
    public void certificateGenerationRaisesExceptionIfOwnerNotFound() throws Exception {
        or.createUeberCertificate(principal, "non-existant");        
    }
    
    @Test(expected = NotFoundException.class)
    public void certificateRetrievalRaisesExceptionIfOwnerNotFound() throws Exception {
        or.getUeberCertificate(principal, "non-existant");
    }
    
    @Test(expected = NotFoundException.class)
    public void certificateRetrievalRaisesExceptionIfNoCertificateWasGenerated()
        throws Exception {
        Owner anotherOwner = ownerCurator.create(new Owner(OWNER_NAME + "1"));
        or.getUeberCertificate(principal, anotherOwner.getKey());
    }

    @Test
    public void certificateRetrievalReturnsCert() {
        EntitlementCertificate cert = or.createUeberCertificate(principal, owner.getKey());
        assertNotNull(cert);
    }
}
