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
package org.candlepin.model;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;

import org.candlepin.auth.Principal;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UniqueIdGenerator;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * UeberCertificateGenerator
 */
public class UeberCertificateGenerator {

    private PoolManager poolManager;
    private PoolCurator poolCurator;
    private ProductServiceAdapter prodAdapter;
    private ContentCurator contentCurator;
    private UniqueIdGenerator idGenerator;
    private SubscriptionServiceAdapter subService;
    private ConsumerTypeCurator consumerTypeCurator;
    private ConsumerCurator consumerCurator;
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
        I18n i18n) {

        this.poolManager = poolManager;
        this.poolCurator = poolCurator;
        this.prodAdapter = prodAdapter;
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
            new HashSet<Product>(), 1L, now(), hundredYearsFromNow(), now());
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
            throw new CuratorException(i18n.tr("No such consumer type: {0}",
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
