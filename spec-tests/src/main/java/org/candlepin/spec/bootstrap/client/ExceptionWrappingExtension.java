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

package org.candlepin.spec.bootstrap.client;

import org.candlepin.ApiException;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extension providing more useful output from thrown {@link ApiException}.
 *
 * When test fails due to ApiException the output consists only of the exception name.
 * This extension provides additional output for such cases otherwise rethrows the exception.
 */
public class ExceptionWrappingExtension implements TestExecutionExceptionHandler,
    LifecycleMethodExecutionExceptionHandler {

    private static final Pattern PATTERN = Pattern.compile("displayMessage\" : \"(.*)\"");

    @Override
    public void handleBeforeAllMethodExecutionException(ExtensionContext context, Throwable throwable) {
        enhanceApiExceptionOutput(throwable);
    }

    @Override
    public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable throwable) {
        enhanceApiExceptionOutput(throwable);
    }

    @Override
    public void handleAfterEachMethodExecutionException(ExtensionContext context, Throwable throwable) {
        enhanceApiExceptionOutput(throwable);
    }

    @Override
    public void handleAfterAllMethodExecutionException(ExtensionContext context, Throwable throwable) {
        enhanceApiExceptionOutput(throwable);
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) {
        enhanceApiExceptionOutput(throwable);
    }

    /**
     * Method improves output provided by ApiException. There are three cases we need to handle.
     * - Request failed to reach the server (such as connection failure)
     * - Request reached the server but result is fail
     * - Rethrow unrelated exceptions
     *
     * @param throwable exception to enhance
     */
    private void enhanceApiExceptionOutput(Throwable throwable) {
        if (throwable instanceof ApiException) {
            ApiException exception = (ApiException) throwable;
            if (exception.getCause() != null) {
                // Handle failed request
                throw new RuntimeException(exception.getMessage(), exception);
            }
            else {
                // Handle fail response
                String responseBody = exception.getResponseBody();
                int responseCode = exception.getCode();
                throw new RuntimeException(
                    "" + responseCode + ": " + extractMessage(responseBody), throwable);
            }
        }
        // Handle unrelated exceptions
        else if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        else {
            throw new RuntimeException(throwable);
        }
    }

    private String extractMessage(String response) {
        Matcher matcher = PATTERN.matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        }
        else {
            throw new RuntimeException("Could not extract an error message from: " + response);
        }
    }

}
