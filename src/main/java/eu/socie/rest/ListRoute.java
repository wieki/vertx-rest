package eu.socie.rest;

import java.util.List;
import java.util.Map.Entry;

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
import eu.socie.rest.util.SearchUtil;

public abstract class ListRoute extends Route {

	private String collection;

	// TODO store localized?
	public static final String ERROR_ENTITY_EMPTY = "The submitted entity is empty and cannot be stored";
	public static final String ERROR_DELETE_DOC_EMPTY = "The delete search document is empty, therefore cannot be executed";

	public ListRoute(String collection, String path, String jsonSchema, Vertx vertx){
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

	protected JsonObject createDeleteDocument(YokeRequest request) {
		if (request.body() == null) {
			replyError(request, ERROR_CLIENT_BAD_REQUEST,
					ERROR_DELETE_DOC_EMPTY);
		}
		return request.body();

	}

	protected final void createDeleteRequest(YokeRequest request) {
		JsonObject delete = new JsonObject();

		JsonObject doc = request.body();

		delete.putString("collection", collection);

		delete.putObject("document", doc);

		mongoHelper.sendDelete(delete, results -> respondDeleteResults(results, request));

	}

	protected JsonObject validateAndConvertCreateDocument(String version, JsonObject object) {
		// TODO API version differentiation
		return validateDocument(object);
	}

	protected JsonObject createCreateDocument(YokeRequest request) {
		if (request.body() == null) {
			replyError(request, ERROR_CLIENT_BAD_REQUEST, ERROR_ENTITY_EMPTY);
		}

		return request.body();
	}

	protected final void createCreateRequest(YokeRequest request) {
		String version = getVersionFromHeader(request);

		try {
			JsonObject doc = validateAndConvertCreateDocument(version,
					createCreateDocument(request));

			JsonObject create = CreateUtil.createCreateDocument(doc, collection);

			mongoHelper.sendCreateOrUpdate(create, results -> respondCreateResults(results, request));

		} catch (ProcessingException pe) {
			replyError(request, ERROR_CLIENT_METHOD_UNACCEPTABLE,
					pe);
		}
	}

	/**
	 * Create a search document from the request. If modifications to the
	 * document are necessary before submitting the query override this method.
	 * The method now only creates an empty search document (which will return
	 * all doucments)
	 * 
	 * @param request
	 *            is the http request
	 * @return a json document that will be used for searching the database
	 */
	protected JsonObject createSearchDocument(YokeRequest request) {
		return new JsonObject();
	}

	/**
	 * Create an request for the async Mongo persistor from the document
	 * submitted in the request
	 * 
	 * @param request
	 */
	protected final void createSearchRequest(YokeRequest request) {
	
		JsonObject doc = createSearchDocument(request);
		
		List<Entry<String, String>> params = request.params().entries();
		
		JsonObject find = SearchUtil.createSearchDocument(doc, collection,params);

		mongoHelper.sendFind(find, results -> respondFindResults(results, request));
		
	}

	protected JsonObject createSortDoc(String sortStr) {
		return SearchUtil.createSortDoc(sortStr);
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
			String id = CreateUtil.geIdFromResults(results);

			YokeResponse response = request.response();

			response.headers().add("Location",
					String.format("%s/%s", getBindPath(), id));

			response.setChunked(true).setStatusCode(SUCCESS_CREATED).end();
		} else {
			ReplyException ex = (ReplyException) results.cause();
			replyError(request, ERROR_SERVER_GENERAL_ERROR, ex);
		}

		// log.debug("Returned results from database: " + results.size());

	}

	protected JsonArray convertFindResults(String version, JsonArray results)
			throws ProcessingException {
		return results;
	}

	protected void respondFindResults(AsyncResult<Message<JsonArray>> result,
			YokeRequest request) {

		if (result.succeeded()) {

			JsonArray results = result.result().body();

			String version = getVersionFromHeader(request);

			JsonArray convertedResults = null;

			try {
				convertedResults = convertFindResults(version, results);
			} catch (ProcessingException pe) {
				replyError(request, ERROR_CLIENT_METHOD_UNACCEPTABLE,
						pe);
				return;
			}

			respondJsonResults(request, convertedResults);

		} else {
			ReplyException ex = (ReplyException) result.cause();
			replyError(request, ERROR_SERVER_GENERAL_ERROR, ex);
		}
	}

}
