package org.fedoraproject.candlepin.client.model;

import java.util.Date;

public class TimeStampedEntity {
    private Date created,updated;

	public Date getCreated() {
		return created;
	}

	public void setCreated(Date created) {
		this.created = created;
	}

	public Date getUpdated() {
		return updated;
	}

	public void setUpdated(Date updated) {
		this.updated = updated;
	}
}
