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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.audit.EventSink;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.manifest.v1.ContentDTO;
import org.candlepin.dto.manifest.v1.OwnerDTO;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.dto.manifest.v1.ProductDTO.ProductContentDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Content;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.SourceSubscription;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;



/**
 * EntitlementImporterTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("synthetic-access")
public class SubscriptionReconcilerTest {

    @Mock private EventSink sink;
    @Mock private PoolCurator poolCurator;
    @Mock private CertificateSerialCurator certSerialCurator;
    @Mock private CdnCurator cdnCurator;
    @Mock private ObjectMapper om;
    @Mock private ProductCurator pc;
    @Mock private EntitlementCurator ec;

    private Owner owner;
    private OwnerDTO ownerDto;
    private EntitlementImporter importer;
    private I18n i18n;
    private int index = 1;
    private SubscriptionReconciler reconciler;
    private ModelTranslator translator;


    @BeforeEach
    public void init() {
        this.owner = new Owner();
        this.ownerDto = new OwnerDTO();

        this.reconciler = new SubscriptionReconciler(this.poolCurator);
        this.translator = new StandardTranslator(new ConsumerTypeCurator(), new EnvironmentCurator(),
            new OwnerCurator());

        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        this.importer = new EntitlementImporter(certSerialCurator, cdnCurator, i18n, pc, ec, translator);
    }


    public Content convertFromDTO(ContentDTO dto) {
        Content content = null;

        if (dto != null) {
            content = new Content();

            content.setId(dto.getId());
            content.setName(dto.getName());
            content.setType(dto.getType());
            content.setLabel(dto.getLabel());
            content.setVendor(dto.getVendor());
            content.setContentUrl(dto.getContentUrl());
            content.setRequiredTags(dto.getRequiredTags());
            content.setReleaseVersion(dto.getReleaseVersion());
            content.setGpgUrl(dto.getGpgUrl());
            content.setMetadataExpiration(dto.getMetadataExpiration());
            content.setModifiedProductIds(dto.getRequiredProductIds());
            content.setArches(dto.getArches());
        }

        return content;
    }

    public Product convertFromDTO(ProductDTO dto) {
        Product product = null;

        if (dto != null) {
            product = new Product(dto.getId(), dto.getName());

            product.setUuid(dto.getUuid());
            product.setMultiplier(dto.getMultiplier());
            product.setAttributes(dto.getAttributes());

            if (dto.getProductContent() != null) {
                for (ProductContentDTO pcd : dto.getProductContent()) {
                    if (pcd != null) {
                        Content content = convertFromDTO(pcd.getContent());

                        if (content != null) {
                            product.addContent(content, pcd.isEnabled() != null ? pcd.isEnabled() : true);
                        }
                    }
                }
            }

            product.setDependentProductIds(dto.getDependentProductIds());
        }

        return product;
    }

    public Pool convertFromDTO(SubscriptionDTO sub) {
        Product product = convertFromDTO(sub.getProduct());
        Product derivedProduct = convertFromDTO(sub.getDerivedProduct());

        if (sub.getProvidedProducts() != null) {
            for (ProductDTO pdata : sub.getProvidedProducts()) {
                if (pdata != null) {
                    product.addProvidedProduct(convertFromDTO(pdata));
                }
            }
        }

        if (sub.getDerivedProvidedProducts() != null) {
            for (ProductDTO pdata : sub.getDerivedProvidedProducts()) {
                if (pdata != null) {
                    derivedProduct.addProvidedProduct(convertFromDTO(pdata));
                }
            }
        }

        Pool pool = new Pool(this.owner, product, new HashSet<>(), sub.getQuantity(),
            sub.getStartDate(), sub.getEndDate(), sub.getContractNumber(), sub.getAccountNumber(),
            sub.getOrderNumber());

        pool.setDerivedProduct(derivedProduct);

        if (sub.getId() != null) {
            pool.setSourceSubscription(new SourceSubscription(sub.getId(), "master"));
        }

        pool.setUpstreamPoolId(sub.getUpstreamPoolId());
        pool.setUpstreamConsumerId(sub.getUpstreamConsumerId());
        pool.setUpstreamEntitlementId(sub.getUpstreamEntitlementId());

        return pool;
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
    private List<Pool> createPoolsFor(SubscriptionDTO ... subs) {
        List<Pool> pools = new LinkedList<>();
        for (SubscriptionDTO sub : subs) {
            pools.add(convertFromDTO(sub));
        }

        // Mock these pools as the return value for the owner:
        CandlepinQuery<Pool> cqmock = mock(CandlepinQuery.class);

        doReturn(pools).when(cqmock).list();
        doReturn(pools.iterator()).when(cqmock).iterator();

        doReturn(cqmock).when(this.poolCurator).listByOwnerAndType(eq(owner), eq(PoolType.NORMAL));

        return pools;
    }

    /*
     * Verify that a subscription ended up with the upstream data we expect.
     */
    private void assertUpstream(SubscriptionDTO sub, String subId) {
        assertEquals(subId, sub.getId());
    }

    @Test
    public void oneExistsUnchanged() {
        SubscriptionDTO testSub1 = createSubscription(ownerDto, "test-prod-1", "up1", "ue1", "uc1", 25);
        createPoolsFor(testSub1);

        reconciler.reconcile(owner, Arrays.asList(testSub1));

        assertUpstream(testSub1, testSub1.getId());
    }

    @Test
    public void oneExistsOneNew() {
        SubscriptionDTO testSub2 = createSubscription(ownerDto, "test-prod-1", "up1", "ue2", "uc1", 20);
        SubscriptionDTO testSub3 = createSubscription(ownerDto, "test-prod-1", "up1", "ue3", "uc1", 15);

        createPoolsFor(testSub2);

        reconciler.reconcile(owner, Arrays.asList(testSub2, testSub3));
        assertUpstream(testSub2, testSub2.getId());
        assertUpstream(testSub3, testSub3.getId());
    }

    @Test
    public void testTwoExistOneRemoved() {
        SubscriptionDTO testSub2 = createSubscription(ownerDto, "test-prod-1", "up1", "ue2", "uc1", 20);
        SubscriptionDTO testSub3 = createSubscription(ownerDto, "test-prod-1", "up1", "ue3", "uc1", 15);

        createPoolsFor(testSub2, testSub3);

        reconciler.reconcile(owner, Arrays.asList(testSub3));
        assertUpstream(testSub3, testSub3.getId());
    }

    @Test
    public void testThreeExistThreeNewOneDifferent() {
        SubscriptionDTO testSub2 = createSubscription(ownerDto, "test-prod-1", "up1", "ue2", "uc1", 20);
        SubscriptionDTO testSub3 = createSubscription(ownerDto, "test-prod-1", "up1", "ue3", "uc1", 15);
        SubscriptionDTO testSub4 = createSubscription(ownerDto, "test-prod-1", "up1", "ue4", "uc1", 10);
        SubscriptionDTO testSub5 = createSubscription(ownerDto, "test-prod-1", "up1", "ue5", "uc1", 5);

        createPoolsFor(testSub2, testSub3, testSub4);

        reconciler.reconcile(owner, Arrays.asList(testSub2, testSub4, testSub5));
        assertUpstream(testSub2, testSub2.getId());
        assertUpstream(testSub4, testSub4.getId());
        // Should assume subscription 3's ID:
        assertUpstream(testSub5, testSub3.getId());
    }

    @Test
    public void testThreeExistThreeNewSameQuantitiesNewConsumer() {
        SubscriptionDTO testSub3 = createSubscription(ownerDto, "test-prod-1", "up1", "ue3", "uc1", 15);
        SubscriptionDTO testSub4 = createSubscription(ownerDto, "test-prod-1", "up1", "ue4", "uc1", 10);
        SubscriptionDTO testSub5 = createSubscription(ownerDto, "test-prod-1", "up1", "ue5", "uc1", 5);
        SubscriptionDTO testSub6 = createSubscription(ownerDto, "test-prod-1", "up1", "ue6", "uc2", 15);
        SubscriptionDTO testSub7 = createSubscription(ownerDto, "test-prod-1", "up1", "ue7", "uc2", 10);
        SubscriptionDTO testSub8 = createSubscription(ownerDto, "test-prod-1", "up1", "ue8", "uc2", 5);

        createPoolsFor(testSub3, testSub4, testSub5);

        reconciler.reconcile(owner, Arrays.asList(testSub6, testSub7, testSub8));
        assertUpstream(testSub6, testSub3.getId());
        assertUpstream(testSub7, testSub4.getId());
        assertUpstream(testSub8, testSub5.getId());
    }

    @Test
    public void testThreeExistTwoNewQuantityMatchNewConsumer() {
        SubscriptionDTO testSub3 = createSubscription(ownerDto, "test-prod-1", "up1", "ue3", "uc1", 15);
        SubscriptionDTO testSub4 = createSubscription(ownerDto, "test-prod-1", "up1", "ue4", "uc1", 10);
        SubscriptionDTO testSub5 = createSubscription(ownerDto, "test-prod-1", "up1", "ue5", "uc1", 5);
        SubscriptionDTO testSub6 = createSubscription(ownerDto, "test-prod-1", "up1", "ue6", "uc2", 15);
        SubscriptionDTO testSub8 = createSubscription(ownerDto, "test-prod-1", "up1", "ue8", "uc2", 5);

        createPoolsFor(testSub3, testSub4, testSub5);

        reconciler.reconcile(owner, Arrays.asList(testSub6, testSub8));
        assertUpstream(testSub6, testSub3.getId());
        assertUpstream(testSub8, testSub5.getId());
    }

    @Test
    public void testTwoExistThreeNewConsumer() {
        SubscriptionDTO testSub3 = createSubscription(ownerDto, "test-prod-1", "up1", "ue3", "uc1", 15);
        SubscriptionDTO testSub4 = createSubscription(ownerDto, "test-prod-1", "up1", "ue4", "uc1", 10);
        SubscriptionDTO testSub6 = createSubscription(ownerDto, "test-prod-1", "up1", "ue6", "uc2", 15);
        SubscriptionDTO testSub7 = createSubscription(ownerDto, "test-prod-1", "up1", "ue7", "uc2", 10);
        SubscriptionDTO testSub8 = createSubscription(ownerDto, "test-prod-1", "up1", "ue8", "uc2", 5);

        createPoolsFor(testSub3, testSub4);

        reconciler.reconcile(owner, Arrays.asList(testSub6, testSub7, testSub8));
        assertUpstream(testSub6, testSub3.getId());
        assertUpstream(testSub7, testSub4.getId());
        assertUpstream(testSub8, testSub8.getId());
    }

    @Test
    public void testThreeExistOldThreeNew() {
        SubscriptionDTO testSub3 = createSubscription(ownerDto, "test-prod-1", "up1", "ue3", "uc1", 15);
        SubscriptionDTO testSub4 = createSubscription(ownerDto, "test-prod-1", "up1", "ue4", "uc1", 10);
        SubscriptionDTO testSub5 = createSubscription(ownerDto, "test-prod-1", "up1", "ue5", "uc1", 5);
        SubscriptionDTO testSub9 = createSubscription(ownerDto, "test-prod-1", "up1", "", "", 15);
        SubscriptionDTO testSub10 = createSubscription(ownerDto, "test-prod-1", "up1", "", "", 10);
        SubscriptionDTO testSub11 = createSubscription(ownerDto, "test-prod-1", "up1", "", "", 5);

        createPoolsFor(testSub9, testSub10, testSub11);

        reconciler.reconcile(owner, Arrays.asList(testSub3, testSub4, testSub5));
        assertUpstream(testSub3, testSub9.getId());
        assertUpstream(testSub4, testSub10.getId());
        assertUpstream(testSub5, testSub11.getId());
    }

    @Test
    public void testQuantMatchAllLower() {
        SubscriptionDTO testSub1 = createSubscription(ownerDto, "test-prod-1", "up1", "ue1", "uc1", 25);
        SubscriptionDTO testSub2 = createSubscription(ownerDto, "test-prod-1", "up1", "ue2", "uc1", 20);
        SubscriptionDTO testSub3 = createSubscription(ownerDto, "test-prod-1", "up1", "ue3", "uc1", 15);
        SubscriptionDTO testSub12 = createSubscription(ownerDto, "test-prod-1", "up1", "ue12", "uc3", 23);
        SubscriptionDTO testSub13 = createSubscription(ownerDto, "test-prod-1", "up1", "ue13", "uc3", 17);
        SubscriptionDTO testSub14 = createSubscription(ownerDto, "test-prod-1", "up1", "ue14", "uc3", 10);

        createPoolsFor(testSub1, testSub2, testSub3);

        reconciler.reconcile(owner, Arrays.asList(testSub12, testSub13, testSub14));

        // Quantities 25, 20, 15 should be replaced by new pools with 23, 17, 10:
        assertUpstream(testSub12, testSub1.getId());
        assertUpstream(testSub13, testSub2.getId());
        assertUpstream(testSub14, testSub3.getId());
    }

    @Test
    public void testQuantMatchMix() {
        SubscriptionDTO testSub2 = createSubscription(ownerDto, "test-prod-1", "up1", "ue2", "uc1", 20);
        SubscriptionDTO testSub3 = createSubscription(ownerDto, "test-prod-1", "up1", "ue3", "uc1", 15);
        SubscriptionDTO testSub4 = createSubscription(ownerDto, "test-prod-1", "up1", "ue4", "uc1", 10);
        SubscriptionDTO testSub5 = createSubscription(ownerDto, "test-prod-1", "up1", "ue5", "uc1", 5);
        SubscriptionDTO testSub12 = createSubscription(ownerDto, "test-prod-1", "up1", "ue12", "uc3", 23);
        SubscriptionDTO testSub14 = createSubscription(ownerDto, "test-prod-1", "up1", "ue14", "uc3", 10);

        createPoolsFor(testSub2, testSub3, testSub4, testSub5);

        reconciler.reconcile(owner, Arrays.asList(testSub12, testSub14));

        assertUpstream(testSub12, testSub2.getId());
        assertUpstream(testSub14, testSub4.getId());
    }

    @Test
    public void testQuantMatchAllSame() {
        SubscriptionDTO testSub3 = createSubscription(ownerDto, "test-prod-1", "up1", "ue3", "uc1", 15);
        SubscriptionDTO testSub15 = createSubscription(ownerDto, "test-prod-1", "up1", "ue15", "uc1", 15);
        SubscriptionDTO testSub16 = createSubscription(ownerDto, "test-prod-1", "up1", "ue16", "uc1", 15);

        createPoolsFor(testSub3, testSub15);

        reconciler.reconcile(owner, Arrays.asList(testSub3, testSub16));

        // Quantities 25, 20, 15 should be replaced by new pools with 23, 17, 10:
        assertUpstream(testSub3, testSub3.getId());
        assertUpstream(testSub16, testSub15.getId());
    }

    @Test
    public void testMultiPools() {
        SubscriptionDTO testSub1 = createSubscription(ownerDto, "test-prod-1", "up1", "ue1", "uc1", 25);
        SubscriptionDTO testSub2 = createSubscription(ownerDto, "test-prod-1", "up1", "ue2", "uc1", 20);
        SubscriptionDTO testSub3 = createSubscription(ownerDto, "test-prod-1", "up1", "ue3", "uc1", 15);
        SubscriptionDTO testSub4 = createSubscription(ownerDto, "test-prod-1", "up1", "ue4", "uc1", 10);
        SubscriptionDTO testSub5 = createSubscription(ownerDto, "test-prod-1", "up1", "ue5", "uc1", 5);
        SubscriptionDTO testSub20 = createSubscription(ownerDto, "test-prod-1", "up2", "ue20", "uc1", 25);
        SubscriptionDTO testSub21 = createSubscription(ownerDto, "test-prod-1", "up2", "ue21", "uc1", 20);
        SubscriptionDTO testSub22 = createSubscription(ownerDto, "test-prod-1", "up2", "ue22", "uc1", 15);
        SubscriptionDTO testSub24 = createSubscription(ownerDto, "test-prod-1", "up2", "ue24", "uc1", 5);
        SubscriptionDTO testSub30 = createSubscription(ownerDto, "test-prod-1", "up3", "ue30", "uc1", 25);
        SubscriptionDTO testSub31 = createSubscription(ownerDto, "test-prod-1", "up3", "ue31", "uc1", 20);
        SubscriptionDTO testSub32 = createSubscription(ownerDto, "test-prod-1", "up3", "ue32", "uc1", 15);
        SubscriptionDTO testSub33 = createSubscription(ownerDto, "test-prod-1", "up3", "ue33", "uc1", 10);
        SubscriptionDTO testSub34 = createSubscription(ownerDto, "test-prod-1", "up3", "ue34", "uc1", 5);

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

    private SubscriptionDTO createSubscription(OwnerDTO daOwner, String productId,
        String poolId, String entId, String conId, long quantity) {

        ProductDTO productDTO = new ProductDTO();
        productDTO.setId(productId);
        productDTO.setName(productId);

        SubscriptionDTO sub = new SubscriptionDTO();
        sub.setProduct(productDTO);
        sub.setUpstreamPoolId(poolId);
        sub.setUpstreamEntitlementId(entId);
        sub.setUpstreamConsumerId(conId);
        sub.setQuantity(quantity);
        sub.setOwner(daOwner);
        sub.setId("" + index++);

        return sub;
    }

    protected EntitlementCertificate createEntitlementCertificate(String key, String cert) {
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
