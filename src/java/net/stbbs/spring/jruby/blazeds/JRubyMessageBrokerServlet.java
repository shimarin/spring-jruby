package net.stbbs.spring.jruby.blazeds;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.stbbs.spring.jruby.JRubyRuntimeListener;
import net.stbbs.spring.jruby.modules.ApplicationContextSupport;
import net.stbbs.spring.jruby.modules.BlazeDSSupport;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyHash;
import org.jruby.runtime.builtin.IRubyObject;

import edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap;
import flex.messaging.Destination;
import flex.messaging.FlexContext;
import flex.messaging.HttpFlexSession;
import flex.messaging.MessageBroker;
import flex.messaging.MessageException;
import flex.messaging.Server;
import flex.messaging.VersionInfo;
import flex.messaging.client.FlexClientManager;
import flex.messaging.config.ChannelSettings;
import flex.messaging.config.ConfigMap;
import flex.messaging.config.ConfigurationException;
import flex.messaging.config.ConfigurationManager;
import flex.messaging.config.FlexClientSettings;
import flex.messaging.config.SecuritySettings;
import flex.messaging.config.SystemSettings;
import flex.messaging.endpoints.Endpoint;
import flex.messaging.endpoints.Endpoint2;
import flex.messaging.log.Log;
import flex.messaging.log.LogCategories;
import flex.messaging.log.Logger;
import flex.messaging.log.ServletLogTarget;
import flex.messaging.security.LoginManager;
import flex.messaging.services.AuthenticationService;
import flex.messaging.services.Service;
import flex.messaging.util.ExceptionUtil;
import flex.messaging.util.RedeployManager;


/**
 * 
 * @author shimarin
 *
 * flex-messaging-common.jar
 * flex-messaging-remoting.jar
 * flex-messaging-core.jar
 */
public class JRubyMessageBrokerServlet  extends HttpServlet {
    public static final String LOG_CATEGORY_STARTUP_BROKER = LogCategories.STARTUP_MESSAGEBROKER;
    public static final String AMF_CHANNELID = "my-amf";
    public static final String POLLING_AMF_CHANNELID = "my-polling-amf";
    
    

    private MessageBroker broker;
    //private ApplicationContext wac;
	protected IRubyObject applicationContext;
    
    public void init(ServletConfig servletConfig)
    throws ServletException, UnavailableException
    {
    	super.init(servletConfig);

		applicationContext = JRubyRuntimeListener.getApplicationContextObject(getServletContext());
		if (applicationContext == null) {
			try {
				applicationContext = JRubyRuntimeListener.initializeRuntime(this.getServletContext());
				servletConfig.getServletContext().setAttribute(ApplicationContextSupport.APPLICATIONCONTEXT_OBJECT_NAME, applicationContext);
			} catch (IOException e) {
				throw new ServletException(e);
			}
		}

    	// Set the servlet config as thread local
        FlexContext.setThreadLocalObjects(null, null, null, null, null, servletConfig);

        ServletLogTarget.setServletContext(servletConfig.getServletContext());

        ClassLoader loader = this.getClass().getClassLoader();

        String useCCLoader;

        if ((useCCLoader = servletConfig.getInitParameter("useContextClassLoader")) != null &&
             useCCLoader.equalsIgnoreCase("true"))
            loader = Thread.currentThread().getContextClassLoader();

        // Start the broker
        try
        {
            // Create broker.
            broker = new MessageBroker(false, BlazeDSSupport.DEFAULT_MESSAGEBROKER_ID, loader);

            // Set the servlet config as thread local
            FlexContext.setThreadLocalObjects(null, null, broker, null, null, servletConfig);

            Logger logger = Log.getLogger(ConfigurationManager.LOG_CATEGORY);
            if (Log.isInfo())
            {
                logger.info(VersionInfo.buildMessage());
            }

            // Create endpoints, services, security, and logger on the broker based on configuration
            configureBroker(broker);

            long timeBeforeStartup = 0;

            //initialize the httpSessionToFlexSessionMap
            synchronized(HttpFlexSession.mapLock)
            {
                if (servletConfig.getServletContext().getAttribute(HttpFlexSession.SESSION_MAP) == null)
                    servletConfig.getServletContext().setAttribute(HttpFlexSession.SESSION_MAP, new ConcurrentHashMap());
            }

            broker.start();
        }
        catch (Throwable t)
        {
            // On any unhandled exception destroy the broker, log it and rethrow.
            destroy();
            
            System.err.println("**** MessageBrokerServlet failed to initialize due to runtime exception: " + ExceptionUtil.exceptionToString(t));
            throw new UnavailableException(t.getMessage());
        }
        finally
        {
            FlexContext.clearThreadLocalObjects();
        }
    }
    
    protected void configureBroker(MessageBroker broker) throws IOException
    {
		Map<String,ChannelSettings> channelSettings = new HashMap<String,ChannelSettings>();
		broker.setChannelSettings(channelSettings);

		SecuritySettings securitySettings = new SecuritySettings();
        securitySettings.setServerInfo(this.getServletConfig().getServletContext().getServerInfo());

        broker.setSecuritySettings(new SecuritySettings());
        SystemSettings systemSettings = new SystemSettings();
        broker.setSystemSettings(systemSettings);
        FlexClientSettings flexClientSettings = new FlexClientSettings();
        broker.setFlexClientSettings(flexClientSettings);
        LoginManager loginManager = new LoginManager();
        broker.setLoginManager(loginManager);
        
        FlexClientManager flexClientManager = new FlexClientManager(broker.isManaged(), broker);
        broker.setFlexClientManager(flexClientManager);
        
        RedeployManager redeployManager = new RedeployManager();
        redeployManager.setEnabled(systemSettings.getRedeployEnabled());
        redeployManager.setWatchInterval(systemSettings.getWatchInterval());
        redeployManager.setTouchFiles(systemSettings.getTouchFiles());
        redeployManager.setWatchFiles(systemSettings.getWatchFiles());
        broker.setRedeployManager(redeployManager);
        
        /**
         * 定数 BlazeDSConfigを読む
         */
		String remotingEndpointURL = BlazeDSSupport.ENDPOINT_URL_BASE + BlazeDSSupport.DEFAULT_REMOTING_ENDPOINT_URL;
		Map<String, IRubyObject> destinations = null;
        Ruby runtime = applicationContext.getRuntime();
		RubyClass proxyClass = (RubyClass)applicationContext.callMethod(runtime.getCurrentContext(), "getProxyClass");
        if (proxyClass.isConstantDefined("BlazeDSConfig")) {
        	Map<String,IRubyObject> config = net.stbbs.jruby.Util.convertRubyHash((RubyHash)proxyClass.getConstant("BlazeDSConfig"));
        	Map<String,IRubyObject> remotingConfig = net.stbbs.jruby.Util.convertRubyHash((RubyHash)config.get("remoting"));
        	if (remotingConfig != null) {
        		if (remotingConfig.containsKey("endpoint_url")) {
        			remotingEndpointURL = remotingConfig.get("endpoint_url").asString().getUnicodeValue();
        			if (!remotingEndpointURL.startsWith("http://") && !remotingEndpointURL.startsWith("https://")) {
        				remotingEndpointURL = BlazeDSSupport.ENDPOINT_URL_BASE + remotingEndpointURL;
        			}
        		}
        		destinations = net.stbbs.jruby.Util.convertRubyHash((RubyHash)remotingConfig.get("destinations"));
        	}
        }

		ChannelSettings csNonPolling = new ChannelSettings(AMF_CHANNELID);
		csNonPolling.setUri(remotingEndpointURL);
		csNonPolling.setClientType("mx.messaging.channels.AMFChannel");
		csNonPolling.setEndpointType("flex.messaging.endpoints.AMFEndpoint");
		csNonPolling.addProperty("polling-enabled", "false");
        createEndpoints(broker, csNonPolling);

		ChannelSettings csPolling = new ChannelSettings(POLLING_AMF_CHANNELID);
		csPolling.setUri("http://{server.name}:{server.port}/{context.root}/rubymessagebroker/amfpolling");
		csPolling.setClientType("mx.messaging.channels.AMFChannel");
		csPolling.setEndpointType("flex.messaging.endpoints.AMFEndpoint");
		csPolling.addProperty("polling-enabled", "false");
		csPolling.addProperty("polling-interval-millis", "0");
		csPolling.addProperty("wait-interval-millis", "-1");
		csPolling.addProperty("max-waiting-poll-requests", "300");
        createEndpoints(broker, csPolling);

        // Default channels have to be set after endpoints are created.
        List<String> defaultChannels = new ArrayList<String>();
        defaultChannels.add(AMF_CHANNELID);
        defaultChannels.add(POLLING_AMF_CHANNELID);
        broker.setDefaultChannels(defaultChannels);

        //the broker needs its AuthenticationService always
        AuthenticationService authService = new AuthenticationService();
        authService.setMessageBroker(broker);

       // Create the RPC Service
       Service service = broker.createService(BlazeDSSupport.DEFAULT_REMOTING_SERVICE_ID, "flex.messaging.services.RemotingService");

       // Default Channels
       service.addDefaultChannel(AMF_CHANNELID);

       // Adapter Definitions
       service.registerAdapter("jruby", "net.stbbs.spring.jruby.blazeds.JRubyAdapter");
       service.setDefaultAdapter("jruby");
       
       // ポーリングサービス
       // Create the Service
       Service msgService = broker.createService(BlazeDSSupport.DEFAULT_MESSAGE_SERVICE_ID, "flex.messaging.services.MessageService");

       // Default Channels
       msgService.addDefaultChannel(POLLING_AMF_CHANNELID);

       // Adapter Definitions
       msgService.registerAdapter("actionscript", "flex.messaging.services.messaging.adapters.ActionScriptAdapter");
       msgService.setDefaultAdapter("actionscript");

       broker.addFactory("JRubyFactory", new JRubyFactory(applicationContext));

       if (destinations != null) {
	       for (Map.Entry<String,IRubyObject> entry : destinations.entrySet()) {
	    	   String destinationId = entry.getKey();
	    	   Map<String,IRubyObject> options = net.stbbs.jruby.Util.convertRubyHash(entry.getValue().convertToHash());
	    	   Destination dest = service.createDestination(destinationId);
	    	   ConfigMap props = new ConfigMap();
	    	   props.addProperty("factory", "JRubyFactory");
	    	   props.addProperty("source", options.containsKey("bean")? options.get("bean").asString().getUnicodeValue(): destinationId);
	    	   if (options.containsKey("include_methods")) {
	    		   ConfigMap im = new ConfigMap();
	    		   for (IRubyObject methodName:net.stbbs.jruby.Util.convertRubyArray(options.get("include_methods").convertToArray())) {
	    			   ConfigMap m = new ConfigMap();
	    			   m.addProperty("name", methodName.asString().getUnicodeValue());
	    			   im.addProperty("method", m);
	    		   }
	    		   props.addProperty("include-methods", im);
	    	   }
	    	   if (options.containsKey("exclude_methods")) {
	    		   ConfigMap im = new ConfigMap();
	    		   for (IRubyObject methodName:net.stbbs.jruby.Util.convertRubyArray(options.get("exclude_methods").convertToArray())) {
	    			   ConfigMap m = new ConfigMap();
	    			   m.addProperty("name", methodName.asString().getUnicodeValue());
	    			   im.addProperty("method", m);
	    		   }
	    		   props.addProperty("exclude-methods", im);
	    	   }
	    	   dest.initialize(destinationId, props);
	    	   dest.setChannels(service.getDefaultChannels());
	       }
       }
    }
   
    private void createEndpoints(MessageBroker broker, ChannelSettings chanSettings)
    {
    	String url = chanSettings.getUri();
    	String endpointClassName = chanSettings.getEndpointType();

    	if (chanSettings.isRemote()) return;

    	Endpoint endpoint = broker.createEndpoint(chanSettings.getId(), url, endpointClassName);
    	endpoint.setSecurityConstraint(chanSettings.getConstraint());
    	endpoint.setClientType(chanSettings.getClientType());

    	String referencedServerId = chanSettings.getServerId();
    	if ((referencedServerId != null) && (endpoint instanceof Endpoint2)) {
    		Server server = broker.getServer(referencedServerId);
    		if (server == null) {
    			ConfigurationException ce = new ConfigurationException();
    			ce.setMessage(11128, new Object[] {chanSettings.getId(), referencedServerId});
    			throw ce;
    		}
    		((Endpoint2)endpoint).setServer(broker.getServer(referencedServerId));
    	}

    	endpoint.initialize(chanSettings.getId(), chanSettings.getProperties());
    }

	public void service(HttpServletRequest req, HttpServletResponse res) throws ServletException
	{
        try
        {
            broker.initThreadLocals();
            FlexContext.setThreadLocalObjects(null, null, broker, req, res, getServletConfig());
            HttpFlexSession fs = HttpFlexSession.getFlexSession(req);
            Principal principal = null;  
            if(FlexContext.isPerClientAuthentication()) {
            	principal = FlexContext.getUserPrincipal();
            } else {            	
            	principal = fs.getUserPrincipal();
            }

            if (principal == null && req.getHeader("Authorization") != null) {
                String encoded = req.getHeader("Authorization");
                if (encoded.indexOf("Basic") > -1) {
                    encoded = encoded.substring(6); //Basic.length()+1
                    try {
                        AuthenticationService.decodeAndLogin(encoded, broker.getLoginManager());
                    }
                    catch (Exception e) {
                        // do nothing
                    }
                }
            }

            String contextPath = req.getContextPath();
            String pathInfo = req.getPathInfo();
            String endpointPath = req.getServletPath();
            if (pathInfo != null)
                endpointPath = endpointPath + pathInfo;

            Endpoint endpoint = null;
            try {
                endpoint = broker.getEndpoint(endpointPath, contextPath);
            }
            catch (MessageException me) {
                try {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                }
                catch (IOException ignore)
                {}
            }
            if (endpoint != null) {
                try {
                    if (Log.isInfo()) {
                        Log.getLogger(LogCategories.ENDPOINT_GENERAL).info("Channel endpoint {0} received request.",
                                                               new Object[] {endpoint.getId()});
                    }

                    endpoint.service(req, res);
                }
                catch (UnsupportedOperationException ue) {
                    try {
                        res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                    }
                    catch (IOException ignore)
                    {}
                }
            } else {
                try {
                    res.sendError(HttpServletResponse.SC_FORBIDDEN);
                }
                catch (IOException ignore)
                {}
            }
        }
        finally {
            FlexContext.clearThreadLocalObjects();
        }
	}
}
