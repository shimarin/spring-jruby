package net.stbbs.spring.jruby.modules;


import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.collection.PersistentCollection;
import org.hibernate.collection.PersistentSet;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.hibernate.type.EmbeddedComponentType;
import org.jruby.Ruby;
import org.jruby.RubyHash;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyMethod;
import org.jruby.exceptions.RaiseException;
import org.jruby.javasupport.JavaEmbedUtils;

import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.context.ApplicationContext;

public class HibernateSupport extends DataSourceSupport {
	static Log logger = LogFactory.getLog(HibernateSupport.class);	

	public HibernateSupport(Ruby runtime, IRubyObject self)
	{
		super(runtime, self);
	}
	
	protected SessionFactory getSessionFactory()
	{
		return getSessionFactory(this.self);
	}
	
	protected Session getCurrentSession()
	{
		return getSessionFactory().getCurrentSession();
	}

	static protected SessionFactory getSessionFactory(IRubyObject self)
	{
		return ApplicationContextSupport.getBean(self, "sessionFactory");
	}
	
	static protected Session getCurrentSession(IRubyObject self)
	{
		return getSessionFactory(self).getCurrentSession();
	}
	
	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, HibernateEntityProxyDecorator.class);
		Util.registerDecorator(runtime, HibernateIdClassProxyDecorator.class);
		
		/**
		 * アプリケーションコンテキストに登録されている全てのSessionFactoryについて、エンティティをエンハンスする
		 */
		ApplicationContext applicationContext = (ApplicationContext)
			JavaEmbedUtils.rubyToJava(runtime, 
				runtime.getModule("ApplicationContextSupport").callMethod(runtime.getCurrentContext(), "applicationContext"),
				ApplicationContext.class);
		Iterator i = applicationContext.getBeansOfType(SessionFactory.class).values().iterator();
		while (i.hasNext()) {
			SessionFactory sessionFactory = (SessionFactory)i.next();
			Map md = sessionFactory.getAllClassMetadata();
			for (Object e:md.values()) {
				if (!(e instanceof AbstractEntityPersister)) continue;
				AbstractEntityPersister persister = (AbstractEntityPersister)e;
				Class returnedClass = persister.getType().getReturnedClass();
				enhance(Util.getJavaClassProxy(runtime, returnedClass));
				logger.info("HibernateSupport: エンティティ " + returnedClass.getName() + " がエンハンスされました");
			}
		}
		
	}
	
	@JRubyMethod
	public Session currentSession(IRubyObject self, IRubyObject[] args, Block block) {
		return getCurrentSession();
	}

	/**
	 * const_missingでHibernateに登録されているエンティティクラスのproxyをゲット出来るようにする
	 * @param self
	 * @param args
	 * @param block
	 */
	@JRubyMethod(required=1)
	static public void included(IRubyObject self, IRubyObject[] args, Block block)
	{
		final String origConstMissing = "orig_const_missing_by_hibernate_support";
		Ruby runtime = self.getRuntime();
		RubyModule target = (RubyModule)args[0];
		RubyModule singletonClass = target.getSingletonClass();
		singletonClass.alias_method(runtime.newString(origConstMissing), runtime.newString("const_missing"));
		target.getSingletonClass().defineMethod("const_missing", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				Ruby runtime = self.getRuntime();
				String name = args[0].asString().getUnicodeValue();
				SessionFactory sessionFactory = ApplicationContextSupport.getBean(self, "sessionFactory");
				Map md = sessionFactory.getAllClassMetadata();
				for (Object e:md.values()) {
					if (!(e instanceof AbstractEntityPersister)) continue;
					AbstractEntityPersister persister = (AbstractEntityPersister)e;
					Class returnedClass = persister.getType().getReturnedClass();
					if (returnedClass.getSimpleName().equals(name)) {
						return JavaEmbedUtils.javaToRuby(runtime, HibernateEntityProxy.getHibernateEntityProxy(returnedClass)); // 見つけた
					}
					org.hibernate.type.Type idType = persister.getIdentifierType();
					if (idType instanceof EmbeddedComponentType) {
						returnedClass = ((EmbeddedComponentType)idType).getReturnedClass();
						if (returnedClass.getSimpleName().equals(name)) {
							return JavaEmbedUtils.javaToRuby(runtime, HibernateIdClassProxy.getHibernateIdClassProxy(returnedClass)); // 見つけた
						}
					}
				}
				
				return self.callMethod(runtime.getCurrentContext(), origConstMissing, args, block);
			}

			public Arity getArity() {
				return Arity.ONE_ARGUMENT;
			}
			
		});
	}
	
	static public void enhance(RubyModule clazz)
	{
		clazz.defineMethod("save", new Callback() {

			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				Ruby runtime = self.getRuntime();
				Object entity = JavaEmbedUtils.rubyToJava(runtime, self, null);
				return JavaEmbedUtils.javaToRuby(runtime, getCurrentSession(self).save(entity));
			}

			public Arity getArity() {
				return Arity.NO_ARGUMENTS;
			}
		});
		clazz.defineMethod("evict", new Callback() {

			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				Ruby runtime = self.getRuntime();
				Object entity = JavaEmbedUtils.rubyToJava(runtime, self, null);
				getCurrentSession(self).evict(entity);
				return runtime.getNil();
			}

			public Arity getArity() {
				return Arity.NO_ARGUMENTS;
			}
		});
		clazz.defineMethod("delete", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				Ruby runtime = self.getRuntime();
				Object entity = JavaEmbedUtils.rubyToJava(runtime, self, null);
				getCurrentSession(self).delete(entity);
				return runtime.getNil();
			}

			public Arity getArity() {
				return Arity.NO_ARGUMENTS;
			}
		});
		clazz.defineMethod("detach", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				Ruby runtime = self.getRuntime();
				Object entity = JavaEmbedUtils.rubyToJava(runtime, self, null);
				return JavaEmbedUtils.javaToRuby(runtime, EntityDetacher.detach(entity));
			}

			public Arity getArity() {
				return Arity.NO_ARGUMENTS;
			}
		});
	}
	
	public static class HibernateEntityProxy {
		static Map<Class, HibernateEntityProxy> proxies;
		private Class entityClass;
		protected HibernateEntityProxy(Class entityClass)
		{
			this.entityClass = entityClass;
		}
		
		public Class getEntityClass()
		{
			return entityClass;
		}
	
		synchronized static HibernateEntityProxy getHibernateEntityProxy(Class entityClass) 
		{
			if (proxies == null) proxies = new HashMap<Class, HibernateEntityProxy>();
			HibernateEntityProxy proxy = proxies.get(entityClass);
			if (proxy == null) {
				proxy = new HibernateEntityProxy(entityClass);
				proxies.put(entityClass, proxy);
			}
			return proxy;
		}
	}
	
	@Decorator(HibernateEntityProxy.class)
	public static class HibernateEntityProxyDecorator {
		private HibernateEntityProxy entityProxy;
		public HibernateEntityProxyDecorator(HibernateEntityProxy entityProxy)
		{
			this.entityProxy = entityProxy;
		}
		
		@JRubyMethod(name="new")
		public Object _new(IRubyObject self, IRubyObject[] args, Block block) throws InstantiationException, IllegalAccessException
		{
			Ruby runtime = self.getRuntime();
			Object newInstance = entityProxy.getEntityClass().newInstance();
			if (args.length > 0 && args[0] instanceof RubyHash) {
				Util.setValueToBean(newInstance, (RubyHash)args[0]);
			}
			return newInstance;
		}
		
		@JRubyMethod(required=1)
		public Object get(IRubyObject self, IRubyObject[] args, Block block)
		{
			Ruby runtime = self.getRuntime();
			if (args.length < 1) {
				throw runtime.newArgumentError(args.length, 1);
			}
			Serializable key = (Serializable) JavaEmbedUtils.rubyToJava(runtime, args[0], Serializable.class);
			Session session = getCurrentSession(self);
			return session.get(entityProxy.getEntityClass(), key);
		}
		
		@JRubyMethod
		public List find(IRubyObject self, IRubyObject[] args, Block block)
		{
			Ruby runtime = self.getRuntime();
			Session session = getCurrentSession(self);
			String hql = "from " + entityProxy.getEntityClass().getName();
			if (args.length > 0) {
				String cond = args[0].asString().getUnicodeValue();
				hql += " " + cond;
			}
			Query q = session.createQuery(hql);
			for (int i = 1; i < args.length; i++) {
				q.setParameter(i - 1, JavaEmbedUtils.rubyToJava(runtime,args[i], null));
			}
			return q.list();
		}
	}
	
	public static class HibernateIdClassProxy {
		static Map<Class, HibernateIdClassProxy> proxies;
		private Class idClass;
		protected HibernateIdClassProxy(Class idClass)
		{
			this.idClass = idClass;
		}
		
		public Class getIdClass()
		{
			return idClass;
		}
	
		synchronized static HibernateIdClassProxy getHibernateIdClassProxy(Class idClass) 
		{
			if (proxies == null) proxies = new HashMap<Class, HibernateIdClassProxy>();
			HibernateIdClassProxy proxy = proxies.get(idClass);
			if (proxy == null) {
				proxy = new HibernateIdClassProxy(idClass);
				proxies.put(idClass, proxy);
			}
			return proxy;
		}
	}
	
	@Decorator(HibernateIdClassProxy.class)
	public static class HibernateIdClassProxyDecorator {
		private HibernateIdClassProxy idClassProxy;
		public HibernateIdClassProxyDecorator(HibernateIdClassProxy idClassProxy)
		{
			this.idClassProxy = idClassProxy;
		}

		@JRubyMethod(name="new")
		public Serializable _new(IRubyObject self, IRubyObject[] args, Block block) throws InstantiationException, IllegalAccessException
		{
			Serializable newInstance = (Serializable)idClassProxy.getIdClass().newInstance();
			if (args.length > 0 && args[0] instanceof RubyHash) {
				Util.setValueToBean(newInstance, (RubyHash)args[0]);
			}
			return newInstance;
		}
	}
	
	/**
	 * Hibernateのオブジェクトをデタッチコピーする
	 * @author shimarin
	 *
	 */
	public static class EntityDetacher {
		protected Collection<Object[]> objectMap = new ArrayList<Object[]>();

		protected Object getExisting(Object obj)
		{
			for (Object[] o:objectMap) {
				if (o[0] == obj) {
					return o[1];
				}
			}
			return null;
		}
		
		protected void setToMap(Object orig, Object serialized)
		{
			if (getExisting(orig) != null) return;
			objectMap.add(new Object[] {orig, serialized});
		}

		protected Collection<Object> newCollection(Object forThis) throws InstantiationException, IllegalAccessException
		{
			Collection<Object> col;
			if (forThis instanceof PersistentSet) {
				col = new HashSet();
			} else if (forThis instanceof PersistentCollection) {
				col = new ArrayList();
			} else {
				col = (Collection)forThis.getClass().newInstance();
			}
			setToMap(forThis, col);
			return col;
		}
		
		protected Object[] newArray(Object forThis, int size)
		{
			Object[] arr = new Object[size];
			setToMap(forThis, arr);
			return arr;
		}
		
		protected Map<String,Object> newMap(Object forThis)
		{
			Map<String,Object> map = new HashMap<String,Object>();
			setToMap(forThis, map);
			return map;
		}
		
		protected Object newObject(Object forThis) throws InstantiationException, IllegalAccessException
		{
			Object obj = forThis.getClass().newInstance();
			setToMap(forThis, obj);
			return obj;
		}
		
		public Object detachCopy(Object o)
		{
			if (o == null) return null;
			
			if (o instanceof HibernateProxy) {
				LazyInitializer li = ( (HibernateProxy) o ).getHibernateLazyInitializer();
				if (li.isUninitialized()) {
					return null;
				}
				if ( li.isUnwrap() ) {
					o = li.getImplementation();
				}
			}
			
			if (o instanceof PersistentCollection) {
				PersistentCollection pc = (PersistentCollection)o;
				if (!pc.wasInitialized()) {
					return null;
				}
			}

			if (o instanceof String) return o;
			if (o instanceof Number) return o;
			if (o instanceof java.util.Date) return o;
			if (o instanceof java.util.Calendar) return o;
			if (o instanceof Boolean) return o;

			Object existingOne = getExisting(o);
			if (existingOne != null) return existingOne;

			if (o instanceof Collection) {
				try {
					Collection<Object> col = newCollection(o);
					for (Object co:(Collection)o) {
						col.add(detachCopy(co));
					}
					return col;
				}
				catch (InstantiationException ex) {
					logger.warn("InstantiationException", ex);
				} catch (IllegalAccessException ex) {
					logger.warn("IllegalAccessException", ex);
				}
				// 例外発生時はそのままのオブジェクトを返す
				return o;
			}
			
			if (o.getClass().isArray()) {
				Object[] src = (Object[])o;
				Object[] dst = newArray(o, src.length);
				for (int i = 0; i < dst.length; i++) {
					dst[i] = detachCopy(src[i]);
				}
				return dst;
			}
			
			//Map<String,Object> map = newMap(o);
			try {
				Object clone = newObject(o);
				Field[] fields = o.getClass().getFields();
				for (Field f:fields) {
					int mod = f.getModifiers();
					if (Modifier.isPublic(mod) && !Modifier.isStatic(mod)) {
						Object src = detachCopy(f.get(o));
						logger.info("clone=" + clone + ", f=" + f.getName() + ", src=" + src);
						f.set(clone, src );
					}
				}
				
				BeanWrapperImpl bwiSrc = new BeanWrapperImpl(o);
				BeanWrapperImpl bwiDst = new BeanWrapperImpl(clone);
				
				PropertyDescriptor[] props = bwiSrc.getPropertyDescriptors();
				for (PropertyDescriptor pd:props) {
					String propName = pd.getName();
					Method readMethod = pd.getReadMethod();
					Method writeMethod = pd.getWriteMethod();
					if (readMethod == null || writeMethod == null) continue;
					if (readMethod.getParameterTypes().length != 0) continue;
					if (writeMethod.getParameterTypes().length != 1) continue;
					if (writeMethod.getParameterTypes()[0] != readMethod.getReturnType()) continue;
					Object value = bwiSrc.getPropertyValue(propName);
					bwiDst.setPropertyValue(propName, detachCopy(value));
				}

				return clone;
			}
			catch (InstantiationException ex) {
				logger.warn("InstantiationException", ex);
			} catch (IllegalAccessException ex) {
				logger.warn("IllegalAccessException", ex);
			}
			// 例外発生時はそのままのオブジェクトを返す
			return o;
		}
		public static Object detach(Object o)
		{
			EntityDetacher me = new EntityDetacher();
			return me.detachCopy(o);
		}

	}

}
