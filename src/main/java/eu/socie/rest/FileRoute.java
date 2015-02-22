package eu.socie.rest;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;

import com.jetdrone.vertx.yoke.core.YokeFileUpload;
import com.jetdrone.vertx.yoke.middleware.YokeRequest;
import com.jetdrone.vertx.yoke.middleware.YokeResponse;

public class FileRoute extends Route {

	public FileRoute(String path, Vertx vertx) {
		super(path, vertx);

		get(r -> handleFileGet(r));
		post(r -> handleFilePost(r));
	}

	private void handleFilePost(YokeRequest request) {
	
		if (request.files() != null) {
			request.files().forEach((key,upload) -> processFile(request, key,upload));
		}

	}
	
	private void processFile(YokeRequest request, String key, YokeFileUpload upload) {
		// TODO consider reading files in chunks, to enable large file support
		vertx.fileSystem().readFile(upload.path(), result -> handleFileRead(request, upload, result));
	}
	
	private void handleFileRead(YokeRequest request, YokeFileUpload upload, AsyncResult<Buffer> fileBuffer){
		mongoHelper.sendStoreFile(upload.filename(), upload.contentType(), fileBuffer.result(), r -> printId(request, r));
		
		upload.delete();
	}
	
	private void printId(YokeRequest request, AsyncResult<Message<String>> fileStoreResult){
		YokeResponse response = request.response().setChunked(true);
		
		if (fileStoreResult.succeeded()) {
			
			response.write(fileStoreResult.result().body());
		}
		
		response.end();
	}

	private void handleFileGet(YokeRequest request) {
		String fileId = request.getParameter("fileId");
		JsonObject fileMsg = new JsonObject();
		
		fileMsg.putString("_id", fileId);

		mongoHelper.sendGetFile(fileMsg,
				fileRequest -> handleFileResponse(fileRequest, request));

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
