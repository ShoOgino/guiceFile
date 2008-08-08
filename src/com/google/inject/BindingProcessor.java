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

package com.google.inject;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.spi.BindingScopingVisitor;
import com.google.inject.spi.BindingTargetVisitor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles {@link Binder#bind} and {@link Binder#bindConstant} elements.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
class BindingProcessor extends AbstractProcessor {

  private static final BindingScopingVisitor<LoadStrategy> LOAD_STRATEGY_VISITOR
      = new BindingScopingVisitor<LoadStrategy>() {
    public LoadStrategy visitEagerSingleton() {
      return LoadStrategy.EAGER;
    }

    public LoadStrategy visitScope(Scope scope) {
      return LoadStrategy.LAZY;
    }

    public LoadStrategy visitScopeAnnotation(Class<? extends Annotation> scopeAnnotation) {
      return LoadStrategy.LAZY;
    }

    public LoadStrategy visitNoScoping() {
      return LoadStrategy.LAZY;
    }
  };

  private final InjectorImpl injector;
  private final Map<Class<? extends Annotation>, Scope> scopes;
  private final List<CreationListener> creationListeners = Lists.newArrayList();
  private final Map<Key<?>, BindingImpl<?>> bindings;
  private final CreationTimeMemberInjector memberInjector;
  private final List<Runnable> untargettedBindings = Lists.newArrayList();

  BindingProcessor(Errors errors,
      InjectorImpl injector,
      Map<Class<? extends Annotation>, Scope> scopes,
      Map<Key<?>, BindingImpl<?>> bindings,
      CreationTimeMemberInjector memberInjector) {
    super(errors);
    this.injector = injector;
    this.scopes = scopes;
    this.bindings = bindings;
    this.memberInjector = memberInjector;
  }

  @Override public <T> Boolean visitBinding(Binding<T> command) {
    final Object source = command.getSource();

    if (Void.class.equals(command.getKey().getRawType())) {
      errors.missingConstantValues();
      return true;
    }

    final Key<T> key = command.getKey();
    Class<? super T> rawType = key.getTypeLiteral().getRawType();

    if (rawType == Provider.class) {
      errors.bindingToProvider();
      return true;
    }

    validateKey(command.getSource(), command.getKey());

    final LoadStrategy loadStrategy = command.acceptScopingVisitor(LOAD_STRATEGY_VISITOR);
    final Scope scope = command.acceptScopingVisitor(new BindingScopingVisitor<Scope>() {
      public Scope visitEagerSingleton() {
        return Scopes.SINGLETON;
      }

      public Scope visitScope(Scope scope) {
        return scope;
      }

      public Scope visitScopeAnnotation(Class<? extends Annotation> scopeAnnotation) {
        Scope scope = scopes.get(scopeAnnotation);
        if (scope != null) {
          return scope;
        } else {
          errors.scopeNotFound(scopeAnnotation);
          return null;
        }
      }

      public Scope visitNoScoping() {
        return null;
      }
    });

    command.acceptTargetVisitor(new BindingTargetVisitor<T, Void>() {
      public Void visitInstance(T instance) {
        if (instance == null) {
          errors.cannotBindToNullInstance();
          putBinding(invalidBinding(injector, key, source));
          return null;
        }

        ConstantFactory<? extends T> factory = new ConstantFactory<T>(instance);
        memberInjector.requestInjection(instance, source);
        InternalFactory<? extends T> scopedFactory
            = Scopes.scope(key, injector, factory, scope);
        putBinding(new InstanceBindingImpl<T>(injector, key, source, scopedFactory, instance));
        return null;
      }

      public Void visitProvider(Provider<? extends T> provider) {
        InternalFactoryToProviderAdapter<? extends T> factory
            = new InternalFactoryToProviderAdapter<T>(provider, source);
        memberInjector.requestInjection(provider, source);
        InternalFactory<? extends T> scopedFactory
            = Scopes.scope(key, injector, factory, scope);
        putBinding(new ProviderInstanceBindingImpl<T>(
                injector, key, source, scopedFactory, scope, provider, loadStrategy));
        return null;
      }

      public Void visitProviderKey(Key<? extends Provider<? extends T>> providerKey) {
        final BoundProviderFactory<T> boundProviderFactory =
            new BoundProviderFactory<T>(providerKey, source);
        creationListeners.add(boundProviderFactory);
        InternalFactory<? extends T> scopedFactory = Scopes.scope(
            key, injector, (InternalFactory<? extends T>) boundProviderFactory, scope);
        putBinding(new LinkedProviderBindingImpl<T>(
                injector, key, source, scopedFactory, scope, providerKey, loadStrategy));
        return null;
      }

      public Void visitKey(Key<? extends T> targetKey) {
        if (key.equals(targetKey)) {
          errors.recursiveBinding();
        }

        FactoryProxy<T> factory = new FactoryProxy<T>(key, targetKey, source);
        creationListeners.add(factory);
        InternalFactory<? extends T> scopedFactory
            = Scopes.scope(key, injector, factory, scope);
        putBinding(new LinkedBindingImpl<T>(
                injector, key, source, scopedFactory, scope, targetKey, loadStrategy));
        return null;
      }

      public Void visitUntargetted() {
        final Type type = key.getTypeLiteral().getType();

        // Error: Missing implementation.
        // Example: bind(Date.class).annotatedWith(Red.class);
        // We can't assume abstract types aren't injectable. They may have an
        // @ImplementedBy annotation or something.
        if (key.hasAnnotationType() || !(type instanceof Class<?>)) {
          errors.missingImplementation(key);
          putBinding(invalidBinding(injector, key, source));
          return null;
        }

        // This cast is safe after the preceeding check.
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) type;
        final BindingImpl<T> binding;
        try {
          binding = injector.createUnitializedBinding(
              key, clazz, scope, source, loadStrategy, errors);
          putBinding(binding);
        } catch (ErrorsException e) {
          errors.merge(e.getErrors());
          putBinding(invalidBinding(injector, key, source));
          return null;
        }

        untargettedBindings.add(new Runnable() {
          public void run() {
            try {
              injector.initializeBinding(binding, errors.withSource(source));
            } catch (ErrorsException e) {
              errors.merge(e.getErrors());
            }
          }
        });

        return null;
      }

      public Void visitConvertedConstant(T value) {
        throw new IllegalArgumentException("Cannot apply a non-module element");
      }

      public Void visitConstructor(Constructor<? extends T> constructor) {
        throw new IllegalArgumentException("Cannot apply a non-module element");
      }

      public Void visitProviderBinding(Key<?> provided) {
        throw new IllegalArgumentException("Cannot apply a non-module element");
      }
    });

    return true;
  }

  private <T> void validateKey(Object source, Key<T> key) {
    Scopes.checkForMisplacedScopeAnnotations(key.getRawType(), source, errors);
  }

  <T> InvalidBindingImpl<T> invalidBinding(InjectorImpl injector, Key<T> key, Object source) {
    return new InvalidBindingImpl<T>(injector, key, source);
  }

  public void createUntargettedBindings() {
    for (Runnable untargettedBinding : untargettedBindings) {
      untargettedBinding.run();
    }
  }

  public void runCreationListeners(InjectorImpl injector) {
    for (CreationListener creationListener : creationListeners) {
      creationListener.notify(injector, errors);
    }
  }

  private void putBinding(BindingImpl<?> binding) {
    Key<?> key = binding.getKey();
    Binding<?> original = bindings.get(key);

    Class<?> rawType = key.getRawType();
    if (FORBIDDEN_TYPES.contains(rawType)) {
      errors.cannotBindToGuiceType(rawType.getSimpleName());
      return;
    }

    if (bindings.containsKey(key)) {
      errors.bindingAlreadySet(key, original.getSource());
    } else {
      bindings.put(key, binding);
    }
  }

  // It's unfortunate that we have to maintain a blacklist of specific
  // classes, but we can't easily block the whole package because of
  // all our unit tests.
  private static final Set<Class<?>> FORBIDDEN_TYPES = ImmutableSet.of(
      AbstractModule.class,
      Binder.class,
      Binding.class,
      // Injector.class,
      Key.class,
      Module.class,
      Provider.class, 
      Scope.class,
      TypeLiteral.class);
  // TODO(jessewilson): fix BuiltInModule, then add Injector and Stage

  interface CreationListener {
    void notify(InjectorImpl injector, Errors errors);
  }
}
