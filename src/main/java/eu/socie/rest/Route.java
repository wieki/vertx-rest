package eu.socie.rest;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.rxjava.core.MultiMap;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.http.HttpServerRequest;
import io.vertx.rxjava.core.http.HttpServerResponse;
import io.vertx.rxjava.core.shareddata.LocalMap;
import io.vertx.rxjava.ext.mongo.MongoClient;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.RoutingContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.github.fge.jsonschema.core.report.ProcessingReport;

import eu.socie.rest.exception.ProcessingException;
import eu.socie.rest.schema.JsonSchemaValidator;
import eu.socie.rest.schema.ProcessReportEncoder;

/**
 * 
 * @author Bram Wiekens
 *
 */
public class Route implements ServerReadyListener {

	private String bindPath;

	private String path;
	private List<Route> routes;
	// private Module module;
	private Handler<RoutingContext> put;
	private Handler<RoutingContext> post;
	private Handler<RoutingContext> get;
	private Handler<RoutingContext> patch;
	private Handler<RoutingContext> delete;

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
	
	protected static Logger logger;
	
	private final static String VERSION_PATTERN = "v[0-9]+";

	private Pattern versionPattern;

	protected JsonSchemaValidator validator;
	protected MongoClient mongoClient;
	protected Vertx vertx;
	private String jsonSchemaPath;
	

	public Route(String path, Vertx vertx) {
		this(path, vertx, VERSION_PATTERN);

		validator = null;
	}

	/**
	 * Create an route bound to path and enable schema validation on incoming
	 * objects. The jsonSchemaPath is based on a resource path
	 * 
	 * @param path
	 *            is relative the path within the URL
	 * @param jsonSchemaPath is the local path to the json schema file
	 */
	public Route(String path, String jsonSchemaPath, Vertx vertx) {
		this(path, vertx, VERSION_PATTERN);

		// FIXME init mongo client

		this.jsonSchemaPath = jsonSchemaPath;

	}

	private Route(String path, Vertx vertx, String pattern) {
		this.path = path;

		this.vertx = vertx;
		versionPattern = Pattern.compile(pattern);

		logger = LoggerFactory
				.getLogger(getClass());
		
		// FIXME init mongo client
		LocalMap<String, JsonObject> map = vertx.sharedData().getLocalMap("app_config");
		JsonObject dbConfig = map.get("db_config");
		
		mongoClient = MongoClient.createShared(vertx, dbConfig);
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

	public Route get(Handler<RoutingContext> handler) {
		this.get = handler;
		return this;
	}

	public Route put(Handler<RoutingContext> handler) {
		this.put = handler;
		return this;
	}

	public Route post(Handler<RoutingContext> handler) {
		this.post = handler;
		return this;
	}

	public Route patch(Handler<RoutingContext> handler) {
		this.patch = handler;
		return this;
	}

	public Route delete(Handler<RoutingContext> handler) {
		this.delete = handler;
		return this;
	}

	public void bind(String path, Router router) {
		StringBuffer options = new StringBuffer("OPTIONS");
		
		if (put != null) {
			router.put(path).handler(put);
			options.append(", " + PUT);
		} else {
			router.put(path).handler(r -> createNotAllowedRequest(r, options.toString()));
		}
		if (post != null) {
			router.post(path).handler(post);
			options.append(", " + POST);
		} else {
			router.post(path).handler((r) -> createNotAllowedRequest(r, options.toString()));
		}
		if (get != null) {
			router.get(path).handler(get);
			options.append(", " + GET);
		} else {
			router.get(path).handler((r) -> createNotAllowedRequest(r, options.toString()));
		}
		if (delete != null) {
			router.delete(path).handler(delete);
			options.append(", " + DELETE);
		} else {
			router.delete(path).handler(
					(r) -> createNotAllowedRequest(r, options.toString()));
		}
		if (patch != null) {
			router.route(HttpMethod.PATCH, path).handler(patch);
			options.append(", " + PATCH);
		} else {
			router.route(HttpMethod.PATCH, path).handler((r) -> createNotAllowedRequest(r, options.toString()));
		}

		String verbs = options.toString();
		
		router.options(path).handler((r) -> handleOptions(verbs, r));
	}

	/**
	 * This method will return the available methods on the url
	 * 
	 * @param verbs
	 *            are the methods available to call on this url
	 * @param context
	 *            is the Http context that contains  to reply the available methods to
	 */
	protected void handleOptions(String verbs, RoutingContext context) {
		HttpServerRequest request = context.request();
		
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
	protected void createNotAllowedRequest(RoutingContext context, String verbs) {
		HttpServerResponse response = context.request().response();

		response.headers().add("Allow", verbs);

		response.setStatusCode(ERROR_CLIENT_METHOD_NOT_ALLOWED).end();

	}

	protected void replyError(RoutingContext context, int statusCode,
			ReplyException exception) {

		String error = String.format("%d : %s", exception.failureCode(),
				exception.getMessage());

		context.request().response().setChunked(true).setStatusCode(statusCode)
				.end(error);
	}

	protected void replyError(RoutingContext context, int code,
			ProcessingException exception) {

		HttpServerRequest request = context.request();
		
		if (exception.hasJsonReport()) {
			addJsonContentHeader(context);
		}

		String error = String.format("%s", exception.getMessage());

		request.response().setChunked(true).setStatusCode(code).end(error);
	}

	protected void replyError(RoutingContext context, int code, String message) {
		HttpServerRequest request = context.request();
		
		request.response().setChunked(true).setStatusCode(code).end(message);
	}

	protected void addJsonContentHeader(RoutingContext context) {
		HttpServerResponse response = context.request().response();
		response.headers().add("Content-Type", "application/json");

	}

	protected String getVersionFromHeader(RoutingContext context) {
		HttpServerRequest request = context.request();
		
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

	protected void respondJsonResults(RoutingContext context, JsonArray obj) {
		HttpServerRequest request = context.request();
		
		addJsonContentHeader(context);

		request.response().setChunked(true).write(obj.toString())
				.setStatusCode(SUCCESS_OK).end();
	}
	
	
	protected void respondJsonResults(RoutingContext context, JsonObject obj) {
		HttpServerRequest request = context.request();
		
		addJsonContentHeader(context);

		request.response().setChunked(true).write(obj.toString())
				.setStatusCode(SUCCESS_OK).end();
	}

	@Override
	public void finishedLoading(@Nullable String hostname,
			@Nullable Integer port) {
		String localhost = hostname == null ? "localhost" : hostname;
		int localport = port == null ? 80 : port;

		if (jsonSchemaPath != null) {
			String path = jsonSchemaPath.startsWith("/") ? jsonSchemaPath : "/"
					+ jsonSchemaPath;
			// FIXME do something about https!!
			
			try {
				URI uri = new URI(String.format("http://%s:%d%s", localhost,
						localport, path));

				validator = new JsonSchemaValidator(uri);
				// TODO this is async, can be a problem?
				validator.load(vertx);
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}

}
