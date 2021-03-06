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
package org.andnav.osm.views.overlay;

import org.andnav.osm.util.constants.OpenStreetMapConstants;
import org.andnav.osm.views.OpenStreetMapView;

import android.graphics.Canvas;
import android.view.KeyEvent;
import android.view.MotionEvent;


/**
 * Base class representing an overlay which may be displayed on top of a {@link OpenStreetMapView}. To add an overlay, subclass this class, create an instance, and add it to the list obtained from getOverlays() of {@link OpenStreetMapView}. 
 * @author Nicolas Gramlich
 */
public abstract class OpenStreetMapViewOverlay implements OpenStreetMapConstants{
	// ===========================================================
	// Constants
	// ===========================================================

	// ===========================================================
	// Fields
	// ===========================================================

	// ===========================================================
	// Constructors
	// ===========================================================

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Methods for SuperClass/Interfaces
	// ===========================================================
	
	/**
	 * Managed Draw calls gives Overlays the possibility to first draw manually and after that do a final draw. This is very useful, i sth. to be drawn needs to be <b>topmost</b>.
	 */
	public void onManagedDraw(final Canvas c, final OpenStreetMapView osmv){
		onDraw(c, osmv);
		onDrawFinished(c, osmv);
	}
	
	protected abstract void onDraw(final Canvas c, final OpenStreetMapView osmv);
	
	protected abstract void onDrawFinished(final Canvas c, final OpenStreetMapView osmv);

	// ===========================================================
	// Methods
	// ===========================================================
	
	/**
	 * By default does nothing (<code>return false</code>). If you handled the Event, return <code>true</code>, otherwise return <code>false</code>.
	 * If you returned <code>true</code> none of the following Overlays or the underlying {@link OpenStreetMapView} has the chance to handle this event. 
	 */
	public boolean onKeyDown(final int keyCode, KeyEvent event, final OpenStreetMapView mapView) {
		return false;
	}
	
	/**
	 * By default does nothing (<code>return false</code>). If you handled the Event, return <code>true</code>, otherwise return <code>false</code>.
	 * If you returned <code>true</code> none of the following Overlays or the underlying {@link OpenStreetMapView} has the chance to handle this event. 
	 */
	public boolean onKeyUp(final int keyCode, KeyEvent event, final OpenStreetMapView mapView) {
		return false;
	}
	
	/**
	 * <b>You can prevent all(!) other Touch-related events from happening!</b><br />
	 * By default does nothing (<code>return false</code>). If you handled the Event, return <code>true</code>, otherwise return <code>false</code>.
	 * If you returned <code>true</code> none of the following Overlays or the underlying {@link OpenStreetMapView} has the chance to handle this event. 
	 */
	public boolean onTouchEvent(final MotionEvent event, final OpenStreetMapView mapView) {
		return false;
	}
	
	/**
	 * By default does nothing (<code>return false</code>). If you handled the Event, return <code>true</code>, otherwise return <code>false</code>.
	 * If you returned <code>true</code> none of the following Overlays or the underlying {@link OpenStreetMapView} has the chance to handle this event. 
	 */
	public boolean onTrackballEvent(final MotionEvent event, final OpenStreetMapView mapView) {
		return false;
	}

	/**
	 * By default does nothing (<code>return false</code>). If you handled the Event, return <code>true</code>, otherwise return <code>false</code>.
	 * If you returned <code>true</code> none of the following Overlays or the underlying {@link OpenStreetMapView} has the chance to handle this event. 
	 */
	public boolean onSingleTapUp(MotionEvent e, OpenStreetMapView openStreetMapView) {
		return false;
	}

	/**
	 * By default does nothing (<code>return false</code>). If you handled the Event, return <code>true</code>, otherwise return <code>false</code>.
	 * If you returned <code>true</code> none of the following Overlays or the underlying {@link OpenStreetMapView} has the chance to handle this event. 
	 */
	public boolean onLongPress(MotionEvent e, OpenStreetMapView openStreetMapView) {
		return false;
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================
}
