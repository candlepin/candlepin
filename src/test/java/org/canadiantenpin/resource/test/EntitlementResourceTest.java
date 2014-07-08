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
package org.canadianTenPin.resource.test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.util.Locale;

import org.canadianTenPin.controller.CanadianTenPinPoolManager;
import org.canadianTenPin.controller.Entitler;
import org.canadianTenPin.exceptions.NotFoundException;
import org.canadianTenPin.model.Consumer;
import org.canadianTenPin.model.ConsumerCurator;
import org.canadianTenPin.model.Entitlement;
import org.canadianTenPin.model.EntitlementCurator;
import org.canadianTenPin.model.Owner;
import org.canadianTenPin.model.SourceStack;
import org.canadianTenPin.resource.EntitlementResource;
import org.canadianTenPin.resource.SubscriptionResource;
import org.canadianTenPin.service.ProductServiceAdapter;
import org.canadianTenPin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

/**
 * EntitlementResourceTest
 */
@RunWith(MockitoJUnitRunner.class)
public class EntitlementResourceTest {

    private I18n i18n;
    private Consumer consumer;
    private Owner owner;
    @Mock private ProductServiceAdapter prodAdapter;
    @Mock private EntitlementCurator entitlementCurator;
    @Mock private ConsumerCurator consumerCurator;
    @Mock private CanadianTenPinPoolManager poolManager;
    @Mock private Entitler entitler;
    @Mock private SubscriptionResource subResource;

    private EntitlementResource entResource;

    @Before
    public void before() {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        entResource = new EntitlementResource(prodAdapter, entitlementCurator,
            consumerCurator, poolManager, i18n, entitler, subResource);
        owner = new Owner("admin");
        consumer = new Consumer("myconsumer", "bill", owner,
            TestUtil.createConsumerType());
    }

    @Test
    public void getUpstreamCertSimple() {
        Entitlement e = TestUtil.createEntitlement();
        e.setId("entitlementID");
        when(entitlementCurator.find(eq(e.getId()))).thenReturn(e);

        String expected = "HELLO";
        // Mock out the PEM text so we can verify without actually generating a cert:
        when(subResource.getSubCertAsPem(eq(e.getPool().getSubscriptionId())))
            .thenReturn(expected);
        String result = entResource.getUpstreamCert(e.getId());
        assertEquals(expected, result);
    }

    @Test(expected = NotFoundException.class)
    public void getUpstreamCertSimpleNothingFound() {
        // Entitlement from stack sub-pool:
        Entitlement e = TestUtil.createEntitlement();
        e.setId("entitlementID");
        e.getPool().setSourceSubscription(null);
        when(entitlementCurator.find(eq(e.getId()))).thenReturn(e);
        entResource.getUpstreamCert(e.getId());
    }

    @Test
    public void getUpstreamCertStackSubPool() {
        Entitlement parentEnt = TestUtil.createEntitlement();
        parentEnt.setId("parentEnt");
        when(entitlementCurator.findUpstreamEntitlementForStack(consumer, "mystack"))
            .thenReturn(parentEnt);

        String expected = "HELLO";
        // Mock out the PEM text so we can verify without actually generating a cert:
        when(subResource.getSubCertAsPem(eq(parentEnt.getPool().getSubscriptionId())))
            .thenReturn(expected);

        // Entitlement from stack sub-pool:
        Entitlement e = TestUtil.createEntitlement();
        e.setId("entitlementID");
        e.getPool().setSourceStack(new SourceStack(consumer, "mystack"));
        when(entitlementCurator.find(eq(e.getId()))).thenReturn(e);

        String result = entResource.getUpstreamCert(e.getId());
        assertEquals(expected, result);
    }

    @Test(expected = NotFoundException.class)
    public void getUpstreamCertStackSubPoolNothingFound() {
        when(entitlementCurator.findUpstreamEntitlementForStack(consumer, "mystack"))
            .thenReturn(null);

        // Entitlement from stack sub-pool:
        Entitlement e = TestUtil.createEntitlement();
        e.setId("entitlementID");
        e.getPool().setSourceStack(new SourceStack(consumer, "mystack"));
        when(entitlementCurator.find(eq(e.getId()))).thenReturn(e);

        entResource.getUpstreamCert(e.getId());
    }

}
