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
package org.candlepin.hostedtest;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.TestingModules;
import org.candlepin.auth.CloudRegistrationData;
import org.candlepin.service.CloudRegistrationAdapter;
import org.candlepin.service.exception.cloudregistration.CloudRegistrationMalformedDataException;
import org.candlepin.service.model.CloudRegistrationInfo;
import org.candlepin.testext.hostedtest.HostedTestCloudRegistrationAdapter;
import org.candlepin.testext.hostedtest.HostedTestDataStore;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xnap.commons.i18n.I18n;



public class HostedTestCloudRegistrationAdapterTest {

    CloudRegistrationAdapter adapter;
    I18n i18n;

    @BeforeEach
    public void init() {
        Injector injector = Guice.createInjector(
            new TestingModules.ServletEnvironmentModule());
        i18n = injector.getInstance(I18n.class);
        HostedTestDataStore ds = new HostedTestDataStore();
        adapter = new HostedTestCloudRegistrationAdapter(ds);
    }

    @Test
    public void testResolveCloudRegistrationData() {
        CloudRegistrationInfo nullInfo = null;
        assertThrows(CloudRegistrationMalformedDataException.class,
            () -> adapter.resolveCloudRegistrationData(nullInfo));

        CloudRegistrationInfo nullMeta = new CloudRegistrationData();
        assertThrows(CloudRegistrationMalformedDataException.class,
            () -> adapter.resolveCloudRegistrationData(nullMeta));
    }
}
