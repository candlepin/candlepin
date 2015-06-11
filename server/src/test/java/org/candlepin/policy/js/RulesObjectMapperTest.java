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

import static org.junit.Assert.assertFalse;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RulesObjectMapperTest
 */
public class RulesObjectMapperTest {

    private RulesObjectMapper objMapper = RulesObjectMapper.instance();
    private Map<String, Object> context;
    private Owner owner;

    @Before
    public void begin() {
        context = new HashMap<String, Object>();
        owner = new Owner("test");
    }

    @Test
    public void filterConsumerIdCert() {
        Consumer c = new Consumer();
        c.setType(new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM));
        IdentityCertificate cert = new IdentityCertificate();
        cert.setCert("FILTERMEPLEASE");
        cert.setKey("KEY");
        c.setIdCert(cert);

        context.put("consumer", c);
        String output = objMapper.toJsonString(context);
        assertFalse(output.contains("FILTERMEPLEASE"));
    }

    @Test
    public void filterEntitlementConsumer() {
        Entitlement e = new Entitlement();
        Consumer c = new Consumer();
        IdentityCertificate cert = new IdentityCertificate();
        cert.setCert("FILTERMEPLEASE");
        cert.setKey("KEY");
        c.setIdCert(cert);
        e.setConsumer(c);

        context.put("entitlement", e);
        String output = objMapper.toJsonString(context);
        assertFalse(output.contains("consumer"));
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
    public void filterTimestampsOffAttributes() {
        Product prod = TestUtil.createProduct(owner);
        Pool p = new Pool();
        p.setProduct(prod);

        ProductAttribute prodAttr = new ProductAttribute("a", "1");
        prodAttr.setCreated(new Date());
        prodAttr.setUpdated(new Date());
        prod.addAttribute(prodAttr);

        PoolAttribute poolAttr = new PoolAttribute("a", "1");
        poolAttr.setCreated(new Date());
        poolAttr.setUpdated(new Date());
        p.addAttribute(poolAttr);

        context.put("pool", p);

        String output = objMapper.toJsonString(context);
        // Shouldn't see timestamps:
        assertFalse(output.contains("created"));
        assertFalse(output.contains("updated"));

        // Shouldn't see a productId:
        assertFalse(output.contains("PRODID"));
    }

    /*
     * Tests a bug found where consumer environment content is serialized without
     * an environment (as it would be a circular dep), resulting in a null environment
     * on the object and a very upset hashCode method.
     */
    @Test
    public void testComplianceStatusWithSourceConsumerInEnv() {
        InputStream is = this.getClass().getResourceAsStream(
            "/json/compliancestatus-with-env.json");
        String json = Util.readFile(is);

        // Just need this to parse without error:
        ComplianceStatus cs = objMapper.toObject(json, ComplianceStatus.class);
    }

}
