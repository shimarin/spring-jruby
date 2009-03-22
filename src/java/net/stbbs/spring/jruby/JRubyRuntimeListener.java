package net.stbbs.spring.jruby;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;
import net.stbbs.spring.jruby.modules.ApplicationContextSupport;
import net.stbbs.spring.jruby.modules.ApplicationContextSupport.ProxyClassCallback;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyKernel;
import org.jruby.RubyProc;
import org.jruby.anno.JRubyMethod;
import org.jruby.exceptions.RaiseException;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.context.ApplicationContext;
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
		final Ruby ruby = Util.initizlize();
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

		Util.registerDecorator(ruby, ScriptEvalProxyDecorator.class);

		applicationContext.callMethod(context, "setProxyClassCallback", JavaEmbedUtils.javaToRuby(ruby, 
				new WebApplicationProxyClassCallback(ruby, wac, classScript)));
		
		return applicationContext; 
	}
	
	public static IRubyObject getApplicationContextObject(ServletContext context)
	{
		return (RubyClass)context.getAttribute(ApplicationContextSupport.APPLICATIONCONTEXT_OBJECT_NAME);
	}

	public static class WebApplicationProxyClassCallback implements ProxyClassCallback {

		private Ruby runtime;
		private WebApplicationContext wac;
		private String initScript;
		private long initScriptTime = 0;

		public WebApplicationProxyClassCallback(Ruby runtime, WebApplicationContext wac, String initScript)
		{
			this.runtime = runtime;
			this.wac = wac;
			this.initScript = initScript;
		}
		
		public IRubyObject getScriptedBeanProxy(IRubyObject self, String beanName) {
			Set paths = wac.getServletContext().getResourcePaths("/WEB-INF/spring-jruby/" + beanName);
			if (paths != null && !paths.isEmpty()) {
				return JavaEmbedUtils.javaToRuby(runtime, new ScriptEvalProxy(self, wac, beanName));
			}
			
			return null;
		}

		public boolean isRefreshNeeded() throws IOException {
			Resource r = wac.getResource(initScript);
			if (r == null || !r.exists()) return false;
			// else
			long lastModified = r.lastModified();
			if (initScriptTime >= lastModified) return false;
			// else
			initScriptTime = lastModified;
			return true;
		}

		public void onDefined(RubyClass newProxyClass) {
			Resource r = wac.getResource(initScript);
			if (r == null || !r.exists()) return;
			
			Ruby runtime = newProxyClass.getRuntime();
			ThreadContext context = runtime.getCurrentContext();
			IRubyObject script = JavaEmbedUtils.javaToRuby(runtime, r).callMethod(context, "read");
			newProxyClass.callMethod(context, "class_eval", script);
			logger.info("リクエスト処理クラス初期化スクリプト " + this.initScript + " をロードしました");
		}
		
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
		public String getBeanName()
		{
			return beanName;
		}
		public ApplicationContext getApplicationContext()
		{
			return applicationContext;
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
			ThreadContext context = runtime.getCurrentContext();
			if (args.length < 1) {
				runtime.newArgumentError(0, 1);
			}
			String methodName = args[0].asString().getUnicodeValue();
			final IRubyObject[] passingArgs = new IRubyObject[args.length - 1];
			for (int i = 1; i < args.length; i++) passingArgs[i - 1] = args[i];

			String scriptName = "WEB-INF/spring-jruby/" + sep.getBeanName() + "/" + methodName + ".rb";
			Resource resource = sep.getApplicationContext().getResource(scriptName);
			if (resource == null || !resource.exists()) {
				// もし該当するスクリプトがなければ Java Beanのほうを呼び出す
				IRubyObject bean = JavaEmbedUtils.javaToRuby(runtime, sep.getApplicationContext()).callMethod(context, sep.getBeanName());
				return bean.callMethod(context, methodName, passingArgs);
			}
			
			// スクリプトを読み出す
			IRubyObject script = JavaEmbedUtils.javaToRuby(runtime, resource).callMethod(context, "read");
			IRubyObject ro = ApplicationContextSupport.scopedInstanceEval(sep.getProxyObject(), script);
			if (!(ro instanceof RubyProc)) return ro; // もし結果が Procオブジェクトでない場合はそれをそのまま返す
			// else Procオブジェクトを引数付きでコールした結果を返す
			final RubyProc proc = (RubyProc)ro;
			return proc.call(passingArgs);
		}

	}

}
