package eu.socie.rest;

import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;

import org.vertx.java.core.eventbus.EventBus;

import com.jetdrone.vertx.yoke.core.YokeFileUpload;
import com.jetdrone.vertx.yoke.middleware.YokeRequest;

import eu.socie.mongo_async_persistor.AsyncMongoPersistor;

/**
 * 
 * @author Bram Wiekens
 *
 */
public class UploadRoute extends Route {

	EventBus eventBus;
	
	public UploadRoute(String path, EventBus eventBus) {
		super(path);
		
		this.eventBus = eventBus;
		
		get((r) -> handleFile());
		post((r) -> handleUpload(r));
	}

	private void handleFile(){
			long since = new Date().getTime();
			eventBus.send(AsyncMongoPersistor.EVENT_DB_GET_FILE, "abc");
			System.out.println("1" + (new Date().getTime() - since));
			eventBus.send(AsyncMongoPersistor.EVENT_DB_GET_FILE, "abc");
			System.out.println("2" + (new Date().getTime() - since));
			eventBus.send(AsyncMongoPersistor.EVENT_DB_GET_FILE, "abc");
			System.out.println("3" + (new Date().getTime() - since));
	}
	
	public void handleUpload(YokeRequest upload) {
		Map<String, YokeFileUpload> files = upload.files();

		if (files != null && files.size() > 0) {

			for (Entry<String, YokeFileUpload> file : files.entrySet()) {
				System.out.println(file.getKey());
				
				/*YokeFileUpload upload = file.getValue();
				upload.*/
			}

		}

		upload.response().setStatusCode(200).end();
	}

}
