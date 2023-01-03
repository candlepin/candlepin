/**
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

package org.candlepin.spec.bootstrap.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.dto.api.client.v1.ComplianceReasonDTO;
import org.candlepin.dto.api.client.v1.ComplianceStatusDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ComplianceAssert extends AbstractAssert<ComplianceAssert, ComplianceStatusDTO> {

    public ComplianceAssert(ComplianceStatusDTO status) {
        super(status, ComplianceAssert.class);
    }

    public static ComplianceAssert assertThatCompliance(ComplianceStatusDTO actual) {
        return new ComplianceAssert(actual);
    }

    public ComplianceAssert isValid() {
        assertStatus("valid");
        return this;
    }

    public ComplianceAssert isPartial() {
        assertStatus("partial");
        return this;
    }

    public ComplianceAssert isInvalid() {
        assertStatus("invalid");
        return this;
    }

    public ComplianceAssert isCompliant() {
        if (!actual.getCompliant()) {
            failWithMessage("Expected compliance to be compliant but it was not");
        }
        return this;
    }

    public ComplianceAssert isNotCompliant() {
        if (actual.getCompliant()) {
            failWithMessage("Expected compliance to be not compliant but it was");
        }
        return this;
    }

    public ComplianceAssert hasCompliantProducts(ProductDTO... products) {
        List<String> productIds = Arrays.stream(products)
            .map(ProductDTO::getId)
            .collect(Collectors.toList());

        assertThat(actual.getCompliantProducts())
            .containsOnlyKeys(productIds);

        return this;
    }

    public ComplianceAssert hasPartiallyCompliantProducts(ProductDTO... products) {
        List<String> productIds = Arrays.stream(products)
            .map(ProductDTO::getId)
            .collect(Collectors.toList());

        assertThat(actual.getPartiallyCompliantProducts())
            .containsOnlyKeys(productIds);

        return this;
    }

    public ComplianceAssert hasReasons(int size) {
        assertThat(actual.getReasons())
            .hasSize(size);

        return this;
    }

    @SafeVarargs
    public final ComplianceAssert hasNotCoveredReason(Map.Entry<String, String>... attributes) {
        Map<String, String> expectedAttributes = toMap(attributes);
        String expectedMessage = "Not supported by a valid subscription.";

        assertHasReasonWithAttributes("NOTCOVERED", expectedMessage, expectedAttributes);

        return this;
    }

    @SafeVarargs
    public final ComplianceAssert hasArchReason(Map.Entry<String, String>... attributes) {
        Map<String, String> expectedAttributes = toMap(attributes);
        String expectedMessage = String.format("Supports architecture %s but the system is %s.",
            expectedAttributes.get(ReasonAttributes.Covered.key()),
            expectedAttributes.get(ReasonAttributes.Has.key()));

        assertHasReasonWithAttributes("ARCH", expectedMessage, expectedAttributes);

        return this;
    }

    @SafeVarargs
    public final ComplianceAssert hasVcpuReason(Map.Entry<String, String>... attributes) {
        Map<String, String> expectedAttributes = toMap(attributes);
        String expectedMessage = String.format("Only supports %s of %s vCPUs.",
            expectedAttributes.get(ReasonAttributes.Covered.key()),
            expectedAttributes.get(ReasonAttributes.Has.key()));

        assertHasReasonWithAttributes("VCPU", expectedMessage, expectedAttributes);

        return this;
    }

    @SafeVarargs
    public final ComplianceAssert hasCoresReason(Map.Entry<String, String>... attributes) {
        Map<String, String> expectedAttributes = toMap(attributes);
        String expectedMessage = String.format("Only supports %s of %s cores.",
            expectedAttributes.get(ReasonAttributes.Covered.key()),
            expectedAttributes.get(ReasonAttributes.Has.key()));

        assertHasReasonWithAttributes("CORES", expectedMessage, expectedAttributes);

        return this;
    }

    @SafeVarargs
    public final ComplianceAssert hasSocketsReason(Map.Entry<String, String>... attributes) {
        Map<String, String> expectedAttributes = toMap(attributes);
        String expectedMessage = String.format("Only supports %s of %s sockets.",
            expectedAttributes.get(ReasonAttributes.Covered.key()),
            expectedAttributes.get(ReasonAttributes.Has.key()));

        assertHasReasonWithAttributes("SOCKETS", expectedMessage, expectedAttributes);

        return this;
    }

    @SafeVarargs
    public final ComplianceAssert hasRamReason(Map.Entry<String, String>... attributes) {
        Map<String, String> expectedAttributes = toMap(attributes);
        String expectedMessage = String.format("Only supports %sGB of %sGB of RAM.",
            expectedAttributes.get(ReasonAttributes.Covered.key()),
            expectedAttributes.get(ReasonAttributes.Has.key()));

        assertHasReasonWithAttributes("RAM", expectedMessage, expectedAttributes);

        return this;
    }

    @NotNull
    private static Map<String, String> toMap(Map.Entry<String, String>[] attributes) {
        return Arrays.stream(attributes)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @SuppressWarnings("indentation")
    private ComplianceAssert assertHasReasonWithAttributes(String reasonKey, String expectedMessage,
        Map<String, String> expectedAttributes) {

        assertThat(actual.getReasons())
            .filteredOn(reason -> reason.getKey().equals(reasonKey))
            .singleElement()
            .satisfies(reason -> assertThat(reason)
                .returns(expectedMessage, ComplianceReasonDTO::getMessage)
                .extracting(ComplianceReasonDTO::getAttributes,
                    Assertions.as(InstanceOfAssertFactories.map(String.class, String.class)))
                .containsAllEntriesOf(expectedAttributes));

        return this;
    }

    private void assertStatus(String partial) {
        if (!partial.equals(actual.getStatus())) {
            failWithMessage("Expected compliance to be: %s but was: %s", partial, actual.getStatus());
        }
    }

}
