package handler;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.io.*;
import java.net.HttpURLConnection;
import org.json.JSONObject;
import org.json.JSONException;

public class RequestHandler implements HttpHandler {
	private DBHandler dbhandler;
	private HashMap<String, BiFunction<HttpExchange, JSONObject, RequestResult>> func_map;

	public RequestHandler(DBHandler dbhandler){
		this.dbhandler = dbhandler;
		initRequestHashMap();
	}

	@Override
	public void handle(HttpExchange ex){
		String method = ex.getRequestURI().getQuery().toLowerCase();

		RequestResult result;
		BiFunction<HttpExchange, JSONObject, RequestResult> handler_function = func_map.get(method);

		if (handler_function == null){
			result = new RequestResult();
			result.response_code = HttpURLConnection.HTTP_NOT_FOUND;
			result.response = new JSONObject();
			result.response.put("result", false);
			result.response.put("error", "invalid method");
		} else {
			JSONObject json = getReqParams(ex);

			if (json != null){
				result = handler_function.apply(ex, json);
			} else {
				result = new RequestResult();
				result.response_code = HttpURLConnection.HTTP_BAD_REQUEST;
				result.response = new JSONObject();
				result.response.put("result", false);
				result.response.put("error", "invalid json");
			}
		}

		String out_response = result.response.toString();

		try {
			ex.sendResponseHeaders(result.response_code, out_response.getBytes("UTF-8").length);
			OutputStream output = ex.getResponseBody();
			output.write(out_response.getBytes("UTF-8"));
			output.close();
		} catch (IOException e){
			System.err.printf("sending data failed [%s]\n", e.getMessage());
		}

		/*
		try{
			Connection conn = dbhandler.getConnection();
			conn.close();
		} catch (Exception e){
			System.out.println(e);
		}
		*/

		ex.close();
	}

	private JSONObject getReqParams(HttpExchange ex){
		String request_data = new String();

		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(ex.getRequestBody(), "UTF-8"));
			for (String line; (line = reader.readLine()) != null;)
				request_data += line;

			reader.close();
		} catch (IOException e){
			return null;
		}

		JSONObject json = null;

		try {
			json = new JSONObject(request_data);
		} catch (JSONException e){
			return null;
		}

		return json;
	}

	private void initRequestHashMap(){
		func_map = new HashMap<String, BiFunction<HttpExchange, JSONObject, RequestResult>>();
		func_map.put("register", (HttpExchange ex, JSONObject data) -> {return userRegister(ex, data);});
	}

	private RequestResult createFailedResult(String error){
		RequestResult result = new RequestResult();

		result.response_code = HttpURLConnection.HTTP_OK;
		result.response = new JSONObject();
		result.response.put("result", false);
		result.response.put("error", error);

		return result;
	}

	private RequestResult createFailedResult(String error, int response_code){
		RequestResult result = createFailedResult(error);
		result.response_code = response_code;
		return result;
	}

	private RequestResult userRegister(HttpExchange ex, JSONObject data){
		String username;
		String password;
		String email;

		try {
			username = data.getString("username").trim();
			password = data.getString("password");
			email = data.getString("email").trim();
		} catch (JSONException e){
			return createFailedResult("missing parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		if (username.isEmpty() || username.length() < 4)
			return createFailedResult("username length is less than 4 characters");

		if (password.isEmpty() || password.length() < 6)
			return createFailedResult("password length is less than 6 characters");

		Connection conn = null;
		boolean sql_error = false;

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			// check duplicate username
			stmt = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE username = ?");
			stmt.setString(1, username);
			sql_result = stmt.executeQuery();
			sql_result.next();

			if (sql_result.getInt(1) == 1){
				conn.close();
				return createFailedResult("username already exists");
			}

			// check duplicate email
			stmt = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE email = ?");
			stmt.setString(1, email);
			sql_result = stmt.executeQuery();
			sql_result.next();

			if (sql_result.getInt(1) == 1){
				conn.close();
				return createFailedResult("email already exists");
			}

			// validate email
			// (add later)

			// create new user
			// Note: CREATE EXTENSION IF NOT EXISTS pgcrypto;
			stmt = conn.prepareStatement("INSERT INTO users(username, password, email, reg_date) VALUES(?, crypt(?, gen_salt(?)), ?, now())");
			stmt.setString(1, username);
			stmt.setString(2, password);
			stmt.setString(3, "md5");
			stmt.setString(4, email);
			stmt.execute();
			conn.close();
		} catch (SQLException e){
			sql_error = true;

		}

		if (sql_error){
			if (conn != null){
				try {
					conn.close();
				} catch (SQLException e){
					// do nothing
				}
			}
			return createFailedResult("internal server error");
		}

		RequestResult result = new RequestResult();
		result.response = new JSONObject();

		result.response_code = HttpURLConnection.HTTP_OK;
		result.response.put("result", true);
		result.response.put("msg", "new user created");
		result.response.put("username", username);
		result.response.put("email", email);

		return result;
	}
}
