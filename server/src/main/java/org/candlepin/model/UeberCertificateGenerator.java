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
import org.candlepin.controller.PoolManager;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UniqueIdGenerator;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;



/**
 * UeberCertificateGenerator
 */
public class UeberCertificateGenerator {

    private PoolManager poolManager;
    private PoolCurator poolCurator;
    private ProductCurator productCurator;
    private ContentCurator contentCurator;
    private UniqueIdGenerator idGenerator;
    private SubscriptionServiceAdapter subService;
    private ConsumerTypeCurator consumerTypeCurator;
    private ConsumerCurator consumerCurator;
    private I18n i18n;

    @Inject
    public UeberCertificateGenerator(PoolManager poolManager,
        PoolCurator poolCurator,
        ProductCurator productCurator,
        ContentCurator contentCurator,
        UniqueIdGenerator idGenerator,
        SubscriptionServiceAdapter subService,
        ConsumerTypeCurator consumerTypeCurator,
        ConsumerCurator consumerCurator,
        I18n i18n) {

        this.poolManager = poolManager;
        this.poolCurator = poolCurator;
        this.productCurator = productCurator;
        this.contentCurator = contentCurator;
        this.idGenerator = idGenerator;
        this.subService = subService;
        this.consumerTypeCurator = consumerTypeCurator;
        this.consumerCurator = consumerCurator;
        this.i18n = i18n;
    }

    public EntitlementCertificate generate(Owner o, Principal principal)
        throws EntitlementRefusedException {

        Product ueberProduct = createUeberProduct(o);
        Subscription ueberSubscription = createUeberSubscription(o, ueberProduct);
        poolManager.createPoolsForSubscription(ueberSubscription);
        Consumer consumer = createUeberConsumer(principal, o);

        Pool ueberPool = poolCurator.findUeberPool(o);
        return generateUeberCertificate(consumer, ueberPool);
    }

    public Product createUeberProduct(Owner o) {
        // TODO: These ueber objects are (heavily) reliant on implementation details of the
        // DefaultUniqueIdGenerator returning only numeric IDs, despite the interface and the return
        // value lacking any such guarantee.
        // Specifically, the X509 filtering functionality will only properly filter when these are
        // generated with numeric IDs.

        Product ueberProduct = Product.createUeberProductForOwner(idGenerator, o);
        productCurator.create(ueberProduct);

        Content ueberContent = Content.createUeberContent(idGenerator, o, ueberProduct);
        contentCurator.create(ueberContent);

        ProductContent productContent = new ProductContent(ueberProduct, ueberContent, true);
        ueberProduct.getProductContent().add(productContent);

        return ueberProduct;
    }

    public Subscription createUeberSubscription(Owner o, Product ueberProduct) {
        Date now = now();

        Subscription subscription = new Subscription(o, ueberProduct,
            new HashSet<Product>(), 1L, now, hundredYearsFromNow(), now);

        // We need to fake a subscription ID here so our generated pool's source subscription ends
        // up with a valid ID.
        subscription.setId(idGenerator.generateId());
        subscription.setCreated(now);
        subscription.setUpdated(now);

        // return subService.createSubscription(subscription);
        return subscription;
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

    private Date hundredYearsFromNow() {
        Calendar now = Calendar.getInstance();
        now.add(Calendar.YEAR, 100);
        Date hunderedYearsFromNow = now.getTime();
        return hunderedYearsFromNow;
    }
}
