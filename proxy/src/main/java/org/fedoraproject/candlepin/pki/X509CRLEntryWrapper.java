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
package org.fedoraproject.candlepin.pki;

import java.math.BigInteger;
import java.util.Date;

/**
 * X509CRLEntryWrapper
 */
public class X509CRLEntryWrapper {
    private BigInteger serialNumber;
    private Date revocationDate;

    /**
     * Instantiates a new simple crl entry.
     * 
     * @param serialNumber the serial number
     * @param revocationDate the revocation date
     */
    public X509CRLEntryWrapper(BigInteger serialNumber, Date revocationDate) {
        this.serialNumber = serialNumber;
        this.revocationDate = revocationDate;
    }
    
    public BigInteger getSerialNumber() {
        return this.serialNumber;
    }
    
    public Date getRevocationDate() {
        return this.revocationDate;
    }
}
