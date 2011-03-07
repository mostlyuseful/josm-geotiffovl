/*  This file is part of geotiffovl.

    geotiffovl is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    geotiffovl is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.openstreetmap.josm.plugins.geotiffovl;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.KeyEvent;

import javax.swing.JMenu;

import org.gdal.gdal.gdal;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

public class GeoTiffOvlPlugin extends Plugin {

	static {
		// Mark this file for translation
		marktr("");
	}

	private JMenu m_menu = null;
	private AddGeoTiffOverlayAction m_addGeoTiffOverlayAction = null;

	public GeoTiffOvlPlugin(PluginInformation info) {
		super(info);

		// Create menu
		if (Main.main.menu != null) {
			m_menu = Main.main.menu.addMenu(tr("GeoTiff"), KeyEvent.VK_G,
					Main.main.menu.defaultMenuPos, "");
		}

		// Create menu items
		if (m_menu != null) {
			m_addGeoTiffOverlayAction = new AddGeoTiffOverlayAction();
			m_menu.add(m_addGeoTiffOverlayAction);
		}

		// Register raster formats, needed to actually open a file
		gdal.AllRegister();
	}
}