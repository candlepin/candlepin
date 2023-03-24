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
 * The CdnInfo represents a minimal set of CDN information used by the service adapters.
 *
 * Data which is not set or does not change should be represented by null values. To explicitly
 * clear a value, an empty string or non-null "empty" value should be used instead.
 */
public interface CdnInfo extends ServiceAdapterModel {

    /**
     * Fetches the name of this CDN. If the name has not been set, this method returns null.
     *
     * @return
     *  The name of the CDN, or null if the name has not been set
     */
    String getName();

    /**
     * Fetches the label of this CDN. If the label has not been set, this method returns null.
     *
     * @return
     *  The label of the CDN, or null if the label has not been set
     */
    String getLabel();

    /**
     * Fetches the URL of this CDN. If the URL has not been set, this method returns null.
     *
     * @return
     *  The URL of the CDN, or null if the URL has not been set
     */
    String getUrl();

    /**
     * Fetches the certificate info associated with this CDN. If this CDN has no associated
     * certificate, this method returns null.
     * <p></p>
     * <strong>Note:</strong> Due to the nature of this field, null is always treated as
     * "no value" rather than "no change."
     *
     * @return
     *  The certificate info associated with this CDN, or null if this CDN does not have a
     *  certificate
     */
    CertificateInfo getCertificate();

}
