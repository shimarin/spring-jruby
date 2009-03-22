package net.stbbs.spring.jruby.standalone;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JFrame;

import net.stbbs.spring.jruby.InstanceEvalServlet;
import net.stbbs.spring.jruby.JRubyRuntimeListener;
import net.stbbs.spring.jruby.blazeds.JRubyMessageBrokerServlet;

import org.h2.server.web.WebServlet;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.webapp.WebAppContext;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.request.RequestContextListener;

public class Main extends Server {

	/**
	 * @param args
	 * @throws Exception 
	 */
	
	static final String WEBAPP_DIR = "webapp";
	
	public static void main(String[] args) {
		Main me = new Main();
		SocketConnector connector = new SocketConnector();
		connector.setPort(8080);
		me.setConnectors(new SocketConnector[] {connector});
		WebAppContext context = new WebAppContext();
		
		context.setContextPath("/");
		context.setResourceBase(WEBAPP_DIR);
		context.setParentLoaderPriority(true);
		me.setHandler(context);

		//context.setDefaultsDescriptor("net/stbbs/spring/jruby/standalone/web.xml");

		Map params = new HashMap();
		// もしWEB-INF/applicationContext.xmlがない場合は内蔵のものを使う
		if (!new File(WEBAPP_DIR + "/WEB-INF/applicationContext.xml").exists()) {
			params.put("contextConfigLocation", "classpath:net/stbbs/spring/jruby/standalone/applicationContext.xml");
		}
		if (!new File(WEBAPP_DIR + '/' + JRubyRuntimeListener.DEFAULT_PROXYCLASS_SCRIPT_NAME).exists()) {
			params.put(JRubyRuntimeListener.PROXYCLASS_SCRIPT_PARAM_NAME,
			"classpath:net/stbbs/spring/jruby/standalone/instanceEvalServlet.rb");
		}
		context.setInitParams(params);
		
		ContextLoaderListener cll = new ContextLoaderListener();
		RequestContextListener rcl = new RequestContextListener();
		context.setEventListeners(new EventListener[]{cll, rcl});
		
		ServletHandler servletHandler = context.getServletHandler();

		ServletHolder holder = servletHandler.newServletHolder(InstanceEvalServlet.class);
		holder.setInitOrder(1);
		params = new HashMap();
		
		holder.setInitParameters(params);
		servletHandler.addServletWithMapping(holder, "/instance_eval");
		servletHandler.addServletWithMapping(JRubyMessageBrokerServlet.class, "/rubymessagebroker/*");
		servletHandler.addServletWithMapping(WebServlet.class, "/h2/*");
		
		//servletHandler.addFilterWithMapping(DispatcherFilter.class, "/*", 0);		
		
		boolean headless = GraphicsEnvironment.isHeadless();
		if (!headless) {
			JFrame frame = new JFrame();
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.setVisible(true);
		}

		try {
			me.start();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		
		try {
			Class desktopClass = Class.forName("java.awt.Desktop");
			Object desktopObject = desktopClass.getMethod("getDesktop").invoke(null);
			URI uri = new URI("http://localhost:8080/instance_eval");
			desktopClass.getMethod("browse", URI.class).invoke(desktopObject, uri);
		} catch (ClassNotFoundException e) {
			// Java5
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}

}
