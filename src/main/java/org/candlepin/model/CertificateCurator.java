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
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;



/**
 * CertificateCurator - general certificate database ops
 */
@Singleton
public class CertificateCurator extends AbstractHibernateCurator<Certificate> {
    private static Logger log = LoggerFactory.getLogger(CertificateCurator.class);

    private PKIUtility pki;
    private X509ExtensionUtil extensionUtil;


    @Inject
    public CertificateCurator(PKIUtility pki, X509ExtensionUtil extensionUtil) {
        super(Certificate.class);

        this.pki = Objects.requireNonNull(pki);
        this.extensionUtil = Objects.requireNonNull(extensionUtil);
    }

    public List<Certificate> list() {
        return this.list((Certificate.Type) null);
    }

    public List<Certificate> list(Certificate.Type... types) {
        TypedQuery<Certificate> query;

        if (types == null || types.length == 0) {
            String jpql = "SELECT cert FROM Certificate cert WHERE cert.type IN (:types)";

            query = this.getEntityManager()
                .createQuery(jpql, Certificate.class)
                .setParameter("types", types);
        }
        else {
            String jpql = "SELECT cert FROM Certificate cert";

            query = this.getEntityManager()
                .createQuery(jpql, Certificate.class);
        }

        return query.getResultList();
    }

    public int revokeCertificates(String... certificateIds) {
        return certificateIds != null ? this.revokeCertificates(List.of(certificateIds)) : 0;
    }

    public int revokeCertificates(Collection<String> certificateIds) {
        if (certificateIds == null || certificateIds.isEmpty()) {
            return 0;
        }

        // TODO: Check if it's faster to run a pile of individual updates rather than a handful of
        // single queries with an IN op. Both should be using the index here in some capacity, but
        // what Hibernate does with each query before running it might impact things.
        String jpql = "UPDATE Certificate cert SET cert.revoked = true, cert.updated = CURRENT_TIMESTAMP " +
            "WHERE cert.id IN (:cert_ids)";

        Query query = this.getEntityManager()
            .createQuery(jpql);

        int updated = 0;

        for (Collection<String> block : this.partition(certificateIds)) {
            updated += query.setParameter("cert_ids", block)
                .executeUpdate();
        }

        return updated;
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

        return query.setParameter("cutoff", cutoff)
            .executeUpdate();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Stuff from CertificateSerialCurator
    //////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Certificate getBySerial(BigInteger serial) {
        if (serial == null) {
            return null;
        }

        String jpql = "SELECT cert FROM Certificate cert WHERE cert.serial = :serial";

        List<Certificate> result = this.getEntityManager()
            .createQuery(jpql, Certificate.class)
            .setParameter("serial", serial)
            .getResultList();

        return result != null && !result.isEmpty() ? result.get(0) : null;
    }

    public List<Certificate> getBySerials(Collection<BigInteger> serials) {
        List<Certificate> output = new ArrayList<>();

        if (serials != null && !serials.isEmpty()) {
            String jpql = "SELECT cert FROM Certificate cert WHERE cert.serial IN (:serials)";

            TypedQuery<Certificate> query = this.getEntityManager()
                .createQuery(jpql, Certificate.class);

            for (List<BigInteger> block : this.partition(serials)) {
                List<Certificate> certs = query.setParameter("serials", block)
                    .getResultList();

                output.addAll(certs);
            }
        }

        return output;
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

    //////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Stuff from ProductCertificateCurator
    //////////////////////////////////////////////////////////////////////////////////////////////////////////

    // corresponds to findForProduct -- lookup only; no creation
    public Certificate getProductCertificate(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product is null");
        }

        log.debug("Retreiving certificate for product: {}", product);

        String jpql = "SELECT prod.certificate FROM Product prod WHERE prod.uuid = :product_uuid";

        // Impl note: this should never return more than one result
        try {
            return this.getEntityManager()
                .createQuery(jpql, Certificate.class)
                .setParameter("product_uuid", product.getUuid())
                .getSingleResult();
        }
        catch (javax.persistence.NoResultException e) {
            return null;
        }
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

        // BigInteger serial = BigInteger.valueOf(product.getId().hashCode()).abs();
        BigInteger serial = this.pki.generateCertificateSerial();

        Instant expiration = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.DAYS)
            .plus(25, ChronoUnit.YEARS) // Arbitrary years. We should codify this somehow. Maybe expand the end date generator stuff?
            .toInstant();

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

    //////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Stuff from EntitlementCertificateCurator
    //////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Corresponds to EntitlementCertificateCurator.listForEntitlement
    public List<Certificate> getCertificatesForEntitlement(Entitlement entitlement) {
        // Unused, don't bother porting it.
        throw new UnsupportedOperationException("unused operation, discard it");
    }

    // Corresponds to EntitlementCertificateCurator.listForConsumer
    public List<Certificate> getEntitlementCertificatesForConsumer(Consumer consumer) {
        String jpql = "SELECT cert FROM Entitlement ent " +
            "JOIN ent.certificates cert " +
            "JOIN ent.consumer consumer " +
            "JOIN ent.pool pool " +
            "WHERE pool.endDate > CURRENT_TIMESTAMP" +
            "  AND consumer.id = :consumer_id";

        return this.getEntityManager()
            .createQuery(jpql, Certificate.class)
            .setParameter("consumer_id", consumer.getId())
            .getResultList();
    }

    // Corresponds to EntitlementCertificateCurator.deleteByEntitlementIds
    public int revokeEntitlementCertificates(String... entitlementIds) {
        return entitlementIds != null ? this.revokeEntitlementCertificates(List.of(entitlementIds)) : 0;
    }

    // Corresponds to EntitlementCertificateCurator.deleteByEntitlementIds
    public int revokeEntitlementCertificates(Collection<String> entitlementIds) {
        if (entitlementIds == null || entitlementIds.isEmpty()) {
            return 0;
        }

        // Get IDs of affected certificates
        String jpql = "SELECT cert.id FROM Entitlement ent JOIN ent.certificates cert " +
            "WHERE ent.id IN (:entitlement_ids)";

        Query query = this.getEntityManager()
            .createQuery(jpql, String.class);

        Set<String> certIds = new HashSet<>();
        for (List<String> block : this.partition(entitlementIds)) {
            certIds.addAll(query.setParameter("entitlement_ids", block).getResultList());
        }

        if (!certIds.isEmpty()) {
            // revoke affected certificates
            this.revokeCertificates(certIds);

            // clear certificates from entitlements
            // Impl note: has to be done natively because JPQL doesn't provide a means of
            // doing this within normal means
            String sql = "DELETE FROM cp_entitlement_certificates " +
                "WHERE entitlement_id IN (:entitlement_ids)";

            query = this.getEntityManager()
                .createNativeQuery(sql);

            for (List<String> block : this.partition(entitlementIds)) {
                query.setParameter("entitlement_ids", block)
                    .executeUpdate();
            }
        }

        // return the number of revoked certs
        return certIds.size();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Stuff from UeberCertificateCurator
    //////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Corresponds to UeberCertificate.deleteForOwner
    public boolean revokeUeberCertificate(Owner owner) {
        return owner != null && this.revokeUeberCertificate(owner.getId());
    }

    // Corresponds to UeberCertificate.deleteForOwner
    public boolean revokeUeberCertificate(String ownerId) {
        String jpql = "SELECT cert.id FROM Owner owner JOIN owner.ueberCert cert " +
            "WHERE owner.id = :owner_id";

        List<String> certIds = this.getEntityManager()
            .createQuery(jpql, String.class)
            .setParameter("owner_id", ownerId)
            .getResultList();

        return this.revokeCertificates(certIds) > 0;
    }

    // /**
    //  * Find the ueber certificate for the specified owner. Only one certificate should ever
    //  * exist per owner.
    //  *
    //  * @param owner the target Owner
    //  * @return the ueber certificate for this owner, null if one is not found.
    //  */
    // @SuppressWarnings("unchecked")
    // public UeberCertificate findForOwner(Owner owner) {
    //     String findByOwner = "select uc from UeberCertificate uc where uc.owner = :owner";
    //     Query query = getEntityManager().createQuery(findByOwner);
    //     query.setParameter("owner", owner);
    //     List<UeberCertificate> certs = query.getResultList();
    //     if (certs.isEmpty()) {
    //         return null;
    //     }
    //     return certs.get(0);
    // }

    // /**
    //  * Delete the UeberCertificate related to the specified owner.
    //  *
    //  * @param owner the target owner
    //  * @return true if a certificate was deleted for the Owner, false otherwise.
    //  */
    // public boolean deleteForOwner(Owner owner) {
    //     // NOTE: We require that the @PreRemove callback be made in order to
    //     //       set the revoked state. Doing a direct delete via JPQL does not
    //     //       trigger callbacks, so unfortunately, we need to look up the owner's
    //     //       certificate and then delete it.
    //     UeberCertificate ownerCert = findForOwner(owner);
    //     if (ownerCert == null) {
    //         return false;
    //     }
    //     getEntityManager().remove(ownerCert);
    //     return true;
    // }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Stuff from IdentityCertificateCurator
    //////////////////////////////////////////////////////////////////////////////////////////////////////////

    // /**
    //  * Lists all expired identity certificates that are not revoked.
    //  *  Upstream consumer certificates are not retrieved.
    //  *
    //  * @return a list of expired certificates
    //  */
    // @SuppressWarnings("unchecked")
    // public List<ExpiredCertificate> listAllExpired() {
    //     String hql = "SELECT new org.candlepin.model.ExpiredCertificate(c.id, s.id)" +
    //         " FROM IdentityCertificate c" +
    //         " INNER JOIN c.serial s" +
    //         " INNER JOIN Consumer con on con.idCert = c.id" +
    //         " WHERE s.expiration < :nowDate";

    //     Query query = this.getEntityManager().createQuery(hql, ExpiredCertificate.class);

    //     return (List<ExpiredCertificate>) query
    //         .setParameter("nowDate", new Date())
    //         .getResultList();
    // }

    // /**
    //  * Deletes identity certificates belonging to the given ids
    //  *
    //  * @param idsToDelete ids to be deleted
    //  * @return a number of deleted certificates
    //  */
    // @Transactional
    // public int deleteByIds(Collection<String> idsToDelete) {
    //     if (idsToDelete == null || idsToDelete.isEmpty()) {
    //         return 0;
    //     }

    //     String query = "DELETE FROM IdentityCertificate c WHERE c.id IN (:idsToDelete)";

    //     int deleted = 0;
    //     for (Collection<String> idsToDeleteBlock : this.partition(idsToDelete)) {
    //         deleted += this.currentSession().createQuery(query)
    //             .setParameter("idsToDelete", idsToDeleteBlock)
    //             .executeUpdate();
    //     }

    //     return deleted;
    // }



    //////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Stuff from ContentAccessCertificateCurator
    //////////////////////////////////////////////////////////////////////////////////////////////////////////


    // @Transactional
    // public ContentAccessCertificate getForConsumer(Consumer c) {
    //     log.debug("Retrieving content access certificate for consumer: {}", c.getId());
    //     return (ContentAccessCertificate) currentSession().createCriteria(ContentAccessCertificate.class)
    //         .add(Restrictions.eq("consumer", c))
    //         .uniqueResult();
    // }

    // Corresponds to ContentAccessCertificateCurator.deleteForOwner
    public int revokeOwnerContentAccessCertificates(Owner owner) {
        if (owner == null) {
            return 0;
        }

        EntityManager entityManager = this.getEntityManager();

        String jpql = "SELECT consumer.contentAccessCert.id FROM Consumer consumer " +
            "WHERE consumer.ownerId = :owner_id";

        List<String> certIds = entityManager.createQuery(jpql, String.class)
            .setParameter("owner_id", owner.getId())
            .getResultList();

        int updated = 0;
        if (certIds != null && !certIds.isEmpty()) {
            updated = this.revokeCertificates(certIds);

            // Remove the cert from all affected consumers
            jpql = "UPDATE Consumer consumer SET contentAccessCert = null, updated = CURRENT_TIMESTAMP " +
                "WHERE consumer.ownerId = :owner_id";

            entityManager.createQuery(jpql, Consumer.class)
                .setParameter("owner_id", owner.getId())
                .executeUpdate();
        }

        return updated;
    }



    // /**
    //  * Delete SCA certs of all consumers that belong to the given org.
    //  *
    //  * @return the number of rows deleted.
    //  */
    // @Transactional
    // @SuppressWarnings("unchecked")
    // public int deleteForOwner(Owner owner) {
    //     if (owner == null || owner.getKey() == null || owner.getKey().isEmpty()) {
    //         return 0;
    //     }
    //     // So we must get ids for this owner, and then delete them
    //     String jpql = "SELECT cac.id FROM Consumer consumer, Owner owner " +
    //         "JOIN consumer.contentAccessCert cac


    //     String hql = " SELECT cac.id, s.id " +
    //         "          FROM Consumer c, Owner o" +
    //         "              JOIN c.contentAccessCert cac" +
    //         "              JOIN  cac .serial s" +
    //         "          WHERE o.key=:ownerkey" +
    //         "                and o.id = c.ownerId";
    //     Query query = this.getEntityManager().createQuery(hql);
    //     List<Object[]> rows = query.setParameter("ownerkey", owner.getKey()).getResultList();

    //     return deleteCerts(rows);
    // }

    // private int deleteCerts(List<Object[]> rows) {
    //     Set<String> certsToDelete = new HashSet<>();
    //     Set<Long> certSerialsToRevoke = new HashSet<>();
    //     for (Object[] row : rows) {
    //         if (row[0] != null) {
    //             certsToDelete.add((String) row[0]);
    //         }

    //         if (row[1] != null) {
    //             certSerialsToRevoke.add((Long) row[1]);
    //         }
    //     }

    //     // First ensure that we've marked all of the certificate serials as revoked.
    //     // Normally we would let the @PreRemove on CertificateSertial do this for us
    //     // when the certificate record is deleted, but since there's a potential for
    //     // a lot of certificates to exist for an Owner, we'll batch these updates.
    //     log.debug("Marked {} certificate serials as revoked.", revokeCertificateSerials(certSerialsToRevoke));

    //     int removed = deleteContentAccessCerts(certsToDelete);
    //     log.debug("Deleted {} content access certificates.", removed);
    //     return removed;
    // }

    // /**
    //  * Mark the specified content access certificate serials as revoked.
    //  *
    //  * @param serialIdsToRevoke the ids of the serials to mark as revoked.
    //  * @return the number of serials that were marked as revoked.
    //  */
    // private int revokeCertificateSerials(Set<Long> serialIdsToRevoke) {
    //     String revokeHql = "UPDATE CertificateSerial SET revoked = true WHERE id IN (:serialsToRevoke)";
    //     Query revokeQuery = this.getEntityManager().createQuery(revokeHql);
    //     int revokedCount = 0;
    //     for (List<Long> block : Iterables.partition(serialIdsToRevoke, getInBlockSize())) {
    //         revokedCount += revokeQuery.setParameter("serialsToRevoke", block).executeUpdate();
    //     }
    //     return revokedCount;
    // }

    // private int deleteContentAccessCerts(Set<String> certIdsToDelete) {
    //     String hql = "DELETE from ContentAccessCertificate WHERE id IN (:certsToDelete)";
    //     Query query = this.getEntityManager().createQuery(hql);

    //     String hql2 = "UPDATE Consumer set contentAccessCert = null WHERE " +
    //         "contentAccessCert.id IN (:certsToDelete)";
    //     Query query2 = this.getEntityManager().createQuery(hql2);

    //     int removed = 0;
    //     for (List<String> block : Iterables.partition(certIdsToDelete, getInBlockSize())) {
    //         String param = block.toString();
    //         query2.setParameter("certsToDelete", block).executeUpdate();
    //         removed += query.setParameter("certsToDelete", block).executeUpdate();
    //     }
    //     return removed;
    // }


    // /**
    //  * Lists all expired content access certificates that are not revoked.
    //  *
    //  * @return a list of expired certificates
    //  */
    // @SuppressWarnings("unchecked")
    // public List<ExpiredCertificate> listAllExpired() {
    //     String hql = "SELECT new org.candlepin.model.ExpiredCertificate(c.id, s.id)" +
    //         " FROM ContentAccessCertificate c" +
    //         " INNER JOIN c.serial s " +
    //         " WHERE s.expiration < :nowDate";

    //     Query query = this.getEntityManager().createQuery(hql, ExpiredCertificate.class);

    //     return (List<ExpiredCertificate>) query
    //         .setParameter("nowDate", new Date())
    //         .getResultList();
    // }

    // /**
    //  * Deletes content access certificates with the given ids
    //  *
    //  * @param idsToDelete ids to be deleted
    //  * @return a number of deleted certificates
    //  */
    // @Transactional
    // public int deleteByIds(Collection<String> idsToDelete) {
    //     if (idsToDelete == null || idsToDelete.isEmpty()) {
    //         return 0;
    //     }

    //     String query = "DELETE FROM ContentAccessCertificate c WHERE c.id IN (:idsToDelete)";

    //     int deleted = 0;
    //     for (Collection<String> idsToDeleteBlock : this.partition(idsToDelete)) {
    //         deleted += this.currentSession().createQuery(query)
    //             .setParameter("idsToDelete", idsToDeleteBlock)
    //             .executeUpdate();
    //     }

    //     return deleted;
    // }


}
