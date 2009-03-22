package net.stbbs.spring.jruby.modules;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyString;
import org.jruby.RubySymbol;
import org.jruby.RubyThread;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.parser.LocalStaticScope;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;
import org.springframework.context.ApplicationContext;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;

public class ApplicationContextSupport {
	public static final String APPLICATIONCONTEXT_OBJECT_NAME = "__JRubyApplicationContext";
	public static final String BINDING_TLD_NAME = "applicationContextBinding";

	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, ApplicationContextDecorator.class);
		Util.registerDecorator(runtime, ResourceDecorator.class);
	}

	@JRubyMethod
	public static IRubyObject applicationContext(IRubyObject self, IRubyObject[] args, Block block)
	{
		return self.getRuntime().getGlobalVariables().get(APPLICATIONCONTEXT_OBJECT_NAME);
	}

	public static IRubyObject scopedInstanceEval(IRubyObject self, IRubyObject script)
	{
		Ruby runtime = self.getRuntime();
		ThreadContext context = runtime.getCurrentContext();
		RubyThread thread = context.getThread();
		DynamicScope evalScope = new DynamicScope(new LocalStaticScope(null)); //context.getCurrentScope().cloneScope();
		context.pushScope(evalScope);
		IRubyObject binding = self.callMethod(context, "binding");
		thread.aset(RubySymbol.newSymbol(runtime, BINDING_TLD_NAME), binding);
		try {
			return self.callMethod(context, "instance_eval", script);
		} 
		finally {
			context.popScope();
		}
	}
	
	public static IRubyObject scopedInstanceEval(IRubyObject self, String script)
	{
		Ruby runtime = self.getRuntime();
		return scopedInstanceEval(self, RubyString.newUnicodeString(runtime, script));
	}

	public static IRubyObject evalWithApplicationContextBinding(IRubyObject self, String expr)
	{
		Ruby runtime = self.getRuntime();
		ThreadContext context = runtime.getCurrentContext();
		RubyThread thread = context.getThread();
		IRubyObject binding = thread.aref(RubySymbol.newSymbol(runtime, BINDING_TLD_NAME));
		IRubyObject[] args = new IRubyObject[binding == null? 1 : 2];
		args[0] = RubyString.newUnicodeString(runtime, expr);
		if (args.length > 1) args[1] = binding;
		return self.callMethod(context, "eval", args);

	}
	
	// for convinience
	public static <T> T getBean(IRubyObject self, String name)
	{
		Ruby runtime = self.getRuntime();
		IRubyObject c = evalWithApplicationContextBinding(self, name);
		Object javaObj = JavaEmbedUtils.rubyToJava(runtime, c, null);
		return (T)javaObj;
	}

	@Decorator(org.springframework.context.ApplicationContext.class)
	public static class ApplicationContextDecorator {
		private Ruby runtime;
		private ApplicationContext applicationContext;
		private RubyClass proxyClass = null;
		private ProxyClassCallback proxyClassCallback = null;
		public static final String PROXY_CLASS_NAME = "ApplicationContextProxy";

		static Log logger = LogFactory.getLog(ApplicationContextDecorator.class);	

		public ApplicationContextDecorator(Ruby runtime, ApplicationContext applicationContext)
		{
			this.runtime = runtime;
			this.applicationContext = applicationContext;
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
		public void setProxyClassCallback(IRubyObject self, IRubyObject[] args, Block block)
		{
			proxyClassCallback = (ProxyClassCallback)JavaEmbedUtils.rubyToJava(self.getRuntime(), args[0], ProxyClassCallback.class);
		}
		
		@JRubyMethod
		public synchronized RubyClass getProxyClass(IRubyObject self, IRubyObject[] args, Block block) throws IOException
		{
			if (proxyClassCallback != null && proxyClass != null) {
				if (proxyClassCallback.isRefreshNeeded()) {
					self.getMetaClass().remove_const(RubySymbol.newSymbol(runtime, PROXY_CLASS_NAME));
					proxyClass = null;
				}
			}
			
			if (proxyClass != null) return proxyClass;

			// else
			proxyClass = self.getMetaClass().defineClassUnder(PROXY_CLASS_NAME, runtime.getObject(), runtime.getObject().getAllocator());

			proxyClass.defineMethod("method_missing", new Callback(){
				public Arity getArity() {
					return Arity.OPTIONAL;
				}

				// method_missing
				public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
					if (args.length < 1) return runtime.getNil();
					String beanName = args[0].asString().getUnicodeValue();

					if (proxyClassCallback != null) {
						IRubyObject scriptedBeanProxy = proxyClassCallback.getScriptedBeanProxy(self, beanName);
						if (scriptedBeanProxy != null) return scriptedBeanProxy;
					}
					// else
					IRubyObject rac = runtime.getModule("ApplicationContextSupport").callMethod(
							runtime.getCurrentContext(), "applicationContext");
					IRubyObject[] passingArgs = new IRubyObject[args.length - 1];
					for (int i = 1; i < args.length; i++) passingArgs[i - 1] = args[i];
					return rac.callMethod(runtime.getCurrentContext(), beanName, passingArgs, block);
				}
			});

			if (proxyClassCallback != null) proxyClassCallback.onDefined(proxyClass);

			return proxyClass;
		}

		@JRubyMethod
		public IRubyObject allocateProxyObject(IRubyObject self, IRubyObject[] args, Block block) throws IOException
		{
			return getProxyClass(self, args, block).allocate();
		}
	}

	public static interface ProxyClassCallback {
		public boolean isRefreshNeeded() throws IOException;
		public void onDefined(RubyClass newProxyClass);
		public IRubyObject getScriptedBeanProxy(IRubyObject self, String beanName);
	}

}
