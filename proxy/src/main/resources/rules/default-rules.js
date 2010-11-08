/*
 * Default Candlepin rule set.
 */

function entitlement_name_space() {
	return Entitlement;
}

function consumer_delete_name_space() {
	return ConsumerDelete;
}

(function(){
    String.prototype.trim = function(){
        return this.replace(/^\s*/, "").replace(/\s*$/, "")
    }

    String.prototype.join = function(iterable){
        var result = "";
        var str = this
        iterable.forEach(function(element){
                result += element + str
                return true
            })
        return result
    }
})();

/* Utility functions */
function contains(a, obj) {
	for (var i = 0; i < a.length; i++) {
	   var result = a[i] == obj
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
		var product = products[i]
		if (pool.provides(product.getId())) {
			provided.push(product);
		}
	}
	
	return provided;
}

function providesSameProducts(products, pool) {
	for each (product in products) {
		if (!pool.provides(product.getId())) {
			return false;
		}
	}
	
	return true;
}

function arrayToString(a) {
	msg = "[";
	for each (q in a) {
		msg += q.getId() + " ";
	}
	msg += "]";
	return msg;
}

// XXX i don't know what this is really called
function recursiveCombination(a, n) {
	if (n == 0) {
		return [];
	}
	
	var res = [];
	for each (x in recursiveCombination(a.slice(1), n - 1)) {
		res.push(x);
		var z = x.slice(0);
		z.push(a[0]);
		res.push(z);
	}
	res.push([a[0]]);
	return res;
}

// Check if the provided list of pools contains any duplicated products
// We don't need to worry about checking multi-entitle allowed products,
// as you can use as many of those as you want.
function hasNoProductOverlap(combination) {
	var seen_product_ids = [];
	for each (pool in combination) {
		var products = pool.products;
		for (var i = 0 ; i < products.length ; i++) {
			var product = products[i];
			if (!contains(seen_product_ids, product.id)) {
				seen_product_ids.push(product.id);
			} else if (product.getAttribute("multi-entitle") != "yes") {
				return false;
			}
		}
	}
	
	return true;
}

function filterOutCombinationsWithDuplicates(combinations) {
	var filtered = [];
	
	for each (combination in combinations) {
		if (hasNoProductOverlap(combination)) {
			filtered.push(combination);
		}
	}
	
	return filtered;
}

var Entitlement = {
		
	// defines mapping of product attributes to functions
	// the format is: <function name>:<order number>:<attr1>:...:<attrn>, comma-separated ex.:
	// func1:1:attr1:attr2:attr3, func2:2:attr3:attr4
	attribute_mappings: function() {
		return "virtualization_host:1:virtualization_host, " +
				"virtualization_host_platform:1:virtualization_host_platform, " +
				"architecture:1:arch, " +
				"sockets:1:sockets, " +
				"requires_consumer_type:1:requires_consumer_type," +
				"user_license:1:user_license";
	},
		
	post_user_license: function() {
		// Default to using the same product from the pool.
		var productId = pool.getProductId()
	
		// Check if the sub-pool should be for a different product:
		if (attributes.containsKey("user_license_product")) {
			productId = attributes.get("user_license_product");
		}
	
		// Create a sub-pool for this user
		post.createUserRestrictedPool(productId, pool.getProvidedProductIds(),
				attributes.get("user_license"));
	},
	
	pre_requires_consumer_type: function() {
		if (!attributes.get("requires_consumer_type").equals(consumer.getType())) {
			pre.addError("rulefailed.consumer.type.mismatch");
		}
	},
	
	pre_architecture: function() {
	java.lang.System.out.printf("\n\n%s\n\n\n", prodAttrSeparator)
	   var result = product.getAttribute('arch').toUpperCase().split(prodAttrSeparator)
	   var str = " ".join(result)
	   java.lang.System.out.printf("\n\n[%s] : %s\n\n", str, contains(result, 'ALL'))
	   if(!contains(result, 'ALL') && 
	       (!consumer.hasFact("cpu.architecture")  ||
            !contains(result, consumer.getFact('cpu.architecture').toUpperCase())
            )
          ){
	       pre.addWarning("rulewarning.architecture.mismatch");
	   
	   }
	},
	
	post_architecture: function() {
	},
	
	pre_sockets: function() {
		if (!consumer.hasFact("cpu.cpu_socket(s)") ||
		    (parseInt(product.getAttribute("sockets")) < parseInt(consumer.getFact("cpu.cpu_socket(s)")))) {
			pre.addWarning("rulewarning.unsupported.number.of.sockets");
		}
	},
	
	post_sockets: function() {
	},
	
	// Checks common for both virt host and virt platform entitlements:
	virtualization_common: function() {
	
	// XXX: this check is bad, as we don't do virt based on consumer type anymore
		// Can only be given to a physical system:
	//	if (consumer.getType() != "system") {
	//		pre.addError("rulefailed.virt.ents.only.for.physical.systems");
	//	}
	
		// Host must not have any guests currently (could be changed but for simplicities sake):
	//	if (consumer.hasFact("total_guests") && parseInt(consumer.getFact("total_guests")) > 0) {
	//		pre.addError("rulefailed.host.already.has.guests");
	//	}
	},
	
	pre_virtualization_host: function() {
		Entitlement.virtualization_common();
	},
	
	post_virtualization_host: function() {
	},
	
	pre_virtualization_host_platform: function() {
		Entitlement.virtualization_common();
	},
	
	post_virtualization_host_platform: function() {
		// unlimited guests;
	},
	
	pre_global: function() {
		if (consumer.hasEntitlement(product) && product.getAttribute("multi-entitlement") != "yes") {
			pre.addError("rulefailed.consumer.already.has.product");
		}
	
		// If the product has no required consumer type, assume it is restricted to "system":
		if (!product.hasAttribute("requires_consumer_type")) {
			if (!consumer.getType().equals("system")) {
				pre.addError("rulefailed.consumer.type.mismatch");
			}

		}
		
		if (pool.getRestrictedToUsername() != null && !pool.getRestrictedToUsername().equals(consumer.getUsername())) {
			pre.addError("pool.not.available.to.user, pool= '" + pool.getRestrictedToUsername() + "', actual username='" + consumer.getUsername() + "'" );
		}
		
		// Support free entitlements for guests, if their parent has virt host or
		// platform,
		// and is entitled to the product the guest is requesting:
		if (consumer.getType() == "virt_system" && consumer.getParent() != null) {
			var parent = consumer.getParent();
			if ((parent.hasEntitlement("virtualization_host") || parent.hasEntitlement("virtualization_host_platform"))
					&& parent.hasEntitlement(product)) {
				pre.grantFreeEntitlement();
			}
		} else {
			pre.checkQuantity(pool);
		}
	},
	
	post_global: function() {
	},
	
	select_pool_global: function() {
		// Greedy selection for now, in order
		// XXX need to watch out for multientitle products

		// pools that have been filtered by expiration date, etc
		var best_in_class_pools = [];
		for (var i = 0 ; i < pools.length ; i++) {
			var pool = pools[i]
			var provided_products = getRelevantProvidedProducts(pool, products);
			// XXX wasteful, should be a hash or something.
			var replaced = false;
			for each (best_pool in best_in_class_pools) {
				if (providesSameProducts(provided_products, best_pool)) {
					// If two pools are equal, select the pool that expires first
					if (best_pool.getEndDate().after(pool.getEndDate())) {
						best_in_class_pools[best_in_class_pools.indexOf(best_pool)] = pool;
						replaced = true;
						break;
					}
					// Autobind 2 logic goes here
				}
			}
			
			if (!replaced) {
				best_in_class_pools.push(pool);
			}
		}
		
		candidate_combos = recursiveCombination(best_in_class_pools, best_in_class_pools.length);

		candidate_combos = filterOutCombinationsWithDuplicates(candidate_combos);
		
		// Select the best pool combo. We prefer:
		// -The combo that provides the most products
		// -The combo that uses the fewest entitlements
		
		var selected_pools = [];
		var best_provided_count = 0;
		
		for each (pool_combo in candidate_combos) {
			var provided_count = 0;
			var unique_provided = [];
			for each (pool in pool_combo) {
				var provided_products = getRelevantProvidedProducts(pool, products);
				for each (provided_product in provided_products) {
					if (!contains(unique_provided, provided_product)) {
						unique_provided.push(provided_product);
					}
				}
			}
			
			if (unique_provided.length < best_provided_count) {
				continue;
			} else if (unique_provided.length > best_provided_count || pool_combo.length < selected_pools.length) {
				selected_pools = pool_combo;
				best_provided_count = unique_provided.length;
			}
		}
				
		// We may not have selected pools for all products; that's ok.
		if (selected_pools.length > 0) {
			return selected_pools;
		}
	
	    return null;
	}
}

var ConsumerDelete = {
	global: function() {
	    if (consumer.getType() == "person") {
	    	helper.deleteUserRestrictedPools(consumer.getUsername());
	    }
	}
}

