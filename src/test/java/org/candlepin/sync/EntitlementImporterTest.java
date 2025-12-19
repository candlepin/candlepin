/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.manifest.v1.BrandingDTO;
import org.candlepin.dto.manifest.v1.CertificateSerialDTO;
import org.candlepin.dto.manifest.v1.EntitlementDTO;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.model.Branding;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import tools.jackson.databind.ObjectMapper;

import java.io.Reader;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EntitlementImporterTest {

    // TODO: FIXME:
    // Brittle mocks are brittle. If/when the importer factor happens, rip out all these mocks
    // and mock either the entire underlying system, or nothing at all.
    // Mocking implementation-specific details that aren't critical parts of the design or
    // underlying functionality just leads to testing/maintenance pain and general badness.

    @Mock private CertificateSerialCurator certSerialCurator;
    @Mock private OwnerCurator ownerCurator;
    @Mock private CdnCurator cdnCurator;
    @Mock private ObjectMapper om;
    @Mock private ProductCurator mockProductCurator;
    @Mock private EnvironmentCurator mockEnvironmentCurator;
    @Mock private ConsumerTypeCurator mockConsumerTypeCurator;

    private I18n i18n;
    private Owner owner;
    private Consumer consumer;
    private EntitlementCertificate cert;
    private Reader reader;
    private Meta meta;
    private ModelTranslator translator;


    @BeforeEach
    public void init() {
        this.owner = new Owner();
        this.owner.setId("test-owner-id");

        this.translator = new StandardTranslator(mockConsumerTypeCurator,
            mockEnvironmentCurator,
            ownerCurator);

        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        ConsumerType ctype = TestUtil.createConsumerType();
        ctype.setId("test-ctype");

        consumer = TestUtil.createConsumer(ctype, owner);

        cert = createEntitlementCertificate("my-test-key", "my-cert");
        cert.setId("test-id");

        reader = mock(Reader.class);

        meta = new Meta();
        meta.setCdnLabel("test-cdn");

        Cdn testCdn = new Cdn("test-cdn", "Test CDN", "https://test.url.com");
        when(this.cdnCurator.getByLabel("test-cdn")).thenReturn(testCdn);
    }

    private EntitlementImporter buildEntitlementImporter(Product... products) {
        Stream<Product> pstream = products != null ? Stream.of(products) : Stream.<Product>empty();
        Map<String, ProductDTO> productMap = pstream
            .map(this.translator.getStreamMapper(Product.class, ProductDTO.class))
            .collect(Collectors.toMap(ProductDTO::getId, Function.identity()));

        return new EntitlementImporter(this.cdnCurator, this.i18n, this.translator, productMap);
    }

    private EntitlementCertificate createEntitlementCertificate(String key, String cert) {
        EntitlementCertificate toReturn = new EntitlementCertificate();
        CertificateSerial certSerial = new CertificateSerial(new Date());
        certSerial.setUpdated(new Date());
        certSerial.setCreated(new Date());
        toReturn.setKeyAsBytes(key.getBytes());
        toReturn.setCertAsBytes(cert.getBytes());
        toReturn.setSerial(certSerial);

        return toReturn;
    }

    /**
     * Converts the input collection of products into a set of the product IDs. Note that this method
     * *does not* collect product IDs from the product's children.
     *
     * @param products
     *  a collection of products from which to pull product IDs
     *
     * @return
     *  a set containing the IDs of the given products
     */
    private Set<String> collectProductIds(Collection<? extends ProductInfo> products) {
        if (products == null) {
            return null;
        }

        return products.stream()
            .map(ProductInfo::getId)
            .collect(Collectors.toSet());
    }

    @Test
    public void fullImport() throws Exception {
        Product derivedProvided1 = TestUtil.createProduct();
        Product derived = TestUtil.createProduct()
            .setProvidedProducts(List.of(derivedProvided1));

        Product provided1 = TestUtil.createProduct();
        Product product = TestUtil.createProduct()
            .setProvidedProducts(List.of(provided1))
            .setDerivedProduct(derived);

        Pool pool = TestUtil.createPool(owner, product)
            .setQuantity(3L);

        Entitlement ent = TestUtil.createEntitlement(owner, consumer, pool, cert);
        ent.setQuantity(3);
        EntitlementDTO dtoEnt = this.translator.translate(ent, EntitlementDTO.class);

        BrandingDTO brandingDTO = new BrandingDTO();
        brandingDTO.setName("brand_name");
        brandingDTO.setProductId("eng_id_1");
        brandingDTO.setType("OS");
        brandingDTO.setId("db_id");
        dtoEnt.getPool().addBranding(brandingDTO);

        when(om.readValue(reader, EntitlementDTO.class)).thenReturn(dtoEnt);

        EntitlementImporter importer = this.buildEntitlementImporter(product, provided1, derived,
            derivedProvided1);

        SubscriptionDTO sub = importer.importObject(om, reader, owner, consumer.getUuid(), meta);

        assertEquals(pool.getId(), sub.getUpstreamPoolId());
        assertEquals(consumer.getUuid(), sub.getUpstreamConsumerId());
        assertEquals(ent.getId(), sub.getUpstreamEntitlementId());

        assertEquals(owner.getKey(), sub.getOwner().getKey());
        assertEquals(ent.getStartDate(), sub.getStartDate());
        assertEquals(ent.getEndDate(), sub.getEndDate());

        assertEquals(pool.getAccountNumber(), sub.getAccountNumber());
        assertEquals(pool.getContractNumber(), sub.getContractNumber());
        assertEquals(pool.getOrderNumber(), sub.getOrderNumber());

        assertEquals(ent.getQuantity().intValue(), sub.getQuantity().intValue());

        ProductDTO subProd = sub.getProduct();
        assertNotNull(subProd);
        assertEquals(product.getId(), subProd.getId());
        assertEquals(product.getProvidedProducts().size(), subProd.getProvidedProducts().size());
        assertEquals(this.collectProductIds(product.getProvidedProducts()),
            this.collectProductIds(subProd.getProvidedProducts()));

        ProductDTO subDerivedProd = subProd.getDerivedProduct();
        assertNotNull(subDerivedProd);
        assertEquals(derived.getId(), subDerivedProd.getId());
        assertEquals(derived.getProvidedProducts().size(), subDerivedProd.getProvidedProducts().size());
        assertEquals(this.collectProductIds(derived.getProvidedProducts()),
            this.collectProductIds(subDerivedProd.getProvidedProducts()));

        assertNotNull(sub.getCertificate());
        CertificateSerialDTO serial = sub.getCertificate().getSerial();
        assertEquals(cert.getSerial().getExpiration(), serial.getExpiration());
        assertEquals(cert.getSerial().getCreated(), serial.getCreated());
        assertEquals(cert.getSerial().getUpdated(), serial.getUpdated());

        assertEquals(sub.getCdn().getLabel(), meta.getCdnLabel());

        assertEquals(brandingDTO, sub.getProduct().getBranding().toArray()[0]);
    }

    /**
     * This test verifies branding's weird existence in the manifest. Namely, that it lives on pool
     * (still), but doesn't have a good way of merging due to the nature of the branding object
     * lacking a business key. Additionally, at some point we did not have support for exporting
     * branding on the product directly, so it was flagged with @JsonIgnore to prevent it.
     *
     * Still, due to the pseudo-stateless nature of our import step, we have to treat the operation
     * like a merge, despite it not really working as such. The end result should be that branding
     * for a given pool is migrated to its product, but only deduplicated in the case of an exact
     * match on all branding properties.
     */
    @Test
    public void testImportEntitlementMergesPoolBrandingData() throws Exception {
        Branding branding1 = TestUtil.createBranding("pid1", "test_branding-1");
        Branding branding2 = TestUtil.createBranding("pid2", "test_branding-2");
        Branding branding3 = TestUtil.createBranding("pid3", "test_branding-3");

        Product product1a = TestUtil.createProduct()
            .setId("shared_pid")
            .setBranding(List.of(branding1, branding2));
        Product product1b = TestUtil.createProduct()
            .setId("shared_pid")
            .setBranding(List.of(branding2, branding3));

        Pool pool1 = TestUtil.createPool(this.owner, product1a);
        Pool pool2 = TestUtil.createPool(this.owner, product1b);
        Entitlement entitlement1 = TestUtil.createEntitlement(this.owner, this.consumer, pool1, this.cert)
            .setQuantity(3);
        Entitlement entitlement2 = TestUtil.createEntitlement(this.owner, this.consumer, pool2, this.cert)
            .setQuantity(3);

        EntitlementDTO entDto1 = this.translator.translate(entitlement1, EntitlementDTO.class);
        EntitlementDTO entDto2 = this.translator.translate(entitlement2, EntitlementDTO.class);

        // See note at the top of this class for why this mock is bad
        when(this.om.readValue(this.reader, EntitlementDTO.class)).thenReturn(entDto1).thenReturn(entDto2);

        EntitlementImporter importer = this.buildEntitlementImporter(product1a);

        SubscriptionDTO sub1 = importer.importObject(this.om, this.reader, this.owner,
            this.consumer.getUuid(), this.meta);
        SubscriptionDTO sub2 = importer.importObject(this.om, this.reader, this.owner,
            this.consumer.getUuid(), this.meta);

        assertNotNull(sub1);
        assertNotNull(sub2);

        BrandingDTO bdto1 = this.translator.translate(branding1, BrandingDTO.class);
        BrandingDTO bdto2 = this.translator.translate(branding2, BrandingDTO.class);
        BrandingDTO bdto3 = this.translator.translate(branding3, BrandingDTO.class);
        Set<BrandingDTO> expectedBrandingDTOs = Set.of(bdto1, bdto2, bdto3);

        // Because our product IDs are the same, even though the products used by each pool differ,
        // our resultant pools should share the same product, and that product should have a union
        // of all branding seen on both instances
        ProductDTO pdto1 = sub1.getProduct();
        ProductDTO pdto2 = sub2.getProduct();

        assertEquals(pdto1, pdto2);
        assertEquals(expectedBrandingDTOs, pdto1.getBranding());
        assertEquals(expectedBrandingDTOs, pdto2.getBranding());
    }

    /**
     * This test verifies that child product information on entitlement pool is merged with that on
     * the pool's product, following the rule of "product definition wins"
     */
    @Test
    public void testImportEntitlementMergesPoolProductData() throws Exception {
        Product derivedProvided1 = TestUtil.createProduct();
        Product derivedProvided2 = TestUtil.createProduct();
        Product derivedProvided3 = TestUtil.createProduct();
        Product derived = TestUtil.createProduct()
            .setProvidedProducts(List.of(derivedProvided1, derivedProvided2, derivedProvided3));

        Product provided1 = TestUtil.createProduct();
        Product provided2 = TestUtil.createProduct();
        Product provided3 = TestUtil.createProduct();

        // The diffentiation in name will help us determine that 1b is the version pulled from
        // the mocked product import step, and allow us to confirm that the product data was
        // indeed pulled from the importer and wasn't already present as it is on 1a
        Product product1a = TestUtil.createProduct()
            .setId("shared_pid")
            .setName("product1a")
            .setProvidedProducts(List.of(provided1, provided2, provided3))
            .setDerivedProduct(derived);

        Product product1b = TestUtil.createProduct()
            .setId("shared_pid")
            .setName("product1b");

        Pool pool1 = TestUtil.createPool(this.owner, product1a);
        Entitlement entitlement1 = TestUtil.createEntitlement(this.owner, this.consumer, pool1, this.cert)
            .setQuantity(3);

        EntitlementDTO entDto1 = this.translator.translate(entitlement1, EntitlementDTO.class);

        // See note at the top of this class for why this mock is bad
        when(this.om.readValue(this.reader, EntitlementDTO.class)).thenReturn(entDto1);

        // Impl note: this *must* provided product1b, *not* 1a.
        EntitlementImporter importer = this.buildEntitlementImporter(derivedProvided1, derivedProvided2,
            derivedProvided3, derived, provided1, provided2, provided3, product1b);
        SubscriptionDTO sub1 = importer.importObject(this.om, this.reader, this.owner,
            this.consumer.getUuid(), this.meta);

        ProductDTO subProd = sub1.getProduct();
        assertNotNull(subProd);
        assertEquals(product1b.getId(), subProd.getId());
        assertEquals(product1b.getName(), subProd.getName()); // *must* be product1b

        // We're using product1a as our source of truth from here down, as that is our origin of
        // children products, and we're expecting the serialized product1b was updated with the
        // children product information from the pool (which used 1a for serialization)
        assertNotNull(subProd.getProvidedProducts());
        assertEquals(product1a.getProvidedProducts().size(), subProd.getProvidedProducts().size());
        assertEquals(this.collectProductIds(product1a.getProvidedProducts()),
            this.collectProductIds(subProd.getProvidedProducts()));

        ProductDTO subDerivedProd = subProd.getDerivedProduct();
        assertNotNull(subDerivedProd);
        assertEquals(derived.getId(), subDerivedProd.getId());

        assertNotNull(subDerivedProd.getProvidedProducts());
        assertEquals(product1a.getProvidedProducts().size(), subDerivedProd.getProvidedProducts().size());
        assertEquals(this.collectProductIds(derived.getProvidedProducts()),
            this.collectProductIds(subDerivedProd.getProvidedProducts()));
    }

    /**
     * This test verifies that in the case where we have two or more entitlements with pools that
     * reference the same product but have divergent [derived] provided product data, that the
     * product ends up with the union of child product data from all pools
     */
    @Test
    public void testImportEntitlementsWithDivergentPoolProductDataCreatesProductWithDataUnion()
        throws Exception {

        Product derivedProvided1 = TestUtil.createProduct();
        Product derivedProvided2 = TestUtil.createProduct();
        Product derivedProvided3 = TestUtil.createProduct();

        Product derived1a = TestUtil.createProduct()
            .setId("derived_pid")
            .setProvidedProducts(List.of(derivedProvided1, derivedProvided2));

        Product derived1b = TestUtil.createProduct()
            .setId("derived_pid")
            .setProvidedProducts(List.of(derivedProvided2, derivedProvided3));

        Product provided1 = TestUtil.createProduct();
        Product provided2 = TestUtil.createProduct();
        Product provided3 = TestUtil.createProduct();

        Product product1a = TestUtil.createProduct()
            .setId("shared_pid")
            .setProvidedProducts(List.of(provided1, provided2))
            .setDerivedProduct(derived1a);

        Product product1b = TestUtil.createProduct()
            .setId("shared_pid")
            .setProvidedProducts(List.of(provided2, provided3))
            .setDerivedProduct(derived1b);

        Pool pool1 = TestUtil.createPool(this.owner, product1a);
        Pool pool2 = TestUtil.createPool(this.owner, product1b);
        Entitlement entitlement1 = TestUtil.createEntitlement(this.owner, this.consumer, pool1, this.cert)
            .setQuantity(3);
        Entitlement entitlement2 = TestUtil.createEntitlement(this.owner, this.consumer, pool2, this.cert)
            .setQuantity(3);

        EntitlementDTO entDto1 = this.translator.translate(entitlement1, EntitlementDTO.class);
        EntitlementDTO entDto2 = this.translator.translate(entitlement2, EntitlementDTO.class);

        // See note at the top of this class for why this mock is bad
        when(this.om.readValue(this.reader, EntitlementDTO.class)).thenReturn(entDto1).thenReturn(entDto2);

        EntitlementImporter importer = this.buildEntitlementImporter(derivedProvided1, derivedProvided2,
            derivedProvided3, derived1a, provided1, provided2, provided3, product1a);

        // Impl note: even though we don't use sub2, it's critical we import it so its pool data
        // gets processed
        SubscriptionDTO sub1 = importer.importObject(this.om, this.reader, this.owner,
            this.consumer.getUuid(), this.meta);
        SubscriptionDTO sub2 = importer.importObject(this.om, this.reader, this.owner,
            this.consumer.getUuid(), this.meta);

        ProductDTO subProd = sub1.getProduct();
        assertNotNull(subProd);
        assertEquals(product1a.getId(), subProd.getId());

        // We expect the product to contain the union of all provided and derived provided products
        // found across all the pools defining whatever weird set of provided product info they may
        // have
        Set<String> expectedProvidedPIDs = this.collectProductIds(List.of(provided1, provided2, provided3));
        Set<String> expectedDerivedPPIDs = this.collectProductIds(List.of(derivedProvided1, derivedProvided2,
            derivedProvided3));

        assertNotNull(subProd.getProvidedProducts());
        assertEquals(expectedProvidedPIDs.size(), subProd.getProvidedProducts().size());
        assertEquals(expectedProvidedPIDs, this.collectProductIds(subProd.getProvidedProducts()));

        ProductDTO subDerivedProd = subProd.getDerivedProduct();
        assertNotNull(subDerivedProd);
        assertEquals(derived1a.getId(), subDerivedProd.getId());

        assertNotNull(subDerivedProd.getProvidedProducts());
        assertEquals(expectedDerivedPPIDs.size(), subDerivedProd.getProvidedProducts().size());
        assertEquals(expectedDerivedPPIDs, this.collectProductIds(subDerivedProd.getProvidedProducts()));
    }

}
