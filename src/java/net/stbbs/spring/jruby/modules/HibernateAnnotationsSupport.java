package net.stbbs.spring.jruby.modules;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import javax.persistence.Entity;
import javax.persistence.Table;

import net.stbbs.jruby.Util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.MappingException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.cfg.Settings;
import org.hibernate.connection.ConnectionProvider;
import org.hibernate.impl.SessionFactoryImpl;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.tool.hbm2ddl.SchemaUpdate;
import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;

import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

public class HibernateAnnotationsSupport extends DataSourceSupport {
	static Log logger = LogFactory.getLog(HibernateAnnotationsSupport.class);	

	public HibernateAnnotationsSupport(Ruby runtime, IRubyObject self)
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
		return HibernateSupport.getSessionFactory(self);
	}
	
	static protected Session getCurrentSession(IRubyObject self)
	{
		return getSessionFactory(self).getCurrentSession();
	}
	
	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, SessionFactory.class, SessionFactoryDecorator.class);
	}
	
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
	
	public static class SessionFactoryDecorator {
		private SessionFactory sessionFactory;
		public SessionFactoryDecorator(SessionFactory sessionFactory)
		{
			this.sessionFactory = sessionFactory;
		}
		
		@JRubyMethod
		public void schemaUpdate(IRubyObject self, IRubyObject[] args, Block block)
		{
			Ruby runtime = self.getRuntime();
			Map md = sessionFactory.getAllClassMetadata();
			AnnotationConfiguration ac = new AnnotationConfiguration();
			for (Object e:md.values()) {
				if (!(e instanceof AbstractEntityPersister)) continue;
				AbstractEntityPersister persister = (AbstractEntityPersister)e;
				Class returnedClass = persister.getType().getReturnedClass();
				Entity t = (Entity)returnedClass.getAnnotation(Entity.class);
				if (t != null) {
					ac.addAnnotatedClass(returnedClass);
				}
			}

			Settings settings = ((SessionFactoryImpl)sessionFactory).getSettings();
			SchemaUpdate su = new SchemaUpdate(ac, settings);
			su.execute(true, true);
		}
	}

}
