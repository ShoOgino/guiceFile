/*
 * Copyright (C) 2014 Google Inc.
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

package com.google.inject.testing.fieldbinder;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation used by {@link BoundFieldModule} to indicate that a field should be bound to its
 * value using Guice.
 *
 * <p>Binding to {@code null} is only allowed for fields that are annotated {@code @Nullable}. See
 * <a href="https://github.com/google/guice/wiki/UseNullable">https://github.com/google/guice/wiki/UseNullable</a>
 *
 * @see BoundFieldModule
 * @author eatnumber1@google.com (Russ Harmon)
 */
@Retention(RUNTIME)
@Target({FIELD})
public @interface Bind {
  /**
   * If specified, {@link BoundFieldModule} will bind the annotated field's value to this type,
   * rather than to the field's actual type.
   */
  Class<?> to() default Bind.class;

  /**
   * If true, {@link BoundFieldModule} will delay retrieving the field's value until injection time
   * rather than eagerly fetching it at configure time.
   *
   * <p>This option is not supported with Provider valued fields.
   */
  boolean lazy() default false;
}
