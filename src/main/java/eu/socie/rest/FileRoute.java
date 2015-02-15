package eu.socie.rest;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;

import com.jetdrone.vertx.yoke.middleware.YokeRequest;

import eu.socie.mongo_async_persistor.AsyncMongoPersistor;
import eu.socie.mongo_async_persistor.util.MongoUtil;

public class FileRoute extends Route {

	EventBus eventBus;

	public FileRoute(String path, Vertx vertx) {
		super(path,vertx);

		this.eventBus = vertx.eventBus();

		get((r) -> handleFile(r));
	}

	private void handleFile(YokeRequest request) {
		String fileId = request.getParameter("fileId");
		JsonObject id = MongoUtil.createIdReference(fileId);

		JsonObject fileMsg = new JsonObject();
		fileMsg.putObject("_id", id);

		eventBus.sendWithTimeout(
				AsyncMongoPersistor.EVENT_DB_GET_FILE,
				fileMsg,
				2000,
				((AsyncResult<Message<Buffer>> fr) -> handleFileResponse(fr,
						request)));

	}

	private void handleFileResponse(AsyncResult<Message<Buffer>> fileResult,
			YokeRequest request) {
		if (fileResult.succeeded()) {

			Message<Buffer> msg = fileResult.result();
			request.response().setChunked(true).write(msg.body()).end();
		} else {
			String errMsg = fileResult.cause().getMessage();
			request.response().setStatusCode(Route.ERROR_CLIENT_NOT_FOUND)
					.end(errMsg);
		}
	}

}
