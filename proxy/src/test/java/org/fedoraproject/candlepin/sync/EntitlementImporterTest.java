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
package org.fedoraproject.candlepin.sync;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.LinkedList;
import org.fedoraproject.candlepin.audit.EventSink;

import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.model.SubscriptionCurator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * EntitlementImporterTest
 */
@RunWith(MockitoJUnitRunner.class)
public class EntitlementImporterTest {

    @Mock private EventSink sink;

    @Test
    public void testSingleSubscriptionInListNotInDbCausesSave() {
        final Subscription testSub = new Subscription();
        testSub.setProduct(new Product("test-prod", "Test Prod"));
        testSub.setUpstreamPoolId(1L);
        Owner owner = new Owner();
        testSub.setOwner(owner);
        
        SubscriptionCurator curator = mock(SubscriptionCurator.class);
        
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>());
        
        EntitlementImporter importer = new EntitlementImporter(curator, null, this.sink);
        importer.store(owner, new HashSet<Subscription>() {
            {
                add(testSub);
            }
        });
        
        verify(curator).create(testSub);
        verify(curator, never()).delete(testSub);
        verify(curator, never()).merge(testSub);
    }
    
    @Test
    public void testSingleSubscriptionInDbAndListCausesMerge() {
        final Subscription testSub = new Subscription();
        testSub.setProduct(new Product("test-prod", "Test Prod"));
        testSub.setUpstreamPoolId(1L);
        Owner owner = new Owner();
        testSub.setOwner(owner);
        
        SubscriptionCurator curator = mock(SubscriptionCurator.class);
        
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>() {
            {
                add(testSub);
            }
        });
        
        EntitlementImporter importer = new EntitlementImporter(curator, null, this.sink);
        importer.store(owner, new HashSet<Subscription>() {
            {
                add(testSub);
            }
        });
        
        verify(curator, never()).create(testSub);
        verify(curator).merge(testSub);
        verify(curator, never()).delete(testSub);
    }
    
    @Test
    public void testEmptyListCausesDbRemove() {
        final Subscription testSub = new Subscription();
        testSub.setProduct(new Product("test-prod", "Test Prod"));
        testSub.setUpstreamPoolId(1L);
        Owner owner = new Owner();
        testSub.setOwner(owner);
        
        SubscriptionCurator curator = mock(SubscriptionCurator.class);
        
        when(curator.listByOwner(owner)).thenReturn(new LinkedList<Subscription>() {
            {
                add(testSub);
            }
        });
        EntitlementImporter importer = new EntitlementImporter(curator, null, null);
        importer.store(owner, new HashSet<Subscription>());
        
        verify(curator, never()).create(testSub);
        verify(curator, never()).merge(testSub);
        verify(curator).delete(testSub);
    }
}
