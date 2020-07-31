/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import javax.persistence.Query;

/**
 * UeberCertificateCurator
 *
 * Facilitates the creation and deletion of UeberCertificate objects in the database.
 *
 */
@Component
public class UeberCertificateCurator extends AbstractHibernateCurator<UeberCertificate> {

    @Autowired
    public UeberCertificateCurator() {
        super(UeberCertificate.class);
    }

    /**
     * Find the ueber certificate for the specified owner. Only one certificate should ever
     * exist per owner.
     *
     * @param owner the target Owner
     * @return the ueber certificate for this owner, null if one is not found.
     */
    @SuppressWarnings("unchecked")
    public UeberCertificate findForOwner(Owner owner) {
        String findByOwner = "select uc from UeberCertificate uc where uc.owner = :owner";
        Query query = getEntityManager().createQuery(findByOwner);
        query.setParameter("owner", owner);
        List<UeberCertificate> certs = query.getResultList();
        if (certs.isEmpty()) {
            return null;
        }
        return certs.get(0);
    }

    /**
     * Delete the UeberCertificate related to the specified owner.
     *
     * @param owner the target owner
     * @return true if a certificate was deleted for the Owner, false otherwise.
     */
    public boolean deleteForOwner(Owner owner) {
        // NOTE: We require that the @PreRemove callback be made in order to
        //       set the revoked state. Doing a direct delete via JPQL does not
        //       trigger callbacks, so unfortunately, we need to look up the owner's
        //       certificate and then delete it.
        UeberCertificate ownerCert = findForOwner(owner);
        if (ownerCert == null) {
            return false;
        }
        getEntityManager().remove(ownerCert);
        return true;
    }

}
