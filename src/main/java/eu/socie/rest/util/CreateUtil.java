/**
 * 
 */
package eu.socie.rest.util;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;

/**
 * @author Bram Wiekens
 *
 */
public class CreateUtil {

	public static JsonObject createCreateDocument(JsonObject createObject, String collection) {
		JsonObject create = new JsonObject();
		
		create.putString("collection", collection);

		create.putObject("document", createObject);

		return create;
	}
	
	public static String geIdFromResults(AsyncResult<Message<JsonObject>> results){
		JsonObject obj = results.result().body();
		String id = obj.getString("result_id");
		
		return id;
	}

}
