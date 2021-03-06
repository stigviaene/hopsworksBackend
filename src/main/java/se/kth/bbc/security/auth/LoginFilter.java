package se.kth.bbc.security.auth;

/**
 *
 * This class redirect the logged in user from the login pages.
 * <p>
 * @author Ali Gholami <gholami@pdc.kth.se>
 */
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginFilter implements Filter {

  @Override
  public void doFilter(ServletRequest req, ServletResponse res,
          FilterChain chain) throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) req;
    HttpServletResponse response = (HttpServletResponse) res;

    // If user is logged in redirect to LIMS first page 
    // otherwise continue 
    if (request.getRemoteUser() != null) {
      String contextPath = ((HttpServletRequest) request).getContextPath();
      response.sendRedirect(contextPath + "/bbc/lims/index.xhtml");
    } else {
      chain.doFilter(req, res);
    }
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
  }

  @Override
  public void destroy() {
  }
}
