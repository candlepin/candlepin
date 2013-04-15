// Version: 2.2

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

function compliance_name_space() {
    return Compliance;
}

function autobind_name_space() {
    return Autobind;
}


// Consumer fact names
var SOCKET_FACT="cpu.cpu_socket(s)";
var RAM_FACT = "memory.memtotal";
var CORES_FACT = "cpu.core(s)_per_socket";
var ARCH_FACT = "uname.machine";
var PROD_ARCHITECTURE_SEPARATOR = ",";

// Product attribute names
var SOCKETS_ATTRIBUTE = "sockets";
var CORES_ATTRIBUTE = "cores";
var ARCH_ATTRIBUTE = "arch";
var RAM_ATTRIBUTE = "ram";

/**
 *  These product attributes are considered when
 *  determining coverage of a consumer. Adding an
 *  attribute here, tells the CoverageCalculator
 *  to enforce the attribute.
 *
 *  NOTE: If you add an attribute to here, you MUST
 *        map it to a corresponding fact in
 *        ATTRIBUTES_TO_CONSUMER_FACTS.
 */
var ATTRIBUTES_AFFECTING_COVERAGE = [
    ARCH_ATTRIBUTE,
    SOCKETS_ATTRIBUTE,
    CORES_ATTRIBUTE,
    RAM_ATTRIBUTE
];

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

/**
 *  These product attributes are considered when determining
 *  coverage of a consumer by a stack. Add an attribute here
 *  to enable stacking on the product attribute.
 *
 *  NOTE: If adding an attribute, be sure to also add it to
 *        ATTRIBUTES_AFFECTING_COVERAGE so that the
 *        CoverageCalculator knows to enforce it.
 */
var STACKABLE_ATTRIBUTES = [
    SOCKETS_ATTRIBUTE,
    CORES_ATTRIBUTE,
    RAM_ATTRIBUTE,
    ARCH_ATTRIBUTE
];


/*
 * Model object related functions.
 */

function createPool(pool) {

    pool.product_list = [];

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
        return this.findAttributeIn(attrName, this.attributes);
    };

    pool.getProductAttribute = function (attrName) {
        return this.findAttributeIn(attrName, this.productAttributes);
    };

    pool.hasProductAttribute = function (attrName) {
        return this.getProductAttribute(attrName) != null;
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

    pool.products = function () {
        if (this.product_list == 0) {
            this.product_list.push(this.productId);
            for (var k = 0; k < this.providedProducts.length; k++) {
                this.product_list.push(this.providedProducts[k].productId);
            }
        }
        return this.product_list;
    };
    return pool;
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

// Compute the set of all sets of combinations of elements in a.
function powerSet(a, n) {
    if (a.length == 0) {
        return [];
    }

    var res = [];
    var tempSet = powerSet(a.slice(1), n);
    for (var j = 0; j < tempSet.length; j++) {
        var x = tempSet[j];

        if (x.length <= n) {
            res.push(x);
        }
        if (x.length + 1 <= n) {
            var z = x.slice(0);
            z.push(a[0]);
            res.push(z);
        }
    }
    res.push([a[0]]);
    return res;
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
            } else if (pool.getProductAttribute("multi-entitlement") != "yes") {
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
        if (pool.getProductAttribute("multi-entitlement") != "yes" &&
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
    if (productArchStr != null) {
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


function get_pool_sockets(pool) {
    return getPoolQuantity(pool, SOCKETS_ATTRIBUTE);
}

function getPoolCores(pool) {
    return getPoolQuantity(pool, CORES_ATTRIBUTE);
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
                    consumer.facts[ATTRIBUTES_TO_CONSUMER_FACTS[prodAttr]] : 1
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
}

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
            arch: function (prodAttr, productValues, consumer) {
                var context = Entitlement.get_attribute_context();

                var supportedArchs = prodAttr in productValues ? productValues[prodAttr] : "";
                var consumerArch = ARCH_FACT in consumer.facts ?
                    consumer.facts[ARCH_FACT] : null;

                var covered = architectureMatches(supportedArchs, consumerArch, consumer.type.label);
                log.debug("  System architecture covered: " + covered);
                return covered;
            },

            /**
             *  The default condition checks is a simple *integer* comparison that makes
             *  sure that the specified product attribute value is >= that of the
             *  consumer's calculated value.
             *
             *  NOTE: If comparing non integer attributes, a special condition should
             *        be added to handle that case.
             */
            default: function(prodAttr, productValues, consumer) {
                var consumerQuantity = FactValueCalculator.getFact(prodAttr, consumer);

                // We assume that the value coming back is an int right now.
                var covered = parseInt(productValues[prodAttr]) >= consumerQuantity;
                log.debug("  System's " + prodAttr + " covered: " + covered);
                return covered;
            }
        };
    },

    /**
     *  Determines the percentage of the consumer covered by the specified pool.
     *  The percentage is expressed in a value ranging from 0 to 1, where 1 is
     *  100%.
     */
    getPoolCoveragePercentage: function(pool, consumer) {
        var poolValues = this.getValues(pool, "hasProductAttribute", "getProductAttribute");
        return this.getCoveragePercentage(consumer, poolValues, this.getDefaultConditions());
    },

    /**
     *  Determines the percentage of the consumer covered by the specified stack tracker.
     *  The percentage is expressed in a value ranging from 0 to 1, where 1 is
     *  100%.
     */
    getStackCoveragePercentage: function(stackTracker, consumer) {
        log.debug("Coverage calculator is checking stack coverage...");
        var stackValues = this.getValues(stackTracker, "enforces", "getAccumulatedValue");
        var conditions = this.getDefaultConditions();

        /**
         *  NOTE: Extend default conditions here for stacks, if required.
         */
        conditions.arch = function (prodAttr, productValues, consumer) {
            var supportedArchs = prodAttr in productValues ? productValues[prodAttr] : [];
            var consumerArch = ARCH_FACT in consumer.facts ?
                consumer.facts[ARCH_FACT] : null;

            for (var archStringIdx in supportedArchs) {
                var archString = supportedArchs[archStringIdx];
                if (!architectureMatches(archString, consumerArch, consumer.type.label)) {
                    log.debug("  System architecture not covered by: " + archString);
                    return false;
                }
            }
            log.debug("  System architecture is covered.");
            return true;
        };

        var coveragePercent = this.getCoveragePercentage(consumer, stackValues, conditions);
        log.debug("Stack coverage: " + coveragePercent);
        return coveragePercent;
    },

    /**
     *  Returns the product attribute values that the source object will cover.
     *  The values are returned as an object which maps each attribute to its value.
     *
     *  This function is very dynamic in nature:
     *      source:
     *            Can be any JS object but must define both functions below.
     *      sourceContainsAttributeValueFunctionName:
     *            Name of the source's function that checks if the specified attribute exists.
     *      getValueFromSourceFunctionName:
     *            Name of the source's function that fetchs the value of the specified attribute.
     */
    getValues: function (source, sourceContainsAttributeValueFunctionName, getValueFromSourceFunctionName) {
        var values = {};
        for (var attrIdx in ATTRIBUTES_AFFECTING_COVERAGE) {
            var nextAttr = ATTRIBUTES_AFFECTING_COVERAGE[attrIdx];
            if (source[sourceContainsAttributeValueFunctionName](nextAttr)) {
                values[nextAttr] = source[getValueFromSourceFunctionName](nextAttr);
            }
        }
        return values;
    },

    /**
     *  Determines the percentage of the consumer covered by the specified source
     *  values. The supplied conditions are checked to determine coverage. Only
     *  attribute values defined in ATTRIBUTES_AFFECTING_COVERAGE are checked.
     *
     *  If an attribute value is not found in the sourceValues, it is considered
     *  to be covered.
     */
    getCoveragePercentage: function (consumer, sourceValues, conditions) {
        var coverageCount = 0;
        for (var attrIdx in ATTRIBUTES_AFFECTING_COVERAGE) {
            var attr = ATTRIBUTES_AFFECTING_COVERAGE[attrIdx];

            // if the value doesn't exist we do not enforce it.
            if ( !(attr in sourceValues) ) {
                coverageCount++;
                continue;
            }

            // Make sure it covers the consumer's values
            var condition = attr in conditions ? conditions[attr] : conditions["default"];
            var attributeCovered = condition(attr, sourceValues, consumer);

            if (attributeCovered) {
                coverageCount++;
            }
        }

        return coverageCount / ATTRIBUTES_AFFECTING_COVERAGE.length;
    },

    /**
     *  Determines the quantity of entitlements needed from a pool in order
     *  for the stack to cover a consumer.
     */
    getQuantityToCoverStack : function(stackTracker, pool, consumer) {
        // Check the number required for every attribute
        // and take the max.

        // Some stacked attributes do not affect the quantity needed to
        // make the stack valid. Stacking multiple instances of 'arch'
        // does nothing (there is no quantity).
        var stackableAttrsNotAffectingQuantity = [ARCH_ATTRIBUTE];

        log.debug("Determining number of entitlements to cover consumer...");

        var maxQuantity = 0;
        for (var attrIdx in STACKABLE_ATTRIBUTES) {
            var attr = STACKABLE_ATTRIBUTES[attrIdx];

            // if the attribute does not affect the quantity,
            // we can skip it.
            if (attr in stackableAttrsNotAffectingQuantity) {
                log.debug("  Skipping " + attr + " because it does not affect the quantity.");
                continue;
            }

            // No need to check this attr if it
            // is not being enforced by the stack.
            if (!stackTracker.enforces(attr)) {
                log.debug("  Skipping " + attr + " because it is not being enforced.");
                continue;
            }

            log.debug("  Checking quantity required for " + attr);
            var currentCovered = stackTracker.getAccumulatedValue(attr);
            log.debug("    Quantity currently covered by stack: " + currentCovered);
            var poolQuantity = pool.getProductAttribute(attr);
            log.debug("    Quantity provided by pool: " + poolQuantity);

            var consumerQuantity = FactValueCalculator.getFact(attr, consumer);
            log.debug("    Quantity to be covered on consumer: " + consumerQuantity);

            // Figure out the max required from the pool to cover
            // the consumers fact.
            var amountRequiredFromPool = 0;
            while (currentCovered + (amountRequiredFromPool * poolQuantity) < consumerQuantity) {
                amountRequiredFromPool++;
            }

            if (maxQuantity < amountRequiredFromPool) {
                maxQuantity = amountRequiredFromPool;
            }
        }
        log.debug("Quantity required to cover consumer: " + maxQuantity);
        return maxQuantity;
    }
}

// assumptions: number of pools consumed from is not considered, so we might not be taking from the smallest amount.
// we only stack within the same pool_class. if you have stacks that provide different sets of products,
// you won't be able to stack from them
//
// iterate over a pool class, and determine the quantity of entitlements needed
// to satisfy any stacking on the pools in the class, for the given consumer
//
// If we find a pool that has no stacking requirements, just use that one
// (as we'll only need a quantity of one)
// otherwise, group the pools by stack id, then select the pools we wish to use
// based on which grouping will come closest to fully stacking.
//
//
function findStackingPools(pool_class, consumer, compliance) {

    var stackTrackers = {};
    var stackToPoolMap = {};
    var notStackable = [];

    // data for existing partial stacks
    // we need a map of product id to stack id
    // (to see if there is an existing stack for a product
    // we can build upon, or a conflicting stack)
    var productIdToStackId = {};
    var partialStacks = compliance.partialStacks;
    var stack_ids = Object.getOwnPropertyNames(partialStacks);

    // going to assume one stack per product on the system
    for (var j = 0; j < stack_ids.length; j++) {
        var stack_id = stack_ids[j];
        log.debug("stack_id: " + stack_id);

        // Track our attribute counts.
        var stackTracker = createStackTracker();
        var entitlements = partialStacks[stack_id];
        for (var k = 0; k < entitlements.length; k++) {
            var entitlement = entitlements[k];
            stackTracker.updateAccumulatedFromEnt(entitlement);
            productIdToStackId[entitlement.pool.productId] = stack_id;

            // Ensure that we map our provided products to the stack.
            for (var m = 0; m < entitlement.pool.providedProducts.length; m++) {
                productIdToStackId[entitlement.pool.providedProducts[m].productId] = stack_id;
            }
        }
        // we can start entitling from the partial stack
        stackTrackers[stack_id] = stackTracker;
    }

    for (var j = 0; j < pool_class.length; j++) {
        var pool = pool_class[j];

        // ignore any pools that clash with installed compliant products
        if (!hasNoInstalledOverlap(pool, compliance)) {
            log.debug("installed overlap found, skipping: " + pool.id);
            continue;
        }

        if (pool.getProductAttribute("multi-entitlement") && pool.getProductAttribute("stacking_id")) {
            // make sure there isn't a conflicting pool already on the system
            var installed_stack_id;
            var seen_stack_id = false;
            var conflicting_stacks = false;
            var products = pool.products();
            for (var m = 0; m < products.length; m++) {
                var productId = products[m];

                if (productIdToStackId.hasOwnProperty(productId)) {
                    var new_installed_stack_id = productIdToStackId[productId];
                    if (new_installed_stack_id != installed_stack_id) {
                        // the first id will be different
                        if (!seen_stack_id) {
                            installed_stack_id = new_installed_stack_id;
                            seen_stack_id = true;
                        } else {
                            conflicting_stacks = true;
                        }
                    }
                }
            }

            // this pool provides 2 or more products that already have entitlements on the system,
            // with multiple stack ids
            if (conflicting_stacks) {
                log.debug("Conflicting stacks encountered, skipping pool: " + pool.id);
                continue;
            }

            var stack_id = pool.getProductAttribute("stacking_id");
            // check if this pool matches the stack id of an existing partial stack
            if (seen_stack_id && installed_stack_id != stack_id) {
                log.debug("Pool matches the stack id of an existing partial stack, skipping pool: " + pool.id);
                continue;
            }

            if (!stackToPoolMap.hasOwnProperty(stack_id)) {
                stackToPoolMap[stack_id] = Utils.getJsMap();

                // we might already have the partial stack from compliance
                if (!stackTrackers.hasOwnProperty(stack_id)) {
                    // Create a new stack based on the pool. It will
                    // not have any entitlements associated with it,
                    // but will track any attributes that must be enforced
                    // when compliance is checked.
                    var poolStackTracker = createStackTrackerFromPool(pool);
                    stackTrackers[stack_id] = poolStackTracker;
                }
            }

            var stackTrackerToProcess = stackTrackers[stack_id];

            // if this stack is already done, no need to add more to it.
            if (CoverageCalculator.getStackCoveragePercentage(stackTrackerToProcess, consumer) == 1) {
                log.debug("Stack " + stack_id + " already covers consumer. No need to increment quantity.");
                continue;
            }

            // don't take more entitlements than are available!
            var quantity = CoverageCalculator.getQuantityToCoverStack(stackTrackerToProcess,
                pool, consumer);
            if (quantity > pool.quantity - pool.consumed) {
                quantity = pool.quantity - pool.consumed;
            }

            // Update the stack accumulated values to simulate attaching X
            // entitlements.
            stackTrackerToProcess.updateAccumulatedFromPool(pool, quantity);

            stackToPoolMap[stack_id].put(pool.id, quantity);
        } else {
            // not stackable, just take one.
            notStackable.push(pool);
        }

    }

    var found_pool = false;
    var bestNotStackedEntitlingPool;
    var bestNonStackedCoveragePercentage = 0;

    var not_stacked_pool_map = Utils.getJsMap();
    // We have a not stackable pool.
    if (notStackable.length > 0) {
        for (var k = 0; k < notStackable.length; k++) {
            var pool = notStackable[k];
            var percentagePoolCovers = CoverageCalculator.getPoolCoveragePercentage(pool, consumer);
            if (bestNonStackedCoveragePercentage < percentagePoolCovers) {
                found_pool = true;
                not_stacked_pool_map = Utils.getJsMap();
                not_stacked_pool_map.put(pool.id, 1);

                // Update the best found.
                bestNonStackedCoveragePercentage = percentagePoolCovers;
                bestNotStackedEntitlingPool = pool;
            }
        }
    }

    not_stacked_pool_map.dump("not_stacked_pool_map");

    // if an unstacked pool can cover all our products, take that.
    if (bestNotStackedEntitlingPool != null && bestNonStackedCoveragePercentage == 1) {
        return not_stacked_pool_map;
    }

    // loop over our potential stacks, and just take the first stack that covers all
    // stackable attributes. Else take the stack that covers the most.
    var bestStackCoverage = 0;
    var best_stack;
    for (stack_id in stackToPoolMap) {
        found_pool = true;

        var stackTracker = stackTrackers[stack_id];
        var coverage = CoverageCalculator.getStackCoveragePercentage(stackTracker, consumer);

        // If the stack fully covers all attributes, we have our pool.
        if (coverage == 1) {
            return stackToPoolMap[stack_id];
        }

        // Check if the stack contains the best coverage thus far.
        if (coverage > bestStackCoverage) {
            best_stack = stack_id;
            bestStackCoverage = coverage;
        }
    }

    // All possible pools may have overlapped with existing products
    // so return nothing!
    if (!found_pool) {
        return Utils.getJsMap();
    }

    // we can't fully cover the product. either select the best non stacker,
    // or the best stacker.
    if (bestNonStackedCoveragePercentage >= bestStackCoverage) {
        return not_stacked_pool_map;
    }
    return stackToPoolMap[best_stack];
}

/**
 *   A stack tracker is an Object that helps to track the state of
 *   stacked entitlements. A stack changes what it provides based
 *   on what entitlements make up the stack. For example, if we have
 *   2 stacked entitlements providing 4 sockets, and we add another
 *   stackable entitlement providing 4GB of RAM, then the stack
 *   will provide 4 sockets and 4GB of ram. A stack tracker tracks
 *   these accumulated values as entitlements are added.
 */
function createStackTracker() {
    return {
        // The IDs of entitlements that have been added to this tracker.
        entitlementIds: [],

        /**
         *  The accumulated values from adding entitlements to this tracker.
         *  This is a mapping of product attribute to the accumulated value.
         */
        accumulatedValues: {},

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
            return attribute in this.accumulatedValues;
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

                // Assumes dealing with integers.
                default: function (currentStackValue, poolValue, quantity) {
                    var stackValue = currentStackValue | 0;
                    stackValue = stackValue + (parseInt(poolValue) * quantity);
                    return stackValue;
                },

                /**
                 *  Architecture is accumulated by adding each pool value to
                 *  a list of arch strings. Each pool value is a , seperated
                 *  string of supported archs.
                 */
                arch: function (currentStackValue, poolValue, quantity) {
                    var stackValue = currentStackValue || [];
                    stackValue.push(poolValue);
                    return stackValue;
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
            log.debug("Updating stack tracker's values from pool...");
            for (var attrIdx in STACKABLE_ATTRIBUTES) {
                var nextAttr = STACKABLE_ATTRIBUTES[attrIdx];
                var poolValue = pool.getProductAttribute(nextAttr);
                if (poolValue != null) {
                    var stackValue = this.enforces(nextAttr) ? this.getAccumulatedValue(nextAttr) : null;
                    var accumulate = this.getAccumulationStrategy(nextAttr);
                    this.setAccumulatedValue(nextAttr, accumulate(stackValue, poolValue, quantityToTake));
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
            if (ent.id in this.entitlementIds) {
                // This entitlement was already added.
                return;
            }

            // Keep track of any entitlements that we've already
            // added to the stack so that our accumulated values do not
            // get out of whack.
            this.entitlementIds.push(ent.id);
            this.updateAccumulatedFromPool(ent.pool, ent.quantity);
        }

    };
}

/**
 *  Creates a stack tracker from the specified pool and sets the
 *  accumulated values of the pool's product attributes and sets
 *  them to empty. This is useful for Autobind in a situation where
 *  it comes across a stackable pool, but there are no entitlements
 *  consumed from it. It can create a tracker based on that pool,
 *  and the Coverage Calculator can then use it to determine how
 *  many entitlements from this pool have to be stacked in order
 *  to cover the consumer.
 */
function createStackTrackerFromPool(pool) {
    var stackTracker = createStackTracker();
    // There are no entitlements for this stack, but
    // we have to tell the stack what attributes it must
    // enforce. This is determined by attributes that are
    // set on the pool. Because there are no entitlements
    // we set the quantity to 0.
    stackTracker.updateAccumulatedFromPool(pool, 0);
    return stackTracker;
}


// given 2 pools, select the best one. It is a assumed that the pools offer the
// same selection of products.
// returns true if pool1 is a better choice than pool2, else false
function comparePools(pool1, pool2) {

    // Prefer a virt_only pool over a regular pool, else fall through to the next rules.
    // At this point virt_only pools will have already been filtered out by the pre rules
    // for non virt machines.
    if (pool1.getProductAttribute("virt_only") == "true" && pool2.getProductAttribute("virt_only") != "true") {
        return true;
    }
    else if (pool2.getProductAttribute("virt_only") == "true" && pool1.getProductAttribute("virt_only") != "true") {
        return false;
    }

    // If both virt_only, prefer one with host_requires, otherwise keep looking
    // for a reason to pick one or the other. We know that the host must match
    // as pools are filtered before even being passed to select best pools.
    if (pool1.getProductAttribute("virt_only") == "true" && pool2.getProductAttribute("virt_only") == "true") {
        if (pool1.getProductAttribute("requires_host") != null && pool2.getProductAttribute("requires_host") == null) {
            return true;
        }
        if (pool2.getProductAttribute("requires_host") != null && pool1.getProductAttribute("requires_host") == null) {
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
            "user_license:1:user_license," +
            "virt_only:1:virt_only," +
            "virt_limit:1:virt_limit," +
            "requires_host:1:requires_host";
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
        }

        // Get attribute from a pool. Pool attributes are preferred
        // but if not found, the top level product attributes will be
        // checked.
        context.getAttribute = function(pool, attributeName) {
            var attr = pool.getAttribute(attributeName);
            if (!attr) {
                attr = pool.getProductAttribute(attributeName);
            }
            return attr;
        }

        return context;
    },

    pre_virt_only: function() {
        var result = Entitlement.ValidationResult();
        context = Entitlement.get_attribute_context();

        var virt_pool = Utils.equalsIgnoreCase('true', context.getAttribute(context.pool, 'virt_only'));
        var guest = false;
        if (context.consumer.facts['virt.is_guest']) {
            guest = Utils.equalsIgnoreCase('true', context.consumer.facts['virt.is_guest']);
        }

        if (virt_pool && !guest) {
            result.addError("rulefailed.virt.only");
        }
        return JSON.stringify(result);
    },

    pre_requires_host: function() {
        var result = Entitlement.ValidationResult();
        context = Entitlement.get_attribute_context();

        // It shouldn't be possible to get a host restricted pool in hosted, but just in
        // case, make sure it won't be enforced if we do.
        if (!context.standalone) {
            return JSON.stringify(result);
        }

        if (!context.consumer.facts["virt.uuid"]) {
            result.addError("rulefailed.virt.only");
            return JSON.stringify(result);
        }

        if (!context.hostConsumer ||
            context.hostConsumer.uuid != context.getAttribute(context.pool,
                                                                   'requires_host')) {
            result.addError("virt.guest.host.does.not.match.pool.owner");
        }
        return JSON.stringify(result);
    },

    pre_requires_consumer_type: function() {
        var result = Entitlement.ValidationResult();
        context = Entitlement.get_attribute_context();

        var requiresConsumerType = context.getAttribute(context.pool, "requires_consumer_type");
        if (requiresConsumerType != null &&
            requiresConsumerType != context.consumer.type.label &&
            context.consumer.type.label != "uebercert") {
            result.addError("rulefailed.consumer.type.mismatch");
        }
        return JSON.stringify(result);
    },

    pre_virt_limit: function() {
    },

    pre_architecture: function() {
        var result = Entitlement.ValidationResult();
        context = Entitlement.get_attribute_context();
        if (!architectureMatches(context.pool.getProductAttribute('arch'),
                                 context.consumer.facts['uname.machine'],
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

        var consumerCores = FactValueCalculator.getFact(CORES_ATTRIBUTE, consumer);
        if (consumerCores && !pool.getProductAttribute("stacking_id")) {
            var poolCores = parseInt(pool.getProductAttribute(CORES_ATTRIBUTE));
            if (poolCores > 0 && poolCores < consumer.facts[CORES_FACT]) {
                result.addWarning("rulewarning.unsupported.number.of.cores");
            }
        }
        return JSON.stringify(result);
    },

    pre_ram: function() {
        var result = Entitlement.ValidationResult();
        context = Entitlement.get_attribute_context();
        var consumerRam = FactValueCalculator.getFact(RAM_ATTRIBUTE, context.consumer);
        log.debug("Consumer has " + consumerRam + "GB of RAM.");

        var productRam = parseInt(context.pool.getProductAttribute(RAM_ATTRIBUTE));
        log.debug("Product has " + productRam + "GB of RAM");
        if (consumerRam > productRam && !context.pool.getProductAttribute("stacking_id")) {
            result.addWarning("rulewarning.unsupported.ram");
        }
        return JSON.stringify(result);
    },

    pre_global: function() {
        var result = Entitlement.ValidationResult();
        context = Entitlement.get_attribute_context();
        log.debug("INPUT: " + JSON.stringify(context));

        var consumer = context.consumer;
        var pool = context.pool;
        if (!consumer.type.manifest) {
            var isMultiEntitlement = pool.getProductAttribute("multi-entitlement");
            if (context.hasEntitlement(pool.id) && isMultiEntitlement != "yes") {
                result.addError("rulefailed.consumer.already.has.product");
            }

            if (context.quantity > 1 && isMultiEntitlement != "yes") {
                result.addError("rulefailed.pool.does.not.support.multi-entitlement");
            }

            // If the product has no required consumer type, assume it is restricted to "system".
            // "hypervisor"/"uebercert" type are essentially the same as "system".
            if (!pool.getProductAttribute("requires_consumer_type")) {
                if (consumer.type.label != "system" && consumer.type.label != "hypervisor" &&
                        consumer.type.label != "uebercert") {
                    result.addError("rulefailed.consumer.type.mismatch");
                }

            }

            if (pool.restrictedToUsername != null && pool.restrictedToUsername != consumer.username) {
                result.addError("pool.not.available.to.user, pool= '" + pool.restrictedToUsername + "', actual username='" + consumer.username + "'" );
            }
        }

        // Manifest consumers should not be able to find to any derived pools. Because
        // they are exempt from all pre-rules, to keep these derived pools out of the list
        // they can bind to we must use pre_global, which is used for manifest consumers.
        else {
            if (pool.getAttribute("pool_derived")) {
                result.addError("pool.not.available.to.manifest.consumers");
            }
        }
        return JSON.stringify(result);
    },

}

var Autobind = {

    create_autobind_context: function() {
        var context = JSON.parse(json_context);

        for (var i = 0; i < context.pools.length; i++) {
            context.pools[i] = createPool(context.pools[i]);
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

        return context;
    },

    select_pools: function() {
        var context = this.create_autobind_context();

        // Greedy selection for now, in order
        // XXX need to watch out for multientitle products - how so?

        // An array of arrays of pools. each array is a grouping of pools that provide the
        // same subset of products which are applicable to the requested products.
        // further, each array is sorted, from best to worst. (pool fitness is determined
        // arbitrarily by rules herein.
        var pools_by_class = [];

        // "pools" is a list of all the owner's pools which are compatible for the system:
        log.debug("Selecting best pools from: " + context.pools.length);
        if (log.debug) {
            for (var m = 0; m < context.pools.length; m++) {
                var pool = context.pools[m];
                log.debug("   " + context.pools[m].id);
            }
        }

        log.debug("context.serviceLevelOverride: " + context.serviceLevelOverride);
        var consumerSLA = context.serviceLevelOverride;
        if (!consumerSLA || consumerSLA.equals("")) {
            consumerSLA = context.consumer.serviceLevel;
        }

        // Builds out the pools_by_class by iterating each pool, checking which products it provides (that
        // are relevant to this request), then filtering out other pools which provide the *exact* same products
        // by selecting the preferred pool based on other criteria.
        for (var i = 0 ; i < context.pools.length ; i++) {
            var pool = context.pools[i];

            // If the SLA of the consumer does not match that of the pool
            // we do not consider the pool unless the level is exempt
            var poolSLA = pool.getProductAttribute('support_level');
            var poolSLAExempt = isLevelExempt(pool.getProductAttribute('support_level'), context.exemptList);

            if (!poolSLAExempt && consumerSLA &&
                consumerSLA != "" && !Utils.equalsIgnoreCase(consumerSLA, poolSLA)) {
                log.debug("Skipping pool " + pool.id +
                        " since SLA does not match that of the consumer.");
                continue;
            }

            log.debug("Checking pool for best unique provides combination: " +
                    pool.id);
            log.debug("  " + pool.endDate);
            log.debug("  top level product: " + pool.productId);

            var unameMachine = context.consumer.facts['uname.machine'] ?
                context.consumer.facts['uname.machine'] : null;
            if (architectureMatches(pool.getProductAttribute('arch'),
                                    unameMachine,
                                    context.consumer.type)) {
                var provided_products = getRelevantProvidedProducts(pool, context.products);
                log.debug("  relevant provided products: ");
                for (var n = 0; n < provided_products.length; n++) {
                    var pp = provided_products[n];
                    log.debug("    " + pp);
                }
                // XXX wasteful, should be a hash or something.
                // Tracks if we found another pool previously looked at which had the exact same provided products:
                var duplicate_found = false;

                // Check current pool against previous best to see if it's better:
                for (var n = 0; n < pools_by_class.length; n++) {
                    var pool_class = pools_by_class[n];

                    var best_pool = pool_class[0];
                    var best_provided_products = getRelevantProvidedProducts(best_pool, context.products);

                    if (providesSameProducts(provided_products, best_provided_products)) {
                        duplicate_found = true;
                        log.debug("  provides same product combo as: " + pool.id);

                        // figure out where to insert this pool in its sorted class
                        var i = 0;
                        for (; i < pool_class.length; i++) {
                            if (comparePools(pool, best_pool)) {
                                break;
                            }
                        }
                        log.debug("  inserted into slot: " + i);

                        // now insert the pool into the middle of the array
                        pool_class.splice(i, 0, pool);
                        break;
                    }
                }

                // If we did not find a duplicate pool providing the same products,
                if (!duplicate_found) {
                    log.debug("  no duplicate found");
                    var pool_class = [];
                    pool_class.push(pool);
                    pools_by_class.push(pool_class);
                }
            }
        }

        var candidate_combos = powerSet(pools_by_class, context.products.length);

        log.debug("Selecting " + context.products.length + " products from " + pools_by_class.length +
                  " pools in " + candidate_combos.length + " possible combinations");

        // Select the best pool combo. We prefer:
        // -The combo that provides the most products
        // -The combo that uses the fewest entitlements


        var selected_pools = Utils.getJsMap();
        var best_provided_count = 0;
        var best_entitlements_count = 0;

        for (var k = 0; k < candidate_combos.length; k++) {
            var pool_combo = candidate_combos[k];

            var provided_count = 0;
            var unique_provided = [];
            for (var m = 0; m < pool_combo.length; m++) {
                var pool_class = pool_combo[m];

                var pool = pool_class[0];
                var provided_products = getRelevantProvidedProducts(pool, context.products);
                for (var n = 0; n < provided_products.length; n++) {
                    var provided_product = provided_products[n];

                    log.debug("\t\tprovided_product " + provided_product);
                    if (!contains(unique_provided, provided_product)) {
                        unique_provided.push(provided_product);
                    }
                }
            }

            for (var m = 0; m < unique_provided.length; m++) {
                var product = unique_provided[m];
                log.debug("unique_provided " + product);
            }

            // number of provided products is less than our best selection. keep our current selection.
            if (unique_provided.length < best_provided_count) {
                continue;
            }

            // we do it after the unique provided.length check because that value is the best we can do
            // create 'best' stacking combo here
            // use that best combo for the following comparison

            if (unique_provided.length > best_provided_count || pool_combo.length < best_entitlements_count) {
                // XXX we'll have to do something here to ensure no product overlap after selecting the actual pool/pools from the combo
                if (hasNoProductOverlap(pool_combo)) {
                    var new_selection = Utils.getJsMap();
                    var total_entitlements = 0;
                    for (var p = 0; p < pool_combo.length; p++) {
                        var pool_class = pool_combo[p];

                        var poolMap = findStackingPools(pool_class, context.consumer, context.compliance);
                        poolMap.dump("poolMap");
                        new_selection.putAll(poolMap);
                        new_selection.dump("new_selection");

                        var quantity = 0;
                        var values = poolMap.values();
                        for (var v = 0; v < values.length; v++) {
                            quantity += values[v];
                        }

                        total_entitlements += quantity;
                    }

                    // now verify that after selecting our actual pools from the pool combo,
                    // we still have a better choice here
                    if (new_selection.values().length > 0) {
                        selected_pools = new_selection;
                        best_provided_count = unique_provided.length;
                        best_entitlements_count = total_entitlements;
                    }
                }
            }
        }

        // We may not have selected pools for all products; that's ok.
        selected_pools.dump("selected_pools");
        var output = JSON.stringify(selected_pools.map);
        log.debug("OUTPUT: " + output);
        return output;
    }
}

function is_stacked(ent) {
    for (var j = 0; j < ent.pool.productAttributes.length; j++) {
        var attr = ent.pool.productAttributes[j];

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
        log.debug("INPUT: " + json_context);
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
        log.debug("OUTPUT: " + output);
        return output;
    },

    is_stack_compliant: function() {
        var context = Compliance.get_status_context();
        return Compliance.stack_is_compliant(context.consumer, context.stack_id,
            context.entitlements, log);
    },

    is_ent_compliant: function () {
        var context = Compliance.get_status_context();
        return Compliance.ent_is_compliant(context.consumer, context.entitlement, log);
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
        var sorter = function(date1, date2) {
            var e1End = new Date(e1.endDate);
            var e2End = new Date(e2.endDate);
            return Utils.date_compare(e1End, e2End);
        };

        var dates = [];
        for (var k = 0; k < entitlements.length; k++) {
            var ent = entitlements[k];

            dates.push(new Date(ent.endDate));
        }
        dates.sort(function(d1, d2) { Utils.date_compare(d1, d2) });
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
                else if(!Compliance.stack_is_compliant(consumer, stack_id, entitlements, log)) {
                    log.debug("    stack is non-compliant");
                    partially_stacked = true;
                    compStatus.add_partial_stack(stack_id, e);
                    non_compliant_stack_ids.push(stack_id);
                }
                else {
                    log.debug("    stack is compliant");
                    compliant_stack_ids.push(stack_id);
                }
            }

            for (var m = 0; m < relevant_pids.length; m++) {
                var relevant_pid = relevant_pids[m];
                if (partially_stacked) {
                    log.debug("   partially compliant: " + relevant_pid);
                    compStatus.add_partial_product(relevant_pid, e);
                }
                else if (!Compliance.ent_is_compliant(consumer, e, log) && !ent_is_stacked) {
                    log.debug("    partially compliant (non-stacked): " + relevant_pid);
                    compStatus.add_partial_product(relevant_pid, e);
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
            }
        }
        return compStatus;
    },

    /**
     * Determine the compliant until date for a consumer based on the specified start date
     * and entitlements.
     */
    determineCompliantUntilDate: function(consumer, entitlements, startDate, log) {
        var initialEntitlements = Compliance.filterEntitlementsByDate(entitlements, startDate);

        // Get all end dates from current entitlements sorted ascending.
        var dates = Compliance.getSortedEndDates(initialEntitlements);

        for (var k = 0; k < dates.length; k++) {
            var dateToCheck = dates[k];


            // Ignore past dates.
            if (dateToCheck < startDate) {
                continue;
            }

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
    stack_is_compliant: function(consumer, stack_id, ents, log) {
        log.debug("Checking stack compliance for: " + stack_id);
        var stackTracker = createStackTracker();
        for (var k = 0; k < ents.length; k++) {
            var ent = ents[k];

            if (is_stacked(ent)) {
                var currentStackId = ent.pool.getProductAttribute("stacking_id");
                if (currentStackId == stack_id) {
                    stackTracker.updateAccumulatedFromEnt(ent);
                }
            }
        }

        return CoverageCalculator.getStackCoveragePercentage(stackTracker, consumer) == 1;
    },

    /*
     * Check an entitlement to see if it covers the system based on
     * its specifications.
     */
    ent_is_compliant: function(consumer, ent, log) {
        log.debug("Checking entitlement compliance: " + ent.id);
        return CoverageCalculator.getPoolCoveragePercentage(ent.pool, consumer) == 1;
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
    }
}
