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

import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Launches JUnit as an ApplicationRunner
 **/
public class JUnitBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(JUnitBootstrap.class);

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Beginning test run");
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectPackage(this.getClass().getPackage().getName()))
            .filters(ClassNameFilter.includeClassNamePatterns(ClassNameFilter.STANDARD_INCLUDE_PATTERN))
            .build();

        Launcher launcher = LauncherFactory.create();

        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);

        launcher.execute(request);

        TestExecutionSummary summary = listener.getSummary();

        PrintWriter writer = new PrintWriter(System.out);
        summary.printTo(writer);
        summary.printFailuresTo(writer);

        JUnitSummaryEvent event = new JUnitSummaryEvent(summary);
        eventPublisher.publishEvent(event);
    }

    /**
     * Class that listens for JUnitSummaryEvents, totals all the failures, and then returns a
     * non-zero exit code if that total is greater than zero.
     */
    public static class JUnitFailureExitCodeGenerator implements ApplicationListener<JUnitSummaryEvent>,
        ExitCodeGenerator {

        // There should only really be one of these but doing this for future proofing.
        private List<TestExecutionSummary> summaries = new ArrayList<>();

        @Override
        public int getExitCode() {
            long failures = summaries.stream().mapToLong(TestExecutionSummary::getTotalFailureCount).sum();

            if (failures > 0) {
                System.out.println("TESTS FAILED");
                return 1;
            }
            return 0;
        }

        @Override
        public void onApplicationEvent(JUnitSummaryEvent event) {
            this.summaries.add(event.getSummary());
        }
    }

    /**
     * An event published that contains the results of a JUnit run.
     */
    public static class JUnitSummaryEvent extends ApplicationEvent {
        private TestExecutionSummary summary;
        public JUnitSummaryEvent(TestExecutionSummary source) {
            super(source);
            this.summary = source;
        }

        public TestExecutionSummary getSummary() {
            return summary;
        }
    }
}
