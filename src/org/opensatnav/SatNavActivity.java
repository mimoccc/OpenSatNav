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

import java.util.ArrayList;
import java.util.List;

import org.andnav.osm.util.GeoPoint;
import org.andnav.osm.util.TypeConverter;
import org.andnav.osm.util.constants.OpenStreetMapConstants;
import org.andnav.osm.views.OpenStreetMapView;
import org.andnav.osm.views.overlay.OpenStreetMapViewDirectedLocationOverlay;
import org.andnav.osm.views.overlay.OpenStreetMapViewRouteOverlay;
import org.andnav.osm.views.util.OpenStreetMapRendererInfo;
import org.opensatnav.services.Router;
import org.opensatnav.services.YOURSRouter;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.RelativeLayout;
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
	private static final int SELECT_POI = 0;

	// ===========================================================
	// Fields
	// ===========================================================

	private OpenStreetMapView mOsmv;
	private OpenStreetMapViewDirectedLocationOverlay mMyLocationOverlay;
	protected OpenStreetMapViewRouteOverlay routeOverlay;
	protected boolean autoFollowing = true;
	protected Location currentLocation;

	protected ArrayList<String> route = new ArrayList<String>();

	// ===========================================================
	// Constructors
	// ===========================================================

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState, false); // Pass true here to actually
		// contribute to OSM!
		final RelativeLayout rl = new RelativeLayout(this);

		this.mOsmv = new OpenStreetMapView(this,
				OpenStreetMapRendererInfo.MAPNIK);
		rl.addView(this.mOsmv, new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

		this.mOsmv.setZoomLevel(19);

		if (temporaryLocation != null)
			this.mOsmv.setMapCenter(TypeConverter
					.locationToGeoPoint(temporaryLocation));

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
			final ZoomControls zoomControls = new ZoomControls(this);
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

		this.setContentView(rl);
		// Restore preferences
		SharedPreferences settings = getSharedPreferences(
				OpenSatNavConstants.PREFS_FILE, 0);

		if (!settings.getBoolean("welcomeVersionSeen", false)) {
			displayWelcomeScreen();
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean("welcomeVersionSeen", true);

			// commit edits
			editor.commit();
		}
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Methods from SuperClass/Interfaces
	// ===========================================================

	@Override
	public void onLocationChanged(Location newLocation) {
		if ((newLocation != null)
				&& ((currentLocation == null) || (currentLocation
						.distanceTo(newLocation) < 50))) {
			this.mMyLocationOverlay.setLocation(TypeConverter
					.locationToGeoPoint(newLocation));
			this.mMyLocationOverlay.setBearing(newLocation.getBearing());
			this.mMyLocationOverlay.setSpeed(newLocation.getSpeed());
			this.mMyLocationOverlay.setAccuracy(newLocation.getAccuracy());
			if (this.autoFollowing)
				this.mOsmv.setMapCenter(TypeConverter
						.locationToGeoPoint(newLocation));
			currentLocation = newLocation;
			// cache tiles around this one
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu pMenu) {
		MenuItem directionsMenuItem = pMenu.add(0, MENU_GET_DIRECTIONS,
				Menu.NONE, R.string.get_directions);
		directionsMenuItem.setIcon(android.R.drawable.ic_menu_directions);
		MenuItem poisMenuItem = pMenu.add(0, MENU_FIND_POIS, Menu.NONE, this
				.getResources().getText(R.string.find_nearest));
		poisMenuItem.setIcon(android.R.drawable.ic_menu_search);
		MenuItem toggleAutoFollowMenuItem = pMenu.add(0,
				MENU_TOGGLE_FOLLOW_MODE, Menu.NONE, R.string.planning_mode);
		toggleAutoFollowMenuItem.setIcon(android.R.drawable.ic_menu_mapmode);
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
		case MENU_FIND_POIS:
			if (currentLocation != null) {
				Intent intent1 = new Intent(this,
						org.opensatnav.SelectPOIActivity.class);
				intent1.setData(Uri.parse(currentLocation.getLatitude() + ","
						+ currentLocation.getLongitude()));
				startActivityForResult(intent1, SELECT_POI);
			} else
				Toast.makeText(this, R.string.start_directions_failed,
						Toast.LENGTH_LONG).show();
			return true;
		case MENU_RENDERER_ID:
			this.mOsmv.invalidate();
			return true;
		case MENU_TOGGLE_FOLLOW_MODE:
			if (this.autoFollowing) {
				this.autoFollowing = false;
				Toast.makeText(this, R.string.planning_mode_on,
						Toast.LENGTH_SHORT).show();
				// this weird style is required to set multiple attributes on
				// the item
				item.setTitle(R.string.navigation_mode).setIcon(
						android.R.drawable.ic_menu_mylocation);
			} else {
				this.autoFollowing = true;
				Toast.makeText(this, R.string.navigation_mode_on,
						Toast.LENGTH_SHORT).show();
				item.setTitle(R.string.planning_mode).setIcon(
						android.R.drawable.ic_menu_mapmode);
			}
			return true;
		case MENU_PREFERENCES:
			Intent intent = new Intent(this,
					org.opensatnav.ConfigurationActivity.class);
			startActivityForResult(intent, MENU_PREFERENCES);
			return true;
		case MENU_ABOUT:
			Intent intent1 = new Intent(
					"org.openintents.action.SHOW_ABOUT_DIALOG");
			startOrDownloadActivity(intent1);
			
			return true;
		default:
			this.mOsmv.setRenderer(OpenStreetMapRendererInfo.values()[item
					.getItemId() - 1000]);
		}
		return false;
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if ((requestCode == DIRECTIONS_OPTIONS) || (requestCode == SELECT_POI)) {
			if (resultCode == RESULT_OK) {
				final GeoPoint to = GeoPoint.fromIntString(data
						.getStringExtra("to"));
				final String vehicle = data.getStringExtra("vehicle");
				refreshRoute(TypeConverter.locationToGeoPoint(currentLocation),
						to, vehicle);
			}
		}

	}
	
	private void startOrDownloadActivity(Intent intent) {
		final PackageManager packageManager = this.getPackageManager();
        List<ResolveInfo> list =
                packageManager.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        if (list.size()!=0) {
           	startActivityForResult(intent, MENU_ABOUT);
        }
        else {
        	new AlertDialog.Builder(this).setIcon(android.R.drawable.stat_notify_sync).setTitle("Android Market")
            .setMessage(R.string.about_get_market)
            .setPositiveButton(R.string.about_get_market_download, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					Uri uri = Uri.parse("market://search?q=pname:org.openintents.about");
					Intent intentAbout = new Intent(Intent.ACTION_VIEW);
					intentAbout.setData(uri);
					startActivity(intentAbout);
				}
			}).setNegativeButton(R.string.about_get_market_cancel, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// nothing to do
				}
			})
            .show();
        }
	}

	public void refreshRoute(final GeoPoint from, final GeoPoint to,
			final String vehicle) {
		final ProgressDialog progress = ProgressDialog.show(
				SatNavActivity.this, this.getResources().getText(
						R.string.please_wait), this.getResources().getText(
						R.string.getting_route), true, true);
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
				progress.dismiss();
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

	public void onSaveInstanceState(Bundle savedInstanceState) {
		savedInstanceState.putStringArrayList("route", route);
		super.onSaveInstanceState(savedInstanceState);
	}

	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		route = savedInstanceState.getStringArrayList("route");
		if (route.size() > 0) {
			ArrayList<GeoPoint> niceRoute = new ArrayList<GeoPoint>();
			for (int i = 0; i < route.size(); i++) {
				GeoPoint nextPoint = GeoPoint.fromIntString(this.route.get(i));
				niceRoute.add(nextPoint);
			}
			this.routeOverlay = new OpenStreetMapViewRouteOverlay(this,
					niceRoute);
			this.mOsmv.getOverlays().add(this.routeOverlay);
		}
	}

	private void displayWelcomeScreen() {

		/*
		 * FIXME ZeroG : need to sleep 500ms (dangerous, arbitrary value) in
		 * order to let SatNavActivity start completely before starting the
		 * WelcomeActivity, otherwise, it only displays a black screen on
		 * Android 1.5 Should maybe be replaced by a call after the onCreate
		 * method (onStart ?).
		 */
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Intent intent = new Intent(SatNavActivity.this,
						org.opensatnav.WelcomeActivity.class);
				SatNavActivity.this.startActivity(intent);

			}

		}).start();
	}

}
