/*
 * Default Candlepin rule set.
 */



var SOCKET_FACT="cpu.cpu_socket(s)";
var RAM_FACT = "memory.memtotal";

function entitlement_name_space() {
    return Entitlement;
}

function consumer_delete_name_space() {
    return ConsumerDelete;
}

function pool_name_space() {
    return Pool;
}

function criteria_name_space() {
    return PoolCriteria;
}

function export_name_space() {
    return Export;
}

function compliance_name_space() {
    return Compliance;
}

function unbind_name_space() {
    return Unbind;
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
    for each (q in a) {
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
    for each (x in powerSet(a.slice(1), n)) {
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
    for each (pool_class in combination) {
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

function architectureMatches(product, consumer) {
    // Non-system consumers without an architecture fact can pass this rule
    // regardless what arch the product requires.
    if (!consumer.hasFact("uname.machine") && !consumer.getType().equals("system")) {
        return true;
    }

    var supportedArches = [];
    var archString = product.getAttribute('arch');
    if (archString != null) {
        supportedArches = archString.toUpperCase().split(prodAttrSeparator);

        supportedArches = new java.util.HashSet(java.util.Arrays.asList(supportedArches));

        // If X86 is supported, add all variants to this list:
        if (supportedArches.contains("X86")) {
           supportedArches.add("I386");
           supportedArches.add("I586");
           supportedArches.add("I686");
        }

        if(!supportedArches.contains('ALL') &&
           (!consumer.hasFact("uname.machine")  ||
            !supportedArches.contains(consumer.getFact('uname.machine').toUpperCase())
            )
          ){
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
    for each (stack_id in compliance.getPartialStacks().keySet().toArray()) {
        var covered_sockets = 0;
        for each (entitlement in partialStacks.get(stack_id).toArray()) {
            covered_sockets += entitlement.getQuantity() * get_pool_sockets(entitlement.getPool());
            productIdToStackId[entitlement.getPool().getProductId()] = stack_id;
            for each (product in entitlement.getPool().getProvidedProducts().toArray()) {
                productIdToStackId[product.getProductId()] = stack_id;
            }
        }
        // we can start entitling from the partial stack
        stackToEntitledSockets[stack_id] = covered_sockets;
    }

    for each (pool in pool_class) {
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
            for each (product in pool.getProducts()) {
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
        for each (pool in notStackable) {
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
    for each (var exemptLevel in exemptList.toArray()) {
        if (exemptLevel.equalsIgnoreCase(level)) {
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

    pre_virt_only: function() {
        var virt_pool = 'true'.equalsIgnoreCase(attributes.get('virt_only'));
        var guest = false;
        if (consumer.hasFact('virt.is_guest')) {
            guest = 'true'.equalsIgnoreCase(consumer.getFact('virt.is_guest'));
        }

        if (virt_pool && !guest) {
            pre.addError("rulefailed.virt.only");
        }
    },

    pre_requires_host: function() {
        // It shouldn't be possible to get a host restricted pool in hosted, but just in
        // case, make sure it won't be enforced if we do.
        if (!standalone) {
            return;
        }
        if (!consumer.hasFact("virt.uuid")) {
            pre.addError("rulefailed.virt.only");
            return;
        }
        var hostConsumer = pre.getHostConsumer(consumer.getFact("virt.uuid"));

        if (hostConsumer == null || !hostConsumer.getUuid().equals(attributes.get('requires_host'))) {
            pre.addError("virt.guest.host.does.not.match.pool.owner");
        }
    },

    post_user_license: function() {
        if (!consumer.isManifest()) {
            // Default to using the same product from the pool.
            var productId = pool.getProductId();

            // Check if the sub-pool should be for a different product:
            if (attributes.containsKey("user_license_product")) {
                productId = attributes.get("user_license_product");
            }

            // Create a sub-pool for this user
            post.createUserRestrictedPool(productId, pool,
                                          attributes.get("user_license"));
        }
    },

    pre_requires_consumer_type: function() {
        if (!attributes.get("requires_consumer_type").equals(consumer.getType()) &&
                !consumer.getType().equals("uebercert")) {
            pre.addError("rulefailed.consumer.type.mismatch");
        }
    },

    pre_virt_limit: function() {
    },

    post_virt_limit: function() {
        if (standalone) {
            var productId = pool.getProductId();
            var virt_limit = attributes.get("virt_limit");
            if ('unlimited'.equals(virt_limit)) {
                post.createHostRestrictedPool(productId, pool, 'unlimited');
            } else {
                var virt_quantity = parseInt(virt_limit) * entitlement.getQuantity();
                if (virt_quantity > 0) {
                    post.createHostRestrictedPool(productId, pool,
                            virt_quantity.toString());
                }
            }
        }
        else {
            // if we are exporting we need to deal with the bonus pools
            if (consumer.isManifest()) {
                var virt_limit = attributes.get("virt_limit");
                if (!'unlimited'.equals(virt_limit)) {
                    // if the bonus pool is not unlimited, then the bonus pool quantity
                    //   needs to be adjusted based on the virt limit
                    var virt_quantity = parseInt(virt_limit) * entitlement.getQuantity();
                    if (virt_quantity > 0) {
                        var pools = post.lookupBySubscriptionId(pool.getSubscriptionId());
                        for (var idex = 0 ; idex < pools.size(); idex++ ) {
                            var derivedPool = pools.get(idex);
                            if (derivedPool.getAttributeValue("pool_derived")) {
                                derivedPool = post.updatePoolQuantity(derivedPool, -1 * virt_quantity);
                            }
                        }
                    }
                }
                else {
                    // if the bonus pool is unlimited, then the quantity needs to go to 0
                    //   when the physical pool is exhausted completely by export.
                    //   A quantity of 0 will block future binds, whereas -1 does not.
                    if (pool.getQuantity() == pool.getExported()) {
                        //getting all pools matching the sub id. Filtering out the 'parent'.
                        var pools = post.lookupBySubscriptionId(pool.getSubscriptionId());
                        for (var idex = 0 ; idex < pools.size(); idex++ ) {
                            var derivedPool = pools.get(idex);
                            if (derivedPool.getAttributeValue("pool_derived")) {
                                derivedPool = post.setPoolQuantity(derivedPool, 0);
                            }
                        }
                    }
                }
            }
        }
    },

    pre_architecture: function() {
       if (!architectureMatches(product, consumer)) {
         pre.addWarning("rulewarning.architecture.mismatch");
       }
    },

    post_architecture: function() {
    },

    pre_sockets: function() {
        //usually, we assume socket count to be 1 if it is undef. However, we need to know if it's
        //undef here in order to know to skip the socket comparison (per acarter/jomara)
        if (consumer.hasFact(SOCKET_FACT) && !product.hasAttribute("stacking_id")) {
            if ((parseInt(product.getAttribute("sockets")) > 0) &&
                (parseInt(product.getAttribute("sockets")) < parseInt(consumer.getFact(SOCKET_FACT)))) {
                pre.addWarning("rulewarning.unsupported.number.of.sockets");
            }
        }
    },

    post_sockets: function() {
    },

    pre_ram: function() {
        var consumerRam = get_consumer_ram(consumer);
        log.debug("Consumer has " + consumerRam + "GB of RAM.");

        var productRam = parseInt(product.getAttribute("ram"));
        log.debug("Product has " + productRam + "GB of RAM");
        if (consumerRam > productRam) {
            pre.addWarning("rulewarning.unsupported.ram");
        }
    },

    post_ram: function() {
    },

    pre_global: function() {
        if (!consumer.isManifest()) {
            if (consumer.hasEntitlement(pool.getId()) && product.getAttribute("multi-entitlement") != "yes") {
                pre.addError("rulefailed.consumer.already.has.product");
            }

            if (pre.getQuantity() > 1 && product.getAttribute("multi-entitlement") != "yes") {
                pre.addError("rulefailed.pool.does.not.support.multi-entitlement");
            }

            // If the product has no required consumer type, assume it is restricted to "system".
            // "hypervisor"/"uebercert" type are essentially the same as "system".
            if (!product.hasAttribute("requires_consumer_type")) {
                if (!consumer.getType().equals("system") && !consumer.getType().equals("hypervisor") &&
                        !consumer.getType().equals("uebercert")) {
                    pre.addError("rulefailed.consumer.type.mismatch");
                }

            }

            if (pool.getRestrictedToUsername() != null && !pool.getRestrictedToUsername().equals(consumer.getUsername())) {
                pre.addError("pool.not.available.to.user, pool= '" + pool.getRestrictedToUsername() + "', actual username='" + consumer.getUsername() + "'" );
            }
        }

        // Manifest consumers should not be able to find to any derived pools. Because
        // they are exempt from all pre-rules, to keep these derived pools out of the list
        // they can bind to we must use pre_global, which is used for manifest consumers.
        else {
            if (pool.getAttributes().containsKey("pool_derived")) {
                pre.addError("pool.not.available.to.manifest.consumers");
            }
        }

        // FIXME
        // for auto sub stacking, we need to be able to pull across multiple
        // pools eventually, so this would need to go away in that case
        pre.checkQuantity(pool);
    },

    post_global: function() {
    },

    select_pool_global: function() {
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
            for each (pool in pools) {
                log.debug("   " + pool.getId());
            }
        }

        var consumerSLA = consumer.getServiceLevel();
        if (consumerSLA && !consumerSLA.equals("")) {
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
                !consumerSLA.equals("") && !consumerSLA.equalsIgnoreCase(poolSLA)) {
                log.debug("Skipping pool " + pool.getId() +
                        " since SLA does not match that of the consumer.");
                continue;
            }

            log.debug("Checking pool for best unique provides combination: " +
                    pool.getId());
            log.debug("  top level product: " + (pool.getTopLevelProduct().getId()));
            if (architectureMatches(pool.getTopLevelProduct(), consumer)) {
                var provided_products = getRelevantProvidedProducts(pool, products);
                log.debug("  relevant provided products: ");
                for each (pp in provided_products) {
                    log.debug("    " + pp.getId());
                }
                // XXX wasteful, should be a hash or something.
                // Tracks if we found another pool previously looked at which had the exact same provided products:
                var duplicate_found = false;

                // Check current pool against previous best to see if it's better:
                for each (pool_class in pools_by_class) {
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

                        // now insert the pool into the middle of the array
                        pool_class.splice(i, 0, pool);
                        break;
                    }
                }

                // If we did not find a duplicate pool providing the same products,
                if (!duplicate_found) {
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

        for each (pool_combo in candidate_combos) {
            var provided_count = 0;
            var unique_provided = [];
            for each (pool_class in pool_combo) {
                var pool = pool_class[0];
                var provided_products = getRelevantProvidedProducts(pool, products);
                for each (provided_product in provided_products) {
                    log.debug("\t\tprovided_product " + provided_product.getId());
                    if (!contains(unique_provided, provided_product)) {
                        unique_provided.push(provided_product);
                    }
                }
            }

            for each (product in unique_provided){
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
                    for each (pool_class in pool_combo) {
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

        for (pool in selected_pools.keySet()){
            log.debug("selected_pool2 " + pool);
        }

        // We may not have selected pools for all products; that's ok.
        return selected_pools;
    }
}

var ConsumerDelete = {
    global: function() {
        if (consumer.getType() == "person") {
            helper.deleteUserRestrictedPools(consumer.getUsername());
        }
    }
}

/*
 * Return Hibernate criteria we can apply to the pool query when listing pools that
 * are relevant for a consumer.
 */
var PoolCriteria = {
    poolCriteria: function() {
        // FIXME: alot of this could be cleaned up with some
        // class/method var's instead of full paths, etc
        var criteriaFilters = new java.util.LinkedList();
        // Don't load virt_only pools if this consumer isn't a guest:
        // or it isn't a manifest consumer
        if (!consumer.isManifest() && !"true".equalsIgnoreCase(consumer.getFact("virt.is_guest"))) {
            // not a guest
            var noVirtOnlyPoolAttr =
                org.hibernate.criterion.DetachedCriteria.forClass(
                        org.candlepin.model.PoolAttribute, "pool_attr")
                    .add(org.hibernate.criterion.Restrictions.eq("name", "virt_only"))
                    .add(org.hibernate.criterion.Restrictions.eq("value", "true"))
                    .add(org.hibernate.criterion.Property.forName("this.id")
                            .eqProperty("pool_attr.pool.id"))
                    .setProjection(org.hibernate.criterion.Projections.property("pool_attr.id"));
            criteriaFilters.add(org.hibernate.criterion.Subqueries.notExists(
                    noVirtOnlyPoolAttr));

            // same criteria but for PoolProduct attributes
            // not sure if this should be two separate criteria, or if it's
            // worth it to combine in some clever fashion
            var noVirtOnlyProductAttr =
                org.hibernate.criterion.DetachedCriteria.forClass(
                        org.candlepin.model.ProductPoolAttribute, "prod_attr")
                    .add(org.hibernate.criterion.Restrictions.eq("name", "virt_only"))
                    .add(org.hibernate.criterion.Restrictions.eq("value", "true"))
                    .add(org.hibernate.criterion.Property.forName("this.id")
                            .eqProperty("prod_attr.pool.id"))
                    .setProjection(org.hibernate.criterion.Projections.property("prod_attr.id"));
            criteriaFilters.add(org.hibernate.criterion.Subqueries.notExists(
                    noVirtOnlyProductAttr));

        } else {
            // we are a virt guest
            // add criteria for filtering out pools that are not for this guest
            if (consumer.hasFact("virt.uuid")) {
                var hostUuid = ""; // need a default value in case there is no registered host
                if (hostConsumer != null) {
                    hostUuid = hostConsumer.getUuid();
                }
                var noRequiresHost = org.hibernate.criterion.DetachedCriteria.forClass(
                        org.candlepin.model.PoolAttribute, "attr")
                        .add(org.hibernate.criterion.Restrictions.eq("name", "requires_host"))
                        //  Note: looking for pools that are not for this guest
                        .add(org.hibernate.criterion.Restrictions.ne("value", hostUuid))
                        .add(org.hibernate.criterion.Property.forName("this.id")
                                .eqProperty("attr.pool.id"))
                                .setProjection(org.hibernate.criterion.Projections.property("attr.id"));
                // we do want everything else
                criteriaFilters.add(org.hibernate.criterion.Subqueries.notExists(
                        noRequiresHost));
            }
            // no virt.uuid, we can't try to filter
        }

        return criteriaFilters;
    }
}


var Pool = {

    /*
     * Creates all appropriate pools for a subscription.
     */
    createPools: function () {
        var pools = new java.util.LinkedList();
        var quantity = sub.getQuantity() * sub.getProduct().getMultiplier();
        var providedProducts = new java.util.HashSet();
        var newPool = new org.candlepin.model.Pool(sub.getOwner(), sub.getProduct().getId(),
                sub.getProduct().getName(), providedProducts,
                    quantity, sub.getStartDate(), sub.getEndDate(), sub.getContractNumber(),
                    sub.getAccountNumber());
        if (sub.getProvidedProducts() != null) {
            for each (var p in sub.getProvidedProducts().toArray()) {
                var providedProduct = new org.candlepin.model.
                    ProvidedProduct(p.getId(), p.getName());
                providedProduct.setPool(newPool);
                providedProducts.add(providedProduct);
            }
        }
        helper.copyProductAttributesOntoPool(sub, newPool);
        newPool.setSubscriptionId(sub.getId());
        newPool.setSubscriptionSubKey("master");
        var virtAtt = sub.getProduct().getAttribute("virt_only");

        // note: the product attributes are getting copied above, but the following will make
        //   virt_only a pool attribute. That makes the pool explicitly virt_only to subscription
        //    manager and any other downstream consumer.
        if (virtAtt != null && virtAtt.getValue() != null &&
            !virtAtt.getValue().equals("")) {
            newPool.addAttribute(new org.candlepin.model.PoolAttribute("virt_only", virtAtt.getValue()));
        }

        pools.add(newPool);

        // Check if we need to create a virt-only pool for this subscription:
        if (attributes.containsKey("virt_limit") && !standalone) {
            var virt_limit = attributes.get("virt_limit");
            var virt_attributes = new java.util.HashMap();
            virt_attributes.put("virt_only", "true");
            virt_attributes.put("pool_derived", "true");
            // Make sure the virt pool does not have a virt_limit,
            // otherwise this will recurse infinitely
            virt_attributes.put("virt_limit", "0");

            if ('unlimited'.equals(virt_limit)) {
                var derivedPool = helper.createPool(sub, sub.getProduct().getId(),
                                                    'unlimited', virt_attributes);
                derivedPool.setSubscriptionSubKey("derived");
                pools.add(derivedPool);
            } else {
                var virt_limit_quantity = parseInt(virt_limit);

                if (virt_limit_quantity > 0) {
                    var virt_quantity = quantity * virt_limit_quantity;

                    var derivedPool = helper.createPool(sub, sub.getProduct().getId(),
                                                        virt_quantity.toString(),
                                                        virt_attributes);
                    derivedPool.setSubscriptionSubKey("derived");
                    pools.add(derivedPool);
                }
            }
        }
        return pools;
    },

    /*
     * Updates the existing pools for a subscription.
     */
    updatePools: function () {
        var poolsUpdated = new java.util.LinkedList();
        for each (var existingPool in pools.toArray()) {
            var datesChanged = (!sub.getStartDate().equals(
                existingPool.getStartDate())) ||
                (!sub.getEndDate().equals(existingPool.getEndDate()));
            // Expected quantity is normally the subscription's quantity, but for
            // virt only pools we expect it to be sub quantity * virt_limit:
            var expectedQuantity = sub.getQuantity() * sub.getProduct().getMultiplier();

            /*
             *  WARNING: when updating pools, we have the added complication of having to
             *  watch out for pools that candlepin creates internally. (i.e. virt bonus
             *  pools in hosted (created when sub is first detected), and host restricted
             *  virt pools when on-site. (created when a host binds)
             */
            if (existingPool.hasAttribute("pool_derived") &&
                existingPool.attributeEquals("virt_only", "true") &&
                existingPool.hasProductAttribute("virt_limit")) {

                if (!attributes.containsKey("virt_limit")) {
                    log.warn("virt_limit attribute has been removed from subscription, flagging pool for deletion if supported: " + existingPool.getId());
                    // virt_limit has been removed! We need to clean up this pool. Set
                    // attribute to notify the server of this:
                    existingPool.setAttribute("candlepin.delete_pool", "true");
                    // Older candlepin's won't look at the delete attribute, so we will
                    // set the expected quantity to 0 to effectively disable the pool
                    // on those servers as well.
                    expectedQuantity = 0;
                }
                else {
                    var virt_limit = attributes.get("virt_limit");

                    if ('unlimited'.equals(virt_limit)) {
                        if (existingPool.getQuantity() == 0) {
                            // this will only happen if the rules set it to be 0.
                            //   don't modify
                            expectedQuantity = 0;
                        }
                        else {
                            // pretty much all the rest.
                            expectedQuantity = -1;
                        }
                    }
                    else {
                        if (standalone) {
                            // this is how we determined the quantity
                            expectedQuantity = existingPool.getSourceEntitlement().getQuantity() * parseInt(virt_limit);
                        }
                        else {
                            // we need to see if a parent pool exists and has been exported. Adjust is number exported
                            //   from a parent pool. If no parent pool, adjust = 0 [a scenario of virtual pool only]
                            // WARNING: we're assuming there is only one base (non-derived) pool. This may change in the
                            // future requiring a more complex adjustment for exported quantities if there are multiple
                            // pools in play.
                            var adjust = 0;
                            for (var idex = 0 ; idex < pools.size(); idex++ ) {
                                var derivedPool = pools.get(idex);
                                if (!derivedPool.getAttributeValue("pool_derived")) {
                                    adjust = derivedPool.getExported();
                                }
                            }
                            expectedQuantity = (expectedQuantity-adjust) * parseInt(virt_limit);
                        }
                    }
                }
            }

            var quantityChanged = !(expectedQuantity == existingPool.getQuantity());
            var productsChanged = helper.checkForChangedProducts(existingPool, sub);

            var productAttributesChanged = helper.copyProductAttributesOntoPool(sub,
                                                                             existingPool);
            if (productAttributesChanged) {
                log.info("Updated product attributes from subscription.");
            }

            if (!(quantityChanged || datesChanged || productsChanged ||
                  productAttributesChanged)) {
                //TODO: Should we check whether pool is overflowing here?
                continue;
            }

            if (quantityChanged) {
                log.info("   Quantity changed to: " + expectedQuantity);
                existingPool.setQuantity(expectedQuantity);
            }

            if (datesChanged) {
                log.info("   Subscription dates changed.");
                existingPool.setStartDate(sub.getStartDate());
                existingPool.setEndDate(sub.getEndDate());
            }

            if (productsChanged) {
                log.info("   Subscription products changed.");
                existingPool.setProductName(sub.getProduct().getName());
                existingPool.setProductId(sub.getProduct().getId());
                existingPool.getProvidedProducts().clear();

                if (sub.getProvidedProducts() != null) {
                    for each (var p in sub.getProvidedProducts().toArray()) {
                        var providedProduct = new org.candlepin.model.
                            ProvidedProduct(p.getId(), p.getName());
                        existingPool.addProvidedProduct(providedProduct);
                    }
                }
            }
            poolsUpdated.add(new org.candlepin.policy.js.pool.PoolUpdate(
                                 existingPool, datesChanged, quantityChanged, productsChanged));
        }
        return poolsUpdated;
    }

}

var Export = {
    can_export_entitlement: function() {
        pool_derived = attributes.containsKey('pool_derived');
        return !consumer.isManifest() || !pool_derived;
    }
}

function is_stacked(ent) {
    return ent.getPool().hasProductAttribute("stacking_id");
}

/**
 * Check the given list of entitlements to see if a stack ID is compliant for
 * a consumer's socket count.
 */
function stack_is_compliant(consumer, stack_id, ents, log) {
    log.debug("Checking stack compliance for: " + stack_id);
    var consumer_sockets = 1;
    if (consumer.hasFact(SOCKET_FACT)) {
        consumer_sockets = parseInt(consumer.getFact(SOCKET_FACT));
    }
    log.debug("Consumer sockets: " + consumer_sockets);

    var covered_sockets = 0;
    for each (var ent in ents.toArray()) {
        if (is_stacked(ent)) {
            var currentStackId = ent.getPool().getProductAttribute("stacking_id").getValue();
            if (currentStackId.equals(stack_id)) {
                covered_sockets += get_pool_sockets(ent.getPool()) * ent.getQuantity();
                log.debug("Ent " + ent.getId() + " took covered sockets to: " + covered_sockets);
            }
        }
    }

    return covered_sockets >= consumer_sockets;
}

/*
 * Check an entitlement to see if it provides sufficient CPU sockets a consumer.
 */
function ent_is_compliant(consumer, ent, log) {
    log.debug("Checking entitlement compliance: " + ent.getId());
    var consumerSockets = 1;
    if (consumer.hasFact(SOCKET_FACT)) {
        consumerSockets = parseInt(consumer.getFact(SOCKET_FACT));
    }
    log.debug("  Consumer sockets found: " + consumerSockets);

    var coveredSockets = get_pool_sockets(ent.getPool());
    log.debug("  Sockets covered by pool: " + coveredSockets);

    if (coveredSockets < consumerSockets) {
        log.debug("  Entitlement does not cover system sockets.");
        return false;
    }

    // Verify RAM coverage if required.
    // Default consumer RAM to 1 GB if not defined
    var consumerRam = get_consumer_ram(consumer);
    log.debug("  Consumer RAM found: " + consumerRam);

    if (ent.getPool().getProductAttribute("ram")) {
        var poolRamAttr = get_attribute_from_pool(ent.getPool(), "ram");
        if (poolRamAttr != null && !poolRamAttr.isEmpty()) {
            var ram = parseInt(poolRamAttr);
            log.debug("  Pool RAM found: " + ram)
            if (consumerRam > ram) {
                return false;
            }
        }
    }
    else {
        log.debug("  No RAM attribute on pool. Skipping RAM check.");
    }

    return true
}

function get_consumer_ram(consumer) {
    var consumerRam = 1;
    if (consumer.hasFact(RAM_FACT)) {
        var ramGb = parseInt(consumer.getFact(RAM_FACT)) / 1024 / 1024;
        consumerRam = java.lang.Math.round(ramGb);
    }
    return consumerRam;
}

/**
 * Returns an array of product IDs the entitlement provides which are relevant
 * (installed) on the given consumer.
 */
function find_relevant_pids(entitlement, consumer) {
    provided_pids = [];
    if (consumer.getInstalledProducts() == null) {
        return provided_pids;
    }
    for each (var installed_prod in consumer.getInstalledProducts().toArray()) {
        var installed_pid = installed_prod.getProductId();
        if (entitlement.getPool().provides(installed_pid) == true) {
            log.debug("pool provides: " + installed_pid);
            provided_pids.push(installed_pid);
        }
    }
    return provided_pids;
}

var Compliance = {
    get_status: function() {
        var status = getComplianceStatusOnDate(consumer, entitlements, ondate, log);
        var compliantUntil = ondate;
        if (status.isCompliant()) {
            if (entitlements.isEmpty()) {
                compliantUntil = null;
            }
            else {
                compliantUntil = determineCompliantUntilDate(consumer, ondate, helper, log);
            }
        }
        status.setCompliantUntil(compliantUntil);
        return status;
    },

    is_stack_compliant: function() {
        return stack_is_compliant(consumer, stack_id, entitlements, log);
    },

    is_ent_compliant: function () {
        return ent_is_compliant(consumer, ent, log);
    }
}

/**
 * Checks compliance status for a consumer on a given date.
 */
function getComplianceStatusOnDate(consumer, entitlements, ondate, log) {
    var status = new org.candlepin.policy.js.compliance.ComplianceStatus(ondate);

    // Track the stack IDs we've already checked to save some time:
    var compliant_stack_ids = new java.util.HashSet();
    var non_compliant_stack_ids = new java.util.HashSet();

    log.debug("Checking compliance status for consumer: " + consumer.getUuid());
    for each (var e in entitlements.toArray()) {
        log.debug("  checking entitlement: " + e.getId());
        relevant_pids = find_relevant_pids(e, consumer);
        log.debug("    relevant products: " + relevant_pids);

        partially_stacked = false;
        var ent_is_stacked = is_stacked(e);
        // If the pool is stacked, check that the stack requirements are met:
        if (ent_is_stacked) {
            var stack_id = e.getPool().getProductAttribute("stacking_id").getValue();
            log.debug("    pool has stack ID: " + stack_id);

            // Shortcuts for stacks we've already checked:
            if (non_compliant_stack_ids.contains(stack_id) > 0) {
                log.debug("    stack already found to be non-compliant");
                partially_stacked = true;
                status.addPartialStack(stack_id, e);
            }
            else if (compliant_stack_ids.contains(stack_id) > 0) {
                log.debug("    stack already found to be compliant");
            }
            // Otherwise check the stack and add appropriately:
            else if(!stack_is_compliant(consumer, stack_id, entitlements, log)) {
                log.debug("    stack is non-compliant");
                partially_stacked = true;
                status.addPartialStack(stack_id, e);
                non_compliant_stack_ids.add(stack_id);
            }
            else {
                log.debug("    stack is compliant");
                compliant_stack_ids.add(stack_id);
            }
        }

        for each (relevant_pid in relevant_pids) {
            if (partially_stacked) {
                log.debug("   partially compliant: " + relevant_pid);
                status.addPartiallyCompliantProduct(relevant_pid, e);
            }
            else if (!ent_is_compliant(consumer, e, log) && !ent_is_stacked) {
                log.debug("    partially compliant (non-stacked): " + relevant_pid);
                status.addPartiallyCompliantProduct(relevant_pid, e);
            }
            else  {
                log.debug("    fully compliant: " + relevant_pid);
                status.addCompliantProduct(relevant_pid, e);
            }
        }
    }

    // Run through each partially compliant product, if we also found a
    // regular entitlement which provides that product, then it should not be
    // considered partially compliant as well. We do however still leavecomplianceRules.getStatus(consumer, next); the *stack*
    // in partial stacks list, as this should be repaired. (it could offer other
    // products)
    for each (var partial_prod in status.getPartiallyCompliantProducts().keySet().toArray()) {
        if (status.getCompliantProducts().keySet().contains(partial_prod)) {
            status.getPartiallyCompliantProducts().remove(partial_prod);
        }
    }

    // Run through the consumer's installed products and see if there are any we
    // didn't find an entitlement for along the way:
    if (consumer.getInstalledProducts() != null) {
        for each (var installed_prod in consumer.getInstalledProducts().toArray()) {
            var installed_pid = installed_prod.getProductId();
            // Not compliant if we didn't find any entitlements for this product:
            if (!status.getCompliantProducts().containsKey(installed_pid) &&
                    !status.getPartiallyCompliantProducts().containsKey(installed_pid)) {
                status.addNonCompliantProduct(installed_pid);
            }
        }
    }
    return status;
}

/**
 * Determine the compliant until date for a consumer based on the specified start date
 * and entitlements.
 */
function determineCompliantUntilDate(consumer, startDate, complianceHelper, log) {
    var initialEntitlements = complianceHelper.getEntitlementsOnDate(consumer, startDate);
    // Get all end dates from current entitlements sorted ascending.
    var dates = complianceHelper.getSortedEndDatesFromEntitlements(initialEntitlements)
        .toArray();

    for each (var dateToCheck in dates) {
        var next = new Date(dateToCheck.getTime());
        var jsStartDate = new Date(startDate.getTime());

        // Ignore past dates.
        if (next < jsStartDate) {
            continue;
        }
        // Need to check if we are still compliant after the end date,
        // so we add one second.
        next.setSeconds(next.getSeconds() + 1);

        var entitlementsOnDate = complianceHelper.getEntitlementsOnDate(consumer,
                next);
        var status = getComplianceStatusOnDate(consumer, entitlementsOnDate, next, log);
        if (!status.isCompliant()) {
        return next;
        }
    }
    return null;
}

var Unbind = {

    // defines mapping of product attributes to functions
    // the format is: <function name>:<order number>:<attr1>:...:<attrn>, comma-separated ex.:
    // func1:1:attr1:attr2:attr3, func2:2:attr3:attr4
    attribute_mappings: function() {
        return  "virt_limit:1:virt_limit";
    },

    pre_virt_limit: function() {
    },

    post_virt_limit: function() {
        if (!standalone && consumer.isManifest()) {
            var virt_limit = attributes.get("virt_limit");
            if (!'unlimited'.equals(virt_limit)) {
                // As we have unbound an entitlement from a physical pool that was previously
                //   exported, we need to add back the reduced bonus pool quantity.
                var virt_quantity = parseInt(virt_limit) * entitlement.getQuantity();
                if (virt_quantity > 0) {
                    var pools = post.lookupBySubscriptionId(pool.getSubscriptionId());
                    for (var idex = 0 ; idex < pools.size(); idex++ ) {
                        var derivedPool = pools.get(idex);
                        if (derivedPool.getAttributeValue("pool_derived")) {
                            post.updatePoolQuantity(derivedPool, virt_quantity);
                        }
                    }
                }
            }
            else {
                // As we have unbound an entitlement from a physical pool that was previously
                //   exported, we need to set the unlimited bonus pool quantity to -1.
                var pools = post.lookupBySubscriptionId(pool.getSubscriptionId());
                for (var idex = 0 ; idex < pools.size(); idex++ ) {
                    var derivedPool = pools.get(idex);
                    if (derivedPool.getAttributeValue("pool_derived")) {
                        if(derivedPool.getQuantity() == 0) {
                            post.setPoolQuantity(derivedPool, -1);
                        }
                    }
                }
            }
        }
    }
}
