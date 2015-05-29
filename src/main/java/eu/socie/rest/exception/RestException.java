/**
 * 
 */
package eu.socie.rest.exception;

import io.vertx.core.VertxException;

/**
 * @author Bram Wiekens
 *
 */
public class RestException extends VertxException{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private int statusCode;
		
	public RestException(String message, int code) {
		super(message);
		
		statusCode = code;
	}

	public int getStatusCode(){
		return statusCode;
	}

	
	
}
