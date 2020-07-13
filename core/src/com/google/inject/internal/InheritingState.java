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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** @author jessewilson@google.com (Jesse Wilson) */
final class InheritingState implements State {

  // The parent injector's State object, if the parent injector exists.
  private final Optional<State> parent;

  // Must be a linked hashmap in order to preserve order of bindings in Modules.
  private final Map<Key<?>, Binding<?>> explicitBindingsMutable = Maps.newLinkedHashMap();
  private final Map<Key<?>, Binding<?>> explicitBindings =
      Collections.unmodifiableMap(explicitBindingsMutable);
  private final Map<Class<? extends Annotation>, ScopeBinding> scopes = Maps.newHashMap();
  private final Set<ProviderLookup<?>> providerLookups = Sets.newLinkedHashSet();
  private final Set<StaticInjectionRequest> staticInjectionRequests = Sets.newLinkedHashSet();
  private final Set<MembersInjectorLookup<?>> membersInjectorLookups = Sets.newLinkedHashSet();
  private final Set<InjectionRequest<?>> injectionRequests = Sets.newLinkedHashSet();
  private final List<TypeConverterBinding> converters = Lists.newArrayList();
  /*if[AOP]*/
  private final List<MethodAspect> methodAspects = Lists.newArrayList();
  /*end[AOP]*/
  private final List<TypeListenerBinding> typeListenerBindings = Lists.newArrayList();
  private final List<ProvisionListenerBinding> provisionListenerBindings = Lists.newArrayList();
  private final List<ModuleAnnotatedMethodScannerBinding> scannerBindings = Lists.newArrayList();
  private final Object lock;

  InheritingState(Optional<State> parent) {
    this.parent = parent;
    this.lock = parent.isPresent() ? parent.get().lock() : this;
  }

  @Override
  public Optional<State> parent() {
    return parent;
  }

  @Override
  @SuppressWarnings("unchecked") // we only put in BindingImpls that match their key types
  public <T> BindingImpl<T> getExplicitBinding(Key<T> key) {
    Binding<?> binding = explicitBindings.get(key);
    if (binding == null && parent.isPresent()) {
      return parent.get().getExplicitBinding(key);
    }
    return (BindingImpl<T>) binding;
  }

  @Override
  public Map<Key<?>, Binding<?>> getExplicitBindingsThisLevel() {
    return explicitBindings;
  }

  @Override
  public void putBinding(Key<?> key, BindingImpl<?> binding) {
    explicitBindingsMutable.put(key, binding);
  }

  @Override
  public void putProviderLookup(ProviderLookup<?> lookup) {
    providerLookups.add(lookup);
  }

  @Override
  public Set<ProviderLookup<?>> getProviderLookupsThisLevel() {
    return providerLookups;
  }

  @Override
  public void putStaticInjectionRequest(StaticInjectionRequest staticInjectionRequest) {
    staticInjectionRequests.add(staticInjectionRequest);
  }

  @Override
  public Set<StaticInjectionRequest> getStaticInjectionRequestsThisLevel() {
    return staticInjectionRequests;
  }

  @Override
  public void putInjectionRequest(InjectionRequest<?> injectionRequest) {
    injectionRequests.add(injectionRequest);
  }

  @Override
  public Set<InjectionRequest<?>> getInjectionRequestsThisLevel() {
    return injectionRequests;
  }

  @Override
  public void putMembersInjectorLookup(MembersInjectorLookup<?> membersInjectorLookup) {
    membersInjectorLookups.add(membersInjectorLookup);
  }

  @Override
  public Set<MembersInjectorLookup<?>> getMembersInjectorLookupsThisLevel() {
    return membersInjectorLookups;
  }

  @Override
  public ScopeBinding getScopeBinding(Class<? extends Annotation> annotationType) {
    ScopeBinding scopeBinding = scopes.get(annotationType);
    if (scopeBinding == null && parent.isPresent()) {
      return parent.get().getScopeBinding(annotationType);
    }
    return scopeBinding;
  }

  @Override
  public void putScopeBinding(Class<? extends Annotation> annotationType, ScopeBinding scope) {
    scopes.put(annotationType, scope);
  }

  @Override
  public Collection<ScopeBinding> getScopeBindingsThisLevel() {
    return scopes.values();
  }

  @Override
  public Iterable<TypeConverterBinding> getConvertersThisLevel() {
    return converters;
  }

  @Override
  public void addConverter(TypeConverterBinding typeConverterBinding) {
    converters.add(typeConverterBinding);
  }

  @Override
  public TypeConverterBinding getConverter(
      String stringValue, TypeLiteral<?> type, Errors errors, Object source) {
    TypeConverterBinding matchingConverter = null;
    State s = this;
    while (s != null) {
      for (TypeConverterBinding converter : s.getConvertersThisLevel()) {
        if (converter.getTypeMatcher().matches(type)) {
          if (matchingConverter != null) {
            errors.ambiguousTypeConversion(stringValue, source, type, matchingConverter, converter);
          }
          matchingConverter = converter;
        }
      }
      s = s.parent().orElse(null);
    }
    return matchingConverter;
  }

  /*if[AOP]*/
  @Override
  public void addMethodAspect(MethodAspect methodAspect) {
    methodAspects.add(methodAspect);
  }

  @Override
  public ImmutableList<MethodAspect> getMethodAspects() {
    if (parent.isPresent()) {
      return new ImmutableList.Builder<MethodAspect>()
          .addAll(parent.get().getMethodAspects())
          .addAll(methodAspects)
          .build();
    }
    return ImmutableList.copyOf(methodAspects);
  }
  /*end[AOP]*/

  @Override
  public void addTypeListener(TypeListenerBinding listenerBinding) {
    typeListenerBindings.add(listenerBinding);
  }

  @Override
  public ImmutableList<TypeListenerBinding> getTypeListenerBindings() {
    if (parent.isPresent()) {
      return new ImmutableList.Builder<TypeListenerBinding>()
          .addAll(parent.get().getTypeListenerBindings())
          .addAll(typeListenerBindings)
          .build();
    }
    return ImmutableList.copyOf(typeListenerBindings);
  }

  @Override
  public ImmutableList<TypeListenerBinding> getTypeListenerBindingsThisLevel() {
    return ImmutableList.copyOf(typeListenerBindings);
  }

  @Override
  public void addProvisionListener(ProvisionListenerBinding listenerBinding) {
    provisionListenerBindings.add(listenerBinding);
  }

  @Override
  public ImmutableList<ProvisionListenerBinding> getProvisionListenerBindings() {
    if (parent.isPresent()) {
      return new ImmutableList.Builder<ProvisionListenerBinding>()
          .addAll(parent.get().getProvisionListenerBindings())
          .addAll(provisionListenerBindings)
          .build();
    }
    return ImmutableList.copyOf(provisionListenerBindings);
  }

  @Override
  public ImmutableList<ProvisionListenerBinding> getProvisionListenerBindingsThisLevel() {
    return ImmutableList.copyOf(provisionListenerBindings);
  }

  @Override
  public void addScanner(ModuleAnnotatedMethodScannerBinding scanner) {
    scannerBindings.add(scanner);
  }

  @Override
  public ImmutableList<ModuleAnnotatedMethodScannerBinding> getScannerBindings() {
    if (parent.isPresent()) {
      return new ImmutableList.Builder<ModuleAnnotatedMethodScannerBinding>()
          .addAll(parent.get().getScannerBindings())
          .addAll(scannerBindings)
          .build();
    }
    return ImmutableList.copyOf(scannerBindings);
  }

  @Override
  public ImmutableList<ModuleAnnotatedMethodScannerBinding> getScannerBindingsThisLevel() {
    return ImmutableList.copyOf(scannerBindings);
  }

  @Override
  public Object lock() {
    return lock;
  }

  @Override
  public Map<Class<? extends Annotation>, Scope> getScopes() {
    ImmutableMap.Builder<Class<? extends Annotation>, Scope> builder = ImmutableMap.builder();
    for (Map.Entry<Class<? extends Annotation>, ScopeBinding> entry : scopes.entrySet()) {
      builder.put(entry.getKey(), entry.getValue().getScope());
    }
    return builder.build();
  }
}
