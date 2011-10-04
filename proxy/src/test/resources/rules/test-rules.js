function entitlement_name_space() {
	return Entitlement;
}

function consumer_delete_name_space() {
	return ConsumerDelete;
}

function pool_name_space() {
    return Pool;
}

function export_name_space() {
	return Export;
}

var Entitlement = {
	// defines mapping of product attributes to functions
	// the format is: <function name>:<order number>:<attr1>:...:<attrn>, comma-separated ex.:
	// func1:1:attr1:attr2:attr3, func2:2:attr3:attr4
	attribute_mappings: function() {
		return "CPULIMITED001:1:CPULIMITED001, virtualization_host:1:virtualization_host," +
				"virtualization_host_platform:1:virtualization_host_platform, " +
				"LONGEST001:2:LONGEST001, QUANTITY001:1:QUANTITY001, BADRULE001:1:BADRULE001";
	},
	
	// Checks common for both virt host and virt platform entitlements:
	virtualization_common: function() {
		pre.checkQuantity(pool);
	
		// Can only be given to a physical system:
		if (consumer.getType() != "system") {
			pre.addError("rulefailed.virt.ents.only.for.physical.systems");
			return;
		}
	
		// Host must not have any guests currently (could be changed but for simplicities sake):
		if (consumer.hasFact("total_guests") && 
				parseInt(consumer.getFact("total_guests")) > 0) {
			pre.addError("rulefailed.host.already.has.guests");
		}
	},
	
	pre_virtualization_host: function() {
		Entitlement.virtualization_common();
	},
	
	post_virtualization_host: function() {
		Entitlement.post_global();
	},
	
	pre_virtualization_host_platform: function() {
		Entitlement.virtualization_common();
	},
	
	post_virtualization_host_platform: function() {
		// unlimited guests:
		Entitlement.post_global();
	},
	
	pre_CPULIMITED001: function() {
		pre.checkQuantity(pool);
		cpus = parseInt(consumer.getFact("cpu_cores"));
		if (cpus > 2) {
			pre.addError("rulefailed.too.many.cpu.cores");
		}
	},
	
	// Select pool for mythical product, based on which has the farthest expiry date:
	select_pool_LONGEST001: function() {
		var furthest = null;
		for each (p in pools) {
			if ((furthest == null) || (p.getEndDate().after(furthest.getEndDate()))) {
				furthest = p;
			}
		}
		
		var toReturn = new java.util.HashMap();
		toReturn.put(furthest, 1);
		return toReturn;
	},
	
	select_pool_QUANTITY001: function() {
		var highestMax = null;
		for each (p in pools) {
			if ((highestMax == null) || (p.getMaxMembers() > highestMax.getMaxMembers())) {
				highestMax = p;
			}
		}
		
		var toReturn = new java.util.HashMap();
		toReturn.put(highestMax, 1);
		return toReturn;
	},
	
	// Bad rule! Just return an empty object instead of picking a pool like it should:
	select_pool_BADRULE001: function() {
		return new java.util.HashMap();
	},
	
	pre_global: function() {
        pre.checkQuantity(pool);
	},
	
	post_global: function() {
	}
}

var ConsumerDelete = {
	global: function() {
	}
}

var Pool = {
    global: function() {
    }
}

var Export = {
	global: function() {
	}
}
