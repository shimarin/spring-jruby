package net.stbbs.spring.jruby.modules;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;

import javax.servlet.ServletContext;

import net.stbbs.jruby.Decorator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyProc;
import org.jruby.RubyString;
import org.jruby.RubySymbol;
import org.jruby.anno.JRubyMethod;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.parser.LocalStaticScope;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.web.context.WebApplicationContext;

@Decorator(org.springframework.context.ApplicationContext.class)
public class ApplicationContextDecorator {
	private Ruby runtime;
	private ApplicationContext applicationContext;
	private RubyClass proxyClass = null;
	private String initScript = "WEB-INF/instanceEvalServlet.rb";
	private long initScriptTime = 0;

	public static final String INIT_SCRIPT_PARAM_NAME = "init-script";
	public static final String PROXY_CLASS_NAME = "ApplicationContextProxy";

	static Log logger = LogFactory.getLog(ApplicationContextDecorator.class);	

	public ApplicationContextDecorator(Ruby runtime, ApplicationContext applicationContext)
	{
		this.runtime = runtime;
		this.applicationContext = applicationContext;
		String _initScript = ((WebApplicationContext)applicationContext).getServletContext().getInitParameter(INIT_SCRIPT_PARAM_NAME);
		if (_initScript != null) this.initScript = _initScript;
	}

	protected IRubyObject toRuby(Object obj)
	{
		return JavaEmbedUtils.javaToRuby(runtime, obj);
	}

	@JRubyMethod
	public Object method_missing(IRubyObject self, IRubyObject[] args, Block block) {
		if (args == null || args.length < 1) return null;
		String beanName = args[0].asSymbol();
		if (!applicationContext.containsBean(beanName)) {
			throw self.getRuntime().newNameError("Bean not found: " + beanName, beanName);
		}
		return applicationContext.getBean(beanName);
	}

	@JRubyMethod
	public void p(IRubyObject self, IRubyObject[] args, Block block) {
		p(args);
	}
	
	public static void p(IRubyObject[] args)
	{
		for (IRubyObject obj:args) {
			if (obj instanceof RubyString) {
				logger.info('"' + ((RubyString)obj).getUnicodeValue() + '"');
			} else {
				logger.info(obj.inspect().asString().getUnicodeValue());
			}
		}
	}
	
	@JRubyMethod
	public ServletContext servletContext(IRubyObject self, IRubyObject[] args, Block block) 
	{
		return ((WebApplicationContext)applicationContext).getServletContext();
	}
	
	@JRubyMethod
	public synchronized RubyClass getProxyClass(IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		Resource r = applicationContext.getResource(initScript);
		if (r != null && r.exists()) {
			File f = r.getFile();
			if (f != null) {
				long lastModified = f.lastModified();
				if (initScriptTime < lastModified) {
					initScriptTime = lastModified;
					if (proxyClass != null) {
						self.getMetaClass().remove_const(RubySymbol.newSymbol(runtime, PROXY_CLASS_NAME));
						proxyClass = null;
					}
				}
			}
		}
		
		if (proxyClass != null) return proxyClass;
		
		// else
		proxyClass = self.getMetaClass().defineClassUnder(PROXY_CLASS_NAME, runtime.getObject(), runtime.getObject().getAllocator());
		final ServletContext sc = ((WebApplicationContext)applicationContext).getServletContext();

		proxyClass.defineModuleFunction("applicationContext", new Callback(){
			public Arity getArity() {
				return Arity.NO_ARGUMENTS;
			}
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				return runtime.getModule("ApplicationContextSupport").callMethod(runtime.getCurrentContext(), "applicationContext");
			}			
		});

		proxyClass.defineMethod("method_missing", new Callback(){
			public Arity getArity() {
				return Arity.OPTIONAL;
			}

			// method_missing
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				if (args.length < 1) return runtime.getNil();

				String beanName = args[0].asSymbol();
				Set paths = sc.getResourcePaths("/WEB-INF/spring-jruby/" + beanName);
				if (paths != null && !paths.isEmpty()) {
					return JavaEmbedUtils.javaToRuby(runtime, new ScriptEvalProxy(self, applicationContext, beanName));
				}
				
				IRubyObject rac = self.callMethod(runtime.getCurrentContext(), "applicationContext");
				IRubyObject[] passingArgs = new IRubyObject[args.length - 1];
				for (int i = 1; i < args.length; i++) passingArgs[i - 1] = args[i];
				return rac.callMethod(runtime.getCurrentContext(), beanName, passingArgs, block);
			}
		});
		if (r != null && r.exists()) {
			evalInClass(proxyClass, r);
		}

		return proxyClass;
	}

	@JRubyMethod
	public IRubyObject allocateProxyObject(IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		return getProxyClass(self, args, block).allocate();
	}

	public static IRubyObject evalInClass(RubyModule clazz, String expr, String filename)
	{
		Ruby ruby = clazz.getRuntime();
		return clazz.evalUnder(clazz, 
				RubyString.newUnicodeString(ruby, expr), 
				RubyString.newUnicodeString(ruby, filename),
				ruby.newFixnum(0));
	}

	protected IRubyObject evalInClass(RubyClass clazz, Resource resource) throws IOException
	{
		StringBuffer buf = new StringBuffer();
		
		// else
		InputStream in = resource.getInputStream();
		InputStreamReader insr = new InputStreamReader(in, "UTF-8");
		BufferedReader br = new BufferedReader(insr);
		String line = null;
		while ((line = br.readLine()) != null) {
			buf.append(line);
			buf.append('\n');
		}
		in.close();
		
		return evalInClass(clazz, buf.toString(), resource.getFilename());
	}
	
	// for convinience
	public static <T> T getBean(IRubyObject self, String name)
	{
		Ruby runtime = self.getRuntime();
		IRubyObject c = self.callMethod(runtime.getCurrentContext(), name);
		Object javaObj = JavaEmbedUtils.rubyToJava(runtime, c, null);
		return (T)javaObj;
	}

	public static class ScriptEvalProxy {
		private IRubyObject proxyObject;
		private ApplicationContext applicationContext;
		private String beanName;
		
		public ScriptEvalProxy(IRubyObject proxyObject, ApplicationContext applicationContext, String beanName)
		{
			this.proxyObject = proxyObject;
			this.applicationContext = applicationContext;
			this.beanName = beanName;
		}
		
		public IRubyObject getProxyObject()
		{
			return proxyObject;
		}
		
		public String getScriptName(String methodName)
		{
			return "WEB-INF/spring-jruby/" + beanName + "/" + methodName + ".rb";
		}
		
		public String loadResource(String scriptName) throws IOException
		{
			Resource script = applicationContext.getResource(scriptName);
			if (!script.exists()) {
				return null;
			}
			StringBuffer buf = new StringBuffer();
			InputStream in = script.getInputStream();
			try {
				InputStreamReader insr = new InputStreamReader(in, "UTF-8");
				BufferedReader br = new BufferedReader(insr);
				String line = null;
				while ((line = br.readLine()) != null) {
					buf.append(line);
					buf.append('\n');
				}
			}
			finally {
				in.close();
			}
			return buf.toString();
		}
	}
	@Decorator(ScriptEvalProxy.class)
	public static class ScriptEvalProxyDecorator {
		private ScriptEvalProxy sep;
		public ScriptEvalProxyDecorator(ScriptEvalProxy sep)
		{
			this.sep = sep;
		}
		
		@JRubyMethod
		public IRubyObject method_missing(IRubyObject self, IRubyObject[] args, Block block) throws IOException
		{
			Ruby runtime = self.getRuntime();
			if (args.length < 1) {
				runtime.newArgumentError(0, 1);
			}
			IRubyObject methodName = args[0];
			String scriptName = sep.getScriptName(methodName.asString().getUnicodeValue());
			String script = sep.loadResource(scriptName);
			if (script == null) {
				throw runtime.newNameError("Script not found:" + scriptName , scriptName);
			}
			IRubyObject rac = sep.getProxyObject();
			IRubyObject ro = ApplicationContextSupport.scopedInstanceEval(rac, script);
			if (!(ro instanceof RubyProc)) return ro;
			// else
			final RubyProc proc = (RubyProc)ro;
			final IRubyObject[] passingArgs = new IRubyObject[args.length - 1];
			for (int i = 1; i < args.length; i++) passingArgs[i - 1] = args[i];
			return proc.call(passingArgs);
		}

	}

}
