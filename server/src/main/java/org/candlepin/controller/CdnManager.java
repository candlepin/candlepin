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

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCertificate;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerialCurator;

import java.util.Arrays;

/**
 * Manages Cdn entity operations.
 */
public class CdnManager {

    private CdnCurator cdnCurator;
    private CertificateSerialCurator certSerialCurator;

    @Inject
    public CdnManager(CdnCurator cdnCurator, CertificateSerialCurator certSerialCurator) {
        this.cdnCurator = cdnCurator;
        this.certSerialCurator = certSerialCurator;
    }

    /**
     * Creates and persists the specified Cdn.
     *
     * @param cdn the Cdn to create and persist.
     * @return the managed Cdn object.
     */
    @Transactional
    public Cdn createCdn(Cdn cdn) {
        // Need to persist the certificate serial since by default
        // we do not cascade persist.
        CdnCertificate cert = cdn.getCertificate();
        if (cert != null && cert.getSerial() != null) {
            certSerialCurator.create(cert.getSerial());
        }
        return cdnCurator.create(cdn);
    }

    /**
     * Updates the specified {@link Cdn}.
     *
     * @param cdn the {@link Cdn} to update.
     */
    @Transactional
    public void updateCdn(Cdn cdn) {
        CdnCertificate cert = cdn.getCertificate();
        if (cert != null && cert.getSerial() != null) {
            // No need to flush here since updating the Cdn will.
            certSerialCurator.saveOrUpdateAll(Arrays.asList(cert.getSerial()), false, false);
        }
        cdnCurator.update(cdn);
    }

    /**
     * Deletes the specified {@link Cdn}.
     *
     * @param cdn the cdn to delete.
     */
    @Transactional
    public void deleteCdn(Cdn cdn) {
        // FIXME When a Cdn is referenced by a Pool, and is deleted, an exception occurs.
        //       We should handle this here.
        cdnCurator.delete(cdn);
    }
}
