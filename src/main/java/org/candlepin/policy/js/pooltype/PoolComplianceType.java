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
package org.candlepin.policy.js.pooltype;

import org.xnap.commons.i18n.I18n;

/**
 * PoolType
 *
 * A class for holding and translating the
 * response from PoolTypeRules.
 */
public class PoolComplianceType {

    private String rawPoolType;
    private String poolType;

    public String getRawPoolType() {
        return rawPoolType;
    }

    public void setRawPoolType(String rawPoolType) {
        this.rawPoolType = rawPoolType;
    }

    public String getPoolType() {
        return poolType;
    }

    public void translatePoolType(I18n i18n) {
        this.poolType = translateType(this.rawPoolType, i18n);
    }

    private String translateType(String rawType, I18n i18n) {
        if (rawType == null ||
                rawType.equals("") ||
                rawType.equals("unknown")) {
            return i18n.tr("Other");
        }
        if (rawType.equals("instance based")) {
            return i18n.tr("Instance Based");
        }
        if (rawType.equals("stackable")) {
            return i18n.tr("Stackable");
        }
        if (rawType.equals("multi entitlement")) {
            return i18n.tr("Multi-Entitleable");
        }
        if (rawType.equals("standard")) {
            return i18n.tr("Standard");
        }
        if (rawType.equals("unique stackable")) {
            return i18n.tr("Stackable only with other subscriptions");
        }
        // If we do not have a matching key, return the original
        return rawType;
    }
}
