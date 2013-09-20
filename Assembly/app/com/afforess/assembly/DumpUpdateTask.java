package com.afforess.assembly;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import org.apache.commons.dbutils.DbUtils;

import play.Logger;

import com.afforess.nsdump.NationsDump;
import com.afforess.nsdump.RegionsDump;
import com.mchange.v2.c3p0.ComboPooledDataSource;

public class DumpUpdateTask implements Runnable {
	private final File regionDump;
	private final File nationDump;
	private final ComboPooledDataSource pool;
	public DumpUpdateTask(ComboPooledDataSource pool, File regionDump, File nationDump) {
		this.pool = pool;
		this.regionDump = regionDump;
		this.nationDump = nationDump;
	}

	@Override
	public void run() {
		try {
			Logger.info("Starting daily dumps update task");
			NationsDump nations = new NationsDump(nationDump);
			nations.parse();
			updateNations(nations);
			
			RegionsDump regions = new RegionsDump(regionDump);
			regions.parse();
			updateRegions(regions);
			
			Logger.info("Finished daily dumps update task");
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private void updateRegions(RegionsDump dump) {
		Connection conn = null;
		Connection assembly = null;
		try {
			conn = dump.getDatabaseConnection();
			PreparedStatement statement = conn.prepareStatement("SELECT name FROM regions");
			ResultSet result = statement.executeQuery();
			final HashSet<String> set = new HashSet<String>(20000);
			while (result.next()) {
				set.add(result.getString(1));
			}
			Logger.info("Updating " + set.size() + " regions from daily dump");
			PreparedStatement select = conn.prepareStatement("SELECT flag, delegate, numnations FROM regions WHERE name = ?");
			int newRegions = 0;
			for (String region : set) {
				select.setString(1, region);
				result = select.executeQuery();
				result.next();
				newRegions += updateRegion(region, result.getString(1), result.getString(2), result.getInt(3));
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) { }
			}
			
			Logger.info("Added " + newRegions + " regions to the database");
			assembly = pool.getConnection();
			HashSet<String> allRegions = new HashSet<String>(20000);
			select = assembly.prepareStatement("SELECT name FROM assembly.region");
			result = select.executeQuery();
			while (result.next()) {
				allRegions.add(result.getString(1));
			}
			allRegions.removeAll(set);
			Logger.info("Marking " + allRegions.size() + " regions as dead");
			
			PreparedStatement delete = assembly.prepareStatement("DELETE FROM assembly.region WHERE name = ?");
			PreparedStatement deletePopulation = assembly.prepareStatement("DELETE FROM assembly.region_populations WHERE region = ?");
			for (String region : allRegions) {
				delete.setString(1, region);
				delete.addBatch();
				deletePopulation.setString(1, region);
				deletePopulation.addBatch();
			}
			delete.executeBatch();
			deletePopulation.executeBatch();
			conn.prepareStatement("DROP TABLE regions");
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(assembly);
		}
	}

	private int updateRegion(String region, String flag, String delegate, int numNations) throws SQLException {
		Connection conn = pool.getConnection();
		try {
			PreparedStatement select = conn.prepareStatement("SELECT name FROM assembly.region WHERE name = ?");
			select.setString(1, region);
			ResultSet result = select.executeQuery();
			boolean found = false;
			if (result.next()) {
				found = true;
			}
	
			Logger.info("Updating region [" + region + "] from the daily dump");
	
			PreparedStatement insert = null;
			insert = conn.prepareStatement("INSERT INTO assembly.region_populations (region, population, timestamp) VALUES (?, ?, ?)");
			insert.setString(1, region);
			insert.setInt(2, numNations);
			insert.setLong(3, System.currentTimeMillis());
			insert.executeUpdate();
	
			PreparedStatement update = null;
			if (!found) {
				update = conn.prepareStatement("INSERT INTO assembly.region (name, flag, delegate) VALUES (?, ?, ?)");
				update.setString(1, region);
				update.setString(2, flag);
				update.setString(3, delegate);
				update.executeUpdate();
				return 1;
			} else {
				update = conn.prepareStatement("UPDATE assembly.region SET flag = ?, delegate = ? WHERE name = ?");
				update.setString(1, flag);
				update.setString(2, delegate);
				update.setString(3, region);
				update.executeUpdate();
				return 0;
			}
		} finally {
			DbUtils.closeQuietly(conn);
		}
	}

	private void updateNations(NationsDump dump) {
		Connection conn = null;
		Connection assembly = null;
		try {
			conn = dump.getDatabaseConnection();
			PreparedStatement statement = conn.prepareStatement("SELECT name FROM nations");
			ResultSet result = statement.executeQuery();
			final HashSet<String> set = new HashSet<String>(150000);
			while (result.next()) {
				set.add(result.getString(1));
			}
			DbUtils.closeQuietly(result);
			Logger.info("Updating " + set.size() + " nations from daily dump");
			PreparedStatement select = conn.prepareStatement("SELECT fullname, unstatus, influence, lastlogin, flag, region FROM nations WHERE name = ?");
			int newNations = 0;
			for (String nation : set) {
				select.setString(1, nation);
				result = select.executeQuery();
				result.next();
				newNations += updateNation(nation, result.getString(1), !result.getString(2).toLowerCase().equals("non-member"), result.getString(3), result.getInt(4), result.getString(5), result.getString(6));
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) { }
			}
			Logger.info("Added " + newNations + " nations to the database");
			assembly = pool.getConnection();
			HashSet<String> allNations = new HashSet<String>(150000);
			select = assembly.prepareStatement("SELECT name FROM assembly.nation WHERE alive = 1");
			result = select.executeQuery();
			while (result.next()) {
				allNations.add(result.getString(1));
			}
			allNations.removeAll(set);
			Logger.info("Marking " + allNations.size() + " nations as dead");
			PreparedStatement dead = assembly.prepareStatement("UPDATE assembly.nation SET alive = 0 WHERE name = ?");
			for (String nation : allNations) {
				dead.setString(1, nation);
				dead.addBatch();
			}
			dead.executeBatch();
			conn.prepareStatement("DROP TABLE nations");
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			DbUtils.closeQuietly(conn);
			DbUtils.closeQuietly(assembly);
		}
	}

	private int updateNation(String nation, String fullName,  boolean waMember, String influence, int lastLogin, String flag, String region) throws SQLException {
		Connection conn = pool.getConnection();
		int id = -1;
		try {
			PreparedStatement select = conn.prepareStatement("SELECT id FROM assembly.nation WHERE name = ?");
			select.setString(1, nation);
			ResultSet result = select.executeQuery();
			if (result.next()) {
				id = result.getInt(1);
			}
			Logger.info("Updating nation [" + nation + "] from the daily dump");
			PreparedStatement insert = null;
			if (id == -1) {
				insert = conn.prepareStatement("INSERT INTO assembly.nation (name, formatted_name, flag, region, influence_desc, last_login, wa_member, alive) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
				insert.setString(1, nation);
				insert.setString(2, fullName);
				insert.setString(3, flag);
				insert.setString(4, region);
				insert.setString(5, influence);
				insert.setInt(6, lastLogin);
				insert.setByte(7, (byte)(waMember ? 1 : 0));
				insert.setByte(8, (byte)1);
				insert.executeUpdate();
				return 1;
			} else {
				insert = conn.prepareStatement("UPDATE assembly.nation SET formatted_name = ?, flag = ?, region = ?, influence_desc = ?, last_login = ?, wa_member = ? WHERE id = ?");
				insert.setString(1, fullName);
				insert.setString(2, flag);
				insert.setString(3, region);
				insert.setString(4, influence);
				insert.setInt(5, lastLogin);
				insert.setByte(6, (byte)(waMember ? 1 : 0));
				insert.setInt(7, id);
				insert.executeUpdate();
				return 0;
			}
		} finally {
			DbUtils.closeQuietly(conn);
		}
	}

}