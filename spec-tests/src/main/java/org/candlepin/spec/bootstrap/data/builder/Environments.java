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

import org.candlepin.dto.api.client.v1.EnvironmentDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

/**
 * Class providing factory functions for EnvironmentDTO instances.
 */
public class Environments {

    private Environments() {
        throw new UnsupportedOperationException();
    }

    public static EnvironmentDTO random() {
        return new EnvironmentDTO()
            .id(StringUtil.random("id-"))
            .name(StringUtil.random("name-"))
            .type(StringUtil.random("type-"))
            .description(StringUtil.random("desc-"))
            .contentPrefix(StringUtil.random("/"));
    }

}
