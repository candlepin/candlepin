/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OIDTest {

    @Test
    void productCertificateOIDs() {
        String productId = "12345";
        assertEquals("1.3.6.1.4.1.2312.9.1.12345.1", OID.ProductCertificate.NAME.value(productId));
        assertEquals("1.3.6.1.4.1.2312.9.1.12345.2", OID.ProductCertificate.VERSION.value(productId));
        assertEquals("1.3.6.1.4.1.2312.9.1.12345.3", OID.ProductCertificate.ARCH.value(productId));
        assertEquals("1.3.6.1.4.1.2312.9.1.12345.4", OID.ProductCertificate.PROVIDES.value(productId));
        assertEquals("1.3.6.1.4.1.2312.9.1.12345.5", OID.ProductCertificate.BRAND_TYPE.value(productId));
    }

    @Test
    void productCertificateOIDsNeedProductId() {
        assertThrows(IllegalArgumentException.class, () -> OID.ProductCertificate.NAME.value(null));
        assertThrows(IllegalArgumentException.class, () -> OID.ProductCertificate.NAME.value(" "));
    }

    @Test
    void productCertificateOIDsNeedNumericProductId() {
        assertEquals("1.3.6.1.4.1.2312.9.1.123.1", OID.ProductCertificate.NAME.value("123"));
        assertThrows(IllegalArgumentException.class, () -> OID.ProductCertificate.NAME.value("invalid"));
    }

    @Test
    void channelFamilyOIDsWithYumRepo() {
        RepoType type = RepoType.YUM;
        String id = "1234";
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.1", OID.ChannelFamily.namespace(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.1.1", OID.ChannelFamily.NAME.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.1.2", OID.ChannelFamily.LABEL.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.1.3", OID.ChannelFamily.PHYS_QUANTITY.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.1.4", OID.ChannelFamily.FLEX_QUANTITY.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.1.5", OID.ChannelFamily.VENDOR_ID.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.1.6", OID.ChannelFamily.DOWNLOAD_URL.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.1.7", OID.ChannelFamily.GPG_URL.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.1.8", OID.ChannelFamily.ENABLED.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.1.9", OID.ChannelFamily.METADATA_EXPIRE.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.1.10", OID.ChannelFamily.REQUIRED_TAGS.value(type, id));
    }

    @Test
    void channelFamilyOIDsWithFileRepo() {
        RepoType type = RepoType.FILE;
        String id = "1234";
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.2", OID.ChannelFamily.namespace(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.2.1", OID.ChannelFamily.NAME.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.2.2", OID.ChannelFamily.LABEL.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.2.3", OID.ChannelFamily.PHYS_QUANTITY.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.2.4", OID.ChannelFamily.FLEX_QUANTITY.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.2.5", OID.ChannelFamily.VENDOR_ID.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.2.6", OID.ChannelFamily.DOWNLOAD_URL.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.2.7", OID.ChannelFamily.GPG_URL.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.2.8", OID.ChannelFamily.ENABLED.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.2.9", OID.ChannelFamily.METADATA_EXPIRE.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.2.10", OID.ChannelFamily.REQUIRED_TAGS.value(type, id));
    }

    @Test
    void channelFamilyOIDsWithKickstartRepo() {
        RepoType type = RepoType.KICKSTART;
        String id = "1234";
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.3", OID.ChannelFamily.namespace(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.3.1", OID.ChannelFamily.NAME.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.3.2", OID.ChannelFamily.LABEL.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.3.3", OID.ChannelFamily.PHYS_QUANTITY.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.3.4", OID.ChannelFamily.FLEX_QUANTITY.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.3.5", OID.ChannelFamily.VENDOR_ID.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.3.6", OID.ChannelFamily.DOWNLOAD_URL.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.3.7", OID.ChannelFamily.GPG_URL.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.3.8", OID.ChannelFamily.ENABLED.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.3.9", OID.ChannelFamily.METADATA_EXPIRE.value(type, id));
        assertEquals("1.3.6.1.4.1.2312.9.2.1234.3.10", OID.ChannelFamily.REQUIRED_TAGS.value(type, id));
    }

    @Test
    void channelFamilyOIDsNeedContentId() {
        RepoType type = RepoType.YUM;
        assertThrows(IllegalArgumentException.class, () -> OID.ChannelFamily.namespace(type, null));
        assertThrows(IllegalArgumentException.class, () -> OID.ChannelFamily.namespace(type, " "));
        assertThrows(IllegalArgumentException.class, () -> OID.ChannelFamily.NAME.value(type, null));
        assertThrows(IllegalArgumentException.class, () -> OID.ChannelFamily.NAME.value(type, " "));
    }

    @Test
    void channelFamilyOIDsNeedsNumericContentId() {
        RepoType type = RepoType.YUM;
        assertEquals("1.3.6.1.4.1.2312.9.2.123.1", OID.ChannelFamily.namespace(type, "123"));
        assertEquals("1.3.6.1.4.1.2312.9.2.123.1.1", OID.ChannelFamily.NAME.value(type, "123"));
        assertThrows(IllegalArgumentException.class, () -> OID.ChannelFamily.namespace(type, "invalid"));
        assertThrows(IllegalArgumentException.class, () -> OID.ChannelFamily.NAME.value(type, "invalid"));
    }

    @Test
    void roleEntitlementOIDs() {
        assertEquals("1.3.6.1.4.1.2312.9.3.1", OID.RoleEntitlement.NAME.value());
        assertEquals("1.3.6.1.4.1.2312.9.3.2", OID.RoleEntitlement.LABEL.value());
        assertEquals("1.3.6.1.4.1.2312.9.3.3", OID.RoleEntitlement.QUANTITY.value());
    }

    @Test
    void orderOIDs() {
        assertEquals("1.3.6.1.4.1.2312.9.4.1", OID.Order.NAME.value());
        assertEquals("1.3.6.1.4.1.2312.9.4.2", OID.Order.NUMBER.value());
        assertEquals("1.3.6.1.4.1.2312.9.4.3", OID.Order.SKU.value());
        assertEquals("1.3.6.1.4.1.2312.9.4.4", OID.Order.SUBSCRIPTION_NUMBER.value());
        assertEquals("1.3.6.1.4.1.2312.9.4.5", OID.Order.QUANTITY.value());
        assertEquals("1.3.6.1.4.1.2312.9.4.6", OID.Order.START_DATE.value());
        assertEquals("1.3.6.1.4.1.2312.9.4.7", OID.Order.END_DATE.value());
        assertEquals("1.3.6.1.4.1.2312.9.4.8", OID.Order.VIRT_LIMIT.value());
        assertEquals("1.3.6.1.4.1.2312.9.4.9", OID.Order.SOCKET_LIMIT.value());
        assertEquals("1.3.6.1.4.1.2312.9.4.10", OID.Order.CONTRACT_NUMBER.value());
        assertEquals("1.3.6.1.4.1.2312.9.4.11", OID.Order.QUANTITY_USED.value());
        assertEquals("1.3.6.1.4.1.2312.9.4.12", OID.Order.WARNING_PERIOD.value());
        assertEquals("1.3.6.1.4.1.2312.9.4.13", OID.Order.ACCOUNT_NUMBER.value());
        assertEquals("1.3.6.1.4.1.2312.9.4.14", OID.Order.PROVIDES_MANAGEMENT.value());
        assertEquals("1.3.6.1.4.1.2312.9.4.15", OID.Order.SUPPORT_LEVEL.value());
        assertEquals("1.3.6.1.4.1.2312.9.4.16", OID.Order.SUPPORT_TYPE.value());
        assertEquals("1.3.6.1.4.1.2312.9.4.17", OID.Order.STACKING_ID.value());
        assertEquals("1.3.6.1.4.1.2312.9.4.18", OID.Order.VIRT_ONLY.value());
    }

    @Test
    void systemOIDs() {
        assertEquals("1.3.6.1.4.1.2312.9.5", OID.System.namespace());
        assertEquals("1.3.6.1.4.1.2312.9.5.1", OID.System.UUID.value());
        assertEquals("1.3.6.1.4.1.2312.9.5.2", OID.System.HOST_UUID.value());
    }

    @Test
    void entitlementVersionOIDs() {
        assertEquals("1.3.6.1.4.1.2312.9.6", OID.EntitlementVersion.namespace());
    }

    @Test
    void entitlementDataOIDs() {
        assertEquals("1.3.6.1.4.1.2312.9.7", OID.EntitlementData.namespace());
    }

    @Test
    void entitlementTypeOIDs() {
        assertEquals("1.3.6.1.4.1.2312.9.8", OID.EntitlementType.namespace());
    }

    @Test
    void entitlementNamespaceOIDs() {
        assertEquals("1.3.6.1.4.1.2312.9.9", OID.EntitlementNamespace.namespace());
    }

}
