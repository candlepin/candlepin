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
package org.candlepin.pki.certs;

import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.dto.EntitlementBody;
import org.candlepin.model.dto.Order;
import org.candlepin.model.dto.Service;
import org.candlepin.model.dto.TinySubscription;
import org.candlepin.util.Util;
import org.candlepin.util.X509Util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Objects;
import java.util.zip.DeflaterOutputStream;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;


@Singleton
public class EntitlementPayloadGenerator extends X509Util {
    private static final Logger log = LoggerFactory.getLogger(EntitlementPayloadGenerator.class);

    private final ObjectMapper mapper;

    @Inject
    public EntitlementPayloadGenerator(
        @Named("X509V3ExtensionUtilObjectMapper") ObjectMapper objectMapper) {
        this.mapper = Objects.requireNonNull(objectMapper);
    }

    public byte[] generate(List<org.candlepin.model.dto.Product> productModels,
        String consumerUuid, Pool pool, Integer quantity) {

        EntitlementBody map = createEntitlementBody(productModels, consumerUuid, pool, quantity);

        String json = toJson(map);
        return processPayload(json);
    }

    private EntitlementBody createEntitlementBody(List<org.candlepin.model.dto.Product> productModels,
        String consumerUuid, Pool pool, Integer quantity) {

        EntitlementBody toReturn = new EntitlementBody();
        toReturn.setConsumer(consumerUuid);
        toReturn.setQuantity(quantity);
        toReturn.setSubscription(createSubscription(pool));
        toReturn.setOrder(createOrder(pool));
        toReturn.setProducts(productModels);
        toReturn.setPool(createPool(pool));

        return toReturn;
    }

    private TinySubscription createSubscription(Pool pool) {
        TinySubscription toReturn = new TinySubscription();
        Product product = pool.getProduct();

        toReturn.setSku(product.getId());
        toReturn.setName(product.getName());

        String warningPeriod = product.getAttributeValue(Product.Attributes.WARNING_PERIOD);
        if (StringUtils.isNotBlank(warningPeriod)) {
            // only included if not the default value of 0
            if (!warningPeriod.equals("0")) {
                toReturn.setWarning(Integer.valueOf(warningPeriod));
            }
        }

        String socketLimit = product.getAttributeValue(Product.Attributes.SOCKETS);
        if (StringUtils.isNotBlank(socketLimit)) {
            toReturn.setSockets(Integer.valueOf(socketLimit));
        }

        String ramLimit = product.getAttributeValue(Product.Attributes.RAM);
        if (StringUtils.isNotBlank(ramLimit)) {
            toReturn.setRam(Integer.valueOf(ramLimit));
        }

        String coreLimit = product.getAttributeValue(Product.Attributes.CORES);
        if (StringUtils.isNotBlank(coreLimit)) {
            toReturn.setCores(Integer.valueOf(coreLimit));
        }

        String management = product.getAttributeValue(Product.Attributes.MANAGEMENT_ENABLED);
        if (StringUtils.isNotBlank(management)) {
            // only included if not the default value of false
            if (management.equalsIgnoreCase("true") || management.equalsIgnoreCase("1")) {
                toReturn.setManagement(Boolean.TRUE);
            }
        }

        String stackingId = product.getAttributeValue(Product.Attributes.STACKING_ID);
        if (StringUtils.isNotBlank(stackingId)) {
            toReturn.setStackingId(stackingId);
        }

        String virtOnly = pool.getAttributeValue(Product.Attributes.VIRT_ONLY);
        if (StringUtils.isNotBlank(virtOnly)) {
            // only included if not the default value of false
            boolean vo = virtOnly.equalsIgnoreCase("true") ||
                virtOnly.equalsIgnoreCase("1");
            if (vo) {
                toReturn.setVirtOnly(vo);
            }
        }

        toReturn.setService(createService(pool));

        String usage = product.getAttributeValue(Product.Attributes.USAGE);
        if (StringUtils.isNotBlank(usage)) {
            toReturn.setUsage(usage);
        }

        String roles = product.getAttributeValue(Product.Attributes.ROLES);
        if (StringUtils.isNotBlank(roles)) {
            toReturn.setRoles(Util.toList(roles));
        }

        String addons = product.getAttributeValue(Product.Attributes.ADDONS);
        if (StringUtils.isNotBlank(addons)) {
            toReturn.setAddons(Util.toList(addons));
        }
        return toReturn;
    }

    private Service createService(Pool pool) {
        if (pool.getProduct().getAttributeValue(Product.Attributes.SUPPORT_LEVEL) == null &&
            pool.getProduct().getAttributeValue(Product.Attributes.SUPPORT_TYPE) == null) {
            return null;
        }
        Service toReturn = new Service();
        toReturn.setLevel(pool.getProduct().getAttributeValue(Product.Attributes.SUPPORT_LEVEL));
        toReturn.setType(pool.getProduct().getAttributeValue(Product.Attributes.SUPPORT_TYPE));

        return toReturn;
    }

    private Order createOrder(Pool pool) {
        SimpleDateFormat iso8601DateFormat = Util.getUTCDateFormat();
        Order toReturn = new Order();

        toReturn.setNumber(pool.getOrderNumber());
        toReturn.setQuantity(pool.getQuantity());
        toReturn.setStart(iso8601DateFormat.format(pool.getStartDate()));
        toReturn.setEnd(iso8601DateFormat.format(pool.getEndDate()));

        if (StringUtils.isNotBlank(pool.getContractNumber())) {
            toReturn.setContract(pool.getContractNumber());
        }

        if (StringUtils.isNotBlank(pool.getAccountNumber())) {
            toReturn.setAccount(pool.getAccountNumber());
        }

        return toReturn;
    }

    private org.candlepin.model.dto.Pool createPool(Pool pool) {
        org.candlepin.model.dto.Pool toReturn = new org.candlepin.model.dto.Pool();
        toReturn.setId(pool.getId());
        return toReturn;
    }

    private String toJson(Object anObject) {
        String output = "";
        try {
            output = this.mapper.writeValueAsString(anObject);
        }
        catch (Exception e) {
            log.error("Could not serialize the object to json {}", anObject, e);
        }
        return output;
    }

    private byte[] processPayload(String payload) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DeflaterOutputStream dos = new DeflaterOutputStream(baos);
        ) {
            dos.write(payload.getBytes(StandardCharsets.UTF_8));
            dos.finish();
            return baos.toByteArray();
        }
        catch (IOException e) {
            throw new CertificateCreationException("Failed to create entitlement data payload!", e);
        }
    }

}
