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
package org.fedoraproject.candlepin.client.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.fedoraproject.candlepin.client.test.ConsumerHttpClientTest.TestServletConfig;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.SpacewalkCertificateCurator;
import org.fedoraproject.candlepin.model.test.SpacewalkCertificateCuratorTest;
import org.fedoraproject.candlepin.test.TestUtil;

import com.redhat.rhn.common.cert.CertificateFactory;

import com.sun.jersey.api.client.WebResource;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class VirtualHostEntitlementHttpClientTest extends
        AbstractGuiceGrizzlyTest {

    private Product virtHost;
    private Product virtHostPlatform;
    private Product virtGuest;

    private Owner o;
    private Consumer parentSystem;

    @Before
    public void setUp() throws Exception {
        TestServletConfig.servletInjector = injector;
        startServer(TestServletConfig.class);

        o = TestUtil.createOwner();
        ownerCurator.create(o);

        String certString = SpacewalkCertificateCuratorTest
                .readCertificate("/certs/spacewalk-with-channel-families.cert");
        spacewalkCertCurator.parseCertificate(CertificateFactory
                .read(certString), o);

        List<EntitlementPool> pools = entitlementPoolCurator.listByOwner(o);
        assertTrue(pools.size() > 0);

        virtHost = productCurator
                .lookupByLabel(SpacewalkCertificateCurator.PRODUCT_VIRT_HOST);
        assertNotNull(virtHost);

        virtHostPlatform = productCurator
                .lookupByLabel(SpacewalkCertificateCurator.PRODUCT_VIRT_HOST_PLATFORM);

        virtGuest = productCurator
                .lookupByLabel(SpacewalkCertificateCurator.PRODUCT_VIRT_GUEST);

        ConsumerType system = new ConsumerType(ConsumerType.SYSTEM);
        consumerTypeCurator.create(system);

        parentSystem = new Consumer("system", o, system);
        parentSystem.getFacts().setFact("total_guests", "0");
        consumerCurator.create(parentSystem);
    }

    @Test
    public void virtualizationHostConsumption() {
        assertEquals(0, entitlementPoolCurator.listByOwnerAndProduct(o,
                parentSystem, virtGuest).size());

        WebResource r = resource().path(
                "/entitlement/consumer/" + parentSystem.getUuid() + "/product/" +
                virtHost.getLabel());
        String s = r.accept("application/json").type("application/json").post(
                String.class);

        assertVirtualizationHostConsumption();
    }

    @Test
    public void virtualizationHostPlatformConsumption() {
        assertEquals(0, entitlementPoolCurator.listByOwnerAndProduct(o,
                parentSystem, virtGuest).size());

        WebResource r = resource().path(
                "/entitlement/consumer/" + parentSystem.getUuid() + "/product/" +
                        virtHostPlatform.getLabel());
        String s = r.accept("application/json").type("application/json").post(
                String.class);

        assertVirtualizationHostPlatformConsumption();
    }

    private void assertVirtualizationHostConsumption() {
        // Consuming a virt host entitlement should result in a pool just for us
        // to consume
        // virt guests.
        EntitlementPool consumerPool = entitlementPoolCurator
                .listByOwnerAndProduct(o, parentSystem, virtGuest).get(0);
        assertNotNull(consumerPool);
        assertNotNull(consumerPool.getConsumer());
        assertEquals(parentSystem.getId(), consumerPool.getConsumer().getId());
        assertEquals(new Long(5), consumerPool.getMaxMembers());
        assertNotNull(consumerPool.getSourceEntitlement().getId());
    }

    private void assertVirtualizationHostPlatformConsumption() {
        // Consuming a virt host entitlement should result in a pool just for us
        // to consume
        // virt guests.
        EntitlementPool consumerPool = entitlementPoolCurator
                .listByOwnerAndProduct(o, parentSystem, virtGuest).get(0);
        assertNotNull(consumerPool.getConsumer());
        assertEquals(parentSystem.getId(), consumerPool.getConsumer().getId());
        assertTrue(consumerPool.getMaxMembers() < 0);
        assertNotNull(consumerPool.getSourceEntitlement().getId());
    }

}
