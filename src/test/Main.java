import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.webapp.WebAppContext;

public class Main extends Server {

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		Main me = new Main();
		SocketConnector connector = new SocketConnector();
		connector.setPort(8080);
		me.setConnectors(new SocketConnector[] {connector});
		WebAppContext context = new WebAppContext();
		
		context.setContextPath("/");
		context.setResourceBase("src/webapp");
		context.setParentLoaderPriority(true);

		me.setHandler(context);
		
		me.start();
	}

}
