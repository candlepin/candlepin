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
package org.candlepin.policy.js.compliance;

import org.candlepin.audit.EventSink;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.rules.v1.ComplianceReasonDTO;
import org.candlepin.dto.rules.v1.ComplianceStatusDTO;
import org.candlepin.dto.rules.v1.ConsumerDTO;
import org.candlepin.dto.rules.v1.EntitlementDTO;
import org.candlepin.dto.rules.v1.GuestIdDTO;
import org.candlepin.dto.rules.v1.PoolDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.RuleExecutionException;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.policy.js.compliance.hash.ComplianceStatusHasher;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;



/**
 * ComplianceRules
 *
 * A class used to check consumer compliance status.
 */
public class ComplianceRules {
    private static Logger log = LoggerFactory.getLogger(ComplianceRules.class);

    private JsRunner jsRules;
    private EntitlementCurator entCurator;
    private StatusReasonMessageGenerator generator;
    private EventSink eventSink;
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private RulesObjectMapper mapper;
    private ModelTranslator translator;

    @Inject
    public ComplianceRules(JsRunner jsRules, EntitlementCurator entCurator,
        StatusReasonMessageGenerator generator, EventSink eventSink, ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator, RulesObjectMapper mapper, ModelTranslator translator) {

        this.jsRules = jsRules;
        this.entCurator = entCurator;
        this.generator = generator;
        this.eventSink = eventSink;
        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.mapper = mapper;
        this.translator = translator;

        jsRules.init("compliance_name_space");
    }

    /**
     * Check compliance status for a consumer on a specific date.
     * This should NOT calculate compliantUntil.
     *
     * @param c Consumer to check.
     * @return Compliance status.
     */
    public ComplianceStatus getStatus(Consumer c) {
        return getStatus(c, null, false);
    }

    /**
     * Check compliance status for a consumer on a specific date.
     *
     * @param c Consumer to check.
     * @param date Date to check compliance status for.
     * @return Compliance status.
     */
    public ComplianceStatus getStatus(Consumer c, Date date) {
        return getStatus(c, date, true);
    }

    /**
     * Check compliance status for a consumer on a specific date.
     *
     * @param c Consumer to check.
     * @param date Date to check compliance status for.
     * @param calculateCompliantUntil calculate how long the system will remain compliant (expensive)
     * @return Compliance status.
     */
    public ComplianceStatus getStatus(Consumer c, Date date, boolean calculateCompliantUntil) {
        boolean currentCompliance = false;
        if (date == null) {
            currentCompliance = true;
        }
        return getStatus(c, null, date, calculateCompliantUntil, true, false, currentCompliance);
    }

    /**
     * Check compliance status for a consumer on a specific date.
     *
     * @param c Consumer to check.
     * @param date Date to check compliance status for.
     * @param calculateCompliantUntil calculate how long the system will remain compliant (expensive)
     * @param updateConsumer whether or not to use consumerCurator.update
     * @return Compliance status.
     */
    public ComplianceStatus getStatus(Consumer c, Date date, boolean calculateCompliantUntil,
        boolean updateConsumer) {

        return this.getStatus(c, null, date, calculateCompliantUntil, updateConsumer, false, true);
    }

    /**
     * Check compliance status for a consumer on a specific date.
     *
     * @param consumer Consumer to check.
     * @param date Date to check compliance status for.
     * @param calculateCompliantUntil calculate how long the system will remain compliant (expensive)
     * @param updateConsumer whether or not to use consumerCurator.update
     * @param calculateProductComplianceDateRanges calculate the individual compliance ranges for each product
     *        (also expensive)
     * @return Compliance status.
     */
    @SuppressWarnings("checkstyle:indentation")
    public ComplianceStatus getStatus(Consumer consumer, Collection<Entitlement> newEntitlements, Date date,
        boolean calculateCompliantUntil, boolean updateConsumer, boolean calculateProductComplianceDateRanges,
        boolean currentCompliance) {

        if (date == null) {
            date = new Date();
        }

        if (currentCompliance) {
            updateEntsOnStart(consumer);
        }

        Stream<EntitlementDTO> entStream = Stream.concat(
            newEntitlements != null ? newEntitlements.stream() : Stream.empty(),
            consumer.getEntitlements() != null ? consumer.getEntitlements().stream() : Stream.empty())
                .map(this.translator.getStreamMapper(Entitlement.class, EntitlementDTO.class));

        Stream<GuestIdDTO> guestIdStream = consumer.getGuestIds() == null ? Stream.empty() :
            consumer.getGuestIds().stream()
                .map(this.translator.getStreamMapper(GuestId.class, GuestIdDTO.class));


        // Status can only be 'disabled' when in golden ticket mode
        if (consumer.getOwner() != null && consumer.getOwner().isContentAccessEnabled()) {
            ComplianceStatus cs = new ComplianceStatus(new Date());
            cs.setDisabled(true);
            applyStatus(consumer, cs, updateConsumer);
            return cs;
        }

        // Do not calculate compliance status for distributors. It is prohibitively
        // expensive and meaningless
        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);
        if (ctype != null && (ctype.isManifest())) {
            return new ComplianceStatus(new Date());
        }

        JsonJsContext args = new JsonJsContext(mapper);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("entitlements", entStream);
        args.put("ondate", date);
        args.put("calculateCompliantUntil", calculateCompliantUntil);
        args.put("calculateProductComplianceDateRanges", calculateProductComplianceDateRanges);
        args.put("log", log, false);
        args.put("guestIds", guestIdStream);

        // Convert the JSON returned into a ComplianceStatus object:
        String json = jsRules.runJsFunction(String.class, "get_status", args);
        try {
            ComplianceStatusDTO statusDTO = mapper.toObject(json, ComplianceStatusDTO.class);
            ComplianceStatus status = new ComplianceStatus();
            Set<Entitlement> allEntitlements = Stream.concat(
                newEntitlements != null ? newEntitlements.stream() : Stream.empty(),
                consumer.getEntitlements() != null ? consumer.getEntitlements().stream() : Stream.empty())
                .collect(Collectors.toSet());
            populateEntity(status, statusDTO, allEntitlements);

            for (ComplianceReason reason : status.getReasons()) {
                generator.setMessage(consumer, reason, status.getDate());
            }

            if (currentCompliance) {
                applyStatus(consumer, status, updateConsumer);
            }

            return status;
        }
        catch (Exception e) {
            throw new RuleExecutionException(e);
        }
    }

    public void updateEntsOnStart(Consumer c) {
        for (Entitlement ent : c.getEntitlements()) {
            if (!ent.isUpdatedOnStart() && ent.isValid()) {
                ent.setUpdatedOnStart(true);
                entCurator.merge(ent);
            }
        }
    }

    public void applyStatus(Consumer c, ComplianceStatus status, boolean updateConsumer) {
        String newHash = getComplianceStatusHash(status, c);
        boolean complianceChanged = !newHash.equals(c.getComplianceStatusHash());
        if (complianceChanged) {
            log.debug("Compliance has changed, sending Compliance event.");
            c.setComplianceStatusHash(newHash);
            eventSink.emitCompliance(c, status);
        }

        boolean entStatusChanged = !status.getStatus().equals(c.getEntitlementStatus());
        if (entStatusChanged) {
            c.setEntitlementStatus(status.getStatus());
        }

        if (updateConsumer && (complianceChanged || entStatusChanged)) {
            // Merge might work better here, but we use update in other places for this
            consumerCurator.update(c, false);
        }
    }

    @SuppressWarnings("checkstyle:indentation")
    public boolean isStackCompliant(Consumer consumer, String stackId, List<Entitlement> entsToConsider) {
        Stream<EntitlementDTO> entStream = entsToConsider == null ? Stream.empty() :
            entsToConsider.stream()
                .map(this.translator.getStreamMapper(Entitlement.class, EntitlementDTO.class));

        Stream<GuestIdDTO> guestIdStream = consumer.getGuestIds() == null ? Stream.empty() :
            consumer.getGuestIds().stream()
                .map(this.translator.getStreamMapper(GuestId.class, GuestIdDTO.class));

        JsonJsContext args = new JsonJsContext(mapper);
        args.put("stack_id", stackId);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("entitlements", entStream);
        args.put("log", log, false);
        args.put("guestIds", guestIdStream);

        return jsRules.runJsFunction(Boolean.class, "is_stack_compliant", args);
    }

    @SuppressWarnings("checkstyle:indentation")
    public boolean isEntitlementCompliant(Consumer consumer, Entitlement ent, Date onDate) {
        List<Entitlement> ents = entCurator.listByConsumerAndDate(consumer, onDate).list();

        Stream<EntitlementDTO> entStream = ents == null ? Stream.empty() :
            ents.stream().map(this.translator.getStreamMapper(Entitlement.class, EntitlementDTO.class));

        Stream<GuestIdDTO> guestIdStream = consumer.getGuestIds() == null ? Stream.empty() :
            consumer.getGuestIds().stream()
                .map(this.translator.getStreamMapper(GuestId.class, GuestIdDTO.class));

        JsonJsContext args = new JsonJsContext(mapper);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("entitlement", this.translator.translate(ent, EntitlementDTO.class));
        args.put("entitlements", entStream);
        args.put("log", log, false);
        args.put("guestIds", guestIdStream);

        return jsRules.runJsFunction(Boolean.class, "is_ent_compliant", args);
    }

    private String getComplianceStatusHash(ComplianceStatus status, Consumer consumer) {
        ComplianceStatusHasher hasher = new ComplianceStatusHasher(consumer, status);
        return hasher.hash();
    }


    /**
     * Populates an entity that is to be created with data from the provided DTO.
     *
     * @param entity
     *  The entity instance to populate
     *
     * @param dto
     *  The DTO containing the data with which to populate the entity
     *
     * @throws IllegalArgumentException
     *  if either entity or dto are null
     */
    @SuppressWarnings("checkstyle:methodlength")
    protected void populateEntity(ComplianceStatus entity, ComplianceStatusDTO dto,
        Set<Entitlement> allExistingEntitlements) {

        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("dto is null");
        }

        if (dto.getDate() != null) {
            entity.setDate(dto.getDate());
        }

        if (dto.getCompliantUntil() != null) {
            entity.setCompliantUntil(dto.getCompliantUntil());
        }

        if (dto.getNonCompliantProducts() != null) {
            dto.getNonCompliantProducts().forEach(entity::addNonCompliantProduct);
        }

        if (dto.getProductComplianceDateRanges() != null) {
            dto.getProductComplianceDateRanges().forEach(entity::addProductComplianceDateRange);
        }

        if (dto.getReasons() != null) {
            if (dto.getReasons().isEmpty()) {
                entity.setReasons(Collections.emptySet());
            }
            else {
                Set<ComplianceReason> reasons = new HashSet<>();
                for (ComplianceReasonDTO reasonDTO : dto.getReasons()) {
                    if (reasonDTO != null) {
                        ComplianceReason reason = new ComplianceReason();
                        reason.setKey(reasonDTO.getKey());
                        reason.setMessage(reasonDTO.getMessage());
                        reason.setAttributes(reasonDTO.getAttributes());
                        reasons.add(reason);
                    }
                }
                entity.setReasons(reasons);
            }
        }

        Set<EntitlementDTO> entitlementDTOs = new HashSet<>();
        Map<String, Entitlement> translated = new HashMap<>();

        Map<String, Set<EntitlementDTO>> compliantProductDTOs = dto.getCompliantProducts();
        Map<String, Set<EntitlementDTO>> pcProductDTOs = dto.getPartiallyCompliantProducts();
        Map<String, Set<EntitlementDTO>> partialStackDTOs = dto.getPartialStacks();

        if (compliantProductDTOs != null) {
            compliantProductDTOs.values().forEach(entitlementDTOs::addAll);
        }

        if (pcProductDTOs != null) {
            pcProductDTOs.values().forEach(entitlementDTOs::addAll);
        }

        if (partialStackDTOs != null) {
            partialStackDTOs.values().forEach(entitlementDTOs::addAll);
        }

        for (EntitlementDTO entitlementDTO : entitlementDTOs) {
            Entitlement entitlementModel = new Entitlement();

            if (entitlementDTO.getId() != null) {
                entitlementModel.setId(entitlementDTO.getId());
            }

            if (entitlementDTO.getEndDate() != null) {
                entitlementModel.setEndDate(entitlementDTO.getEndDate());
            }

            if (entitlementDTO.getStartDate() != null) {
                entitlementModel.setStartDate(entitlementDTO.getStartDate());
            }

            if (entitlementDTO.getQuantity() != null) {
                entitlementModel.setQuantity(entitlementDTO.getQuantity());
            }

            PoolDTO poolDTO = entitlementDTO.getPool();
            if (poolDTO != null) {
                Pool pool = new Pool();

                if (poolDTO.getId() != null) {
                    pool.setId(poolDTO.getId());
                }

                if (poolDTO.getQuantity() != null) {
                    pool.setQuantity(poolDTO.getQuantity());
                }

                if (poolDTO.getStartDate() != null) {
                    pool.setStartDate(poolDTO.getStartDate());
                }

                if (poolDTO.getEndDate() != null) {
                    pool.setEndDate(poolDTO.getEndDate());
                }

                if (poolDTO.getConsumed() != null) {
                    pool.setConsumed(poolDTO.getConsumed());
                }

                if (poolDTO.getRestrictedToUsername() != null) {
                    pool.setRestrictedToUsername(poolDTO.getRestrictedToUsername());
                }

                if (poolDTO.getAttributes() != null) {
                    pool.setAttributes(poolDTO.getAttributes());
                }

                if (poolDTO.getProductId() == null) {
                    throw new IllegalArgumentException("Received null product ID on entitlement");
                }

                Product product = new Product()
                    .setId(poolDTO.getProductId())
                    .setAttributes(poolDTO.getProductAttributes());

                pool.setProduct(product);

                // Impl note: we don't support N-tier here in any capacity. This will need to be
                // updated accordingly once we know what N-tier means/requires.
                Collection<PoolDTO.ProvidedProductDTO> ppDTOs = poolDTO.getProvidedProducts();
                if (ppDTOs != null) {
                    Map<String, Product> providedProducts = ppDTOs.stream()
                        .collect(Collectors.toMap(PoolDTO.ProvidedProductDTO::getProductId, ppDTO -> {
                            return new Product()
                                .setId(ppDTO.getProductId())
                                .setName(ppDTO.getProductName());
                        }));

                    product.setProvidedProducts(providedProducts.values());
                }

                if (poolDTO.getDerivedProductId() != null) {
                    Product derived = new Product()
                        .setId(poolDTO.getDerivedProductId());

                    Collection<PoolDTO.ProvidedProductDTO> dppDTOs = poolDTO.getDerivedProvidedProducts();
                    if (dppDTOs != null) {
                        Map<String, Product> providedProducts = dppDTOs.stream()
                            .collect(Collectors.toMap(PoolDTO.ProvidedProductDTO::getProductId, dppDTO -> {
                                return new Product()
                                    .setId(dppDTO.getProductId())
                                    .setName(dppDTO.getProductName());
                            }));

                        derived.setProvidedProducts(providedProducts.values());
                    }

                    product.setDerivedProduct(derived);
                }

                entitlementModel.setPool(pool);
            }

            for (Entitlement existingEntitlement : allExistingEntitlements) {
                if (entitlementModel.getId().equals(existingEntitlement.getId()) &&
                    entitlementModel.getPool() != null && existingEntitlement.getPool() != null) {
                    entitlementModel.getPool().setOwner(existingEntitlement.getPool().getOwner());
                }
            }

            translated.put(entitlementModel.getId(), entitlementModel);
        }

        // Rebuild the translated maps
        entity.setCompliantProducts(this.translateEntitlementMap(compliantProductDTOs, translated));
        entity.setPartiallyCompliantProducts(this.translateEntitlementMap(pcProductDTOs, translated));
        entity.setPartialStacks(this.translateEntitlementMap(partialStackDTOs, translated));
    }

    /**
     * Rebuilds a translated map of entitlement sets from the DTO map and given mapping of
     * translated entitlement model objects.
     *
     * @param sourceMap
     *  The source mapping of product ID to entitlementDTO set
     *
     * @param entities
     *  A mapping of entitlement ID to entitlement model object to use for rebuilding the entitlement map
     *
     * @return
     *  The rebuilt and translated entitlement map, or null if the source map is null
     */
    private Map<String, Set<Entitlement>> translateEntitlementMap(Map<String, Set<EntitlementDTO>> sourceMap,
        Map<String, Entitlement> entities) {

        Map<String, Set<Entitlement>> output = null;

        if (sourceMap != null) {
            output = new HashMap<>();

            for (Map.Entry<String, Set<EntitlementDTO>> entry : sourceMap.entrySet()) {
                if (entry.getValue() != null) {
                    output.put(entry.getKey(), entry.getValue().stream()
                        .filter(Objects::nonNull)
                        .map(e -> entities.get(e.getId()))
                        .collect(Collectors.toSet()));
                }
            }
        }

        return output;
    }

}
