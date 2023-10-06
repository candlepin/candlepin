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
package org.candlepin.controller;

import static org.candlepin.model.SourceSubscription.PRIMARY_POOL_SUB_KEY;

import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCertificate;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.SubscriptionsCertificate;
import org.candlepin.service.model.CdnInfo;
import org.candlepin.service.model.CertificateInfo;
import org.candlepin.service.model.CertificateSerialInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.SubscriptionInfo;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;



public class PoolConverter {
    private final OwnerCurator ownerCurator;
    private final ProductCurator productCurator;
    private final CdnCurator cdnCurator;

    @Inject
    public PoolConverter(
        OwnerCurator ownerCurator,
        ProductCurator productCurator,
        CdnCurator cdnCurator) {

        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.productCurator = Objects.requireNonNull(productCurator);
        this.cdnCurator = Objects.requireNonNull(cdnCurator);
    }

    /*
     * if you are using this method, you might want to override the quantity with
     * PoolRules.calculateQuantity
     */
    public Pool convertToPrimaryPool(SubscriptionInfo sub) {
        // TODO: Replace this method with a call to the (currently unwritten) EntityResolver.

        if (sub == null) {
            throw new IllegalArgumentException("subscription is null");
        }

        // Resolve the subscription's owner...
        if (sub.getOwner() == null || sub.getOwner().getKey() == null) {
            throw new IllegalStateException("Subscription references an invalid owner: " + sub.getOwner());
        }

        Owner owner = this.ownerCurator.getByKey(sub.getOwner().getKey());
        if (owner == null) {
            throw new IllegalStateException("Subscription references an owner which cannot be resolved: " +
                sub.getOwner());
        }

        // Resolve the subscription's product...
        ProductInfo pinfo = sub.getProduct();
        if (pinfo == null) {
            throw new IllegalStateException("Subscription lacks a product: " + sub);
        }

        String pid = pinfo.getId();
        if (pid == null || pid.isEmpty()) {
            throw new IllegalStateException("Subscription references an incomplete product: " + pinfo);
        }

        // Resolve product ref using the org's namespace
        Product product = this.productCurator.resolveProductId(owner.getKey(), pid);
        if (product == null) {
            throw new IllegalStateException("product reference could not be resolved to a local product: " +
                pid);
        }

        // Do the actual conversion work & return the result
        return this.convertToPrimaryPool(sub, owner, Collections.singletonMap(pid, product));
    }

    /**
     * Builds a pool instance from the given subscription, using the specified owner and products for
     * resolution.
     * <p>
     * The provided owner and products will be used to match and resolve the owner and product DTOs
     * present on the subscription. If the subscription uses DTOs which cannot be resolved, this method
     * will throw an exception.
     *
     * @param sub
     *  The subscription to convert to a pool
     *
     * @param owner
     *  The owner the pool will be assigned to
     *
     * @param productMap
     *  Products for the created pool
     *
     * @return primary pool
     */
    @SuppressWarnings("checkstyle:methodlength")
    public Pool convertToPrimaryPool(SubscriptionInfo sub, Owner owner, Map<String, Product> productMap) {
        if (sub == null) {
            throw new IllegalArgumentException("subscription is null");
        }

        if (owner == null || (owner.getKey() == null && owner.getId() == null)) {
            throw new IllegalArgumentException("owner is null or incomplete");
        }

        if (productMap == null) {
            throw new IllegalArgumentException("productMap is null");
        }

        Pool pool = new Pool();

        // Validate and resolve owner...
        if (sub.getOwner() == null || !owner.getKey().equals(sub.getOwner().getKey())) {
            throw new IllegalStateException("Subscription references an invalid owner: " + sub.getOwner());
        }

        pool.setOwner(owner);
        pool.setQuantity(sub.getQuantity());
        pool.setStartDate(sub.getStartDate());
        pool.setEndDate(sub.getEndDate());
        pool.setContractNumber(sub.getContractNumber());
        pool.setAccountNumber(sub.getAccountNumber());
        pool.setOrderNumber(sub.getOrderNumber());

        // Copy over subscription details
        pool.setSourceSubscription(new SourceSubscription(sub.getId(), PRIMARY_POOL_SUB_KEY));

        // Copy over upstream details
        pool.setUpstreamPoolId(sub.getUpstreamPoolId());
        pool.setUpstreamEntitlementId(sub.getUpstreamEntitlementId());
        pool.setUpstreamConsumerId(sub.getUpstreamConsumerId());

        // Resolve CDN
        if (sub.getCdn() != null) {
            // Impl note: we're attempting to resolve the CDN nicely, but since we used to just
            // copy this as-is, we need to fall back to accepting whatever we had before if that
            // fails.

            CdnInfo cinfo = sub.getCdn();
            Cdn cdn = this.cdnCurator.getByLabel(cinfo.getLabel());

            if (cdn == null) {
                // Create a new CDN instance using the data we received and hope for the best...
                cdn = new Cdn();

                cdn.setLabel(cinfo.getLabel());
                cdn.setName(cinfo.getName());
                cdn.setUrl(cinfo.getUrl());

                // More cert stuff...
                if (cinfo.getCertificate() != null) {
                    CertificateInfo certInfo = cinfo.getCertificate();
                    CdnCertificate cert = new CdnCertificate();

                    cert.setKey(certInfo.getKey());
                    cert.setCert(certInfo.getCertificate());

                    if (certInfo.getSerial() != null) {
                        CertificateSerialInfo serialInfo = certInfo.getSerial();
                        CertificateSerial serial = new CertificateSerial();

                        // Impl note:
                        // We don't set the ID or serial here, as we generate the ID automagically,
                        // and the serial is currently implemented as an alias for the ID.

                        serial.setRevoked(serialInfo.isRevoked());
                        serial.setExpiration(serialInfo.getExpiration());

                        cert.setSerial(serial);
                    }

                    cdn.setCertificate(cert);
                }
            }

            pool.setCdn(cdn);
        }

        // Resolve subscription certificate
        if (sub.getCertificate() != null) {
            // FIXME: This is probably incorrect. We're blindly copying the cert info to new
            // certificate objects, as this was effectively what we were doing before, but it seems
            // a tad dangerous.

            CertificateInfo certInfo = sub.getCertificate();
            SubscriptionsCertificate cert = new SubscriptionsCertificate();

            cert.setKey(certInfo.getKey());
            cert.setCert(certInfo.getCertificate());

            if (certInfo.getSerial() != null) {
                CertificateSerialInfo serialInfo = certInfo.getSerial();
                CertificateSerial serial = new CertificateSerial();

                // Impl note:
                // We don't set the ID or serial here, as we generate the ID automagically, and the
                // serial is currently implemented as an alias for the ID.

                serial.setRevoked(serialInfo.isRevoked());
                serial.setExpiration(serialInfo.getExpiration());

                cert.setSerial(serial);
            }

            pool.setCertificate(cert);
        }

        if (sub.getProduct() == null || sub.getProduct().getId() == null) {
            throw new IllegalStateException("Subscription has no product, or its product is incomplete: " +
                sub.getProduct());
        }

        Product product = productMap.get(sub.getProduct().getId());
        if (product == null) {
            throw new IllegalStateException("Subscription references a product which cannot be resolved: " +
                sub.getProduct());
        }

        pool.setProduct(product);

        return pool;
    }

}
