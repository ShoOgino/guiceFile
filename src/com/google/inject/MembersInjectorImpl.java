/**
 * Copyright (C) 2009 Google Inc.
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

import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.ImmutableList;
import com.google.inject.internal.ImmutableSet;
import com.google.inject.internal.InternalContext;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.InjectionListener;

/**
 * Injects members of instances of a given type.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
class MembersInjectorImpl<T> implements MembersInjector<T> {
  private final TypeLiteral<T> typeLiteral;
  private final InjectorImpl injector;
  private final ImmutableList<SingleMemberInjector> memberInjectors;
  private final ImmutableList<InjectionListener<? super T>> injectionListeners;

  MembersInjectorImpl(InjectorImpl injector, TypeLiteral<T> typeLiteral,
      ImmutableList<SingleMemberInjector> memberInjectors) {
    this(injector, typeLiteral, memberInjectors, ImmutableList.<InjectionListener<? super T>>of());
  }

  MembersInjectorImpl(InjectorImpl injector, TypeLiteral<T> typeLiteral,
      ImmutableList<SingleMemberInjector> memberInjectors,
      ImmutableList<InjectionListener<? super T>> injectionListeners) {
    this.injector = injector;
    this.typeLiteral = typeLiteral;
    this.memberInjectors = memberInjectors;
    this.injectionListeners = injectionListeners;
  }

  public ImmutableList<SingleMemberInjector> getMemberInjectors() {
    return memberInjectors;
  }

  public void injectMembers(T instance) {
    Errors errors = new Errors(typeLiteral);
    try {
      injectMembers(instance, errors);
    } catch (ErrorsException e) {
      errors.merge(e.getErrors());
    }

    errors.throwProvisionExceptionIfErrorsExist();
  }

  void injectMembers(final T instance, final Errors errors) throws ErrorsException {
    if (instance == null) {
      return;
    }

    injector.callInContext(new ContextualCallable<Void>() {
      public Void call(InternalContext context) throws ErrorsException {
        injectMembers(instance, errors, context);
        return null;
      }
    });

    for (InjectionListener<? super T> injectionListener : injectionListeners) {
      try {
        injectionListener.afterInjection(instance);
      } catch (RuntimeException e) {
        throw errors.errorNotifyingInjectionListener(
            injectionListener, typeLiteral, e).toException();
      }
    }
  }

  void injectMembers(T t, Errors errors, InternalContext context) {
    for (SingleMemberInjector injector : memberInjectors) {
      injector.inject(errors, context, t);
    }
  }

  @Override public String toString() {
    return "MembersInjector<" + typeLiteral + ">";
  }

  public ImmutableSet<InjectionPoint> getInjectionPoints() {
    ImmutableSet.Builder<InjectionPoint> builder = ImmutableSet.builder();
    for (SingleMemberInjector memberInjector : memberInjectors) {
      builder.add(memberInjector.getInjectionPoint());
    }
    return builder.build();
  }

  MembersInjectorImpl<T> withListeners(
      ImmutableList<InjectionListener<? super T>> injectionListeners) {
    return injectionListeners.isEmpty()
        ? this
        : new MembersInjectorImpl<T>(injector, typeLiteral, memberInjectors, injectionListeners);
  }
}
