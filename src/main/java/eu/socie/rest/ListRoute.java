package eu.socie.rest;

import io.vertx.core.VertxException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.MultiMap;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.http.HttpServerRequest;
import io.vertx.rxjava.core.http.HttpServerResponse;
import io.vertx.rxjava.ext.apex.RoutingContext;

import java.net.URLDecoder;
import java.util.List;

import eu.socie.rest.exception.ProcessingException;

public abstract class ListRoute extends Route { 

	private String collection;

	// TODO store localized?
	public static final String ERROR_ENTITY_EMPTY = "The submitted entity is empty and cannot be stored";
	public static final String ERROR_DELETE_DOC_EMPTY = "The delete search document is empty, therefore cannot be executed";

	public ListRoute(String collection, String path, String jsonSchema,
			Vertx vertx) {
		super(path, jsonSchema, vertx);

		init(collection);
	}

	public ListRoute(String collection, String path, Vertx vertx) {
		super(path, vertx);

		init(collection);
	}

	private void init(String collection) {

		this.collection = collection;

		get((r) -> createSearchRequest(r));
		post((r) -> createCreateRequest(r));
		delete((r) -> createDeleteRequest(r));
	}

	protected JsonObject createDeleteDocument(RoutingContext context) {

		if (context.getBodyAsJson() == null) {
			replyError(context, ERROR_CLIENT_BAD_REQUEST,
					ERROR_DELETE_DOC_EMPTY);
			throw new VertxException(ERROR_DELETE_DOC_EMPTY);
		}
		return context.getBodyAsJson();

	}

	protected final void createDeleteRequest(RoutingContext context) {
		JsonObject queryDoc = context.getBodyAsJson();

		mongoClient
				.removeObservable(collection, queryDoc)
				.doOnError(
						ex -> replyError(context, ERROR_SERVER_GENERAL_ERROR,
								ex.getMessage()))
				.subscribe(results -> respondDeleteResults(results, context));

	}

	protected JsonObject validateAndConvertCreateDocument(String version,
			JsonObject object) {
		// TODO API version differentiation
		return validateDocument(object);
	}

	protected JsonObject createCreateDocument(RoutingContext context) {
		if (context.getBodyAsJson() == null) {

			replyError(context, ERROR_CLIENT_BAD_REQUEST, ERROR_ENTITY_EMPTY);
			throw new VertxException(ERROR_DELETE_DOC_EMPTY);
		}

		return context.getBodyAsJson();
	}

	protected final void createCreateRequest(RoutingContext context) {
		String version = getVersionFromHeader(context);

		try {
			JsonObject doc = validateAndConvertCreateDocument(version,
					createCreateDocument(context));

			mongoClient.insertObservable(collection, doc)
			.doOnError(ex -> replyError(context, ERROR_SERVER_GENERAL_ERROR, ex.getMessage()))
			.subscribe(results -> respondCreateResults(results, context));

		} catch (ProcessingException pe) {
			replyError(context, ERROR_CLIENT_METHOD_UNACCEPTABLE, pe);
		}
	}

	/**
	 * Create a search document from the request. If modifications to the
	 * document are necessary before submitting the query override this method.
	 * The method now only creates an empty search document (which will return
	 * all document)
	 * 
	 * @param context
	 *            is the routing context of the http request
	 * @return a json document that will be used for searching the database
	 */
	protected JsonObject createSearchDocument(RoutingContext context) {
		return new JsonObject();
	}

	/**
	 * Create an request for the async Mongo persistor from the document
	 * submitted in the request
	 * 
	 * @param request
	 *            is the request passed from the client
	 */
	protected final void createSearchRequest(RoutingContext context) {
		// FIXME this makes no sense, get requests don't have a json body!
		JsonObject doc = createSearchDocument(context);

		MultiMap params = context.request().params();

		String queryValue = params.get("q");

		if (queryValue != null && !queryValue.isEmpty()) {
			try {
				String value = URLDecoder.decode(queryValue, "UTF-8");

				JsonObject obj = new JsonObject(value);

				doc.mergeIn(obj);

			} catch (Exception e) {
				System.out.println(e.getMessage());
				// Query string is invalid, just ignore it;
			}
		}

		// FIXME add options for limit, skip and sorting again!!!!
		mongoClient
				.findObservable(queryValue, doc)
				.doOnError(
						e -> replyError(context,
								Route.ERROR_SERVER_GENERAL_ERROR,
								e.getMessage()))
				.subscribe(results -> respondFindResults(results, context));

	}

	/*
	 * protected JsonObject createSortDoc(String sortStr) { return
	 * SearchUtil.createSortDoc(sortStr); }
	 */

	protected void respondDeleteResults(Void results, RoutingContext context) {

		HttpServerRequest request = context.request();

		request.response().setChunked(true).setStatusCode(SUCCESS_OK).end();

	}

	protected void respondCreateResults(String result,
			RoutingContext context) {

		HttpServerRequest request = context.request();

		String id = result; //result.getString("result_id");

		HttpServerResponse response = request.response();

		// FIXME fill other parameters from request!!
		response.headers().add("Location",
				String.format("%s/%s", getBindPath(), id));

		response.setChunked(true).setStatusCode(SUCCESS_CREATED).end();

	}

	protected List<JsonObject> convertFindResults(String version,
			List<JsonObject> results) throws ProcessingException {
		return results;
	}

	protected void respondFindResults(List<JsonObject> results,
			RoutingContext context) {

		String version = getVersionFromHeader(context);

		List<JsonObject> convertedResults = null;

		try {
			convertedResults = convertFindResults(version, results);
		} catch (ProcessingException pe) {
			replyError(context, ERROR_CLIENT_METHOD_UNACCEPTABLE, pe);
			return;
		}

		JsonArray resultArray = new JsonArray(convertedResults);

		respondJsonResults(context, resultArray);

	}

}
