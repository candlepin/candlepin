/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.candlepin.config.Configuration;
import org.candlepin.controller.util.ContentPathBuilder;
import org.candlepin.controller.util.PromotedContent;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.pki.X509Extension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;


public class X509ExtensionUtilTest {
    private Configuration config;

    @BeforeEach
    public void init() {
        config = mock(Configuration.class);
    }

    @Test
    public void shouldWorkWithValidContentId() {
        X509ExtensionUtil util = new X509ExtensionUtil(config);

        Content content = new Content("123456")
            .setName("testcontent")
            .setType("yum")
            .setLabel("testlabel")
            .setVendor("testvendor")
            .setContentUrl("/content_path");

        List<ProductContent> contents = List.of(new ProductContent(new Product(), content, true));
        PromotedContent promotedContent = new PromotedContent(contentPathBuilder());
        Set<X509Extension> extensions = util
            .contentExtensions(contents, promotedContent, null, new Product());

        assertFalse(extensions.isEmpty());
    }

    private static ContentPathBuilder contentPathBuilder() {
        return ContentPathBuilder.from(new Owner(), List.of());
    }

    @ParameterizedTest
    @MethodSource("invalidContentIds")
    public void failsForInvalidContentIds(String contentId) {
        X509ExtensionUtil util = new X509ExtensionUtil(config);
        Content content = new Content(contentId)
            .setName("testcontent")
            .setType("yum")
            .setLabel("testlabel")
            .setVendor("testvendor");

        Product skuProduct = new Product();
        List<ProductContent> contents = List.of(new ProductContent(skuProduct, content, true));
        PromotedContent promotedContent = new PromotedContent(contentPathBuilder());

        assertThrows(IllegalArgumentException.class,
            () -> util.contentExtensions(contents, promotedContent, null, skuProduct));
    }

    public static Stream<Arguments> invalidContentIds() {
        return Stream.of(
            Arguments.of("123-456"),
            Arguments.of("-"),
            Arguments.of(" ,"),
            Arguments.of("test"),
            Arguments.of("123asd")
        );
    }
}
