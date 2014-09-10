package com.peterfranza.simplisafe;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.peterfranza.simplisafe.SimplisafeClient.Event;
import com.peterfranza.simplisafe.SimplisafeClient.Location;
import com.peterfranza.simplisafe.SimplisafeClient.SYSTEM_STATE;

/**
 * 
 * Implementation of the simplisafe api as reverse engineered and documented by http://www.leftovercode.info/simplisafe.php
 * 
 * @author pfranza
 *
 */
public class SimpliSafeClientFactory {

	public static final String ROOT = "https://simplisafe.com/mobile/";
	
	public SimplisafeClient create(String username, String password) throws Exception {
		
		String uuid = UUID.randomUUID().toString();
		
		BasicCookieStore cookieStore = new BasicCookieStore();
		CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).build();
		try {
		
			HttpPost httpPost = new HttpPost(ROOT + "login/");
			List <NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("name", username));
			nvps.add(new BasicNameValuePair("pass", password));
			nvps.add(new BasicNameValuePair("device_name", "SimpliSafeClientAPI"));
			nvps.add(new BasicNameValuePair("device_uuid", uuid));
			nvps.add(new BasicNameValuePair("version", "1200"));
			nvps.add(new BasicNameValuePair("no_persist", "1"));
			nvps.add(new BasicNameValuePair("XDEBUG_SESSION_START", "session_name"));			
			httpPost.setEntity(new UrlEncodedFormEntity(nvps));
			
			CloseableHttpResponse response = httpClient.execute(httpPost);
			try {
				HttpEntity entity = response.getEntity();
				if (entity != null) {
					InputStream instream = entity.getContent();
					try {
						
						LoginResponse r = new Gson().fromJson(new InputStreamReader(instream), LoginResponse.class);
						if(r.return_code == 1) {
							return new SimpliSafeClientImpl(cookieStore, r, uuid);
						} else {
							throw new RuntimeException("Login Failed");
						}

					}  finally {
						instream.close();
					}
				} else {
					throw new RuntimeException("Login Failed");
				}
			} finally {
				response.close();
			}
		} catch (Exception e) {
			throw e;
		} finally {
			try {
				httpClient.close();
			} catch (IOException e) {
				throw e;
			}
		}
	}
	
	private static class SimpliSafeClientImpl implements SimplisafeClient {

		private BasicCookieStore cookieStore;
		private String uuid;
		private LoginResponse login;

		public SimpliSafeClientImpl(BasicCookieStore cookieStore,
				LoginResponse r, String uuid) {
			this.cookieStore = cookieStore;
			this.uuid = uuid;
			this.login = r;
		}

		private void sendCommand(String path, InputStreamHandler handler, NameValuePair ... params ) {
			
			if(cookieStore == null)
				throw new RuntimeException("Session has been closed");
			
			CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).build();
			try {
			
				HttpPost httpPost = new HttpPost(ROOT + path);
				List <NameValuePair> nvps = new ArrayList<NameValuePair>();
				nvps.add(new BasicNameValuePair("no_persist", "0"));
				nvps.add(new BasicNameValuePair("XDEBUG_SESSION_START", "session_name"));
				nvps.addAll(Arrays.asList(params));
				httpPost.setEntity(new UrlEncodedFormEntity(nvps));
				
				CloseableHttpResponse response = httpClient.execute(httpPost);
				try {
					HttpEntity entity = response.getEntity();
					if (entity != null) {
						InputStream instream = entity.getContent();
						try {
							if(handler != null)
								handler.on(instream);
						}  finally {
							instream.close();
						}
					} else {
						throw new RuntimeException("Command Failed");
					}
				} finally {
					response.close();
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				try {
					httpClient.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
		
		public Collection<Location> getLocations() {
			
			final Collection<Location> list = new ArrayList<Location>();
			
			sendCommand(login.uid +"/locations", new InputStreamHandler(){
				public void on(InputStream in) {
					JsonElement json = new JsonParser().parse(new InputStreamReader(in));
					JsonObject l = json.getAsJsonObject().get("locations").getAsJsonObject();
					for(Entry<String, JsonElement> e: l.entrySet()) {
						LocationImpl location = new LocationImpl();
							location.id = e.getKey();							
							location.street1 = e.getValue().getAsJsonObject().get("street1").getAsString();
							location.street2 = e.getValue().getAsJsonObject().get("street2").getAsString();
							location.city = e.getValue().getAsJsonObject().get("city").getAsString();
							location.state = e.getValue().getAsJsonObject().get("state").getAsString();
							location.postalCode = e.getValue().getAsJsonObject().get("postal_code").getAsString();
							location.status = e.getValue().getAsJsonObject().get("s_status").getAsString();
							location.systemState = SYSTEM_STATE.valueOf(e.getValue().getAsJsonObject().get("system_state").getAsString().toUpperCase());
						list.add(location);
					}
				}				
			});
			
			return list;			
		}

		public void setAlarmState(Location location, SYSTEM_STATE newState) {
			sendCommand(login.uid +"/sid/" + location.getId() + "/set-state", null, 
					new BasicNameValuePair("mobile", "1"), new BasicNameValuePair("state", newState.name().toLowerCase()));
		}

		public Collection<Event> getEvents(Location location) {
			final Collection<Event> list = new ArrayList<Event>();
			
			sendCommand(login.uid +"/sid/" + location.getId() + "/events", new InputStreamHandler(){
				public void on(InputStream in) {
					JsonElement json = new JsonParser().parse(new InputStreamReader(in));
					
					JsonArray l = json.getAsJsonObject().get("events").getAsJsonArray();
					for(JsonElement e: l) {
						EventImpl event = new EventImpl();
						event.timestamp = e.getAsJsonObject().get("event_date") + " " + e.getAsJsonObject().get("event_time");
						event.description = e.getAsJsonObject().get("event_desc").getAsString();
						list.add(event);
					}
				}				
			});
			
			return list;		
		}

		public void close() {
			sendCommand("logout", null);
			cookieStore = null;
		}
		
	}
	
	private static class EventImpl implements SimplisafeClient.Event {

		private String timestamp;
		private String description;
		
		public String getTimestamp() {
			return timestamp;
		}

		public String getEventDescription() {
			return description;
		}
		
	}
	
	private static class LocationImpl implements SimplisafeClient.Location {

		private String id;
		private String street1;
		private String street2;
		private String city;
		private String state;
		private String postalCode;
		private String status;
		private SYSTEM_STATE systemState;
		
		public String getId() {
			return id;
		}
		public String getStreet1() {
			return street1;
		}
		public String getStreet2() {
			return street2;
		}
		public String getCity() {
			return city;
		}
		public String getState() {
			return state;
		}
		public String getPostalCode() {
			return postalCode;
		}
		public String getStatus() {
			return status;
		}
		public SYSTEM_STATE getSystemState() {
			return systemState;
		}
	
	}
	
	private static class LoginResponse {
		int return_code;
		String session;
		String uid;
		String username;
	}

	private interface InputStreamHandler {
		void on(InputStream in);
	}
	
	public static void main( String[] args ) throws Exception {
       SimplisafeClient c = new SimpliSafeClientFactory().create(args[0], args[1]);
       for(Location l: c.getLocations()) {
    	   System.out.println(l.getId() + "  " + l.getStreet1() + "  " + l.getSystemState());
    	  for(Event e:  c.getEvents(l)) {
    		  System.out.println("  -- " + e.getTimestamp() + "  " + e.getEventDescription());
    	  }
       }
    }
	
}
