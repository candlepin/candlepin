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

import org.candlepin.service.model.CertificateInfo;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

/**
 * AbstractCertificate is a base class for all certificates within Candlepin.
 * It contains a number of common methods used by all certificates.
 *
 * @param <T>
 *  Entity type extending this class; should be the name of the subclass
 */
@MappedSuperclass
@XmlType(name = "Certificate")
public abstract class AbstractCertificate<T extends AbstractCertificate> extends AbstractHibernateObject<T>
    implements CertificateInfo {

    @Column(nullable = false, name = "privatekey")
    @NotNull
    private byte[] key;

    @Column(nullable = false)
    @NotNull
    private byte[] cert;

    @XmlTransient
    public void setKeyAsBytes(byte[] key) {
        this.key = key;
    }

    @XmlTransient
    public byte[] getKeyAsBytes() {
        return key;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getKey() {
        return new String(key);
    }

    public void setKey(String key) {
        this.key = key.getBytes();
    }

    @XmlTransient
    public void setCertAsBytes(byte[] cert) {
        this.cert = cert;
    }

    @XmlTransient
    public byte[] getCertAsBytes() {
        return cert;
    }

    public String getCert() {
        return new String(cert);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCertificate() {
        return this.getCert();
    }

    public void setCert(String cert) {
        this.cert = cert.getBytes();
    }

}
