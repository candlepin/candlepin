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

import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.NestedOwnerDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

/**
 * Class meant to provide fully randomized instances of consumer.
 *
 * Individual tests can then modify the instance according to their needs.
 */
public final class Consumers {

    private Consumers() {
        throw new UnsupportedOperationException();
    }

    public static ConsumerDTO random(OwnerDTO owner) {
        return random(owner != null ? Owners.toNested(owner) : null);
    }

    public static ConsumerDTO random(OwnerDTO owner, ConsumerTypes type) {
        return random(owner)
            .type(type.value());
    }

    private static ConsumerDTO random(NestedOwnerDTO owner) {
        // TODO: fill in rest of the data
        return new ConsumerDTO()
            .name(StringUtil.random("test_consumer-", 8, StringUtil.CHARSET_NUMERIC_HEX))
            .owner(owner)
            .type(ConsumerTypes.System.value())
            .putFactsItem("system.certificate_version", "3.3");
    }

}
