mpliSafe API
=============

Java API for the SimpliSafe home security system.  Java library is based off of the reverse engineering work done by [http://www.leftovercode.info/simplisafe.php](http://www.leftovercode.info/simplisafe.php)

Simple Usage
---------------------

1. Create a client

        SimplisafeClient c = new SimpliSafeClientFactory().create("username", "password");

1.  List Locations

        for(Location l: c.getLocations()) {
    	   System.out.println(l.getId() + "  " + l.getStreet1() + "  " + l.getSystemState());
        }

1.  Change System State

        for(Location l: c.getLocations()) {
    	   c.setAlarmState(l, SYSTEM_STATE.AWAY);
        }

