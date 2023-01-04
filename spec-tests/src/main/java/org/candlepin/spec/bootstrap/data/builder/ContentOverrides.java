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

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentOverrideDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;


/**
 * Class providing factory functions for ContentOverrideDTO instances.
 */
public class ContentOverrides {

    private ContentOverrides() {
        throw new UnsupportedOperationException();
    }

    public static ContentOverrideDTO random() {
        return new ContentOverrideDTO()
            .name(StringUtil.random("name").toLowerCase())
            .value(StringUtil.random("value"))
            .contentLabel(StringUtil.random("contentlabel"));
    }

    public static ContentOverrideDTO withValue(ConsumerDTO consumer) {
        return random().value(consumer.getUuid());
    }

}
