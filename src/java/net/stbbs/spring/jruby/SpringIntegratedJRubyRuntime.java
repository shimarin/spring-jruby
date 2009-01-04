package net.stbbs.spring.jruby;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.stbbs.spring.jruby.modules.AbstractModule;
import net.stbbs.spring.jruby.modules.Module;
import net.stbbs.spring.jruby.modules.ModuleException;
import net.stbbs.spring.jruby.modules.ModuleMethod;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyKernel;
import org.jruby.RubyModule;
import org.jruby.RubyString;
import org.jruby.RubySymbol;
import org.jruby.exceptions.RaiseException;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;
import org.jruby.util.KCode;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.util.ClassUtils;

import com.ibm.icu.text.Normalizer;

public class SpringIntegratedJRubyRuntime {
	Ruby ruby;
	private ApplicationContext applicationContext;
	private Map<String, Object> modules = new HashMap<String,Object>();
	static Log logger = LogFactory.getLog(SpringIntegratedJRubyRuntime.class);	

	public static SpringIntegratedJRubyRuntime init(ApplicationContext applicationContext)
	{
		return new SpringIntegratedJRubyRuntime(applicationContext);
	}
	
	public Object getBean(String beanName)
	{
		return applicationContext.getBean(beanName);
	}
	
	public IRubyObject getNil()
	{
		return ruby.getNil();
	}
	
	public boolean loadRubyGems()
	{
		IRubyObject obj;
		try {
			obj = RubyKernel.require(ruby.getKernel(), this.newString("rubygems"), null);
		}
		catch (RaiseException ex) {
			return false;
		}
		return obj.isTrue();
	}
	
	public void terminate()
	{
		JavaEmbedUtils.terminate(ruby);
	}
	
	public IRubyObject evalScript(String script)
	{
		return ruby.evalScript(script);
	}
	
	public void defineModule(Object module, String moduleName)  throws ModuleException
	{
		RubyModule newModule = ruby.defineModule(moduleName);
		synchronized (modules) {
			modules.put(moduleName, module);
		}
		registerModuleMethods(newModule, module);
		if (module instanceof AbstractModule) {
			((AbstractModule)module).onRegister(this, newModule);	// モジュール側に登録時処理の機会を与える
		}
	}
	
	public Object getModule(String name)
	{
		synchronized (modules) {
			return modules.get(name);
		}
	}
	
	public void defineModule(Class clazz, String moduleName) throws ModuleException
	{
		Module m = (Module) clazz.getAnnotation(Module.class);
		if (m != null && !m.value().equals("")) {
			// アノテーションからモジュール名を取る
			moduleName = m.value();
		}
		if (moduleName == null) {
			// もしmoduleNameがnullならクラス名から取る
			moduleName = ClassUtils.getShortName(clazz);
		}
		try {
			defineModule(clazz.newInstance(), moduleName);
		} catch (InstantiationException e) {
			throw new ModuleException("Module couldn't instantiate", e);
		} catch (IllegalAccessException e) {
			throw new ModuleException("Module couldn't instantiate", e);
		}
	}
	
	public void defineModule(String className, String moduleName) throws ModuleException
	{
		try {
			defineModule(Class.forName(className), moduleName);
		} catch (ClassNotFoundException e) {
			throw new ModuleException("Class '" + className + "' not found.", e);
		}
	}
	
	public void defineModule(String className) throws ModuleException
	{
		defineModule(className, null);
	}
	
	public RubyClass defineApplicationContextClass(String className)
	{
		RubyClass clazz = ruby.defineClass(className, ruby.getObject(), ruby.getObject().getAllocator());
		clazz.defineMethod("method_missing", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				if (args == null || args.length < 1) return null;
				String beanName = args[0].asSymbol();
				if (!applicationContext.containsBean(beanName)) {
					throw self.getRuntime().newNameError("Bean not found: " + beanName, beanName);
				}
				return toRuby(applicationContext.getBean(beanName));
			}

			public Arity getArity() {
				return Arity.noArguments();
			}
		});
		clazz.definePrivateMethod("applicationContext", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] arg1, Block arg2) {
				return JavaEmbedUtils.javaToRuby(ruby, applicationContext);
			}
			public Arity getArity() {
				return Arity.NO_ARGUMENTS;
			}
		});
		clazz.definePrivateMethod("getResource", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				// 引数が０の場合エラー
				if (args.length < 1) {
					throw self.getRuntime().newArgumentError("Method requires at least one argument.");
				}
				return toRuby(applicationContext.getResource(args[0].asString().getUnicodeValue()));
			}
			public Arity getArity() {
				return Arity.ONE_ARGUMENT;
			}
		});
		clazz.definePrivateMethod("getResourceAsInputStream", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				// 引数が０の場合エラー
				if (args.length < 1) {
					throw self.getRuntime().newArgumentError("Method requires at least one argument.");
				}
				Resource r = applicationContext.getResource(args[0].asString().getUnicodeValue());
				InputStream is = null;
				try {
					is = r.getInputStream();
				} catch (IOException e) {
					ruby.newIOError(e.getMessage());
				}
				try {
					if (block.isGiven()) {
						block.call(
							ruby.getCurrentContext(), 
							new IRubyObject[] {toRuby(is)});
					}
				}
				finally {
					if (is != null) {
						try {
							is.close();
						} catch (IOException e) {
							ruby.newIOError(e.getMessage());
						}
					}
				}

				return toRuby(r);
			}
			public Arity getArity() {
				return Arity.ONE_ARGUMENT;
			}
		});
		clazz.definePrivateMethod("download", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				// 引数が０の場合エラー
				if (args.length < 1) {
					throw self.getRuntime().newArgumentError("Method requires at least one argument.");
				}
				String contentType = "text/plain";
				if (args.length > 1) {
					contentType = args[1].asString().getUnicodeValue();
				}
				// 今はとりあえず第1引数をResourceとして決め打ちで扱う
				Resource r = (Resource)toJava(args[0]);
				InputStream is = null;
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				byte[] buf = new byte[1024];
				try {
					is = r.getInputStream();
					int n;
					while ((n = is.read(buf)) >= 0) {
						baos.write(buf, 0, n);
					}
				} catch (IOException e) {
					ruby.newIOError(e.getMessage());
				}
				finally {
					if (is != null) {
						try {
							is.close();
						} catch (IOException e) {
							ruby.newIOError(e.getMessage());
						}
					}
				}
				return toRuby(new DownloadContent(contentType, baos.toByteArray()));
			}
			public Arity getArity() {
				return Arity.ONE_REQUIRED;
			}
		});
		clazz.definePrivateMethod("p", new Callback(){
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				for (IRubyObject obj:args) {
					if (obj instanceof RubyString) {
						logger.info('"' + ((RubyString)obj).getUnicodeValue() + '"');
					} else {
						logger.info(obj.inspect().asString().getUnicodeValue());
					}
				}
				return ruby.getNil();
			}
			public Arity getArity() {
				return Arity.ONE_REQUIRED;
			}
		});
		return clazz;
	}
	
	public void setRawStringInspection()
	{
		ruby.getString().defineMethod("inspect", new Callback(){
			public IRubyObject execute(IRubyObject self, IRubyObject[] args,Block block) {
				String str = self.asString().getUnicodeValue(); 
				return newUnicodeString('"' + str + '"');
			}

			public Arity getArity() {
				return Arity.noArguments();
			}
		});
	}
	
	public void setNFKCNormalization()
	{
		ruby.getString().defineMethod("utf8nfkc", new Callback(){
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				String str = ((RubyString)self).getUnicodeValue(); 
				return newUnicodeString(Normalizer.normalize(str, Normalizer.NFKC));
			}

			public Arity getArity() {
				return Arity.noArguments();
			}
		});
	}
	
	public void setGetBytes()
	{
		ruby.getString().defineMethod("getBytes", new Callback(){
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				String str = ((RubyString)self).getUnicodeValue(); 
				try {
					return toRuby(str.getBytes("UTF-8"));
				} catch (UnsupportedEncodingException e) {
					throw ruby.newInvalidEncoding(e.getMessage());
				}
			}

			public Arity getArity() {
				return Arity.noArguments();
			}
		});
	}

	protected SpringIntegratedJRubyRuntime(final ApplicationContext applicationContext)
	{
		this.applicationContext = applicationContext;

		ruby = JavaEmbedUtils.initialize(new ArrayList<String> ());
		ruby.setKCode(KCode.UTF8);
		//ruby.setSafeLevel(3);

	}
	
	public void defineVariousModules() throws ModuleException
	{
		defineModule("net.stbbs.spring.jruby.modules.TransactionSupport");
		defineModule("net.stbbs.spring.jruby.modules.SQLMonitor");
		defineModule("net.stbbs.spring.jruby.modules.SQLSupport");
		
		defineModule("net.stbbs.spring.jruby.modules.GraphicsSupport");
		defineModule("net.stbbs.spring.jruby.modules.MIDISupport");
		defineModule("net.stbbs.spring.jruby.modules.MVCSupport");
		ClassLoader cl = getClass().getClassLoader();
		
		if (ClassUtils.isPresent("net.stbbs.spring.dbunit.TransactionAwareDataSourceDatabaseTester", cl)) {
			defineModule("net.stbbs.spring.jruby.modules.DbUnitSupport");
		}
		
		if (ClassUtils.isPresent("org.apache.poi.hssf.usermodel.HSSFWorkbook", cl)) {
			defineModule("net.stbbs.spring.jruby.modules.POISupport");
		}
		
		if (ClassUtils.isPresent("javax.mail.internet.MimeMessage", cl)) {
			defineModule("net.stbbs.spring.jruby.modules.MailSupport");
		}
		if (ClassUtils.isPresent("org.hibernate.SessionFactory", cl)) {
			defineModule("net.stbbs.spring.jruby.modules.HibernateSupport");
		}
		if (ClassUtils.isPresent("javax.persistence.EntityManagerFactory", cl)) {
			defineModule("net.stbbs.spring.jruby.modules.JPASupport");
		}
		if (ClassUtils.isPresent("org.apache.velocity.VelocityContext", cl)) {
			defineModule("net.stbbs.spring.jruby.modules.VelocitySupport");
		}
	}
	
	public RubyClass getClass(String className)
	{
		return ruby.getClass(className);
	}
	
	public ThreadContext getCurrentContext()
	{
		return ruby.getCurrentContext();
	}
	
	public RubyString newUnicodeString(String str)
	{
		return RubyString.newUnicodeString(ruby, str);
	}

	public RubyString newString(String str)
	{
		return newUnicodeString(str);
	}
	
	public RubySymbol newSymbol(String str)
	{
		return ruby.newSymbol(str);
	}
	
	public RaiseException newArgumentError(String message)
	{
		return ruby.newArgumentError(message);
	}
	
	public RaiseException newIOError(String message)
	{
		return ruby.newIOError(message);
	}
	
	public IRubyObject allocate(String className)
	{
		return ruby.getClass(className).allocate();
	}
	
	public IRubyObject toRuby(Object obj)
	{
		return JavaEmbedUtils.javaToRuby(ruby, obj);
	}
	
	public Object toJava(IRubyObject obj)
	{
		return JavaEmbedUtils.rubyToJava(ruby, obj, null);
	}

	public IRubyObject evalInClass(RubyClass clazz, String expr, String filename)
	{
		return clazz.evalUnder(clazz, this.newString(expr), this.newString(filename), ruby.newFixnum(0));
	}
	
	public IRubyObject evalInClass(String className, String expr, String filename)
	{
		return evalInClass(ruby.getClass(className), expr, filename);
	}
	
	public IRubyObject evalInClass(RubyClass clazz, String resourceName, boolean errorIfNotFound) throws IOException
	{
		StringBuffer buf = new StringBuffer();
		Resource r = applicationContext.getResource(resourceName);
		if (!errorIfNotFound && (r == null || !r.exists())) return null;

		// else
		InputStream in = r.getInputStream();
		InputStreamReader insr = new InputStreamReader(in, "UTF-8");
		BufferedReader br = new BufferedReader(insr);
		String line = null;
		while ((line = br.readLine()) != null) {
			buf.append(line);
			buf.append('\n');
		}
		in.close();
		
		return evalInClass(clazz, buf.toString(), resourceName);
	}
	
	public RubyClass getString()
	{
		return ruby.getString();
	}

	public Ruby getRuntime() {
		return ruby;
	}

	protected void registerModuleMethod(RubyModule newModule, final Object module, final Method method)
	{
		try {
			ModuleMethod mi = method.getAnnotation(ModuleMethod.class);
			String methodName = method.getName();
			if (!mi.value().equals("")) methodName = mi.value(); 
			final Arity arity = Arity.createArity(mi.arity());
			final SpringIntegratedJRubyRuntime me = this;
			newModule.defineMethod(methodName, new Callback(){
				public IRubyObject execute(IRubyObject arg0, IRubyObject[] arg1, Block arg2) {
					try {
						Object rst = method.invoke(module, me, arg0, arg1, arg2);
						if (rst instanceof IRubyObject) return (IRubyObject)rst;
						return toRuby(rst);
					} catch (IllegalArgumentException e) {
						throw ruby.newNoMethodError(
								"IllegalArgumentException", method.getName(), 
								ruby.newArray(arg1));
					} catch (IllegalAccessException e) {
						throw ruby.newNoMethodError(
								"IllegalAccessException", method.getName(), 
								ruby.newArray(arg1));
					} catch (InvocationTargetException e) {
						Throwable te = e.getTargetException();
						if (te instanceof RuntimeException) throw (RuntimeException)te;
						throw ruby.newRuntimeError(te.getMessage());
					}
				}

				public Arity getArity() {
					return arity;
				}

			});
		} catch (SecurityException e) {
			throw ruby.newRuntimeError("SecurityException while registering module method.");
		}
	}

	public void registerModuleMethods(RubyModule newModule, Object module) {
		Method[] methods = module.getClass().getMethods();
		for (Method m:methods) {
			ModuleMethod mm = m.getAnnotation(ModuleMethod.class);
			if (mm != null) {
				this.registerModuleMethod(newModule, module, m);
			}
		}
	}

	public <T> T getComponent(IRubyObject self, String name)
	{
		T component;
		IRubyObject c = self.callMethod(getCurrentContext(), name);
		Object javaObj = toJava(c);
		if (javaObj instanceof IRubyObject) {
			component = (T)getBean(c.asString().getUnicodeValue());
		} else {
			component = (T)javaObj;
		}
		return component;
	}
	
	public Resource getResource(String resourceName)
	{
		return applicationContext.getResource(resourceName);
	}

}
