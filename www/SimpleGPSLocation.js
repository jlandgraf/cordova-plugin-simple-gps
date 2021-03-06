/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

var argscheck = require('cordova/argscheck'),
	utils = require('cordova/utils'),
	exec = require('cordova/exec'),
	PositionError = require('./PositionError'),
	Position = require('./Position');

var timers = {}; // list of timers in use

// Returns default params, overrides if provided with values
function parseParameters(options) {
	var opt = {
		maximumAge: 0,
		timeout: Infinity,
		useLastLocation : false,
	};

	if (options) {
		if (options.maximumAge !== undefined && !isNaN(options.maximumAge) && options.maximumAge > 0) {
			opt.maximumAge = options.maximumAge;
		}
		if (options.timeout !== undefined && !isNaN(options.timeout)) {
			if (options.timeout < 0) {
				opt.timeout = 0;
			} else {
				opt.timeout = options.timeout;
			}
		}
		if (options.useLastLocation !== undefined) {
			opt.useLastLocation = options.useLastLocation;
		}
	}

	return opt;
}

// Returns a timeout failure, closed over a specified timeout value and error callback.
function createTimeout(errorCallback, timeout) {
	var t = setTimeout(function () {
		clearTimeout(t);
		t = null;
		errorCallback({
			code: PositionError.TIMEOUT,
			message: "Position retrieval timed out."
		});
	}, timeout);
	return t;
}

var SimpleGPSLocation = {
	lastPosition: null, // reference to last known (cached) position returned
	/**
	 * Asynchronously acquires the current position.
	 *
	 * @param {Function} successCallback    The function to call when the position data is available
	 * @param {Function} errorCallback      The function to call when there is an error getting the heading position. (OPTIONAL)
	 * @param {PositionOptions} options     The options for getting the position data. (OPTIONAL)
	 */
	getCurrentPosition: function (successCallback, errorCallback, options) {
		console.log('getCurrentPosition called');
		console.log(options);
		argscheck.checkArgs('fFO', 'SimpleGPSLocation.getCurrentPosition', arguments);
		options = parseParameters(options);

		// Timer var that will fire an error callback if no position is retrieved from native
		// before the "timeout" param provided expires

		console.log('options.timeout:' + options.timeout + '\n' + 
			'options.maximumAge:' + options.maximumAge + '\n' +
			'options.useLastLocation:' + options.useLastLocation + '\n'
			);
		var timeoutTimer = {
			timer: null
		};
		//console.log('declaring the functions');
		var win = function (p) {
			console.log('win resolve');
			clearTimeout(timeoutTimer.timer);
			if (!(timeoutTimer.timer)) {
				// Timeout already happened, or native fired error callback for
				// this geo request.
				// Don't continue with success callback.
 				console.log('timeout already happened');
				return;
			}
			//console.log('win:' + p.latitude + ',' + p.longitude);
			var pos = new Position({
				latitude: p.latitude,
				longitude: p.longitude,
				altitude: p.altitude,
				accuracy: p.accuracy,
				heading: p.heading,
				velocity: p.velocity,
				altitudeAccuracy: p.altitudeAccuracy
			}, (p.timestamp === undefined ? new Date() : ((p.timestamp instanceof Date) ? p.timestamp : new Date(p.timestamp))));
			SimpleGPSLocation.lastPosition = pos;
			successCallback(pos);
		};
		var fail = function (e) {
			console.log('fail resolve');
			console.log(e);
			clearTimeout(timeoutTimer.timer);
			timeoutTimer.timer = null;
			var err = new PositionError(!!e.code ? e.code : -999, !!e.message ? e.message : 'unknown error');
			if (errorCallback) {
				errorCallback(err);
			}
		};

		if (SimpleGPSLocation.lastPosition && options.maximumAge && (((new Date()).getTime() - SimpleGPSLocation.lastPosition.timestamp.getTime()) <= options.maximumAge)) {
			console.log('returning cached last Position');
			successCallback(SimpleGPSLocation.lastPosition);
			// If the cached position check failed and the timeout was set to 0, error out with a TIMEOUT error object.
		} else if (options.timeout === 0) {
			console.log('error because of timeout = 0');
			fail({
				code: PositionError.TIMEOUT,
				message: "timeout value in PositionOptions set to 0 and no cached Position object available, or cached Position object's age exceeds provided PositionOptions' maximumAge parameter."
			});
			// Otherwise we have to call into native to retrieve a position.
		} else {
			console.log('calling java');
			if (options.timeout !== Infinity) {
				// If the timeout value was not set to Infinity (default), then
				// set up a timeout function that will fire the error callback
				// if no successful position was retrieved before timeout expired.
				timeoutTimer.timer = createTimeout(fail, options.timeout);
			} else {
				// This is here so the check in the win function doesn't mess stuff up
				// may seem weird but this guarantees timeoutTimer is
				// always truthy before we call into native
				timeoutTimer.timer = true;
			}
			//console.log('permission first, then actual calling');
			var permissionWin = function () {
				console.log('permission Success' + options.maximumAge +',' + options.useLastLocation);
	            //var geo = cordova.require('cordova/modulemapper').getOriginalSymbol(window, 'navigator.geolocation'); // eslint-disable-line no-undef
				exec(win, fail, 'SimpleGPSLocation', 'getLocation', [options.maximumAge, options.useLastLocation]);
	        };
	        var permissionFail = function () {
				console.log('Permission Failed');
	            if (errorCallback) {
	                errorCallback(new PositionError(PositionError.PERMISSION_DENIED, 'Illegal Access'));
	            }
	        };
	 		exec(permissionWin, permissionFail, 'SimpleGPSLocation', 'getPermission', []);
		}
		//console.log('returning timeoutTimer');
		return timeoutTimer;
	},
};

module.exports = SimpleGPSLocation;
