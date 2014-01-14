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
package org.candlepin.util;

import java.util.HashMap;
import java.util.Map;

/**
 * implements the OID structure found here
 * https://docspace.corp.redhat.com/clearspace/docs/DOC-30244
 *
 * @author jomara
 */
public final class OIDUtil {

    private OIDUtil() {
    }

    public static final String REDHAT_OID = "1.3.6.1.4.1.2312.9";
    public static final String SYSTEM_NAMESPACE_KEY = "System";
    public static final String ORDER_NAMESPACE_KEY = "Order";
    public static final String CHANNEL_FAMILY_NAMESPACE_KEY = "Channel Family";
    public static final String ROLE_ENT_NAMESPACE_KEY = "Role Entitlement";
    public static final String PRODUCT_CERT_NAMESPACE_KEY = "Product";
    public static final String ENTITLEMENT_VERSION_KEY = "Entitlement Version";
    public static final String ENTITLEMENT_DATA_KEY = "Entitlement Data";
    public static final String UUID_KEY = "UUID";
    public static final String HOST_UUID_KEY = "Host UUID";
    public static final String ORDER_NAME_KEY = "Name";
    public static final String ORDER_NUMBER_KEY = "Order Number";
    public static final String ORDER_CONTRACT_NUMBER_KEY = "Contract Number";
    public static final String ORDER_QUANTITY_USED = "Quantity Used";
    public static final String ORDER_SKU_KEY = "SKU";
    public static final String ORDER_SUBSCRIPTION_NUMBER_KEY = "Subscription Number";
    public static final String ORDER_QUANTITY_KEY = "Quantity";
    public static final String ORDER_STARTDATE_KEY = "Entitlement Start Date";
    public static final String ORDER_ENDDATE_KEY = "Entitlement End Date";
    public static final String ORDER_VIRTLIMIT_KEY = "Virtualization Limit";
    public static final String ORDER_SOCKETLIMIT_KEY = "Socket Limit";
    public static final String ORDER_WARNING_PERIOD = "Warning Period";
    public static final String ORDER_ACCOUNT_NUMBER_KEY = "Account Number";
    public static final String ORDER_PROVIDES_MANAGEMENT_KEY = "Provides Management";
    public static final String ORDER_SUPPORT_LEVEL = "Support Level";
    public static final String ORDER_SUPPORT_TYPE = "Support Type";
    public static final String ORDER_STACKING_ID = "Stacking Id";
    public static final String ORDER_VIRT_ONLY_KEY = "Virt Only";

    public static final String OP_NAME_KEY = "Name";
    public static final String OP_VERSION_KEY = "Version";
    public static final String OP_ARCH_KEY = "Architecture";
    public static final String OP_PROVIDES_KEY = "Provides";
    public static final String OP_BRAND_TYPE_KEY = "Brand Type";

    public static final String CF_NAME_KEY = "Name";
    public static final String CF_LABEL_KEY = "Label";
    public static final String CF_PHYS_QUANTITY_KEY = "Physical Entitlement Quantity";
    public static final String CF_FLEX_QUANTITY_KEY = "Flex Guest Entitlement Quantity";
    public static final String CF_CHANNELS_NAMESPACE = "Channel Namespace";
    public static final String CF_VENDOR_ID_KEY = "Vendor ID";
    public static final String CF_DOWNLOAD_URL_KEY = "Download URL";
    public static final String CF_GPG_URL_KEY = "GPG Key URL";
    public static final String CF_ENABLED = "Enabled";
    public static final String CF_METADATA_EXPIRE = "Metadata Expire";
    public static final String CF_REQUIRED_TAGS = "Required Tags";

    public static final String ROLE_NAME_KEY = "Name";
    public static final String ROLE_LABEL_KEY = "Label";
    public static final String ROLE_QUANTITY_KEY = "Quantity";

    public static final String CF_REPO_TYPE_FILE_KEY = "file";
    public static final String CF_REPO_TYPE_YUM_KEY = "yum";
    public static final String CF_REPO_TYPE_KICKSTART_KEY = "kickstart";

    public static final Map<String, String> SYSTEM_OIDS = new HashMap<String, String>();
    public static final Map<String, String> ORDER_OIDS = new HashMap<String, String>();
    public static final Map<String, String> ORDER_PRODUCT_OIDS =
        new HashMap<String, String>();
    public static final Map<String, String> TOPLEVEL_NAMESPACES =
        new HashMap<String, String>();
    public static final Map<String, String> CONTENT_ENTITLEMENT_OIDS =
        new HashMap<String, String>();
    public static final Map<String, String> ROLE_ENTITLEMENT_OIDS =
        new HashMap<String, String>();
    public static final Map<String, String> CONTENT_ENTITLEMENT_NAMESPACES =
        new HashMap<String, String>();
    public static final Map<String, String> SERVER_ENTITLEMENT_NAMESPACES =
        new HashMap<String, String>();
    public static final Map<String, String> CONTENT_ARCHITECTURES =
        new HashMap<String, String>();
    public static final Map<String, String> CHANNEL_FAMILY_OIDS =
        new HashMap<String, String>();
    public static final Map<String, String> CHANNEL_OIDS = new HashMap<String, String>();
    public static final Map<String, String> CF_REPO_TYPE = new HashMap<String, String>();

    static {
        // load top level namespaces
        TOPLEVEL_NAMESPACES.put(PRODUCT_CERT_NAMESPACE_KEY, "1");
        TOPLEVEL_NAMESPACES.put(CHANNEL_FAMILY_NAMESPACE_KEY, "2");
        TOPLEVEL_NAMESPACES.put(ROLE_ENT_NAMESPACE_KEY, "3");
        TOPLEVEL_NAMESPACES.put(ORDER_NAMESPACE_KEY, "4");
        TOPLEVEL_NAMESPACES.put(SYSTEM_NAMESPACE_KEY, "5");
        TOPLEVEL_NAMESPACES.put(ENTITLEMENT_VERSION_KEY, "6");
        TOPLEVEL_NAMESPACES.put(ENTITLEMENT_DATA_KEY, "7");

        // system OIDs
        SYSTEM_OIDS.put(UUID_KEY, "1");
        SYSTEM_OIDS.put(HOST_UUID_KEY, "2");
        // order OIDs
        ORDER_OIDS.put(ORDER_NAME_KEY, "1");
        ORDER_OIDS.put(ORDER_NUMBER_KEY, "2");
        ORDER_OIDS.put(ORDER_SKU_KEY, "3");
        ORDER_OIDS.put(ORDER_SUBSCRIPTION_NUMBER_KEY, "4");
        ORDER_OIDS.put(ORDER_QUANTITY_KEY, "5");
        ORDER_OIDS.put(ORDER_STARTDATE_KEY, "6");
        ORDER_OIDS.put(ORDER_ENDDATE_KEY, "7");
        ORDER_OIDS.put(ORDER_VIRTLIMIT_KEY, "8");
        ORDER_OIDS.put(ORDER_SOCKETLIMIT_KEY, "9");
        ORDER_OIDS.put(ORDER_CONTRACT_NUMBER_KEY, "10");
        ORDER_OIDS.put(ORDER_QUANTITY_USED, "11");
        ORDER_OIDS.put(ORDER_WARNING_PERIOD, "12");
        ORDER_OIDS.put(ORDER_ACCOUNT_NUMBER_KEY, "13");
        ORDER_OIDS.put(ORDER_PROVIDES_MANAGEMENT_KEY, "14");
        ORDER_OIDS.put(ORDER_SUPPORT_LEVEL, "15");
        ORDER_OIDS.put(ORDER_SUPPORT_TYPE, "16");
        ORDER_OIDS.put(ORDER_STACKING_ID, "17");
        ORDER_OIDS.put(ORDER_VIRT_ONLY_KEY, "18");

        // load order product OIDs
        ORDER_PRODUCT_OIDS.put(OP_NAME_KEY, "1");
        ORDER_PRODUCT_OIDS.put(OP_VERSION_KEY, "2");
        ORDER_PRODUCT_OIDS.put(OP_ARCH_KEY, "3");
        ORDER_PRODUCT_OIDS.put(OP_PROVIDES_KEY, "4");
        ORDER_PRODUCT_OIDS.put(OP_BRAND_TYPE_KEY, "5");

        // role entitlement OIDs
        ROLE_ENTITLEMENT_OIDS.put(ROLE_NAME_KEY, "1");
        ROLE_ENTITLEMENT_OIDS.put(ROLE_LABEL_KEY, "2");
        ROLE_ENTITLEMENT_OIDS.put(ROLE_QUANTITY_KEY, "3");

        // channel family OIDs
        CHANNEL_FAMILY_OIDS.put(CF_NAME_KEY, "1");
        CHANNEL_FAMILY_OIDS.put(CF_LABEL_KEY, "2");
        CHANNEL_FAMILY_OIDS.put(CF_PHYS_QUANTITY_KEY, "3");
        CHANNEL_FAMILY_OIDS.put(CF_FLEX_QUANTITY_KEY, "4");
        CHANNEL_FAMILY_OIDS.put(CF_VENDOR_ID_KEY, "5");
        CHANNEL_FAMILY_OIDS.put(CF_DOWNLOAD_URL_KEY, "6");
        CHANNEL_FAMILY_OIDS.put(CF_GPG_URL_KEY, "7");
        CHANNEL_FAMILY_OIDS.put(CF_ENABLED, "8");
        CHANNEL_FAMILY_OIDS.put(CF_METADATA_EXPIRE, "9");
        CHANNEL_FAMILY_OIDS.put(CF_REQUIRED_TAGS, "10");

        // server entitlement namespaces
        // this could come from the RHN DB, but we will wait until we actually
        // need it...
        SERVER_ENTITLEMENT_NAMESPACES.put("sw_mgr_entitled", "2");
        SERVER_ENTITLEMENT_NAMESPACES.put("enterprise_entitled", "3");
        SERVER_ENTITLEMENT_NAMESPACES.put("provisioning_entitled", "24");
        SERVER_ENTITLEMENT_NAMESPACES.put("nonlinux_entitled", "25");
        SERVER_ENTITLEMENT_NAMESPACES.put("monitoring_entitled", "26");
        SERVER_ENTITLEMENT_NAMESPACES.put("virtualization_host", "27");
        SERVER_ENTITLEMENT_NAMESPACES.put("virtualization_host_platform", "28");

        CF_REPO_TYPE.put(CF_REPO_TYPE_YUM_KEY, "1");
        CF_REPO_TYPE.put(CF_REPO_TYPE_FILE_KEY, "2");
        CF_REPO_TYPE.put(CF_REPO_TYPE_KICKSTART_KEY, "3");
    }

    // Maybe not the best place for this, but better than relying on bouncycastle for it.
    public static final String CRL_NUMBER = "2.5.29.20";
}
