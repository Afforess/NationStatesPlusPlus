package controllers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import org.apache.commons.dbutils.DbUtils;
import org.apache.commons.lang3.text.WordUtils;

import play.libs.Json;
import play.mvc.Result;
import play.mvc.Results;

import com.afforess.assembly.util.NationCache;
import com.afforess.assembly.util.RegionCache;
import com.afforess.assembly.util.Utils;
import com.mchange.v2.c3p0.ComboPooledDataSource;

public class AutocompleteController extends DatabaseController {

	public AutocompleteController(ComboPooledDataSource pool, NationCache cache, RegionCache regionCache) {
		super(pool, cache, regionCache);
	}

	public Result autocompleteNation(String start) throws SQLException {
		ArrayList<String> nations = new ArrayList<String>();
		if (start == null || start.length() < 3) {
			Utils.handleDefaultGetHeaders(request(), response(), null);
			return Results.badRequest();
		}
		
		Connection conn = null;
		try {
			conn = getConnection();
			PreparedStatement select = conn.prepareStatement("SELECT name FROM assembly.nation WHERE name LIKE ? LIMIT 0, 50");
			select.setString(1, start.toLowerCase().replaceAll(" ", "_") + "%");
			ResultSet result = select.executeQuery();
			while(result.next()) {
				nations.add(WordUtils.capitalizeFully(result.getString(1).replaceAll("_", " ")));
			}
		} finally {
			DbUtils.closeQuietly(conn);
		}
		
		Result result = Utils.handleDefaultGetHeaders(request(), response(), String.valueOf(nations.hashCode()));
		if (result != null) {
			return result;
		}
		return ok(Json.toJson(nations)).as("application/json");
	}
}