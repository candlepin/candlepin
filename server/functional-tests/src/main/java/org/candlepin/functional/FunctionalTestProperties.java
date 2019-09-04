/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.functional;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** POJO used to hold application settings */
@ConfigurationProperties(prefix = "functional-tests")
public class FunctionalTestProperties {
    private boolean verbose;
    private boolean cleanUp;

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isCleanUp() {
        return cleanUp;
    }

    public void setCleanUp(boolean cleanUp) {
        this.cleanUp = cleanUp;
    }
}
