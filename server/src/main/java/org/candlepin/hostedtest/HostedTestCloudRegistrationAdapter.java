/**
 * Copyright (c) 2009 - 2021 Red Hat, Inc.
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

import org.candlepin.service.CloudRegistrationAdapter;
import org.candlepin.service.exception.CloudRegistrationAuthorizationException;
import org.candlepin.service.exception.MalformedCloudRegistrationException;
import org.candlepin.service.model.CloudRegistrationInfo;
import org.candlepin.service.model.OwnerInfo;

import javax.inject.Inject;
import javax.inject.Singleton;



/**
 * The HostedTestProductServiceAdapter is a CloudRegistrationAdapter implementation backed by the
 * HostedTestDataStore upstream simulator.
 */
@Singleton
public class HostedTestCloudRegistrationAdapter implements CloudRegistrationAdapter {

    private final HostedTestDataStore datastore;

    @Inject
    public HostedTestCloudRegistrationAdapter(HostedTestDataStore datastore) {
        if (datastore == null) {
            throw new IllegalArgumentException("datastore is null");
        }

        this.datastore = datastore;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String resolveCloudRegistrationData(CloudRegistrationInfo cloudRegInfo)
        throws CloudRegistrationAuthorizationException, MalformedCloudRegistrationException {

        if (cloudRegInfo == null) {
            throw new MalformedCloudRegistrationException("No cloud registration information provided");
        }

        if (cloudRegInfo.getMetadata() == null) {
            throw new MalformedCloudRegistrationException(
                "No metadata provided with the cloud registration info");

        }

        // We don't care about the type or signature, just attempt to resolve the metadata to an
        // owner key
        OwnerInfo owner = this.datastore.getOwner(cloudRegInfo.getMetadata());
        return owner != null ? owner.getKey() : null;
    }

}
