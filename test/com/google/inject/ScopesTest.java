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

package com.google.inject;

import junit.framework.TestCase;
import com.google.inject.servlet.ServletScopes;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ScopesTest extends TestCase {

  public void testContainerScopedAnnotation()
      throws CreationException {
    BinderImpl builder = new BinderImpl();
    BindingBuilderImpl<Singleton> bindingBuilder
        = builder.bind(Singleton.class);
    builder.createContainer();
    assertSame(Scopes.CONTAINER, bindingBuilder.scope);
  }

  @ContainerScoped
  static class Singleton {}

  public void testOverriddingAnnotation()
      throws CreationException {
    BinderImpl builder = new BinderImpl();
    BindingBuilderImpl<Singleton> bindingBuilder
        = builder.bind(Singleton.class);
    bindingBuilder.in(ServletScopes.REQUEST);
    builder.createContainer();
    assertSame(ServletScopes.REQUEST, bindingBuilder.scope);
  }
}
