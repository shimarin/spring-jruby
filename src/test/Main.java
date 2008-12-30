import net.stbbs.applicationserver.SingleContextServer;

public class Main extends SingleContextServer {

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		Main me = new Main();
		me.setContextPath("/");
		me.setResourceBase("src/webapp");
		
		me.shutdownRunning();
		me.start();
		me.waitForShutdown();
	}

}
