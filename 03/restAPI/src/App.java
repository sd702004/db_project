import cfg.ConfigLoader;

public class App{
	public static void main(String args[]){
		ConfigLoader config = new ConfigLoader();

		if (!config.loadConfigFile())
			System.exit(1);
	}
}
