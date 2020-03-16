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

import org.candlepin.dto.rules.v1.ComplianceStatusDTO;
import org.candlepin.jackson.ProductCachedSerializationModule;
import org.candlepin.model.Owner;
import org.candlepin.model.ProductCurator;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;


/**
 * RulesObjectMapperTest
 */
public class RulesObjectMapperTest {
    private RulesObjectMapper objMapper;
    private Map<String, Object> context;
    private Owner owner;

    @BeforeEach
    public void begin() {
        context = new HashMap<>();
        owner = new Owner("test");
        ProductCurator productCurator = Mockito.mock(ProductCurator.class);
        objMapper = new RulesObjectMapper(new ProductCachedSerializationModule(productCurator));
    }

    /*
     * Tests a bug found where consumer environment content is serialized without
     * an environment (as it would be a circular dep), resulting in a null environment
     * on the object and a very upset hashCode method.
     */
    @Test
    public void testComplianceStatusWithSourceConsumerInEnv() {
        InputStream is = this.getClass().getResourceAsStream("/json/compliancestatus-with-env.json");
        String json = Util.readFile(is);

        // Just need this to parse without error:
        ComplianceStatusDTO cs = objMapper.toObject(json, ComplianceStatusDTO.class);
    }

    /*
     * Tests a bug found where consumer environment content is serialized without
     * an environment (as it would be a circular dep), resulting in a null environment
     * on the object and a very upset hashCode method.
     */
    @Test
    public void testComplianceStatusWithSourceConsumerInEnvV2() {
        InputStream is = this.getClass().getResourceAsStream("/json/compliancestatus-with-env-v2.json");
        String json = Util.readFile(is);

        // Just need this to parse without error:
        ComplianceStatusDTO cs = objMapper.toObject(json, ComplianceStatusDTO.class);
    }
}
