package net.stbbs.spring.jruby;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;

import net.stbbs.jruby.Util;
import net.stbbs.spring.jruby.modules.ApplicationContextSupport;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyKernel;
import org.jruby.exceptions.RaiseException;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;
import org.jruby.util.KCode;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.web.context.support.WebApplicationContextUtils;

public class JRubyRuntimeListener implements ServletContextListener {

	public static final String DEFAULT_RUNTOME_SCRIPT_NAME = "WEB-INF/runtime.rb";
	public static final String DEFAULT_INIT_SCRIPT_NAME = "WEB-INF/instanceEvalServlet.rb";
	
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

		ApplicationContext wac;
		try { wac = WebApplicationContextUtils.getRequiredWebApplicationContext(servletContext);}
		catch (IllegalStateException ex) {
			throw new ServletException("SpringのWebアプリケーションコンテキストが取得できませんでした。web.xmlに ContextLoaderListenerは登録されているでしょうか？:" + ex.getMessage() );
		}
		IRubyObject applicationContext = JavaEmbedUtils.javaToRuby(ruby, wac); 
		ruby.getGlobalVariables().set(ApplicationContextSupport.APPLICATIONCONTEXT_OBJECT_NAME, applicationContext);
		
		loadRubyGems(ruby);

		Resource resource = wac.getResource(DEFAULT_RUNTOME_SCRIPT_NAME);
		if (!resource.exists()) {
			resource = wac.getResource("classpath:net/stbbs/spring/jruby/web_runtime.rb");
		}
		if (resource.exists()) {
			StringBuffer buf = new StringBuffer();
			InputStream in = resource.getInputStream();
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
			ruby.evalScript(buf.toString());
		}

		return applicationContext; 
	}
	
	public static IRubyObject getApplicationContextObject(ServletContext context)
	{
		return (RubyClass)context.getAttribute(ApplicationContextSupport.APPLICATIONCONTEXT_OBJECT_NAME);
	}

}
