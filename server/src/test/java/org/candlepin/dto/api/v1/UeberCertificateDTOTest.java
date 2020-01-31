/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.AbstractDTOTest;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;



/**
 * Test suite for the UeberCertificateDTO class
 */
public class UeberCertificateDTOTest extends AbstractDTOTest<UeberCertificateDTO> {

    protected Map<String, Object> values;

    public UeberCertificateDTOTest() {
        super(UeberCertificateDTO.class);

        CertificateSerialDTO serial = new CertificateSerialDTO();
        serial.setId(123L);
        serial.setSerial(BigInteger.TEN);
        serial.setExpiration(new Date());
        serial.setCollected(true);
        serial.setRevoked(true);

        OwnerDTO owner = new OwnerDTO();
        owner.setId("owner_id");
        owner.setKey("owner_key");
        owner.setDisplayName("owner_name");
        owner.setContentPrefix("content_prefix");
        owner.setDefaultServiceLevel("service_level");
        owner.setLogLevel("log_level");
        owner.setAutobindDisabled(true);
        owner.setContentAccessMode("content_access_mode");
        owner.setContentAccessModeList("content_access_mode_list");

        this.values = new HashMap<>();
        this.values.put("Id", "test-id");
        this.values.put("Key", "test-key");
        this.values.put("Cert", "test-cert");
        this.values.put("Serial", serial);
        this.values.put("Owner", owner);
        this.values.put("Created", new Date());
        this.values.put("Updated", new Date());
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
