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

package org.candlepin.auth;

import org.candlepin.dto.AbstractDTOTest;

import java.util.HashMap;
import java.util.Map;

class CloudRegistrationDataTest extends AbstractDTOTest<CloudRegistrationData> {

    protected Map<String, Object> values;

    public CloudRegistrationDataTest() {
        super(CloudRegistrationData.class);

        this.values = new HashMap<>();
        this.values.put("Type", "test_type");
        this.values.put("Metadata", "test_metadata");
        this.values.put("Signature", "test_signature");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getInputValueForMutator(String field) {
        return this.values.get(field);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getOutputValueForAccessor(String field, Object input) {
        // Nothing to do here
        return input;
    }
}
