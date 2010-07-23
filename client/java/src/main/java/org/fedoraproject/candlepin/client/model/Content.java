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
package org.fedoraproject.candlepin.client.model;

import org.apache.commons.lang.math.NumberUtils;

/**
 * Content
 */
public class Content extends Entitlement {

    private Extensions extensions;

    public Content(Extensions extensions, String productId) {
        this.extensions = extensions.getValue("1") == null ? extensions
            .branch("2") : extensions.branch("1");
        super.setProductId(productId);
    }

    /**
     * 
     */
    public Content() {
        super();
    }

    public String getName() {
        return extensions.getValue("1");
    }

    public String getLabel() {
        return extensions.getValue("2");
    }

    public int getPhysicalEntitlements() {
        return NumberUtils.toInt(extensions.getValue("3"), -1);
    }

    public int getFlexGuestEntitlements() {
        return NumberUtils.toInt(extensions.getValue("4"), -1);
    }

    public String getVendorID() {
        return extensions.getValue("5");
    }

    public String getDownloadURL() {
        return extensions.getValue("6");
    }

    public String getGPCKeyURL() {
        return extensions.getValue("7");
    }

    public boolean isEnabled() {
        return Boolean.valueOf(extensions.getValue("8"));
    }

}
