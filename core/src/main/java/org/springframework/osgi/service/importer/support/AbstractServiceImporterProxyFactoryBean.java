/*
 * Copyright 2006-2009 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.osgi.service.importer.support;

import java.security.AccessController;
import java.security.PrivilegedAction;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.osgi.context.internal.classloader.ChainedClassLoader;
import org.springframework.osgi.context.internal.classloader.ClassLoaderFactory;

/**
 * Package protected class that provides the common aop infrastructure functionality for OSGi service importers.
 * Provides most of the constructs required for assembling the service proxies, leaving subclasses to decide on the
 * service cardinality (one service or multiple) and proxy weaving.
 * 
 * 
 * @author Costin Leau
 * @author Adrian Colyer
 * @author Hal Hildebrand
 * 
 */
abstract class AbstractServiceImporterProxyFactoryBean extends AbstractOsgiServiceImportFactoryBean implements
		SmartFactoryBean<Object> {

	private boolean initialized = false;
	private Object proxy;
	private boolean useBlueprintException = false;
	private volatile boolean lazyProxy = false;

	/** aop classloader */
	private ChainedClassLoader aopClassLoader;
	private boolean blueprintCompliant;

	public void afterPropertiesSet() {
		super.afterPropertiesSet();

		if (blueprintCompliant) {
			setUseBlueprintExceptions(true);
		}

		Class<?>[] intfs = getInterfaces();

		for (int i = 0; i < intfs.length; i++) {
			Class<?> intf = intfs[i];
			if (blueprintCompliant && !intf.isInterface()) {
				throw new IllegalArgumentException(
						"Blueprint importers support only interfaces - for using concrete classes, use the Spring DM namespace");
			}
			aopClassLoader.addClassLoader(intf);
		}

		initialized = true;
	}

	public void destroy() throws Exception {
		Runnable callback = getProxyDestructionCallback();
		try {
			if (callback != null) {
				callback.run();
			}
		} finally {
			proxy = null;

		}
	}

	/**
	 * Returns a managed object for accessing OSGi service(s).
	 * 
	 * @return managed OSGi service(s)
	 */
	public Object getObject() {
		if (!initialized)
			throw new FactoryBeanNotInitializedException();

		if (proxy == null) {
			synchronized (this) {
				if (proxy == null) {
					proxy = createProxy(false);
				}
			}
		}

		if (lazyProxy) {
			synchronized (this) {
				if (lazyProxy) {
					getProxyInitializer().run();
					lazyProxy = false;
				}
			}
		}

		return proxy;
	}

	/**
	 * Returns the managed proxy type. Note that calling this method will cause the creation of the proxy but not its
	 * initialization.
	 */
	public Class<?> getObjectType() {
		if (!initialized)
			throw new FactoryBeanNotInitializedException();
		if (proxy == null) {
			synchronized (this) {
				if (proxy == null) {
					proxy = createProxy(true);
					lazyProxy = true;
				}
			}
		}
		return proxy.getClass();
	}

	/**
	 * {@inheritDoc}
	 * 
	 * The object managed by this factory is a singleton.
	 * 
	 * @return true (i.e. the FactoryBean returns singletons)
	 */
	public boolean isSingleton() {
		return true;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * The object created by this factory bean is eagerly initialized.
	 * 
	 * @return true (this factory bean should be eagerly initialized)
	 */
	public boolean isEagerInit() {
		return true;
	}

	/**
	 * {@inheritDoc} The object returned by this FactoryBean is a not a prototype.
	 * 
	 * @return false (the managed object is not a prototype)
	 */
	public boolean isPrototype() {
		return false;
	}

	/**
	 * Creates the proxy tracking the matching OSGi services. This method is guaranteed to be called only once, normally
	 * during initialization.
	 * 
	 * @param lazy indicates whether the proxy is lazy (no code is executed in the proxy) or not
	 * @return OSGi service tracking proxy.
	 * @see #getProxyDestructionCallback()
	 */
	abstract Object createProxy(boolean lazy);

	/**
	 * Returns a callback to the proxy which gets called when the proxy is called for the first time, if a lazy creation
	 * was used. For eager initialization, a null object should be returned.
	 * 
	 * @return proxy initialization callback
	 */
	abstract Runnable getProxyInitializer();

	/**
	 * Returns the destruction callback associated with the proxy created by this object. The callback is called once,
	 * during the destruction process of the {@link FactoryBean}.
	 * 
	 * @return destruction callback for the service proxy.
	 * @see #createProxy()
	 */
	abstract Runnable getProxyDestructionCallback();

	/**
	 * Returns the class loader used for AOP weaving
	 * 
	 * @return the classloader used for weaving
	 */
	ClassLoader getAopClassLoader() {
		return aopClassLoader;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * The class will automatically chain this classloader with the AOP infrastructure classes (even if these are not
	 * visible to the user) so that the proxy creation can be completed successfully.
	 */
	public void setBeanClassLoader(final ClassLoader classLoader) {
		super.setBeanClassLoader(classLoader);
		AccessController.doPrivileged(new PrivilegedAction<Object>() {

			public Object run() {
				aopClassLoader = ClassLoaderFactory.getAopClassLoaderFor(classLoader);
				return null;
			}
		});
	}

	/**
	 * Indicates whether Blueprint exceptions are preferred over Spring DM ones.
	 * 
	 * @param useBlueprintExceptions
	 */
	public void setUseBlueprintExceptions(boolean useBlueprintExceptions) {
		this.useBlueprintException = useBlueprintExceptions;
	}

	boolean isUseBlueprintExceptions() {
		return useBlueprintException;
	}

	/**
	 * Indicates whether the importer should use (strict) blueprint spec compliance or not. Strict compliance means that
	 * only interfaces are supported and that Blueprint exceptions are thrown by default.
	 * 
	 * @param compliant
	 */
	public void setBlueprintCompliant(boolean compliant) {
		this.blueprintCompliant = compliant;
	}

	boolean isBlueprintCompliant() {
		return blueprintCompliant;
	}
}