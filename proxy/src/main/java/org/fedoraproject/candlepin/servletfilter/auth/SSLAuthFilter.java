package org.fedoraproject.candlepin.servletfilter.auth;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.log4j.Logger;

import com.google.inject.Singleton;

@Singleton
public class SSLAuthFilter implements Filter {
    private static Logger log = Logger.getLogger(BasicAuthViaDbFilter.class);
    
    private FilterConfig filterConfig = null;
    
    public SSLAuthFilter() {
    }
    
    public void init(FilterConfig filterConfig) throws ServletException {
       this.filterConfig = filterConfig;
    }
    
    public void destroy() {
       this.filterConfig = null;
    }
    
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
           throws IOException, ServletException {
        
        log.debug("in basic auth filter");
        chain.doFilter(request, response);
        log.debug("leaving basic auth filter");
    }
}
