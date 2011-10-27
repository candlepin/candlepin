/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.client.model;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.candlepin.client.PemUtil;

/**
 * The Class AbstractCertificate.
 */
public class AbstractCertificate extends TimeStampedEntity {

    /** The x509 certificate. */
    private X509Certificate x509Certificate;

    /**
     * Instantiates a new abstract certificate.
     *
     * @param certificate the certificate
     */
    public AbstractCertificate(X509Certificate certificate) {
        this.setX509Certificate(certificate);
    }

    /**
     * Instantiates a new abstract certificate.
     */
    public AbstractCertificate() {
    }

    /**
     * Gets the start date.
     *
     * @return the start date
     */
    public Date getStartDate() {
        return this.x509Certificate.getNotBefore();
    }

    /**
     * Gets the end date.
     *
     * @return the end date
     */
    public Date getEndDate() {
        return this.x509Certificate.getNotAfter();
    }

    /**
     * Gets the x509 certificate.
     *
     * @return the x509 certificate
     */
    @JsonIgnore
    public X509Certificate getX509Certificate() {
        return x509Certificate;
    }

    /**
     * Sets the x509 certificate.
     *
     * @param x509Certificate the new x509 certificate
     */
    @JsonIgnore
    public void setX509Certificate(X509Certificate x509Certificate) {
        this.x509Certificate = x509Certificate;
    }

    /**
     * Gets the serial.
     *
     * @return the serial
     */
    @JsonIgnore
    public BigInteger getSerial() {
        return this.getX509Certificate().getSerialNumber();
    }

    /**
     * Gets the x509 certificate as pem.
     *
     * @return the x509 certificate as pem
     */
    @JsonIgnore
    public String getX509CertificateAsPem() {
        try {
            return PemUtil.getPemEncoded(this.getX509Certificate());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isValid() {
        Date currentDate = new Date();
        return currentDate.after(getStartDate()) &&
            currentDate.before(getEndDate());
    }
}
