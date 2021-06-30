package handler;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

public class RequestHandler implements HttpHandler {
	private DBHandler dbhandler;

	public RequestHandler(DBHandler dbhandler){
		this.dbhandler = dbhandler;
	}

	@Override
	public void handle(HttpExchange ex){
	}
}
