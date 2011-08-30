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
package org.fedoraproject.candlepin.compliance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.fedoraproject.candlepin.compliance.Compliance;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerInstalledProduct;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.ProvidedProduct;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;



/**
 * ComplianceTest
 */
public class ComplianceTest {
    private Owner owner;
    private Compliance compliance;
    @Mock EntitlementCurator entCurator;
    
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        compliance = new Compliance(entCurator);
        owner = new Owner("test");
    }
    
    private Consumer mockConsumer(String [] installedProducts) {
        Consumer c = new Consumer();
        for (String pid : installedProducts) {
            c.addInstalledProduct(new ConsumerInstalledProduct(pid, pid));
        }
        return c;
    }
    
    private Entitlement mockEntitlement(Consumer consumer, String productId, 
        String ... providedProductIds) {
        
        Set<ProvidedProduct> provided = new HashSet<ProvidedProduct>();
        for (String pid : providedProductIds) {
            provided.add(new ProvidedProduct(pid, pid));
        }
        Pool p = new Pool(owner, productId, productId, provided, 
            new Long(1000), TestUtil.createDate(2000, 1, 1), TestUtil.createDate(2050, 1, 1), 
            "1000", "1000");
        Entitlement e = new Entitlement(p, consumer, p.getStartDate(), p.getEndDate(), 1);
        return e;
    }
    
    @Test
    public void noEntitlements() {
        Consumer c = mockConsumer(new String [] {"product1", "product2"});
        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        
        assertEquals(2, status.getNonCompliantProducts().size());
        assertEquals(0, status.getCompliantProducts().size());
        assertEquals(0, status.getPartiallyCompliantProducts().size());
    }
    
    @Test
    public void entitledProducts() {
        Consumer c = mockConsumer(new String [] {"product1", "product2"});
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockEntitlement(c, "Awesome Product", "product1"));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);
        
        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        
        assertEquals(1, status.getNonCompliantProducts().size());
        assertTrue(status.getNonCompliantProducts().contains("product2"));
        
        assertEquals(1, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains("product1"));
        
        assertEquals(0, status.getPartiallyCompliantProducts().size());
    }
    
    @Test
    public void fullyEntitled() {
        Consumer c = mockConsumer(new String [] {"product1", "product2"});
        List<Entitlement> ents = new LinkedList<Entitlement>();
        ents.add(mockEntitlement(c, "Awesome Product", "product1", "product2"));
        when(entCurator.listByConsumerAndDate(eq(c), any(Date.class))).thenReturn(ents);
        
        ComplianceStatus status = compliance.getStatus(c, TestUtil.createDate(2011, 8, 30));
        
        assertEquals(0, status.getNonCompliantProducts().size());
        
        // Our one entitlement should cover both of these:
        assertEquals(2, status.getCompliantProducts().size());
        assertTrue(status.getCompliantProducts().keySet().contains("product1"));
        assertTrue(status.getCompliantProducts().keySet().contains("product2"));
        
        assertEquals(0, status.getPartiallyCompliantProducts().size());
    }
    
    @Test
    public void notInstalledButPartiallyStacked() {
        // TODO: spoke to PM, this is considered invalid even though the product is
        // not technically installed. (yet) 
        fail();
    }
    
    @Test
    public void partiallyStackedAndFullyEntitled() {
        // TODO: test a weird scenario where consumer has both a regular entitlement
        // providing a product, as well as a partially stacked. (regular should cover them)
        fail();
    }
}
