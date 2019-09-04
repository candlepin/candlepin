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

import org.junit.jupiter.api.DisplayNameGeneration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Apply this annotation to a class to have it load the application context defined in
 * {@link FunctionalTestConfiguration}.  FunctionalTestConfiguration also has ComponentScan enabled
 * so it will pull in everything else tagged as a Component.
 */

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@DisplayNameGeneration(CamelCaseDisplayNameGenerator.class)
@SpringJUnitConfig(classes = FunctionalTestConfiguration.class)
@TestExecutionListeners(value = {CleanupListener.class},
    inheritListeners = true, mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public @interface FunctionalTestCase {

}
