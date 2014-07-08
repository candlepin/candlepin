package org.candlepin.gutterball.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.candlepin.gutterball.config.Configuration;
import org.xnap.commons.i18n.I18n;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Inject;

/**
 * Status
 */
@XmlRootElement(name = "status")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Status {

	// TODO can we inject version directly from the configuration?
	private String version;
	private String requestLocale;

	@Inject
	public Status(I18n i18n, Configuration config) {
		version = config.getString("gutterball.version", i18n.tr("Unknown"));
		requestLocale = i18n.getLocale().toString();
	}

	@JsonProperty("gutterball.version")
	public String getVersion() {
		return version;
	}

	@JsonProperty("request_locale")
	public String getRequestLocale() {
		return requestLocale;
	}
}
