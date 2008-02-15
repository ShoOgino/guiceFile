/**
 * Copyright (C) 2008 Google Inc.
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

package com.google.inject.visitable;

import com.google.inject.Binder;

/**
 * Immutable snapshot of a request to add a string message.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class AddMessageErrorCommand implements Command {

  private final String message;
  private final Object[] arguments;

  AddMessageErrorCommand(String message, Object[] arguments) {
    this.message = message;
    this.arguments = arguments.clone();
  }

  public void execute(Binder binder) {
    binder.addError(message, arguments);
  }

  public <T> T acceptVisitor(BinderVisitor<T> visitor) {
    return visitor.visitAddMessageError(this);
  }

  public String getMessage() {
    return message;
  }

  public Object[] getArguments() {
    return arguments.clone();
  }
}
