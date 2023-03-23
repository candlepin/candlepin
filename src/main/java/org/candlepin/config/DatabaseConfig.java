/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "db.config")
public interface DatabaseConfig {

    @WithName("db.config.in.operator.block.size")
    @WithDefault("15000")
    int inOperatorBlockSize();
    @WithName("db.config.case.operator.block.size")
    @WithDefault("100")
    int caseOperatorBlockSize();
    @WithName("db.config.batch.block.size")
    @WithDefault("500")
    int batchBlockSize();
    @WithName("db.config.query.parameter.limit")
    @WithDefault("32000")
    int queryParameterLimit();

}
