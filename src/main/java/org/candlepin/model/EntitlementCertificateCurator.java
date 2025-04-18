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
package org.candlepin.model;

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Query;



/**
 * EntitlementCertificateCurator
 */
@Singleton
public class EntitlementCertificateCurator extends AbstractHibernateCurator<EntitlementCertificate> {
    private static final Logger log = LoggerFactory.getLogger(EntitlementCertificateCurator.class);

    @Inject
    public EntitlementCertificateCurator() {
        super(EntitlementCertificate.class);
    }

    public List<EntitlementCertificate> listForEntitlement(Entitlement entitlement) {
        if (entitlement == null) {
            return new ArrayList<>();
        }

        String query = "SELECT ec FROM EntitlementCertificate ec WHERE ec.entitlement.id = :id";

        return this.getEntityManager()
            .createQuery(query, EntitlementCertificate.class)
            .setParameter("id", entitlement.getId())
            .getResultList();
    }

    public List<EntitlementCertificate> listForConsumer(Consumer consumer) {
        if (consumer == null) {
            return new ArrayList<>();
        }

        String query = "SELECT ec FROM EntitlementCertificate ec " +
            "WHERE ec.entitlement.consumer.id = :consumer_id AND ec.entitlement.pool.endDate >= :now";

        return this.getEntityManager()
            .createQuery(query, EntitlementCertificate.class)
            .setParameter("consumer_id", consumer.getId())
            .setParameter("now", new Date())
            .getResultList();
    }

    @Transactional
    public void delete(EntitlementCertificate cert) {
        // make sure to delete it! else get ready to face
        // javax.persistence.EntityNotFoundException('deleted entity passed to persist')
        cert.getEntitlement().removeCertificate(cert);
        super.delete(cert);
    }

    /**
     * Deletes any existing entitlement certificates for the specified entitlements.
     *
     * @param entitlementIds
     *  A collection of IDs of the entitlements for which to delete certificates
     *
     * @return
     *  the number of entitlement certificates deleted
     */
    public int deleteByEntitlementIds(String... entitlementIds) {
        return entitlementIds != null ? this.deleteByEntitlementIds(Arrays.asList(entitlementIds)) : 0;
    }

    /**
     * Deletes any existing entitlement certificates for the specified entitlements.
     *
     * @param entitlementIds
     *  A collection of IDs of the entitlements for which to delete certificates
     *
     * @return
     *  the number of entitlement certificates deleted
     */
    @Transactional
    public int deleteByEntitlementIds(Iterable<String> entitlementIds) {
        if (entitlementIds == null) {
            return 0;
        }

        Map<String, Long> certIdToSerialMap = findCertIdsAndSerialsOf(entitlementIds);
        Set<String> certIds = certIdToSerialMap.keySet();
        Set<Long> serials = new HashSet<>(certIdToSerialMap.values());

        int removed = deleteOldEntitlementCertificates(certIds);
        log.debug("{} entitlement certificates removed", removed);

        // Mark the serials as revoked so they get picked up and added to the CRL later
        int revoked = revokeCertificateSerials(serials);
        log.debug("{} certificate serials revoked", revoked);

        return removed;
    }

    private Map<String, Long> findCertIdsAndSerialsOf(Iterable<String> entitlementIds) {
        Map<String, Long> certIdToSerialMap = new HashMap<>();

        String sjpql = "SELECT ec.id, ec.serial.id FROM EntitlementCertificate ec " +
            "WHERE ec.entitlement.id IN :entids";
        Query selector = this.getEntityManager().createQuery(sjpql);

        // Impl note:
        // If we don't set the flush mode here, we will trigger inserts on any pending new
        // entitlement certificates, on top of potentially catching certs we don't intend
        // to pick up.
        selector.setFlushMode(javax.persistence.FlushModeType.COMMIT);

        // Get certificate and serial IDs...
        for (List<String> block : this.partition(entitlementIds)) {
            selector.setParameter("entids", block);

            for (Object[] ids : (List<Object[]>) selector.getResultList()) {
                certIdToSerialMap.put((String) ids[0], (Long) ids[1]);
            }
        }
        return certIdToSerialMap;
    }

    private int deleteOldEntitlementCertificates(Set<String> certIds) {
        String rjpql = "DELETE FROM EntitlementCertificate ec WHERE ec.id IN :ecids";
        Query remover = this.getEntityManager().createQuery(rjpql);

        int removed = 0;
        for (List<String> block : this.partition(certIds)) {
            remover.setParameter("ecids", block);
            removed += remover.executeUpdate();
        }

        return removed;
    }

    private int revokeCertificateSerials(Set<Long> serials) {
        String ujpql = "UPDATE CertificateSerial cs SET cs.revoked = true WHERE cs.id IN :csids";
        Query updater = this.getEntityManager().createQuery(ujpql);

        int revoked = 0;
        for (List<Long> block : this.partition(serials)) {
            updater.setParameter("csids", block);
            revoked += updater.executeUpdate();
        }

        return revoked;
    }
}
