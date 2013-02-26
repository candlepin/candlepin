// Version: 2.0

/*
 * Default Candlepin rule set.
 *
 * Minor version number bumped on every content change. Major version bumped
 * when the API between the engine and these rules changes.
 */



var SOCKET_FACT="cpu.cpu_socket(s)";
var RAM_FACT = "memory.memtotal";

function entitlement_name_space() {
    return Entitlement;
}

function compliance_name_space() {
    return Compliance;
}

function autobind_name_space() {
    return Autobind;
}



/*
 * Model object related functions.
 */

function createPool(pool) {

    // General function to look for an attribute in the specified
    // attribute collection.
    pool.findAttributeIn = function (attrName, attrs) {
        for (var k = 0; k < attrs.length; k++) {
            var attr = attrs[k];
            if (attrName == attr.name) {
                return attr.value;
            }
        }
        return null;
    }

    pool.getAttribute = function (attrName) {
        return this.findAttributeIn(attrName, this.attributes);
    }

    pool.getProductAttribute = function (attrName) {
        return this.findAttributeIn(attrName, this.productAttributes);
    }

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

function getRelevantProvidedProducts(pool, products) {
    var provided = [];

    for (var i = 0 ; i < products.length ; i++) {
        var product = products[i];
        if (pool.provides(product.getId())) {
            provided.push(product);
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
    for (var j = 0; j < powerSet(a.slice(1), n).length; j++) {
        var x = powerSet(a.slice(1), n)[j];

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
        var products = pool.products;
        for (var i = 0 ; i < products.length ; i++) {
            var product = products[i];
            if (!contains(seen_product_ids, product.id)) {
                seen_product_ids.push(product.id);
            } else if (product.getAttribute("multi-entitlement") != "yes") {
                return false;
            }
        }
    }

    return true;
}

//Check to see if a pool provides any products that are already compliant
function hasNoInstalledOverlap(pool, compliance) {
    var products = pool.products;
    for (var i = 0 ; i < products.length ; i++) {
        var product = products[i];
        log.debug("installed overlap: " + product.id);
        if (product.getAttribute("multi-entitlement") != "yes" &&
            compliance.getCompliantProducts().containsKey(product.id)) {
            return false;
        }
    }

    return true;
}

function architectureMatches(productArchStr, consumerUnameMachine, consumerType,
    prodAttrSeparator) {
    // Non-system consumers without an architecture fact can pass this rule
    // regardless what arch the product requires.
    if (!consumerUnameMachine && "system" != consumerType) {
        return true;
    }

    var supportedArches = [];
    if (productArchStr != null) {
        supportedArches = productArchStr.toUpperCase().split(prodAttrSeparator);

        supportedArches = new java.util.HashSet(java.util.Arrays.asList(supportedArches));

        // If X86 is supported, add all variants to this list:
        if (supportedArches.contains("X86")) {
           supportedArches.add("I386");
           supportedArches.add("I586");
           supportedArches.add("I686");
        }

        if(!supportedArches.contains('ALL') && (!consumerUnameMachine ||
           !supportedArches.contains(consumerUnameMachine.toUpperCase()))) {
           return false;
       }
   }

   return true;
}

function get_attribute_from_pool(pool, attributeName) {
    // this can be either a ReadOnlyPool or a Pool, so deal with attributes as appropriate.
    var attribute = pool.getProductAttribute(attributeName);
    if ("getValue" in attribute) {
        var value = attribute.getValue();
    }
    else {
        var value = attribute;
    }
    return value
}

// get the number of sockets that each entitlement from a pool covers.
// if sockets is set to 0 or is not set, it is considered to be unlimited.
//
// TODO: WARNING: method has been forked for new JS objects approach, switch all callers
// to new new_pool_sockets and delete this once all namespaces are converted.
function get_pool_sockets(pool) {
    if (pool.getProductAttribute("sockets")) {
        var sockets = get_attribute_from_pool(pool, "sockets");
        if (sockets == 0) {
            return Infinity;
        }
        else {
            return parseInt(sockets);
        }
    }
    else {
        return Infinity;
    }
}

function new_get_pool_sockets(pool) {
    for (var j = 0; j < pool.productAttributes.length; j++) {
        var prodAttr = pool.productAttributes[j];

        if (prodAttr.name == "sockets") {
            var sockets = prodAttr.value;
            // TODO: is this 0 right? We would have a string here...
            if (sockets == 0) {
                return Infinity;
            }
            else {
                return parseInt(sockets);
            }
        }
    }
    return Infinity;
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
    var consumer_sockets = 1;
    if (consumer.hasFact(SOCKET_FACT)) {
        consumer_sockets = consumer.getFact(SOCKET_FACT);
     }

    var stackToEntitledSockets = {};
    var stackToPoolMap = {};
    var notStackable = [];

    // data for existing partial stacks
    // we need a map of product id to stack id
    // (to see if there is an existing stack for a product
    // we can build upon, or a conflicting stack)
    var productIdToStackId = {};
    var partialStacks = compliance.getPartialStacks();

    // going to assume one stack per product on the system
    for (var j = 0; j < compliance.getPartialStacks().keySet().toArray().length; j++) {
        var stack_id = compliance.getPartialStacks().keySet().toArray()[j];

        var covered_sockets = 0;
        for (var k = 0; k < partialStacks.get(stack_id).toArray().length; k++) {
            var entitlement = partialStacks.get(stack_id).toArray()[k];

            covered_sockets += entitlement.getQuantity() * get_pool_sockets(entitlement.getPool());
            productIdToStackId[entitlement.getPool().getProductId()] = stack_id;
            for (var m = 0; m < entitlement.getPool().getProvidedProducts().toArray().length; m++) {
                var product = entitlement.getPool().getProvidedProducts().toArray()[m];

                productIdToStackId[product.getProductId()] = stack_id;
            }
        }
        // we can start entitling from the partial stack
        stackToEntitledSockets[stack_id] = covered_sockets;
    }

    for (var j = 0; j < pool_class.length; j++) {
        var pool = pool_class[j];

        var quantity = 0;
        // ignore any pools that clash with installed compliant products
        if (!hasNoInstalledOverlap(pool, compliance)) {
            log.debug("installed overlap found, skipping: " + pool.getId());
            continue;
        }

        if (pool.getProductAttribute("multi-entitlement") && pool.getProductAttribute("stacking_id")) {

            // make sure there isn't a conflicting pool already on the system
            var installed_stack_id;
            var seen_stack_id = false;
            var conflicting_stacks = false;
            for (var m = 0; m < pool.getProducts().length; m++) {
                var product = pool.getProducts()[m];

                if (productIdToStackId.hasOwnProperty(product.id)) {
                    var new_installed_stack_id = productIdToStackId[product.id];
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
                continue;
            }

            var stack_id = pool.getProductAttribute("stacking_id");
            // check if this pool matches the stack id of an existing partial stack
            if (seen_stack_id && installed_stack_id != stack_id) {
                continue;
            }


            if (!stackToPoolMap.hasOwnProperty(stack_id)) {
                stackToPoolMap[stack_id] = new java.util.HashMap();

                // we might already have the partial stack from compliance
                if (!stackToEntitledSockets.hasOwnProperty(stack_id)) {
                    stackToEntitledSockets[stack_id] = 0;
                }
            }

            // if this stack is already done, no need to add more to it.
            if (stackToEntitledSockets[stack_id] >= consumer_sockets) {
                continue;
            }

            var product_sockets = 0;
            var pool_sockets = get_pool_sockets(pool);

            while (stackToEntitledSockets[stack_id] + product_sockets < consumer_sockets) {
                product_sockets += pool_sockets;
                quantity++;
            }

            // don't take more entitlements than are available!
            if (quantity > pool.getMaxMembers() - pool.getCurrentMembers()) {
                quantity = pool.getMaxMembers() - pool.getCurrentMembers();
            }

            stackToEntitledSockets[stack_id] += quantity * pool_sockets;

            stackToPoolMap[stack_id].put(pool, quantity);
        } else {
            // not stackable, just take one.
            notStackable.push(pool);
        }

    }

    var found_pool = false;

    var not_stacked_sockets = 0;
    var not_stacked_pool_map = new java.util.HashMap();
    // We have a not stackable pool.
    if (notStackable.length > 0) {
        for (var k = 0; k < notStackable.length; k++) {
            var pool = notStackable[k];

            var covered_sockets = get_pool_sockets(pool);
            if (covered_sockets > not_stacked_sockets) {
                found_pool = true;
                not_stacked_pool_map = new java.util.HashMap();
                not_stacked_pool_map.put(pool, 1);
                not_stacked_sockets = covered_sockets;
            }
        }
    }

    // if an unstacked pool can cover all our products, take that.
    if (not_stacked_sockets >= consumer_sockets) {
        return not_stacked_pool_map;
    }

    // loop over our potential stacks, and just take the first stack that covers all sockets.
    // else take the stack that covers the most sockets.
    var best_sockets = 0;
    var best_stack;
    for (stack_id in stackToPoolMap) {
        found_pool = true;
        if (stackToEntitledSockets[stack_id] >= consumer_sockets) {
            return stackToPoolMap[stack_id];
        }
        else if (stackToEntitledSockets[stack_id] > best_sockets) {
            best_stack = stack_id;
            best_sockets = stackToEntitledSockets[stack_id];
        }
    }

    // All possible pools may have overlapped with existing products
    // so return nothing!
    if (!found_pool) {
        return new java.util.HashMap();
    }

    // we can't fully cover the product. either select the best non stacker, or the best stacker.
    if (not_stacked_sockets >= best_sockets) {
        return not_stacked_pool_map;
    }
    else {
        return stackToPoolMap[best_stack];
    }
}


// given 2 pools, select the best one. It is a assumed that the pools offer the
// same selection of products.
// returns true if pool1 is a better choice than pool2, else false
function comparePools(pool1, pool2) {

    // Prefer a virt_only pool over a regular pool, else fall through to the next rules.
    // At this point virt_only pools will have already been filtered out by the pre rules
    // for non virt machines.
    if (pool1.getAttribute("virt_only") == "true" && pool2.getAttribute("virt_only") != "true") {
        return true;
    }
    else if (pool2.getAttribute("virt_only") == "true" && pool1.getAttribute("virt_only") != "true") {
        return false;
    }

    // If both virt_only, prefer one with host_requires, otherwise keep looking
    // for a reason to pick one or the other. We know that the host must match
    // as pools are filtered before even being passed to select best pools.
    if (pool1.getAttribute("virt_only") == "true" && pool2.getAttribute("virt_only") == "true") {
        if (pool1.getAttribute("requires_host") != null && pool2.getAttribute("requires_host") == null) {
            return true;
        }
        if (pool2.getAttribute("requires_host") != null && pool1.getAttribute("requires_host") == null) {
            return false;
        }
        // If neither condition is true, no preference...
    }

    // If two pools are still considered equal, select the pool that expires first
    if (pool2.getEndDate().after(pool1.getEndDate())) {
        return true;
    }

}

function isLevelExempt (level, exemptList) {
    for (var j = 0; j < exemptList.toArray().length; j++) {
        var exemptLevel = exemptList.toArray()[j];

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
        context = eval(json_context);

        if ("pool" in context) {
            context.pool = createPool(context.pool);
        }

        context.hasEntitlement = function(poolId) {
            for each (var e in this.consumerEntitlements) {
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
                                 context.consumer.type.label,
                                 context.prodAttrSeparator)) {
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
            if ((parseInt(pool.getProductAttribute("sockets")) > 0) &&
                (parseInt(pool.getProductAttribute("sockets")) < parseInt(consumer.facts[SOCKET_FACT]))) {
                result.addWarning("rulewarning.unsupported.number.of.sockets");
            }
        }
        return JSON.stringify(result);
    },

    pre_ram: function() {
        var result = Entitlement.ValidationResult();
        context = Entitlement.get_attribute_context();
        var consumerRam = get_consumer_ram(context.consumer);
        log.debug("Consumer has " + consumerRam + "GB of RAM.");

        var productRam = parseInt(context.pool.getProductAttribute("ram"));
        log.debug("Product has " + productRam + "GB of RAM");
        if (consumerRam > productRam) {
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

    select_pools: function() {
        // Greedy selection for now, in order
        // XXX need to watch out for multientitle products - how so?

        // An array of arrays of pools. each array is a grouping of pools that provide the
        // same subset of products which are applicable to the requested products.
        // further, each array is sorted, from best to worst. (pool fitness is determined
        // arbitrarily by rules herein.
        var pools_by_class = [];

        // "pools" is a list of all the owner's pools which are compatible for the system:
        if (log.isDebugEnabled()) {
            log.debug("Selecting best pools from: " + pools.length);
            for (var m = 0; m < pools.length; m++) {
                var pool = pools[m];

                log.debug("   " + pool.getId());
            }
        }

        var consumerSLA = consumer.getServiceLevel();
        if (consumerSLA && consumerSLA != "") {
            log.debug("Filtering pools by SLA: " + consumerSLA);
        }

        // Builds out the pools_by_class by iterating each pool, checking which products it provides (that
        // are relevant to this request), then filtering out other pools which provide the *exact* same products
        // by selecting the preferred pool based on other criteria.
        for (var i = 0 ; i < pools.length ; i++) {
            var pool = pools[i];

            // If the SLA of the consumer does not match that of the pool
            // we do not consider the pool unless the level is exempt
            var poolSLA = pool.getProductAttribute('support_level');
            var poolSLAExempt = isLevelExempt(pool.getProductAttribute('support_level'), exemptList);

            if (!poolSLAExempt && consumerSLA &&
                consumerSLA != "" && !Utils.equalsIgnoreCase(consumerSLA, poolSLA)) {
                log.debug("Skipping pool " + pool.getId() +
                        " since SLA does not match that of the consumer.");
                continue;
            }

            log.debug("Checking pool for best unique provides combination: " +
                    pool.getId());
            log.debug("  " + pool.getEndDate());
            log.debug("  top level product: " + (pool.getTopLevelProduct().getId()));

            var unameMachine = consumer.hasFact('uname.machine') ?
                consumer.getFact('uname.machine') : null;
            if (architectureMatches(pool.getTopLevelProduct().getAttribute('arch'),
                                    unameMachine,
                                    consumer.getType(),
                                    prodAttrSeparator)) {
                var provided_products = getRelevantProvidedProducts(pool, products);
                log.debug("  relevant provided products: ");
                for (var n = 0; n < provided_products.length; n++) {
                    var pp = provided_products[n];

                    log.debug("    " + pp.getId());
                }
                // XXX wasteful, should be a hash or something.
                // Tracks if we found another pool previously looked at which had the exact same provided products:
                var duplicate_found = false;

                // Check current pool against previous best to see if it's better:
                for (var n = 0; n < pools_by_class.length; n++) {
                    var pool_class = pools_by_class[n];

                    var best_pool = pool_class[0];
                    var best_provided_products = getRelevantProvidedProducts(best_pool, products);

                    if (providesSameProducts(provided_products, best_provided_products)) {
                        duplicate_found = true;
                        log.debug("  provides same product combo as: " + pool.getId());

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

        var candidate_combos = powerSet(pools_by_class, products.length);

        log.debug("Selecting " + products.length + " products from " + pools_by_class.length +
                  " pools in " + candidate_combos.length + " possible combinations");

        // Select the best pool combo. We prefer:
        // -The combo that provides the most products
        // -The combo that uses the fewest entitlements


        var selected_pools = new java.util.HashMap();
        var best_provided_count = 0;
        var best_entitlements_count = 0;

        for (var k = 0; k < candidate_combos.length; k++) {
            var pool_combo = candidate_combos[k];

            var provided_count = 0;
            var unique_provided = [];
            for (var m = 0; m < pool_combo.length; m++) {
                var pool_class = pool_combo[m];

                var pool = pool_class[0];
                var provided_products = getRelevantProvidedProducts(pool, products);
                for (var n = 0; n < provided_products.length; n++) {
                    var provided_product = provided_products[n];

                    log.debug("\t\tprovided_product " + provided_product.getId());
                    if (!contains(unique_provided, provided_product)) {
                        unique_provided.push(provided_product);
                    }
                }
            }

            for (var m = 0; m < unique_provided.length; m++) {
                var product = unique_provided[m];

                log.debug("unique_provided " + product.getId() + " " + product.getName());
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
                    var new_selection = new java.util.HashMap();
                    var total_entitlements = 0;
                    for (var p = 0; p < pool_combo.length; p++) {
                        var pool_class = pool_combo[p];

                        var poolMap = findStackingPools(pool_class, consumer, compliance);
                        new_selection.putAll(poolMap);

                        var quantity = 0;
                        for (value in poolMap.values()) {
                            quantity += value;
                        }

                        total_entitlements += quantity;
                    }

                    // now verify that after selecting our actual pools from the pool combo,
                    // we still have a better choice here
                    if (new_selection.size() > 0) {
                        selected_pools = new_selection;
                        best_provided_count = unique_provided.length;
                        best_entitlements_count = total_entitlements;
                    }
                }
            }
        }

        // We may not have selected pools for all products; that's ok.
        return selected_pools;
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

// TODO: remove this once entitlement namespace is ported to json in/out
function old_get_consumer_ram(consumer) {
    var consumerRam = 1;
    if (consumer.hasFact(RAM_FACT)) {
        var ramGb = parseInt(consumer.getFact(RAM_FACT)) / 1024 / 1024;
        consumerRam = Math.round(ramGb);
    }
    return consumerRam;
}


function get_consumer_ram(consumer) {
    var consumerRam = 1;
    if (!(typeof consumer.facts[RAM_FACT] === undefined)) {
        var ramGb = parseInt(consumer.facts[RAM_FACT]) / 1024 / 1024;
        consumerRam = Math.round(ramGb);
    }
    return consumerRam;
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
        context = eval(json_context);
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
        var compStatus = Compliance.getComplianceStatusOnDate(context.consumer,
            context.entitlements, context.ondate, log);
        var compliantUntil = context.ondate;
        if (compStatus.isCompliant()) {
            if (context.entitlements.length == 0) {
                compliantUntil = null;
            }
            else {
                compliantUntil = Compliance.determineCompliantUntilDate(context.consumer,
                    context.entitlements, context.ondate, helper, log);
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
        var status = new org.candlepin.policy.js.compliance.ComplianceStatus(ondate);
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
        // TODO: don't use java sets
        var compliant_stack_ids = new java.util.HashSet();
        var non_compliant_stack_ids = new java.util.HashSet();

        log.debug("Checking compliance status for consumer: " + consumer.uuid + " on date: " + ondate);
        var entitlementsOnDate = Compliance.filterEntitlementsByDate(entitlements, ondate);
        for (var k = 0; k < entitlementsOnDate.length; k++) {
            var e = entitlementsOnDate[k];

            log.debug("  checking entitlement: " + e.id);
            relevant_pids = find_relevant_pids(e, consumer);
            log.debug("    relevant products: " + relevant_pids);

            partially_stacked = false;
            var ent_is_stacked = is_stacked(e);
            log.info("### ent is stacked: " + ent_is_stacked);
            // If the pool is stacked, check that the stack requirements are met:
            if (ent_is_stacked) {
                var stack_id = e.pool.getProductAttribute("stacking_id");
                log.debug("    pool has stack ID: " + stack_id);

                // Shortcuts for stacks we've already checked:
                if (non_compliant_stack_ids.contains(stack_id) > 0) {
                    log.debug("    stack already found to be non-compliant");
                    partially_stacked = true;
                    compStatus.add_partial_stack(stack_id, e);
                }
                else if (compliant_stack_ids.contains(stack_id) > 0) {
                    log.debug("    stack already found to be compliant");
                }
                // Otherwise check the stack and add appropriately:
                else if(!Compliance.stack_is_compliant(consumer, stack_id, entitlements, log)) {
                    log.debug("    stack is non-compliant");
                    partially_stacked = true;
                    compStatus.add_partial_stack(stack_id, e);
                    non_compliant_stack_ids.add(stack_id);
                }
                else {
                    log.debug("    stack is compliant");
                    compliant_stack_ids.add(stack_id);
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
                else  {
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
    determineCompliantUntilDate: function(consumer, entitlements, startDate, complianceHelper, log) {
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
        var consumer_sockets = 1;
        if (consumer.facts[SOCKET_FACT]) {
            consumer_sockets = parseInt(consumer.facts[SOCKET_FACT]);
        }
        log.debug("Consumer sockets: " + consumer_sockets);

        var covered_sockets = 0;
        for (var k = 0; k < ents.length; k++) {
            var ent = ents[k];

            if (is_stacked(ent)) {
                var currentStackId = ent.pool.getProductAttribute("stacking_id");
                if (currentStackId == stack_id) {
                    covered_sockets += new_get_pool_sockets(ent.pool) * ent.quantity;
                    log.debug("Ent " + ent.id + " took covered sockets to: " + covered_sockets);
                }
            }
        }

        return covered_sockets >= consumer_sockets;
    },

    /*
     * Check an entitlement to see if it provides sufficent CPU sockets a consumer.
     */
    ent_is_compliant: function(consumer, ent, log) {
        log.debug("Checking entitlement compliance: " + ent.id);
        var consumerSockets = 1;
        if (!(typeof consumer.facts[SOCKET_FACT] === undefined)) {
            consumerSockets = parseInt(consumer.facts[SOCKET_FACT]);
        }
        log.debug("  Consumer sockets found: " + consumerSockets);

        var coveredSockets = new_get_pool_sockets(ent.pool);
        log.debug("  Sockets covered by pool: " + coveredSockets);

        if (coveredSockets < consumerSockets) {
            log.debug("  Entitlement does not cover system sockets.");
            return false;
        }

        // Verify RAM coverage if required.
        // Default consumer RAM to 1 GB if not defined
        var consumerRam = get_consumer_ram(consumer);
        log.debug("  Consumer RAM found: " + consumerRam);

        var poolRam = ent.pool.getProductAttribute("ram");
        log.debug("poolRam: " + poolRam);
        if (poolRam == null) {
            log.debug("  No RAM attribute on pool. Skipping RAM check.");
        }
        else {
            if (!poolRam == "") {
                var ram = parseInt(poolRam);
                log.debug("  Pool RAM found: " + ram)
                if (consumerRam > ram) {
                    return false;
                }
            }
            else {
                log.debug("  Pool's RAM attribute was empty. Skipping RAM check.");
            }
        }

        return true
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
    }
}
