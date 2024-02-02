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
package org.candlepin.pki;

import org.apache.commons.lang3.StringUtils;

/**
 * This class represents the Red Hat's OID structure we use. Each nested part
 * represents a subset of OID accessible as enum variants.
 */
public final class OID {

    private static final String REDHAT_OID = "1.3.6.1.4.1.2312.9";

    /** 1.3.6.1.4.1.2312.9.1.{ productId }.x */
    public enum ProductCertificate {
        /** 1.3.6.1.4.1.2312.9.1.{ productId }.1 */
        NAME("1"),
        /** 1.3.6.1.4.1.2312.9.1.{ productId }.2 */
        VERSION("2"),
        /** 1.3.6.1.4.1.2312.9.1.{ productId }.3 */
        ARCH("3"),
        /** 1.3.6.1.4.1.2312.9.1.{ productId }.4 */
        PROVIDES("4"),
        /** 1.3.6.1.4.1.2312.9.1.{ productId }.5 */
        BRAND_TYPE("5");

        private static final String NAMESPACE = "1";

        private final String id;

        ProductCertificate(String id) {
            this.id = id;
        }

        public String value() {
            return join(REDHAT_OID, NAMESPACE, this.id);
        }

        public String value(String productId) {
            validate(productId);
            return join(REDHAT_OID, NAMESPACE, productId, this.id);
        }
    }

    /** 1.3.6.1.4.1.2312.9.2.{ contentId }.{ repoType }.x */
    public enum ChannelFamily {
        /** 1.3.6.1.4.1.2312.9.2.{ contentId }.{ repoType }.1 */
        NAME("1"),
        /** 1.3.6.1.4.1.2312.9.2.{ contentId }.{ repoType }.2 */
        LABEL("2"),
        /** 1.3.6.1.4.1.2312.9.2.{ contentId }.{ repoType }.3 */
        PHYS_QUANTITY("3"),
        /** 1.3.6.1.4.1.2312.9.2.{ contentId }.{ repoType }.4 */
        FLEX_QUANTITY("4"),
        /** 1.3.6.1.4.1.2312.9.2.{ contentId }.{ repoType }.5 */
        VENDOR_ID("5"),
        /** 1.3.6.1.4.1.2312.9.2.{ contentId }.{ repoType }.6 */
        DOWNLOAD_URL("6"),
        /** 1.3.6.1.4.1.2312.9.2.{ contentId }.{ repoType }.7 */
        GPG_URL("7"),
        /** 1.3.6.1.4.1.2312.9.2.{ contentId }.{ repoType }.8 */
        ENABLED("8"),
        /** 1.3.6.1.4.1.2312.9.2.{ contentId }.{ repoType }.9 */
        METADATA_EXPIRE("9"),
        /** 1.3.6.1.4.1.2312.9.2.{ contentId }.{ repoType }.10 */
        REQUIRED_TAGS("10");

        private static final String NAMESPACE = "2";

        /**
         * 1.3.6.1.4.1.2312.9.2.{ contentId }.{ repoType }
         *
         * @return Channel family OID
         */
        public static String namespace(RepoType type, String contentId) {
            validate(contentId);
            return join(REDHAT_OID, NAMESPACE, contentId, type.id());
        }

        /**
         * 1.3.6.1.4.1.2312.9.2
         *
         * @return Channel family OID
         */
        public static String namespace() {
            return join(REDHAT_OID, NAMESPACE);
        }

        private final String id;

        ChannelFamily(String id) {
            this.id = id;
        }

        public String value(RepoType type, String contentId) {
            validate(contentId);
            return join(REDHAT_OID, NAMESPACE, contentId, type.id(), this.id);
        }
    }

    /** 1.3.6.1.4.1.2312.9.3.x */
    public enum RoleEntitlement {
        /** 1.3.6.1.4.1.2312.9.3.1 */
        NAME("1"),
        /** 1.3.6.1.4.1.2312.9.3.2 */
        LABEL("2"),
        /** 1.3.6.1.4.1.2312.9.3.3 */
        QUANTITY("3");

        private static final String NAMESPACE = "3";

        private final String id;

        RoleEntitlement(String id) {
            this.id = id;
        }

        public String value() {
            return join(REDHAT_OID, NAMESPACE, this.id);
        }
    }

    /** 1.3.6.1.4.1.2312.9.4.x */
    public enum Order {
        /** 1.3.6.1.4.1.2312.9.4.1 */
        NAME("1"),
        /** 1.3.6.1.4.1.2312.9.4.2 */
        NUMBER("2"),
        /** 1.3.6.1.4.1.2312.9.4.3 */
        SKU("3"),
        /** 1.3.6.1.4.1.2312.9.4.4 */
        SUBSCRIPTION_NUMBER("4"),
        /** 1.3.6.1.4.1.2312.9.4.5 */
        QUANTITY("5"),
        /** 1.3.6.1.4.1.2312.9.4.6 */
        START_DATE("6"),
        /** 1.3.6.1.4.1.2312.9.4.7 */
        END_DATE("7"),
        /** 1.3.6.1.4.1.2312.9.4.8 */
        VIRT_LIMIT("8"),
        /** 1.3.6.1.4.1.2312.9.4.9 */
        SOCKET_LIMIT("9"),
        /** 1.3.6.1.4.1.2312.9.4.10 */
        CONTRACT_NUMBER("10"),
        /** 1.3.6.1.4.1.2312.9.4.11 */
        QUANTITY_USED("11"),
        /** 1.3.6.1.4.1.2312.9.4.12 */
        WARNING_PERIOD("12"),
        /** 1.3.6.1.4.1.2312.9.4.13 */
        ACCOUNT_NUMBER("13"),
        /** 1.3.6.1.4.1.2312.9.4.14 */
        PROVIDES_MANAGEMENT("14"),
        /** 1.3.6.1.4.1.2312.9.4.15 */
        SUPPORT_LEVEL("15"),
        /** 1.3.6.1.4.1.2312.9.4.16 */
        SUPPORT_TYPE("16"),
        /** 1.3.6.1.4.1.2312.9.4.17 */
        STACKING_ID("17"),
        /** 1.3.6.1.4.1.2312.9.4.18 */
        VIRT_ONLY("18");

        private static final String NAMESPACE = "4";

        public static String namespace() {
            return join(REDHAT_OID, NAMESPACE);
        }

        private final String id;

        Order(String id) {
            this.id = id;
        }

        public String value() {
            return join(REDHAT_OID, NAMESPACE, this.id);
        }
    }

    /** 1.3.6.1.4.1.2312.9.5.x */
    public enum System {
        /** 1.3.6.1.4.1.2312.9.5.1 */
        UUID("1"),
        /** 1.3.6.1.4.1.2312.9.5.2 */
        HOST_UUID("2");

        private static final String NAMESPACE = "5";

        public static String namespace() {
            return join(REDHAT_OID, NAMESPACE);
        }

        private final String id;

        System(String id) {
            this.id = id;
        }

        public String value() {
            return join(REDHAT_OID, NAMESPACE, this.id);
        }
    }

    /** 1.3.6.1.4.1.2312.9.6 */
    public static class EntitlementVersion {
        private static final String NAMESPACE = "6";

        public static String namespace() {
            return join(REDHAT_OID, NAMESPACE);
        }
    }

    /** 1.3.6.1.4.1.2312.9.7 */
    public static class EntitlementData {
        private static final String NAMESPACE = "7";

        public static String namespace() {
            return join(REDHAT_OID, NAMESPACE);
        }
    }

    /** 1.3.6.1.4.1.2312.9.8 */
    public static class EntitlementType {
        private static final String NAMESPACE = "8";

        public static String namespace() {
            return join(REDHAT_OID, NAMESPACE);
        }
    }

    /** 1.3.6.1.4.1.2312.9.9 */
    public static class EntitlementNamespace {
        private static final String NAMESPACE = "9";

        public static String namespace() {
            return join(REDHAT_OID, NAMESPACE);
        }
    }

    private static String join(String... parts) {
        return String.join(".", parts);
    }

    private static void validate(String id) {
        if (!StringUtils.isNumeric(id)) {
            throw new IllegalArgumentException("OID must be numeric: [%s].".formatted(id));
        }
    }

}
