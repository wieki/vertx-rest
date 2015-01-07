package eu.socie.rest;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.ReplyException;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import com.jetdrone.vertx.yoke.middleware.YokeRequest;
import com.jetdrone.vertx.yoke.middleware.YokeResponse;

import eu.socie.mongo_async_persistor.AsyncMongoPersistor;
import eu.socie.mongo_async_persistor.util.MongoUtil;

public abstract class EntityRoute extends Route {

	private EventBus eventBus;

	private String collection;

	// TODO move to String
	private static final String NOT_FOUND = "Entity with id %s was not found";

	public EntityRoute(String collection, String path, EventBus eventBus) {
		super(path);

		this.eventBus = eventBus;

		this.collection = collection;

		get((r) -> createSearchRequest(r));
		put((r) -> createUpdateRequest(r));
		delete((r) -> createDeleteRequest(r));
		patch((r) -> createPatchRequest(r));
	}

	private Object createPatchRequest(YokeRequest r) {
		// TODO Auto-generated method stub
		return null;
	}

	protected final JsonObject createDeleteDocument(YokeRequest request) {
		JsonObject doc = new JsonObject();

		String id = request.getParameter("id");

		doc.putObject("_id", MongoUtil.createIdReference(id));

		return doc;
	}

	protected final void createDeleteRequest(YokeRequest request) {
		JsonObject delete = new JsonObject();

		JsonObject doc = createDeleteDocument(request);

		delete.putBoolean("just_one", true);
		delete.putString("collection", collection);

		delete.putObject("document", doc);

		eventBus.sendWithTimeout(
				AsyncMongoPersistor.EVENT_DB_DELETE,
				delete,
				TIMEOUT,
				(AsyncResult<Message<Integer>> results) -> respondDeleteResults(
						results, request));

	}

	protected void createUpdateRequest(YokeRequest request) {

		JsonObject create = new JsonObject();

		JsonObject doc = request.body();

		create.putString("collection", collection);

		create.putObject("document", doc);

		eventBus.sendWithTimeout(
				AsyncMongoPersistor.EVENT_DB_CREATE,
				create,
				TIMEOUT,
				(AsyncResult<Message<JsonObject>> results) -> respondCreateResults(
						results, request));

	}

	protected JsonObject createSearchDocument(YokeRequest request) {
		JsonObject doc = new JsonObject();

		String id = request.getParameter("id");

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

		eventBus.sendWithTimeout(
				AsyncMongoPersistor.EVENT_DB_FIND,
				find,
				TIMEOUT,
				(AsyncResult<Message<JsonArray>> results) -> respondFindResults(
						results, request));
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

			String id = request.getParameter("id");
			JsonArray results = result.result().body();

			if (results == null || results.size() == 0) {
				replyError(request, ERROR_CLIENT_NOT_FOUND,
						String.format(NOT_FOUND, id));
			}

			addJsonContentHeader(request);

			// log.debug("Returned results from database: " + results.size());
			request.response().setChunked(true)
					.write(results.get(0).toString()).setStatusCode(SUCCESS_OK)
					.end();

		} else {
			ReplyException ex = (ReplyException) result.cause();
			replyError(request, ERROR_SERVER_GENERAL_ERROR, ex);
		}
	}

}
