package handler;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
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

			if (json != null || ex.getRequestMethod().equals("GET") ){
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
		func_map.put("login", (HttpExchange ex, JSONObject data) -> {return userLogin(ex, data);});
		func_map.put("logout", (HttpExchange ex, JSONObject data) -> {return userLogout(ex, data);});
		func_map.put("newvideo", (HttpExchange ex, JSONObject data) -> {return uploadVideo(ex, data);});
		func_map.put("deletevideo", (HttpExchange ex, JSONObject data) -> {return deleteVideo(ex, data);});
		func_map.put("newchannel", (HttpExchange ex, JSONObject data) -> {return createChannel(ex, data);});
		func_map.put("deletechannel", (HttpExchange ex, JSONObject data) -> {return deleteChannel(ex, data);});
		func_map.put("addcomment", (HttpExchange ex, JSONObject data) -> {return addComment(ex, data);});
		func_map.put("removecomment", (HttpExchange ex, JSONObject data) -> {return removeComment(ex, data);});
		func_map.put("scorevideo", (HttpExchange ex, JSONObject data) -> {return scoreVideo(ex, data);});
		func_map.put("scorecomment", (HttpExchange ex, JSONObject data) -> {return scoreComment(ex, data);});
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

	private RequestResult userLogin(HttpExchange ex, JSONObject data){
		String username;
		String password;

		try {
			username = data.getString("username").trim();
			password = data.getString("password");
		} catch (JSONException e){
			return createFailedResult("missing parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		if (username.isEmpty())
			return createFailedResult("username is empty");

		if (password.isEmpty())
			return createFailedResult("password is empty");

		Connection conn = null;
		boolean sql_error = false;

		int userid = 0;
		String token = null;

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			// check username & password
			stmt = conn.prepareStatement("SELECT userid FROM users WHERE username=? AND password=crypt(?, password)");
			stmt.setString(1, username);
			stmt.setString(2, password);
			sql_result = stmt.executeQuery();
			sql_result.next();

			userid = sql_result.getInt(1);

			if (userid == 0){
				conn.close();
				return createFailedResult("invalid username/password");
			}

			// generate token
			stmt = conn.prepareStatement("SELECT encode(digest(now()::text || random() || ?, ?), ?)");
			stmt.setInt(1, userid);
			stmt.setString(2, "sha224");
			stmt.setString(3, "hex");
			sql_result = stmt.executeQuery();
			sql_result.next();

			token = sql_result.getString(1);

			conn.setAutoCommit(false);

			stmt = conn.prepareStatement("DELETE FROM userlogin WHERE userid = ?");
			stmt.setInt(1, userid);
			stmt.execute();

			stmt = conn.prepareStatement("INSERT INTO userlogin values(?, decode(?, ?))");
			stmt.setInt(1, userid);
			stmt.setString(2, token);
			stmt.setString(3, "hex");
			stmt.execute();

			conn.commit();

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
		result.response.put("msg", "successful login");
		result.response.put("token", String.format("%d,%s", userid, token));

		return result;
	}

	private RequestResult userLogout(HttpExchange ex, JSONObject data){
		int userid = getUserID(ex);

		if (userid == 0)
			return createFailedResult("access denied", HttpURLConnection.HTTP_FORBIDDEN);

		Connection conn = null;
		boolean sql_error = false;

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			stmt = conn.prepareStatement("DELETE FROM userlogin WHERE userid=?");
			stmt.setInt(1, userid);
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
		result.response.put("msg", "successfully logged out");

		return result;
	}

	private int getUserID(HttpExchange ex){
		// returns userid if login or 0 if not login
		Headers headers = ex.getRequestHeaders();

		if (!headers.containsKey("X-token"))
			return 0;

		String[] token_list = headers.get("X-token").get(0).split(",");

		if (token_list.length != 2)
			return 0;

		int userid = 0;

		try {
			userid = Integer.parseInt(token_list[0]);

			if (userid < 1)
				return 0;
		} catch (NumberFormatException e){
			return 0;
		}

		String token = token_list[1];

		Connection conn = null;
		boolean sql_error = false;

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			stmt = conn.prepareStatement("SELECT userid FROM userlogin WHERE userid=? AND encode(token,?)=?");
			stmt.setInt(1, userid);
			stmt.setString(2, "hex");
			stmt.setString(3, token);

			sql_result = stmt.executeQuery();
			sql_result.next();
			userid = sql_result.getInt(1);

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

			return 0;
		}

		return userid;
	}

	private RequestResult uploadVideo(HttpExchange ex, JSONObject data){
		int userid = getUserID(ex);

		if (userid == 0)
			return createFailedResult("access denied", HttpURLConnection.HTTP_FORBIDDEN);

		String video_title;
		String file_name;
		String description;

		try {
			video_title = data.getString("title").trim();
			file_name = data.getString("filename");
			description = data.getString("description").trim();
		} catch (JSONException e){
			return createFailedResult("missing parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		if (video_title.isEmpty())
			return createFailedResult("title is empty");

		if (video_title.length() > 50)
			return createFailedResult("title length is larger than 50");

		if (file_name.isEmpty())
			return createFailedResult("filename is empty");

		if (description.isEmpty())
			return createFailedResult("description is empty");

		Connection conn = null;
		boolean sql_error = false;

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			String query = "INSERT INTO video(userid, name, filename, description, duration, upload_date)"
				+ "VALUES (?, ?, ?, ?, ?, now())";

			stmt = conn.prepareStatement(query);
			stmt.setInt(1, userid);
			stmt.setString(2, video_title);
			stmt.setString(3, file_name);
			stmt.setString(4, description);
			stmt.setInt(5, 60); // this is symbolic

			stmt.executeUpdate();
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
		result.response.put("msg", "video has been uploaded");

		return result;
	}

	private RequestResult deleteVideo(HttpExchange ex, JSONObject data){
		int userid = getUserID(ex);

		if (userid == 0)
			return createFailedResult("access denied", HttpURLConnection.HTTP_FORBIDDEN);

		int videoid = 0;

		try {
			videoid = data.getInt("videoid");

			if (videoid < 1)
				return createFailedResult("invalid videoid");
		} catch (JSONException e){
			return createFailedResult("missing/invalid parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		Connection conn = null;
		boolean sql_error = false;
		boolean delete_okay = false;

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			stmt = conn.prepareStatement("DELETE FROM video WHERE userid=? AND video_id=?");
			stmt.setInt(1, userid);
			stmt.setInt(2, videoid);

			delete_okay = (stmt.executeUpdate() != 0);
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
		result.response.put("result", delete_okay);

		if (delete_okay)
			result.response.put("msg", "video has been deleted");
		else
			result.response.put("msg", "video not found");

		return result;
	}

	private RequestResult createChannel(HttpExchange ex, JSONObject data){
		int userid = getUserID(ex);

		if (userid == 0)
			return createFailedResult("access denied", HttpURLConnection.HTTP_FORBIDDEN);

		String name;
		String description;
		String image_name; // image will rename to <userid>_<channelid>.jpg after upload

		try {
			name = data.getString("name").trim();
			description = data.getString("description").trim();
			image_name = data.getString("imagename").trim();
		} catch (JSONException e){
			return createFailedResult("missing parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		if (name.isEmpty())
			return createFailedResult("name is empty");

		if (name.length() > 50)
			return createFailedResult("channel name length is larger than 50");

		if (description.isEmpty())
			return createFailedResult("description is empty");

		if (image_name.isEmpty())
			return createFailedResult("image_name is empty");

		Connection conn = null;
		boolean sql_error = false;

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;

			stmt = conn.prepareStatement("INSERT INTO channel(userid, name, description, creation_date) VALUES (?,?,?,now())");
			stmt.setInt(1, userid);
			stmt.setString(2, name);
			stmt.setString(3, description);

			stmt.executeUpdate();
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
		result.response.put("msg", "channel has been created");

		return result;
	}

	private RequestResult deleteChannel(HttpExchange ex, JSONObject data){
		int userid = getUserID(ex);

		if (userid == 0)
			return createFailedResult("access denied", HttpURLConnection.HTTP_FORBIDDEN);

		int channel_id = 0;

		try {
			channel_id = data.getInt("channel_id");

			if (channel_id < 1)
				return createFailedResult("invalid channel_id");
		} catch (JSONException e){
			return createFailedResult("missing/invalid parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		Connection conn = null;
		boolean sql_error = false;
		boolean delete_okay = false;

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			stmt = conn.prepareStatement("DELETE FROM channel WHERE userid=? AND channel_id=?");
			stmt.setInt(1, userid);
			stmt.setInt(2, channel_id);

			delete_okay = (stmt.executeUpdate() != 0);
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
		result.response.put("result", delete_okay);

		if (delete_okay)
			result.response.put("msg", "channel has been deleted");
		else
			result.response.put("msg", "channel not found");

		return result;
	}

	private RequestResult addComment(HttpExchange ex, JSONObject data){
		int userid = getUserID(ex);

		if (userid == 0)
			return createFailedResult("access denied", HttpURLConnection.HTTP_FORBIDDEN);

		int video_id;
		int reply_to = 0;
		String comment;

		try {
			video_id = data.getInt("videoid");
			comment = data.getString("comment").trim();
		} catch (JSONException e){
			return createFailedResult("missing/invalid parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		if (video_id < 1)
			return createFailedResult("invalid video_id");

		if (comment.isEmpty())
			return createFailedResult("empty comment");

		if (data.has("reply_to")){
			try {
				reply_to = data.getInt("reply_to");

				if (reply_to < 0)
					return createFailedResult("invalid reply_to");
			} catch (JSONException e){
				return createFailedResult("invalid reply_to parameter", HttpURLConnection.HTTP_BAD_REQUEST);
			}
		}

		Connection conn = null;
		boolean sql_error = false;

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			// check videoid
			stmt = conn.prepareStatement("SELECT COUNT(*) FROM video WHERE video_id = ?");
			stmt.setInt(1, video_id);
			sql_result = stmt.executeQuery();
			sql_result.next();

			if (sql_result.getInt(1) != 1){
				conn.close();
				return createFailedResult(String.format("videoid %d doesn't exist", video_id));
			}

			// check reply_to
			if (reply_to > 0){
				stmt = conn.prepareStatement("SELECT COUNT(*) FROM comments WHERE comment_id = ?");
				stmt.setInt(1, reply_to);
				sql_result = stmt.executeQuery();
				sql_result.next();

				if (sql_result.getInt(1) != 1){
					conn.close();
					return createFailedResult(String.format("comment_id %d doesn't exist", reply_to));
				}
			}

			// submit
			String query = "INSERT INTO comments(userid, video_id, parent_id, comment, submit_date)"
				+ "VALUES (?, ?, ?, ?, now())";
			stmt = conn.prepareStatement(query);
			stmt.setInt(1, userid);
			stmt.setInt(2, video_id);

			if (reply_to > 0)
				stmt.setInt(3, reply_to);
			else
				stmt.setNull(3, java.sql.Types.NULL);
			stmt.setString(4, comment);

			stmt.executeUpdate();
			conn.close();
		} catch (SQLException e){
			sql_error = true;
			System.out.println(e);
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
		result.response.put("msg", "comment has been submited");

		return result;
	}

	private RequestResult removeComment(HttpExchange ex, JSONObject data){
		int userid = getUserID(ex);

		if (userid == 0)
			return createFailedResult("access denied", HttpURLConnection.HTTP_FORBIDDEN);

		int comment_id;

		try {
			comment_id = data.getInt("comment_id");

			if (comment_id < 1)
				return createFailedResult("invalid comment_id");
		} catch (JSONException e){
			return createFailedResult("missing/invalid parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		Connection conn = null;
		boolean sql_error = false;
		boolean delete_okay = false;

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			stmt = conn.prepareStatement("DELETE FROM comments WHERE userid=? AND comment_id=?");
			stmt.setInt(1, userid);
			stmt.setInt(2, comment_id);

			delete_okay = (stmt.executeUpdate() != 0);
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
		result.response.put("result", delete_okay);

		if (delete_okay)
			result.response.put("msg", "comment has been deleted");
		else
			result.response.put("msg", "comment not found");

		return result;
	}

	private RequestResult scoreVideo(HttpExchange ex, JSONObject data){
		int userid = getUserID(ex);

		if (userid == 0)
			return createFailedResult("access denied", HttpURLConnection.HTTP_FORBIDDEN);

		int videoid;
		String method;
		String score = null;

		try {
			videoid = data.getInt("videoid");
			method = data.getString("method").trim().toLowerCase();

			if (method.equals("add"))
				score = data.getString("score").trim().toLowerCase();

		} catch (JSONException e){
			return createFailedResult("missing parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		if (method.isEmpty())
			return createFailedResult("method is empty");

		if (!method.equals("add") && !method.equals("remove"))
			return createFailedResult("invalid method. must be 'add' or 'remove'");

		if (videoid < 1)
			return createFailedResult("invalid videoid");

		if (method.equals("add")){
			if (score.isEmpty())
				return createFailedResult("score is empty");

			if (!score.equals("like") && !score.equals("dislike"))
				return createFailedResult("invalid score. must be 'like' or 'dislike'");
		}

		Connection conn = null;
		boolean sql_error = false;
		boolean delete_okay = false;

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			// check videoid
			stmt = conn.prepareStatement("SELECT COUNT(*) FROM video WHERE video_id = ?");
			stmt.setInt(1, videoid);
			sql_result = stmt.executeQuery();
			sql_result.next();

			if (sql_result.getInt(1) != 1){
				conn.close();
				return createFailedResult(String.format("videoid %d doesn't exist", videoid));
			}

			// delete old score and submit new one
			conn.setAutoCommit(false);

			stmt = conn.prepareStatement("DELETE FROM video_score WHERE (userid,video_id) = (?,?)");
			stmt.setInt(1, userid);
			stmt.setInt(2, videoid);
			delete_okay = (stmt.executeUpdate() != 0);

			if (method.equals("add")){
				stmt = conn.prepareStatement("INSERT INTO video_score VALUES(?,?,?::score_t)");
				stmt.setInt(1, videoid);
				stmt.setInt(2, userid);
				stmt.setString(3, score);
				stmt.executeUpdate();
			}

			conn.commit();
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

		if (method.equals("add")){
			result.response.put("result", true);
			result.response.put("msg", "score has been submited");
		} else if (delete_okay){
			result.response.put("result", true);
			result.response.put("msg", "score has been deleted");
		} else {
			result.response.put("result", false);
			result.response.put("msg", "no score found");
		}

		return result;
	}

	private RequestResult scoreComment(HttpExchange ex, JSONObject data){
		int userid = getUserID(ex);

		if (userid == 0)
			return createFailedResult("access denied", HttpURLConnection.HTTP_FORBIDDEN);

		int comment_id;
		String method;
		String score = null;

		try {
			comment_id = data.getInt("comment_id");
			method = data.getString("method").trim().toLowerCase();

			if (method.equals("add"))
				score = data.getString("score").trim().toLowerCase();

		} catch (JSONException e){
			return createFailedResult("missing parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		if (method.isEmpty())
			return createFailedResult("method is empty");

		if (!method.equals("add") && !method.equals("remove"))
			return createFailedResult("invalid method. must be 'add' or 'remove'");

		if (comment_id < 1)
			return createFailedResult("invalid videoid");

		if (method.equals("add")){
			if (score.isEmpty())
				return createFailedResult("score is empty");

			if (!score.equals("like") && !score.equals("dislike"))
				return createFailedResult("invalid score. must be 'like' or 'dislike'");
		}

		Connection conn = null;
		boolean sql_error = false;
		boolean delete_okay = false;

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			// check videoid
			stmt = conn.prepareStatement("SELECT COUNT(*) FROM comments WHERE comment_id = ?");
			stmt.setInt(1, comment_id);
			sql_result = stmt.executeQuery();
			sql_result.next();

			if (sql_result.getInt(1) != 1){
				conn.close();
				return createFailedResult(String.format("comment %d doesn't exist", comment_id));
			}

			// delete old score and submit new one
			conn.setAutoCommit(false);

			stmt = conn.prepareStatement("DELETE FROM comment_score WHERE (userid,comment_id) = (?,?)");
			stmt.setInt(1, userid);
			stmt.setInt(2, comment_id);
			delete_okay = (stmt.executeUpdate() != 0);

			if (method.equals("add")){
				stmt = conn.prepareStatement("INSERT INTO comment_score VALUES(?,?,?::score_t)");
				stmt.setInt(1, comment_id);
				stmt.setInt(2, userid);
				stmt.setString(3, score);
				stmt.executeUpdate();
			}

			conn.commit();
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

		if (method.equals("add")){
			result.response.put("result", true);
			result.response.put("msg", "score has been submited");
		} else if (delete_okay){
			result.response.put("result", true);
			result.response.put("msg", "score has been deleted");
		} else {
			result.response.put("result", false);
			result.response.put("msg", "no score found");
		}

		return result;
	}
}
