/**
 * 
 */
package eu.socie.rest.schema;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.regex.Pattern;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxException;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.main.JsonSchemaFactoryBuilder;

import eu.socie.rest.Route;

/**
 * @author Bram Wiekens
 *
 */
public class JsonSchemaValidator {

	private String resourcePath;
	private URI resourceUrl;
	
	private JsonSchema schema;
	
	private static final String ERROR_DOCUMENT_VALIDATION = "Problem validating document due to: %s";
	private static final String ERROR_SCHEMA_READ = "Problem reading %s due to: %s";
	private static final String ERROR_READ = "Could not read %s";
	private static final String ERROR_URI = "Server returned \"%d - %s\" for resource %s";

/*	public JsonSchemaValidator(String resourcePath) {
		this.resourcePath = resourcePath;
	}*/
	
	public JsonSchemaValidator(URI resourceUri) {
		resourceUrl = resourceUri;
	}

	public ProcessingReport validate(JsonObject obj){
		// TODO evaluate if this is efficient enough??
		try {
			String jsonString = obj.toString();
			JsonNode node = JsonLoader.fromString(jsonString);
			ProcessingReport report = schema.validate(node);
			
			return report;
		} catch (IOException | ProcessingException e) {
			throw new VertxException(String.format(ERROR_DOCUMENT_VALIDATION, e.getMessage()));
		}
	}
	
	public void load(Vertx vertx) {
		// Read resource from URI
		if (resourceUrl != null) {
			
			HttpClient client = vertx.createHttpClient();
			client.setHost(resourceUrl.getHost());
			client.setPort(resourceUrl.getPort());
			
			String url = resourceUrl.getPath();  

			HttpClientRequest request = client.get(url, r -> handleSchemaResponse(r, url));
			request.headers().add("Accept", "application/json");
			request.end();
			
		}// Read resource from file
		else {
			URL url = getClass().getClassLoader().getResource(resourcePath);	
		
			if (url != null) {
				String filePath = url.getPath();
			if (Pattern.matches("^/[A-Z]:.*$", filePath)) {
				vertx.fileSystem().readFile(filePath, b -> handleSchema(b));
			}
			}
			
		}
		
	}
	
	private void handleSchemaResponse(HttpClientResponse response, String url) {

		if (response.statusCode() == Route.SUCCESS_OK) {
			response.bodyHandler(b -> handleSchema(b));
		} else {
			throw new VertxException(String.format(ERROR_URI, response.statusCode(), response.statusMessage(), url));
		}
	}
	
	private void handleSchema(Buffer buffer) {
		try {
		String jsonString = buffer.toString();

		JsonNode node = JsonLoader.fromString(jsonString);
		JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
		schema = factory.getJsonSchema(node);
		
		}  catch (IOException | ProcessingException e) {
			throw new VertxException(String.format(ERROR_SCHEMA_READ, resourceUrl.toString(), e.getMessage()));
		}
		
	}
	
	private void handleSchema(AsyncResult<Buffer> buffer) {
		if (buffer.succeeded()) {
			try {
				String jsonString = buffer.result().toString();

				JsonNode node = JsonLoader.fromString(jsonString);
				JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
				schema = factory.getJsonSchema(node);
			} catch (IOException | ProcessingException e) {
				throw new VertxException(String.format(ERROR_SCHEMA_READ, resourcePath, e.getMessage()));
			}

		} else {
			throw new VertxException(String.format(ERROR_READ, resourcePath));
		}
	}
	


}
