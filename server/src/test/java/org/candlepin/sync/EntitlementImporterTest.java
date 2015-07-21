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

import org.candlepin.audit.EventSink;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.dto.Subscription;
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

/**
 * EntitlementImporterTest
 */
@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("synthetic-access")
public class EntitlementImporterTest {

    @Mock private EventSink sink;
    @Mock private CertificateSerialCurator certSerialCurator;
    @Mock private CdnCurator cdnCurator;
    @Mock private ObjectMapper om;

    private Owner owner;
    private EntitlementImporter importer;
    private I18n i18n;
    private Consumer consumer;
    private ConsumerDto consumerDto;
    private EntitlementCertificate cert;
    private Reader reader;
    private Meta meta;
    private Cdn testCdn;


    @Before
    public void init() {
        this.owner = new Owner();

        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        this.importer = new EntitlementImporter(certSerialCurator, cdnCurator,
            i18n);

        consumer = TestUtil.createConsumer(owner);
        consumerDto = new ConsumerDto(consumer.getUuid(), consumer.getName(),
            consumer.getType(), consumer.getOwner(), "", "");

        cert = createEntitlementCertificate("my-test-key", "my-cert");
        reader = mock(Reader.class);

        meta = new Meta();
        meta.setCdnLabel("test-cdn");
        testCdn = new Cdn("test-cdn", "Test CDN", "https://test.url.com");
        when(cdnCurator.lookupByLabel("test-cdn")).thenReturn(testCdn);
    }

    @Test
    public void fullImport() throws Exception {
        Product parentProduct = TestUtil.createProduct(owner);

        Product derivedProduct = TestUtil.createProduct(owner);

        Product provided1 = TestUtil.createProduct(owner);
        Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();
        providedProducts.add(new ProvidedProduct(provided1));

        Product derivedProvided1 = TestUtil.createProduct(owner);
        Set<ProvidedProduct> derivedProvidedProducts = new HashSet<ProvidedProduct>();
        derivedProvidedProducts.add(new ProvidedProduct(derivedProvided1));

        Pool pool = TestUtil.createPool(
            owner, parentProduct, new HashSet<Product>(), derivedProduct, new HashSet<Product>(), 3
        );
        // Add the provided products as DTOs:
        pool.setProvidedProductDtos(providedProducts);
        pool.setDerivedProvidedProductDtos(derivedProvidedProducts);


        Entitlement ent = TestUtil.createEntitlement(owner, consumer, pool, cert);
        ent.setQuantity(3);
        when(om.readValue(reader, Entitlement.class)).thenReturn(ent);

        // Create our expected products
        Map<String, Product> productsById = buildProductCache(
                parentProduct, provided1, derivedProduct, derivedProvided1);

        Subscription sub = importer.importObject(om, reader, owner, productsById,
                consumerDto, meta);

        assertEquals(pool.getId(), sub.getUpstreamPoolId());
        assertEquals(consumer.getUuid(), sub.getUpstreamConsumerId());
        assertEquals(ent.getId(), sub.getUpstreamEntitlementId());

        assertEquals(owner, sub.getOwner());
        assertEquals(ent.getStartDate(), sub.getStartDate());
        assertEquals(ent.getEndDate(), sub.getEndDate());

        assertEquals(pool.getAccountNumber(), sub.getAccountNumber());
        assertEquals(pool.getContractNumber(), sub.getContractNumber());
        assertEquals(pool.getOrderNumber(), sub.getOrderNumber());

        assertEquals(ent.getQuantity().intValue(), sub.getQuantity().intValue());

        assertEquals(parentProduct, sub.getProduct());
        assertEquals(providedProducts.size(), sub.getProvidedProducts().size());
        assertEquals(
            provided1.getId(),
            sub.getProvidedProducts().iterator().next().getId()
        );

        assertEquals(derivedProduct, sub.getDerivedProduct());
        assertEquals(1, sub.getDerivedProvidedProducts().size());
        assertEquals(
            derivedProvided1.getId(),
            sub.getDerivedProvidedProducts().iterator().next().getId()
        );

        assertNotNull(sub.getCertificate());
        CertificateSerial serial = sub.getCertificate().getSerial();
        assertEquals(cert.getSerial().isCollected(), serial.isCollected());
        assertEquals(cert.getSerial().getExpiration(), serial.getExpiration());
        assertEquals(cert.getSerial().getCreated(), serial.getCreated());
        assertEquals(cert.getSerial().getUpdated(), serial.getUpdated());

        assertEquals(sub.getCdn().getLabel(), meta.getCdnLabel());
    }

    private Map<String, Product> buildProductCache(Product... products) {
        Map<String, Product> productsById = new HashMap<String, Product>();
        for (Product p : products) {
            productsById.put(p.getId(), p);
        }
        return productsById;
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
