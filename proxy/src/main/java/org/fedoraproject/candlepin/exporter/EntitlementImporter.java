/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.exporter;

import java.io.IOException;
import java.io.Reader;
import java.math.BigInteger;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;

/**
 * EntitlementImporter
 */
public class EntitlementImporter {
    public Entitlement importObject(
        ObjectMapper mapper, Reader reader, Map<BigInteger, EntitlementCertificate> certs) 
        throws IOException {
        
        return mapper.readValue(reader, EntitlementDto.class).entitlement(certs);
    }
}
