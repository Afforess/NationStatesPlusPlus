package net.nationstatesplusplus.assembly;

import java.beans.PropertyVetoException;
import java.io.File;

import org.spout.cereal.config.ConfigurationException;
import org.spout.cereal.config.ConfigurationNode;
import org.spout.cereal.config.yaml.YamlConfiguration;

import play.Logger;

import com.mchange.v2.c3p0.ComboPooledDataSource;

public class Start {
	
	public static YamlConfiguration loadConfig() {
		YamlConfiguration config = new YamlConfiguration(new File("./config.yml"));
		try {
			config.load();
		} catch (ConfigurationException e1) {
			Logger.error("Unable to parse configuration", e1);
			throw new RuntimeException(e1);
		}
		return config;
	}

	public static File getApplicationDirectory() {
		return new File(Start.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParentFile().getParentFile();
	}

	public static ComboPooledDataSource loadDatabase(ConfigurationNode settings) {
		try {
			Logger.info("Initializing database connection pool to [ " + settings.getChild("jbdc").getString() + " ]");
			//Connection Driver
			Class.forName("com.mysql.jdbc.Driver");
			ComboPooledDataSource pool = new ComboPooledDataSource();
			pool.setDriverClass("com.mysql.jdbc.Driver");
			pool.setJdbcUrl(settings.getChild("jbdc").getString());

			//Connection Pooling
			pool.setMaxPoolSize(75);
			pool.setMinPoolSize(1);

			pool.setMaxIdleTime(180); // 3 min after being unused, conn is closed
			pool.setMaxConnectionAge(60 * 60); //1 hr after connection is open, it is closed

			//Connection Debugging
			pool.setDebugUnreturnedConnectionStackTraces(true);
			pool.setUnreturnedConnectionTimeout(6 * 60); //throw an exception if any code holds a connection open for > 6 min

			//Statement caching
			pool.setMaxStatementsPerConnection(10);

			//Connection testing (supports connection reconnecting)
			pool.setTestConnectionOnCheckin(true);
			pool.setTestConnectionOnCheckout(true);
			pool.setPreferredTestQuery("SELECT 1");
			
			//Timeout acquiring a connection after 30000ms (30s)
			pool.setCheckoutTimeout(1000 * 30);

			//Connection Auth
			Logger.info("Authenticating database connection pool with user [ " + settings.getChild("user").getString() + " ]");
			pool.setUser(settings.getChild("user").getString());
			pool.setPassword(settings.getChild("password").getString());
			return pool;
		} catch (ClassNotFoundException e) {
			Logger.error("No Database driver found!", e);
			return null;
		} catch (PropertyVetoException e) {
			Logger.error("No Database driver found!", e);
			return null;
		}
	}

}
