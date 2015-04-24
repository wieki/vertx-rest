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
	
	public static String getIdFromResults(AsyncResult<Message<JsonObject>> results){
		JsonObject obj = results.result().body();
		String id = obj.getString("result_id");
		
		return id;
	}
	
	public static JsonObject createUpdateDocument(JsonObject updateObject, JsonObject findQuery, String collection) {
		JsonObject create = new JsonObject();
		
		create.putString("collection", collection);

		create.putObject("document", updateObject);
		
		create.putObject("query", findQuery);

		return create;
		
	}

}
