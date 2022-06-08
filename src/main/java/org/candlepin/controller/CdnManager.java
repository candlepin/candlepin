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
package org.candlepin.controller;

import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCertificate;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateCurator;
import org.candlepin.model.PoolCurator;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;



/**
 * Manages Cdn entity operations.
 */
public class CdnManager {

    private CdnCurator cdnCurator;
    private CertificateCurator certificateCurator;
    private PoolCurator poolCurator;

    @Inject
    public CdnManager(CdnCurator cdnCurator, PoolCurator poolCurator, CertificateCurator certificateCurator) {
        this.cdnCurator = Objects.requireNonNull(cdnCurator);
        this.certificateCurator = Objects.requireNonNull(certificateCurator);
        this.poolCurator = Objects.requireNonNull(poolCurator);
    }

    /**
     * Creates and persists the specified Cdn.
     *
     * @param cdn the Cdn to create and persist.
     * @return the managed Cdn object.
     */
    @Transactional
    public Cdn createCdn(Cdn cdn) {
        // Certificate certificate = cdn.getCertificate();
        // if (certificate != null) {
        //     this.certificateCurator.create(certificate, false);
        // }

        return this.cdnCurator.create(cdn);
    }

    /**
     * Updates the specified {@link Cdn}.
     *
     * @param cdn the {@link Cdn} to update.
     */
    @Transactional
    public void updateCdn(Cdn cdn) {
        // Certificate certificate = cdn.getCertificate();
        // if (certificate != null) {
        //     this.certificateCurator.update(certificate, false);
        // }

        this.cdnCurator.update(cdn);
    }

    /**
     * Deletes the specified {@link Cdn}.
     *
     * @param cdn the cdn to delete.
     */
    @Transactional
    public void deleteCdn(Cdn cdn) {
        this.poolCurator.removeCdn(cdn);
        this.cdnCurator.delete(cdn);
    }
}
