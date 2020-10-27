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
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509ExtensionWrapper;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.util.X509ExtensionUtil;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TimeZone;



/**
 * UeberCertificateGenerator
 */
public class UeberCertificateGenerator {
    private static final String UEBER_CERT_CONSUMER_TYPE = "uebercert";
    private static final String UEBER_CERT_CONSUMER = "ueber_cert_consumer";
    private static final  String UEBER_CONTENT_NAME = "ueber_content";
    private static final String UEBER_PRODUCT_POSTFIX = "_ueber_product";

    private static Logger log = LoggerFactory.getLogger(UeberCertificateGenerator.class);

    private UniqueIdGenerator idGenerator;
    private PKIUtility pki;
    private KeyPairCurator keyPairCurator;
    private CertificateSerialCurator serialCurator;
    private X509ExtensionUtil extensionUtil;
    private OwnerCurator ownerCurator;
    private UeberCertificateCurator ueberCertCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private I18n i18n;

    @Inject
    public UeberCertificateGenerator(
        UniqueIdGenerator idGenerator,
        PKIUtility pki,
        X509ExtensionUtil extensionUtil,
        KeyPairCurator keyPairCurator,
        CertificateSerialCurator serialCurator,
        OwnerCurator ownerCurator,
        UeberCertificateCurator ueberCertCurator,
        ConsumerTypeCurator consumerTypeCurator,
        I18n i18n) {

        this.idGenerator = idGenerator;
        this.pki = pki;
        this.keyPairCurator = keyPairCurator;
        this.serialCurator = serialCurator;
        this.extensionUtil = extensionUtil;
        this.ownerCurator = ownerCurator;
        this.ueberCertCurator = ueberCertCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.i18n = i18n;
    }

    @Transactional
    public UeberCertificate generate(String ownerKey, Principal principal) {
        Owner owner = this.ownerCurator.getByKey(ownerKey);
        if (owner == null) {
            throw new NotFoundException(i18n.tr("Unable to find an owner with key: {0}", ownerKey));
        }

        this.ownerCurator.lock(owner);

        try {
            // There can only be one ueber certificate per owner, so delete the existing and regenerate it.
            this.ueberCertCurator.deleteForOwner(owner);
            return  this.generateUeberCert(owner, principal.getUsername());
        }
        catch (Exception e) {
            log.error("Problem generating ueber cert for owner: {}", ownerKey, e);
            throw new BadRequestException(i18n.tr("Problem generating ueber cert for owner {0}", ownerKey),
                e);
        }
    }

    private UeberCertificate generateUeberCert(Owner owner, String generatedByUsername) throws Exception {
        ConsumerType ueberCertType = this.consumerTypeCurator.getByLabel(UEBER_CERT_CONSUMER_TYPE, true);

        UeberCertData ueberCertData = new UeberCertData(owner, generatedByUsername, ueberCertType);

        CertificateSerial serial = new CertificateSerial(ueberCertData.getEndDate());
        serialCurator.create(serial);

        KeyPair keyPair = keyPairCurator.getKeyPair();
        byte[] pemEncodedKeyPair = pki.getPemEncoded(keyPair.getPrivate());
        X509Certificate x509Cert =
            createX509Certificate(ueberCertData, BigInteger.valueOf(serial.getId()), keyPair);

        UeberCertificate ueberCert = new UeberCertificate();
        ueberCert.setSerial(serial);
        ueberCert.setKeyAsBytes(pemEncodedKeyPair);
        ueberCert.setOwner(owner);
        ueberCert.setCert(new String(this.pki.getPemEncoded(x509Cert)));
        ueberCert.setCreated(ueberCertData.getStartDate());
        ueberCert.setUpdated(ueberCertData.getStartDate());
        ueberCertCurator.create(ueberCert);

        return ueberCert;
    }

    private X509Certificate createX509Certificate(UeberCertData data, BigInteger serialNumber,
        KeyPair keyPair) throws GeneralSecurityException, IOException {
        Set<X509ByteExtensionWrapper> byteExtensions = new LinkedHashSet<>();
        Set<X509ExtensionWrapper> extensions = new LinkedHashSet<>();
        extensions.addAll(extensionUtil.productExtensions(data.getProduct()));
        extensions.addAll(extensionUtil.contentExtensions(data.getProduct().getProductContent(), null,
            new HashMap<>(), new Consumer(), data.getProduct()));
        extensions.addAll(extensionUtil.subscriptionExtensions(data.getEntitlement().getPool()));
        extensions.addAll(extensionUtil.entitlementExtensions(data.getEntitlement().getQuantity()));
        extensions.addAll(extensionUtil.consumerExtensions(data.getConsumer()));

        if (log.isDebugEnabled()) {
            log.debug("Ueber certificate extensions for Owner: {}", data.getOwner().getKey());
            for (X509ExtensionWrapper eWrapper : extensions) {
                log.debug("Extension {} with value {}", eWrapper.getOid(), eWrapper.getValue());
            }
        }

        String dn = "O=" + data.getOwner().getKey();
        return this.pki.createX509Certificate(dn, extensions, byteExtensions,  data.getStartDate(),
            data.getEndDate(), keyPair, serialNumber, null);
    }

    /**
     * Contains the data that will be used to create an ueber certificate for the owner.
     * This class should remain private and is used primarily for allowing a single definition
     * of an ueber product/content.
     *
     */
    private class UeberCertData {
        private Owner owner;
        private ConsumerType ueberCertType;
        private Consumer consumer;
        private Product product;
        private Content content;
        private Pool pool;
        private Entitlement entitlement;

        private Date startDate;
        private Date endDate;

        public UeberCertData(Owner owner, String generatedByUsername, ConsumerType ueberCertType) {
            startDate = Calendar.getInstance().getTime();
            endDate = lateIn2049();

            this.owner = owner;
            this.ueberCertType = ueberCertType;
            this.consumer = createUeberConsumer(generatedByUsername, owner);
            this.product = createUeberProductForOwner(idGenerator, owner);
            this.content = createUeberContent(idGenerator, owner, product);
            this.product.addContent(this.content, true);

            this.pool = new Pool()
                .setOwner(this.owner)
                .setProduct(this.product)
                .setQuantity(1L)
                .setStartDate(this.startDate)
                .setEndDate(this.endDate);

            this.entitlement = new Entitlement(this.pool, this.consumer, owner, 1);
        }

        public Product getProduct() {
            return product;
        }

        public Owner getOwner() {
            return owner;
        }

        public Date getStartDate() {
            return startDate;
        }

        public Date getEndDate() {
            return endDate;
        }

        public Content getContent() {
            return content;
        }

        public Consumer getConsumer() {
            return consumer;
        }

        public Entitlement getEntitlement() {
            return entitlement;
        }

        private Consumer createUeberConsumer(String username, Owner owner) {
            Consumer consumer = new Consumer(UEBER_CERT_CONSUMER, username, owner, this.ueberCertType);
            return consumer;
        }

        private Product createUeberProductForOwner(UniqueIdGenerator idGenerator, Owner owner) {
            String ueberProductName = owner.getKey() + UEBER_PRODUCT_POSTFIX;
            return new Product(idGenerator.generateId(), ueberProductName, 1L);
        }

        private Content createUeberContent(UniqueIdGenerator idGenerator, Owner owner, Product product) {
            Content ueberContent = new Content(idGenerator.generateId());
            ueberContent.setName(UEBER_CONTENT_NAME);
            ueberContent.setType("yum");
            ueberContent.setLabel(product.getId() + "_" + UEBER_CONTENT_NAME);
            ueberContent.setVendor("Custom");
            ueberContent.setContentUrl("/" + owner.getKey());
            ueberContent.setGpgUrl("");
            ueberContent.setArches("");

            return ueberContent;
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
}
