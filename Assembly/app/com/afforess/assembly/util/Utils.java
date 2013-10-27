package com.afforess.assembly.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.WordUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

import com.limewoodMedia.nsapi.NationStates;

import play.Logger;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;

public class Utils {
	public static final Pattern NATION_PATTERN = Pattern.compile("@@[^\\s]*@@");
	public static final Pattern REGION_PATTERN = Pattern.compile("%%[^\\s]*%%");
	public static final Pattern RMB_PATTERN = Pattern.compile("##[^\\s]*##");
	public static final DateTimeFormatter HTTP_DATE_TIME = (new DateTimeFormatterBuilder()).appendDayOfWeekShortText().appendLiteral(", ")
			.appendDayOfMonth(2).appendLiteral(' ')
			.appendMonthOfYearShortText().appendLiteral(' ')
			.appendYear(4,4).appendLiteral(' ')
			.appendHourOfDay(2).appendLiteral(':')
			.appendMinuteOfHour(2).appendLiteral(':')
			.appendSecondOfMinute(2).appendLiteral(" GMT").toFormatter();

	public static Result handleDefaultGetHeaders(Request request, Response response, String calculatedEtag) {
		return handleDefaultGetHeaders(request, response, calculatedEtag, "60");
	}

	public static Result handleDefaultGetHeaders(Request request, Response response, String calculatedEtag, String seconds) {
		response.setHeader("Access-Control-Allow-Origin", "*");
		response.setHeader("Access-Control-Allow-Methods", "POST, GET, HEAD");
		response.setHeader("Access-Control-Max-Age", seconds);
		response.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
		response.setHeader("Cache-Control", "public, max-age=" + seconds);
		response.setHeader("Expires", new DateTime().plusHours(1).toString(Utils.HTTP_DATE_TIME));
		response.setHeader("Last-Modified", new DateTime().plusHours(-1).toString(Utils.HTTP_DATE_TIME));
		response.setHeader("Vary", "Accept-Encoding");
		response.setHeader("ETag", calculatedEtag);

		String eTag = request.getHeader("If-None-Match");
		if (calculatedEtag != null && calculatedEtag.equals(eTag)) {
			return Results.status(304);
		}
		return null;
	}

	public static void handleDefaultPostHeaders(Request request, Response response) {
		response.setHeader("Access-Control-Allow-Origin", "*");
		response.setHeader("Access-Control-Allow-Methods", "POST, GET, HEAD");
		response.setHeader("Access-Control-Max-Age", "60");
		response.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
	}

	public static String formatHappeningText(String text, Connection conn, String owner) throws SQLException {
		text = text.replaceAll("%%capitalist_paradise%rmb%%", "<a href=\"http://www.nationstates.net/region=capitalist_paradise#rmb \">Regional Message Board</a>");
		do {
			Matcher match = NATION_PATTERN.matcher(text);
			if (match.find()) {
				String nation = text.substring(match.start() + 2, match.end() - 2);
				String replacement = formatFullName(nation, conn, !owner.equals(nation));
				text = match.replaceFirst(replacement);
			} else {
				break;
			}
		} while (true);

		do {
			Matcher match = REGION_PATTERN.matcher(text);
			if (match.find()) {
				String region = text.substring(match.start() + 2, match.end() - 2);
				String replacement = "<a href=\"http://www.nationstates.net/region=" + region + "\">" + formatName(region) + "</a>";
				text = match.replaceFirst(replacement);
			} else {
				break;
			}
		} while (true);
		
		do {
			Matcher match = RMB_PATTERN.matcher(text);
			if (match.find()) {
				String region = text.substring(match.start() + 2, match.end() - 2);
				String replacement = "<a href=\"http://www.nationstates.net/region=" + region + "#rmb\">Regional Message Board</a>";
				text = match.replaceFirst(replacement);
			} else {
				break;
			}
		} while (true);
		
		return text;
	}

	public static String formatFullName(String nation, Connection conn, boolean fullName) throws SQLException {
		PreparedStatement statement = conn.prepareStatement("SELECT full_name, flag, alive, title from assembly.nation WHERE name = ?");
		statement.setString(1, sanitizeName(nation));
		ResultSet result = statement.executeQuery();
		if (result.next()) {
			//Dead, return just full title
			if (result.getByte(3) != 1) {
				return result.getString(1);
			} else {
				return "<a href=\"http://www.nationstates.net/nation=" + nation + "\"><img src=\"" + result.getString(2) + "\" class=\"miniflag\" alt=\"\" title=\"" + result.getString(1) + "\">" + (fullName ? result.getString(1) : result.getString(4)) + "</a>";
			}
		}
		return formatNationLink(nation);
	}

	public static String sanitizeName(String name) {
		return name.toLowerCase().replaceAll(" ", "_");
	}

	public static String formatName(String name) {
		return WordUtils.capitalizeFully(name.replaceAll("_", " "));
	}

	public static String formatNationLink(String nation) {
		return "<a href=\"http://www.nationstates.net/nation=" + nation + "\">" + formatName(nation) + "</a>";
	}

	public static String getNationFlag(String nation, Connection conn) throws SQLException {
		PreparedStatement statement = conn.prepareStatement("SELECT flag, alive from assembly.nation WHERE name = ?");
		statement.setString(1, sanitizeName(nation));
		ResultSet result = statement.executeQuery();
		if (result.next()) {
			if (result.getByte(2) == 1) {
				return result.getString(1);
			}
			return "http://www.nationstates.net/images/flags/exnation.png";
		}
		return "http://www.nationstates.net/images/flags/default.jpg";
	}

	public static String getPostValue(Http.Request request, String property) {
		try {
			return request.body().asFormUrlEncoded().get(property)[0];
		} catch (Exception e) {
			return null;
		}
	}

	public static Result validateRequest(Http.Request request, Http.Response response, NationStates api, DatabaseAccess access) {
		String authToken = Utils.getPostValue(request, "auth-token");
		String nation = Utils.getPostValue(request, "nation");
		String auth = Utils.getPostValue(request, "auth");
		String reason = "UNKNOWN NATION ID";
		try {
			final int nationId = access.getNationIdCache().get(sanitizeName(nation));
			if (nation != null && nationId != -1) {
				if (authToken != null && access.isValidAuthToken(nationId, authToken)) {
					response.setHeader("Access-Control-Expose-Headers", "X-Auth-Token");
					response.setHeader("X-Auth-Token", authToken);
					return null;
				} else {
					reason = "INVALID AUTH TOKEN";
				}
				if (auth != null && api.verifyNation(nation, auth)) {
					response.setHeader("Access-Control-Expose-Headers", "X-Auth-Token");
					response.setHeader("X-Auth-Token", access.generateAuthToken(nationId));
					return null;
				} else {
					reason = "INVALID NS AUTH CODE";
				}
			}
		} catch (ExecutionException e) {
			Logger.error("Unable to validate request", e);
			reason = "UNKNOWN REASON";
		}
		Utils.handleDefaultPostHeaders(request, response);
		return Results.unauthorized(reason);
	}
}
