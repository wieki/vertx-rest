package eu.socie.rest;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.ReplyException;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import com.jetdrone.vertx.yoke.middleware.YokeRequest;
import com.jetdrone.vertx.yoke.middleware.YokeResponse;

import eu.socie.rest.exception.ProcessingException;
import eu.socie.rest.util.CreateUtil;
import eu.socie.rest.util.DeleteUtil;

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

	protected JsonObject validateAndConvertPatchDocument(JsonObject obj){
		return obj;
	}
	
	private final void createPatchRequest(YokeRequest request) {
		JsonObject d = request.body();
		
		JsonObject doc = validateAndConvertPatchDocument(d);
		
		String idParam = getIdParam();
		String id = request.getParameter(idParam);

		JsonObject query = new JsonObject();
		query.putString("_id", id);

		JsonObject update = CreateUtil.createUpdateDocument(doc, query,
				collection);

		mongoHelper.sendUpdate(update, r -> handlePatchResult(request, r, id));
	}

	protected void handlePatchResult(YokeRequest request,
			AsyncResult<Message<JsonObject>> result, String id) {
		if (result.succeeded()) {
			JsonObject doc = result.result().body();
			doc.putString("result_id", id);
			
			convertPatchResult(doc);
			request.response().setStatusCode(Route.SUCCESS_OK).end();
		} else {
			String msg = result.cause().getMessage();
			// TODO update message to something more useful
			replyError(request, Route.ERROR_SERVER_GENERAL_ERROR, msg);
		}

	}
	
	protected JsonObject convertPatchResult(JsonObject doc){
		return doc;
	}

	protected JsonObject createDeleteDocument(YokeRequest request) {
		JsonObject queryDoc = new JsonObject();

		String id = request.getParameter(idParam);

		queryDoc.putString("_id", id);

		return DeleteUtil.createDeleteDocument(queryDoc, collection, true);

	}

	protected final void createDeleteRequest(YokeRequest request) {
		JsonObject delete = createDeleteDocument(request);

		mongoHelper.sendDelete(delete,
				results -> respondDeleteResults(results, request));
	}

	protected JsonObject createUpdateDocument(YokeRequest request) {
		JsonObject updateDoc = request.body();
		if (!updateDoc.containsField("_id")) {
			String id = request.getParameter(getIdParam());

			updateDoc.putString("_id", id);
		}

		return updateDoc;
	}

	protected JsonObject validateAndConvertDocument(String version,
			JsonObject object) {
		return validateDocument(object);
	}

	protected final void createUpdateRequest(YokeRequest request) {
		String version = getVersionFromHeader(request);

		try {

			JsonObject doc = validateAndConvertDocument(version,
					createUpdateDocument(request));

			JsonObject create = CreateUtil
					.createCreateDocument(doc, collection);

			mongoHelper.sendCreateOrUpdate(create,
					results -> respondCreateResults(results, request));

		} catch (ProcessingException pe) {
			replyError(request, ERROR_CLIENT_METHOD_UNACCEPTABLE, pe);
		}

	}

	protected JsonObject createSearchDocument(YokeRequest request) {
		JsonObject doc = new JsonObject();

		String id = request.getParameter(getIdParam());

		doc.putString("_id", id);

		return doc;
	}

	/**
	 * Create an request for the async Mongo persistor from the document
	 * submitted in the request
	 * 
	 * @param request
	 */
	protected final void createSearchRequest(YokeRequest request) {
		JsonObject find = new JsonObject();
		JsonObject doc = createSearchDocument(request);

		find.putString("collection", collection);

		find.putObject("document", doc);

		mongoHelper.sendFind(find,
				results -> respondFindResults(results, request));
	}

	protected void respondDeleteResults(AsyncResult<Message<Integer>> results,
			YokeRequest request) {

		if (results.succeeded()) {

			request.response().setChunked(true).setStatusCode(SUCCESS_OK).end();
		} else {
			ReplyException ex = (ReplyException) results.cause();
			replyError(request, ERROR_SERVER_GENERAL_ERROR, ex);
		}

		// log.debug("Returned results from database: " + results.size());

	}

	protected void respondCreateResults(
			AsyncResult<Message<JsonObject>> results, YokeRequest request) {

		if (results.succeeded()) {

			YokeResponse response = request.response();

			response.setChunked(true).setStatusCode(SUCCESS_OK).end();
		} else {
			ReplyException ex = (ReplyException) results.cause();
			replyError(request, ERROR_SERVER_GENERAL_ERROR, ex);
		}

		// log.debug("Returned results from database: " + results.size());

	}

	protected JsonArray convertFindResults(String version, JsonArray results) {
		return results;
	}

	protected final void respondFindResults(
			AsyncResult<Message<JsonArray>> result, YokeRequest request) {

		if (result.succeeded()) {

			String id = request.getParameter("id");
			JsonArray results = result.result().body();

			if (results == null || results.size() == 0) {
				replyError(request, ERROR_CLIENT_NOT_FOUND,
						String.format(NOT_FOUND, id));
			}

			String version = getVersionFromHeader(request);

			JsonArray convertedResults = convertFindResults(version, results);

			respondJsonResults(request, convertedResults.get(0));

		} else {
			ReplyException ex = (ReplyException) result.cause();
			replyError(request, ERROR_SERVER_GENERAL_ERROR, ex);
		}
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
