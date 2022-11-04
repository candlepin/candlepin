/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

import java.util.Map;

public enum Facts {
    MultiEntitlement("multi-entitlement"),
    UnameMachine("uname.machine"),
    VirtIsGuest("virt.is_guest"),
    VirtLimit("virt_limit"),
    VirtUuid("virt.uuid");

    private final String key;

    Facts(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    public Map.Entry<String, String> withValue(String value) {
        return Map.entry(this.key, value);
    }

}
