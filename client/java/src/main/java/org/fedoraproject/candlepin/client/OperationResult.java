package org.fedoraproject.candlepin.client;

public enum OperationResult {
	INVALID_UUID("User id is not valid."),
	ERROR_WHILE_SAVING_CERTIFICATES("Error occured when saving cert/keys to disk."),
	NOT_A_FAILURE("Success!"),
	CLIENT_NOT_REGISTERED("Client is not registered"),
	UNKNOWN("Failure reason not known.");
	
	
	private String reason;
	OperationResult(String reason){
		this.reason = reason;
	}
	
	public String getReason() {
		return reason;
	}
	
}
