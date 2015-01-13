package eu.socie.rest;

import java.util.Map;
import java.util.Map.Entry;

import org.vertx.java.core.eventbus.EventBus;

import com.jetdrone.vertx.yoke.core.YokeFileUpload;
import com.jetdrone.vertx.yoke.middleware.YokeRequest;

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
		
		get((r) -> handleUpload(r));
		post((r) -> handleUpload(r));
	}

	public void handleUpload(YokeRequest upload) {
		Map<String, YokeFileUpload> files = upload.files();

		if (files != null && files.size() > 0) {

			for (Entry<String, YokeFileUpload> file : files.entrySet()) {
				System.out.println(file.getKey());
			}

		}

		upload.response().setStatusCode(200).end();
	}

}
