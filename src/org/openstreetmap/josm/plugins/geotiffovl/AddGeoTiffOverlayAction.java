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

import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;

public class AddGeoTiffOverlayAction extends JosmAction {

	static {
		// Mark this file for translation
		marktr("");
	}

	/**
	 * Needed for serialization
	 */
	private static final long serialVersionUID = -7572571349254359760L;

	/**
	 * Filters files by extension
	 *
	 * To be used with JFileChooser
	 */
	public class FileFilterByExtension extends FileFilter {

		List<String> m_extensions = null;

		/**
		 * Creates the filter
		 *
		 * Filtering is case-insensitive: ".bmp" matches "abc.bmp" as well as
		 * "abc.BmP"
		 *
		 * Example: flt = new FileFilterByExtension(new String[] { ".bmp",
		 * ".abc" });
		 *
		 * @param extensions
		 *            file extensions, INCLUDING the dot!
		 */
		public FileFilterByExtension(String[] extensions) {
			m_extensions = Arrays.asList(extensions);
		}

		@Override
		public boolean accept(File f) {

			if (f.isDirectory()) {
				return true;
			}

			String fn = f.getName();
			for (String ext : m_extensions) {
				if (fn.toLowerCase().endsWith(ext.toLowerCase())) {
					return true;
				}
			}

			return false;
		}

		@Override
		public String getDescription() {
			StringBuilder buf = new StringBuilder();
			Iterator<String> it = m_extensions.iterator();
			while (it.hasNext()) {
				buf.append(it.next());
				// Append separator only if there are more elements
				if (it.hasNext()) {
					buf.append(", ");
				}
			}
			return buf.toString();
		}
	}

	public AddGeoTiffOverlayAction() {
		super(tr("Add GeoTiff overlay"), null, null, null, false);
	}

	@Override
	public void actionPerformed(ActionEvent arg0) {

		String lastOpenDir = Main.pref.get("geotiffovl.lastopendir", "");
		JFileChooser fc = new JFileChooser(lastOpenDir);

		FileFilterByExtension tif_filter = new FileFilterByExtension(
				new String[] { ".tif", ".tiff" });
		fc.addChoosableFileFilter(tif_filter);
		fc.setFileFilter(tif_filter);
		fc.setMultiSelectionEnabled(false);
		fc.setAcceptAllFileFilterUsed(true);
		if (fc.showOpenDialog(Main.parent) != JFileChooser.APPROVE_OPTION) {
			// dialog cancelled by user
			return;
		}

		File f = fc.getSelectedFile();

		// Remember directory
		Main.pref.put("geotiffovl.lastopendir", f.getParent());

		// Open GeoTiff file
		Dataset ds = gdal.Open(f.getAbsolutePath());

		// add new layer
		GDALRasterLayer layer = new GDALRasterLayer(f.getName(), ds, f);
		Main.main.addLayer(layer);
	}

}
