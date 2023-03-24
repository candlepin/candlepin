/**
 * Copyright (c) 2009 - 2021 Red Hat, Inc.
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

import java.util.Objects;

/**
 * An immutable dto projection of certificate id and its serial.
 */
public class ExpiredCertificate {
    private final String certId;
    private final Long serial;

    public ExpiredCertificate(String certId, Long serial) {
        this.certId = certId;
        this.serial = serial;
    }

    public String getCertId() {
        return certId;
    }

    public Long getSerial() {
        return serial;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExpiredCertificate that = (ExpiredCertificate) o;
        return Objects.equals(certId, that.certId) && Objects.equals(serial, that.serial);
    }

    @Override
    public int hashCode() {
        return Objects.hash(certId, serial);
    }

    @Override
    public String toString() {
        return "ExpiredCertificate{" +
            "certId='" + certId + '\'' +
            ", serial=" + serial +
            '}';
    }

}
