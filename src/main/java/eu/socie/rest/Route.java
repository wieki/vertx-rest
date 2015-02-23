package eu.socie.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.ReplyException;
import org.vertx.java.core.json.JsonElement;
import org.vertx.java.core.json.JsonObject;

import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.jetdrone.vertx.yoke.middleware.Router;
import com.jetdrone.vertx.yoke.middleware.YokeRequest;
import com.jetdrone.vertx.yoke.middleware.YokeResponse;

import eu.socie.rest.exception.ProcessingException;
import eu.socie.rest.mongo.MongoHelper;
import eu.socie.rest.schema.JsonSchemaValidator;
import eu.socie.rest.schema.ProcessReportEncoder;

/**
 * 
 * @author Bram Wiekens
 *
 */
public class Route {

	private String bindPath;

	private String path;
	private List<Route> routes;
	// private Module module;
	private Handler<YokeRequest> put;
	private Handler<YokeRequest> post;
	private Handler<YokeRequest> get;
	private Handler<YokeRequest> patch;
	private Handler<YokeRequest> delete;

	// TODO Make this a configuration parameter
	public static final long TIMEOUT = 2000;

	public static final String PUT = "PUT";
	public static final String POST = "POST";
	public static final String GET = "GET";
	public static final String DELETE = "DELETE";
	public static final String PATCH = "PATCH";
	public static final String OPTIONS = "OPTIONS";
	public static final String HEAD = "HEAD";

	public final static int SUCCESS_OK = 200;
	public final static int SUCCESS_CREATED = 201;
	public final static int SUCCESS_NO_CONTENT = 204;

	public final static int ERROR_CLIENT_BAD_REQUEST = 400;
	public final static int ERROR_CLIENT_UNAUTHORIZED = 401;
	public final static int ERROR_CLIENT_FORBIDDEN = 403;
	public final static int ERROR_CLIENT_NOT_FOUND = 404;
	public final static int ERROR_CLIENT_METHOD_NOT_ALLOWED = 405;
	public final static int ERROR_CLIENT_METHOD_UNACCEPTABLE = 406;

	public final static int ERROR_SERVER_GENERAL_ERROR = 500;
	public final static int ERROR_SERVER_NOT_IMPLEMENTED = 501;
	public final static int ERROR_SERVER_SERVICE_UNAVAILABLE = 503;

	public final static String ERROR_CLIENT_VERSION_MSG = "No version in accept header specified";
	public final static String ERROR_CLIENT_EMPTY_DOC_MSG = "Cannot process an empty document";

	private Pattern versionPattern;

	protected JsonSchemaValidator validator;
	protected MongoHelper mongoHelper;
	protected Vertx vertx;

	public Route(String path, Vertx vertx) {
		this.path = path;

		this.vertx = vertx;
		
		mongoHelper = new MongoHelper(vertx);

		versionPattern = Pattern.compile("v[0-9]+");

		validator = null;
	}

	/**
	 * Create an route bound to path and enable schema validation on incoming
	 * objects. The jsonSchemaPath is based on a resource path
	 * 
	 * @param path
	 *            is relative the path within the URL
	 * @param jsonSchemaPath
	 */
	public Route(String path, String jsonSchemaPath, Vertx vertx) {
		this.path = path;
		this.vertx = vertx;

		mongoHelper = new MongoHelper(vertx);

		versionPattern = Pattern.compile("v[0-9]+");

		validator = new JsonSchemaValidator(jsonSchemaPath);
		// TODO this is async, can be a problem?
		validator.load(vertx);
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public List<Route> getRoutes() {
		return routes;
	}

	public Route addRoute(Route route) {
		if (routes == null)
			routes = new ArrayList<Route>();

		routes.add(route);

		return this;
	}

	public Route get(Handler<YokeRequest> handler) {
		this.get = handler;
		return this;
	}

	public Route put(Handler<YokeRequest> handler) {
		this.put = handler;
		return this;
	}

	public Route post(Handler<YokeRequest> handler) {
		this.post = handler;
		return this;
	}

	public Route patch(Handler<YokeRequest> handler) {
		this.patch = handler;
		return this;
	}

	public Route delete(Handler<YokeRequest> handler) {
		this.delete = handler;
		return this;
	}

	public void bind(String path, Router router) {
		StringBuffer options = new StringBuffer("OPTIONS");

		if (put != null) {
			router.put(path, put);
			options.append(", " + PUT);
		} else {
			router.put(path,
					(r) -> createNotAllowedRequest(r, options.toString()));
		}
		if (post != null) {
			router.post(path, post);
			options.append(", " + POST);
		} else {
			router.post(path,
					(r) -> createNotAllowedRequest(r, options.toString()));
		}
		if (get != null) {
			router.get(path, get);
			options.append(", " + GET);
		} else {
			router.get(path,
					(r) -> createNotAllowedRequest(r, options.toString()));
		}
		if (delete != null) {
			router.delete(path, delete);
			options.append(", " + DELETE);
		} else {
			router.delete(path,
					(r) -> createNotAllowedRequest(r, options.toString()));
		}
		if (patch != null) {
			router.patch(path, patch);
			options.append(", " + PATCH);
		} else {
			router.patch(path,
					(r) -> createNotAllowedRequest(r, options.toString()));
		}

		String verbs = options.toString();
		router.options(path, (r) -> handleOptions(verbs, r));
	}

	/**
	 * This method will return the available methods on the url
	 * 
	 * @param verbs
	 *            are the methods available to call on this url
	 * @param request
	 *            is the request to reply the available methods to
	 */
	protected void handleOptions(String verbs, YokeRequest request) {
		MultiMap map = request.response().headers();
		map.add("Allow", verbs);

		request.response().setStatusCode(SUCCESS_OK).end();
	}

	/**
	 * This method will return the full bind path of the route, in contrary to
	 * the getPath() method which will only return the relative path of the
	 * route
	 * 
	 * @return the full uri which the route is bound to
	 */
	public String getBindPath() {
		return bindPath;
	}

	/**
	 * Should only be called when binding the route to its full url.
	 * 
	 * @param bindPath
	 *            is the full url this route will listen too
	 */
	public void setBindPath(String bindPath) {
		this.bindPath = bindPath;
	}

	/**
	 * Respond with a method not allowed response to the requester. All methods
	 * that are not bound should reply this way and should state which methods
	 * are available for use
	 * 
	 * @param request
	 *            is the request that can not be fulfilled
	 * @param verbs
	 *            are the available methods the requester can use on this uri
	 */
	protected void createNotAllowedRequest(YokeRequest request, String verbs) {
		YokeResponse response = request.response();

		response.headers().add("Allow", verbs);

		response.setStatusCode(ERROR_CLIENT_METHOD_NOT_ALLOWED).end();

	}

	protected void replyError(YokeRequest request, int statusCode,
			ReplyException exception) {

		String error = String.format("%d : %s", exception.failureCode(),
				exception.getMessage());

		request.response().setChunked(true).setStatusCode(statusCode).end(error);
	}

	protected void replyError(YokeRequest request, int code,
			ProcessingException exception) {

		if (exception.hasJsonReport()) {
			addJsonContentHeader(request);
		}

		String error = String.format("%s", exception.getMessage());
		
		request.response().setChunked(true).setStatusCode(code).end(error);
	}

	protected void replyError(YokeRequest request, int code, String message) {

		String error = String.format("%s", message);

		request.response().setChunked(true).setStatusCode(code).end(error);
	}

	protected void addJsonContentHeader(YokeRequest request) {
		YokeResponse response = request.response();
		response.headers().add("Content-Type", "application/json");

	}

	protected String getVersionFromHeader(YokeRequest request) {
		String acceptHeader = request.getHeader("Accept");
		String version = "";

		Matcher matcher = versionPattern.matcher(acceptHeader);
		if (matcher.find()) {
			version = matcher.group(0);
		} else {
			request.response().setStatusCode(400).end(ERROR_CLIENT_VERSION_MSG);
			throw new ProcessingException(ERROR_CLIENT_VERSION_MSG);
		}

		return version;
	}

	protected JsonObject validateDocument(JsonObject object) {
		if (object == null) {
			throw new ProcessingException(ERROR_CLIENT_EMPTY_DOC_MSG);
		}
		if (validator != null) {
			// TODO include version checks and automatic handling
			ProcessingReport report = validator.validate(object);
			if (report.isSuccess()) {
				return object;
			} else {
				JsonObject obj = ProcessReportEncoder.encode(report);

				throw new ProcessingException(obj);
			}
		}

		return object;
	}

	protected void respondJsonResults(YokeRequest request, JsonElement obj) {
		addJsonContentHeader(request);

		request.response().setChunked(true).write(obj.toString())
				.setStatusCode(SUCCESS_OK).end();
	}
}
