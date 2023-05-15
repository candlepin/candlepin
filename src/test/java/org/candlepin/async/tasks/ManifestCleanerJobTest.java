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
package org.candlepin.async.tasks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.ManifestManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;


public class ManifestCleanerJobTest {

    private Path manifestDir;
    private ManifestManager manifestManager;
    private DevConfig config;

    @BeforeEach
    public void setUp() throws IOException {
        this.manifestDir = Files.createTempDirectory("test_sync-");
        this.manifestManager = mock(ManifestManager.class);

        this.config = TestConfig.defaults();
        this.config.setProperty(ConfigProperties.SYNC_WORK_DIR, manifestDir.toString());
    }

    private ManifestCleanerJob createJobInstance() {
        return new ManifestCleanerJob(this.config, this.manifestManager);
    }

    private File createManifest(Path dir, String name, long lastModifiedTime) throws IOException {
        Path manifest = Files.createTempDirectory(dir, name + '-');
        File dummyFile = Files.createTempFile(manifest, name, "dummy").toFile();

        File manifestFile = manifest.toFile();
        manifestFile.setLastModified(lastModifiedTime);

        return manifestFile;
    }

    private void setMaxAgeConfig(int maxAgeInMinutes) {
        String cfg = ConfigProperties.jobConfig(ManifestCleanerJob.JOB_KEY,
            ManifestCleanerJob.CFG_MAX_AGE_IN_MINUTES);

        this.config.setProperty(cfg, String.valueOf(maxAgeInMinutes));
    }

    private long subtractMinutes(long baseTime, int minutes) {
        return baseTime - minutes * 60 * 1000;
    }

    private static Stream<Arguments> maxAgeProvider() {
        return Stream.of(
            Arguments.of(ManifestCleanerJob.DEFAULT_MAX_AGE_IN_MINUTES),
            Arguments.of(60),
            Arguments.of(1));
    }

    @ParameterizedTest
    @MethodSource("maxAgeProvider")
    public void testStandardExecution(int maxAge) throws JobExecutionException, IOException {
        long now = System.currentTimeMillis();
        this.setMaxAgeConfig(maxAge);

        long old = this.subtractMinutes(now, maxAge << 1);
        long cur = this.subtractMinutes(now, maxAge >> 1);

        File manifest1 = this.createManifest(this.manifestDir, "manifest1", old);
        File manifest2 = this.createManifest(this.manifestDir, "manifest2", cur);
        File manifest3 = this.createManifest(this.manifestDir, "manifest3", old);

        JobExecutionContext context = mock(JobExecutionContext.class);
        ManifestCleanerJob job = this.createJobInstance();
        job.execute(context);

        assertFalse(manifest1.exists());
        assertTrue(manifest2.exists());
        assertFalse(manifest3.exists());

        verify(this.manifestManager, times(1)).cleanup(eq(maxAge));
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "-50" })
    public void testBadAgeConfig(int maxAge) {
        this.setMaxAgeConfig(maxAge);

        JobExecutionContext context = mock(JobExecutionContext.class);
        ManifestCleanerJob job = this.createJobInstance();
        assertThrows(JobExecutionException.class, () -> job.execute(context));
    }
}
