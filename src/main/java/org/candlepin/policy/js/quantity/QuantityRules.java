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
package org.candlepin.policy.js.quantity;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.rules.v1.ConsumerDTO;
import org.candlepin.dto.rules.v1.EntitlementDTO;
import org.candlepin.dto.rules.v1.GuestIdDTO;
import org.candlepin.dto.rules.v1.PoolDTO;
import org.candlepin.dto.rules.v1.SuggestedQuantityDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.GuestId;
import org.candlepin.model.Pool;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.RuleExecutionException;
import org.candlepin.policy.js.RulesObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.type.TypeReference;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.inject.Inject;


/**
 * QuantityRules
 */
public class QuantityRules {

    private JsRunner jsRules;
    private RulesObjectMapper mapper;
    private static Logger log = LoggerFactory.getLogger(QuantityRules.class);
    private ModelTranslator translator;

    @Inject
    public QuantityRules(JsRunner jsRules, RulesObjectMapper mapper,
        ModelTranslator translator) {

        this.jsRules = jsRules;
        this.mapper = mapper;
        this.translator = translator;

        jsRules.init("quantity_name_space");
    }

    @SuppressWarnings("checkstyle:indentation")
    public SuggestedQuantityDTO getSuggestedQuantity(Pool p, Consumer c, Date date) {
        JsonJsContext args = new JsonJsContext(mapper);

        Stream<EntitlementDTO> entStream = c.getEntitlements() == null ? Stream.empty() :
            c.getEntitlements().stream()
                .filter(ent -> ent.isValidOnDate(date))
                .map(this.translator.getStreamMapper(Entitlement.class, EntitlementDTO.class));

        Stream<GuestIdDTO> guestIdStream = c.getGuestIds() == null ? Stream.empty() :
            c.getGuestIds().stream()
                .map(this.translator.getStreamMapper(GuestId.class, GuestIdDTO.class));

        args.put("consumer", this.translator.translate(c, ConsumerDTO.class));
        args.put("pool", this.translator.translate(p, PoolDTO.class));
        args.put("validEntitlements", entStream);
        args.put("log", log, false);
        args.put("guestIds", guestIdStream);

        String json = jsRules.runJsFunction(String.class, "get_suggested_quantity", args);
        return mapper.toObject(json, SuggestedQuantityDTO.class);
    }

    /**
     * Calculates the suggested quantities for many pools in one call. This allows for
     * performant list pools queries with large numbers of pools and a large amount of
     * entitlements to serialize.
     *
     * Map returned will map each pool ID to the suggested quantities for it. Every pool
     * provided should have it's ID present in the result.
     *
     * @param pools
     * @param c
     * @param date
     * @return suggested quantities for all pools requested
     */
    @SuppressWarnings("checkstyle:indentation")
    public Map<String, SuggestedQuantityDTO> getSuggestedQuantities(List<Pool> pools, Consumer c, Date date) {
        JsonJsContext args = new JsonJsContext(mapper);

        Stream<PoolDTO> poolStream = pools == null ? Stream.empty() :
            pools.stream().map(this.translator.getStreamMapper(Pool.class, PoolDTO.class));

        Stream<EntitlementDTO> entStream = c.getEntitlements() == null ? Stream.empty() :
            c.getEntitlements().stream()
                .filter(ent -> ent.isValidOnDate(date))
                .map(this.translator.getStreamMapper(Entitlement.class, EntitlementDTO.class));

        Stream<GuestIdDTO> guestIdStream = c.getGuestIds() == null ? Stream.empty() :
            c.getGuestIds().stream()
                .map(this.translator.getStreamMapper(GuestId.class, GuestIdDTO.class));

        args.put("pools", poolStream);
        args.put("consumer", this.translator.translate(c, ConsumerDTO.class));
        args.put("validEntitlements", entStream);
        args.put("log", log, false);
        args.put("guestIds", guestIdStream);

        String json = jsRules.runJsFunction(String.class, "get_suggested_quantities", args);
        Map<String, SuggestedQuantityDTO> resultMap;
        TypeReference<Map<String, SuggestedQuantityDTO>> typeref =
            new TypeReference<Map<String, SuggestedQuantityDTO>>() {};

        try {
            resultMap = mapper.toObject(json, typeref);
        }
        catch (Exception e) {
            throw new RuleExecutionException(e);
        }

        return resultMap;
    }
}
