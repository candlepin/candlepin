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
import io.smallrye.config.WithName;

import java.util.List;

@ConfigMapping(prefix = "candlepin.audit")
public interface AuditConfig {

    @WithName("listeners")
    List<String> listeners();
    @WithName("filter.enabled")
    boolean filterEnabled();
    @WithName("filter.donotfilter")
    List<String> doNotFilter();
    @WithName("filter.dofilter")
    List<String> doFilter();
    @WithName("filter.policy")
    String defaultPolicy();

}
