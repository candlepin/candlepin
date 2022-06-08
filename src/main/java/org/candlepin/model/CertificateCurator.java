/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.X509ExtensionWrapper;
import org.candlepin.util.X509ExtensionUtil;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

import javax.inject.Singleton;
import javax.persistence.Query;



/**
 * CertificateCurator - general certificate database ops
 */
@Singleton
public class CertificateCurator extends AbstractHibernateCurator<Certificate> {
    private static Logger log = LoggerFactory.getLogger(CertificateCuratorclass);

    private PKIUtility pki;
    private X509ExtensionUtil extensionUtil;


    @Inject
    public CertificateCurator(PKIUtility pki, X509ExtensionUtil extensionUtil) {
        super(Certificate.class);

        this.pki = Objects.requireNonNull(pki);
        this.extensionUtil = Objects.requireNonNull(extensionUtil);
    }

    // Stuff from CertificateSerialCurator

    public List<Certificate> list() {
        this.getEntityManager()
            .createQuery("SELECT cert FROM Certificate cert", Certificate.class)
            .getResultList();
    }

    public int deleteExpiredCertificates(Certificate.Type... types) {
        Query query;

        if (types == null || types.length == 0) {
            String jpql = "DELETE FROM Certificate cert WHERE cert.expiration < :cutoff";

            query = this.getEntityManager()
                .createQuery(jpql);
        }
        else {
            String jpql = "DELETE FROM Certificate cert " +
                "WHERE cert.expiration < :cutoff " +
                "  AND cert.type IN (:types)";

            query = this.getEntityManager()
                .createQuery(jpql)
                .setParameter("types", types);
        }

        Instant cutoff = Instant.now()
            .truncatedTo(ChronoUnit.DAYS);

        return query.setParameter("cutoff", cutoff)
            .executeUpdate();
    }

    public int deleteRevokedExpiredCertificates(Certificate.Type... types) {
        Query query;

        if (types == null || types.length == 0) {
            String jpql = "DELETE FROM Certificate cert " +
                "WHERE cert.revoked = true " +
                "  AND cert.expiration < :cutoff";

            query = this.getEntityManager()
                .createQuery(jpql);
        }
        else {
            String jpql = "DELETE FROM Certificate cert " +
                "WHERE cert.revoked = true " +
                "  AND cert.expiration < :cutoff " +
                "  AND cert.type IN (:types)";

            query = this.getEntityManager()
                .createQuery(jpql)
                .setParameter("types", types);
        }

        Instant cutoff = Instant.now()
            .truncatedTo(ChronoUnit.DAYS);

        Query query = this.getEntityManager()
            .createQuery(jpql)
            .setParameter("cutoff", cutoff)
            .executeUpdate();
    }

    public List<BigInteger> listEntitlementSerials(Consumer consumer) {
        String jpql = "SELECT cert.serial FROM Entitlement ent " +
            "JOIN ent.certificates cert " +
            "JOIN ent.pool pool " +
            "WHERE ent.consumer.id = :consumer_id " +
            "  AND pool.endDate >= CURRENT_TIMESTAMP";

        return this.getEntityManager()
            .createQuery(jpql, BigInteger.class)
            .setParameter("consumer_id", consumer.getId())
            .getResultList();
    }

    public List<BigInteger> getNonExpiredRevokedSerials() {
        String jpql = "SELECT cert.serial FROM Certificate cert " +
            "WHERE cert.revoked = true " +
            "  AND cert.expiration >= CURRENT_TIMESTAMP";

        return this.getEntityManager()
            .createQuery(jpql, BigInteger.class)
            .getResultList();
    }

    public int revokeBySerials(BigInteger... serials) {
        return this.revokeBySerials(List.of(serials));
    }

    public int revokeBySerials(Collection<BigInteger> serials) {
        String jpql = "UPDATE Certificate cert " +
            "SET cert.revoked = true, " +
            "  cert.updated = CURRENT_TIMESTAMP " +
            "WHERE cert.revoked = false " +
            "  AND cert.serial IN (:serials)";

        Query query = this.getEntityManager()
            .createQuery(jpql);

        int updated = 0;

        for (Collection<BigInteger> block : this.partition(serials)) {
            updated += query.setParameter("serials", block)
                .executeUpdate();
        }

        return updated;
    }



    // Stuff from ProductCertificateCurator

    // corresponds to findForProduct -- lookup only; no creation
    public Certificate getProductCertificate(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product is null");
        }

        log.debug("Retreiving certificate for product: {}", product);

        String jpql = "SELECT prod.certificate FROM Product prod WHERE prod.uuid = :product_uuid";

        // Impl note: this should never return more than one result
        return this.getEntityManager()
            .createQuery(jpql)
            .setParameter("product_uuid", productUuid)
            .getSingleResult();
    }

    // corresponds to getCertForProduct -- fetch w/create
    public Certificate getProductCertificate(Product product, boolean create) {
        if (product == null) {
            throw new IllegalArgumentException("product is null");
        }

        Certificate cert = this.getProductCertificate(product);
        if (create && cert == null) {
            try {
                cert = this.buildProductCertificate(product);
                this.create(cert);
            }
            catch (IllegalArgumentException e) {
                // This occurs if the product is not an engineering product, fails the cert
                // encoding (as marketing products have non-numeric IDs), and fails out with
                // an IAE.
                log.warn("Attempted to create a product certificate for a non-engineering product: {}",
                    product, e);
            }
            catch (Exception e) {
                log.error("Error creating product certificate:", e);
                throw new RuntimeException("Unable to generate product certificate", e);
            }
        }

        return cert;
    }

    private Certificate buildProductCertificate(Product product)
        throws GeneralSecurityException, IOException {

        log.debug("Generating new certificate for product: {}", product);

        KeyPair keyPair = this.pki.generateKeyPair();
        Set<X509ExtensionWrapper> extensions = this.extensionUtil.productExtensions(product);

        BigInteger serial = BigInteger.valueOf(product.getId().hashCode()).abs();

        Instant expiration = Instant.now()
            .truncatedTo(ChronoUnit.DAYS)
            .plus(25, ChronoUnit.YEARS); // Arbitrary years. We should codify this somehow. Maybe expand the end date generator stuff?

        X509Certificate x509Cert = this.pki.createX509Certificate("CN=" + product.getId(), extensions, null,
            new Date(), Date.from(expiration), keyPair, serial, null);

        Certificate cert = new Certificate()
            .setType(Certificate.Type.PRODUCT)
            .setSerial(x509Cert.getSerialNumber())
            .setCertificate(this.pki.getPemEncoded(x509Cert))
            .setPrivateKey(this.pki.getPemEncoded(keyPair.getPrivate()))
            .setExpiration(x509Cert.getNotAfter().toInstant());

        return cert;
    }
}
