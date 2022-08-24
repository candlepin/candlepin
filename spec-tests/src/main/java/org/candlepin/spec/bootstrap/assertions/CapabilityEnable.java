/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.client.ApiClients;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.AnnotatedElement;
import java.util.Optional;
import java.util.Set;

@SuppressWarnings("unused")
public class CapabilityEnable implements ExecutionCondition {

    private static final Set<String> CAPABILITIES;

    static {
        try {
            CAPABILITIES = ApiClients.admin().status().status().getManagerCapabilities();
        }
        catch (ApiException e) {
            throw new IllegalStateException("Unable to determine Candlepin's capabilities!", e);
        }
        if (CAPABILITIES == null) {
            throw new IllegalStateException("Unable to determine Candlepin's capabilities!");
        }
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        OnlyWithCapability capability = null;
        Optional<AnnotatedElement> annotation = context.getElement();

        if (annotation.isPresent()) {
            capability = annotation.get().getAnnotation(OnlyWithCapability.class);
        }

        if (annotation.isEmpty() || capability == null) {
            return enabledByDefault();
        }

        if (capability.value() == null) {
            throw new IllegalArgumentException("Capability name can't be null!");
        }
        return CAPABILITIES.contains(capability.value()) ?
            ConditionEvaluationResult.enabled("Capability is set.") :
            ConditionEvaluationResult.disabled("Capability: "
                .concat(capability.value()).concat(" is not set!"));
    }

    private ConditionEvaluationResult enabledByDefault() {
        return ConditionEvaluationResult.enabled("Enabled by default. Annotation is not present.");
    }
}
