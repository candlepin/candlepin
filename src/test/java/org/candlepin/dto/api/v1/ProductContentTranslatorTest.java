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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Content;
import org.candlepin.model.ProductContent;


/**
 * Test suite for the ContentTranslator class
 */
public class ProductContentTranslatorTest extends
    AbstractTranslatorTest<ProductContent, ProductContentDTO, ProductContentTranslator> {

    protected ProductContentTranslator translator = new ProductContentTranslator();
    protected ContentTranslatorTest contentTranslatorTest = new ContentTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(new ContentTranslator(), Content.class, ContentDTO.class);
        modelTranslator.registerTranslator(this.translator, ProductContent.class, ProductContentDTO.class);
    }

    @Override
    protected ProductContentTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected ProductContent initSourceObject() {
        Content content = this.contentTranslatorTest.initSourceObject();

        ProductContent source = new ProductContent();
        source.setContent(content);
        source.setEnabled(true);

        return source;
    }

    @Override
    protected ProductContentDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new ProductContentDTO();
    }

    @Override
    protected void verifyOutput(ProductContent source, ProductContentDTO dto, boolean childrenGenerated) {
        if (source != null) {
            // This DTO does not have any nested objects, so we don't need to worry about the
            // childrenGenerated flag

            assertEquals(source.isEnabled(), dto.getEnabled());

            if (childrenGenerated) {
                this.contentTranslatorTest.verifyOutput(
                    source.getContent(), dto.getContent(), childrenGenerated);
            }
        }
        else {
            assertNull(dto);
        }
    }
}
