package eu.socie.rest;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;

import com.jetdrone.vertx.yoke.core.YokeFileUpload;
import com.jetdrone.vertx.yoke.middleware.YokeRequest;
import com.jetdrone.vertx.yoke.middleware.YokeResponse;

import eu.socie.mongo_async_persistor.util.MongoFileUtil;

public class FileRoute extends Route {

	public FileRoute(String path, Vertx vertx) {
		super(path, vertx);

		get(r -> handleFileGet(r));
		post(r -> handleFilePost(r));
	}

	private void handleFilePost(YokeRequest request) {

		if (request.files() != null) {
			request.files().forEach(
					(key, upload) -> processFile(request, key, upload));
		}

	}

	private void processFile(YokeRequest request, String key,
			YokeFileUpload upload) {
		// TODO consider reading files in chunks, to enable large file support
		vertx.fileSystem().readFile(upload.path(),
				result -> handleFileRead(request, upload, result));
	}

	private void handleFileRead(YokeRequest request, YokeFileUpload upload,
			AsyncResult<Buffer> fileBuffer) {
		mongoHelper.sendStoreFile(upload.filename(), upload.contentType(),
				fileBuffer.result(), r -> handleFileStoreResult(request, r));

		upload.delete();
	}

	protected void handleFileStoreResult(YokeRequest request,
			AsyncResult<Message<String>> fileStoreResult) {
		YokeResponse response = request.response().setChunked(true);

		if (fileStoreResult.succeeded()) {
			String id = fileStoreResult.result().body();
			
			response.headers().add("location", String.format("%s/%s", getBindPath(), id));
			response.setStatusCode(Route.SUCCESS_CREATED)
			.write(fileStoreResult.result().body());
		}

		response.end();
	}

	private boolean checkDownload(String download){
		boolean isDownload = false;
		
		if (download != null) {
			if (download.equalsIgnoreCase("true") || download.equalsIgnoreCase("false")){
				isDownload = new Boolean(download);
			}
		}
		
		return isDownload;
	}
	
	private void handleFileGet(YokeRequest request) {
		String fileId = request.getParameter("fileId");
		String download =	request.getParameter("download");
		final boolean isDownload = checkDownload(download);

		doFileGet(request, fileId, isDownload);
	}
	
	protected void doFileGet(YokeRequest request, String fileId, boolean isDownload){
		JsonObject fileMsg = new JsonObject();

		fileMsg.putString("_id", fileId);

		mongoHelper.sendGetFile(fileMsg,
				fileRequest -> handleFileResponse(fileRequest, request, isDownload));
	}

	private void handleFileResponse(AsyncResult<Message<Buffer>> fileResult,
			YokeRequest request, boolean isDownload) {
		if (fileResult.succeeded()) {
			String disposition = isDownload ? "attachtment" : "inline";
			YokeResponse response = request.response().setChunked(true);

			Message<Buffer> msg = fileResult.result();

			Buffer fileBuffer = msg.body();

			String filename = MongoFileUtil.getFilenameFromBuffer(fileBuffer);
			String contentType = MongoFileUtil
					.getContentFileFromBuffer(fileBuffer);

			response.headers().add("Content-Disposition",
					String.format("%s; filename=\"%s\"", disposition, filename));

			response.setContentType(contentType);
			
			response.write(MongoFileUtil.getFileContentsFromBuffer(fileBuffer));

			response.end();
		} else {
			String errMsg = fileResult.cause().getMessage();
			request.response().setStatusCode(Route.ERROR_CLIENT_NOT_FOUND)
					.end(errMsg);
		}
	}

}
