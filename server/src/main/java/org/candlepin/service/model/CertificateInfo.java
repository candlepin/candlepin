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



/**
 * The CertificateInfo represents a minimal set of certificate information used by the service
 * adapters.
 *
 * Data which is not set or does not change should be represented by null values. To explicitly
 * clear a value, an empty string or non-null "empty" value should be used instead.
 */
public interface CertificateInfo extends ServiceAdapterModel {

    /**
     * Fetches the serial of this certificate. If the serial has not been set, this method returns
     * null.
     *
     * @deprecated
     *  This method may be refactored or removed entirely when the backing certificate model is
     *  reexamined and potentially normalized. This method should be properly implemented, but
     *  building functionality around it is not advised.
     *
     * @return
     *  the serial of this certificate, or null if the serial has not been set
     */
    @Deprecated
    CertificateSerialInfo getSerial();

    /**
     * Fetches this certificate's key as PEM-encoded string. If the key has not been set, this
     * method returns null.
     *
     * @return
     *  the key for this certificate, or null if the key has not been set
     */
    String getKey();

    /**
     * Fetches a PEM-encoded x509 certificate as a string. If the certificate has not been set, this
     * method returns null.
     *
     * @return
     *  a PEM-encoded x509 certificate, or null if the certificate has not been set
     */
    String getCertificate();

}
