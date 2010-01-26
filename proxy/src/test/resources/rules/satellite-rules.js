// Checks common for both virt host and virt platform entitlements:
function virtualization_common() {
	// Can only be given to a physical system:
	if (consumer.getType() != "system") {
		result.addError("rulefailed.virt.ents.only.for.physical.systems");
		return;
	}
	
	// Host must not have any guests currently (could be changed but for simplicities sake):
	if (parseInt(consumer.getFact("total_guests")) > 0) {
		result.addError("rulefailed.host.already.has.guests");
	}
}

function pre_virtualization_host() {
	virtualization_common();
}

function post_virtualization_host() {
	postHelper.createConsumerPool("virt_guest", product.getAttribute("allowed_guests"));
}
function pre_virtualization_host_platform() {
	virtualization_common();
}

function post_virtualization_host_platform() {
	// unlimited guests:
	postHelper.createConsumerPool("virt_guest", product.getAttribute("allowed_guests"));
}

function pre_global() {
	
	// Support free entitlements for guests, if their parent has virt host or platform,
	// and is entitled to the product the guest is requesting:
	if (consumer.getType() == "virt_system" && consumer.getParent() != null) {
		if ((consumer.getParent().hasEntitlement("virtualization_host") || 
				consumer.getParent().hasEntitlement("virtualization_host_platform")) && 
				consumer.getParent().hasEntitlement(product.getLabel())) {
			result.setFreeEntitlement(true);
		}
	}
}

