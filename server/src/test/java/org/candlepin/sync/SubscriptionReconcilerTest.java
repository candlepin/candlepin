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
package org.candlepin.sync;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.candlepin.audit.EventSink;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * EntitlementImporterTest
 */
@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("synthetic-access")
public class SubscriptionReconcilerTest {

    @Mock private EventSink sink;
    @Mock private PoolCurator poolCurator;
    @Mock private CertificateSerialCurator certSerialCurator;
    @Mock private CdnCurator cdnCurator;
    @Mock private ObjectMapper om;
    @Mock private ProductCurator pc;

    private Owner owner;
    private EntitlementImporter importer;
    private I18n i18n;
    private int index = 1;
    private SubscriptionReconciler reconciler;


    @Before
    public void init() {
        this.owner = new Owner();
        this.reconciler = new SubscriptionReconciler(this.poolCurator);

        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        this.importer = new EntitlementImporter(certSerialCurator, cdnCurator, i18n, pc);
    }

    /*
     * Creates a pool with the properties of each incoming subscription. This is partially
     * being used due to the original test suite comparing existing local subscriptions
     * to incoming subscriptions. Today we don't have local subscriptions, only the
     * master pools they created.
     *
     * TODO: Might be worth switching from copying data of a subscription to just creating
     * the local pool with params.
     */
    private List<Pool> createPoolsFor(Subscription ... subs) {
        List<Pool> pools = new LinkedList<Pool>();
        for (Subscription sub : subs) {
            pools.add(TestUtil.copyFromSub(sub));
        }

        // Mock these pools as the return value for the owner:
        CandlepinQuery<Pool> cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(pools);
        when(cqmock.iterator()).thenReturn(pools.iterator());

        when(poolCurator.listByOwnerAndType(owner, PoolType.NORMAL)).thenReturn(cqmock);
        return pools;
    }

    /*
     * Verify that a subscription ended up with the upstream data we expect.
     */
    private void assertUpstream(Subscription sub, String subId) {
        assertEquals(subId, sub.getId());
    }

    @Test
    public void oneExistsUnchanged() {
        Subscription testSub1 = createSubscription(owner, "test-prod-1", "up1", "ue1", "uc1", 25);
        createPoolsFor(testSub1);

        reconciler.reconcile(owner, Arrays.asList(testSub1));

        assertUpstream(testSub1, testSub1.getId());
    }

    @Test
    public void oneExistsOneNew() {
        Subscription testSub2 = createSubscription(owner, "test-prod-1", "up1", "ue2", "uc1", 20);
        Subscription testSub3 = createSubscription(owner, "test-prod-1", "up1", "ue3", "uc1", 15);

        createPoolsFor(testSub2);

        reconciler.reconcile(owner, Arrays.asList(testSub2, testSub3));
        assertUpstream(testSub2, testSub2.getId());
        assertUpstream(testSub3, testSub3.getId());
    }

    @Test
    public void testTwoExistOneRemoved() {
        Subscription testSub2 = createSubscription(owner, "test-prod-1", "up1", "ue2", "uc1", 20);
        Subscription testSub3 = createSubscription(owner, "test-prod-1", "up1", "ue3", "uc1", 15);

        createPoolsFor(testSub2, testSub3);

        reconciler.reconcile(owner, Arrays.asList(testSub3));
        assertUpstream(testSub3, testSub3.getId());
    }

    @Test
    public void testThreeExistThreeNewOneDifferent() {
        Subscription testSub2 = createSubscription(owner, "test-prod-1", "up1", "ue2", "uc1", 20);
        Subscription testSub3 = createSubscription(owner, "test-prod-1", "up1", "ue3", "uc1", 15);
        Subscription testSub4 = createSubscription(owner, "test-prod-1", "up1", "ue4", "uc1", 10);
        Subscription testSub5 = createSubscription(owner, "test-prod-1", "up1", "ue5", "uc1", 5);

        createPoolsFor(testSub2, testSub3, testSub4);

        reconciler.reconcile(owner, Arrays.asList(testSub2, testSub4, testSub5));
        assertUpstream(testSub2, testSub2.getId());
        assertUpstream(testSub4, testSub4.getId());
        // Should assume subscription 3's ID:
        assertUpstream(testSub5, testSub3.getId());
    }

    @Test
    public void testThreeExistThreeNewSameQuantitiesNewConsumer() {
        Subscription testSub3 = createSubscription(owner, "test-prod-1", "up1", "ue3", "uc1", 15);
        Subscription testSub4 = createSubscription(owner, "test-prod-1", "up1", "ue4", "uc1", 10);
        Subscription testSub5 = createSubscription(owner, "test-prod-1", "up1", "ue5", "uc1", 5);
        Subscription testSub6 = createSubscription(owner, "test-prod-1", "up1", "ue6", "uc2", 15);
        Subscription testSub7 = createSubscription(owner, "test-prod-1", "up1", "ue7", "uc2", 10);
        Subscription testSub8 = createSubscription(owner, "test-prod-1", "up1", "ue8", "uc2", 5);


        createPoolsFor(testSub3, testSub4, testSub5);

        reconciler.reconcile(owner, Arrays.asList(testSub6, testSub7, testSub8));
        assertUpstream(testSub6, testSub3.getId());
        assertUpstream(testSub7, testSub4.getId());
        assertUpstream(testSub8, testSub5.getId());
    }

    @Test
    public void testThreeExistTwoNewQuantityMatchNewConsumer() {
        Subscription testSub3 = createSubscription(owner, "test-prod-1", "up1", "ue3", "uc1", 15);
        Subscription testSub4 = createSubscription(owner, "test-prod-1", "up1", "ue4", "uc1", 10);
        Subscription testSub5 = createSubscription(owner, "test-prod-1", "up1", "ue5", "uc1", 5);
        Subscription testSub6 = createSubscription(owner, "test-prod-1", "up1", "ue6", "uc2", 15);
        Subscription testSub8 = createSubscription(owner, "test-prod-1", "up1", "ue8", "uc2", 5);

        createPoolsFor(testSub3, testSub4, testSub5);

        reconciler.reconcile(owner, Arrays.asList(testSub6, testSub8));
        assertUpstream(testSub6, testSub3.getId());
        assertUpstream(testSub8, testSub5.getId());
    }

    @Test
    public void testTwoExistThreeNewConsumer() {
        Subscription testSub3 = createSubscription(owner, "test-prod-1", "up1", "ue3", "uc1", 15);
        Subscription testSub4 = createSubscription(owner, "test-prod-1", "up1", "ue4", "uc1", 10);
        Subscription testSub6 = createSubscription(owner, "test-prod-1", "up1", "ue6", "uc2", 15);
        Subscription testSub7 = createSubscription(owner, "test-prod-1", "up1", "ue7", "uc2", 10);
        Subscription testSub8 = createSubscription(owner, "test-prod-1", "up1", "ue8", "uc2", 5);

        createPoolsFor(testSub3, testSub4);

        reconciler.reconcile(owner, Arrays.asList(testSub6, testSub7, testSub8));
        assertUpstream(testSub6, testSub3.getId());
        assertUpstream(testSub7, testSub4.getId());
        assertUpstream(testSub8, testSub8.getId());
    }

    @Test
    public void testThreeExistOldThreeNew() {
        Subscription testSub3 = createSubscription(owner, "test-prod-1", "up1", "ue3", "uc1", 15);
        Subscription testSub4 = createSubscription(owner, "test-prod-1", "up1", "ue4", "uc1", 10);
        Subscription testSub5 = createSubscription(owner, "test-prod-1", "up1", "ue5", "uc1", 5);
        Subscription testSub9 = createSubscription(owner, "test-prod-1", "up1", "", "", 15);
        Subscription testSub10 = createSubscription(owner, "test-prod-1", "up1", "", "", 10);
        Subscription testSub11 = createSubscription(owner, "test-prod-1", "up1", "", "", 5);

        createPoolsFor(testSub9, testSub10, testSub11);

        reconciler.reconcile(owner, Arrays.asList(testSub3, testSub4, testSub5));
        assertUpstream(testSub3, testSub9.getId());
        assertUpstream(testSub4, testSub10.getId());
        assertUpstream(testSub5, testSub11.getId());
    }

    @Test
    public void testQuantMatchAllLower() {
        Subscription testSub1 = createSubscription(owner, "test-prod-1", "up1", "ue1", "uc1", 25);
        Subscription testSub2 = createSubscription(owner, "test-prod-1", "up1", "ue2", "uc1", 20);
        Subscription testSub3 = createSubscription(owner, "test-prod-1", "up1", "ue3", "uc1", 15);
        Subscription testSub12 = createSubscription(owner, "test-prod-1", "up1", "ue12", "uc3", 23);
        Subscription testSub13 = createSubscription(owner, "test-prod-1", "up1", "ue13", "uc3", 17);
        Subscription testSub14 = createSubscription(owner, "test-prod-1", "up1", "ue14", "uc3", 10);

        createPoolsFor(testSub1, testSub2, testSub3);

        reconciler.reconcile(owner, Arrays.asList(testSub12, testSub13, testSub14));

        // Quantities 25, 20, 15 should be replaced by new pools with 23, 17, 10:
        assertUpstream(testSub12, testSub1.getId());
        assertUpstream(testSub13, testSub2.getId());
        assertUpstream(testSub14, testSub3.getId());
    }

    @Test
    public void testQuantMatchMix() {
        Subscription testSub2 = createSubscription(owner, "test-prod-1", "up1", "ue2", "uc1", 20);
        Subscription testSub3 = createSubscription(owner, "test-prod-1", "up1", "ue3", "uc1", 15);
        Subscription testSub4 = createSubscription(owner, "test-prod-1", "up1", "ue4", "uc1", 10);
        Subscription testSub5 = createSubscription(owner, "test-prod-1", "up1", "ue5", "uc1", 5);
        Subscription testSub12 = createSubscription(owner, "test-prod-1", "up1", "ue12", "uc3", 23);
        Subscription testSub14 = createSubscription(owner, "test-prod-1", "up1", "ue14", "uc3", 10);

        createPoolsFor(testSub2, testSub3, testSub4, testSub5);

        reconciler.reconcile(owner, Arrays.asList(testSub12, testSub14));

        assertUpstream(testSub12, testSub2.getId());
        assertUpstream(testSub14, testSub4.getId());
    }

    @Test
    public void testQuantMatchAllSame() {
        Subscription testSub3 = createSubscription(owner, "test-prod-1", "up1", "ue3", "uc1", 15);
        Subscription testSub15 = createSubscription(owner, "test-prod-1", "up1", "ue15", "uc1", 15);
        Subscription testSub16 = createSubscription(owner, "test-prod-1", "up1", "ue16", "uc1", 15);

        createPoolsFor(testSub3, testSub15);

        reconciler.reconcile(owner, Arrays.asList(testSub3, testSub16));

        // Quantities 25, 20, 15 should be replaced by new pools with 23, 17, 10:
        assertUpstream(testSub3, testSub3.getId());
        assertUpstream(testSub16, testSub15.getId());
    }

    @Test
    public void testMultiPools() {
        Subscription testSub1 = createSubscription(owner, "test-prod-1", "up1", "ue1", "uc1", 25);
        Subscription testSub2 = createSubscription(owner, "test-prod-1", "up1", "ue2", "uc1", 20);
        Subscription testSub3 = createSubscription(owner, "test-prod-1", "up1", "ue3", "uc1", 15);
        Subscription testSub4 = createSubscription(owner, "test-prod-1", "up1", "ue4", "uc1", 10);
        Subscription testSub5 = createSubscription(owner, "test-prod-1", "up1", "ue5", "uc1", 5);
        Subscription testSub20 = createSubscription(owner, "test-prod-1", "up2", "ue20", "uc1", 25);
        Subscription testSub21 = createSubscription(owner, "test-prod-1", "up2", "ue21", "uc1", 20);
        Subscription testSub22 = createSubscription(owner, "test-prod-1", "up2", "ue22", "uc1", 15);
        Subscription testSub24 = createSubscription(owner, "test-prod-1", "up2", "ue24", "uc1", 5);
        Subscription testSub30 = createSubscription(owner, "test-prod-1", "up3", "ue30", "uc1", 25);
        Subscription testSub31 = createSubscription(owner, "test-prod-1", "up3", "ue31", "uc1", 20);
        Subscription testSub32 = createSubscription(owner, "test-prod-1", "up3", "ue32", "uc1", 15);
        Subscription testSub33 = createSubscription(owner, "test-prod-1", "up3", "ue33", "uc1", 10);
        Subscription testSub34 = createSubscription(owner, "test-prod-1", "up3", "ue34", "uc1", 5);

        createPoolsFor(testSub1, testSub2, testSub3, testSub4, testSub5, testSub20, testSub21, testSub22,
            testSub24);

        reconciler.reconcile(owner, Arrays.asList(testSub1, testSub2, testSub3, testSub4, testSub5, testSub30,
            testSub31, testSub32, testSub33, testSub34));

        // 20-24 have no matchup with 30-34 due to different upstream pool ID:
        assertUpstream(testSub1, testSub1.getId());
        assertUpstream(testSub2, testSub2.getId());
        assertUpstream(testSub3, testSub3.getId());
        assertUpstream(testSub4, testSub4.getId());
        assertUpstream(testSub5, testSub5.getId());
        assertUpstream(testSub30, testSub30.getId());
        assertUpstream(testSub31, testSub31.getId());
        assertUpstream(testSub32, testSub32.getId());
        assertUpstream(testSub33, testSub33.getId());
        assertUpstream(testSub34, testSub34.getId());
    }

    private Subscription createSubscription(Owner daOwner, String productId,
        String poolId, String entId, String conId, long quantity) {

        ProductData pdata = new ProductData();
        pdata.setId(productId);
        pdata.setName(productId);

        Subscription sub = new Subscription();
        sub.setProduct(pdata);
        sub.setUpstreamPoolId(poolId);
        sub.setUpstreamEntitlementId(entId);
        sub.setUpstreamConsumerId(conId);
        sub.setQuantity(quantity);
        sub.setOwner(daOwner);
        sub.setId("" + index++);

        return sub;
    }

    protected EntitlementCertificate createEntitlementCertificate(String key,
        String cert) {
        EntitlementCertificate toReturn = new EntitlementCertificate();
        CertificateSerial certSerial = new CertificateSerial(new Date());
        certSerial.setCollected(true);
        certSerial.setUpdated(new Date());
        certSerial.setCreated(new Date());
        toReturn.setKeyAsBytes(key.getBytes());
        toReturn.setCertAsBytes(cert.getBytes());
        toReturn.setSerial(certSerial);
        return toReturn;
    }
}
