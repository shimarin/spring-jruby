package net.stbbs.spring.jruby;

import java.io.IOException;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;

import net.stbbs.jruby.Util;
import net.stbbs.spring.jruby.modules.ApplicationContextSupport;
import net.stbbs.spring.jruby.modules.ApplicationContextSupport.ProxyClassCallback;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyKernel;
import org.jruby.RubyProc;
import org.jruby.exceptions.RaiseException;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;
import org.springframework.core.io.Resource;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

public class JRubyRuntimeListener implements ServletContextListener {

	public static final String DEFAULT_RUNTIME_SCRIPT_NAME = "WEB-INF/runtime.rb";
	public static final String DEFAULT_PROXYCLASS_SCRIPT_NAME = "WEB-INF/instanceEvalServlet.rb";
	public static final String RUNTIME_SCRIPT_PARAM_NAME = "spring-jruby-runtime-script";
	public static final String PROXYCLASS_SCRIPT_PARAM_NAME = "spring-jruby-proxyclass-script";
	
	protected static Log logger = LogFactory.getLog(JRubyRuntimeListener.class);
	
	public void contextDestroyed(ServletContextEvent event)
	{
		IRubyObject applicationContext = getApplicationContextObject(event.getServletContext());
		event.getServletContext().removeAttribute(ApplicationContextSupport.APPLICATIONCONTEXT_OBJECT_NAME);
		JavaEmbedUtils.terminate(applicationContext.getRuntime());
	}

	public void contextInitialized(ServletContextEvent event)
	{
		try {
			IRubyObject applicationContext = initializeRuntime(event.getServletContext());
			event.getServletContext().setAttribute(ApplicationContextSupport.APPLICATIONCONTEXT_OBJECT_NAME, applicationContext);
		}
		catch (ServletException ex) {
			throw new IllegalStateException(ex); 
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex); 
		}
	}

	protected static boolean loadRubyGems(Ruby ruby)
	{
		IRubyObject obj;
		try {
			obj = RubyKernel.require(ruby.getKernel(), ruby.newString("rubygems"), null);
		}
		catch (RaiseException ex) {
			return false;
		}
		return obj.isTrue();
	}

	public static IRubyObject initializeRuntime(ServletContext servletContext) throws ServletException, IOException
	{
		final Ruby ruby = Util.initialize();
		loadRubyGems(ruby);

		WebApplicationContext wac;
		try { wac = WebApplicationContextUtils.getRequiredWebApplicationContext(servletContext);}
		catch (IllegalStateException ex) {
			throw new ServletException("SpringのWebアプリケーションコンテキストが取得できませんでした。web.xmlに ContextLoaderListenerは登録されているでしょうか？:" + ex.getMessage() );
		}
		IRubyObject applicationContext = JavaEmbedUtils.javaToRuby(ruby, wac); 
		ruby.getGlobalVariables().set(ApplicationContextSupport.APPLICATIONCONTEXT_OBJECT_NAME, applicationContext);
		Util.registerModule(ruby, ApplicationContextSupport.class);

		ThreadContext context = ruby.getCurrentContext();
		
		String runtimeScript = servletContext.getInitParameter(RUNTIME_SCRIPT_PARAM_NAME);
		if (runtimeScript == null) runtimeScript = DEFAULT_RUNTIME_SCRIPT_NAME;
		String classScript = servletContext.getInitParameter(PROXYCLASS_SCRIPT_PARAM_NAME);
		if (classScript == null) classScript = DEFAULT_PROXYCLASS_SCRIPT_NAME;
		
		Resource resource = wac.getResource(runtimeScript);
		if (resource == null || !resource.exists()) {
			runtimeScript = "classpath:net/stbbs/spring/jruby/web_runtime.rb";
			resource = wac.getResource(runtimeScript);
		}
		if (resource != null && resource.exists()) {
			IRubyObject script = JavaEmbedUtils.javaToRuby(ruby, resource).callMethod(context, "read");
			ruby.getKernel().callMethod(context, "eval", script);
			logger.info("Rubyランタイム初期化スクリプト " + resource.getFilename() + " をロードしました");
		}

		applicationContext.callMethod(context, "setProxyClassCallback", JavaEmbedUtils.javaToRuby(ruby, 
				new WebApplicationProxyClassCallback(ruby, wac, classScript)));

		// webapplication_initメソッドが存在したらそれを実行する
		IRubyObject applicationContextProxy = ((RubyClass)applicationContext.callMethod(context, "getProxyClass")).allocate();
		if (applicationContextProxy.respondsTo("webapplication_init")) {
			applicationContextProxy.callMethod(context, "webapplication_init");
		}
		
		return applicationContext; 
	}
	
	public static IRubyObject getApplicationContextObject(ServletContext context)
	{
		return (IRubyObject)context.getAttribute(ApplicationContextSupport.APPLICATIONCONTEXT_OBJECT_NAME);
	}

	public static class WebApplicationProxyClassCallback implements ProxyClassCallback {

		private Ruby runtime;
		private WebApplicationContext applicationContext;
		private String initScript;
		private long initScriptTime = 0;
		private RubyClass scriptedBeanProxyClass;

		public WebApplicationProxyClassCallback(Ruby runtime, WebApplicationContext wac, String initScript)
		{
			this.runtime = runtime;
			this.applicationContext = wac;
			this.initScript = initScript;
			
			// RubyスクリプトをBeanにみせかけて実行するProxyクラスの定義
			this.scriptedBeanProxyClass = runtime.defineClass("ScriptedBeanProxy", runtime.getObject(), runtime.getObject().getAllocator());
			this.scriptedBeanProxyClass.defineMethod("method_missing", new Callback() {
				public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block)
				{
					Ruby runtime = self.getRuntime();
					ThreadContext context = runtime.getCurrentContext();
					if (args.length < 1) {
						runtime.newArgumentError(0, 1);
					}
					String methodName = args[0].asString().getUnicodeValue();
					final IRubyObject[] passingArgs = new IRubyObject[args.length - 1];
					for (int i = 1; i < args.length; i++) passingArgs[i - 1] = args[i];

					String beanName = self.getInstanceVariable("@beanName").asString().getUnicodeValue();
					String scriptName = "WEB-INF/spring-jruby/" + beanName + "/" + methodName + ".rb";
					Resource resource = applicationContext.getResource(scriptName);
					if (resource == null || !resource.exists()) {
						// もし該当するスクリプトがなければ Java Beanのほうを呼び出す
						if (applicationContext.containsBean(beanName)) {
							IRubyObject bean = JavaEmbedUtils.javaToRuby(runtime, applicationContext.getBean(beanName));
							return bean.callMethod(context, methodName, passingArgs);
						}
						// else
						throw runtime.newNoMethodError("No such method " + methodName + " in bean " + beanName, methodName, runtime.getNil());
					}
					
					// スクリプトを読み出す
					IRubyObject script = JavaEmbedUtils.javaToRuby(runtime, resource).callMethod(context, "read");
					IRubyObject proxyObject = self.getInstanceVariable("@proxyObject");
					IRubyObject ro = ApplicationContextSupport.scopedInstanceEval(proxyObject, script);
					if (!(ro instanceof RubyProc)) return ro; // もし結果が Procオブジェクトでない場合はそれをそのまま返す
					// else Procオブジェクトを引数付きでコールした結果を返す
					final RubyProc proc = (RubyProc)ro;
					return proc.call(passingArgs);
				}

				public Arity getArity() {
					return Arity.ONE_REQUIRED;
				}
				
			});
		}
		
		public IRubyObject getScriptedBeanProxy(IRubyObject self, String beanName) {
			Set paths = applicationContext.getServletContext().getResourcePaths("/WEB-INF/spring-jruby/" + beanName);
			if (paths != null && !paths.isEmpty()) {
				Ruby runtime = self.getRuntime();
				IRubyObject scriptedBeanProxy = runtime.getClass("ScriptedBeanProxy").allocate();
				scriptedBeanProxy.setInstanceVariable("@beanName", runtime.newString(beanName));
				scriptedBeanProxy.setInstanceVariable("@proxyObject", self);
				return scriptedBeanProxy;
			}
			
			return null;
		}

		public boolean isRefreshNeeded() throws IOException {
			Resource r = applicationContext.getResource(initScript);
			if (r == null || !r.exists()) return false;
			// else
			long lastModified = r.lastModified();
			if (initScriptTime >= lastModified) return false;
			// else
			initScriptTime = lastModified;
			return true;
		}

		public void onDefined(RubyClass newProxyClass) {
			Resource r = applicationContext.getResource(initScript);
			if (r == null || !r.exists()) {
				r = applicationContext.getResource("classpath:net/stbbs/spring/jruby/instanceEvalServlet.rb");
			}
			
			Ruby runtime = newProxyClass.getRuntime();
			ThreadContext context = runtime.getCurrentContext();
			newProxyClass.callMethod(context, "include_resource", JavaEmbedUtils.javaToRuby(runtime, r));
			logger.info("リクエスト処理クラス初期化スクリプト " + this.initScript + " をロードしました");
		}
		
	}

}
