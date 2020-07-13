/*
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

package com.google.inject.internal;

import com.google.common.collect.ImmutableList;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.InjectionRequest;
import com.google.inject.spi.MembersInjectorLookup;
import com.google.inject.spi.ModuleAnnotatedMethodScannerBinding;
import com.google.inject.spi.ProviderLookup;
import com.google.inject.spi.ProvisionListenerBinding;
import com.google.inject.spi.ScopeBinding;
import com.google.inject.spi.StaticInjectionRequest;
import com.google.inject.spi.TypeConverterBinding;
import com.google.inject.spi.TypeListenerBinding;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The inheritable data within an injector. This class is intended to allow parent and local
 * injector data to be accessed as a unit.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
interface State {
  Optional<State> parent();

  /** Gets a binding which was specified explicitly in a module, or null. */
  <T> BindingImpl<T> getExplicitBinding(Key<T> key);

  /** Returns the explicit bindings at this level only. */
  Map<Key<?>, Binding<?>> getExplicitBindingsThisLevel();

  void putBinding(Key<?> key, BindingImpl<?> binding);

  void putProviderLookup(ProviderLookup<?> lookup);

  Set<ProviderLookup<?>> getProviderLookupsThisLevel();

  void putStaticInjectionRequest(StaticInjectionRequest staticInjectionRequest);

  Set<StaticInjectionRequest> getStaticInjectionRequestsThisLevel();

  ScopeBinding getScopeBinding(Class<? extends Annotation> scopingAnnotation);

  void putInjectionRequest(InjectionRequest<?> injectionRequest);

  Set<InjectionRequest<?>> getInjectionRequestsThisLevel();

  void putMembersInjectorLookup(MembersInjectorLookup<?> membersInjectorLookup);

  Set<MembersInjectorLookup<?>> getMembersInjectorLookupsThisLevel();

  void putScopeBinding(Class<? extends Annotation> annotationType, ScopeBinding scope);

  Collection<ScopeBinding> getScopeBindingsThisLevel();

  void addConverter(TypeConverterBinding typeConverterBinding);

  /** Returns the matching converter for {@code type}, or null if none match. */
  TypeConverterBinding getConverter(
      String stringValue, TypeLiteral<?> type, Errors errors, Object source);

  /** Returns all converters at this level only. */
  Iterable<TypeConverterBinding> getConvertersThisLevel();

  /*if[AOP]*/
  void addMethodAspect(MethodAspect methodAspect);

  ImmutableList<MethodAspect> getMethodAspects();
  /*end[AOP]*/

  void addTypeListener(TypeListenerBinding typeListenerBinding);

  ImmutableList<TypeListenerBinding> getTypeListenerBindings();

  ImmutableList<TypeListenerBinding> getTypeListenerBindingsThisLevel();

  void addProvisionListener(ProvisionListenerBinding provisionListenerBinding);

  ImmutableList<ProvisionListenerBinding> getProvisionListenerBindings();

  ImmutableList<ProvisionListenerBinding> getProvisionListenerBindingsThisLevel();

  void addScanner(ModuleAnnotatedMethodScannerBinding scanner);

  ImmutableList<ModuleAnnotatedMethodScannerBinding> getScannerBindings();

  ImmutableList<ModuleAnnotatedMethodScannerBinding> getScannerBindingsThisLevel();

  /**
   * Returns the shared lock for all injector data. This is a low-granularity, high-contention lock
   * to be used when reading mutable data (ie. just-in-time bindings, and binding blacklists).
   */
  Object lock();

  /** Returns all the scope bindings at this level and parent levels. */
  Map<Class<? extends Annotation>, Scope> getScopes();
}
