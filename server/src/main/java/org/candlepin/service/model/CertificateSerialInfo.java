/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.service.model;

import java.math.BigInteger;
import java.util.Date;



/**
 * The CertificateSerialInfo represents a minimal set of certificate information used by the service
 * adapters.
 *
 * Data which is not set or does not change should be represented by null values. To explicitly
 * clear a value, an empty string or non-null "empty" value should be used instead.
 *
 * @deprecated
 *  This class may be refactored or removed entirely when the backing certificate model is
 *  reexamined and potentially normalized. This class should be properly implemented, but
 *  building functionality around it is not advised.
 */
@Deprecated
public interface CertificateSerialInfo extends ServiceAdapterModel {

    /**
     * Fetches the serial of the linking certificate. If the serial has not been set, this method
     * returns null.
     *
     * @return
     *  the serial of the linking certificate, or null if the serial has not been set
     */
    BigInteger getSerial();

    /**
     * Checks if the linking certificate(s) has/have been revoked. If the revocation flag has not
     * been set, this method returns null.
     *
     * @return
     *  true if the linking certificate(s) has/have been revoked, or null if the revocation flag has
     *  not been set
     */
    Boolean isRevoked();

    /**
     * Checks if the linking certificate(s) has/have been collected. If the collected flag has not
     * been set, this method returns null.
     *
     * @return
     *  true if the linking certificate(s) has/have been collected, or null if the collected flag
     *  has not been set
     */
    Boolean isCollected();

    /**
     * Fetches the expiration date/time of the linking certificate(s). If the expiration has not
     * been set, this method returns null.
     *
     * @return
     *  the expiration of the linking certificate(s), or null if the expiration has not been set
     */
    Date getExpiration();

}
