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

function pre_virtualization_host_platform() {
	virtualization_common();
}