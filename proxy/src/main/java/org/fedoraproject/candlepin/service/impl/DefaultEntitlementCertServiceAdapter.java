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
package org.fedoraproject.candlepin.service.impl;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.fedoraproject.candlepin.model.CertificateSerial;
import org.fedoraproject.candlepin.model.CertificateSerialCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.EntitlementCertificateCurator;
import org.fedoraproject.candlepin.model.KeyPairCurator;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProvidedProduct;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.fedoraproject.candlepin.pki.X509ExtensionWrapper;
import org.fedoraproject.candlepin.service.BaseEntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.util.Util;
import org.fedoraproject.candlepin.util.X509ExtensionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

/**
 * DefaultEntitlementCertServiceAdapter
 */
public class DefaultEntitlementCertServiceAdapter extends 
    BaseEntitlementCertServiceAdapter {
    
    private PKIUtility pki;
    private X509ExtensionUtil extensionUtil;
    private KeyPairCurator keyPairCurator;
    private CertificateSerialCurator serialCurator;
    private ProductServiceAdapter productAdapter;
    
    private static Logger log = 
        LoggerFactory.getLogger(DefaultEntitlementCertServiceAdapter.class);
    
    @Inject
    public DefaultEntitlementCertServiceAdapter(PKIUtility pki,
        X509ExtensionUtil extensionUtil,
        EntitlementCertificateCurator entCertCurator, 
        KeyPairCurator keyPairCurator,
        CertificateSerialCurator serialCurator,
        ProductServiceAdapter productAdapter) {
        
        this.pki = pki;
        this.extensionUtil = extensionUtil;
        this.entCertCurator = entCertCurator;
        this.keyPairCurator = keyPairCurator;
        this.serialCurator = serialCurator;
        this.productAdapter = productAdapter;
    }

    
    // NOTE: we use entitlement here, but it version does not...
    // NOTE: we can get consumer from entitlement.getConsumer()
    @Override
    public EntitlementCertificate generateEntitlementCert(Entitlement entitlement, 
        Subscription sub, Product product)
        throws GeneralSecurityException, IOException {
        
        log.debug("Generating entitlement cert for:");
        log.debug("   consumer: {}", entitlement.getConsumer().getUuid());
        log.debug("   product: {}" , product.getId());
        log.debug("entitlement's endDt == subs endDt? {} == {} ?", 
            entitlement.getEndDate(), sub.getEndDate());
        Preconditions
            .checkArgument(
                entitlement.getEndDate().getTime() == sub.getEndDate().getTime(),
                "Entitlement #%s 's endDt[%s] must equal Subscription #%s 's endDt[%s]",
                entitlement.getId(), sub.getId(), entitlement.getEndDate(), sub
                    .getEndDate());

        KeyPair keyPair = keyPairCurator.getConsumerKeyPair(entitlement.getConsumer());
        CertificateSerial serial = new CertificateSerial(entitlement.getEndDate());
        // We need the sequence generated id before we create the EntitlementCertificate,
        // otherwise we could have used cascading create
        serialCurator.create(serial);
        
        X509Certificate x509Cert = createX509Certificate(entitlement, sub,
            product, BigInteger.valueOf(serial.getId()), keyPair);
        
        EntitlementCertificate cert = new EntitlementCertificate();
        cert.setSerial(serial);
        cert.setKeyAsBytes(pki.getPemEncoded(keyPair.getPrivate()));
        cert.setCertAsBytes(this.pki.getPemEncoded(x509Cert));
        cert.setEntitlement(entitlement);
        
        log.debug("Generated cert serial number: " + serial.getId());
        log.debug("Key: " + cert.getKey());
        log.debug("Cert: " + cert.getCert());
        
        entitlement.getCertificates().add(cert);
        entCertCurator.create(cert);
        return cert;
    }
    
    @Override
    public void revokeEntitlementCertificates(Entitlement e) {
        for (EntitlementCertificate cert : e.getCertificates()) {
            CertificateSerial serial = cert.getSerial();
            serial.setRevoked(true);
            
            this.serialCurator.merge(serial);
        }
    }

    private Set<Product> getProvidedProducts(Pool pool, Subscription sub) {
        Set<Product> providedProducts = new HashSet<Product>();
        if (sub != null) {
            providedProducts = sub.getProvidedProducts();
        }
        else {
            // If this pool doesn't have a subscription associated with it, we need to
            // lookup all the Product objects manually:
            for (ProvidedProduct providedProduct : pool.getProvidedProducts()) {
                providedProducts.add(
                    productAdapter.getProductById(providedProduct.getId()));
            }
        }
        return providedProducts;
    }

    public X509Certificate createX509Certificate(Entitlement ent,
        Subscription sub, Product product, BigInteger serialNumber,
        KeyPair keyPair) throws GeneralSecurityException, IOException {
        // oiduitl is busted at the moment, so do this manually
        Set<X509ExtensionWrapper> extensions = new LinkedHashSet<X509ExtensionWrapper>();
        Set<Product> products = new HashSet<Product>(getProvidedProducts(ent
            .getPool(), sub));
        products.add(product);

        for (Product prod : Collections2
            .filter(products, PROD_FILTER_PREDICATE)) {
            extensions.addAll(extensionUtil.productExtensions(prod));
            extensions.addAll(extensionUtil.contentExtensions(prod));
        }

        if (sub != null) {
            extensions.addAll(extensionUtil.subscriptionExtensions(sub));
        }
        
        extensions.addAll(extensionUtil.entitlementExtensions(ent));
        extensions.addAll(extensionUtil.consumerExtensions(ent.getConsumer()));
        // TODO: rounding time could give the consumer like extra 1 day at most.
        // Should we check that?
        X509Certificate x509Cert = this.pki
            .createX509Certificate(createDN(ent), extensions, ent
                .getStartDate(), Util.roundToMidnight(ent.getEndDate()),
                keyPair, serialNumber, null);
        return x509Cert;
    }
    
    private String createDN(Entitlement ent) {
        StringBuilder sb = new StringBuilder("CN=");
        sb.append(ent.getId());
        return sb.toString();
    }

    private static final Predicate<Product>
    PROD_FILTER_PREDICATE = new Predicate<Product>() {
        @Override
        public boolean apply(Product product) {
            return product != null && StringUtils.isNumeric(product.getId());
        }
    };
}
