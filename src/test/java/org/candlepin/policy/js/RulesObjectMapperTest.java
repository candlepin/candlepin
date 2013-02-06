/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.policy.js;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertFalse;

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.ProductAttribute;
import org.junit.Before;
import org.junit.Test;

/**
 * RulesObjectMapperTest
 */
public class RulesObjectMapperTest {

    private RulesObjectMapper objMapper = RulesObjectMapper.instance();
    private Map<String, Object> context;

    @Before
    public void begin() {
        context = new HashMap<String, Object>();
    }

    @Test
    public void filterConsumerIdCert() {
        Consumer c = new Consumer();
        IdentityCertificate cert = new IdentityCertificate();
        cert.setCert("FILTERMEPLEASE");
        cert.setKey("KEY");
        c.setIdCert(cert);

        context.put("consumer", c);
        String output = objMapper.toJsonString(context);
        assertFalse(output.contains("FILTERMEPLEASE"));
    }

    @Test
    public void filterEntitlementCert() {
        List<Entitlement> allEnts = new LinkedList<Entitlement>();

        Entitlement e = new Entitlement();
        Set<EntitlementCertificate> entCerts = new HashSet<EntitlementCertificate>();
        EntitlementCertificate cert = new EntitlementCertificate();
        cert.setCert("FILTERME");
        cert.setKey("FILTERME");
        entCerts.add(cert);
        e.setCertificates(entCerts);

        allEnts.add(e);

        context.put("entitlements", allEnts);
        String output = objMapper.toJsonString(context);
        assertFalse(output.contains("FILTERME"));
    }

    @Test
    public void filterTimestamps() {
        ProductAttribute pa = new ProductAttribute("a", "1");
        pa.setCreated(new Date());
        pa.setUpdated(new Date());
        context.put("productAttribute", pa);

        String output = objMapper.toJsonString(context);
        assertFalse(output.contains("created"));
        assertFalse(output.contains("updated"));
    }

}
