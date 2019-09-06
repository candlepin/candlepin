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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Listener to delete owners, users, roles, etc. created during testing.
 */
public class CleanupListener extends AbstractTestExecutionListener {
    private static final Logger log = LoggerFactory.getLogger(CleanupListener.class);

    @Override
    public void afterTestMethod(TestContext testContext) throws Exception {
        TestManifest myManifest = testContext.getApplicationContext().getBean(TestManifest.class);

        FunctionalTestProperties properties =
            testContext.getApplicationContext().getBean(FunctionalTestProperties.class);

        if (properties.isCleanUp()) {
            log.debug("Begin Candlepin clean-up");
            myManifest.clearManifest();
            log.debug("Completed Candlepin clean-up");
        }
        else {
            log.debug("No clean-up performed");
        }
    }
}
