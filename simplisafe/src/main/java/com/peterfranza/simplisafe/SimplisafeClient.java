package com.peterfranza.simplisafe;

import java.util.Collection;
/**
 * 
 * Implementation of the simplisafe api as reverse engineered and documented by http://www.leftovercode.info/simplisafe.php
 * 
 * @author pfranza
 *
 */
public interface SimplisafeClient {

	enum SYSTEM_STATE {HOME, AWAY, OFF}
	
	interface Location {
		String getId();
		String getStreet1();
		String getStreet2();
		String getCity();
		String getState();
		String getPostalCode();
		String getStatus();
		SYSTEM_STATE getSystemState();
	}
	
	interface Event {
		String getTimestamp();
		String getEventDescription();
	}
	
	Collection<Location> getLocations();
	
	void setAlarmState(Location location, SYSTEM_STATE newState);
	
	Collection<Event> getEvents(Location location);
	
	void close();
	
}
