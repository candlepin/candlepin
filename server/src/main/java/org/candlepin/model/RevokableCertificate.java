/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.MappedSuperclass;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.PreRemove;
import javax.persistence.Transient;


/**
 * A class that represents a revokable certificate. A revokable certificate should
 * have an associated {@link CertificateSerial} that can be placed on the Certificate
 * Revocation List.
 */
@MappedSuperclass
public abstract class RevokableCertificate extends AbstractCertificate {
    @Transient
    private static Logger log = LoggerFactory.getLogger(RevokableCertificate.class);

    @OneToOne
    @JoinColumn(name = "serial_id")
    protected CertificateSerial serial;

    public RevokableCertificate() {

    }

    public RevokableCertificate(CertificateSerial serial) {
        this.serial = serial;
    }

    public CertificateSerial getSerial() {
        return serial;
    }

    public void setSerial(CertificateSerial serialNumber) {
        this.serial = serialNumber;
    }

    /**
     * When a certificate is deleted, it is considered revoked, and
     * its {@link CertificateSerial} should be marked as such. This
     * method is run before the certificate is deleted from the database and sets
     * the revoked flag on the certificates serial to true.
     *
     * <strong>
     *     NOTE: This callback is not fired when a certificate is deleted using
     *     JPQL. The certificate has to be removed via EntityManager.remove or via
     *     cascading.
     * </strong>
     */
    @PreRemove
    public void revokeCertificateSerial() {
        CertificateSerial certSerial = getSerial();
        if (certSerial != null) {
            certSerial.setRevoked(true);
        }
    }
}
