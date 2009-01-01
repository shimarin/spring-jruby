package net.stbbs.spring.jruby.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import javax.persistence.Table;
import javax.sql.DataSource;

import net.stbbs.spring.jruby.SpringIntegratedJRubyRuntime;

import org.hibernate.MappingException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.cfg.Settings;
import org.hibernate.impl.SessionFactoryImpl;
import org.hibernate.tool.hbm2ddl.SchemaUpdate;
import org.jruby.RubyArray;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

public class HibernateSupport {

	protected DataSource getDataSource(SpringIntegratedJRubyRuntime ruby, IRubyObject self)
	{
		return ruby.getComponent(self, "dataSource");
	}

	protected SessionFactory getSessionFactory(SpringIntegratedJRubyRuntime ruby, IRubyObject self)
	{
		return ruby.getComponent(self, "sessionFactory");
	}
	
	@ModuleMethod(arity=ModuleMethod.ARITY_NO_ARGUMENTS)
	protected Session getCurrentSession(SpringIntegratedJRubyRuntime ruby,IRubyObject self)
	{
		return getSessionFactory(ruby, self).getCurrentSession();
	}
	
	public IRubyObject currentSession(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) {
		return ruby.toRuby(getCurrentSession(ruby, self));
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
		newModule.definePrivateMethod("get", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				IRubyObject sf = self.callMethod(getCurrentContext(), "sessionFactory");
				Session session = ((SessionFactory)toJava(sf)).getCurrentSession();
				Object result;
				Object entity = toJava(args[0]);
				Serializable key = (Serializable)toJava(args[1]);
				LockMode lm = args.length > 2? (args[2].isTrue()? LockMode.UPGRADE : LockMode.NONE) : LockMode.NONE;
				if (entity instanceof Class) {
					result = session.get((Class)entity, key, lm);
				} else {
					result = session.get(entity.toString(), key, lm);
				}
				return toRuby(result);
			}
			public Arity getArity() {
				return Arity.TWO_REQUIRED;
			}
		});
		newModule.definePrivateMethod("save", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				Session session = getCurrentSession(self);
				return toRuby(session.save(toJava(args[0])));
			}
			public Arity getArity() {
				return Arity.ONE_ARGUMENT;
			}
		});
		newModule.definePrivateMethod("evict", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				getCurrentSession(self).evict(toJava(args[0]));
				return getNil();
			}
			public Arity getArity() {
				return Arity.ONE_ARGUMENT;
			}
		});
		newModule.definePrivateMethod("delete", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				getCurrentSession(self).delete(toJava(args[0]));
				return getNil();
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

		// schemaUpdateメソッド
		newModule.definePrivateMethod("schemaUpdate", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				return schemaUpdate(self, args, block);
			}
			public Arity getArity() {
				return Arity.ONE_REQUIRED;
			}
		});
	}
*/
	protected Collection<Class> anArgToClasses(SpringIntegratedJRubyRuntime ruby, IRubyObject arg)
	{
		Object jo = ruby.toJava(arg);
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
			throw ruby.newArgumentError("Class '" + className + "' couldn't load.");
		} catch (ClassNotFoundException e) {
			throw ruby.newArgumentError("Class '" + className + "' couldn't load.");
		}
		return classes;
	}

	@ModuleMethod(arity=ModuleMethod.ARITY_ONE_REQUIRED)
	public IRubyObject schemaUpdate(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) {
		// 引数が足りない場合エラー
		if (args.length < 1) {
			throw self.getRuntime().newArgumentError("Method requires at least one argument.");
		}
		
		Collection<Class> classes = this.anArgToClasses(ruby, args[0]);
	
		AnnotationConfiguration ac = new AnnotationConfiguration();
		Settings settings = ((SessionFactoryImpl)getSessionFactory(ruby, self)).getSettings();
		for (Class c:classes) {
			ac.addAnnotatedClass(c);
		}
		SchemaUpdate su = new SchemaUpdate(ac, settings);
		su.execute(true, true);

		return ruby.getNil();
	}

	@ModuleMethod(arity=ModuleMethod.ARITY_ONE_REQUIRED)
	public IRubyObject schemaReplace(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) {
		// 引数が足りない場合エラー
		if (args.length < 1) {
			throw self.getRuntime().newArgumentError("Method requires at least one argument.");
		}
		
		Collection<Class> classes = this.anArgToClasses(ruby, args[0]);

		AnnotationConfiguration ac = new AnnotationConfiguration();
		Settings settings = ((SessionFactoryImpl)getSessionFactory(ruby, self)).getSettings();
		JdbcTemplate jt = new JdbcTemplate(getDataSource(ruby, self));
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

		return ruby.getNil();
	}
}
