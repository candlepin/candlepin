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
package org.candlepin.service.impl;

import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Content;
import org.candlepin.model.ContentAccessCertificate;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.KeyPairCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerEnvContentAccess;
import org.candlepin.model.OwnerEnvContentAccessCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509ExtensionWrapper;
import org.candlepin.service.ContentAccessCertServiceAdapter;
import org.candlepin.util.OIDUtil;
import org.candlepin.util.Util;
import org.candlepin.util.X509V3ExtensionUtil;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DefaultEntitlementCertServiceAdapter
 */
public class DefaultContentAccessCertServiceAdapter implements ContentAccessCertServiceAdapter {
    private static Logger log = LoggerFactory.getLogger(DefaultContentAccessCertServiceAdapter.class);

    private PKIUtility pki;
    private KeyPairCurator keyPairCurator;
    private CertificateSerialCurator serialCurator;
    private OwnerContentCurator ownerContentCurator;
    private OwnerCurator ownerCurator;
    private ContentAccessCertificateCurator contentAccessCertificateCurator;
    private X509V3ExtensionUtil v3extensionUtil;
    private OwnerEnvContentAccessCurator ownerEnvContentAccessCurator;
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private EnvironmentCurator environmentCurator;


    @Inject
    public DefaultContentAccessCertServiceAdapter(PKIUtility pki,
        X509V3ExtensionUtil v3extensionUtil,
        ContentAccessCertificateCurator contentAccessCertificateCurator,
        KeyPairCurator keyPairCurator,
        CertificateSerialCurator serialCurator,
        OwnerContentCurator ownerContentCurator,
        OwnerCurator ownerCurator,
        OwnerEnvContentAccessCurator ownerEnvContentAccessCurator,
        ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        EnvironmentCurator environmentCurator) {

        this.pki = pki;
        this.contentAccessCertificateCurator = contentAccessCertificateCurator;
        this.keyPairCurator = keyPairCurator;
        this.serialCurator = serialCurator;
        this.v3extensionUtil = v3extensionUtil;
        this.ownerContentCurator = ownerContentCurator;
        this.ownerCurator = ownerCurator;
        this.ownerEnvContentAccessCurator = ownerEnvContentAccessCurator;
        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.environmentCurator = environmentCurator;
    }

    @Transactional
    public ContentAccessCertificate getCertificate(Consumer consumer)
        throws GeneralSecurityException, IOException {

        Owner owner = ownerCurator.findOwnerById(consumer.getOwnerId());
        // we only know about one mode right now. If add any, we will need to add the
        // appropriate cert generation
        if (!ORG_ENV_ACCESS_MODE.equals(owner.getContentAccessMode()) ||
            !this.consumerIsCertV3Capable(consumer)) {

            return null;
        }

        ContentAccessCertificate existing = consumer.getContentAccessCert();
        ContentAccessCertificate result = new ContentAccessCertificate();
        String pem = "";

        if (existing != null &&
            existing.getSerial().getExpiration().getTime() < (new Date()).getTime()) {
            consumer.setContentAccessCert(null);
            contentAccessCertificateCurator.delete(existing);
            existing = null;
        }

        if (existing == null) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, -1);
            Date startDate = cal.getTime();
            cal.add(Calendar.YEAR, 1);
            Date endDate = cal.getTime();

            CertificateSerial serial = new CertificateSerial(endDate);
            // We need the sequence generated id before we create the Certificate,
            // otherwise we could have used cascading create
            serialCurator.create(serial);

            KeyPair keyPair = keyPairCurator.getConsumerKeyPair(consumer);
            byte[] pemEncodedKeyPair = pki.getPemEncoded(keyPair.getPrivate());

            X509Certificate x509Cert = createX509Certificate(consumer, owner,
                BigInteger.valueOf(serial.getId()), keyPair, startDate, endDate);

            existing = new ContentAccessCertificate();
            existing.setSerial(serial);
            existing.setKeyAsBytes(pemEncodedKeyPair);
            existing.setConsumer(consumer);

            log.info("Setting PEM encoded cert.");
            pem = new String(this.pki.getPemEncoded(x509Cert));
            existing.setCert(pem);
            consumer.setContentAccessCert(existing);
            contentAccessCertificateCurator.create(existing);
            consumerCurator.merge(consumer);
        }
        else {
            pem = existing.getCert();
        }

        Environment env = this.environmentCurator.getConsumerEnvironment(consumer);
        // we need to see if this is newer than the previous result
        OwnerEnvContentAccess oeca = ownerEnvContentAccessCurator.getContentAccess(owner.getId(),
            env == null ? null : env.getId());
        if (oeca == null) {
            String contentJson = createPayloadAndSignature(owner, env);
            oeca = new OwnerEnvContentAccess(owner, env, contentJson);
            ownerEnvContentAccessCurator.saveOrUpdate(oeca);
        }
        pem += oeca.getContentJson();

        result.setCert(pem);
        result.setCreated(existing.getCreated());
        result.setUpdated(existing.getUpdated());
        result.setId(existing.getId());
        result.setConsumer(existing.getConsumer());
        result.setKey(existing.getKey());
        result.setSerial(existing.getSerial());
        return result;
    }

    public boolean hasCertChangedSince(Consumer consumer, Date date) {
        if (date == null) {
            return true;
        }

        Environment env = this.environmentCurator.getConsumerEnvironment(consumer);
        OwnerEnvContentAccess oeca = ownerEnvContentAccessCurator.getContentAccess(
            consumer.getOwnerId(), env == null ? null : env.getId());
        return oeca == null || consumer.getContentAccessCert() == null ||
            oeca.getUpdated().getTime() > date.getTime();
    }

    public String createPayloadAndSignature(Owner owner, Environment environment)
        throws IOException {

        byte[] payloadBytes = createContentAccessDataPayload(owner, environment);

        String payload = "-----BEGIN ENTITLEMENT DATA-----\n";
        payload += Util.toBase64(payloadBytes);
        payload += "-----END ENTITLEMENT DATA-----\n";

        byte[] bytes = pki.getSHA256WithRSAHash(new ByteArrayInputStream(payloadBytes));
        String signature = "-----BEGIN RSA SIGNATURE-----\n";
        signature += Util.toBase64(bytes);
        signature += "-----END RSA SIGNATURE-----\n";
        return payload + signature;
    }

    public X509Certificate createX509Certificate(Consumer consumer, Owner owner, BigInteger serialNumber,
        KeyPair keyPair, Date startDate, Date endDate)
        throws GeneralSecurityException, IOException {

        // fake a product dto as a container for the org content
        org.candlepin.model.dto.Product container = new org.candlepin.model.dto.Product();
        org.candlepin.model.dto.Content dContent = new org.candlepin.model.dto.Content();
        List<org.candlepin.model.dto.Content> dtoContents = new ArrayList<>();
        dtoContents.add(dContent);

        Environment environment = this.environmentCurator.getConsumerEnvironment(consumer);
        dContent.setPath(getContentPrefix(owner, environment));

        container.setContent(dtoContents);

        Set<X509ExtensionWrapper> extensions = prepareV3Extensions();
        Set<X509ByteExtensionWrapper> byteExtensions = prepareV3ByteExtensions(container);

        X509Certificate x509Cert =  this.pki.createX509Certificate(
            createDN(consumer, owner), extensions, byteExtensions, startDate,
            endDate, keyPair, serialNumber, null);

        return x509Cert;
    }

    /**
     * Checks if the specified consumer is capable of using v3 certificates
     *
     * @param consumer
     *  The consumer to check
     *
     * @return
     *  true if the consumer is capable of using v3 certificates; false otherwise
     */
    private boolean consumerIsCertV3Capable(Consumer consumer) {
        if (consumer == null || consumer.getTypeId() == null) {
            throw new IllegalArgumentException("consumer is null or lacks a consumer type");
        }

        ConsumerType type = this.consumerTypeCurator.getConsumerType(consumer);

        if (type.isManifest()) {
            for (ConsumerCapability capability : consumer.getCapabilities()) {
                if ("cert_v3".equals(capability.getName())) {
                    return true;
                }
            }

            return false;
        }
        else if (type.isType(ConsumerTypeEnum.HYPERVISOR)) {
            // Hypervisors in this context don't use content, so V3 is allowed
            return true;
        }

        // Consumer isn't a special type, check their certificate_version fact
        String entitlementVersion = consumer.getFact("system.certificate_version");
        return entitlementVersion != null && entitlementVersion.startsWith("3.");
    }

    private Map<String, EnvironmentContent> getPromotedContent(Environment environment) {
        // Build a set of all content IDs promoted to the consumer's environment so
        // we can determine if anything needs to be skipped:
        Map<String, EnvironmentContent> promotedContent = new HashMap<>();
        if (environment != null) {
            log.debug("Consumer has an environment, checking for promoted content in: {}", environment);

            for (EnvironmentContent envContent : environment.getEnvironmentContent()) {
                log.debug("  promoted content: {}", envContent.getContent().getId());
                promotedContent.put(envContent.getContent().getId(), envContent);
            }
        }

        return promotedContent;
    }

    private String getContentPrefix(Owner owner, Environment environment) throws IOException {
        StringBuffer contentPrefix = new StringBuffer();
        contentPrefix.append("/");
        contentPrefix.append(owner.getKey());
        if (environment != null) {
            contentPrefix.append("/");
            contentPrefix.append(environment.getName());
        }
        return contentPrefix.toString();
    }

    private String createDN(Consumer consumer, Owner owner) {
        StringBuilder sb = new StringBuilder("CN=");
        sb.append(consumer.getUuid());
        sb.append(", O=");
        sb.append(owner.getKey());

        if (consumer.getEnvironmentId() != null) {
            Environment environment = this.environmentCurator.getConsumerEnvironment(consumer);
            sb.append(", OU=");
            sb.append(environment.getName());
        }

        return sb.toString();
    }

    public Set<X509ExtensionWrapper> prepareV3Extensions() {
        Set<X509ExtensionWrapper> result = v3extensionUtil.getExtensions();
        X509ExtensionWrapper typeExtension = new X509ExtensionWrapper(OIDUtil.REDHAT_OID + "." +
            OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ENTITLEMENT_TYPE_KEY), false, "OrgLevel");

        result.add(typeExtension);
        return result;
    }

    public Set<X509ByteExtensionWrapper> prepareV3ByteExtensions(org.candlepin.model.dto.Product container)
        throws IOException {
        List<org.candlepin.model.dto.Product> products = new ArrayList<>();
        products.add(container);
        Set<X509ByteExtensionWrapper> result = v3extensionUtil.getByteExtensions(null, products,
            null,  null);
        return result;
    }

    private byte[] createContentAccessDataPayload(Owner owner, Environment environment) throws IOException {
        // fake a product dto as a container for the org content
        Set<Product> containerSet = new HashSet<>();
        CandlepinQuery<Content> ownerContent = ownerContentCurator.getContentByOwner(owner);
        Set<String> entitledProductIds = new HashSet<>();
        List<org.candlepin.model.dto.Product> productModels = new ArrayList<>();
        Map<String, EnvironmentContent> promotedContent = getPromotedContent(environment);
        String contentPrefix = getContentPrefix(owner, environment);
        Product container = new Product();
        Entitlement emptyEnt = new Entitlement();
        Pool emptyPool = new Pool();
        Product skuProduct = new Product();
        Consumer emptyConsumer = new Consumer();

        containerSet.add(container);
        container.setId("content_access");
        container.setName(" Content Access");
        for (Content c : ownerContent) {
            container.addContent(c, false);
        }

        emptyConsumer.setEnvironment(environment);
        emptyEnt.setPool(emptyPool);
        emptyEnt.setConsumer(emptyConsumer);
        emptyPool.setProduct(skuProduct);
        emptyPool.setStartDate(new Date());
        emptyPool.setEndDate(new Date());
        skuProduct.setName("Content Access");
        skuProduct.setId("content_access");
        entitledProductIds.add("content-access");

        org.candlepin.model.dto.Product productModel = v3extensionUtil.mapProduct(container, skuProduct,
            contentPrefix, promotedContent, emptyConsumer, emptyPool, entitledProductIds);

        productModels.add(productModel);

        return v3extensionUtil.createEntitlementDataPayload(productModels,
            emptyConsumer, emptyPool, null);
    }
}
