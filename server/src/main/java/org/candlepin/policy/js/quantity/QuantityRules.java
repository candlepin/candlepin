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
package org.candlepin.policy.js.quantity;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.rules.v1.ConsumerDTO;
import org.candlepin.dto.rules.v1.PoolDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.RuleExecutionException;
import org.candlepin.policy.js.RulesObjectMapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * QuantityRules
 */
public class QuantityRules {

    private JsRunner jsRules;
    private RulesObjectMapper mapper;
    private static Logger log = LoggerFactory.getLogger(QuantityRules.class);
    private ModelTranslator translator;

    @Inject
    public QuantityRules(JsRunner jsRules, RulesObjectMapper mapper, ModelTranslator translator) {
        this.jsRules = jsRules;
        this.mapper = mapper;
        this.translator = translator;

        jsRules.init("quantity_name_space");
    }

    public SuggestedQuantity getSuggestedQuantity(Pool p, Consumer c, Date date) {
        JsonJsContext args = new JsonJsContext(mapper);

        Set<Entitlement> validEntitlements = new HashSet<Entitlement>();
        for (Entitlement e : c.getEntitlements()) {
            if (e.isValidOnDate(date)) {
                validEntitlements.add(e);
            }
        }

        args.put("consumer", this.translator.translate(c, ConsumerDTO.class));
        args.put("pool", this.translator.translate(p, PoolDTO.class));
        args.put("validEntitlements", validEntitlements);
        args.put("log", log, false);
        args.put("guestIds", c.getGuestIds());

        String json = jsRules.runJsFunction(String.class, "get_suggested_quantity", args);
        SuggestedQuantity dto = mapper.toObject(json, SuggestedQuantity.class);
        return dto;
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
    public Map<String, SuggestedQuantity> getSuggestedQuantities(List<Pool> pools,
        Consumer c, Date date) {

        JsonJsContext args = new JsonJsContext(mapper);

        Set<Entitlement> validEntitlements = new HashSet<Entitlement>();
        for (Entitlement e : c.getEntitlements()) {
            if (e.isValidOnDate(date)) {
                validEntitlements.add(e);
            }
        }

        List<PoolDTO> poolDTOs = new ArrayList<PoolDTO>();
        for (Pool pool : pools) {
            poolDTOs.add(this.translator.translate(pool, PoolDTO.class));
        }

        args.put("pools", poolDTOs);
        args.put("consumer", this.translator.translate(c, ConsumerDTO.class));
        args.put("validEntitlements", validEntitlements);
        args.put("log", log, false);
        args.put("guestIds", c.getGuestIds());

        String json = jsRules.runJsFunction(String.class, "get_suggested_quantities", args);
        Map<String, SuggestedQuantity> resultMap;
        TypeReference<Map<String, SuggestedQuantity>> typeref =
            new TypeReference<Map<String, SuggestedQuantity>>() {};
        try {
            resultMap = mapper.toObject(json, typeref);
        }
        catch (Exception e) {
            throw new RuleExecutionException(e);
        }
        return resultMap;
    }
}
