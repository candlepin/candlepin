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

import org.candlepin.functional.JUnitBootstrap.JUnitFailureExitCodeGenerator;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Bootstrapper for our functional tests.  This class is a little different from a normal Spring Boot
 * application. It does not do a component scan nor does it enable auto-configuration.  Instead, it just
 * creates a single ApplicationRunner bean, {@link JUnitBootstrap}, that invokes JUnit.
 *
 * The JUnit tests should all be annotated with {@link FunctionalTestCase}.  That annotation will create a
 * Spring ApplicationContext defined by {@link FunctionalTestConfiguration}.  FunctionalTestIntegration also
 * has component-scan enabled, so other classes annotated with @Component (or its kin) will also be loaded
 * into the BeanFactory.
 *
 * The main point to remember is this: This test harness has <strong>2</strong> ApplicationContexts.  One
 * is defined by this class and should only be the beans necessary to get JUnit up off the ground.  The
 * other is created for each test by virtue of the FunctionalTestCase class and consists of beans defined
 * in FunctionalTestConfiguration <strong>and every class annotated with @Component (or equivalent)</strong>
 *
 * */
@SpringBootConfiguration
@SuppressWarnings("checkstyle:hideutilityclassconstructor")
public class BootApplication {
    @Bean
    public ApplicationRunner junitBootstrap() {
        return new JUnitBootstrap();
    }

    /**
     * SpringApplication.exit will automatically load beans of type ExitCodeGenerator, ask each one to
     * generate an exit code, and then pick the highest value code from the results.
     * @return an ExitCodeGenerator that picks the exit code based on the JUnit results
     */
    @Bean
    public ExitCodeGenerator failureExitCodeGenerator() {
        return new JUnitFailureExitCodeGenerator();
    }

    public static void main(String[] args) {
        /* NB: These arguments will not make it to the ApplicationContext that the tests are using.
         * Instead, define properties using the JVM -D syntax.  For example
         * "-Dfunctional-tests.client.debug=true" will display debug information
         */
        System.exit(SpringApplication.exit(
            SpringApplication.run(BootApplication.class, args)
        ));
    }
}
