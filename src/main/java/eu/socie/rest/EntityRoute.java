package eu.socie.rest;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.http.HttpServerRequest;
import io.vertx.rxjava.core.http.HttpServerResponse;
import io.vertx.rxjava.ext.apex.RoutingContext;
import eu.socie.rest.exception.ProcessingException;
import eu.socie.rest.exception.RestException;

public abstract class EntityRoute extends Route {

	private String collection;

	private String idParam = "id";

	// TODO move to String
	private static final String NOT_FOUND = "Entity with id %s was not found";

	public EntityRoute(String collection, String path, String jsonSchema,
			Vertx vertx, String id) {
		super(path, jsonSchema, vertx);

		this.idParam = id;

		init(collection);
	}

	public EntityRoute(String collection, String path, String jsonSchema,
			Vertx vertx) {
		super(path, jsonSchema, vertx);

		init(collection);
	}

	public EntityRoute(String collection, String path, Vertx vertx) {
		super(path, vertx);

		init(collection);
	}

	public EntityRoute(String collection, String path, Vertx vertx, String id) {
		super(path, vertx);

		this.idParam = id;

		init(collection);
	}

	private void init(String collection) {
		this.collection = collection;

		get((r) -> createSearchRequest(r));
		put((r) -> createUpdateRequest(r));
		delete((r) -> createDeleteRequest(r));
		patch((r) -> createPatchRequest(r));
	}

	protected JsonObject validateAndConvertPatchDocument(JsonObject obj) {
		return obj;
	}

	private final void createPatchRequest(RoutingContext context) {
		HttpServerRequest request = context.request();

		JsonObject d = context.getBodyAsJson();

		JsonObject doc = validateAndConvertPatchDocument(d);

		String idParam = getIdParam();
		String id = request.getParam(idParam);

		JsonObject query = new JsonObject();
		query.put("_id", id);

		// FIXME add error handling
		mongoClient.updateObservable(collection, query, doc).subscribe(
				r -> handlePatchResult(context, r, id));

	}

	protected void handlePatchResult(RoutingContext request, Void result,
			String id) {

		request.response().setStatusCode(Route.SUCCESS_OK).end();
	}

	protected JsonObject createDeleteDocument(RoutingContext context) {
		JsonObject queryDoc = new JsonObject();

		HttpServerRequest request = context.request();

		String id = request.getParam(idParam);

		queryDoc.put("_id", id);

		return queryDoc;

	}

	protected final void createDeleteRequest(RoutingContext context) {
		JsonObject delete = createDeleteDocument(context);

		// FIXME add error handling!!
		mongoClient.removeObservable(collection, delete).subscribe(
				results -> respondDeleteResults(results, context));

	}

	protected JsonObject createUpdateDocument(RoutingContext context) {
		HttpServerRequest request = context.request();

		JsonObject updateDoc = context.getBodyAsJson();

		if (!updateDoc.containsKey("_id")) {
			String id = request.getParam(getIdParam());

			updateDoc.put("_id", id);
		}

		return updateDoc;
	}

	protected JsonObject validateAndConvertDocument(String version,
			JsonObject object) {
		return validateDocument(object);
	}

	protected final void createUpdateRequest(RoutingContext context) {
		String version = getVersionFromHeader(context);

		try {

			JsonObject doc = validateAndConvertDocument(version,
					createUpdateDocument(context));

			String id = doc.getString("_id");
			JsonObject queryDoc = new JsonObject();
			queryDoc.put("_id", id);

			mongoClient
					.updateObservable(collection, queryDoc, doc)
					.doOnError(
							ex -> replyError(context,
									ERROR_SERVER_GENERAL_ERROR, ex.getMessage()))
					.subscribe(
							results -> responseUpdateResults(results, context));

		} catch (ProcessingException pe) {
			replyError(context, ERROR_CLIENT_METHOD_UNACCEPTABLE, pe);
		}

	}

	protected JsonObject createSearchDocument(RoutingContext context) {
		JsonObject doc = new JsonObject();

		HttpServerRequest request = context.request();

		String id = request.getParam(getIdParam());

		doc.put("_id", id);

		return doc;
	}

	/**
	 * Create an request for the async Mongo persistor from the document
	 * submitted in the request
	 * 
	 * @param request
	 *            is the request passed from the client
	 */
	protected final void createSearchRequest(RoutingContext context) {
		JsonObject find = new JsonObject();

		// Get all fields
		JsonObject allFields = new JsonObject();

		mongoClient
				.findOneObservable(collection, find, allFields)
				.doOnError(
						ex -> replyError(context, ERROR_SERVER_GENERAL_ERROR,
								ex.getMessage()))
				.subscribe(results -> respondFindResults(results, context));
	}

	protected void respondDeleteResults(Void result, RoutingContext context) {

		HttpServerRequest request = context.request();

		request.response().setChunked(true).setStatusCode(SUCCESS_OK).end();

	}

	protected void responseUpdateResults(Void results,
			RoutingContext context) {

		HttpServerRequest request = context.request();
		HttpServerResponse response = request.response();

		response.setChunked(true).setStatusCode(SUCCESS_OK).end();

	}

	protected JsonObject convertFindResults(String version, JsonObject result) {
		return result;
	}

	protected final void respondFindResults(JsonObject result,
			RoutingContext context) {

		String id = context.request().getParam(getIdParam());

		if (result == null) {
			throw new RestException(String.format(NOT_FOUND, id),
					ERROR_CLIENT_NOT_FOUND);
		}

		String version = getVersionFromHeader(context);

		JsonObject convertedResult = convertFindResults(version, result);

		respondJsonResults(context, convertedResult);
	}

	/**
	 * Return the parameter that is set to be the id of the object. If it's not
	 * changed it will return 'id'
	 * 
	 * @return the param
	 */
	public String getIdParam() {
		return idParam;
	}

}
