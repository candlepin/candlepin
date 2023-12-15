/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.spec.bootstrap.data.builder;

import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;

public final class OID {

    private static final String REDHAT_OID = "1.3.6.1.4.1.2312.9.";
    private static final String CONTENT_OID = REDHAT_OID + "2.";

    private OID() {
        throw new UnsupportedOperationException();
    }

    public static String productName(ProductDTO product) {
        return REDHAT_OID + "1." + product.getId() + ".1";
    }

    public static String contentRepoType(ContentDTO content) {
        return CONTENT_OID + content.getId() + ".1";
    }

    public static String contentName(ContentDTO content) {
        return CONTENT_OID + content.getId() + ".1.1";
    }

    public static String contentRepoEnabled(ContentDTO content) {
        return CONTENT_OID + content.getId() + ".1.8";
    }

    public static String certificateVersion() {
        return REDHAT_OID + "6";
    }

    public static String entitlementPayload() {
        return REDHAT_OID + "7";
    }

    public static String entitlementType() {
        return REDHAT_OID + "8";
    }

    public static String entitlementNamespace() {
        return REDHAT_OID + "9";
    }
}
