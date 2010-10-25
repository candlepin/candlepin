/*
 * Default Candlepin rule set.
 */

function entitlement_name_space() {
	return Entitlement;
}

function consumer_delete_name_space() {
	return ConsumerDelete;
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
		if ((product.getAttribute("arch").toUpperCase() != "ALL") &&
				(!consumer.hasFact("cpu.architecture") ||
				(product.getAttribute("arch") != consumer.getFact("cpu.architecture")))) {
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
		var selected_pools = [];
		var used_products = [];
		for (pool in Iterator(pools)) {
			for (product in Iterator(products)) {
				//var product = products[j];
				if (product.getId() == pool.getProductId()) {
					used_products.push(product);
					selected_pools.push(pool);
					break;
				}
			}
		}
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

