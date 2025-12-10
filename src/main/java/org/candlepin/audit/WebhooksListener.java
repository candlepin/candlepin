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
package org.candlepin.audit;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.util.Util;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.DatatypeConverter;

/**
 * WebhooksListener
 */
public class WebhooksListener implements EventListener {
    private static Logger log = LoggerFactory.getLogger(WebhooksListener.class);
    private static final String HMAC_ALGO = "HmacSHA512";
    private static final String REQUEST_BODY_FORMAT = "{\"event\": %s}";
    private static final String SIGNATURE_HEADER = "X-Candlepin-Signature";

    private final HttpClient httpClient;
    private final Mac mac;
    private final URI ENDPOINT_URI;

    private String HMAC_KEY;

    @Inject
    public WebhooksListener(Configuration config) throws NoSuchAlgorithmException, InvalidKeyException {
        this.HMAC_KEY = Objects.requireNonNull(config.getString(ConfigProperties.WEBHOOKS_SHARED_SECRET));
        this.ENDPOINT_URI = URI.create(config.getString(ConfigProperties.WEBHOOKS_ENDPOINT_URL));
        this.mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(HMAC_KEY.getBytes(), HMAC_ALGO));
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void onEvent(Event event) {
        String eventJson;
        try {
            eventJson = Util.toJson(event);
        }
        catch (JsonProcessingException exception) {
            log.error("Event could not be converted to JSON; webhook will not be sent");
            return;
        }

        final byte[] signatureBytes = mac.doFinal(eventJson.getBytes(StandardCharsets.UTF_8));
        final String signature = DatatypeConverter.printHexBinary(signatureBytes).toLowerCase(Locale.US);
        final String requestBody = String.format(REQUEST_BODY_FORMAT, eventJson);

        HttpRequest request  = HttpRequest.newBuilder()
            .uri(ENDPOINT_URI)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .header(SIGNATURE_HEADER, signature)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return;
            }

            // This results in a retry but not sure it's valuable to do so at this point..
            throw new RuntimeException("Webhook client returned an error.");
        }
        catch (IOException | InterruptedException ex) {
            throw new RuntimeException("Could not reach or lost connection to webhook client.");
        }
    }
}
