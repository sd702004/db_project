import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.io.IOException;

import handler.DBHandler;
import handler.RequestHandler;
import cfg.Config;

public class Server {
	private int port;

	public Server(Config config){
		port = config.server_port;
	}

	public boolean startServer(DBHandler dbhandler){
		HttpServer server = null;

		try {
			server = HttpServer.create(new InetSocketAddress(port), 0);

		} catch (IOException e){
			System.err.printf("server starting failed: %s\n", e.getMessage());
			return false;
		}

		server.createContext("/", new RequestHandler(dbhandler));
		server.setExecutor(Executors.newCachedThreadPool()); // multi-thread request handling
		server.start();

		System.out.printf("server started on port %d\n", port);
		return true;
	}
}
