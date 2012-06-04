package org.opensolaris.opengrok.web;

import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.*;
import javax.servlet.http.*;

/**
 * Filter to lie to the application about the context.
 * @author Gustavo Lopes
 */
public class ContextOverrideFilter implements Filter {
    
    private final static String FAKE_CONTEXT_CONFIG_NAME = "fakeContext";
    
    private String fakedContext;
    
    private class ContextOverrideWrapper extends HttpServletRequestWrapper {
        
        ContextOverrideWrapper(HttpServletRequest request) {
            super(request);
        }
        
        @Override
        public String getContextPath() {
            return ContextOverrideFilter.this.fakedContext;
        }
    }
    
    @Override
    public void init(FilterConfig config) throws ServletException {
        Enumeration<String> en = config.getInitParameterNames();
        while (en.hasMoreElements()) {
            String name = en.nextElement();
            if (!name.equals(FAKE_CONTEXT_CONFIG_NAME)) {
                throw new ServletException("Invalid filter init param name: "
                    + name);
            }
            this.fakedContext = config.getInitParameter(name);;
        }
        if (this.fakedContext == null) {
            throw new ServletException("The init param '" +
                    FAKE_CONTEXT_CONFIG_NAME + "' must be specified");
        }
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        chain.doFilter(new ContextOverrideWrapper((HttpServletRequest)request),
                response);
    }
	
    @Override
    public void destroy() {}
    
}
