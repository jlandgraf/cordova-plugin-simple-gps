/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */
package com.coolprofs.cordova.simplegpslocation;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;

import android.content.pm.PackageManager;
import android.content.Context;

import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import android.os.Build;
import android.os.Bundle;
import android.os.Looper;

import android.util.Log;

public class SimpleGPSLocation extends CordovaPlugin {

	public static int PERMISSION_DENIED = 1;
	public static int POSITION_UNAVAILABLE = 2;
	public static int TIMEOUT = 3;

	private static final String TAG = "SimpleLocationPlugin";
	private static final long MIN_UPDATE_INTERVAL_IN_MS = 1 * 1000;
  private static final float MIN_UPDATE_DISTANCE_IN_M = 0;

	private LocationListener mListener;
	private LocationManager mLocationManager;
	CallbackContext _context;

	String [] permissions = { Manifest.permission.ACCESS_COARSE_LOCATION, 
		Manifest.permission.ACCESS_FINE_LOCATION,
		Manifest.permission.ACCESS_BACKGROUND_LOCATION,
		Manifest.permission.FOREGROUND_SERVICE 
	};

	LocationManager getLocationManager() {
		return mLocationManager;
	}

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);


		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
    // We are running a Looper to allow the Cordova CallbackContext to be passed within the Thread as a message.
		mLocationManager = (LocationManager) cordova.getActivity().getSystemService(Context.LOCATION_SERVICE);
		initializeLocationListener();
		
	}

	/**
	 * Executes the request and returns PluginResult.
	 *
	 * @param action
	 *            The action to execute.
	 * @param args
	 *            JSONArry of arguments for the plugin.
	 * @param callbackContext
	 *            The callback id used when calling back into JavaScript.
	 * @return True if the action was valid, or false if not.
	 * @throws JSONException
	 */
	public boolean execute(final String action, final JSONArray args,
			final CallbackContext callbackContext) {

		_context = callbackContext;

		if (action == null || !action.matches("getLocation|getPermission")) {
			fail(99, "unknown action");
			return false;
		}

		if(action.equals("getPermission"))
		{
		    if(hasLocationPermission())
		    {
		        PluginResult r = new PluginResult(PluginResult.Status.OK);
		        r.setKeepCallback(true);
		        _context.sendPluginResult(r);
		        return true;
		    }
		    else {
		        PermissionHelper.requestPermissions(this, 0, permissions);
		    }
		    return true;
		}


		/* only GPS check */
		if (!isProviderEnabled()) {
				fail(POSITION_UNAVAILABLE, "GPS is disabled on this device.");
				return true;
		}

		if (action.equals("getLocation")) {
			getLastLocation(args);
		}
		return true;
	}

	/**
	 * Called when the activity is to be shut down. Stop listener.
	 */
	public void onDestroy() {
    mLocationManager.removeUpdates(mListener);
	}

	public void onResume(boolean multitasking) {
		super.onResume(multitasking);
		initializeLocationListener();
  }

  public void onPause(boolean multitasking) {
  	super.onPause(multitasking);
		this.onDestroy();
  }

	/**
	 * Called when the view navigates. Stop the listeners.
	 */
	public void onReset() {
		this.onDestroy();
	}

	public JSONObject returnLocationJSON(Location loc) {
		JSONObject o = new JSONObject();

		try {
			o.put("latitude", loc.getLatitude());
			o.put("longitude", loc.getLongitude());
			o.put("altitude", (loc.hasAltitude() ? loc.getAltitude() : null));
			o.put("accuracy", loc.getAccuracy());
			o.put("heading",
					(loc.hasBearing() ? (loc.hasSpeed() ? loc.getBearing()
							: null) : null));
			o.put("velocity", loc.getSpeed());
			o.put("timestamp", loc.getTime());
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return o;
	}

	public void win(Location loc) {
		PluginResult result = new PluginResult(PluginResult.Status.OK,
				this.returnLocationJSON(loc));
		result.setKeepCallback(true);
		_context.sendPluginResult(result);
	}

	/**
	 * Location failed. Send error back to JavaScript.
	 * @param code The error code
	 * @param msg  The error message
	 * @throws JSONException
	 */
	public void fail(int code, String msg) {
		JSONObject obj = new JSONObject();
		String backup = null;
		try {
			obj.put("code", code);
			obj.put("message", msg);
		} catch (JSONException e) {
			obj = null;
			backup = "{'code':" + code + ",'message':'"
					+ msg.replaceAll("'", "\'") + "'}";
		}
		PluginResult result;
		if (obj != null) {
			result = new PluginResult(PluginResult.Status.ERROR, obj);
		} else {
			result = new PluginResult(PluginResult.Status.ERROR, backup);
		}

		result.setKeepCallback(true);
		_context.sendPluginResult(result);
	}

	private boolean isProviderEnabled() {
		boolean gps_enabled;
		boolean network_enabled;
		try {
			gps_enabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
		} catch (Exception ex) {
			ex.printStackTrace();
			gps_enabled = false;
		}

		try {
			network_enabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
		} catch (Exception ex) {
			ex.printStackTrace();
			network_enabled = false;
		}
		return gps_enabled || network_enabled;
	}

	private void getLastLocation(JSONArray args) {
		int maximumAge;
		boolean useLastLocation;
		try {
			maximumAge = args.getInt(0);
			useLastLocation = args.getBoolean(1);
		} catch (JSONException e) {
			e.printStackTrace();
			maximumAge = 0;
			useLastLocation = false;
		}

		Criteria criteria = new Criteria();
    //criteria.setAccuracy(Criteria.ACCURACY_HIGH);
    //criteria.setPowerRequirement(Criteria.POWER_LOW);
    //criteria.setAltitudeRequired(false);
    //criteria.setBearingRequired(false);

    //if true then only enabled providers are included
    //LocationManager.GPS_PROVIDER
    boolean enabledOnly = false;
    String provider = mLocationManager.getBestProvider(criteria, enabledOnly);
		Location last = mLocationManager.getLastKnownLocation(provider);

		// Check if we can use lastKnownLocation to get a quick reading and use
		// less battery
		if ((last != null) 
				&& (useLastLocation || ((System.currentTimeMillis() - last.getTime()) <= maximumAge))) 
		{
				win(last);
		} else {
				/* start listening */
		    mLocationManager.requestLocationUpdates(provider, MIN_UPDATE_INTERVAL_IN_MS, MIN_UPDATE_DISTANCE_IN_M, mListener);
		}
	}

	private void initializeLocationListener() {
		if (mListener == null) {
			mListener = new LocationListener() {
				@Override
				public void onLocationChanged(Location location) {
					Log.d(TAG, "The location has been updated!");
					win(location);
				}

				@Override
				public void onProviderDisabled(String provider) {
					if (LocationManager.GPS_PROVIDER.equals(provider)) {
						fail(POSITION_UNAVAILABLE, "GPS provider has been disabled.");
					}
				}

				@Override
				public void onStatusChanged(String provider, int status, Bundle extras) {
					Log.d(TAG, "Provider " + provider + " status changed to " + status);
					fail(POSITION_UNAVAILABLE, "Provider " + provider + " status changed to " + status);
				}

				@Override
				public void onProviderEnabled(String provider) {
					Log.d(TAG, "Provider " + provider + " has been enabled.");
					fail(POSITION_UNAVAILABLE, "Provider " + provider + " has been enabled ");
				}
			};
		}
	}

	public void onRequestPermissionResult(int requestCode, String[] permissions,
	                                      int[] grantResults) throws JSONException
	{
	    PluginResult result;
	    //This is important if we're using Cordova without using Cordova, but we have the geolocation plugin installed
	    if(_context != null) {
	        for (int r : grantResults) {
	            if (r == PackageManager.PERMISSION_DENIED) {
	                Log.d(TAG, "Permission Denied!");
	                result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION);
	                _context.sendPluginResult(result);
	                return;
	            }

	        }
	        result = new PluginResult(PluginResult.Status.OK);
	        _context.sendPluginResult(result);
	    }
	}

	public boolean hasLocationPermission() {
	  for(String p : permissions)
	  {
	      if(!PermissionHelper.hasPermission(this, p))
	      {
	          return false;
	      }
	  }
	  return true;
	}

}
