/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

package org.candlepin.sync;

/**
 * Archive filenames and directory names shared by version 2 manifest import and export
 */
// Constants are intentionally exposed through a shared interface.
@SuppressWarnings("checkstyle:InterfaceIsType")
public interface ManifestConstants {
    /**
     * Metadata filename
     */
    String META_JSON = "meta.json";

    /**
     * Directory for upstream consumer metadata and credentials
     */
    String UPSTREAM_CONSUMER = "upstream_consumer";

    /**
     * Upstream consumer metadata filename
     */
    String CONSUMER_JSON = "consumer.json";

    /**
     * Directory for v2 subscriptions keyed by upstream pool ID
     */
    String SUBSCRIPTIONS = "subscriptions";

    /**
     * Directory for exported products
     */
    String PRODUCTS = "products";

    /**
     * JSON filename suffix
     */
    String JSON_SUFFIX = ".json";

    /**
     * JSON filename pattern containing an identifier placeholder
     */
    String ID_JSON = "%s" + JSON_SUFFIX;

    /**
     * Signed inner manifest archive filename
     */
    String CONSUMER_EXPORT_ZIP = "consumer_export.zip";

    /**
     * Detached manifest signature filename
     */
    String SIGNATURE = "signature";

    /**
     * Signing scheme metadata filename
     */
    String SCHEME_JSON = SchemeFile.FILENAME;

    /**
     * SCA certificate JSON filename
     */
    String SCA_CERTIFICATE_JSON = "sca_cert.json";
}
