package eu.socie.rest;

import java.util.List;
import java.util.Map.Entry;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.ReplyException;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import com.jetdrone.vertx.yoke.middleware.YokeRequest;
import com.jetdrone.vertx.yoke.middleware.YokeResponse;

import eu.socie.mongo_async_persistor.AsyncMongoPersistor;

public abstract class ListRoute extends Route {

	private EventBus eventBus;

	private String collection;

	private static final String LIMIT = "limit";
	private static final String SORT = "sort";
	private static final int ASCENDING = 1;
	private static final int DESCENDING = -1;

	// TODO store localized?
	public static final String ERROR_ENTITY_EMPTY = "The submitted entity is empty and cannot be stored";
	private static final String ERROR_DELETE_DOC_EMPTY = "The delete search document is empty, therefore cannot be executed";

	public ListRoute(String collection, String path, EventBus eventBus) {
		super(path);

		this.eventBus = eventBus;

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

		eventBus.sendWithTimeout(
				AsyncMongoPersistor.EVENT_DB_DELETE,
				delete,
				TIMEOUT,
				(AsyncResult<Message<Integer>> results) -> respondDeleteResults(
						results, request));

	}

	protected JsonObject createCreateDocument(YokeRequest request) {
		if (request.body() == null) {
			replyError(request, ERROR_CLIENT_BAD_REQUEST, ERROR_ENTITY_EMPTY);
		}

		return request.body();
	}

	protected final void createCreateRequest(YokeRequest request) {
		JsonObject create = new JsonObject();

		JsonObject doc = createCreateDocument(request);

		create.putString("collection", collection);

		create.putObject("document", doc);

		eventBus.sendWithTimeout(
				AsyncMongoPersistor.EVENT_DB_CREATE,
				create,
				TIMEOUT,
				(AsyncResult<Message<JsonObject>> results) -> respondCreateResults(
						results, request));

	}

	/**
	 * Create a search document from the request. If modifications to the
	 * document are necessary before submitting the query override this method.
	 * The method now only creates an empty search document (which will return all doucments)
	 * 
	 * @param request is the http request
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
		JsonObject find = new JsonObject();
		JsonObject doc = createSearchDocument(request);

		List<Entry<String, String>> params = request.params().entries();

		for (Entry<String, String> param : params) {
			String key = param.getKey();

			if (key.equalsIgnoreCase(LIMIT)) {
				int limit = Integer.parseInt(param.getValue());
				find.putNumber("limit", limit);
			}

			if (key.equalsIgnoreCase(SORT)) {
				JsonObject sortObj = createSortDoc(param.getValue());
				find.putObject(SORT, sortObj);
			}

		}

		find.putString("collection", collection);

		find.putObject("document", doc);

		eventBus.sendWithTimeout(
				AsyncMongoPersistor.EVENT_DB_FIND,
				find,
				TIMEOUT,
				(AsyncResult<Message<JsonArray>> results) -> respondFindResults(
						results, request));
	}

	protected JsonObject createSortDoc(String sortStr) {
		JsonObject obj = new JsonObject();

		String[] sorts = sortStr.split(",");
		for (String sort : sorts) {
			int direction = ASCENDING;

			if (sort.startsWith("-")) {
				sort = sort.substring(1);
				direction = DESCENDING;
			}

			obj.putNumber(sort, direction);
		}

		return obj;
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
			JsonObject obj = results.result().body();

			String id = obj.getString("result_id");

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

	protected void respondFindResults(AsyncResult<Message<JsonArray>> result,
			YokeRequest request) {

		if (result.succeeded()) {

			JsonArray results = result.result().body();

			// log.debug("Returned results from database: " + results.size());
			addJsonContentHeader(request);
			
			request.response().setChunked(true).write(results.toString())
					.setStatusCode(SUCCESS_OK).end();

		} else {
			ReplyException ex = (ReplyException) result.cause();
			replyError(request, ERROR_SERVER_GENERAL_ERROR, ex);
		}
	}

}
