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
package org.fedoraproject.candlepin.client.model;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Set;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.fedoraproject.candlepin.client.Constants;
import org.fedoraproject.candlepin.client.PemUtil;

/**
 * The Class AbstractCertificate.
 */
public class AbstractCertificate extends TimeStampedEntity {

    private X509Certificate x509Certificate;
    private BigInteger serial;
    public AbstractCertificate(X509Certificate certificate) {
        this.setX509Certificate(certificate);
    }

    public AbstractCertificate() {
    }
    @JsonIgnore
    public String getProductName() {
        return PemUtil.getExtensionValue(x509Certificate,
            Constants.PROD_NAME_EXTN_VAL, "Unknown");
    }

    public Date getStartDate() {
        return this.x509Certificate.getNotBefore();
    }

    public Date getEndDate() {
        return this.x509Certificate.getNotAfter();
    }
    @JsonIgnore
    public int getProductID() {
        Set<String> extensions = this.x509Certificate.getNonCriticalExtensionOIDs();
        for (String s : extensions) {
            int index = s.indexOf(Constants.PROD_ID_BEGIN);
            if (index != -1) {
                String value = s.substring(index + Constants.PROD_ID_BEGIN.length() + 1,
                    s.indexOf(".", index + Constants.PROD_ID_BEGIN.length() + 1));
                return Integer.parseInt(value);
            }
        }
        return -1;
    }

    @JsonIgnore
    public X509Certificate getX509Certificate() {
        return x509Certificate;
    }

    @JsonIgnore
    public void setX509Certificate(X509Certificate x509Certificate) {
        this.x509Certificate = x509Certificate;
    }

    @JsonIgnore
    public BigInteger getSerial() {
        if (this.serial != null) {
            return serial;
        }
        return this.getX509Certificate().getSerialNumber();
    }

    public void setSerial(BigInteger bg) {
        this.serial = bg;
    }

    @JsonIgnore
    public String getX509CertificateAsPem() {
        try {
            return PemUtil.getPemEncoded(this.getX509Certificate());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
