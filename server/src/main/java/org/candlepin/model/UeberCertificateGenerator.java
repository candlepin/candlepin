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
package org.candlepin.model;

import org.candlepin.auth.Principal;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UniqueIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.xnap.commons.i18n.I18n;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;

/**
 * UeberCertificateGenerator
 */
public class UeberCertificateGenerator {

    private static Logger log = LoggerFactory.getLogger(UeberCertificateGenerator.class);

    private PoolManager poolManager;
    private PoolCurator poolCurator;
    private ProductServiceAdapter prodAdapter;
    private ContentCurator contentCurator;
    private UniqueIdGenerator idGenerator;
    private SubscriptionServiceAdapter subService;
    private ConsumerTypeCurator consumerTypeCurator;
    private ConsumerCurator consumerCurator;
    private EntitlementCurator entitlementCurator;
    private EntitlementCertificateCurator entitlementCertCurator;
    private OwnerCurator ownerCurator;
    private I18n i18n;

    @Inject
    public UeberCertificateGenerator(PoolManager poolManager,
        PoolCurator poolCurator,
        ProductServiceAdapter prodAdapter,
        ContentCurator contentCurator,
        UniqueIdGenerator idGenerator,
        SubscriptionServiceAdapter subService,
        ConsumerTypeCurator consumerTypeCurator,
        ConsumerCurator consumerCurator,
        EntitlementCurator entitlementCurator,
        EntitlementCertificateCurator entitlementCertCurator,
        OwnerCurator ownerCurator,
        I18n i18n) {

        this.poolManager = poolManager;
        this.poolCurator = poolCurator;
        this.prodAdapter = prodAdapter;
        this.contentCurator = contentCurator;
        this.idGenerator = idGenerator;
        this.subService = subService;
        this.consumerTypeCurator = consumerTypeCurator;
        this.consumerCurator = consumerCurator;
        this.entitlementCurator = entitlementCurator;
        this.entitlementCertCurator = entitlementCertCurator;
        this.ownerCurator = ownerCurator;
        this.i18n = i18n;
    }

    @Transactional
    public EntitlementCertificate generate(String ownerKey, Principal principal) {
        Owner o = ownerCurator.findAndLock(ownerKey);
        if (o == null) {
            throw new NotFoundException(i18n.tr("owner with key: {0} was not found.", ownerKey));
        }

        try {
            Consumer ueberConsumer = consumerCurator.findByName(o, Consumer.UEBER_CERT_CONSUMER);

            // ueber cert has already been generated - re-generate it now
            if (ueberConsumer != null) {
                List<Entitlement> ueberEntitlements = entitlementCurator.listByConsumer(ueberConsumer);

                if (ueberEntitlements.size() > 0) {
                    // Immediately revoke and regenerate ueber certificates:
                    poolManager.regenerateCertificatesOf(ueberEntitlements.get(0), true, false);
                    return entitlementCertCurator.listForConsumer(ueberConsumer).get(0);
                }

                // If there is a consumer without an entitlement, generate a new one. If an ueber pool
                // does not exist, create it.
                Pool ueberPool = poolCurator.findUeberPool(o);
                if (ueberPool == null) {
                    createSubscriptionAndPool(o);
                    ueberPool = poolCurator.findUeberPool(o);
                }
                return generateUeberCertificate(ueberConsumer, ueberPool);
            }

            return this.generateCertificate(o, principal);
        }
        catch (Exception e) {
            log.error("Problem generating ueber cert for owner: " + ownerKey, e);
            throw new BadRequestException(i18n.tr("Problem generating ueber cert for owner {0}", ownerKey),
                e);
        }
    }

    private EntitlementCertificate generateCertificate(Owner o, Principal principal)
        throws EntitlementRefusedException {
        createSubscriptionAndPool(o);
        Consumer consumer = createUeberConsumer(principal, o);

        Pool ueberPool = poolCurator.findUeberPool(o);
        return generateUeberCertificate(consumer, ueberPool);

    }

    private void createSubscriptionAndPool(Owner o) {
        Product ueberProduct = createUeberProduct(o);
        Subscription ueberSubscription = createUeberSubscription(o, ueberProduct);
        poolManager.createPoolsForSubscription(ueberSubscription);
    }

    public Product createUeberProduct(Owner o) {
        Product ueberProduct =
            prodAdapter.createProduct(Product.createUeberProductForOwner(o));
        Content ueberContent =
            contentCurator.create(Content.createUeberContent(idGenerator, o, ueberProduct));

        ProductContent productContent =
            new ProductContent(ueberProduct, ueberContent, true);
        ueberProduct.getProductContent().add(productContent);

        return ueberProduct;
    }

    public Subscription createUeberSubscription(Owner o, Product ueberProduct) {
        Subscription subscription = new Subscription(o, ueberProduct,
            new HashSet<Product>(), 1L, now(), lateIn2049(), now());
        return subService.createSubscription(subscription);
    }

    public Consumer createUeberConsumer(Principal principal, Owner o) {
        ConsumerType type = lookupConsumerType(ConsumerTypeEnum.UEBER_CERT.toString());
        Consumer consumer = consumerCurator.create(new Consumer(
            Consumer.UEBER_CERT_CONSUMER,
            principal.getUsername(),
            o,
            type));
        return consumer;
    }

    public EntitlementCertificate generateUeberCertificate(Consumer consumer,
        Pool ueberPool) throws EntitlementRefusedException {
        Entitlement e = poolManager.ueberCertEntitlement(consumer, ueberPool, 1);
        return (EntitlementCertificate) e.getCertificates().toArray()[0];
    }

    private ConsumerType lookupConsumerType(String label) {
        ConsumerType type = consumerTypeCurator.lookupByLabel(label);

        if (type == null) {
            throw new CuratorException(i18n.tr("No such unit type: {0}",
                label));
        }
        return type;
    }

    private Date now() {
        Calendar now = Calendar.getInstance();
        Date currentTime = now.getTime();
        return currentTime;
    }

    /*
     * RFC 5280 states in section 4.1.2.5:
     *
     *     CAs conforming to this profile MUST always encode certificate
     *     validity dates through the year 2049 as UTCTime; certificate validity
     *     dates in 2050 or later MUST be encoded as GeneralizedTime.
     *     Conforming applications MUST be able to process validity dates that
     *     are encoded in either UTCTime or GeneralizedTime.
     *
     * But currently, python-rhsm is parsing certificates with either M2Crypto
     * or a custom C binding (certificate.c) to OpenSSL's x509v3 (so that we can access
     * the raw octets in the custom X509 extensions used in version 3 entitlement
     * certificates).  Both M2Crypto and our binding contain code that automatically
     * converts the Not After time into a UTCTime.  The conversion "succeeds" but the
     * value of the resultant object is something like "Bad time value" which, when
     * fed into Python's datetime, causes an exception.
     *
     * The quick fix is to not issue certificates that expire after January 1, 2050.
     *
     * See https://bugzilla.redhat.com/show_bug.cgi?id=1242310
     */
    private Date lateIn2049() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        // December 1, 2049 at 13:00 GMT
        cal.set(1900 + 149, Calendar.DECEMBER, 1, 13, 0, 0);
        Date late2049 = cal.getTime();
        return late2049;
    }
}
