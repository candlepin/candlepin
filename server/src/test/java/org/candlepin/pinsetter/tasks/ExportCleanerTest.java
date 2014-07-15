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
package org.candlepin.pinsetter.tasks;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;


/**
 * ExportCleanerTest
 */
@RunWith(MockitoJUnitRunner.class)
public class ExportCleanerTest {

    @Mock private Config config;
    private ExportCleaner cleaner;

    @Before
    public void init() {
        cleaner = new ExportCleaner(config);
    }

    @Test
    public void execute() throws Exception {
        File baseDir = new File("/tmp/syncdirtest");
        baseDir.mkdir();
        try {
            File tmp1 = new File(baseDir.getAbsolutePath(), "test-dir-1");
            tmp1.mkdir();
            tmp1.setLastModified(Util.yesterday().getTime() - 1000);
            File tmp2 = new File(baseDir.getAbsolutePath(), "test-dir-2");
            tmp2.mkdir();
            tmp2.setLastModified(Util.yesterday().getTime() - 1000);
            File tmp3 = new File(baseDir.getAbsolutePath(), "test-dir-3");
            tmp3.mkdir();

            when(config.getString(eq(ConfigProperties.SYNC_WORK_DIR)))
                .thenReturn(baseDir.getPath());
            cleaner.execute(null);

            assert (!tmp1.exists());
            assert (!tmp2.exists());
            assert (tmp3.exists());
        }
        finally {
            FileUtils.deleteDirectory(baseDir);
        }
    }
}
