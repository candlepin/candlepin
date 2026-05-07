/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.junit;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xnap.commons.i18n.I18n;

import jakarta.inject.Inject;


@ExtendWith(GuiceExtension.class)
public class GuiceExtensionTest {

    @Inject
    private I18n i18n;

    @Test
    void testPostProcessTestInstanceInjectsAnnotatedFields() {
        assertThat(this.i18n).isNotNull();
    }

    @Test
    void testGetInjectorReturnsSameSharedInstanceAcrossCalls() {
        Injector injector1 = GuiceExtension.getInjector();
        Injector injector2 = GuiceExtension.getInjector();

        assertThat(injector1)
            .isSameAs(injector2);
    }

}
