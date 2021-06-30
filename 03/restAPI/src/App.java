import cfg.ConfigLoader;
import handler.DBHandler;

public class App{
	public static void main(String args[]){
		// load config
		ConfigLoader config = new ConfigLoader();

		if (!config.loadConfigFile())
			System.exit(1);

		// connect to database
		DBHandler dbhandler = new DBHandler();

		if (!dbhandler.connectToDatabase(config.getConfigData()))
			System.exit(1);

		// start server
		Server server = new Server(config.getConfigData());

		if (!server.startServer(dbhandler))
			System.exit(1);
	}
}
