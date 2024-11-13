/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
package org.candlepin.pki.certs;

import org.candlepin.controller.util.ContentPathBuilder;
import org.candlepin.controller.util.PromotedContent;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.UeberCertificate;
import org.candlepin.model.UeberCertificateCurator;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.X509Extension;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.util.X509ExtensionUtil;

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;


/**
 * This generator is responsible for generation of ueber certificates.
 */
@Singleton
public class UeberCertificateGenerator {
    private static final String UEBER_CERT_CONSUMER_TYPE = "uebercert";
    private static final String UEBER_CERT_CONSUMER = "ueber_cert_consumer";
    private static final  String UEBER_CONTENT_NAME = "ueber_content";
    private static final String UEBER_PRODUCT_POSTFIX = "_ueber_product";

    private static final Logger log = LoggerFactory.getLogger(UeberCertificateGenerator.class);

    private final UniqueIdGenerator idGenerator;
    private final KeyPairGenerator keyPairGenerator;
    private final CertificateSerialCurator serialCurator;
    private final X509ExtensionUtil extensionUtil;
    private final OwnerCurator ownerCurator;
    private final UeberCertificateCurator ueberCertCurator;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final I18n i18n;
    private final PemEncoder pemEncoder;
    private final Provider<X509CertificateBuilder> certificateBuilder;

    @Inject
    public UeberCertificateGenerator(
        UniqueIdGenerator idGenerator,
        X509ExtensionUtil extensionUtil,
        CertificateSerialCurator serialCurator,
        OwnerCurator ownerCurator,
        UeberCertificateCurator ueberCertCurator,
        ConsumerTypeCurator consumerTypeCurator,
        KeyPairGenerator keyPairGenerator,
        PemEncoder pemEncoder,
        I18n i18n,
        Provider<X509CertificateBuilder> certificateBuilder) {

        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.serialCurator = Objects.requireNonNull(serialCurator);
        this.extensionUtil = Objects.requireNonNull(extensionUtil);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.ueberCertCurator = Objects.requireNonNull(ueberCertCurator);
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.i18n = Objects.requireNonNull(i18n);
        this.keyPairGenerator = Objects.requireNonNull(keyPairGenerator);
        this.pemEncoder = Objects.requireNonNull(pemEncoder);
        this.certificateBuilder = Objects.requireNonNull(certificateBuilder);
    }

    @Transactional
    public UeberCertificate generate(String ownerKey, String username) {
        Owner owner = this.ownerCurator.getByKey(ownerKey);
        if (owner == null) {
            throw new NotFoundException(i18n.tr("Unable to find an owner with key: {0}", ownerKey));
        }

        this.ownerCurator.lock(owner);

        try {
            // There can only be one ueber certificate per owner, so delete the existing and regenerate it.
            this.ueberCertCurator.deleteForOwner(owner);
            return  this.generateUeberCert(owner, username);
        }
        catch (Exception e) {
            log.error("Problem generating ueber cert for owner: {}", ownerKey, e);
            throw new BadRequestException(i18n.tr(
                "Problem generating ueber cert for owner {0}", ownerKey), e);
        }
    }

    private UeberCertificate generateUeberCert(Owner owner, String generatedByUsername) {
        ConsumerType ueberCertType = this.consumerTypeCurator.getByLabel(UEBER_CERT_CONSUMER_TYPE, true);

        UeberCertData ueberCertData = new UeberCertData(owner, generatedByUsername, ueberCertType);

        CertificateSerial serial = new CertificateSerial(ueberCertData.getEndDate());
        serial = serialCurator.create(serial);

        KeyPair keyPair = this.keyPairGenerator.generateKeyPair();
        byte[] pemEncodedKeyPair = this.pemEncoder.encodeAsBytes(keyPair.getPrivate());
        X509Certificate x509Cert =
            createX509Certificate(ueberCertData, BigInteger.valueOf(serial.getId()), keyPair);

        UeberCertificate ueberCert = new UeberCertificate();
        ueberCert.setSerial(serial);
        ueberCert.setKeyAsBytes(pemEncodedKeyPair);
        ueberCert.setOwner(owner);
        ueberCert.setCert(this.pemEncoder.encodeAsString(x509Cert));
        ueberCert.setCreated(ueberCertData.getStartDate());
        ueberCert.setUpdated(ueberCertData.getStartDate());
        ueberCertCurator.create(ueberCert);

        return ueberCert;
    }

    private X509Certificate createX509Certificate(UeberCertData data,
        BigInteger serialNumber, KeyPair keyPair) {
        ContentPathBuilder contentPathBuilder = ContentPathBuilder.from(null, List.of());
        Set<X509Extension> extensions = new LinkedHashSet<>(
            extensionUtil.productExtensions(data.getProduct()));
        extensions.addAll(extensionUtil.contentExtensions(data.getProduct().getProductContent(),
            new PromotedContent(contentPathBuilder), new Consumer(), data.getProduct()));
        extensions.addAll(extensionUtil.subscriptionExtensions(data.getEntitlement().getPool()));
        extensions.addAll(extensionUtil.entitlementExtensions(data.getEntitlement().getQuantity()));
        extensions.addAll(extensionUtil.consumerExtensions(data.getConsumer()));

        if (log.isDebugEnabled()) {
            log.debug("Ueber certificate extensions for Owner: {}", data.getOwner().getKey());
            for (X509Extension eWrapper : extensions) {
                log.debug("Extension {} with value {}", eWrapper.oid(), eWrapper.value());
            }
        }

        DistinguishedName dn = new DistinguishedName(null, data.getOwner());
        return this.certificateBuilder.get()
            .withDN(dn)
            .withSerial(serialNumber)
            .withValidity(data.startDate.toInstant(), data.endDate.toInstant())
            .withKeyPair(keyPair)
            .withExtensions(extensions)
            .build();
    }

    /**
     * Contains the data that will be used to create an ueber certificate for the owner.
     * This class should remain private and is used primarily for allowing a single definition
     * of an ueber product/content.
     *
     */
    private class UeberCertData {
        private final Owner owner;
        private final ConsumerType ueberCertType;
        private final Consumer consumer;
        private final Product product;
        private final Content content;
        private final Pool pool;
        private final Entitlement entitlement;
        private final Date startDate;
        private final Date endDate;

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
            return new Consumer()
                .setName(UEBER_CERT_CONSUMER)
                .setUsername(username)
                .setOwner(owner)
                .setType(this.ueberCertType);
        }

        private Product createUeberProductForOwner(UniqueIdGenerator idGenerator, Owner owner) {
            String ueberProductName = owner.getKey() + UEBER_PRODUCT_POSTFIX;

            return new Product()
                .setId(idGenerator.generateId())
                .setName(ueberProductName)
                .setMultiplier(1L);
        }

        private Content createUeberContent(UniqueIdGenerator idGenerator, Owner owner, Product product) {
            return new Content(idGenerator.generateId())
                .setName(UEBER_CONTENT_NAME)
                .setType("yum")
                .setLabel(product.getId() + "_" + UEBER_CONTENT_NAME)
                .setVendor("Custom")
                .setContentUrl("/" + owner.getKey())
                .setGpgUrl("")
                .setArches("");
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
            // December 1, 2049, at 13:00 UTC
            OffsetDateTime late2049 = OffsetDateTime.of(2049, 12, 1, 13, 0, 0, 0, ZoneOffset.UTC);
            return Date.from(late2049.toInstant());
        }
    }
}
