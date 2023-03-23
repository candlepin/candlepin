package org.candlepin.resteasy.filter;

import java.util.Locale;

import javax.enterprise.context.RequestScoped;

@RequestScoped
public class DefaultLocaleContext implements LocaleContext {
    private Locale locale;

    @Override
    public Locale currentLocale() {
        if (this.locale == null) {
            return Locale.getDefault();
        }
        return this.locale;
    }

    public void setCurrentUser(Locale locale) {
        this.locale = locale;
    }

}
