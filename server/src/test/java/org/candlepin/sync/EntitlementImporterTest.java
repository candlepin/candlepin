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

import org.candlepin.audit.EventSink;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.manifest.v1.BrandingDTO;
import org.candlepin.dto.manifest.v1.CertificateSerialDTO;
import org.candlepin.dto.manifest.v1.ConsumerDTO;
import org.candlepin.dto.manifest.v1.EntitlementDTO;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.test.TestUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.io.Reader;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * EntitlementImporterTest
 */
@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("synthetic-access")
public class EntitlementImporterTest {

    @Mock private EventSink sink;
    @Mock private CertificateSerialCurator certSerialCurator;
    @Mock private OwnerCurator ownerCurator;
    @Mock private CdnCurator cdnCurator;
    @Mock private ObjectMapper om;
    @Mock private ProductCurator mockProductCurator;
    @Mock private EntitlementCurator ec;
    @Mock private EnvironmentCurator mockEnvironmentCurator;
    @Mock private ConsumerTypeCurator mockConsumerTypeCurator;

    private Owner owner;
    private EntitlementImporter importer;
    private I18n i18n;
    private Consumer consumer;
    private ConsumerDTO consumerDTO;
    private EntitlementCertificate cert;
    private Reader reader;
    private Meta meta;
    private Cdn testCdn;
    private ModelTranslator translator;


    @Before
    public void init() {
        this.owner = new Owner();
        this.owner.setId("test-owner-id");

        this.translator = new StandardTranslator(mockConsumerTypeCurator,
            mockEnvironmentCurator,
            ownerCurator);

        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        this.importer = new EntitlementImporter(certSerialCurator, cdnCurator, i18n, mockProductCurator, ec,
            translator);

        ConsumerType ctype = TestUtil.createConsumerType();
        ctype.setId("test-ctype");

        consumer = TestUtil.createConsumer(ctype, owner);

        consumerDTO = this.translator.translate(consumer, ConsumerDTO.class);
        consumerDTO.setUrlWeb("");
        consumerDTO.setUrlApi("");

        when(this.mockConsumerTypeCurator.getConsumerType(eq(consumer))).thenReturn(ctype);
        when(this.mockConsumerTypeCurator.get(eq(ctype.getId()))).thenReturn(ctype);

        cert = createEntitlementCertificate("my-test-key", "my-cert");
        cert.setId("test-id");

        reader = mock(Reader.class);

        meta = new Meta();
        meta.setCdnLabel("test-cdn");
        testCdn = new Cdn("test-cdn", "Test CDN", "https://test.url.com");
        when(cdnCurator.getByLabel("test-cdn")).thenReturn(testCdn);
    }

    @Test
    public void fullImport() throws Exception {
        Product parentProduct = TestUtil.createProduct();
        Product derivedProduct = TestUtil.createProduct();

        Product provided1 = TestUtil.createProduct();
        Set<Product> providedProducts = new HashSet<>();
        providedProducts.add(new Product(provided1));
        parentProduct.setProvidedProducts(providedProducts);

        Product derivedProvided1 = TestUtil.createProduct();
        Set<Product> derivedProvidedProducts = new HashSet<>();
        derivedProvidedProducts.add(new Product(derivedProvided1));
        derivedProduct.setProvidedProducts(derivedProvidedProducts);

        Pool pool = TestUtil.createPool(
            owner, parentProduct, new HashSet<>(), derivedProduct, new HashSet<>(), 3);

        pool.setProvidedProducts(providedProducts);
        pool.setDerivedProvidedProducts(derivedProvidedProducts);


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

        // Create our expected products
        Map<String, ProductDTO> productsById = buildProductCache(
            parentProduct, provided1, derivedProduct, derivedProvided1);

        SubscriptionDTO sub = importer.importObject(
            om, reader, owner, productsById, consumerDTO.getUuid(), meta);

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

        assertEquals(parentProduct.getId(), sub.getProduct().getId());
        assertEquals(providedProducts.size(), sub.getProvidedProducts().size());
        assertEquals(provided1.getId(), sub.getProvidedProducts().iterator().next().getId());

        assertEquals(derivedProduct.getId(), sub.getDerivedProduct().getId());
        assertEquals(1, sub.getDerivedProvidedProducts().size());
        assertEquals(derivedProvided1.getId(), sub.getDerivedProvidedProducts().iterator().next().getId());

        assertNotNull(sub.getCertificate());
        CertificateSerialDTO serial = sub.getCertificate().getSerial();
        assertEquals(cert.getSerial().isCollected(), serial.isCollected());
        assertEquals(cert.getSerial().getExpiration(), serial.getExpiration());
        assertEquals(cert.getSerial().getCreated(), serial.getCreated());
        assertEquals(cert.getSerial().getUpdated(), serial.getUpdated());

        assertEquals(sub.getCdn().getLabel(), meta.getCdnLabel());

        assertEquals(brandingDTO, sub.getProduct().getBranding().toArray()[0]);
    }

    private Map<String, ProductDTO> buildProductCache(Product... products) {
        Map<String, ProductDTO> productsById = new HashMap<>();
        for (Product p : products) {
            productsById.put(p.getId(), this.translator.translate(p, ProductDTO.class));
        }

        return productsById;
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
