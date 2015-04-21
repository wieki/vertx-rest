/**
 * 
 */
package eu.socie.rest.util;

import org.vertx.java.core.json.JsonObject;

/**
 * @author Bram Wiekens
 *
 */
public class DeleteUtil {

	
	public static JsonObject createDeleteDocument(JsonObject deleteQuery, String collection, boolean justOne) {
		JsonObject delete = new JsonObject();
		
		delete.putBoolean("just_one", true);
		delete.putString("collection", collection);
	
		delete.putObject("query", deleteQuery);

		return delete;
		
	}
	
}
