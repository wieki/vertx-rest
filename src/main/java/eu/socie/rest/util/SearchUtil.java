/**
 * 
 */
package eu.socie.rest.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.vertx.java.core.json.JsonObject;

/**
 * @author Bram Wiekens
 *
 */
public class SearchUtil {

	private static final String LIMIT = "limit";
	private static final String SORT = "sort";
	private static final int ASCENDING = 1;
	private static final int DESCENDING = -1;
	
	
	public static JsonObject createSearchDocument(JsonObject searchDoc, String collection) {
		JsonObject find = new JsonObject();
		
		find.putString("collection", collection);

		find.putObject("document", searchDoc);
		
		return find;
	}
	
	
	public static JsonObject createSearchDocument(JsonObject searchDoc, String collection, List<Map.Entry<String, String>> params){
		Params convParams = convertParams(params);
		
		return createSearchDocument(searchDoc, collection, convParams);
	}
	
	
	public static JsonObject createSearchDocument(JsonObject searchDoc, String collection, Params params){
		JsonObject find = createSearchDocument(searchDoc, collection);

		for (Entry param : params.getEntries()) {
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
		
		return find;
	}
	
	public static JsonObject createSortDoc(String sortStr) {
		
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
	
	private static Params convertParams(List<Map.Entry<String, String>> params) {
		
		Params entries = new Params();
		
		params.forEach((e)-> entries.add(new Entry(e.getKey(), e.getValue())));
		
		return entries;
	}
	
	public static class Params {
		
		public List<Entry> params;
		
		public Params() {
			params = new ArrayList<>(2);
		}
		
		public Params add(Entry entry) {
			params.add(entry);
			
			return this;
		}
		
		public Params limit(Integer limit) {
			params.add(new Entry("limit", limit.toString()));
			
			return this;
		}
		
		public Params sort(String sortStr){
			params.add(new Entry("sort", sortStr));
			
			return this;
		}
		
		public List<Entry> getEntries() {
			return params;
		}
		
	}
	
	public static class Entry {

		String key;
		String value;
		
		public Entry(String key, String value) {
			this.key = key;
			this.value = value;
		}
		

		public String getKey() {
			return key;
		}


		public String getValue() {
			return value;
		}

		@Override
		public String toString() {
			return String.format("%s:%s", key, value);
		}
	
	}
	
}
