/**
 * 
 */
package eu.socie.rest.schema;

import org.vertx.java.core.json.JsonObject;

import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;

/**
 * @author Bram Wiekens
 *
 * Transform an JSON Schema Exception into a JSON Object that can be displayed
 */
public class ProcessReportEncoder {

	public static JsonObject encode(ProcessingReport report) {
		JsonObject base = new JsonObject();
		
		report.forEach(action -> addMessage(base, action));
		
		return base;
	}
	
	
	private static void addMessage(JsonObject base, ProcessingMessage msg) {
		JsonObject msgNode = new JsonObject(msg.asJson().toString());
		
		base.putObject(msg.getLogLevel().name(), msgNode);
	}
	
}
