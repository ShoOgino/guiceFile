/**
 * Copyright (C) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.servlet;

import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import static com.google.inject.servlet.ServletScopes.REQUEST;
import static com.google.inject.servlet.ServletScopes.SESSION;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Configures the servlet scopes and creates bindings for the servlet API
 * objects so you can inject the request, response, session, etc.
 *
 * <p>
 * You should subclass this module to register servlets and
 * filters in the {@link #configureServlets()} method.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class ServletModule extends AbstractModule {

  @Override
  protected final void configure() {
    // Install the filter and servlet bindings.
    configureServlets();
    install(filtersModuleBuilder);
    install(servletsModuleBuilder);

    // Scopes.
    bindScope(RequestScoped.class, REQUEST);
    bindScope(SessionScoped.class, SESSION);

    // Bind request.
    Provider<HttpServletRequest> requestProvider =
        new Provider<HttpServletRequest>() {
          public HttpServletRequest get() {
            return GuiceFilter.getRequest();
          }

          public String toString() {
            return "RequestProvider";
          }
        };
    bind(HttpServletRequest.class).toProvider(requestProvider);
    bind(ServletRequest.class).toProvider(requestProvider);

    // Bind response.
    Provider<HttpServletResponse> responseProvider =
        new Provider<HttpServletResponse>() {
          public HttpServletResponse get() {
            return GuiceFilter.getResponse();
          }

          public String toString() {
            return "ResponseProvider";
          }
        };
    bind(HttpServletResponse.class).toProvider(responseProvider);
    bind(ServletResponse.class).toProvider(responseProvider);

    // Bind session.
    bind(HttpSession.class).toProvider(new Provider<HttpSession>() {
      public HttpSession get() {
        return GuiceFilter.getRequest().getSession();
      }

      public String toString() {
        return "SessionProvider";
      }
    });

    // Bind servlet context.
    bind(ServletContext.class).toProvider(new Provider<ServletContext>() {
      public ServletContext get() {
        return GuiceFilter.getServletContext();
      }

      public String toString() {
        return "ServletContextProvider";
      }
    });

    // Bind request parameters.
    bind(new TypeLiteral<Map<String, String[]>>() {})
        .annotatedWith(RequestParameters.class)
        .toProvider(new Provider<Map<String, String[]>>() {
              @SuppressWarnings({"unchecked"})
              public Map<String, String[]> get() {
                return GuiceFilter.getRequest().getParameterMap();
              }

              public String toString() {
                return "RequestParametersProvider";
              }
            });

    // inject the pipeline into GuiceFilter so it can route requests correctly
    // Unfortunate staticness... =(
    requestStaticInjection(GuiceFilter.class);
  }

  /**
   * <h3>Servlet Mapping EDSL</h3>
   *
   * <p> Part of the EDSL builder language for configuring servlets
   * and filters with guice-servlet. Think of this as an in-code replacement for web.xml.
   * Filters and servlets are configured here using simple java method calls. Here is a typical
   * example of registering a filter when creating your Guice injector:
   *
   * <pre>
   *   Guice.createInjector(..., new ServletModule() {
   *
   *     {@literal @}Override
   *     protected void configureServlets() {
   *       <b>serve("*.html").with(MyServlet.class)</b>
   *     }
   *   }
   * </pre>
   *
   * This registers a servlet (subclass of {@code HttpServlet}) called {@code MyServlet} to service
   * any web pages ending in {@code .html}. You can also use a path-style syntax to register
   * servlets:
   *
   * <pre>
   *       <b>serve("/my/*").with(MyServlet.class)</b>
   * </pre>
   *
   * Every servlet is required to be a singleton and will implicitly be bound as one if it isn't
   * already. Mapping a servlet that is bound under any other scope is an error.
   *
   * <p>
   * <h4>Dispatch Order</h4>
   * You are free to register as many servlets and filters as you like this way. They will
   * be compared and dispatched in the order in which the filter methods are called:
   *
   * <pre>
   *
   *   Guice.createInjector(..., new ServletModule() {
   *
   *     {@literal @}Override
   *     protected void configureServlets() {
   *       filter("/*").through(MyFilter.class);
   *       filter("*.css").through(MyCssFilter.class);
   *       // etc..
   *
   *       serve("*.html").with(MyServlet.class);
   *       serve("/my/*").with(MyServlet.class);
   *       // etc..
   *      }
   *    }
   * </pre>
   * This will traverse down the list of rules in lexical order. For example, a url
   *  "{@code /my/file.js}" (after it runs through the matching filters) will first
   *  be compared against the servlet mapping:
   * 
   * <pre>
   *       serve("*.html").with(MyServlet.class);
   * </pre>
   * And failing that, it will descend to the next servlet mapping:
   *
   * <pre>
   *       serve("/my/*").with(MyServlet.class);
   * </pre>
   *
   * Since this rule matches, Guice Servlet will dispatch to {@code MyServlet}. These
   * two mapping rules can also be written in more compact form using varargs syntax:
   *
   * <pre>
   *       serve(<b>"*.html", "/my/*"</b>).with(MyServlet.class);
   * </pre>
   * 
   * This way you can map several URI patterns to the same servlet. A similar syntax is
   * also available for filter mappings.
   *
   * <p>
   * <h4>Regular Expressions</h4>
   * You can also map servlets (or filters) to URIs using regular expressions:
   * <pre>
   *    <b>serveRegex("(.)*ajax(.)*").with(MyAjaxServlet.class)</b>
   * </pre>
   *
   * This will map any URI containing the text "ajax" in it to {@code MyAjaxServlet}. Such as:
   * <ul>
   * <li>http://www.google.com/ajax.html</li>
   * <li>http://www.google.com/content/ajax/index</li>
   * <li>http://www.google.com/it/is_totally_ajaxian</li>
   * </ul>
   *
   *
   * <h3>Initialization Parameters</h3>
   *
   * Servlets (and filters) allow you to pass in init params
   * using the {@code <init-param>} tag in web.xml. You can similarly pass in parameters to
   * Servlets and filters registered in Guice-servlet using a {@link java.util.Map} of parameter
   * name/value pairs. For example, to initialize {@code MyServlet} with two parameters
   * ({@code name="Dhanji", site="google.com"}) you could write:
   *
   * <pre>
   *  Map&lt;String, String&gt; params = new HashMap&lt;String, String&gt;();
   *  params.put("name", "Dhanji");
   *  params.put("site", "google.com");
   *
   *  ...
   *      serve("/*").with(MyServlet.class, <b>params</b>)
   * </pre>
   *
   * <p>
   * <h3>Binding Keys</h3>
   *
   * You can also bind keys rather than classes. This lets you hide
   * implementations with package-local visbility and expose them using
   * only a Guice module and an annotation:
   *
   * <pre>
   *  ...
   *      filter("/*").through(<b>Key.get(Filter.class, Fave.class)</b>);
   * </pre>
   *
   * Where {@code Filter.class} refers to the Servlet API interface and {@code Fave.class} is a
   * custom binding annotation. Elsewhere (in one of your own modules) you can bind this
   * filter's implementation:
   *
   * <pre>
   *   bind(Filter.class)<b>.annotatedWith(Fave.class)</b>.to(MyFilterImpl.class);
   * </pre>
   *
   * See {@link com.google.inject.Binder} for more information on binding syntax.
   */
  protected void configureServlets() {
  }


  private final FiltersModuleBuilder filtersModuleBuilder = new FiltersModuleBuilder();
  private final ServletsModuleBuilder servletsModuleBuilder = new ServletsModuleBuilder();

  /**
   * @param urlPattern Any Servlet-style pattern. examples: /*, /html/*, *.html, etc.
   */
  protected final FilterKeyBindingBuilder filter(String urlPattern, String... morePatterns) {
    return filtersModuleBuilder.filter(Lists.asList(urlPattern, morePatterns));
  }

  /**
   * @param regex Any Java-style regular expression.
   */
  protected final FilterKeyBindingBuilder filterRegex(String regex, String... regexes) {
    return filtersModuleBuilder.filterRegex(Lists.asList(regex, regexes));
  }

  /**
   * @param urlPattern Any Servlet-style pattern. examples: /*, /html/*, *.html, etc.
   */
  protected final ServletKeyBindingBuilder serve(String urlPattern, String... morePatterns) {
    return servletsModuleBuilder.serve(Lists.asList(urlPattern, morePatterns));
  }

  /**
   * @param regex Any Java-style regular expression.
   */
  protected final ServletKeyBindingBuilder serveRegex(String regex, String... regexes) {
    return servletsModuleBuilder.serveRegex(Lists.asList(regex, regexes));
  }

  public static interface FilterKeyBindingBuilder {
    void through(Class<? extends Filter> filterKey);
    void through(Key<? extends Filter> filterKey);
    void through(Class<? extends Filter> dummyFilterClass, Map<String, String> contextParams);
    void through(Key<? extends Filter> dummyFilterClass, Map<String, String> contextParams);
  }

  public static interface ServletKeyBindingBuilder {
    void with(Class<? extends HttpServlet> servletKey);
    void with(Key<? extends HttpServlet> servletKey);
    void with(Class<? extends HttpServlet> servletKey, Map<String, String> contextParams);
    void with(Key<? extends HttpServlet> servletKey, Map<String, String> contextParams);
  }
}
