/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Test suite for the ProductCertificateDTO class
 */
public class ProductCertificateDTOTest extends AbstractDTOTest<ProductCertificateDTO> {

    protected Map<String, Object> values;
    protected static ArrayList<String> skipMethodList = new ArrayList<>(Arrays.asList("getSerial"));


    public ProductCertificateDTOTest() {
        super(ProductCertificateDTO.class);

        ProductDTO pdto = new ProductDTO();
        pdto.setId("test-prod-id");
        this.values = new HashMap<>();
        this.values.put("Id", "test-id");
        this.values.put("Key", "test-key");
        this.values.put("Cert", "test-cert");
        this.values.put("Product", pdto);
        this.values.put("Created", new Date());
        this.values.put("Updated", new Date());

    }

    /**
     * Method to skip testing any field in any DTO class by
     * not including their accessor method for testing.
     *
     * @param methodName getter method of field.
     * @return boolean
     */
    @Override
    protected boolean skipMethod(String methodName) {
        return skipMethodList.contains(methodName) ? true : false;
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
