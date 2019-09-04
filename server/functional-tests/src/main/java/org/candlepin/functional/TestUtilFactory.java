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

import org.candlepin.client.ApiClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/** Factory to generate TestUtil instances */
@Component
public class TestUtilFactory extends AbstractFactoryBean<TestUtil> {

    private final TestManifest testManifest;
    private ApiClient adminApiClient;

    @Autowired
    public TestUtilFactory(TestManifest testManifest,
        @Qualifier("adminApiClient") ApiClient adminApiClient) {
        this.testManifest = testManifest;
        this.adminApiClient = adminApiClient;
    }

    @Override
    public Class<?> getObjectType() {
        return TestUtil.class;
    }

    @NonNull
    @Override
    protected TestUtil createInstance() throws Exception {
        return new TestUtil(adminApiClient, testManifest);
    }

    public TestUtil createInstance(ApiClientBuilder builder) {
        return new TestUtil(builder.build(), testManifest);
    }

    public TestUtil createInstance(ApiClient apiClient) {
        return new TestUtil(apiClient, testManifest);
    }
}
