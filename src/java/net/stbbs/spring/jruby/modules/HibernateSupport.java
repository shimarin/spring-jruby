package net.stbbs.spring.jruby.modules;

import java.beans.PropertyDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import javax.persistence.Entity;
import javax.persistence.Table;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.MappingException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.cfg.Settings;
import org.hibernate.collection.PersistentCollection;
import org.hibernate.collection.PersistentMap;
import org.hibernate.collection.PersistentSet;
import org.hibernate.impl.SessionFactoryImpl;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.hibernate.tool.hbm2ddl.SchemaUpdate;
import org.hibernate.type.EmbeddedComponentType;
import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyHash;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyMethod;
import org.jruby.exceptions.RaiseException;
import org.jruby.javasupport.JavaEmbedUtils;

import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

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
				EntitySerializer es = new EntitySerializer();
				try {
					return JavaEmbedUtils.javaToRuby(runtime, es.serialize(entity));
				} catch (InstantiationException e) {
					throw RaiseException.createNativeRaiseException(runtime, e);
				} catch (IllegalAccessException e) {
					throw RaiseException.createNativeRaiseException(runtime, e);
				} catch (InvocationTargetException e) {
					throw RaiseException.createNativeRaiseException(runtime, e.getTargetException());
				} catch (NoSuchMethodException e) {
					throw RaiseException.createNativeRaiseException(runtime, e);
				}
			}

			public Arity getArity() {
				return Arity.NO_ARGUMENTS;
			}
		});
	}

	/*
	@Override
	public void register(RubyModule newModule) {
		newModule.definePrivateMethod("createQuery", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				return toRuby(getCurrentSession(self).createQuery(args[0].asString().getUnicodeValue()));
			}
			public Arity getArity() {
				return Arity.ONE_ARGUMENT;
			}
			
		});
		newModule.definePrivateMethod("createSQLQuery", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				return toRuby(getCurrentSession(self).createSQLQuery(args[0].asString().getUnicodeValue()));
			}
			public Arity getArity() {
				return Arity.ONE_ARGUMENT;
			}
		});
		newModule.definePrivateMethod("flush", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				getCurrentSession(self).flush();
				return getNil();
			}
			public Arity getArity() {
				return Arity.NO_ARGUMENTS;
			}
		});
		newModule.definePrivateMethod("clear", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				getCurrentSession(self).clear();
				return getNil();
			}
			public Arity getArity() {
				return Arity.NO_ARGUMENTS;
			}
		});
		newModule.definePrivateMethod("detachCopy", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				EntitySerializer es = new EntitySerializer();
				try {
					return toRuby(es.serialize(toJava(args[0])));
				} catch (Exception e) {
					throw newArgumentError("detachCopy failed:" + e.getMessage());
				}
			}
			public Arity getArity() {
				return Arity.ONE_ARGUMENT;
			}
		});
	}
*/
	protected Collection<Class> anArgToClasses(IRubyObject arg)
	{
		Object jo = toJava(arg);
		String className = null;
		Collection<Class> classes = new ArrayList<Class>();
		try {
			if (jo instanceof Class) {
				classes.add((Class)jo);
			} else if (arg instanceof RubyArray) {
				RubyArray ra = (RubyArray)arg;
				Iterator i = ra.iterator();
				while (i.hasNext()) {
					Object o = i.next();
					if (o instanceof Class) {
						classes.add((Class)o);
					} else {
						className = o.toString();
						classes.add(Class.forName(className));
					}
				}
			} else {
				className = arg.asString().getUnicodeValue();
				classes.add(Class.forName(className));
			}
		} catch (MappingException e) {
			throw runtime.newArgumentError("Class '" + className + "' couldn't load.");
		} catch (ClassNotFoundException e) {
			throw runtime.newArgumentError("Class '" + className + "' couldn't load.");
		}
		return classes;
	}

	@JRubyMethod(required=1,optional=1)
	public IRubyObject schemaUpdate(IRubyObject self, IRubyObject[] args, Block block) throws IOException, ClassNotFoundException {
		// 引数が足りない場合エラー
		if (args.length < 1) {
			throw self.getRuntime().newArgumentError("Method requires at least one argument.");
		}
		
		Collection<Class> classes;
		Object firstArg = JavaEmbedUtils.rubyToJava(runtime, args[0], null); 
		if (firstArg instanceof Resource) {// jarとみなす
			classes = new HashSet<Class>();
			JarClassLoader jcl = new JarClassLoader((Resource)firstArg);
			for (String className:jcl.getAllClassNames()) {
				Class clazz = Class.forName(className, true, jcl);
				if (clazz.getAnnotation(Entity.class) != null) classes.add(clazz);
			}
		} else {
			classes = this.anArgToClasses(args[0]);
		}
	
		AnnotationConfiguration ac = new AnnotationConfiguration();
		Settings settings = ((SessionFactoryImpl)getSessionFactory()).getSettings();
		RubyArray tableNames = runtime.newArray();
		for (Class c:classes) {
			Table t = (Table) c.getAnnotation(Table.class);
			if (t != null) {
				String tableName = t.name();
				if ("".equals(tableName)) {
					tableName = c.getName();
				}
				tableNames.add(tableName);
			} else {
				tableNames.add(c.getName());
			}
			ac.addAnnotatedClass(c);
		}
		SchemaUpdate su = new SchemaUpdate(ac, settings);
		su.execute(true, true);

		return tableNames;
	}

	@JRubyMethod(required=1,optional=1)
	public IRubyObject schemaReplace(IRubyObject self, IRubyObject[] args, Block block) {
		// 引数が足りない場合エラー
		if (args.length < 1) {
			throw self.getRuntime().newArgumentError("Method requires at least one argument.");
		}
		
		Collection<Class> classes = this.anArgToClasses(args[0]);

		AnnotationConfiguration ac = new AnnotationConfiguration();
		Settings settings = ((SessionFactoryImpl)getSessionFactory()).getSettings();
		JdbcTemplate jt = new JdbcTemplate(getDataSource());
		for (Class c:classes) {
			Table t = (Table)c.getAnnotation(Table.class);
			if (t != null) {
				try {
					jt.execute("drop table " + t.name());	// might be necessary to escape depend on kind of DBMS
				}
				catch (BadSqlGrammarException ex) {
					// probably the table wasn't exist
				}
				ac.addAnnotatedClass(c);
			}
		}
		SchemaUpdate su = new SchemaUpdate(ac, settings);
		su.execute(true, true);

		return runtime.getNil();
	}
	
	protected static class JarClassLoader extends ClassLoader {
		private Resource jar;
		private static final int BUFFER_SIZE = 1024;

		public JarClassLoader(Resource jar)
		{
			this.jar = jar;
		}
		protected Class findClass(String name) throws ClassNotFoundException {
			
			String nameInJarFile = name.replace(".", "/") + ".class";
			
			try {
				InputStream is = jar.getInputStream();
				try {
					JarInputStream ji = new JarInputStream(is);
					JarEntry je;
					while ((je = ji.getNextJarEntry()) != null) {
						if (je.getName().equals(nameInJarFile)) continue;
						// else
						byte[] data = read(ji);
						return defineClass(name, data, 0, data.length);
					}
					ji.close();
				}
				finally {
					is.close();
				}
			}
			catch (IOException ex) {
				throw new ClassNotFoundException(ex.getMessage());
			}
			return null;
		}
		public Collection<String> getAllClassNames() throws IOException
		{
			InputStream is = jar.getInputStream();
			Set<String> results = new HashSet<String>();
			try {
				JarInputStream ji = new JarInputStream(is);
				JarEntry je;
				while ((je = ji.getNextJarEntry()) != null) {
					if (je.getName().endsWith(".class")) {
						results.add(je.getName().replace("/",".").replaceFirst("\\.class$", ""));
					}
				}
				ji.close();
			}
			finally {
				is.close();
			}
			return results;
		}
		private static byte[] read(InputStream in) throws IOException {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[BUFFER_SIZE];
			for (int readBytes = in.read(buf); readBytes != -1; readBytes = in.read(buf)) {
				out.write(buf, 0, readBytes);
			}
			return out.toByteArray();
		}
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
	public static class EntitySerializer {
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
		
		public Object serialize(Object o) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException
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
				Collection<Object> col = newCollection(o);
				for (Object co:(Collection)o) {
					col.add(serialize(co));
				}
				return col;
			}
			
			if (o.getClass().isArray()) {
				Object[] src = (Object[])o;
				Object[] dst = newArray(o, src.length);
				for (int i = 0; i < dst.length; i++) {
					dst[i] = serialize(src[i]);
				}
				return dst;
			}
			
			//Map<String,Object> map = newMap(o);
			Object clone = newObject(o);
			
			Field[] fields = o.getClass().getFields();
			for (Field f:fields) {
				int mod = f.getModifiers();
				if (Modifier.isPublic(mod) && !Modifier.isStatic(mod)) {
					Object src = serialize(f.get(o));
					logger.info("clone=" + clone + ", f=" + f.getName() + ", src=" + src);
					f.set(clone, src );
				}
			}
			
			PropertyDescriptor[] props = BeanUtils.getPropertyDescriptors(o.getClass());
			for (PropertyDescriptor pd:props) {
				Method msrc = pd.getReadMethod();
				Method mdst = pd.getWriteMethod();
				if (msrc == null || mdst == null) continue;
				Object value;
				try {
					value = msrc.invoke(o);
					mdst.invoke(clone, serialize(o));
				} catch (IllegalArgumentException e) {
					continue;
				} catch (IllegalAccessException e) {
					continue;
				}
			}

			return clone;
		}

	}

}
