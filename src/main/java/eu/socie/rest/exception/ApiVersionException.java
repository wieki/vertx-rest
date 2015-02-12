/**
 * 
 */
package eu.socie.rest.exception;

import org.vertx.java.core.VertxException;

/**
 * @author Bram Wiekens
 *
 */
public class ApiVersionException extends VertxException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public static final String MULTIPLE_AVAILABLE = "Version %s is not available, try to use one of the following versions: %s";
	public static final String SINGLE_AVAILABLE = "Version %s is not available, try to use version %s";

	public ApiVersionException(String wrongVersion, String... availableVersions) {
		super(availableVersions.length == 1 ? 
				String.format(SINGLE_AVAILABLE,	wrongVersion, getVersions(availableVersions)) : 
				String.format(MULTIPLE_AVAILABLE, wrongVersion, getVersions(availableVersions)));
	}

	private static String getVersions(String... versions) {
		String result = "";

		for (int i = 0; i < versions.length - 1; i++) {
			result += versions[i] + ", ";
		}
	
		result += versions[versions.length - 1];

		return result;
	}

}
