/* 
This file is part of OpenSatNav.

    OpenSatNav is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    OpenSatNav is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with OpenSatNav.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.opensatnav;

import java.io.IOException;
import java.util.ArrayList;

import org.anddev.openstreetmap.contributor.util.GPXToFileWriter;
import org.anddev.openstreetmap.contributor.util.OSMUploader;
import org.anddev.openstreetmap.contributor.util.RecordedGeoPoint;
import org.anddev.openstreetmap.contributor.util.RecordedWayPoint;
import org.anddev.openstreetmap.contributor.util.RouteRecorder;
import org.andnav.osm.util.GeoPoint;
import org.andnav.osm.util.TypeConverter;
import org.andnav.osm.util.constants.OpenStreetMapConstants;
import org.andnav.osm.views.OpenStreetMapView;
import org.andnav.osm.views.OpenStreetMapView.OpenStreetMapViewProjection;
import org.andnav.osm.views.overlay.OpenStreetMapViewDirectedLocationOverlay;
import org.andnav.osm.views.overlay.OpenStreetMapViewOldTraceOverlay;
import org.andnav.osm.views.overlay.OpenStreetMapViewRouteOverlay;
import org.andnav.osm.views.overlay.OpenStreetMapViewTraceOverlay;
import org.andnav.osm.views.util.OpenStreetMapRendererInfo;
import org.opensatnav.services.Router;
import org.opensatnav.services.TripStatistics;
import org.opensatnav.services.TripStatisticsListener;
import org.opensatnav.services.YOURSRouter;
import org.opensatnav.util.BugReportExceptionHandler;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Point;
import android.graphics.Rect;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ZoomControls;
import android.widget.RelativeLayout.LayoutParams;

/**
 * 
 * @author Kieran Fleming
 * 
 */

public class SatNavActivity extends OpenStreetMapActivity implements
		OpenStreetMapConstants {
	// ===========================================================
	// Constants
	// ===========================================================

	private static final int MENU_ZOOMIN_ID = Menu.FIRST;
	private static final int MENU_ZOOMOUT_ID = MENU_ZOOMIN_ID + 1;
	private static final int MENU_RENDERER_ID = MENU_ZOOMOUT_ID + 1;
	private static final int MENU_TOGGLE_FOLLOW_MODE = MENU_RENDERER_ID + 1;
	private static final int MENU_FIND_POIS = MENU_TOGGLE_FOLLOW_MODE + 1;
	private static final int MENU_GET_DIRECTIONS = MENU_FIND_POIS + 1;
	private static final int MENU_PREFERENCES = MENU_GET_DIRECTIONS + 1;
	private static final int MENU_ABOUT = MENU_PREFERENCES + 1;
	private static final int DIRECTIONS_OPTIONS = MENU_ABOUT + 1;
	private static final int MENU_CONTRIBUTE = DIRECTIONS_OPTIONS + 1;
	private static final int MENU_TRIP_STATS = MENU_CONTRIBUTE + 1;

	private static final int SELECT_POI = 0;
	private static final int CONTRIBUTE = SELECT_POI + 1;
	private static final int UPLOAD_NOW = 10;
	private static final int TRACE_TOGGLE = UPLOAD_NOW + 1;
	private static final int DELETE_TRACKS = TRACE_TOGGLE + 1;
	private static final int NEW_WAYPOINT = DELETE_TRACKS + 1;
	private static final int CLEAR_OLD_TRACES = NEW_WAYPOINT + 1;

	// ===========================================================
	// Fields
	// ===========================================================

	private OpenStreetMapView mOsmv;
	private ZoomControls zoomControls;
	private TripStatisticsController mTripStatsController;

	private OpenStreetMapViewDirectedLocationOverlay mMyLocationOverlay;
	protected OpenStreetMapViewRouteOverlay routeOverlay;
	protected OpenStreetMapViewTraceOverlay traceOverlay;
	protected OpenStreetMapViewTraceOverlay oldTraceOverlay;
	protected boolean autoFollowing = true;
	protected boolean viewingTripStatistics = false;
	protected boolean gettingRoute = false;
	protected Time latestRouteReceived;
	protected Location currentLocation;
	protected GeoPoint to;
	protected String vehicle;

	protected ArrayList<String> route = new ArrayList<String>();

	

	// ===========================================================
	// Constructors
	// ===========================================================

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		BugReportExceptionHandler.register(this);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
		final RelativeLayout rl = new RelativeLayout(this);
		latestRouteReceived = new Time();
		latestRouteReceived.set(0,0,0,0,0,1970);

		this.mOsmv = new OpenStreetMapView(this,
				OpenStreetMapRendererInfo.MAPNIK) {
			@Override
			public boolean onTouchEvent(MotionEvent event) {
				// switches to 'planning mode' as soon as you scroll anywhere
				if (event.getAction() == MotionEvent.ACTION_MOVE
						&& SatNavActivity.this.autoFollowing == true) {
					SatNavActivity.this.autoFollowing = false;
					SatNavActivity.this.displayToast(R.string.planning_mode_on);
				}
				return super.onTouchEvent(event);
			}
		};
		rl.addView(this.mOsmv, new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

		this.mOsmv.setZoomLevel(19);

		if (mLocationHandler.getFirstLocation() != null)
			this.mOsmv.setMapCenter(TypeConverter
					.locationToGeoPoint(mLocationHandler.getFirstLocation()));

		/* SingleLocation-Overlay */
		{
			/*
			 * Create a static Overlay showing a single location. (Gets updated
			 * in onLocationChanged(Location loc)!
			 */
			this.mMyLocationOverlay = new OpenStreetMapViewDirectedLocationOverlay(
					this);
			this.mOsmv.getOverlays().add(mMyLocationOverlay);

		}

		/* ZoomControls */
		{
			zoomControls = new ZoomControls(this);
			// by default we are zoomed in to the max
			zoomControls.setIsZoomInEnabled(false);
			zoomControls.setOnZoomOutClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					SatNavActivity.this.mOsmv.zoomOut();
					if (!SatNavActivity.this.mOsmv.canZoomOut())
						zoomControls.setIsZoomOutEnabled(false);
					zoomControls.setIsZoomInEnabled(true);
				}
			});
			zoomControls.setOnZoomInClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					SatNavActivity.this.mOsmv.zoomIn();
					if (!SatNavActivity.this.mOsmv.canZoomIn())
						zoomControls.setIsZoomInEnabled(false);
					zoomControls.setIsZoomOutEnabled(true);
				}
			});

			final RelativeLayout.LayoutParams zoomParams = new RelativeLayout.LayoutParams(
					RelativeLayout.LayoutParams.WRAP_CONTENT,
					RelativeLayout.LayoutParams.WRAP_CONTENT);
			zoomParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
			zoomParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
			rl.addView(zoomControls, zoomParams);

		}

		// Trip statistics
		mTripStatsController = new TripStatisticsController(SatNavActivity.this);
		mTripStatsController.addViewTo(rl);

		// for after configuration change like keyboard open/close
	    final TripStatistics.TripStatisticsStrings data = 
	    	(TripStatistics.TripStatisticsStrings)getLastNonConfigurationInstance();
	    
	    if (data != null) {
	    	mTripStatsController.setAllStats(data);
	    }
		
		
		this.setContentView(rl);
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Methods from SuperClass/Interfaces
	// ===========================================================

	@Override
	public void onLocationChanged(Location newLocation) {
		if (newLocation != null) {
			this.mMyLocationOverlay.setLocation(TypeConverter
					.locationToGeoPoint(newLocation));
			this.mMyLocationOverlay.setBearing(newLocation.getBearing());
			this.mMyLocationOverlay.setSpeed(newLocation.getSpeed());
			this.mMyLocationOverlay.setAccuracy(newLocation.getAccuracy());

			if( autoFollowing ) {
				this.mOsmv.setMapCenter(TypeConverter.locationToGeoPoint(newLocation));
			} else {
				// tell the viewer that it should redraw
				SatNavActivity.this.mOsmv.postInvalidate();
			}
			
			if (TraceRecorderService.isTracing()) {
				refreshTracks();
			}
			if (OpenSatNavConstants.DEBUGMODE)
				Log.v(OpenSatNavConstants.LOG_TAG, "Accuracy: "
						+ newLocation.getAccuracy());
			currentLocation = newLocation;

			/*
			 * 2 situations where we want to fetch the route again: 1: if we got
			 * back from ChooseLocationActivity and didn't have a location yet
			 * and the accuracy's good enough (otherwise GPS will probably kick
			 * in soon and it's best to wait) 2: if the user has moved off the
			 * route (if we judge it's worth it based on what the user's doing)
			 */
			if (this.to != null && this.autoFollowing
					&& this.mOsmv.getZoomLevel() > 10
					&& newLocation.getAccuracy() < 40) {
				if (this.routeOverlay != null) {
					int tolerance = 250;
					// metres that the user can go before
					// we need to get the route again
					OpenStreetMapViewProjection pj = this.mOsmv.getProjection();
					int pixelToleranceRadius = (int) (pj
							.metersToEquatorPixels(tolerance) * 100);
					Point pointLocation = pj.toPixels(TypeConverter
							.locationToGeoPoint(currentLocation), null);

					// constuct a Rect that defines the area where the user
					// should be within if on the route
					Rect onRoute = new Rect(pointLocation.x
							- pixelToleranceRadius, pointLocation.y
							- pixelToleranceRadius, pointLocation.x
							+ pixelToleranceRadius, pointLocation.y
							+ pixelToleranceRadius);
					ArrayList<Point> pixelRoute = this.routeOverlay
							.getPixelRoute();

					// if all of the route segments fail to intersect
					// we need a new route
					int offRouteCount = 0;
					for (int i = 0; i < pixelRoute.size() - 1; i++) {
						Rect routeSegment = new Rect(pixelRoute.get(i + 1).x,
								pixelRoute.get(i + 1).y, pixelRoute.get(i).x,
								pixelRoute.get(i).y);
						if (Rect.intersects(onRoute, routeSegment))
							break;
						else
							offRouteCount++;
					}
					if (offRouteCount == pixelRoute.size() - 1) {
						refreshRoute(TypeConverter
								.locationToGeoPoint(currentLocation), to,
								vehicle);
					}
				} else
					refreshRoute(TypeConverter
							.locationToGeoPoint(currentLocation), to, vehicle);

			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu pMenu) {
		MenuItem directionsMenuItem = pMenu.add(0, MENU_GET_DIRECTIONS,
				Menu.NONE, R.string.get_directions);
		directionsMenuItem.setIcon(android.R.drawable.ic_menu_directions);

		MenuItem contributeMenuItem = pMenu.add(0, MENU_CONTRIBUTE, Menu.NONE,
				R.string.menu_contribute);
		contributeMenuItem.setIcon(android.R.drawable.ic_menu_edit);
		MenuItem toggleAutoFollowMenuItem = pMenu.add(0,
				MENU_TOGGLE_FOLLOW_MODE, Menu.NONE, R.string.planning_mode);
		toggleAutoFollowMenuItem.setIcon(android.R.drawable.ic_menu_mapmode);
		MenuItem tripStatsMenuItem = pMenu.add(0,MENU_TRIP_STATS,Menu.NONE,
				R.string.menu_show_trip_stats);
		tripStatsMenuItem.setIcon(android.R.drawable.ic_menu_recent_history);
		MenuItem prefsMenuItem = pMenu.add(0, MENU_PREFERENCES, Menu.NONE,
				R.string.preferences);
		prefsMenuItem.setIcon(android.R.drawable.ic_menu_preferences);
		MenuItem aboutMenuItem = pMenu.add(0, MENU_ABOUT, Menu.NONE,
				R.string.about);
		aboutMenuItem.setIcon(android.R.drawable.ic_menu_info_details);

		// uncomment if you want to enable map mode switching
		// SubMenu mapModeMenuItem = pMenu.addSubMenu(0, MENU_RENDERER_ID,
		// Menu.NONE, "Map mode");
		// {
		// for (int i = 0; i < OpenStreetMapRendererInfo.values().length; i++)
		// mapModeMenuItem.add(0, 1000 + i, Menu.NONE,
		// OpenStreetMapRendererInfo.values()[i].NAME);
		// }
		// mapModeMenuItem.setIcon(android.R.drawable.ic_menu_mapmode);
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case MENU_GET_DIRECTIONS:
			if (currentLocation != null) {
				Intent intent = new Intent(this,
						org.opensatnav.GetDirectionsActivity.class);
				intent.setData(Uri.parse(currentLocation.getLatitude() + ","
						+ currentLocation.getLongitude()));

				startActivityForResult(intent, DIRECTIONS_OPTIONS);
			} else
				Toast.makeText(this, R.string.start_directions_failed,
						Toast.LENGTH_LONG).show();
			return true;
		case MENU_CONTRIBUTE:

			Intent intentContribute = new Intent(this,
					org.opensatnav.ContributeActivity.class);
			startActivityForResult(intentContribute, CONTRIBUTE);

			return true;
		case MENU_RENDERER_ID:
			this.mOsmv.invalidate();
			return true;
		case MENU_TOGGLE_FOLLOW_MODE:
			if (this.autoFollowing) {
				this.autoFollowing = false;
				Toast.makeText(this, R.string.planning_mode_on,
						Toast.LENGTH_SHORT).show();
			} else {
				this.autoFollowing = true;
				Toast.makeText(this, R.string.navigation_mode_on,
						Toast.LENGTH_SHORT).show();
			}
			return true;
		case MENU_PREFERENCES:
			Intent intent = new Intent(this,
					org.opensatnav.ConfigurationActivity.class);
			startActivityForResult(intent, MENU_PREFERENCES);

			return true;
		case MENU_ABOUT:
			Intent intent1 = new Intent(this, org.openintents.about.About.class);
			startActivityForResult(intent1, MENU_ABOUT);

			return true;
		case MENU_TRIP_STATS:
			viewingTripStatistics = true;
			showTripStatistics(true);

			return true;
			
		default:
			this.mOsmv.setRenderer(OpenStreetMapRendererInfo.values()[item
					.getItemId() - 1000]);
		}
		return false;
	}

	/** Display trip statistics */
	public void showTripStatistics(boolean show) {
		if( show ) {
			mTripStatsController.setVisible(true);
			mOsmv.setVisibility(View.GONE);
			zoomControls.setVisibility(View.GONE);
		} else {
			mTripStatsController.setVisible(false);
			mOsmv.setVisibility(View.VISIBLE);
			zoomControls.setVisibility(View.VISIBLE);
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem item = menu.findItem(MENU_TOGGLE_FOLLOW_MODE);
		if (!(this.autoFollowing)) {

			// this weird style is required to set multiple attributes on
			// the item

			item.setTitle(R.string.navigation_mode).setIcon(
					android.R.drawable.ic_menu_mylocation);
		} else {

			item.setTitle(R.string.planning_mode).setIcon(
					android.R.drawable.ic_menu_mapmode);
		}
		return true;
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {

		if ((requestCode == DIRECTIONS_OPTIONS) || (requestCode == SELECT_POI)) {
			if (resultCode == RESULT_OK) {
				to = GeoPoint.fromIntString(data.getStringExtra("to"));
				vehicle = data.getStringExtra("vehicle");
				if (currentLocation != null)
					refreshRoute(TypeConverter
							.locationToGeoPoint(currentLocation), to, vehicle);
			}
		}
		if (requestCode == CONTRIBUTE) {
			if (OpenSatNavConstants.DEBUGMODE)
				Log.v(OpenSatNavConstants.LOG_TAG, "Called contribute");
			RouteRecorder mRouteRecorder = TraceRecorderService
					.getRouteRecorder();
			if (resultCode == UPLOAD_NOW) {
				// Check actually got some traces:
				if (mRouteRecorder == null
						|| mRouteRecorder.getRecordedGeoPoints().size() == 0) {
					displayToast(R.string.contribute_error_no_traces);
				} else {

					ProgressDialog dialog = ProgressDialog
							.show(
									this,
									"",
									getText(R.string.contribute_uploading_traces),
									true);

					dialog.show();

					// Check logged in:
					SharedPreferences prefs = PreferenceManager
							.getDefaultSharedPreferences(this);
					String username = prefs.getString(
							getString(R.string.pref_username_key), null);
					String password = prefs.getString(
							getString(R.string.pref_password_key), null);
					if (username == null || password == null) {
						displayToast(R.string.contribute_error_enter_osm_login_details);
					} else {
						TraceRecorderService.stop(this);
						try {

							String description = data
									.getStringExtra("description");
							OSMUploader.uploadAsync(this, TraceRecorderService
									.getRouteRecorder(), username, password,
									description);
							GPXToFileWriter.writeToFileAsync(TraceRecorderService.getRouteRecorder().getRecordedGeoPoints());

							String resultsTextFormat = getString(R.string.contribute_track_uploaded);
							String resultsText = String.format(
									resultsTextFormat, description);

							displayToast(resultsText);
							
							TraceRecorderService.resetTrace();

						} catch (IOException e) {
							displayToast(getString(R.string.contribute_track_error_happened)
									+ e); 
							// i think this is never called because expections happen
							// earlier in the code
							Log.e(OpenSatNavConstants.LOG_TAG,
									"Error uploading route to openstreemaps.",
									e);
						}
					}
					if (dialog.isShowing())
						try {
							dialog.dismiss();
						} catch (IllegalArgumentException e) {
							// if orientation change, thread continue but the dialog cannot be dismissed without exception
						}
				}

			}

			if (resultCode == NEW_WAYPOINT) {
				if (mRouteRecorder == null) {
					// it shouldn't be possible to get here... but regardless
					displayToast(R.string.contribute_error_no_traces);
					Log.v(OpenSatNavConstants.LOG_TAG,
							"Cannot add waypoint when not recording a trace!");
				} else if (mRouteRecorder.getRecordedGeoPoints().size() != 0) {
					String wayPointName = data.getStringExtra("wayPointName");
					mRouteRecorder.addWayPoint(wayPointName);
					String resultsTextFormat = getString(R.string.contribute_waypoint_added);
					String resultsText = String.format(resultsTextFormat,
							wayPointName);
					displayToast(resultsText);
				} else {
					displayToast(R.string.contribute_waypoing_gps_fix_not_good);
				}
			}
			if (resultCode == CLEAR_OLD_TRACES) {
				
				displayToast("Done");
			}
			refreshTracks();
		}

	}

	public void refreshTracks() {
		Log.v("OSM", "Refreshing tracks");
		RouteRecorder routeRecorder = getRouteRecorder();
		if (routeRecorder != null
				&& routeRecorder.getRecordedGeoPoints() != null) {
			if (SatNavActivity.this.mOsmv.getOverlays().contains(
					SatNavActivity.this.traceOverlay)) {
				SatNavActivity.this.mOsmv.getOverlays().remove(
						SatNavActivity.this.traceOverlay);
			}
			SatNavActivity.this.traceOverlay = new OpenStreetMapViewTraceOverlay(
					SatNavActivity.this, routeRecorder);
			SatNavActivity.this.mOsmv.getOverlays().add(
					SatNavActivity.this.traceOverlay);
			if (OpenSatNavConstants.DEBUGMODE)
				Log.v(OpenSatNavConstants.LOG_TAG, "Drew "
						+ routeRecorder.getRecordedGeoPoints().size()
						+ " points");
			// tell the viewer that it should redraw
			SatNavActivity.this.mOsmv.postInvalidate();
		} else {
			Log.v("killme", "RR null");
		}

		

	}

	public void refreshRoute(final GeoPoint from, final GeoPoint to,
			final String vehicle) {
		latestRouteReceived.second+=15;
		Time now = new Time();
		now.setToNow();
		if (!gettingRoute && latestRouteReceived.before(now)) {
			gettingRoute = true;
			final ProgressDialog progress = ProgressDialog.show(
					SatNavActivity.this, this.getResources().getText(
							R.string.please_wait), this.getResources().getText(
							R.string.getting_route), true, true, new OnCancelListener() {			
								@Override
								//if the user cancels move the time up anyway so it doesn't popup again
								public void onCancel(DialogInterface dialog) {
									latestRouteReceived.setToNow();
								}
							});
			final Handler handler = new Handler() {
				// threading stuff - this actually handles the stuff after the
				// thread has completed (code below)
				public void handleMessage(Message msg) {
					if (route != null) {
						ArrayList<GeoPoint> niceRoute = new ArrayList<GeoPoint>();
						for (int i = 0; i < route.size(); i++) {
							GeoPoint nextPoint = GeoPoint.fromIntString(route
									.get(i));
							niceRoute.add(nextPoint);
						}

						if (SatNavActivity.this.mOsmv.getOverlays().contains(
								SatNavActivity.this.routeOverlay)) {
							SatNavActivity.this.mOsmv.getOverlays().remove(
									SatNavActivity.this.routeOverlay);
						}
						SatNavActivity.this.routeOverlay = new OpenStreetMapViewRouteOverlay(
								SatNavActivity.this, niceRoute);
						SatNavActivity.this.mOsmv.getOverlays().add(
								SatNavActivity.this.routeOverlay);
						// tell the viewer that it should redraw
						SatNavActivity.this.mOsmv.postInvalidate();
					} else {
						Toast.makeText(
								SatNavActivity.this,
								SatNavActivity.this.getResources().getText(
										R.string.directions_not_found),
								Toast.LENGTH_LONG).show();
					}
					if (progress.isShowing())
						try {
							progress.dismiss();
						} catch (IllegalArgumentException e) {
							// if orientation change, thread continue but the dialog cannot be dismissed without exception
						}
					gettingRoute = false;
					latestRouteReceived.setToNow();
				}
			};
			new Thread(new Runnable() {
				public void run() {
					// put long running operations here
					Router router = new YOURSRouter();
					if (to != null)
						route = router.getRoute(from, to, vehicle,
								SatNavActivity.this);
					// ok, we are done
					handler.sendEmptyMessage(0);
				}
			}).start();
		}
	}

	public void onSaveInstanceState(Bundle savedInstanceState) {
		savedInstanceState.putStringArrayList("route", route);

		savedInstanceState.putInt("zoomLevel", this.mOsmv.getZoomLevel());
		savedInstanceState.putBoolean("autoFollowing", autoFollowing);
		savedInstanceState.putInt("mLatitudeE6", this.mOsmv
				.getMapCenterLatitudeE6());
		savedInstanceState.putInt("mLongitudeE6", this.mOsmv
				.getMapCenterLongitudeE6());
		
		savedInstanceState.putBoolean("viewTripStatistics", viewingTripStatistics);

		if (to != null) {
			savedInstanceState.putInt("toLatitudeE6", to.getLatitudeE6());
			savedInstanceState.putInt("toLongitudeE6", to.getLongitudeE6());
		}

		super.onSaveInstanceState(savedInstanceState);
	}

	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		route = savedInstanceState.getStringArrayList("route");
		if (route != null && route.size() > 0) {
			ArrayList<GeoPoint> niceRoute = new ArrayList<GeoPoint>();
			for (int i = 0; i < route.size(); i++) {
				GeoPoint nextPoint = GeoPoint.fromIntString(this.route.get(i));
				niceRoute.add(nextPoint);
			}
			this.routeOverlay = new OpenStreetMapViewRouteOverlay(this,
					niceRoute);
			this.mOsmv.getOverlays().add(this.routeOverlay);

			if (savedInstanceState.getInt("toLatitudeE6") == 0)
				this.to = new GeoPoint(savedInstanceState
						.getInt("toLatitudeE6"), savedInstanceState
						.getInt("toLongitudeE6"));
		}

		

		autoFollowing = savedInstanceState.getBoolean("autoFollowing");
		this.mOsmv.setZoomLevel(savedInstanceState.getInt("zoomLevel"));
		if (this.mOsmv.canZoomIn()) {
			zoomControls.setIsZoomInEnabled(true);
			if (!this.mOsmv.canZoomOut())
				zoomControls.setIsZoomOutEnabled(false);
		}
		this.mOsmv.setMapCenter(savedInstanceState.getInt("mLatitudeE6"),
				savedInstanceState.getInt("mLongitudeE6"));

		viewingTripStatistics = savedInstanceState.getBoolean("viewTripStatistics");
		if( viewingTripStatistics ) {
			showTripStatistics(true);
		}
	}

	private void displayToast(String msg) {
		Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
	}

	private void displayToast(int stringReference) {
		displayToast((String) getText(stringReference));
	}

	protected RouteRecorder getRouteRecorder() {
		return TraceRecorderService.getRouteRecorder();
	}

	@Override
	protected void onStop() {
		super.onStop();
	}
	
	@Override
	protected void onDestroy() {
		Log.v(OpenSatNavConstants.LOG_TAG, "onDestroy()");
		super.onDestroy();
	}

	/** This works fine for opening/closing keyboard.
	 * Still, is it better to use onSave/Restore inst state()?
	 */
	@Override
	public Object onRetainNonConfigurationInstance() {
		return mTripStatsController.getAllStatistics();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if( keyCode == KeyEvent.KEYCODE_BACK ) {
			if( viewingTripStatistics ) {
				viewingTripStatistics = false;
				showTripStatistics(false);
				return true;
			} else {
				TripStatisticsService.stop(this);
				return super.onKeyDown(keyCode, event);
			}
		}
		return super.onKeyDown(keyCode, event);
	}
	
	void setViewingTripStats(boolean flag) {
		viewingTripStatistics = flag;
	}

}
