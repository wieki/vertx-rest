/**
 * 
 */
package eu.socie.rest.mongo;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import eu.socie.mongo_async_persistor.AsyncMongoPersistor;
import eu.socie.mongo_async_persistor.util.MongoFileUtil;
import eu.socie.rest.Route;

/**
 * This class aims to simplify sending events to the database
 * 
 * @author Bram Wiekens
 * 
 *
 */
public class MongoHelper {

	Vertx vertx;

	public MongoHelper(Vertx vertx) {
		this.vertx = vertx;
	}

	public void sendCreate(JsonObject create,
			Handler<AsyncResult<Message<JsonObject>>> handler) {
		vertx.eventBus().sendWithTimeout(AsyncMongoPersistor.EVENT_DB_CREATE,
				create, Route.TIMEOUT, handler);
	}

	public void sendDelete(JsonObject delete,
			Handler<AsyncResult<Message<Integer>>> handler) {

		vertx.eventBus().sendWithTimeout(AsyncMongoPersistor.EVENT_DB_DELETE,
				delete, Route.TIMEOUT, handler);

	}

	public void sendFind(JsonObject find,
			Handler<AsyncResult<Message<JsonArray>>> handler) {

		vertx.eventBus().sendWithTimeout(AsyncMongoPersistor.EVENT_DB_FIND,
				find, Route.TIMEOUT, handler);
	}
	
	public void sendGetFile(JsonObject fileMsg, Handler<AsyncResult<Message<Buffer>>> handler) {
		vertx.eventBus().sendWithTimeout(
				AsyncMongoPersistor.EVENT_DB_GET_FILE,
				fileMsg,
				Route.TIMEOUT,
				handler);
	}
	
	public void sendStoreFile(String fileName, String contentType, Buffer fileMsg, Handler<AsyncResult<Message<String>>> handler) {
		
		Buffer fileBuffer = MongoFileUtil.createFileBuffer(fileName, contentType, fileMsg);
		
		vertx.eventBus().sendWithTimeout(
				AsyncMongoPersistor.EVENT_DB_STORE_FILE,
				fileBuffer,
				Route.TIMEOUT,
				handler);
	}

}
