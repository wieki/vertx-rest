package eu.socie.rest;


import io.vertx.rxjava.core.Vertx;

public class FileRoute extends Route {

	public FileRoute(String path, String schema, Vertx vertx) {
		super(path, schema, vertx);
		
		init();
	}
	
	public FileRoute(String path, Vertx vertx) {
		super(path, vertx);
		
		init();
	}

	private void init() {
		//get(r -> handleFileGet(r));
		//post(r -> handleFilePost(r));
	}

/*	private void handleFilePost(RoutingContext context) {
		
		if (context.fileUploads()!= null) {
			context.fileUploads().forEach((upload) -> processFile(context, upload));
		
		}

	}

	private void processFile(RoutingContext context,
			FileUpload upload) {
		// FIXME consider reading files in chunks, to enable large file support
		
		vertx.fileSystem()
			.readFileObservable(upload.uploadedFileName()).flatMap(buffer -> )
		
		vertx.fileSystem().readFile(upload.uploadedFileName(),
				result -> handleFileRead(context, upload, result));
	}

	private void handleFileRead(RoutingContext request, FileUpload upload,
			Buffer fileBuffer) {
		
		mongoClient.
		mongoClient.sendStoreFile(upload.filename(), upload.contentType(),
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
	
	protected void doFileCheck(String fileId, Handler<AsyncResult<Message<JsonObject>>> handler){
		JsonObject fileMsg = new JsonObject();

		fileMsg.putString("_id", fileId);

		mongoClient.sendCheckFile(fileMsg, handler);
	}
	
	protected void doFileGet(YokeRequest request, String fileId, boolean isDownload){
		JsonObject fileMsg = new JsonObject();

		fileMsg.putString("_id", fileId);

		mongoClient.sendGetFile(fileMsg,
				fileRequest -> handleFileResponse(fileRequest, request, isDownload));
	}

	protected void handleFileResponse(AsyncResult<Message<Buffer>> fileResult,
			YokeRequest request, boolean isDownload) {
		if (fileResult.succeeded()) {
			String disposition = isDownload ? "attachment" : "inline";
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
	}*/

}
