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


import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Query;



/**
 * EntitlementCertificateCurator
 */
@Component
public class EntitlementCertificateCurator extends AbstractHibernateCurator<EntitlementCertificate> {
    private static Logger log = LoggerFactory.getLogger(EntitlementCertificateCurator.class);

    @Autowired
    public EntitlementCertificateCurator() {
        super(EntitlementCertificate.class);
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public List<EntitlementCertificate> listForEntitlement(Entitlement e) {
        return currentSession().createCriteria(
            EntitlementCertificate.class).add(
                Restrictions.eq("entitlement", e)).list();

    }

    @SuppressWarnings("unchecked")
    @Transactional
    public List<EntitlementCertificate> listForConsumer(Consumer c) {
        return currentSession().createCriteria(EntitlementCertificate.class)
            .createAlias("entitlement", "ent")
            .createAlias("ent.pool", "p")
            .add(Restrictions.eq("ent.consumer", c))
            // Never show a consumer expired certificates
            .add(Restrictions.ge("p.endDate", new Date()))
            .list();
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
        int removed = 0;
        int revoked = 0;

        if (entitlementIds != null) {
            Set<String> certIds = new HashSet<>();
            Set<Long> serials = new HashSet<>();

            String sjpql = "SELECT ec.id, ec.serial.id FROM EntitlementCertificate ec " +
                "WHERE ec.entitlement.id IN :entids";
            Query selector = this.getEntityManager().createQuery(sjpql);

            // Impl note:
            // If we don't set the flush mode here, we will trigger inserts on any pending new
            // entitlement certificates, on top of potentially catching certs we don't intend
            // to pick up.
            selector.setFlushMode(javax.persistence.FlushModeType.COMMIT);

            String rjpql = "DELETE FROM EntitlementCertificate ec WHERE ec.id IN :ecids";
            Query remover = this.getEntityManager().createQuery(rjpql);

            // Get certificate and serial IDs...
            for (List<String> block : this.partition(entitlementIds)) {
                selector.setParameter("entids", block);

                for (Object[] ids : (List<Object[]>) selector.getResultList()) {
                    certIds.add((String) ids[0]);
                    serials.add((Long) ids[1]);
                }
            }

            // Delete the old entitlement certificates
            for (List<String> block : this.partition(certIds)) {
                remover.setParameter("ecids", block);
                removed += remover.executeUpdate();
            }

            log.debug("{} entitlement certificates removed", removed);

            // Mark the serials as revoked so they get picked up and added to the CRL later
            String ujpql = "UPDATE CertificateSerial cs SET cs.revoked = true WHERE cs.id IN :csids";
            Query updater = this.getEntityManager().createQuery(ujpql);

            for (List<Long> block : this.partition(serials)) {
                updater.setParameter("csids", block);
                revoked += updater.executeUpdate();
            }

            log.debug("{} certificate serials revoked", revoked);
        }

        return removed;
    }
}
