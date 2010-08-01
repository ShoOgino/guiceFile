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

package com.google.inject.internal;

import com.google.inject.internal.util.Function;
import com.google.inject.internal.util.ImmutableMap;
import com.google.inject.internal.util.MapMaker;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Utility methods for runtime code generation and class loading. We use this stuff for {@link
 * net.sf.cglib.reflect.FastClass faster reflection}, {@link net.sf.cglib.proxy.Enhancer method
 * interceptors} and to proxy circular dependencies.
 *
 * <p>When loading classes, we need to be careful of:
 * <ul>
 *   <li><strong>Memory leaks.</strong> Generated classes need to be garbage collected in long-lived
 *       applications. Once an injector and any instances it created can be garbage collected, the
 *       corresponding generated classes should be collectable.
 *   <li><strong>Visibility.</strong> Containers like <code>OSGi</code> use class loader boundaries
 *       to enforce modularity at runtime.
 * </ul>
 *
 * <p>For each generated class, there's multiple class loaders involved:
 * <ul>
 *    <li><strong>The related class's class loader.</strong> Every generated class services exactly
 *        one user-supplied class. This class loader must be used to access members with private and
 *        package visibility.
 *    <li><strong>Guice's class loader.</strong>
 *    <li><strong>Our bridge class loader.</strong> This is a child of the user's class loader. It
 *        selectively delegates to either the user's class loader (for user classes) or the Guice
 *        class loader (for internal classes that are used by the generated classes). This class
 *        loader that owns the classes generated by Guice.
 * </ul>
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class BytecodeGen {

  static final Logger logger = Logger.getLogger(BytecodeGen.class.getName());

  static final ClassLoader GUICE_CLASS_LOADER = canonicalize(BytecodeGen.class.getClassLoader());

  // initialization-on-demand...
  private static class SystemBridgeHolder {
    static final BridgeClassLoader SYSTEM_BRIDGE = new BridgeClassLoader();
  }

  /** ie. "com.google.inject.internal" */
  static final String GUICE_INTERNAL_PACKAGE
      = BytecodeGen.class.getName().replaceFirst("\\.internal\\..*$", ".internal");

  /*if[AOP]*/
  /** either "net.sf.cglib", or "com.google.inject.internal.cglib" */
  static final String CGLIB_PACKAGE
      = net.sf.cglib.proxy.Enhancer.class.getName().replaceFirst("\\.cglib\\..*$", ".cglib");

  static final net.sf.cglib.core.NamingPolicy NAMING_POLICY
      = new net.sf.cglib.core.DefaultNamingPolicy() {
    @Override protected String getTag() {
      return "ByGuice";
    }
  };
  /*end[AOP]*/
  /*if[NO_AOP]
  private static final String CGLIB_PACKAGE = " "; // any string that's illegal in a package name
  end[NO_AOP]*/

  /** Use "-Dguice.custom.loader=false" to disable custom classloading. */
  private static final boolean CUSTOM_LOADER_ENABLED
      = Boolean.parseBoolean(System.getProperty("guice.custom.loader", "true"));

  /**
   * Weak cache of bridge class loaders that make the Guice implementation
   * classes visible to various code-generated proxies of client classes.
   */
  private static final Map<ClassLoader, ClassLoader> CLASS_LOADER_CACHE;

  static {
    if (CUSTOM_LOADER_ENABLED) {
      CLASS_LOADER_CACHE = new MapMaker().weakKeys().weakValues().makeComputingMap(
          new Function<ClassLoader, ClassLoader>() {
            public ClassLoader apply(final @Nullable ClassLoader typeClassLoader) {
              logger.fine("Creating a bridge ClassLoader for " + typeClassLoader);
              return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                public ClassLoader run() {
                  return new BridgeClassLoader(typeClassLoader);
                }
              });
            }
          });
    } else {
      CLASS_LOADER_CACHE = ImmutableMap.of();
    }
  }

  /**
   * Attempts to canonicalize null references to the system class loader.
   * May return null if for some reason the system loader is unavailable.
   */
  private static ClassLoader canonicalize(ClassLoader classLoader) {
    return classLoader != null ? classLoader : SystemBridgeHolder.SYSTEM_BRIDGE.getParent();
  }

  /**
   * Returns the class loader to host generated classes for {@code type}.
   */
  public static ClassLoader getClassLoader(Class<?> type) {
    return getClassLoader(type, type.getClassLoader());
  }

  private static ClassLoader getClassLoader(Class<?> type, ClassLoader delegate) {

    // simple case: do nothing!
    if (!CUSTOM_LOADER_ENABLED) {
      return delegate;
    }
    
    // java.* types can be seen everywhere
    if (type.getName().startsWith("java.")) {
      return GUICE_CLASS_LOADER;
    }

    delegate = canonicalize(delegate);

    // no need for a bridge if using same class loader, or it's already a bridge
    if (delegate == GUICE_CLASS_LOADER || delegate instanceof BridgeClassLoader) {
      return delegate;
    }

    // don't try bridging private types as it won't work
    if (Visibility.forType(type) == Visibility.PUBLIC) {
      if (delegate != SystemBridgeHolder.SYSTEM_BRIDGE.getParent()) {
        // delegate guaranteed to be non-null here
        return CLASS_LOADER_CACHE.get(delegate);
      }
      // delegate may or may not be null here
      return SystemBridgeHolder.SYSTEM_BRIDGE;
    }

    return delegate; // last-resort: do nothing!
  }

  /*if[AOP]*/
  // use fully-qualified names so imports don't need preprocessor statements 
  public static net.sf.cglib.reflect.FastClass newFastClass(Class<?> type, Visibility visibility) {
    net.sf.cglib.reflect.FastClass.Generator generator
        = new net.sf.cglib.reflect.FastClass.Generator();
    generator.setType(type);
    if (visibility == Visibility.PUBLIC) {
      generator.setClassLoader(getClassLoader(type));
    }
    generator.setNamingPolicy(NAMING_POLICY);
    logger.fine("Loading " + type + " FastClass with " + generator.getClassLoader());
    return generator.create();
  }

  public static net.sf.cglib.proxy.Enhancer newEnhancer(Class<?> type, Visibility visibility) {
    net.sf.cglib.proxy.Enhancer enhancer = new net.sf.cglib.proxy.Enhancer();
    enhancer.setSuperclass(type);
    enhancer.setUseFactory(false);
    if (visibility == Visibility.PUBLIC) {
      enhancer.setClassLoader(getClassLoader(type));
    }
    enhancer.setNamingPolicy(NAMING_POLICY);
    logger.fine("Loading " + type + " Enhancer with " + enhancer.getClassLoader());
    return enhancer;
  }
  /*end[AOP]*/

  /**
   * The required visibility of a user's class from a Guice-generated class. Visibility of
   * package-private members depends on the loading classloader: only if two classes were loaded by
   * the same classloader can they see each other's package-private members. We need to be careful
   * when choosing which classloader to use for generated classes. We prefer our bridge classloader,
   * since it's OSGi-safe and doesn't leak permgen space. But often we cannot due to visibility.
   */
  public enum Visibility {

    /**
     * Indicates that Guice-generated classes only need to call and override public members of the
     * target class. These generated classes may be loaded by our bridge classloader.
     */
    PUBLIC {
      @Override
      public Visibility and(Visibility that) {
        return that;
      }
    },

    /**
     * Indicates that Guice-generated classes need to call or override package-private members.
     * These generated classes must be loaded in the same classloader as the target class. They
     * won't work with OSGi, and won't get garbage collected until the target class' classloader is
     * garbage collected.
     */
    SAME_PACKAGE {
      @Override
      public Visibility and(Visibility that) {
        return this;
      }
    };

    public static Visibility forMember(Member member) {
      if ((member.getModifiers() & (Modifier.PROTECTED | Modifier.PUBLIC)) == 0) {
        return SAME_PACKAGE;
      }

      Class[] parameterTypes = member instanceof Constructor
          ? ((Constructor) member).getParameterTypes()
          : ((Method) member).getParameterTypes();
      for (Class<?> type : parameterTypes) {
        if (forType(type) == SAME_PACKAGE) {
          return SAME_PACKAGE;
        }
      }

      return PUBLIC;
    }

    public static Visibility forType(Class<?> type) {
      return (type.getModifiers() & (Modifier.PROTECTED | Modifier.PUBLIC)) != 0
          ? PUBLIC
          : SAME_PACKAGE;
    }

    public abstract Visibility and(Visibility that);
  }

  /**
   * Loader for Guice-generated classes. For referenced classes, this delegates to either either the
   * user's classloader (which is the parent of this classloader) or Guice's class loader.
   */
  private static class BridgeClassLoader extends ClassLoader {

    BridgeClassLoader() {
      // use system loader as parent
    }

    BridgeClassLoader(ClassLoader usersClassLoader) {
      super(usersClassLoader);
    }

    @Override protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {

      if (name.startsWith("sun.reflect")) {
        // these reflection classes must be loaded from bootstrap class loader
        return SystemBridgeHolder.SYSTEM_BRIDGE.classicLoadClass(name, resolve);
      }

      if (name.startsWith(GUICE_INTERNAL_PACKAGE) || name.startsWith(CGLIB_PACKAGE)) {
        if (null == GUICE_CLASS_LOADER) {
          // use special system bridge to load classes from bootstrap class loader
          return SystemBridgeHolder.SYSTEM_BRIDGE.classicLoadClass(name, resolve);
        }
        try {
          Class<?> clazz = GUICE_CLASS_LOADER.loadClass(name);
          if (resolve) {
            resolveClass(clazz);
          }
          return clazz;
        } catch (Throwable e) {
          // fall-back to classic delegation
        }
      }

      return classicLoadClass(name, resolve);
    }

    // make the classic delegating loadClass method visible
    Class<?> classicLoadClass(String name, boolean resolve)
      throws ClassNotFoundException {
      return super.loadClass(name, resolve);
    }
  }
}
