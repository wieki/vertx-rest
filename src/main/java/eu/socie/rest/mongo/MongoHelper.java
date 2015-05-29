/**
 * 
 */
package eu.socie.rest.mongo;


/**
 * This class aims to simplify sending events to the database
 * 
 * @author Bram Wiekens
 * 
 *
 */
public class MongoHelper {

/*	Vertx vertx; 

	public MongoHelper(Vertx vertx) {
		this.vertx = vertx;
	}

	public void sendCreateOrUpdate(JsonObject create,
			Handler<AsyncResult<Message<JsonObject>>> handler) {
		vertx.eventBus().sendWithTimeout(AsyncMongoPersistor.EVENT_DB_CREATE,
				create, Route.TIMEOUT, handler);
	}

	public void sendDelete(JsonObject delete,
			Handler<AsyncResult<Message<Integer>>> handler) {

		vertx.eventBus().sendWithTimeout(AsyncMongoPersistor.EVENT_DB_DELETE,
				delete, Route.TIMEOUT, handler);

	}
	
	public void sendUpdate(JsonObject update, Handler<AsyncResult<Message<JsonObject>>> handler) {
		vertx.eventBus().sendWithTimeout(AsyncMongoPersistor.EVENT_DB_UPDATE,
				update, Route.TIMEOUT, handler);
	}

	public void sendFind(JsonObject find,
			Handler<AsyncResult<Message<JsonArray>>> handler) {

		vertx.eventBus().sendWithTimeout(AsyncMongoPersistor.EVENT_DB_FIND,
				find, Route.TIMEOUT, handler);
	}
	
	public void sendCount(JsonObject count,
			Handler<AsyncResult<Message<Long>>> handler) {

		vertx.eventBus().sendWithTimeout(AsyncMongoPersistor.EVENT_DB_COUNT,
				count, Route.TIMEOUT, handler);
	}
	
	public void sendCheckFile(JsonObject fileMsg, Handler<AsyncResult<Message<JsonObject>>> handler) {
		vertx.eventBus().sendWithTimeout(
				AsyncMongoPersistor.EVENT_DB_CHECK_FILE,
				fileMsg,
				Route.TIMEOUT,
				handler);
	}
	
	public void sendGetFile(JsonObject fileMsg, Handler<AsyncResult<Message<Buffer>>> handler) {
		vertx.eventBus().sendWithTimeout(
				AsyncMongoPersistor.EVENT_DB_GET_FILE,
				fileMsg,
				3 * Route.TIMEOUT,
				handler);
	}
	
	public void sendStoreFile(String fileName, String contentType, Buffer fileMsg, Handler<AsyncResult<Message<String>>> handler) {
		
		Buffer fileBuffer = MongoFileUtil.createFileBuffer(fileName, contentType, fileMsg);
		
		vertx.eventBus().sendWithTimeout(
				AsyncMongoPersistor.EVENT_DB_STORE_FILE,
				fileBuffer,
				Route.TIMEOUT,
				handler);
	}*/

}
