// Version: 5.8

/*
 * Default Candlepin rule set.
 *
 * Minor version number bumped on every content change. Major version bumped
 * when the API between the engine and these rules changes.
 */


// Define our namespaces

function entitlement_name_space() {
    return Entitlement;
}

function activation_key_name_space() {
    return ActivationKey;
}

function compliance_name_space() {
    return Compliance;
}

function autobind_name_space() {
    return Autobind;
}

function quantity_name_space() {
    return Quantity;
}

function override_name_space() {
    return Override;
}

function pool_type_name_space() {
    return PoolType;
}

// consumer types
var SYSTEM_TYPE = "system";
var HYPERVISOR_TYPE = "hypervisor";
var UEBERCERT_TYPE = "uebercert";

// Consumer fact names
var SOCKET_FACT="cpu.cpu_socket(s)";
var RAM_FACT = "memory.memtotal";
var CORES_FACT = "cpu.core(s)_per_socket";
var ARCH_FACT = "uname.machine";
var IS_VIRT_GUEST_FACT = "virt.is_guest";
var PROD_ARCHITECTURE_SEPARATOR = ",";

// Product attribute names
var SOCKETS_ATTRIBUTE = "sockets";
var CORES_ATTRIBUTE = "cores";
var ARCH_ATTRIBUTE = "arch";
var RAM_ATTRIBUTE = "ram";
var INSTANCE_ATTRIBUTE = "instance_multiplier";
var REQUIRES_HOST_ATTRIBUTE = "requires_host";
var VIRT_ONLY = "virt_only";
var PHYSICAL_ONLY = "physical_only";
var POOL_DERIVED = "pool_derived";
var GUEST_LIMIT_ATTRIBUTE = "guest_limit";
var VCPU_ATTRIBUTE = "vcpu";
var MULTI_ENTITLEMENT_ATTRIBUTE = "multi-entitlement";

// caller types
var BEST_POOLS_CALLER = "best_pools";
var BIND_CALLER = "bind";
var LIST_POOLS_CALLER = "list_pools";
var UNKNOWN_CALLER = "unknown";

/**
 * A FactValueCalculator allows the rules to determine which
 * consumer fact is associated with a particular product
 * attribute, and determines the calculated fact value
 * that should be used when determining coverage. The attribute
 * should be mapped to the fact here.
 *
 * NOTE: Can't define these inside object creation.
 *       JS is funny in that you can't seem to use
 *       a var for the key inside.
 *
 */
var ATTRIBUTES_TO_CONSUMER_FACTS = {};
ATTRIBUTES_TO_CONSUMER_FACTS[SOCKETS_ATTRIBUTE] = SOCKET_FACT;
ATTRIBUTES_TO_CONSUMER_FACTS[CORES_ATTRIBUTE] = CORES_FACT;
ATTRIBUTES_TO_CONSUMER_FACTS[ARCH_ATTRIBUTE] = ARCH_FACT;
ATTRIBUTES_TO_CONSUMER_FACTS[RAM_ATTRIBUTE] = RAM_FACT;
ATTRIBUTES_TO_CONSUMER_FACTS[VCPU_ATTRIBUTE] = CORES_FACT;

/**
 *  These product attributes are considered when determining
 *  coverage of a consumer by a stack. Add an attribute here
 *  to enable stacking on the product attribute.
 */
var PHYSICAL_ATTRIBUTES = [
    SOCKETS_ATTRIBUTE,
    CORES_ATTRIBUTE,
    RAM_ATTRIBUTE,
    ARCH_ATTRIBUTE,
    GUEST_LIMIT_ATTRIBUTE
];

var VIRT_ATTRIBUTES = [
    VCPU_ATTRIBUTE,
    RAM_ATTRIBUTE,
    ARCH_ATTRIBUTE,
    GUEST_LIMIT_ATTRIBUTE
];

/**
 * These product attributes will not be considered on
 * pools that are host restricted/
 */
var UNCHECKED_WHEN_HOST_RESTRICTED = [
    RAM_ATTRIBUTE,
    VCPU_ATTRIBUTE
];

/**
 * These product attributes are considered by grouping them
 * with the same attribute on all other subscriptions on
 * the system.
 */
var GLOBAL_ATTRIBUTES = [
    GUEST_LIMIT_ATTRIBUTE
];

/*
 * Depending on the consumer, different attributes may
 * affect compliance.
 */
function getComplianceAttributes(consumer) {
    // Currently we only differentiate between physical/virtual
    if (Utils.isGuest(consumer)) {
        return VIRT_ATTRIBUTES;
    }
    return PHYSICAL_ATTRIBUTES;
}

/*
 * Model object related functions.
 */

function createPool(pool) {

    // Lazily initialized arrays of provided product IDs. Includes the pool's
    // main productId.
    pool.product_list = [];
    pool.derived_product_list = [];

    // General function to look for an attribute in the specified
    // attribute collection.
    pool.findAttributeIn = function (attrName, attrs) {
        for (var k = 0; k < attrs.length; k++) {
            var attr = attrs[k];
            if (attrName == attr.name) {
                var value = attr.value;

                // An attribute is considered not set if it
                // it has a value of 0.
                if (value === 0 || value === "0") {
                    return null;
                }
                return value;
            }
        }
        return null;
    };

    pool.getAttribute = function (attrName) {
        attr_result = this.findAttributeIn(attrName, this.attributes);
        if (attr_result === null) {
            return this.findAttributeIn(attrName, this.productAttributes);
        }
        return attr_result;
    };

    pool.hasAttribute = function(attrName) {
        return this.getAttribute(attrName) !== null;
    };

    pool.getProductAttribute = function (attrName) {
        var attr_result = this.findAttributeIn(attrName, this.productAttributes);
        if (attr_result === null) {
            return this.findAttributeIn(attrName, this.attributes);
        }
        return attr_result;
    };

    pool.hasProductAttribute = function (attrName) {
        return this.getProductAttribute(attrName) !== null;
    };

    // Add some functions onto pool objects:
    pool.provides = function (productId) {
        if (this.productId == productId) {
            return true;
        }
        for (var k = 0; k < this.providedProducts.length; k++) {
            var provided = this.providedProducts[k];

            if (provided.productId == productId) {
                return true;
            }
        }
        return false;
    };

    // Lazily initialize the list of provided product IDs.
    pool.products = function () {
        if (this.product_list == 0) {
            this.product_list.push(this.productId);
            for (var k = 0; k < this.providedProducts.length; k++) {
                this.product_list.push(this.providedProducts[k].productId);
            }
        }
        return this.product_list;
    };

    // Return true if this pool is carrying derived pool information. Watch out
    // for older servers which didn't send any derived properties in, just in
    // case. (this is probably impossible to hit due to changes in rule
    // versioning)
    pool.hasDerived = function () {
        if (this.derivedProductId == null) {
          return false;
        }
        return true;
    };

    pool.isUnlimited = function() {
        return pool.quantity < 0;
    };

    pool.getAvailable = function() {
        return pool.quantity - pool.consumed; 
    };

    // Lazily initialize the list of derived provided product IDs.
    pool.derivedProducts = function () {

        // Just being overly cautious here, but make sure that if this were requested
        // on a server that didn't support derived pools, we just return empty results.
        if (!this.hasDerived()) {
          return this.derived_product_list;
        }

        if (this.derived_product_list == 0) {
            this.derived_product_list.push(this.derivedProductId);
            for (var k = 0; k < this.derivedProvidedProducts.length; k++) {
                this.derived_product_list.push(this.derivedProvidedProducts[k].productId);
            }
        }
        return this.derived_product_list;
    };
    return pool;
}

function createActivationKey(key) {
    key.virt_only = false;
    key.physical_only = false;
    key.requires_host = null;
    key.consumer_type = null;

    for (var i = 0; i < key.pools.length; i++) {
        var pool = createPool(key.pools[i].pool);

        // If any pools in the key are physical only, consider the key physical only
        key.physical_only = key.physical_only ||
            Utils.equalsIgnoreCase('true', pool.getAttribute(PHYSICAL_ONLY));

        // If any pools in the key are virt only, consider the key virt only
        key.virt_only = key.virt_only ||
            Utils.equalsIgnoreCase('true', pool.getAttribute(VIRT_ONLY)) ||
            (pool.hasAttribute(INSTANCE_ATTRIBUTE) &&
             context.key.pools[i].quantity !== null &&
             parseInt(pool.getAttribute(INSTANCE_ATTRIBUTE)) > context.key.pools[i].quantity);

        var pool_host_requires = pool.getAttribute(REQUIRES_HOST_ATTRIBUTE);
        if (pool_host_requires !== null && pool_host_requires != "") {
            key.requires_host = pool_host_requires;
            key.virt_only = true;
        }
        var pool_consumer_type = pool.getAttribute("requires_consumer_type");
        if (pool_consumer_type !== null && pool_consumer_type != "") {
            key.consumer_type = pool_consumer_type;
        }
    }
    return key;
}

/*
 * Creates an object that represents the entitlement that would be created
 * by a given pool.  Uses the maximum quantity available.
 */
function get_mock_ent_for_pool(pool, consumer) {
    return {
        pool: pool,
        startDate: pool.startDate,
        endDate: pool.endDate,
        quantity: pool.currently_available,
        consumer: consumer,
        owner: consumer.owner
    };
}

/* Utility functions */
function contains(a, obj) {
    for (var i = 0; i < a.length; i++) {
        var result = a[i] == obj;
        if (result) {
            return true;
        }
    }
    return false;
}

function containsAll(a, b) {
    for (var i = 0 ; i < b.length ; i++) {
        if (!contains(a, b[i])) {
            return false;
        }
    }

    return true;
}

function getRelevantProvidedProducts(pool, productIds) {
    var provided = [];

    for (var i = 0 ; i < productIds.length ; i++) {
        var productId = productIds[i];
        if (pool.provides(productId)) {
            provided.push(productId);
        }
    }

    return provided;
}

function providesSameProducts(products1, products2) {
    return containsAll(products1, products2) && containsAll(products2, products1);
}

function arrayToString(a) {
    msg = "[";
    for (var j = 0; j < a.length; j++) {
        var q = a[j];

        msg += q.getId() + " ";
    }
    msg += "]";
    return msg;
}

// Check if the provided list of pools contains any duplicated products
// We don't need to worry about checking multi-entitle allowed products,
// as you can use as many of those as you want.
function hasNoProductOverlap(combination) {
    var seen_product_ids = [];
    for (var j = 0; j < combination.length; j++) {
        var pool_class = combination[j];

        var pool = pool_class[0];
        var products = pool.products();
        for (var i = 0 ; i < products.length ; i++) {
            var productId = products[i];
            log.debug("product overlap: " + productId);
            if (!contains(seen_product_ids, productId)) {
                seen_product_ids.push(productId);
            } else if (!Utils.isMultiEnt(pool)) {
                return false;
            }
        }
    }

    return true;
}

//Check to see if a pool provides any products that are already compliant
function hasNoInstalledOverlap(pool, compliance) {
    var products = pool.products();
    for (var i = 0 ; i < products.length ; i++) {
        var productId = products[i];
        log.debug("installed overlap: " + productId);
        if (!Utils.isMultiEnt(pool) &&
            Object.hasOwnProperty(compliance.compliantProducts, productId)) {
            return false;
        }
    }

    return true;
}

function architectureMatches(productArchStr, consumerUnameMachine, consumerType) {
    // Non-system consumers without an architecture fact can pass this rule
    // regardless what arch the product requires.
    if (!consumerUnameMachine && "system" != consumerType) {
        return true;
    }

    var supportedArches = [];
    if (productArchStr !== null) {
        supportedArches = productArchStr.toUpperCase().split(PROD_ARCHITECTURE_SEPARATOR);

        // If X86 is supported, add all variants to this list:
        if (Utils.inArray(supportedArches, "X86")) {
           supportedArches.push("I386");
           supportedArches.push("I586");
           supportedArches.push("I686");
        }

        if(!Utils.inArray(supportedArches, 'ALL') && (!consumerUnameMachine ||
           !Utils.inArray(supportedArches, consumerUnameMachine.toUpperCase()))) {
           return false;
       }
   }

   return true;
}

/**
 * Get the quantity of the specified attribute that each
 * entitlement from a pool covers. If the attribute is
 * set to 0 or is not set, it is considered to be unlimited.
 */
function getPoolQuantity(pool, attributeName) {
    for (var j = 0; j < pool.productAttributes.length; j++) {
        var prodAttr = pool.productAttributes[j];

        if (prodAttr.name == attributeName) {
            var initialQuantity = prodAttr.value;
            // TODO: We might have a string here...
            return parseInt(initialQuantity);
        }
    }
    return null;
}

/**
 * A FactValueCalculator allows the rules to determine which
 * consumer fact is associated with a particular product
 * attribute, and determines the calculated fact value
 * that should be used when determining coverage.
 */
var FactValueCalculator = {
    /**
     * 'calculators': Defines a mapping of product attribute to a function that
     *                calculates the consumer value that should be used for
     *                comparison when determining coverage.
     *
     *                The mapped function should have the following signature:
     *                     function (prodAttr, consumer)
     */
    calculators: {
        /*
         * The default calculator used if there is no custom calculator
         * defined for the product attribute. The RAW consumer fact value
         * is returned, or otherwise 1.
         */
        default: function (prodAttr, consumer) {
            return consumer.facts[ATTRIBUTES_TO_CONSUMER_FACTS[prodAttr]] ?
                    consumer.facts[ATTRIBUTES_TO_CONSUMER_FACTS[prodAttr]] : 1;
        },

        /**
         * Calculates the consumer's RAM value based on its "ram" fact.
         * RAM from the consumer must be converted to GB so that it can
         * be compared to that specified on the product.
         */
        ram: function (prodAttr, consumer) {
            var consumerRam = consumer.facts[ATTRIBUTES_TO_CONSUMER_FACTS[prodAttr]] ?
                    consumer.facts[ATTRIBUTES_TO_CONSUMER_FACTS[prodAttr]] : 1;
            var ramGb = parseInt(consumerRam) / 1024 / 1024;
            return Math.round(ramGb);
        },

        /**
         * Calculates the total number of cores associated with a consumer.
         * The consumer provides us with the number of cores per socket, so
         * we need to multiply that by the number of sockets we have to
         * determine the total number of cores.
         */
        cores: function (prodAttr, consumer) {
            var consumerSockets = FactValueCalculator.getFact(SOCKETS_ATTRIBUTE, consumer);

            // Use the 'default' calculator to get the RAW cores value from the consumer.
            var consumerCoresPerSocket =
                FactValueCalculator.calculators.default(prodAttr, consumer);

            var cores = consumerCoresPerSocket * consumerSockets;
            log.debug("Consumer has " + cores + " cores.");
            return cores;
        },

        vcpu: function (prodAttr, consumer) {
            return FactValueCalculator.getFact(CORES_ATTRIBUTE, consumer);
        },

        guest_limit: function (prodAttr, consumer) {
            if (consumer.guestIds === null) {
                return 0;
            }
            var activeGuestCount = 0;
            for (var guestIdx = 0; guestIdx < consumer.guestIds.length; guestIdx++) {
                var guest = consumer.guestIds[guestIdx];
                if (Utils.isGuestActive(guest)) {
                    activeGuestCount++;
                }
            }
            return activeGuestCount;
        }
    },

    getFact: function(prodAttr, consumer) {
        var calculate = this.calculators.default;
        if (prodAttr in this.calculators) {
            calculate = this.calculators[prodAttr];
        }
        var calculatedValue =  calculate(prodAttr, consumer);
        log.debug("Calculated consumer " + prodAttr + " to be " + calculatedValue);
        return calculatedValue;
    }
};

/*
 * This Attribute Calculator works similarly to others, except that it is
 * specific to global attributes such as guest_limit.  It needs to check
 * every attached entitlement on the system, as a complianceTracker needs to
 * check every entitlement in the stack.
 *
 * TODO: Cache this value when possible. It's only O(n), but we can avoid
 * some unnecessary work
 */
var GlobalAttributeCalculator = {
    calculators: {
        default: function (prodAttr, entitlements) {
            var total = 0;
            for (var eidx = 0; eidx < entitlements.length; eidx++) {
                var pool = entitlements[eidx].pool;
                if (pool.hasProductAttribute(prodAttr)) {
                    total += parseInt(pool.getProductAttribute(prodAttr)) || 0;
                }
            }
            return total;
        },

        guest_limit: function (prodAttr, entitlements) {
            var total = null;
            for (var eidx = 0; eidx < entitlements.length; eidx++) {
                var pool = entitlements[eidx].pool;
                if (pool.hasProductAttribute(prodAttr)) {
                    if (total === null) {
                        total = 0;
                    }
                    var poolValue = parseInt(pool.getProductAttribute(prodAttr));
                    if (poolValue == -1) {
                        return poolValue;
                    }
                    if (poolValue > total) {
                        total = poolValue;
                    }
                }
            }
            return total;
        }
    },

    getValue: function (prodAttr, entitlements) {
        var calculate = this.calculators.default;
        if (prodAttr in this.calculators) {
            calculate = this.calculators[prodAttr];
        }
        var calculatedValue =  calculate(prodAttr, entitlements);
        log.debug("Calculated consumer global attribute  " + prodAttr + " to be " + calculatedValue);
        return calculatedValue;
    }
};
/**
 * A factory for creating JS objects representing the reasons
 * affecting a non covered entitlement or stack.
 */
var StatusReasonGenerator = {

    /*
     * Add a reason for non-valid status
     *   stacked: If true, id is a stack_id, otherwise an entitlement_id
     */
    buildReason: function (reason_key, sourceType, id, has, covered) {
        // Define any attributes for this reason.
        var attributes = {};
        attributes["has"] = has;
        attributes["covered"] = covered;

        var idAttribute = this.getIdAttribute(sourceType);
        if (idAttribute) {
            attributes[idAttribute] = id;
        }

        var reason = {};
        reason["key"] = reason_key;
        reason["message"] = reason_key;
        reason["attributes"] = attributes;
        return reason;
    },

   /*
    * Add a reason for an installed product without entitlement (red)
    */
    buildInstalledProductReason: function (installed_pid) {
        var attributes = {};
        attributes["product_id"] = installed_pid;

        var reason = {};
        reason["key"] = "NOTCOVERED";
        reason["message"] = reason["key"];
        reason["attributes"] = attributes;
        return reason;
    },

    getIdAttribute: function (type) {
        var attribute = null;
        if (type == "STACK") {
            attribute = "stack_id";
        }
        else if (type == "ENTITLEMENT") {
            attribute = "entitlement_id";
        }
        return attribute;
    }
};

/**
 *  Determines the coverage of a consumer based on a single pool, or a stack.
 *  Product attributes are checked based on 'conditions' and whether or not
 *  a product attribute was set.
 */
var CoverageCalculator = {

    /**
     *  Returns an object that defines the conditions that are checked against
     *  a defined product attribute. If special conditions should be checked
     *  for a particular attribute, map the attribute to a function that checks
     *  the condition.
     *
     *  NOTE: Be sure that any new function has the same signature.
     */
    getDefaultConditions: function() {
        return {
            /**
             *  Checks to make sure that the architecture matches that of the consumer.
             */
            arch: function (complianceTracker, prodAttr, consumer) {
                var supportedArchs = complianceTracker.enforces(prodAttr) ? complianceTracker.getAccumulatedValue(prodAttr) : [];
                var consumerArch = ARCH_FACT in consumer.facts ?
                    consumer.facts[ARCH_FACT] : null;

                for (var archStringIdx in supportedArchs) {
                    var archString = supportedArchs[archStringIdx];
                    if (!architectureMatches(archString, consumerArch, consumer.type.label)) {
                        log.debug("  System architecture not covered by: " + archString);
                        return StatusReasonGenerator.buildReason(prodAttr.toUpperCase(),
                                                             complianceTracker.type,
                                                             complianceTracker.id,
                                                             consumerArch,
                                                             archString);
                    }
                }
                log.debug("  System architecture is covered.");
                return null;
            },

            /**
             * Same int compare as the default, except -1 is unlimited
             */
            guest_limit: function (complianceTracker, prodAttr, consumer) {
                var consumerQuantity = FactValueCalculator.getFact(prodAttr, consumer);
                var sourceValue = complianceTracker.getAccumulatedValue(prodAttr);

                var covered = (sourceValue == -1) || (parseInt(sourceValue) >= consumerQuantity);
                log.debug("  System's " + prodAttr + " covered: " + covered);

                var reason = null;
                if (!covered) {
                    reason = StatusReasonGenerator.buildReason(prodAttr.toUpperCase(),
                            complianceTracker.type,
                            complianceTracker.id,
                            consumerQuantity,
                            sourceValue);
                }
                return reason;
            },

            /**
             *  The default condition checks is a simple *integer* comparison that makes
             *  sure that the specified product attribute value is >= that of the
             *  consumer's calculated value.
             *
             *  NOTE: If comparing non integer attributes, a special condition should
             *        be added to handle that case.
             */
            default: function(complianceTracker, prodAttr, consumer) {
                var consumerQuantity = FactValueCalculator.getFact(prodAttr, consumer);
                var sourceValue = complianceTracker.getAccumulatedValue(prodAttr);

                // We assume that the value coming back is an int right now.
                var covered = parseInt(sourceValue) >= consumerQuantity;
                log.debug("  System's " + prodAttr + " covered: " + covered);

                var reason = null;
                if (!covered) {
                    reason = StatusReasonGenerator.buildReason(prodAttr.toUpperCase(),
                                                               complianceTracker.type,
                                                               complianceTracker.id,
                                                               consumerQuantity,
                                                               sourceValue);
                }
                return reason;
            }
        };
    },

    /**
     * Adjusts the value of what is covered for a specific attribute based on
     * the consumer and certain pool/product attributes.
     *
     *      attribute - Attribute we are enforcing.
     *      consumer - consumer in question
     *      attributeValue - sockets covered by normal accumulation rules
     *                     (sockets attribute * quantity)
     *
     *      Return: actual value covered
     */
    adjustCoverage: function(attribute, consumer, attributeValue, entitlements) {
        if (Utils.inArray(GLOBAL_ATTRIBUTES, attribute)) {
            attributeValue = GlobalAttributeCalculator.getValue(attribute, entitlements);
        }

        return attributeValue;
    },

    /**
     *  Determines the amount of consumer coverage provided by the specified
     *  stack.
     */
    getStackCoverage: function(complianceTracker, consumer, entitlements) {
        log.debug("Coverage calculator is checking stack coverage...");
        var conditions = this.getDefaultConditions();

        var complianceAttributes = getComplianceAttributes(consumer);
        for (var attrIdx in complianceAttributes) {
            var nextAttr = complianceAttributes[attrIdx];
            if (complianceTracker.enforces(nextAttr)) {
                complianceTracker.setAccumulatedValue(nextAttr, this.adjustCoverage(nextAttr,
                            consumer,
                            complianceTracker.getAccumulatedValue(nextAttr),
                            entitlements));
            }
        }
        var coverage = this.getCoverageForTracker(complianceTracker, consumer, conditions);
        log.debug("Stack coverage: " + coverage.percentage);
        return coverage;
    },

    /**
     *  Determines the amount of consumer coverage provided by the specified
     *  source.
     *
     *  Coverage consists of:
     *     covered: If the source covers the consumer
     *     percentage: percentage of the consumer covered by the specified source values.
     *     reasons: The reasons why the source does not cover the consumer.
     *
     *  The supplied conditions are checked to determine coverage. Only
     *  attribute values defined in getComplianceAttributes() for the given
     *  consumer are checked.
     *
     *  If an attribute value is not found in the sourceValues, it is considered
     *  to be covered.
     */
    getCoverageForTracker: function (complianceTracker, consumer, conditions) {
        var coverageCount = 0;
        var reasons = [];
        var complianceAttributes = getComplianceAttributes(consumer);
        for (var attrIdx in complianceAttributes) {
            var attr = complianceAttributes[attrIdx];

            // if the value doesn't exist we do not enforce it.
            if ( !complianceTracker.enforces(attr) ) {
                coverageCount++;
                continue;
            }

            // Make sure it covers the consumer's values
            var condition = attr in conditions ? conditions[attr] : conditions["default"];
            var reason = condition(complianceTracker, attr, consumer);

            if (!reason) {
                coverageCount++;
            } else {
                reasons.push(reason);
            }
        }

        var percentage = coverageCount / complianceAttributes.length;
        var coverage = {
            covered: percentage == 1,
            percentage: percentage,
            reasons: reasons
        };

        return coverage;
    },

    /**
     *  Determines the quantity of entitlements needed from a pool in order
     *  for the stack to cover a consumer.
     */
    getQuantityToCoverStack : function(complianceTracker, pool, consumer, entitlements) {
        // Check the number required for every attribute
        // and take the max.

        // Some stacked attributes do not affect the quantity needed to
        // make the stack valid. Stacking multiple instances of 'arch'
        // does nothing (there is no quantity).
        var stackableAttrsNotAffectingQuantity = [ARCH_ATTRIBUTE, GUEST_LIMIT_ATTRIBUTE];
        var complianceAttributes = getComplianceAttributes(consumer);
        var complianceAttributesToUse = [];

        for (var attrIdx in complianceAttributes) {
            var attr = complianceAttributes[attrIdx];
            if (!Utils.inArray(stackableAttrsNotAffectingQuantity, attr) && pool.hasProductAttribute(attr)) {
                complianceAttributesToUse.push(attr);
            } else {
                log.debug("  Skipping " + attr + " because it does not affect the quantity or is not enforced");
            }
        }

        log.debug("Determining number of entitlements to cover consumer...");

        var increment = (pool.hasProductAttribute(INSTANCE_ATTRIBUTE) && !Utils.isGuest(consumer)) ? parseInt(pool.getProductAttribute(INSTANCE_ATTRIBUTE)): 1;

        var covered = false;
        var quantity = 0;
        var startedEmpty = complianceTracker.empty;
        do {
            if (startedEmpty || quantity != 0) {
                // If the stack is empty, we can assume at least one is needed. This is to
                // work around situations where the coverage comes back as 100% because no
                // attributes are being enforced.
                complianceTracker.updateAccumulatedFromPool(pool, increment);
                quantity += increment;
            }
            startedEmpty = true;
            var coverage = CoverageCalculator.getStackCoverage(complianceTracker, consumer, entitlements);
            covered = true;
            for (var i = 0; i < coverage.reasons.length; i++) {
                var attribute = coverage.reasons[i]["key"].toLowerCase();
                if (Utils.inArray(complianceAttributesToUse, attribute)) {
                    covered = false;
                }
            }
        // Loop while the stack isn't covered for all the stackable attributes we're checking
        // and there is enough available quantity for the next iteration
        } while (!covered && ((quantity + increment <= pool.getAvailable()) || pool.isUnlimited()));

        log.debug("Quantity required to cover consumer: " + quantity);
        return quantity;
    }
};

/**
 *   A compliance tracker is an Object that helps to track the state of
 *   an entitlement or set of stackable entitlements.
 *   A stack changes what it provides based
 *   on what entitlements make up the stack. For example, if we have
 *   2 stacked entitlements providing 4 sockets, and we add another
 *   stackable entitlement providing 4GB of RAM, then the stack
 *   will provide 4 sockets and 4GB of ram. A compliance tracker tracks
 *   these accumulated values as entitlements are added.
 */
function createComplianceTracker(consumer, id) {
    return {
        id: id,
        type: id == null ? "ENTITLEMENT" : "STACK",
        consumer: consumer,

        // The IDs of entitlements that have been added to this tracker.
        entitlementIds: [],

        // Did we detect a "host restricted" pool anywhere in the stack?
        hostRestricted: null,

        /**
         *  The accumulated values from adding entitlements to this tracker.
         *  This is a mapping of product attribute to the accumulated value.
         */
        accumulatedValues: {},

        /**
         * Has *anything* been added to this stack, flips to true as soon as
         * we add something to the tracker. This is to cover situations where
         * no attributes are being enforced. (i.e. consumer may be a guest,
         * certain types of subscriptions, etc)
         */
        empty: true,

        // Sets the accumulated value for the specified product attribute.
        setAccumulatedValue: function(attribute, value) {
            this.accumulatedValues[attribute] = value;
        },

        /**
         *   Retrieves the accumulated value for the specified product
         *   attribute. Returns null if it does not exist.
         */
        getAccumulatedValue: function(attribute) {
            if (attribute in this.accumulatedValues) {
                return this.accumulatedValues[attribute];
            }

            // No quantity available
            return null;
        },

        /**
         *  Determines whether the specified product attribute is being enforced
         *  by the stack. An attribute is being enforced if the tracker has
         *  an accumulated value set.
         */
        enforces: function(attribute) {
            // Guests are not subjected to Sockets/RAM/Cores/Vcpus limitations if
            // using a host-restricted sub-pool.
            if (this.hostRestricted !== null && Utils.isGuest(this.consumer) &&
                    contains(UNCHECKED_WHEN_HOST_RESTRICTED, attribute)) {
                log.debug("Not enforcing " + attribute + ": guest / host restricted pool");
                return false;
            }

            var enforcing = attribute in this.accumulatedValues;
            log.debug("Enforcing " + attribute + ": " + enforcing);
            return enforcing;
        },

        /**
         *  Gets the set of accumulation strategies for the tracker. Strategies are
         *  mapped product attribute to strategy function.
         *
         *  A strategy should define how a product attribute's value should be
         *  accumulated for a stack. For example, the default strategy assumes
         *  an integer value, and simply multiplys the product attribute's value
         *  by the specified quantity and appends it to the trackers current
         *  accumulated value for that attribute.
         *
         *  A strategy function must be defined with the following signature:
         *       function (currentStackValue, poolValue, quantity);
         *
         *       currentStackValue: The current accumulated value in this tracker.
         *       poolValue: The value defined by the product attribute.
         *       quantity: The quantity to apply.
         *
         */
        getAccumulationStrategy: function (attr) {

            var strategies = {

                default: function (currentStackValue, poolValue, pool, quantity) {
                    var stackValue = currentStackValue | 0;
                    stackValue = stackValue + (parseInt(poolValue) * quantity);
                    return stackValue;
                },

                /**
                 *  Architecture is accumulated by adding each pool value to
                 *  a list of arch strings. Each pool value is a , seperated
                 *  string of supported archs.
                 */
                arch: function (currentStackValue, poolValue, pool, quantity) {
                    var stackValue = currentStackValue || [];
                    stackValue.push(poolValue);
                    return stackValue;
                },

                sockets: function (currentStackValue, poolValue, pool, quantity) {
                    var stackValue = currentStackValue | 0;
                    var increment = parseInt(pool.getProductAttribute(INSTANCE_ATTRIBUTE)) || 1;
                    // use lowest quantity evenly divisible by the instance multiplier
                    var adjustedQuantity = quantity - (quantity % increment);
                    stackValue = stackValue + ((parseInt(poolValue) * adjustedQuantity) / increment);
                    return stackValue;
                },

                guest_limit: function (currentStackValue, poolValue, pool, quantity) {
                    return -1; //Value doesn't matter, just need it to be enforced
                }
            };

            var strategy = strategies.default;
            if (attr in strategies) {
                strategy = strategies[attr];
            }
            return strategy;
        },

        /**
         *  Updates the tracker's accumulated values based on the provided pool.
         *
         *  Accumulated values are calculated from the product attributes on a
         *  pool. Can be used to simulate adding x entitlements to this tracker
         *  without an actual entitlement being needed. This is helpful
         *  when Autobind wants to track a stack without having actual entitlements
         *  to apply. i.e I already have 4 entitlements, but I want this tracker
         *  to behave as if I gave it x entitlement from the specified pool.
         */
        updateAccumulatedFromPool: function (pool, quantityToTake) {
            log.debug("Updating compliance tracker's values from pool, quantity: " + quantityToTake);
            if (quantityToTake > 0) {
                this.empty = false;
            }

            if (pool.getAttribute(REQUIRES_HOST_ATTRIBUTE)) {
                this.hostRestricted = pool.getAttribute(REQUIRES_HOST_ATTRIBUTE);
            }

            var complianceAttributes = getComplianceAttributes(this.consumer);
            for (var attrIdx in complianceAttributes) {
                var nextAttr = complianceAttributes[attrIdx];
                var poolValue = pool.getProductAttribute(nextAttr);
                if (poolValue !== null) {
                    var stackValue = this.enforces(nextAttr) ? this.getAccumulatedValue(nextAttr) : null;
                    var accumulate = this.getAccumulationStrategy(nextAttr);
                    this.setAccumulatedValue(nextAttr, accumulate(stackValue, poolValue, pool, quantityToTake));
                    log.debug("  Set " + nextAttr + " quantity on stack to " +
                        this.getAccumulatedValue(nextAttr));
                }
            }
        },

        /**
         *  Updates the tracker's accumulated values based on the provided
         *  entitlement. Entitlements that are added can only be added once.
         */
        updateAccumulatedFromEnt: function(ent) {
            log.debug("Updating compliance tracker's values from entitlement.");
            if (this.type == "ENTITLEMENT" && this.entitlementIds.length == 0) {
                this.id = ent.id;
            }
            if (ent.id in this.entitlementIds) {
                // This entitlement was already added.
                return;
            }
            this.empty = false;

            // Keep track of any entitlements that we've already
            // added to the stack so that our accumulated values do not
            // get out of whack.
            this.entitlementIds.push(ent.id);

            // If quantity is > 1 but the entitlement is not stacked
            // only calculate compliance for quantity 1
            var quantity = ent.quantity;
            if (!is_stacked(ent) && ent.quantity > 1) {
                quantity = 1;
            }
            this.updateAccumulatedFromPool(ent.pool, quantity);
        }

    };
}

/**
 *  Creates a compliance tracker from the specified pool and sets the
 *  accumulated values of the pool's product attributes and sets
 *  them to empty. This is useful for Autobind in a situation where
 *  it comes across a stackable pool, but there are no entitlements
 *  consumed from it. It can create a tracker based on that pool,
 *  and the Coverage Calculator can then use it to determine how
 *  many entitlements from this pool have to be stacked in order
 *  to cover the consumer.
 */
function createComplianceTrackerFromPool(pool, consumer) {
    var complianceTracker = createComplianceTracker(consumer, pool.getProductAttribute("stacking_id"));
    // There are no entitlements for this stack, but
    // we have to tell the stack what attributes it must
    // enforce. This is determined by attributes that are
    // set on the pool. Because there are no entitlements
    // we set the quantity to 0.
    complianceTracker.updateAccumulatedFromPool(pool, 0);
    return complianceTracker;
}


// given 2 pools, select the best one. It is a assumed that the pools offer the
// same selection of products.
// returns true if pool1 is a better choice than pool2, else false
function comparePools(pool1, pool2) {

    // Prefer a virt_only pool over a regular pool, else fall through to the next rules.
    // At this point virt_only pools will have already been filtered out by the pre rules
    // for non virt machines.
    if (Utils.equalsIgnoreCase(pool1.getProductAttribute(VIRT_ONLY), "true") && !Utils.equalsIgnoreCase(pool2.getProductAttribute(VIRT_ONLY), "true")) {
        return true;
    }
    else if (Utils.equalsIgnoreCase(pool2.getProductAttribute(VIRT_ONLY)) && !Utils.equalsIgnoreCase(pool1.getProductAttribute(VIRT_ONLY), "true")) {
        return false;
    }

    // If both virt_only, prefer one with host_requires, otherwise keep looking
    // for a reason to pick one or the other. We know that the host must match
    // as pools are filtered before even being passed to select best pools.
    if (Utils.equalsIgnoreCase(pool1.getProductAttribute(VIRT_ONLY), "true") && Utils.equalsIgnoreCase(pool2.getProductAttribute(VIRT_ONLY), "true")) {
        if (pool1.getAttribute(REQUIRES_HOST_ATTRIBUTE) !== null && pool2.getAttribute(REQUIRES_HOST_ATTRIBUTE) === null) {
            return true;
        }
        if (pool2.getAttribute(REQUIRES_HOST_ATTRIBUTE) !== null && pool1.getAttribute(REQUIRES_HOST_ATTRIBUTE) === null) {
            return false;
        }
        // If neither condition is true, no preference...
    }

    // If two pools are still considered equal, select the pool that expires first
    if (pool2.endDate > pool1.endDate) {
        return true;
    }

}

function isLevelExempt (level, exemptList) {
    for (var j = 0; j < exemptList.length; j++) {
        var exemptLevel = exemptList[j];

        if (Utils.equalsIgnoreCase(exemptLevel, level)) {
            return true;
        }
    }
    return false;
}

/*
 * Code to validate pools before adding them to an activation key.
 * This way we can't create activation keys that can never successfully
 * register a consumer.
 */
var ActivationKey = {

    get_attribute_context: function() {
        context = JSON.parse(json_context);

        // Pool to validate
        context.pool = createPool(context.pool);
        context.key = createActivationKey(context.key);

        return context;
    },

    /*
     * If the activation key is only for physical machines, the quantity of instance
     * based pools must be either null or evenly divisible by the instance multiplier
     */
    validate_instance: function(key, pool, quantity, result) {
        if (key.physical_only && quantity !== null && pool.hasAttribute(INSTANCE_ATTRIBUTE)) {
            var instance_multi = parseInt(pool.getAttribute(INSTANCE_ATTRIBUTE));
            if (quantity % instance_multi != 0) {
                result.addError("rulefailed.invalid.quantity.instancebased.physical");
            }
        }
    },

    /*
     * Quantity of the pool we're attaching must be null or positive.
     * There must be sufficient quantity available if quantity is specified.
     */
    validate_quantity: function(key, pool, quantity, result) {
        if (quantity !== null && quantity < 1) {
            result.addError("rulefailed.invalid.quantity");
        }

        var minRequiredQuantity = 1;
        // Instance based quantity
        if (key.physical_only && pool.hasAttribute(INSTANCE_ATTRIBUTE)) {
            minRequiredQuantity = parseInt(pool.getAttribute(INSTANCE_ATTRIBUTE));
        }

        if (!pool.isUnlimited() &&
                (pool.quantity < minRequiredQuantity ||
                 (quantity !== null && quantity > pool.quantity))) {
            result.addError("rulefailed.insufficient.quantity");
        }
        if (!Utils.isMultiEnt(pool)) {
            // If the pool isn't multi-entitlable, we can only accept null quantity and 1
            if (quantity !== null && quantity > 1) {
                result.addError("rulefailed.invalid.nonmultient.quantity");
            }
            // Don't allow non-multi-ent pools to be attached to an activation key more than once
            for (var i = 0; i < key.pools.length; i++) {
                if (pool.id == key.pools[i].pool.id) {
                    result.addError("rulefailed.already.exists");
                    break;
                }
            }
        }
    },

    /*
     * We can only allow one required host per activation key, otherwise the key becomes
     * useless.
     *
     * If there are physical only pools, we cannot require hosts, because the attributes
     * are mutually exclusive.
     */
    validate_requires_host: function(key, pool, result) {
        pool_requires = pool.getAttribute(REQUIRES_HOST_ATTRIBUTE);
        if (pool_requires !== null && pool_requires != "") {
            if (key.requires_host !== null && pool_requires != key.requires_host) {
                result.addError("rulefailed.multiple.host.restrictions");
            }
            if (key.physical_only) {
                result.addError("rulefailed.host.restriction.physical.only");
            }
        }
    },

    /*
     * Do not allow pools that require a "person" type consumer to be added to an activation key.
     * Do not allow multiple different consumer types to be required on the same activation key.
     */
    validate_consumer_type: function(key, pool, result) {
        pool_consumer_type = pool.getAttribute("requires_consumer_type");
        if (pool_consumer_type == "person") {
            result.addError("rulefailed.actkey.cannot.use.person.pools");
        }
        else if (key.consumer_type !== null && pool_consumer_type !== null &&
                key.consumer_type != pool_consumer_type) {
            result.addError("rulefailed.actkey.single.consumertype");
        }
    },

    validate_physical_virtual: function(key, pool, result) {
       var virt_pool = Utils.equalsIgnoreCase('true', pool.getAttribute(VIRT_ONLY));
       var phys_pool = Utils.equalsIgnoreCase('true', pool.getAttribute(PHYSICAL_ONLY));
       if (virt_pool && key.physical_only) {
           result.addError("rulefailed.virtonly.on.physical.key");
       }
       if (phys_pool && key.virt_only) {
           result.addError("rulefailed.physicalonly.on.virt.key");
       }
    },

    validate_pool: function() {
        // TODO: rewrite Entitlement rules in such a way that we can use overlapping code.
        // pre-entitlement rules should probably be redesigned to take only one call.
        var result = Entitlement.ValidationResult();
        context = this.get_attribute_context();
        key = context.key;
        pool = context.pool;
        quantity = context.quantity;

        this.validate_quantity(key, pool, quantity, result);
        this.validate_consumer_type(key, pool, result);
        this.validate_requires_host(key, pool, result);
        this.validate_physical_virtual(key, pool, result);
        this.validate_instance(key, pool, quantity, result);
        return JSON.stringify(result);
    }
}

var Entitlement = {

    // defines mapping of product attributes to functions
    // the format is: <function name>:<order number>:<attr1>:...:<attrn>, comma-separated ex.:
    // func1:1:attr1:attr2:attr3, func2:2:attr3:attr4
    attribute_mappings: function() {
        return  "architecture:1:arch," +
            "sockets:1:sockets," +
            "ram:1:ram," +
            "cores:1:cores," +
            "requires_consumer_type:1:requires_consumer_type," +
            "virt_only:1:virt_only," +
            "virt_limit:1:virt_limit," +
            "requires_host:1:requires_host," +
            "instance_multiplier:1:instance_multiplier," +
            "guest_limit:1:guest_limit," +
            "vcpu:1:vcpu," +
            "physical_only:1:physical_only";
    },

    ValidationResult: function () {
        var result = {
            errors: [],
            warnings: [],

            addWarning: function(message) {
               this.warnings.push(message);
            },

            addError: function(message) {
               this.errors.push(message);
            }
        };

        return result;
    },

    get_attribute_context: function() {
        context = JSON.parse(json_context);

        if ("pool" in context) {
            context.pool = createPool(context.pool);
        }

        context.hasEntitlement = function(poolId) {
            for (var k = 0; k < this.consumerEntitlements.length; k++) {
                var e = this.consumerEntitlements[k];
                if (e.pool.id == poolId) {
                    return true;
                }
            }
            return false;
        };

        // Get attribute from a pool. Pool attributes are preferred
        // but if not found, the top level product attributes will be
        // checked.
        context.getAttribute = function(pool, attributeName) {
            var attr = pool.getAttribute(attributeName);
            if (!attr) {
                attr = pool.getProductAttribute(attributeName);
            }
            return attr;
        };

        return context;
    },

    pre_virt_only: function() {
        var result = Entitlement.ValidationResult();
        context = Entitlement.get_attribute_context();
        var caller = context.caller;
        var consumer = context.consumer;
        var virt_pool = Utils.equalsIgnoreCase('true', context.getAttribute(context.pool, VIRT_ONLY));
        var pool_derived = Utils.equalsIgnoreCase('true', context.getAttribute(context.pool, POOL_DERIVED));
        var guest = Utils.isGuest(consumer);

        if (virt_pool) {
            if (consumer.type.manifest) {
                if (pool_derived) {
                    result.addError("pool.not.available.to.manifest.consumers");
                }
            }
            else if (!guest) {
                if (BEST_POOLS_CALLER == caller ||
                    BIND_CALLER == caller) {
                    result.addError("rulefailed.virt.only");
                }
                else {
                    result.addWarning("rulewarning.virt.only");
                }
            }
        }
        return JSON.stringify(result);
    },

    pre_physical_only: function() {
        var result = Entitlement.ValidationResult();
        context = Entitlement.get_attribute_context();
        var caller = context.caller;
        var consumer = context.consumer;
        var physical_pool = Utils.equalsIgnoreCase('true', context.getAttribute(context.pool, PHYSICAL_ONLY));
        var guest = Utils.isGuest(consumer);

        if (physical_pool) {
            if (!consumer.type.manifest && guest) {
                if (BEST_POOLS_CALLER == caller ||
                    BIND_CALLER == caller) {
                    result.addError("rulefailed.physical.only");
                }
                else {
                    result.addWarning("rulewarning.physical.only");
                }
            }
        }
        return JSON.stringify(result);
    },

    pre_requires_host: function() {
        var result = Entitlement.ValidationResult();
        context = Entitlement.get_attribute_context();

        // requires_host derived pools not available to manifest
        if (context.consumer.type.manifest) {
            result.addError("pool.not.available.to.manifest.consumers");
            return JSON.stringify(result);
        }

        if (!context.consumer.facts["virt.uuid"]) {
            result.addError("rulefailed.virt.only");
            return JSON.stringify(result);
        }

        if (!context.hostConsumer ||
            context.hostConsumer.uuid != context.getAttribute(context.pool,
                                                                   REQUIRES_HOST_ATTRIBUTE)) {
            result.addError("virt.guest.host.does.not.match.pool.owner");
        }
        return JSON.stringify(result);
    },

    pre_requires_consumer_type: function() {
        var result = Entitlement.ValidationResult();
        context = Entitlement.get_attribute_context();
        // Distributors can access everything
        if (context.consumer.type.manifest) {
            return JSON.stringify(result);
        }

        var requiresConsumerType = context.getAttribute(context.pool, "requires_consumer_type");
        // skip if the attribtue is not defined, or if generating an uebercert.
        if (requiresConsumerType != null &&
            context.consumer.type.label != UEBERCERT_TYPE ) {
            // Consumer types need to match (except below)
            if (requiresConsumerType != context.consumer.type.label) {
                // Allow hypervisors to be like systems
                if (!(requiresConsumerType == SYSTEM_TYPE &&
                    context.consumer.type.label == HYPERVISOR_TYPE)) {
                     result.addError("rulefailed.consumer.type.mismatch");
                }
            }
        }
        return JSON.stringify(result);
    },

    pre_virt_limit: function() {
    },

    pre_guest_limit: function() {
    },

    pre_vcpu: function() {
        var result = Entitlement.ValidationResult();
        context = Entitlement.get_attribute_context();

        var consumer = context.consumer;
        var pool = context.pool;
        var caller = context.caller;

        if (!consumer.type.manifest) {
            if (Utils.isGuest(consumer)) {
                var consumerCores = FactValueCalculator.getFact(VCPU_ATTRIBUTE, consumer);
                if (consumerCores && !pool.getProductAttribute("stacking_id")) {
                    var poolCores = parseInt(pool.getProductAttribute(VCPU_ATTRIBUTE));
                    if (poolCores > 0 && poolCores < consumerCores) {
                        result.addWarning("rulewarning.unsupported.number.of.vcpus");
                    }
                }
            }
        }
        // Don't check if the manifest consumer is capable,
        // this attribute has existed for a while
        // now, we have just never checked it before
        return JSON.stringify(result);
    },

    pre_architecture: function() {
        var result = Entitlement.ValidationResult();
        context = Entitlement.get_attribute_context();
        var consumer = context.consumer;
        if (consumer.type.manifest) {
            return JSON.stringify(result);
        }

        if (!architectureMatches(context.pool.getProductAttribute(ARCH_ATTRIBUTE),
                                 context.consumer.facts[ARCH_FACT],
                                 context.consumer.type.label)) {
            result.addWarning("rulewarning.architecture.mismatch");
        }
        return JSON.stringify(result);
    },

    pre_sockets: function() {
        var result = Entitlement.ValidationResult();
        context = Entitlement.get_attribute_context();
        var consumer = context.consumer;
        var pool = context.pool;

        if (consumer.type.manifest || Utils.isGuest(consumer)) {
            return JSON.stringify(result);
        }

        //usually, we assume socket count to be 1 if it is undef. However, we need to know if it's
        //undef here in order to know to skip the socket comparison (per acarter/jomara)
        if (consumer.facts[SOCKET_FACT] && !pool.getProductAttribute("stacking_id")) {
            if ((parseInt(pool.getProductAttribute(SOCKETS_ATTRIBUTE)) > 0) &&
                (parseInt(pool.getProductAttribute(SOCKETS_ATTRIBUTE)) < parseInt(consumer.facts[SOCKET_FACT]))) {
                result.addWarning("rulewarning.unsupported.number.of.sockets");
            }
        }
        return JSON.stringify(result);
    },

    pre_cores: function() {
        var result = Entitlement.ValidationResult();
        context = Entitlement.get_attribute_context();

        var consumer = context.consumer;
        var pool = context.pool;
        var caller = context.caller;

        if (!consumer.type.manifest) {
            if (!Utils.isGuest(consumer)) {
                var consumerCores = FactValueCalculator.getFact(CORES_ATTRIBUTE, consumer);
                if (consumerCores && !pool.getProductAttribute("stacking_id")) {
                    var poolCores = parseInt(pool.getProductAttribute(CORES_ATTRIBUTE));
                    if (poolCores > 0 && poolCores < consumerCores) {
                        result.addWarning("rulewarning.unsupported.number.of.cores");
                    }
                }
            }
        }
        else {
            if (!Utils.isCapable(consumer, CORES_ATTRIBUTE)) {
                if (BEST_POOLS_CALLER == caller ||
                    BIND_CALLER == caller) {
                    result.addError("rulefailed.cores.unsupported.by.consumer");
                }
                else {
                    result.addWarning("rulewarning.cores.unsupported.by.consumer");
                }
            }
        }
        return JSON.stringify(result);
    },

    pre_ram: function() {
        var result = Entitlement.ValidationResult();
        context = Entitlement.get_attribute_context();
        var caller = context.caller;
        var consumer = context.consumer;

        if (!consumer.type.manifest) {
            var consumerRam = FactValueCalculator.getFact(RAM_ATTRIBUTE, consumer);
            log.debug("Consumer has " + consumerRam + "GB of RAM.");

            var productRam = parseInt(context.pool.getProductAttribute(RAM_ATTRIBUTE));
            log.debug("Product has " + productRam + "GB of RAM");
            if (consumerRam > productRam && !context.pool.getProductAttribute("stacking_id")) {
                result.addWarning("rulewarning.unsupported.ram");
            }
        }
        else {
            if (!Utils.isCapable(consumer, RAM_ATTRIBUTE)) {
                if (BEST_POOLS_CALLER == caller ||
                    BIND_CALLER == caller) {
                    result.addError("rulefailed.ram.unsupported.by.consumer");
                }
                else {
                    result.addWarning("rulewarning.ram.unsupported.by.consumer");
                }
            }
        }
        return JSON.stringify(result);
    },

    pre_instance_multiplier: function() {
        var result = Entitlement.ValidationResult();
        context = Entitlement.get_attribute_context();
        var pool = context.pool;
        var caller = context.caller;
        var consumer = context.consumer;
        log.debug("pre_instance_multiplier being called by [" + caller + "]");

        // only block quantities that do not evenly divide the multiplier
        // and only on physical systems

        if (!consumer.type.manifest) {
            if (BIND_CALLER == caller && !Utils.isGuest(consumer)) {
                var multiplier = pool.getProductAttribute(INSTANCE_ATTRIBUTE);
                log.debug("instance_multiplier: [" + multiplier + "]");

                var mod = (context.quantity % multiplier);
                log.debug("result [" + context.quantity  + " % " +
                    multiplier + " = " + mod + "]");
                if (mod != 0) {
                    log.debug("quantity NOT divisible by multplier");
                    result.addError("rulefailed.quantity.mismatch");
                }
            }
        }
        else {
            if (!Utils.isCapable(consumer, INSTANCE_ATTRIBUTE)) {
                if (BEST_POOLS_CALLER == caller ||
                    BIND_CALLER == caller) {
                    result.addError("rulefailed.instance.unsupported.by.consumer");
                }
                else {
                    result.addWarning("rulewarning.instance.unsupported.by.consumer");
                }
            }
        }
        return JSON.stringify(result);
    },

    pre_global: function() {
        var result = Entitlement.ValidationResult();
        context = Entitlement.get_attribute_context();
        var pool = context.pool;
        var caller = context.caller;
        var consumer = context.consumer;

        log.debug("pre_global being called by [" + caller + "]");

        // Manifest should be able to extract by default.
        if (consumer.type.manifest) {
            // Distributors should not be able to consume from pools with sub products
            // if they are not capable of supporting them.
            //
            // NOTE: We check for subProductId in the pre_global space because it is not
            // a product attribute.
            if (pool.derivedProductId && !Utils.isCapable(consumer, "derived_product")) {
                if (BEST_POOLS_CALLER == caller || BIND_CALLER == caller) {
                    result.addError("rulefailed.derivedproduct.unsupported.by.consumer");
                }
                else {
                    result.addWarning("rulewarning.derivedproduct.unsupported.by.consumer");
                }
            }
            return JSON.stringify(result);
        }

        if (context.hasEntitlement(pool.id) && !Utils.isMultiEnt(pool)) {
            result.addError("rulefailed.consumer.already.has.product");
        }

        if (context.quantity > 1 && !Utils.isMultiEnt(pool)) {
            result.addError("rulefailed.pool.does.not.support.multi-entitlement");
        }


        // If the product has no required consumer type, assume it is restricted to "system".
        // "hypervisor"/"uebercert" type are essentially the same as "system".
        if (!pool.getProductAttribute("requires_consumer_type")) {
            if (consumer.type.label != SYSTEM_TYPE && consumer.type.label != HYPERVISOR_TYPE &&
                    consumer.type.label != UEBERCERT_TYPE) {
                result.addError("rulefailed.consumer.type.mismatch");
            }
        }

        if (pool.restrictedToUsername != null && pool.restrictedToUsername != consumer.username) {
            result.addError("pool.not.available.to.user, pool= '" + pool.restrictedToUsername + "', actual username='" + consumer.username + "'" );
        }

        return JSON.stringify(result);
    },

}

var Autobind = {

    /*
     * An entitlement group is an abstraction that allows us to check
     * and modify groups of available subscriptions uniformly.  That way we don't
     * have to know if it's a stack, single entitlement, etc... It is either valid
     * or not, and provides products.
     */
    create_entitlement_group: function(stackable, stack_id, installed_ids, consumer, attached_ents, consider_derived) {
        return {
            pools: [],
            stackable: stackable,
            stack_id: stack_id,
            installed: installed_ids,
            consumer: consumer,
            attached_ents: attached_ents,

            // Indicates we are trying to autobind a host to things that would unlock pools for it's guests.
            // Implies we should look at derived product data if it exists on the pool, otherwise we look at
            // the usual. (product ID, attributes, provided products)
            consider_derived: consider_derived,

            /*
             * Method returns whether or not it is possible for the entitlement
             * group to fully cover the consumer.  If this is stackable, some subset
             * of entitlements must become fully compliant.  Pools that break compliance
             * are removed.
             */
            validate: function(context) {
                var all_ents = this.get_all_ents(this.pools).concat(this.attached_ents);

                if (all_ents.length == 0) {
                    log.debug("No entitlements");
                    return false;
                }

                if (!this.stackable && Compliance.getEntitlementCoverage(this.consumer, all_ents[0], all_ents).covered) {
                    return true;
                }
                else if (!this.stackable) {
                    log.debug("Not stackable...");
                    return false;
                }
                // At this point, we must be stackable
                var coverage = Compliance.getStackCoverage(this.consumer, this.stack_id, all_ents);
                if (!coverage.covered) {
                    log.debug("stack " + this.stack_id + " is partial with all entitlements stacked.");
                    var attrs_to_remove = [];
                    for(var i = 0; i < coverage.reasons.length; i++) {
                        if (coverage.reasons[i]["key"] == ARCH_ATTRIBUTE) {
                            // Must have already attached a bad arch
                            // entitlement, we already pruned them out of pools
                            log.debug("This stack contains an arch mismatch");
                            return false;
                        }
                        var attribute = coverage.reasons[i]["key"].toLowerCase();
                        // Shouldn't have to worry about duplicates because
                        // we're dealing with only this stack
                        attrs_to_remove.push(attribute);
                    }
                    var pools_without_bad_attrs = [];
                    // remove all pools with attributes that we cannot support
                    for (var j = this.pools.length - 1; j >= 0; j--) {
                        var pool = this.pools[j];
                        var pool_is_valid = true;
                        for(var i = 0; i < attrs_to_remove.length; i++) {
                            var bad_attribute = attrs_to_remove[i];
                            var prodAttrValue = pool.getProductAttribute(bad_attribute);
                            if (prodAttrValue) {
                                pool_is_valid = false;
                                break;
                            }
                        }
                        if (pool_is_valid) {
                            pools_without_bad_attrs.push(pool);
                        }
                    }
                    this.pools = pools_without_bad_attrs;
                    all_ents = this.get_all_ents(this.pools).concat(this.attached_ents);
                    coverage = Compliance.getStackCoverage(consumer, this.stack_id, all_ents);
                    return coverage.covered;
                }
                return true;
            },

            get_num_host_specific: function() {
                var count = 0;
                for (var i = 0; i < this.pools.length; i++) {
                    var pool = this.pools[i];
                    if (pool.getAttribute(REQUIRES_HOST_ATTRIBUTE) != null) {
                        count++;
                    }
                }
                return count;
            },

            get_num_virt_only: function() {
                var count = 0;
                for (var i = 0; i < this.pools.length; i++) {
                    var pool = this.pools[i];
                    if (Utils.equalsIgnoreCase('true',  pool.getProductAttribute(VIRT_ONLY))) {
                        count++;
                    }
                }
                return count;
            },

            /*
             * 2^n again, but this time n is the number of stackable attributes that aren't arch.
             * This method generates combinations of compliance attributes so that we can attempt
             * to become compliant without some pools.
             *
             * This avoids parallel stacks where removing
             * any 1 sockets pool or any 1 cores pool will become incompliant, but removing all of
             * either one will not.
             */
            get_sets: function(list, max_length) {
                if (list.length == 0) {
                    return [[]];
                }
                var results = [];
                var tempSet = this.get_sets(list.slice(1), max_length);
                for (var i=0; i < tempSet.length; i++) {
                    if (tempSet[i].length < max_length) {
                        results.push([list[0]].concat(tempSet[i]));
                    }
                    results.push(tempSet[i]);
                }
                return results;
            },

            /*
             * Generates sets of attributes to attempt to remove
             *
             * This avoids parallel stacks where removing
             * any 1 sockets pool or any 1 cores pool will become incompliant, but removing all of
             * either one will not.
             */
            get_attribute_sets: function(pools) {
                var stack_attributes = [];
                // get unique list of additive stack attributes
                var complianceAttributes = getComplianceAttributes(this.consumer);
                for (var attrIdx in complianceAttributes) {
                    var attr = complianceAttributes[attrIdx];
                    if (attr != ARCH_ATTRIBUTE) {
                        // Only check attributes that the pools actually use
                        for (var poolIdx = 0; poolIdx < pools.length; poolIdx++) {
                            var pool = pools[poolIdx];
                            if (pool.hasProductAttribute(attr)) {
                                stack_attributes.push(attr);
                                break;
                            }
                        }
                    }
                }
                log.debug("entitlement group uses attributes: " + stack_attributes);
                sets = this.get_sets(stack_attributes, stack_attributes.length - 1);
                for (var i = sets.length - 1; i >= 0; i--) {
                    var set = sets[i];
                    if (set.length == 0) {
                        sets.splice(i, 1);
                    }
                }
                return sets;
            },

            /*
             * Remove parallel stacks so we aren't essentially binding two stacks
             * that would be fully compliant on their own
             *
             * Attempts to remove all pools from a group that enforce each set of stackable attributes, then
             * checks compliance.  This prevents us from suggesting two fully compliant stacks that
             * enforce different attributes
             */
            remove_extra_attrs: function() {
                var possible_pool_sets = [];
                possible_pool_sets.push(this.pools);
                var original_provided = this.get_provided_products().length;
                var sets_to_check = this.get_attribute_sets(this.pools); //array of arrays of attributes to remove
                for (var setIdx = 0; setIdx < sets_to_check.length; setIdx++) {
                    var attrs_to_remove = sets_to_check[setIdx];
                    for (var attrIdx = 0; attrIdx < attrs_to_remove.length; attrIdx++) {
                        var attr = attrs_to_remove[attrIdx];

                        var pools_without = [];
                        for (var i = 0; i < this.pools.length; i++) {
                            var pool = this.pools[i];
                            var prodAttrValue = pool.getProductAttribute(attr);
                            if (!prodAttrValue || prodAttrValue === null) {
                                pools_without.push(pool);
                            }
                        }
                        all_ents = this.get_all_ents(pools_without).concat(this.attached_ents);
                        if (Compliance.getStackCoverage(this.consumer, this.stack_id, all_ents).covered && (this.get_provided_products_pools(pools_without).length == original_provided)) {
                            possible_pool_sets.push(pools_without);
                        }
                    }
                }
                var best = 0;
                var best_priority = 0.0;
                var num_pools=this.pools.length;
                for (var i = 0; i < possible_pool_sets.length; i++) {
                    var pools = possible_pool_sets[i];
                    var priority = 0;
                    for (var j = 0; j < pools.length; j++) {
                        var pool = pools[j];
                        // use virt only if possible
                        // if the consumer is not virt, the pool will have been filtered out
                        if (Utils.equalsIgnoreCase(pool.getProductAttribute(VIRT_ONLY), "true")) {
                            priority += 100;
                        }
                        // better still if host_specific
                        if (pool.getAttribute(REQUIRES_HOST_ATTRIBUTE) != null) {
                            priority += 150;
                        }
                    }
                    // Priority per pool, that way we don't tend towards stacks with more pools.
                    priority /= pools.length;
                    if (priority > best_priority) {
                        best_priority = priority;
                        best = i;
                        num_pools = pools.length;
                    } else if (priority == best_priority && num_pools > pools.length) {
                        best = i;
                        num_pools = pools.length;
                    }
                }
                this.pools = possible_pool_sets[best];
            },

            /*
             * Remove all pools that aren't necessary for compliance
             * TODO: needs elaboration
             */
            prune_pools: function() {
                // We know this group is required at this point,
                // so we cannot remove the one pool if it's non-stackable
                if (!this.stackable) {
                    return;
                }
                // Sort pools such that we preserve virt_only and host_requires if possible
                this.pools.sort(this.compare_pools);
                var temp = null;
                var prior_pool_size = this.pools.length;
                var provided_size = this.get_provided_products().length;
                for (var i = this.pools.length - 1; i >= 0; i--) {
                    temp = this.pools[i];
                    this.pools.splice(i, 1);
                    var ents = this.get_all_ents(this.pools);
                    if (ents.length == 0 || !Compliance.getStackCoverage(this.consumer, this.stack_id, ents.concat(this.attached_ents)).covered || this.get_provided_products().length != provided_size) {
                        // if something has broken, we add the pool back
                        this.pools.push(temp);
                    }
                }
                log.debug("removed " + (prior_pool_size - this.pools.length) + " of " + prior_pool_size + " pools");
            },

            /*
             * Sort pools for pruning (helps us later with quantity as well)
             */
            compare_pools: function(pool0, pool1) {
                get_pool_priority = function(pool) {
                    var priority = 0;
                    // use virt only if possible
                    // if the consumer is not virt, the pool will have been filtered out
                    if (Utils.equalsIgnoreCase(pool.getProductAttribute(VIRT_ONLY), "true")) {
                        priority += 100;
                    }
                    // better still if host_specific
                    if (pool.getAttribute(REQUIRES_HOST_ATTRIBUTE) != null) {
                        priority += 150;
                    }
                    return priority;
                };
                var priority0 = get_pool_priority(pool0);
                var priority1 = get_pool_priority(pool1);
                // If two pools are still considered equal, select the pool that expires first
                if (pool0.endDate > pool1.endDate) {
                    priority1 += 1;
                }
                else if (pool0.endDate < pool1.endDate) {
                    priority0 += 1;
                }
                // Sort descending, because it's easier to remove items from
                // an array while going backwards.
                return priority1 - priority0;
            },

            /*
             * Returns a map of pool id to pool quantity for every pool that is required from this group
             */
            get_pool_quantity: function() {
                var result = Utils.getJsMap();
                // Still in priority order, but reversed from prune_pools
                var ents = this.get_all_ents(this.pools);
                for (var i = 0; i < this.pools.length; i++) {
                    var pool = this.pools[i];
                    var increment = 1;
                    if (pool.hasProductAttribute("instance_multiplier") && !Utils.isGuest(this.consumer)) {
                        increment = parseInt(pool.getProductAttribute(INSTANCE_ATTRIBUTE));
                    }

                    //entitlement index matches pool index
                    var current_ent = ents[i];

                    for (var j = increment; j <= pool.currently_available; j += increment) {
                        current_ent.quantity = j;
                        //can probably do this part better.  ex: get compliance once, use compliance reasons to calculate the number required

                        if (this.stackable) {
                            if (Compliance.getStackCoverage(this.consumer, this.stack_id, ents.concat(this.attached_ents)).covered) {
                                result.put(pool.id, j);
                                break;
                            }
                        } else {
                            if (Compliance.getEntitlementCoverage(this.consumer, current_ent, ents.concat(this.attached_ents)).covered) {
                                result.put(pool.id, j);
                                break;
                            }
                        }
                    }
                }
                return result;
            },

            get_all_ents: function(in_pools) {
                var ents = [];
                for (var i = 0; i < in_pools.length; i++) {
                    var pool = in_pools[i];
                    ents.push(get_mock_ent_for_pool(pool, this.consumer));
                }
                return ents;
            },

            add_pool: function(pool) {
                this.pools.push(pool);
            },

            // Eventually, could map pools to provided products to save time
            // checking
            get_provided_products: function() {
                return this.get_provided_products_pools(this.pools);
            },

            // Returns list of all provided product IDs from the given array of pools.
            get_provided_products_pools: function(in_pools) {
                var provided = [];
                for (var i = 0; i < in_pools.length; i++) {
                    var provided_by_pool = in_pools[i].products();

                    // If we are considering derived provided products, check
                    // to see if this pool has any and use them instead of the
                    // regular set if so:
                    if (this.consider_derived && in_pools[i].hasDerived()) {
                      provided_by_pool = in_pools[i].derivedProducts();
                    }

                    for (var j = 0; j < provided_by_pool.length; j++) {
                        var provided_id = provided_by_pool[j];
                        if (provided.indexOf(provided_id) == -1 && this.installed.indexOf(provided_id) >= 0) {
                            provided.push(provided_id);
                        }
                    }
                }
                return provided;
            }
        };
    },

    create_autobind_context: function() {
        var context = JSON.parse(json_context);

        // The considerDerived property indicates if we should look to derived
        // provided products rather than the usual set. Used in situations where
        // we're binding a host to things that will unlock pools for it's
        // guests.
        // Check for unset property for backward compatability with old servers.
        if (!context.hasOwnProperty("considerDerived")) {
            context.considerDerived = false;
        }

        // Also need to convert all pools reported in compliance.
        var compliance = context.compliance;

        // Create the pools for all entitlement maps in compliance.
        // The number of entitlements should be relatively small.
        var createPoolsFor = ["partialStacks",
                              "partiallyCompliantProducts",
                              "compliantProducts"];

        for (var i = 0; i < createPoolsFor.length; i++) {
            var nextMapAttrName = createPoolsFor[i];
            var nextMap = compliance[nextMapAttrName];
            for (var key in nextMap) {
                var ents = nextMap[key];
                for (var entIdx = 0; entIdx < ents.length; entIdx++) {
                    ents[entIdx].pool = createPool(ents[entIdx].pool);
                }
            }
        }

        for (var i = 0; i < context.pools.length; i++) {
            context.pools[i] = createPool(context.pools[i]);
            var pool = context.pools[i];
            if (pool.quantity == -1) {
                // In the unlimited case, we need at most the number required to cover the system
                pool.currently_available = Quantity.get_suggested_pool_quantity(pool, context.consumer, []);
                // Can use an empty list here because global attributes don't necessarily change quantity
            } else {
                pool.currently_available = pool.getAvailable();
            }
            // If the pool is not multi-entitlable, only one may be used
            if (pool.currently_available > 0 && !Utils.isMultiEnt(pool)) {
                pool.currently_available = 1;
            }
        }

        return context;
    },

    /*
     * Only use pools that match the consumer SLA or SLA override, if set
     */
    is_pool_sla_valid: function(context, pool, consumerSLA) {
        var poolSLA = pool.getProductAttribute('support_level');
        var poolSLAExempt = isLevelExempt(pool.getProductAttribute('support_level'), context.exemptList);

        if (!poolSLAExempt && consumerSLA &&
            consumerSLA != "" && !Utils.equalsIgnoreCase(consumerSLA, poolSLA)) {
            log.debug("Skipping pool " + pool.id +
                    " since SLA does not match that of the consumer.");
            return false;
        }
        return true;
    },

    /*
     * If the architecture does not match the consumer, this pool can never be valid
     */
    is_pool_arch_valid: function(context, pool, consumerArch) {
        if (architectureMatches(pool.getProductAttribute(ARCH_ATTRIBUTE),
                consumerArch,
                context.consumer.type)) {
            return true;
        }
        log.debug("Skipping pool " + pool.id + " since the ARCH doesn't match that of the consumer");
        return false;
    },

    // confusing name. checks if the pool is valid on grounds of virt_only
    is_pool_virt_valid: function(pool, isGuest) {
        // if physical, and pool is virt_only, invalid.
        if (!isGuest && pool.hasProductAttribute(VIRT_ONLY)) {
            var valid = !Utils.equalsIgnoreCase('true', pool.getProductAttribute(VIRT_ONLY));
            if (!valid) {
                log.debug("Skipping pool " + pool.id + " since a physical system can not consume from a virt-only pool.");
            }
            return valid;
        }
        return true;
    },

    is_pool_not_empty: function(pool) {
        if (pool.currently_available > 0) {
            return true;
        }
        log.debug("Skipping pool " + pool.id + " since all entitlements have been consumed.");
        return false;
    },

    /*
     * Gets the sla of the consumer, unless serviceLevelOverride is set, in which
     * case we use that.
     */
    get_consumer_sla: function(context) {
        log.debug("context.serviceLevelOverride: " + context.serviceLevelOverride);
        var consumerSLA = context.serviceLevelOverride;
        if (!consumerSLA || consumerSLA == "") {
            consumerSLA = context.consumer.serviceLevel;
                if (!consumerSLA || consumerSLA == "") {
                    consumerSLA = context.owner.defaultServiceLevel;
                }
        }
        return consumerSLA;
    },

    // returns all pools that can be attached to this consumer
    get_valid_pools: function(context) {
        var consumerSLA = this.get_consumer_sla(context);
        var isGuest = Utils.isGuest(context.consumer);
        var consumerArch = ARCH_FACT in context.consumer.facts ?
                context.consumer.facts[ARCH_FACT] : null;
        var valid_pools = [];
        for (var i = 0; i < context.pools.length ; i++) {
            var pool = context.pools[i];

            // Since pool.quantity may change, track initial unlimited state here.
            var pool_not_empty = this.is_pool_not_empty(pool);

            if (this.is_pool_arch_valid(context, pool, consumerArch) &&
                    this.is_pool_virt_valid(pool, isGuest) &&
                    this.is_pool_sla_valid(context, pool, consumerSLA) &&
                    pool_not_empty) {
                valid_pools.push(pool);
            }
        }
        return valid_pools;
    },

    /*
     * Builds entitlement group objects that allow us to treat stacks and individual entitlements the same
     */
    build_entitlement_groups: function(valid_pools, installed, consumer, attached_ents, consider_derived) {
        var ent_groups = [];
        for (var i = 0; i < valid_pools.length; i++) {
            var pool = valid_pools[i];

            if (is_pool_stacked(pool)) {
                var found = false;
                var stack_id = pool.getProductAttribute("stacking_id");
                for (var j = 0; j < ent_groups.length; j++) {
                    ent_group = ent_groups[j];
                    if (ent_group.stack_id == stack_id) {
                        ent_group.add_pool(pool);
                        found = true;
                        break;
                    }
                }
                // If the pool is stackable, and not part of an existing entitlement group, create a new group and add it
                if (!found) {
                    var new_ent_group = this.create_entitlement_group(true, stack_id, installed, consumer, attached_ents, consider_derived);
                    new_ent_group.add_pool(pool);
                    ent_groups.push(new_ent_group);
                }
            } else {
                //if the entitlement is not stackable, create a new stack group for it
                var new_ent_group = this.create_entitlement_group(false, "", installed, consumer, attached_ents, consider_derived);
                new_ent_group.add_pool(pool);
                ent_groups.push(new_ent_group);
            }
        }
        return ent_groups;
    },

    /*
     * Returns the list of productIds that the stack will cover, which the consumer requires.
     */
    get_common_products: function(installed, group) {
        var group_installed = group.get_provided_products();
        var common_products = [];
        for (var i = 0; i < group_installed.length; i++) {
            var inst_id = group_installed[i];
            if (installed.indexOf(inst_id) != -1) {
                common_products.push(inst_id);
            }
        }
        return common_products;
    },

    // been adding more attributes to break ties, there's probably a better way to write this at this point
    find_best_ent_group: function(all_groups, installed) {
        var max_provide = 0;
        var stacked = false;
        var best = null;
        var num_virt_only = 0;
        var num_host_specific = 0;
        for (var i = 0; i < all_groups.length; i++) {
            var group = all_groups[i];
            var intersection = this.get_common_products(installed, group).length;
            var group_host_specific = group.get_num_host_specific();
            var group_virt_only = group.get_num_virt_only();
            // Choose group that provides the most installed products
            if (intersection > max_provide) {
                max_provide = intersection;
                stacked = group.stackable;
                num_virt_only = group_virt_only;
                num_host_specific = group_host_specific;
                best = group;
            }
            if (intersection > 0 && intersection == max_provide) {
                // Break ties with number of host specific pools
                if (num_host_specific < group_host_specific) {
                   best = group;
                   stacked = group.stackable;
                   num_virt_only = group_virt_only;
                   num_host_specific = group_host_specific;
                }
                if (num_host_specific == group_host_specific) {
                    // Break ties with number of virt only pools
                    if (num_virt_only < group_virt_only) {
                        best = group;
                        num_virt_only = group_virt_only;
                        stacked = group.stackable;
                    }
                    if (num_virt_only == group_virt_only) {
                        // Break ties by prefering non-stacked entitlements
                        if (stacked && !group.stackable) {
                            best = group;
                            stacked = group.stackable;
                        }
                    }
                }
            }
        }
        return best;
    },

    get_best_entitlement_groups: function(all_groups, installed, compliance) {
        var best = [];

        var partial_stacks = [];
        for (var stack_id in compliance["partialStacks"]) {
            if (compliance["partialStacks"].hasOwnProperty(stack_id)) {
                for(var i = 0; i < all_groups.length; i++) {
                    var current_group = all_groups[i];
                    if (current_group.stack_id == stack_id) {
                        var in_common = this.get_common_products(installed, current_group);
                        current_group.installed = in_common; // don't have to
                                                                // worry about
                                                                // products that
                                                                // other stacks
                                                                // are handling
                        best.push(current_group);

                        for (var j = installed.length - 1; j >= 0; j--) {
                            var current= installed[j];
                            if (in_common.indexOf(current) != -1) {
                               installed.splice(j, 1);
                            }
                        }
                    }
                }
            }
        }

        var group = this.find_best_ent_group(all_groups, installed);
        while (group != null) {
            best.push(group);
            var in_common = this.get_common_products(installed, group);
            for (var j = installed.length - 1; j >= 0; j--) {
                var current = installed[j];
                if (in_common.indexOf(current) != -1) {
                   installed.splice(j, 1);
                }
            }
            group.installed = in_common;
            group = this.find_best_ent_group(all_groups, installed);
        }
        return best;
    },

    get_attached_ents: function(compliance) {
        var attached_ents = [];
        var createPoolsFor = ["partialStacks",
                              "partiallyCompliantProducts",
                              "compliantProducts"];

        // Create the pools for all entitlement maps in compliance.
        // The number of entitlements should be relatively small.
        for (var i = 0; i < createPoolsFor.length; i++) {
            var nextMapAttrName = createPoolsFor[i];
            var nextMap = compliance[nextMapAttrName];
            for (var key in nextMap) {
                var ents = nextMap[key];
                for (var entIdx = 0; entIdx < ents.length; entIdx++) {
                    var contains = false;
                    //Must make sure there are no duplicates
                    for (var j = 0; j < attached_ents.length; j++) {
                        if (ents[entIdx].id == attached_ents[j].id) {
                            contains = true;
                        }
                    }
                    if (!contains) {
                        attached_ents.push(ents[entIdx]);
                    }
                }
            }
        }
        return attached_ents;
    },

    select_pools: function() {
        var context = this.create_autobind_context();
        log.debug("considerDerived = " + context.considerDerived);

        var attached_ents = this.get_attached_ents(context.compliance);

        var valid_pools = this.get_valid_pools(context);

        var installed = context.products;
        log.debug("Installed products: " + installed);
        //filter compliant products from this list
        for (var prod in context.compliance["compliantProducts"]) {
            if (installed.indexOf(prod) != -1) {
                installed.splice(installed.indexOf(prod), 1);
            }
        }
        var ent_groups = this.build_entitlement_groups(valid_pools, installed, context.consumer, attached_ents, context.considerDerived);
        log.debug("Total ent groups: "+ent_groups.length);

        var valid_groups = [];
        for (var i = ent_groups.length - 1; i >= 0; i--) {
            var ent_group = ent_groups[i];
            if (ent_group.validate()) {
                valid_groups.push(ent_group);
            } else {
                log.debug("Group "+ent_group.stack_id+" failed validation.");
            }
        }
        log.debug("valid ent groups size: " + valid_groups.length);

        log.debug("finding best ent groups");
        var best_groups = this.get_best_entitlement_groups(valid_groups, installed, context.compliance,
                                                           context.considerDerived);
        log.debug("best_groups size: "+best_groups.length);

        for (var i = 0; i < best_groups.length; i++) {
            var group = best_groups[i];
            group.remove_extra_attrs();
            group.prune_pools();
        }

        selected_pools = Utils.getJsMap();

        for (var i = 0; i < best_groups.length; i++) {
            var group = best_groups[i];
            selected_pools.putAll(group.get_pool_quantity());
        }
        selected_pools.dump("selected_pools");
        var output = JSON.stringify(selected_pools.map);
        return output;
    }
}

function is_stacked(ent) {
    return is_pool_stacked(ent.pool);
}

function is_pool_stacked(pool) {
    for (var j = 0; j < pool.productAttributes.length; j++) {
        var attr = pool.productAttributes[j];

        if (attr.name == "stacking_id") {
            return true;
        }
    }
    return false;
}

/**
 * Returns an array of product IDs the entitlement provides which are relevant
 * (installed) on the given consumer.
 */
function find_relevant_pids(entitlement, consumer) {
    provided_pids = [];
    if (consumer.installedProducts == null) {
        return provided_pids;
    }
    for (var j = 0; j < consumer.installedProducts.length; j++) {
        var installed_prod = consumer.installedProducts[j];

        var installed_pid = installed_prod.productId;
        if (entitlement.pool.provides(installed_pid)) {
            log.debug("pool provides: " + installed_pid);
            provided_pids.push(installed_pid);
        }
    }
    return provided_pids;
}

/*
 * Namespace for determining compliance status of a consumer system on a
 * specific date.
 *
 * Compares entitlements against installed products, accounts for stacking,
 * socket and RAM usage.
 */
var Compliance = {
    get_status_context: function() {
        context = JSON.parse(json_context);
        context.ondate = new Date(context.ondate);

        // Add some methods to the various Pool objects:
        for (var k = 0; k < ((context.entitlements) ? context.entitlements.length : 0); k++) {
            var e = context.entitlements[k];
            e.pool = createPool(e.pool);
        }
        if ("entitlement" in context) {
            context.entitlement.pool = createPool(context.entitlement.pool);
        }
        return context;
    },

    get_status: function() {
        var context = Compliance.get_status_context();
        var compStatus = this.getComplianceStatusOnDate(context.consumer,
            context.entitlements, context.ondate, log);
        var compliantUntil = context.ondate;
        if (compStatus.isCompliant()) {
            if (context.entitlements.length == 0) {
                compliantUntil = null;
            }
            else {
                compliantUntil = this.determineCompliantUntilDate(context.consumer,
                    context.entitlements, context.ondate, log);
            }
        }
        compStatus.compliantUntil = compliantUntil;
        var output = JSON.stringify(compStatus);
        return output;
    },

    is_stack_compliant: function() {
        var context = Compliance.get_status_context();
        var stackCoverage = Compliance.getStackCoverage(context.consumer, context.stack_id,
            context.entitlements);
        return stackCoverage.covered;
    },

    is_ent_compliant: function () {
        var context = Compliance.get_status_context();
        var coverage = Compliance.getEntitlementCoverage(context.consumer, context.entitlement, context.entitlements);
        return coverage.covered;
    },

    filterEntitlementsByDate: function (entitlements, date) {
        var filtered_ents = [];
        for (var k = 0; k < entitlements.length; k++) {
            var ent = entitlements[k];

            var startDate = new Date(ent.startDate);
            var endDate = new Date(ent.endDate);
            if (Utils.date_compare(startDate, date) <= 0 && Utils.date_compare(endDate, date) >= 0) {
                filtered_ents.push(ent);
            }
        }
        return filtered_ents;
    },

    getSortedEndDates: function(entitlements) {
        var dates = [];
        for (var k = 0; k < entitlements.length; k++) {
            var ent = entitlements[k];

            dates.push(new Date(ent.endDate));
        }
        dates.sort(function(d1, d2) { return Utils.date_compare(d1, d2) });
        return dates;
    },

    /**
     * Checks compliance status for a consumer on a given date.
     */
    getComplianceStatusOnDate: function(consumer, entitlements, ondate, log) {
        var compStatus = {

            date: ondate,

            // Maps partially compliant stack IDs to entitlements:
            partialStacks: {},

            // Maps partially compliant product IDs to entitlements:
            partiallyCompliantProducts: {},

            // Maps compliant product IDs to entitlements:
            compliantProducts: {},

            // List of non-compliant product IDs:
            nonCompliantProducts: [],

            /*
             * Keep track of the reasons why we are not compliant.
             */
            reasons: [],

            /*
             * Add entitlement to partial stack list, or create list if it does not
             * already exist.
             */
            add_partial_stack: function (stack_id, entitlement) {
                this.partialStacks[stack_id] = this.partialStacks[stack_id] || [];
                this.partialStacks[stack_id].push(entitlement);
            },

            /*
             * Add entitlement to partial products list, or create list if it does not
             * already exist.
             */
            add_partial_product: function (product_id, entitlement) {
                this.partiallyCompliantProducts[product_id] = this.partiallyCompliantProducts[product_id] || [];
                this.partiallyCompliantProducts[product_id].push(entitlement);
            },

            /*
             * Add entitlement to compliant products list, or create list if it does not
             * already exist.
             */
            add_compliant_product: function (product_id, entitlement) {
                this.compliantProducts[product_id] = this.compliantProducts[product_id] || [];
                this.compliantProducts[product_id].push(entitlement);
            },

            add_reasons: function(reasonsToAdd) {
                reasonsToAdd = reasonsToAdd || [];
                for (var i = 0; i < reasonsToAdd.length; i++) {
                    this.reasons.push(reasonsToAdd[i]);
                }
            },

            /*
             * Return boolean indicating whether the system is compliant or not.
             */
            isCompliant: function() {
                return this.nonCompliantProducts.length == 0 &&
                    Object.keys(this.partiallyCompliantProducts).length == 0;
            }

        };

        // Track the stack IDs we've already checked to save some time:
        var compliant_stack_ids = [];
        var non_compliant_stack_ids = [];

        log.debug("Checking compliance status for consumer: " + consumer.uuid + " on date: " + ondate);

        var entitlementsOnDate = Compliance.filterEntitlementsByDate(entitlements, ondate);
        for (var k = 0; k < entitlementsOnDate.length; k++) {
            var e = entitlementsOnDate[k];
            log.debug("  checking entitlement: " + e.id);
            relevant_pids = find_relevant_pids(e, consumer);
            log.debug("    relevant products: " + relevant_pids);

            partially_stacked = false;
            var ent_is_stacked = is_stacked(e);
            // If the pool is stacked, check that the stack requirements are met:
            if (ent_is_stacked) {
                var stack_id = e.pool.getProductAttribute("stacking_id");
                log.debug("    pool has stack ID: " + stack_id);

                // Shortcuts for stacks we've already checked:
                if (Utils.inArray(non_compliant_stack_ids, stack_id)) {
                    log.debug("    stack already found to be non-compliant");
                    partially_stacked = true;
                    compStatus.add_partial_stack(stack_id, e);
                }
                else if (Utils.inArray(compliant_stack_ids, stack_id)) {
                    log.debug("    stack already found to be compliant");
                }
                // Otherwise check the stack and add appropriately:
                else {
                    var stackCoverage = Compliance.getStackCoverage(consumer, stack_id, entitlementsOnDate);
                    if(!stackCoverage.covered) {
                        log.debug("    stack is non-compliant");
                        partially_stacked = true;
                        compStatus.add_partial_stack(stack_id, e);
                        non_compliant_stack_ids.push(stack_id);
                        compStatus.add_reasons(stackCoverage.reasons);
                    }
                    else {
                        log.debug("    stack is compliant");
                        compliant_stack_ids.push(stack_id);
                    }
                }
            }

            // If we have no installed products and the entitlement
            // is partially covered, we want the system to be partial.
            if (relevant_pids.length == 0 && !ent_is_stacked) {
                var entCoverage = Compliance.getEntitlementCoverage(consumer, e, entitlementsOnDate);
                if (!entCoverage.covered) {
                    compStatus.add_reasons(entCoverage.reasons);
                }
            }

            for (var m = 0; m < relevant_pids.length; m++) {
                var relevant_pid = relevant_pids[m];
                if (partially_stacked) {
                    log.debug("   partially compliant: " + relevant_pid);
                    compStatus.add_partial_product(relevant_pid, e);
                    continue;
                }

                var entCoverage = Compliance.getEntitlementCoverage(consumer, e, entitlementsOnDate);
                if (!entCoverage.covered && !ent_is_stacked) {
                    log.debug("    partially compliant (non-stacked): " + relevant_pid);
                    compStatus.add_partial_product(relevant_pid, e);
                    compStatus.add_reasons(entCoverage.reasons);
                }
                else {
                    log.debug("    fully compliant: " + relevant_pid);
                    compStatus.add_compliant_product(relevant_pid, e);
                }
            }
        }

        // Run through each partially compliant product, if we also found a
        // regular entitlement which provides that product, then it should not be
        // considered partially compliant as well. We do however still leave the *stack*
        // in partial stacks list, as this should be repaired. (it could offer other
        // products)
        for (var partial_prod in compStatus.partiallyCompliantProducts) {
            if (!(typeof compStatus.compliantProducts[partial_prod] === "undefined")) {
                delete compStatus.partiallyCompliantProducts[partial_prod];
            }
        }

        // Run through the consumer's installed products and see if there are any we
        // didn't find an entitlement for along the way:
        for (var k = 0; k < ((consumer.installedProducts) ? consumer.installedProducts.length : 0); k++) {
            var installed_prod = consumer.installedProducts[k];

            var installed_pid = installed_prod.productId;
            // Not compliant if we didn't find any entitlements for this product:
            if (typeof compStatus.compliantProducts[installed_pid] === "undefined" &&
                    typeof compStatus.partiallyCompliantProducts[installed_pid] === "undefined") {
                compStatus.nonCompliantProducts.push(installed_pid);
                var installedProductReason = StatusReasonGenerator.buildInstalledProductReason(installed_pid);
                compStatus.add_reasons([installedProductReason]);
            }
        }
        return compStatus;
    },

    /**
     * Determine the compliant until date for a consumer based on the specified start date
     * and entitlements.
     */
    determineCompliantUntilDate: function(consumer, entitlements, startDate, log) {
        var installedProducts = [];
        if (consumer.installedProducts === null || consumer.installedProducts.length == 0) {
            return null;
        }

        for (var i = 0; i < consumer.installedProducts.length; i++) {
            var productId =  consumer.installedProducts[i].productId;
            installedProducts.push(productId);
        }

        // TODO: pull out all entitlements that provide or
        // stack with entitlements that provide installed products.
        // For now I don't think that's necessary
        var entitlementsProvidingProducts = [];
        for (var i = 0; i < entitlements.length; i++) {
            var ent = entitlements[i];
            for (var j = 0; j < installedProducts.length; j++) {
                var productId = installedProducts[j];
                if (ent.pool.provides(productId)) {
                    entitlementsProvidingProducts.push(ent);
                    break;
                }
            }
        }

        // Get all end dates from current entitlements sorted ascending.
        var dates = Compliance.getSortedEndDates(entitlementsProvidingProducts);

        var lastDate = startDate;
        for (var k = 0; k < dates.length; k++) {
            var dateToCheck = dates[k];

            // Ignore past dates and duplicates
            if (Utils.date_compare(dateToCheck, lastDate) != 1) {
                continue;
            }
            lastDate = dateToCheck;

            // Need to check if we are still compliant after the end date,
            // so we add one second.
            dateToCheck.setSeconds(dateToCheck.getSeconds() + 1);

            var compStatus = Compliance.getComplianceStatusOnDate(consumer, entitlements,
                                                       dateToCheck, log);
            if (!compStatus.isCompliant()) {
                return dateToCheck;
            }
        }
        return null;
    },

    /**
     * Check the given list of entitlements to see if a stack ID is compliant for
     * a consumer's socket count.
     */
    getStackCoverage: function(consumer, stack_id, ents) {
        log.debug("Checking stack compliance for: " + stack_id);
        var complianceTracker = createComplianceTracker(consumer, stack_id);
        for (var k = 0; k < ents.length; k++) {
            var ent = ents[k];

            if (is_stacked(ent)) {
                var currentStackId = ent.pool.getProductAttribute("stacking_id");
                if (currentStackId == stack_id) {
                    complianceTracker.updateAccumulatedFromEnt(ent);
                }
            }
        }
        return CoverageCalculator.getStackCoverage(complianceTracker, consumer, ents);
    },

    getEntitlementCoverage: function(consumer, entitlement, ents) {
        log.debug("Checking compliance for entitlement: " + entitlement.id);
        var complianceTracker = createComplianceTracker(consumer, null);
        complianceTracker.updateAccumulatedFromEnt(entitlement);
        return CoverageCalculator.getStackCoverage(complianceTracker, consumer, ents);
    }
}

var Quantity = {
    get_quantity_context: function() {
        context = JSON.parse(json_context);
        context.pool = createPool(context.pool);
        if ("validEntitlements" in context) {
            for (var i = 0; i < context.validEntitlements.length; i++) {
                var e = context.validEntitlements[i];
                e.pool = createPool(e.pool);
            }
        }
        return context;
    },

    get_suggested_quantity: function() {
        var context = Quantity.get_quantity_context();
        var pool = context.pool;
        var consumer = context.consumer;
        var validEntitlements = context.validEntitlements;

        var result = {
            suggested: 1,
            increment: 1
        };

        // Distributors increment is always 1, suggested is irrelevant
        if (!Utils.isMultiEnt(pool) || consumer.type.manifest) {
            return JSON.stringify(result);
        }

        if (pool.hasProductAttribute("stacking_id")) {
            var complianceTracker = createComplianceTrackerFromPool(pool, consumer);

            for (var j = 0; j < validEntitlements.length; j++) {
                var ent = validEntitlements[j];
                if (ent.pool.hasProductAttribute("stacking_id") &&
                        ent.pool.getProductAttribute("stacking_id") == pool.getProductAttribute("stacking_id")) {
                    complianceTracker.updateAccumulatedFromEnt(ent);
                }
            }
            result.suggested = CoverageCalculator.getQuantityToCoverStack(complianceTracker, pool, consumer, validEntitlements);
        }
        else {
            result.suggested = 1;
        }

        // Adjust the suggested quantity increment if necessary:
        if (pool.hasProductAttribute("instance_multiplier") && !Utils.isGuest(consumer)) {
            result.increment = parseInt(pool.getProductAttribute("instance_multiplier"));
        }

        return JSON.stringify(result);
    },

    get_suggested_pool_quantity: function(pool, consumer, entitlements) {
        if (Utils.isMultiEnt(pool) && pool.hasProductAttribute("stacking_id")) {
            var complianceTracker = createComplianceTrackerFromPool(pool, consumer);
            return CoverageCalculator.getQuantityToCoverStack(complianceTracker, pool, consumer, entitlements);
        }
        return 1;
    }
}

/**
 * Namespace for determining the human readable type of pool we are dealing with
 * for display in the client.
 */
var PoolType = {

    get_pool_type_context: function() {
        context = JSON.parse(json_context);
        context.pool = createPool(context.pool);
        return context;
    },

    /*
     * Currently, the result list can only have length zero or one,
     * however it is possible we will need more in the future.
     * For example stackable and non-stackable instance based
     * subscriptions.
     */
    get_arg_pool_type: function(pool) {
        var hasStacking = pool.hasProductAttribute("stacking_id");
        var multiEnt = Utils.isMultiEnt(pool);
        var isInstanceBased = pool.hasProductAttribute(INSTANCE_ATTRIBUTE);
        if (isInstanceBased) {
            if (multiEnt && hasStacking) {
                return "instance based";
            }
        }
        else {
            if (hasStacking && multiEnt) {
                return "stackable";
            }
            else if (!hasStacking && multiEnt) {
                return "multi entitlement";
            }
            else if (hasStacking && !multiEnt) {
                return "unique stackable";
            }
            else if (!hasStacking && !multiEnt) {
                return "standard";
            }
        }
        return "unknown";
    },

    get_pool_type: function() {
        var context = PoolType.get_pool_type_context();
        var result = {
            rawPoolType: this.get_arg_pool_type(context.pool)
        };
        return JSON.stringify(result);
    }
}

/**
 * Namespace for determining ability to override a specific content set
 *  value
 *
 * The list of disallowed value names are listed in this method
 */
var Override = {
    get_override_context: function() {
        context = JSON.parse(json_context);
        return context;
    },

    get_allow_override: function() {
        var blacklist = ['name','label','baseurl']
        var context = Override.get_override_context();

        var check = context.name ? context.name.toLowerCase() : "";
        return Utils.inArray(blacklist, check);
    }
}

var Utils = {

    date_compare: function(d1, d2) {
        if (d1 - d2 > 0) {
            return 1;
        }

        if (d1 - d2 < 0) {
            return -1;
        }

        return 0;
    },

    /**
     * Determine if a guest is considered active for purposes
     * of compliance (guest_limit).  Right now we only check
     * qemu/kvm hypervisor, and only when active is "1".
     * Active can also be 0 (inactive) or -1 (error)
     */
    isGuestActive: function(guest) {
        if ("attributes" in guest &&
                "virtWhoType" in guest.attributes &&
                guest.attributes.virtWhoType == "libvirt" &&
                "active" in guest.attributes) {
                    return guest.attributes.active == "1";
                }
        return false;
    },

    /**
     * Determines if two strings are equal, ignoring case.
     *
     * NOTE: null does NOT equal ""
     */
    equalsIgnoreCase: function(str1, str2) {
        if (str1) {
            str1 = str1.toLowerCase();
        }
        if (str2) {
            str2 = str2.toLowerCase();
        }

        return str1 == str2;
    },

    /**
    *  This is used to collect some of the operations needed on the maps
    *  so we do not iterate through the maps in the code above:
    *  putAll, values, isEmpty, and dump.
    */
    getJsMap: function() {
        var js_map = {
            map: {},

            put: function (key, value) {
                this.map[key] = value;
            },

            putAll: function (add_js_map) {
                var add_map = add_js_map.map
                for(key in add_map)
                {
                    this.map[key] = add_map[key];
                }
             },

             values: function () {
                values = [];
                for(key in this.map)
                {
                    values.push(this.map[key]);
                }
                return values;
             },

             isEmpty: function () {
                 for(key in this.map) {
                     return false;
                 }
                 return true;
             },

             dump: function (name) {
                if (!log.debug) { return; }
                log.debug("Map name: " + name);
                for(key in this.map)
                {
                    log.debug("    Key: " + key + ", value: " + this.map[key]);
                }
             }
         };
         return js_map;
     },

    /**
     * Checks if the given value is in the specified array
     */
    inArray: function(array, value) {
        for (var idx = 0; idx < array.length; idx++) {
            if (array[idx] == value) {
                return true;
            }
        }
        return false;
    },

    isGuest: function(consumer) {
        if (consumer === null || consumer.facts === null || !consumer.facts[IS_VIRT_GUEST_FACT]) {
            return false;
        }

        log.debug(consumer.facts[IS_VIRT_GUEST_FACT]);
        log.debug("is guest? " + Utils.equalsIgnoreCase('true', consumer.facts[IS_VIRT_GUEST_FACT]));
        return Utils.equalsIgnoreCase('true', consumer.facts[IS_VIRT_GUEST_FACT]);
    },

    isCapable: function(consumer, capability) {
        var isCapable = false;
        if (consumer.capabilities) {
            for (var i = 0; i < consumer.capabilities.length; i++) {
                if (consumer.capabilities[i].name == capability) {
                    isCapable = true;
                    break;
                }
            }
        }
        return isCapable;
    },

    isMultiEnt: function(pool) {
        return Utils.equalsIgnoreCase(pool.getProductAttribute(MULTI_ENTITLEMENT_ATTRIBUTE), "yes");
    }
}
