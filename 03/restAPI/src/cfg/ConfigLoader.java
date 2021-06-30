package cfg;

import java.io.*;
import java.util.HashMap;

public class ConfigLoader {
	private Config config_data;

	public boolean loadConfigFile(){
		HashMap<String, String> config = new HashMap<String, String>();

		try {
			FileInputStream input = new FileInputStream("config.cfg");
			BufferedReader reader = new BufferedReader(new InputStreamReader(input));

			String line;
			while ( (line=reader.readLine()) != null ){
				line = line.trim();
				if (line.isEmpty() || line.charAt(0) == '#')
					continue;

				String cfg[] = line.split("#")[0].split("=");
				if (cfg.length != 2)
					continue;

				config.put(cfg[0].trim(), cfg[1].trim());
			}

			reader.close();
		} catch (FileNotFoundException e) {
			System.err.printf("config file not found\n");
			return false;
		} catch (IOException e) {
			System.err.println(e.getMessage());
			return false;
		}

		String val;
		String error_msg = "%s not found in config file\n";

		// set config
		config_data = new Config();

		// db username
		if ( (val = config.get("db_username")) == null ){
			System.err.printf(error_msg, "db_username");
			return false;
		}

		config_data.db_username = val;

		// db password
		if ( (val = config.get("db_password")) == null ){
			System.err.printf(error_msg, "db_username");
			return false;
		}

		config_data.db_password = val;

		// db name
		if ( (val = config.get("db_name")) == null ){
			System.err.printf(error_msg, "db_name");
			return false;
		}

		config_data.db_name = val;

		// db port
		if ( (val = config.get("db_port")) == null ){
			System.err.printf(error_msg, "db_port");
			return false;
		}

		try {
			config_data.db_port = Integer.parseInt(val);
		} catch (NumberFormatException e){
			System.err.printf("db_port must be a number. \"%s\" isn't a numeric value\n", val);
			return false;
		}

		// server port
		if ( (val = config.get("server_port")) == null ){
			System.err.printf(error_msg, "server_port");
			return false;
		}

		try {
			config_data.server_port = Integer.parseInt(val);
		} catch (NumberFormatException e){
			System.err.printf("server must be a number. \"%s\" isn't a numeric value\n", val);
			return false;
		}

		return true;
	}

	public Config getConfigData(){
		return config_data;
	}
}
