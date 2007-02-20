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

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * Annotates the members of your implementation class (constructors, methods
 * and fields) into which the container should inject appropriate references or
 * values.  The Container fulfills injection requests for:
 *
 * <ul>
 * <li>every instance it constructs, whether this construction is in response to
 * {@link Container#getInstance}, {@link Locator#get} on an injected Locator,
 * or to fulfill another {@code @Inject} request (recursive).  The class being
 * constructed must have exactly one of its constructors marked with
 * {@code @Inject}, or must have a constructor taking no parameters.  The
 * container then proceeds to perform method and field injections.
 *
 * <li>pre-constructed instances passed to {@link Container#injectMembers}.
 * In this case any injectable constructor is, of course, ignored.
 *
 * <li>static fields and methods of classes which any Module has specifically
 * requested static injection for, using {@link Binder#requestStaticInjection}.
 * </ul>
 *
 * In all cases, a member can be injected regardless of its Java access
 * specifier (private, default, protected, public).
 *
 * @author crazybob@google.com (Bob Lee)
 */
@Target({ METHOD, CONSTRUCTOR, FIELD })
@Retention(RUNTIME)
public @interface Inject {

  /**
   * If true, and the appropriate binding is not present in the container,
   * the container will skip injection of this method or field rather than
   * produce an error.  When applied to a field, any default value already
   * assigned to the field will remain (guice will not actively null out the
   * field).  When applied to a method, the method will only be invoked if
   * bindings for <i>all</i> parameters are found.  When applied to a
   * constructor, an error will result upon container creation.
   */
  boolean optional() default false;
}
