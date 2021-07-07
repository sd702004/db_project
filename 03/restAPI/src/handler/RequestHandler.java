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
import java.util.ArrayList;
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
		func_map.put("displaychannel", (HttpExchange ex, JSONObject data) -> {return displayChannel(ex, data);});
		func_map.put("channelsubscribe", (HttpExchange ex, JSONObject data) -> {return subscribeChannel(ex, data);});
		func_map.put("addcomment", (HttpExchange ex, JSONObject data) -> {return addComment(ex, data);});
		func_map.put("removecomment", (HttpExchange ex, JSONObject data) -> {return removeComment(ex, data);});
		func_map.put("scorevideo", (HttpExchange ex, JSONObject data) -> {return scoreVideo(ex, data);});
		func_map.put("scorecomment", (HttpExchange ex, JSONObject data) -> {return scoreComment(ex, data);});
		func_map.put("newplaylist", (HttpExchange ex, JSONObject data) -> {return addPlaylist(ex, data);});
		func_map.put("vidplaylist", (HttpExchange ex, JSONObject data) -> {return videoPlaylist(ex, data);});
		func_map.put("mngplaylist", (HttpExchange ex, JSONObject data) -> {return managePlaylist(ex, data);});
		func_map.put("displayplaylist", (HttpExchange ex, JSONObject data) -> {return displayPlaylist(ex, data);});
		func_map.put("displayuserinfo", (HttpExchange ex, JSONObject data) -> {return displayUserInfo(ex, data);});
		func_map.put("getvideo", (HttpExchange ex, JSONObject data) -> {return getVideo(ex, data);}); // ++video_watch
		func_map.put("search", (HttpExchange ex, JSONObject data) -> {return search(ex, data);}); // ++video_watch
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

			if(!sql_result.next()){
				conn.close();
				return createFailedResult("invalid username/password");
			}

			userid = sql_result.getInt(1);

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
			if (!sql_result.next()){
				conn.close();
				return 0;
			}

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

	private RequestResult addPlaylist(HttpExchange ex, JSONObject data){
		int userid = getUserID(ex);

		if (userid == 0)
			return createFailedResult("access denied", HttpURLConnection.HTTP_FORBIDDEN);

		String listname;

		try {
			listname = data.getString("listname").trim();
		} catch (JSONException e){
			return createFailedResult("missing parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		if (listname.isEmpty())
			return createFailedResult("listname is empty");

		if (listname.length() > 50)
			return createFailedResult("listname length is larger than 50");

		Connection conn = null;
		boolean sql_error = false;

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			// check if playlist exists
			stmt = conn.prepareStatement("SELECT COUNT(*) FROM playlist WHERE userid=? AND name=?");
			stmt.setInt(1, userid);
			stmt.setString(2, listname);
			sql_result = stmt.executeQuery();
			sql_result.next();

			if (sql_result.getInt(1) == 1){
				conn.close();
				return createFailedResult(String.format("'%s' already exists", listname));
			}

			// create playlist
			stmt = conn.prepareStatement("INSERT INTO playlist(userid,name) VALUES(?,?)");
			stmt.setInt(1, userid);
			stmt.setString(2, listname);
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
		result.response.put("msg", "new list created");

		return result;
	}

	private RequestResult videoPlaylist(HttpExchange ex, JSONObject data){
		int userid = getUserID(ex);

		if (userid == 0)
			return createFailedResult("access denied", HttpURLConnection.HTTP_FORBIDDEN);

		String method;
		int videoid;
		int listid;

		try {
			listid = data.getInt("listid");
			videoid = data.getInt("videoid");
			method = data.getString("method").trim().toLowerCase();
		} catch (JSONException e){
			return createFailedResult("missing/invalid parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		if (method.isEmpty())
			return createFailedResult("method is empty");

		if (!method.equals("add") && !method.equals("remove"))
			return createFailedResult("invalid method. must be 'add' or 'remove'");

		if (videoid < 1)
			return createFailedResult("invalid videoid");

		if (listid < 1)
			return createFailedResult("invalid listid");

		Connection conn = null;
		boolean sql_error = false;

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			// check videoid
			stmt = conn.prepareStatement("SELECT COUNT(*) FROM video WHERE video_id=?");
			stmt.setInt(1, videoid);
			sql_result = stmt.executeQuery();
			sql_result.next();

			if (sql_result.getInt(1) != 1){
				conn.close();
				return createFailedResult(String.format("video #%d doesn't exist", videoid));
			}

			// check playlist
			stmt = conn.prepareStatement("SELECT COUNT(*) FROM playlist WHERE userid=? AND list_id=?");
			stmt.setInt(1, userid);
			stmt.setInt(2, listid);
			sql_result = stmt.executeQuery();
			sql_result.next();

			if (sql_result.getInt(1) != 1){
				conn.close();
				return createFailedResult(String.format("list #%d doesn't exist", listid));
			}

			// check if video is in the playlist
			stmt = conn.prepareStatement("SELECT COUNT(*) FROM playlist_video WHERE video_id=? AND list_id=?");
			stmt.setInt(1, videoid);
			stmt.setInt(2, listid);
			sql_result = stmt.executeQuery();
			sql_result.next();

			boolean in_playlist = (sql_result.getInt(1) == 1);

			//  add video to playlist / delete video fram playlist
			if (method.equals("add")){
				if (in_playlist){
					conn.close();
					return createFailedResult(String.format("video #%d is already in playlist #%d", videoid, listid));
				}

				stmt = conn.prepareStatement("INSERT INTO playlist_video(video_id,list_id) VALUES(?,?)");
				stmt.setInt(1, videoid);
				stmt.setInt(2, listid);
				stmt.executeUpdate();
			} else {
				if (!in_playlist){
					conn.close();
					return createFailedResult(String.format("video #%d isn't in playlist #%d", videoid, listid));
				}

				stmt = conn.prepareStatement("DELETE FROM playlist_video WHERE (video_id,list_id)=(?,?)");
				stmt.setInt(1, videoid);
				stmt.setInt(2, listid);
				stmt.executeUpdate();
			}

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
		String msg = (method.equals("add"))? "video has been added to playlist" : "video has been removed from playlist";
		result.response.put("msg", msg);

		return result;
	}

	private RequestResult managePlaylist(HttpExchange ex, JSONObject data){
		int userid = getUserID(ex);

		if (userid == 0)
			return createFailedResult("access denied", HttpURLConnection.HTTP_FORBIDDEN);

		String method;
		int listid;
		boolean visible = false;

		try {
			listid = data.getInt("listid");
			method = data.getString("method").trim().toLowerCase();

			if (method.equals("change"))
				visible = data.getBoolean("public");
		} catch (JSONException e){
			return createFailedResult("missing/invalid parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		if (method.isEmpty())
			return createFailedResult("method is empty");

		if (!method.equals("change") && !method.equals("remove"))
			return createFailedResult("invalid method. must be 'change' or 'remove'");

		if (listid < 1)
			return createFailedResult("invalid listid");

		Connection conn = null;
		boolean sql_error = false;

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			// check playlist
			stmt = conn.prepareStatement("SELECT COUNT(*) FROM playlist WHERE userid=? AND list_id=?");
			stmt.setInt(1, userid);
			stmt.setInt(2, listid);
			sql_result = stmt.executeQuery();
			sql_result.next();

			if (sql_result.getInt(1) != 1){
				conn.close();
				return createFailedResult(String.format("list #%d doesn't exist", listid));
			}

			// change/change playlist
			if (method.equals("change")){
				stmt = conn.prepareStatement("UPDATE playlist SET is_public=? WHERE (userid,list_id) = (?,?)");
				stmt.setBoolean(1, visible);
				stmt.setInt(2, userid);
				stmt.setInt(3, listid);
			} else {
				stmt = conn.prepareStatement("SELECT name FROM playlist WHERE userid=? AND list_id=?");
				stmt.setInt(1, userid);
				stmt.setInt(2, listid);
				sql_result = stmt.executeQuery();
				sql_result.next();

				if (sql_result.getString(1).equals("watch_later")){
					conn.close();
					return createFailedResult("'watch_later' playlist cannot be removed");
				}

				stmt = conn.prepareStatement("DELETE FROM playlist WHERE (userid,list_id) = (?,?)");
				stmt.setInt(1, userid);
				stmt.setInt(2, listid);
			}

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

		String msg;

		if (method.equals("remove"))
			msg = "playlist has been removed";
		else
			msg = String.format("playlist has been changed to %s", visible? "public":"private");

		result.response.put("msg", msg);

		return result;
	}

	private RequestResult displayPlaylist(HttpExchange ex, JSONObject data){
		int userid = getUserID(ex);
		int listid;

		try {
			listid = data.getInt("listid");

			if (listid < 1)
				return createFailedResult("invalid listid");
		} catch (JSONException e){
			return createFailedResult("missing/invalid parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		Connection conn = null;
		boolean sql_error = false;

		RequestResult result = new RequestResult();
		result.response = new JSONObject();
		result.response.put("result", true);

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			// check playlist
			stmt = conn.prepareStatement("SELECT userid, is_public FROM playlist WHERE list_id=?");
			stmt.setInt(1, listid);
			sql_result = stmt.executeQuery();

			if (!sql_result.next()){
				conn.close();
				return createFailedResult("playlist not found");
			}

			if ( (sql_result.getInt(1) != userid) && !sql_result.getBoolean(2)){
				conn.close();
				return createFailedResult("playlist isn't public");
			}

			// get list info
			stmt = conn.prepareStatement("SELECT name, username FROM playlist"
				+ " INNER JOIN users USING(userid) WHERE list_id=?");

			stmt.setInt(1, listid);
			sql_result = stmt.executeQuery();
			sql_result.next();

			JSONObject list_info = new JSONObject();
			list_info.put("list_name", sql_result.getString(1));
			list_info.put("created_by", sql_result.getString(2));
			result.response.put("list_info", list_info);

			// get list videos
			stmt = conn.prepareStatement("SELECT video_id, username, name FROM playlist_video"
				+ " INNER JOIN video USING(video_id)"
				+ " INNER JOIN users USING(userid)"
				+ " WHERE list_id=?");

			stmt.setInt(1, listid);
			sql_result = stmt.executeQuery();

			ArrayList<JSONObject> videos = new ArrayList<JSONObject>();
			int video_number = 0;

			while(sql_result.next()){
				JSONObject video = new JSONObject();
				video.put("videoid", sql_result.getInt(1));
				video.put("uploader", sql_result.getString(2));
				video.put("video_name", sql_result.getString(3));

				videos.add(video);
				++video_number;
			}

			result.response.put("videos", videos);
			result.response.put("video_count", video_number);

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

		return result;
	}

	private RequestResult displayUserInfo(HttpExchange ex, JSONObject data){
		int userid = getUserID(ex);
		int target_uid;

		try {
			target_uid = data.getInt("userid");

			if (target_uid < 1)
				return createFailedResult("invalid userid");
		} catch (JSONException e){
			return createFailedResult("missing/invalid parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		Connection conn = null;
		boolean sql_error = false;

		RequestResult result = new RequestResult();
		result.response = new JSONObject();
		result.response.put("result", true);

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;
			String query;

			// check user
			stmt = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE userid=?");
			stmt.setInt(1, target_uid);
			sql_result = stmt.executeQuery();
			sql_result.next();

			if (sql_result.getInt(1) != 1){
				conn.close();
				return createFailedResult(String.format("user #%d doesn't exist", target_uid));
			}

			boolean all_info = (userid == target_uid);

			// get user info
			stmt = conn.prepareStatement("SELECT username, reg_date, has_avatar FROM users WHERE userid=?");

			stmt.setInt(1, target_uid);
			sql_result = stmt.executeQuery();
			sql_result.next();

			result.response.put("username", sql_result.getString(1));
			result.response.put("register_date", sql_result.getString(2));

			if (sql_result.getBoolean(3))
				result.response.put("avatar", String.format("%s_%d.jpg", sql_result.getString(1), target_uid));
			else
				result.response.put("avatar", JSONObject.NULL);

			// get videos
			stmt = conn.prepareStatement("SELECT video_id, name, upload_date FROM video"
				+" INNER JOIN users USING(userid) WHERE userid=? ORDER BY upload_date DESC");
			stmt.setInt(1, target_uid);
			sql_result = stmt.executeQuery();

			ArrayList<JSONObject> videos = new ArrayList<JSONObject>();
			int video_number = 0;

			while(sql_result.next()){
				JSONObject video = new JSONObject();
				video.put("videoid", sql_result.getInt(1));
				video.put("video_name", sql_result.getString(2));
				video.put("upload_time", sql_result.getString(3));

				videos.add(video);
				++video_number;
			}

			result.response.put("videos", videos);
			result.response.put("video_count", video_number);

			// get playlists
			if (all_info){
				stmt = conn.prepareStatement("SELECT list_id, name, is_public FROM playlist WHERE userid=?");
			} else {
				stmt = conn.prepareStatement("SELECT list_id, name, is_public FROM playlist WHERE userid=? AND is_public=?");
				stmt.setBoolean(2, true);
			}

			stmt.setInt(1, target_uid);
			sql_result = stmt.executeQuery();

			ArrayList<JSONObject> playlists = new ArrayList<JSONObject>();
			int list_number = 0;

			while(sql_result.next()){
				JSONObject playlist = new JSONObject();
				playlist.put("playlist_id", sql_result.getInt(1));
				playlist.put("list_name", sql_result.getString(2));
				playlist.put("is_public", sql_result.getBoolean(3));

				playlists.add(playlist);
				++list_number;
			}

			result.response.put("playlists", playlists);
			result.response.put("playlist_count", list_number);

			// get channels
			stmt = conn.prepareStatement("SELECT channel_id, name FROM channel WHERE userid=?");
			stmt.setInt(1, target_uid);
			sql_result = stmt.executeQuery();

			ArrayList<JSONObject> channels = new ArrayList<JSONObject>();
			int channel_number = 0;

			while(sql_result.next()){
				JSONObject channel = new JSONObject();
				channel.put("channel_id", sql_result.getInt(1));
				channel.put("channel_name", sql_result.getString(2));

				channels.add(channel);
				++channel_number;
			}

			result.response.put("channels", channels);
			result.response.put("channel_count", channel_number);

			// get subscribed channels
			if (all_info){
				stmt = conn.prepareStatement("SELECT channel.channel_id, name FROM channel"
					+ " INNER JOIN channel_subscription USING(userid)"
					+ " WHERE userid=?");

				stmt.setInt(1, target_uid);
				sql_result = stmt.executeQuery();

				ArrayList<JSONObject> subchannels = new ArrayList<JSONObject>();

				while(sql_result.next()){
					JSONObject subchannel = new JSONObject();
					subchannel.put("channel_id", sql_result.getInt(1));
					subchannel.put("channel_name", sql_result.getString(2));

					subchannels.add(subchannel);
				}

				result.response.put("subscribed_channels", subchannels);
			}

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

		return result;
	}

	private RequestResult getVideo(HttpExchange ex, JSONObject data){
		int videoid;

		try {
			videoid = data.getInt("videoid");

			if (videoid < 1)
				return createFailedResult("invalid videoid");
		} catch (JSONException e){
			return createFailedResult("missing/invalid parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		Connection conn = null;
		boolean sql_error = false;

		RequestResult result = new RequestResult();
		result.response = new JSONObject();
		result.response.put("result", true);

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			// get video information
			stmt = conn.prepareStatement("SELECT name, filename, userid, username,"
				+ " description, duration, upload_date, total_watch FROM video"
				+ " INNER JOIN users USING (userid)"
				+ " WHERE video_id = ?");
			stmt.setInt(1, videoid);
			sql_result = stmt.executeQuery();

			if (!sql_result.next()){
				conn.close();
				return createFailedResult(String.format("video #%d doesn't exist", videoid));
			}

			result.response.put("videoname", sql_result.getString(1));
			result.response.put("filename", sql_result.getString(2));
			result.response.put("uploader_id", sql_result.getInt(3));
			result.response.put("uploader_username", sql_result.getString(4));
			result.response.put("description", sql_result.getString(5));
			result.response.put("video_duration", sql_result.getInt(6));
			result.response.put("upload_date", sql_result.getString(7));
			result.response.put("total_views", sql_result.getInt(8) + 1);

			// get likes/dislikes
			stmt = conn.prepareStatement("SELECT COUNT(*) FROM video_score WHERE video_id=? AND score=?::SCORE_T");
			stmt.setInt(1, videoid);
			stmt.setString(2, "like");
			sql_result = stmt.executeQuery();
			sql_result.next();
			result.response.put("likes", sql_result.getInt(1));

			stmt.setString(2, "dislike");
			sql_result = stmt.executeQuery();
			sql_result.next();
			result.response.put("dislikes", sql_result.getInt(1));

			// get comments
			stmt = conn.prepareStatement("SELECT comment_id, parent_id, username, comment, submit_date FROM comments"
				+ " INNER JOIN users USING(userid) WHERE video_id=? ORDER BY submit_date DESC");
			stmt.setInt(1, videoid);
			sql_result = stmt.executeQuery();

			ArrayList<JSONObject> comments = new ArrayList<JSONObject>();

			PreparedStatement local_stmt;

			local_stmt = conn.prepareStatement("SELECT COUNT(*) FROM comment_score"
				+ " WHERE comment_id=? AND score=?::SCORE_T");

			while(sql_result.next()){
				int comment_id = sql_result.getInt(1);
				JSONObject comment = new JSONObject();

				comment.put("comment_id", comment_id);
				comment.put("reply_to", sql_result.getInt(2));
				comment.put("sender", sql_result.getString(3));
				comment.put("comment", sql_result.getString(4));
				comment.put("date", sql_result.getString(5));

				ResultSet lsql_result;

				local_stmt.setInt(1, comment_id);

				local_stmt.setString(2, "like");
				lsql_result = local_stmt.executeQuery();
				lsql_result.next();
				comment.put("likes", lsql_result.getInt(1));

				local_stmt.setString(2, "dislike");
				lsql_result = local_stmt.executeQuery();
				lsql_result.next();
				comment.put("dislikes", lsql_result.getInt(1));

				comments.add(comment);
			}

			result.response.put("comments", comments);

			// add to watch number
			stmt = conn.prepareStatement("UPDATE video SET total_watch = total_watch+1 WHERE video_id=?");
			stmt.setInt(1, videoid);
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

		return result;
	}

	private RequestResult displayChannel(HttpExchange ex, JSONObject data){
		int channel_id;

		try {
			channel_id = data.getInt("channel_id");

			if (channel_id  < 1)
				return createFailedResult("invalid channel_id");
		} catch (JSONException e){
			return createFailedResult("missing/invalid parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		Connection conn = null;
		boolean sql_error = false;

		RequestResult result = new RequestResult();
		result.response = new JSONObject();
		result.response.put("result", true);

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			// get channel info
			stmt = conn.prepareStatement("SELECT name, username, description, creation_date, userid FROM channel"
				+ " INNER JOIN users USING(userid) WHERE channel_id=?");

			stmt.setInt(1, channel_id);
			sql_result = stmt.executeQuery();

			if (!sql_result.next()){
				conn.close();
				return createFailedResult(String.format("channel #%d doesn't exist", channel_id));
			}

			result.response.put("name", sql_result.getString(1));
			result.response.put("owner", sql_result.getString(2));
			result.response.put("description", sql_result.getString(3));
			result.response.put("creation_date", sql_result.getString(4));
			result.response.put("channel_picture", String.format("%d_%d.jpg", sql_result.getInt(5), channel_id));

			// get videos
			stmt = conn.prepareStatement("SELECT video_id, name FROM channel_video"
				+ " INNER JOIN video USING(video_id)"
				+ " INNER JOIN users USING(userid)"
				+ " WHERE channel_id=? ORDER BY upload_date DESC");

			stmt.setInt(1, channel_id);
			sql_result = stmt.executeQuery();

			int total_videos = 0;
			ArrayList<JSONObject> videos = new ArrayList<JSONObject>();

			while(sql_result.next()){
				JSONObject video = new JSONObject();
				video.put("video_id", sql_result.getInt(1));
				video.put("video_name", sql_result.getString(2));

				videos.add(video);
				++total_videos;
			}

			result.response.put("videos", videos);
			result.response.put("video_count", total_videos);

			// get subscribers
			stmt = conn.prepareStatement("SELECT userid, username FROM channel_subscription"
				+ " INNER JOIN users USING(userid)"
				+ " WHERE channel_id = ?");

			stmt.setInt(1, channel_id);
			sql_result = stmt.executeQuery();

			int total_subs = 0;
			ArrayList<JSONObject> subs = new ArrayList<JSONObject>();

			while(sql_result.next()){
				JSONObject sub = new JSONObject();
				sub.put("userid", sql_result.getInt(1));
				sub.put("username", sql_result.getString(2));

				subs.add(sub);
				++total_subs;
			}

			result.response.put("subscribers", subs);
			result.response.put("subscribers_count", total_subs);

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

		return result;
	}

	private RequestResult search(HttpExchange ex, JSONObject data){
		String keywords;

		try {
			keywords = data.getString("keywords").trim();

			if (keywords.isEmpty())
				return createFailedResult("empty keywords");
		} catch (JSONException e){
			return createFailedResult("missing/invalid parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		keywords = keywords.replaceAll(" ", " | ");


		Connection conn = null;
		boolean sql_error = false;

		RequestResult result = new RequestResult();
		result.response = new JSONObject();
		result.response.put("result", true);

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			// search videos
			stmt = conn.prepareStatement("SELECT video_id, name, username"
			+ " FROM video INNER JOIN users USING(userid)"
			+ " WHERE name @@ to_tsquery(?) LIMIT 10");

			stmt.setString(1, keywords);
			sql_result = stmt.executeQuery();

			int total_videos = 0;
			ArrayList<JSONObject> videos = new ArrayList<JSONObject>();

			while (sql_result.next()){
				JSONObject video = new JSONObject();
				video.put("video_id", sql_result.getInt(1));
				video.put("video_name", sql_result.getString(2));
				video.put("uploader", sql_result.getString(3));

				videos.add(video);
				++total_videos;
			}

			result.response.put("videos", videos);
			result.response.put("video_count", total_videos);

			// search channels
			stmt = conn.prepareStatement("SELECT channel_id, name, description, username FROM channel"
				+ " INNER JOIN users USING(userid)"
				+ " WHERE name @@ to_tsquery(?) LIMIT 10");

			stmt.setString(1, keywords);
			sql_result = stmt.executeQuery();

			int total_channels = 0;
			ArrayList<JSONObject> channels = new ArrayList<JSONObject>();

			while (sql_result.next()){
				JSONObject channel = new JSONObject();
				channel.put("channel_id", sql_result.getInt(1));
				channel.put("channel_name", sql_result.getString(2));
				channel.put("description", sql_result.getString(3));
				channel.put("owner", sql_result.getString(4));

				channels.add(channel);
				++total_channels;
			}

			result.response.put("channels", channels);
			result.response.put("channel_count", total_channels);

			// public playlists
			stmt = conn.prepareStatement("SELECT list_id, name, username FROM playlist"
				+ " INNER JOIN users USING(userid)"
				+ " WHERE is_public=? AND name @@ to_tsquery(?) LIMIT 10");

			stmt.setBoolean(1, true);
			stmt.setString(2, keywords);
			sql_result = stmt.executeQuery();

			int total_lists = 0;
			ArrayList<JSONObject> plists = new ArrayList<JSONObject>();

			while (sql_result.next()){
				JSONObject plist = new JSONObject();
				plist.put("list_id", sql_result.getInt(1));
				plist.put("playlist_name", sql_result.getString(2));
				plist.put("owner", sql_result.getString(3));

				plists.add(plist);
				++total_lists;
			}

			result.response.put("playlists", plists);
			result.response.put("playlist_count", total_lists);

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
			return createFailedResult("keywords format is not valid");
		}

		return result;
	}

	private RequestResult subscribeChannel(HttpExchange ex, JSONObject data){
		int userid = getUserID(ex);

		if (userid == 0)
			return createFailedResult("access denied", HttpURLConnection.HTTP_FORBIDDEN);

		int channel_id;
		boolean subscribe;

		try {
			channel_id = data.getInt("channel_id");

			if (channel_id < 1)
				return createFailedResult("invalid channel_id");

			subscribe = data.getBoolean("subscribe");
		} catch (JSONException e){
			return createFailedResult("missing/invalid parameters", HttpURLConnection.HTTP_BAD_REQUEST);
		}

		Connection conn = null;
		boolean sql_error = false;

		try {
			conn = dbhandler.getConnection();
			PreparedStatement stmt;
			ResultSet sql_result;

			// check channel
			stmt = conn.prepareStatement("SELECT COUNT(*) FROM channel WHERE channel_id = ?");
			stmt.setInt(1, channel_id);
			sql_result = stmt.executeQuery();
			sql_result.next();

			if (sql_result.getInt(1) == 0){
				conn.close();
				return createFailedResult("channel not found");
			}

			// check if user is subscribed
			stmt = conn.prepareStatement("SELECT COUNT(*) FROM channel_subscription WHERE (userid,channel_id) = (?,?)");
			stmt.setInt(1, userid);
			stmt.setInt(2, channel_id);
			sql_result = stmt.executeQuery();
			sql_result.next();

			boolean is_subscribed = (sql_result.getInt(1) == 1);

			if (subscribe && is_subscribed){
				conn.close();
				return createFailedResult("you are already subscribed to this channel");
			}

			if (!subscribe && !is_subscribed){
				conn.close();
				return createFailedResult("you're not subscribed to this channel");
			}

			if (subscribe)
				stmt = conn.prepareStatement("INSERT INTO channel_subscription(userid, channel_id) VALUES(?,?)");
			else
				stmt = conn.prepareStatement("DELETE FROM channel_subscription WHERE (userid, channel_id) = (?,?)");

			stmt.setInt(1, userid);
			stmt.setInt(2, channel_id);

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

		if (subscribe)
			result.response.put("msg", "user has been subscribed");
		else
			result.response.put("msg", "user has been unsubscribed");

		return result;
	}
}
