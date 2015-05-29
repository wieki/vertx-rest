/**
 * 
 */
package eu.socie.rest.exception;

import io.vertx.core.VertxException;
import io.vertx.core.json.JsonObject;


/**
 * @author Bram Wiekens
 *
 */
public class ProcessingException extends VertxException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	

	private boolean hasJsonReport;
	
	public ProcessingException(String message) {
		super(message);
		
		this.hasJsonReport = false;
	}
	
	
	public ProcessingException(JsonObject message) {
		super(message.toString());

		this.hasJsonReport = true;
	}

	public boolean hasJsonReport() {
		return hasJsonReport;
	}

}
