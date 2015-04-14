/**
 * 
 */
package eu.socie.rest.schema;

import java.io.IOException;
import java.net.URL;
import java.util.regex.Pattern;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxException;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.json.JsonObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;

/**
 * @author Bram Wiekens
 *
 */
public class JsonSchemaValidator {

	private String resourcePath;
	private JsonSchema schema;

	public JsonSchemaValidator(String resourcePath) {
		this.resourcePath = resourcePath;
	}

	private void handleSchema(AsyncResult<Buffer> buffer) {
		if (buffer.succeeded()) {
			try {
				String jsonString = buffer.result().toString();

				JsonNode node = JsonLoader.fromString(jsonString);
				JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
				schema = factory.getJsonSchema(node);
			} catch (IOException | ProcessingException e) {
				throw new VertxException(String.format("Problem reading %s due to %s", resourcePath, e.getMessage()));
			}

		} else {
			throw new VertxException("Could not read " + resourcePath);
		}
	}

	public ProcessingReport validate(JsonObject obj){
		// TODO evaluate if this is efficient enough??
		try {
			String jsonString = obj.toString();
			JsonNode node = JsonLoader.fromString(jsonString);
			ProcessingReport report = schema.validate(node);
			
			return report;
		} catch (IOException | ProcessingException e) {
			throw new VertxException("Problem validating document " + e.getMessage());
		}
	}
	
	public void load(Vertx vertx) {
		URL url = getClass().getClassLoader().getResource(resourcePath);

		if (url != null) {
			String filePath = url.getPath().substring(1);
			if (Pattern.matches("^/[A-Z]:.*$", filePath)) {
				filePath = filePath.substring(1);
			}
			vertx.fileSystem().readFile(filePath, (b) -> handleSchema(b));
		}
	}

}
