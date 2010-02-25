package org.fedoraproject.candlepin;

import static org.mockito.Mockito.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.inject.AbstractModule;

public class CandlepinNonServletEnvironmentTestingModule extends AbstractModule {

    @Override
    public void configure() {        
        bind(HttpServletRequest.class).toInstance(mock(HttpServletRequest.class));
        bind(HttpServletResponse.class).toInstance(mock(HttpServletResponse.class));
    }
}