package net.stbbs.spring.jruby.standalone;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.net.URI;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JFrame;

import net.stbbs.spring.jruby.DispatcherFilter;
import net.stbbs.spring.jruby.InstanceEvalServlet;
import net.stbbs.spring.jruby.SpringIntegratedJRubyRuntime;

import org.h2.server.web.WebServlet;
import org.jruby.RubyClass;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.servlet.ServletMapping;
import org.mortbay.jetty.webapp.WebAppContext;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.request.RequestContextListener;

public class Main extends Server {

	/**
	 * @param args
	 * @throws Exception 
	 */
	
	static final String WEBAPP_DIR = "webapp";
	
	public static void main(String[] args) throws Exception {
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
		context.setInitParams(params);
		
		ContextLoaderListener cll = new ContextLoaderListener();
		RequestContextListener rcl = new RequestContextListener();
		context.setEventListeners(new EventListener[]{cll, rcl});
		
		ServletHandler servletHandler = context.getServletHandler();

		ServletHolder holder = servletHandler.newServletHolder(InstanceEvalServlet.class);
		holder.setInitOrder(1);
		params = new HashMap();
		params.put(InstanceEvalServlet.INIT_SCRIPT_PARAM_NAME,
				"classpath:net/stbbs/spring/jruby/standalone/instanceEvalServlet.rb,WEB-INF/instanceEvalServlet.rb");
		holder.setInitParameters(params);
		servletHandler.addServletWithMapping(holder, "/instance_eval");
		
		servletHandler.addServletWithMapping(WebServlet.class, "/h2/*");
		
		servletHandler.addFilterWithMapping(DispatcherFilter.class, "/*", 0);		
		
		boolean headless = GraphicsEnvironment.isHeadless();
		if (!headless) {
			JFrame frame = new JFrame();
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.setVisible(true);
		}

		me.start();
		Desktop.getDesktop().browse(new URI("http://localhost:8080/instance_eval"));
	}

}
