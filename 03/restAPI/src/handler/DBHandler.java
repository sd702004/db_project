package handler;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool.PoolInitializationException;
import java.sql.Connection;
import java.sql.SQLException;

import cfg.Config;

public class DBHandler {
	private HikariDataSource ds;

	public boolean connectToDatabase(Config config){
		HikariConfig hconfig = new HikariConfig();
		String db_url = String.format("jdbc:postgresql://localhost:%d/%s", config.db_port, config.db_name);

		hconfig.setJdbcUrl(db_url);
		hconfig.setUsername(config.db_username);
		hconfig.setPassword(config.db_password);

		try {
			ds = new HikariDataSource(hconfig);
		} catch (PoolInitializationException e){
			System.err.println(e.getMessage());
			return false;
		}

		if (ds == null){
			System.err.println("DataSource creation failed");
			return false;
		}

		return true;
	}

	public Connection getConnection() throws SQLException {
		return ds.getConnection();
	}
}
