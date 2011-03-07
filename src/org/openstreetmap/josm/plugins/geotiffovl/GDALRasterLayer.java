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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;
import org.gdal.osr.SpatialReference;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.dialogs.LayerListPopup;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * A layer that displays an image correctly projected using its geographic
 * metadata
 */
public class GDALRasterLayer extends Layer {

	static {
		// Mark this file for translation
		marktr("");
	}

	private File m_sourceFile;
	/**
	 * Layer name, used for displaying in layer list
	 */
	private String m_name;
	/**
	 * GDAL dataset
	 */
	private Dataset m_srcDataset;
	/**
	 * Last used projection to display image
	 */
	private String m_lastProj;
	/**
	 * Projected image, used for displaying
	 */
	private BufferedImage m_image;
	/**
	 * Coordinate transformation of projected image
	 */
	private double[] m_geoTransform;

	/**
	 * Constructs a layer suitable to display a GDAL raster image with proper
	 * alignment
	 * 
	 * @param name
	 *            Name for the layer, e.g. the source file name
	 * @param dataset
	 *            The GDAL dataset
	 */
	public GDALRasterLayer(String name, Dataset dataset, File sourceFile) {
		super(tr("Image: {0}", name));

		m_sourceFile = sourceFile;
		m_name = name;
		m_srcDataset = dataset;
		m_lastProj = "";
		m_image = null;

		// Project image for the first time
		try {
			invalidate();
		} catch (Exception e) {
			System.err
					.println(tr("Error occurred while initializing GDALRasterLayer:"));
			System.err.println(e.getMessage());
			e.printStackTrace(System.err);
		}
	}

	@Override
	public Icon getIcon() {

		// Try to locate icon in resources
		java.net.URL icon_url = getClass()
				.getResource("/images/layer_icon.png");

		if (icon_url != null) {
			// Load our icon and return it
			return new ImageIcon(icon_url);
		} else {
			// Icon not available. Load a generic one
			return ImageProvider.get("", "cancel.png");
		}
	}

	@Override
	public Object getInfoComponent() {

		try {
			invalidate();
		} catch (Exception e) {
			StringBuilder buf = new StringBuilder();
			buf.append(tr("Unusable dataset:\n"));
			buf.append(e.toString());
			return buf.toString();
		}

		StringBuilder buf = new StringBuilder();

		buf.append(tr("Source image properties:\n"));
		buf.append(tr("Dimensions: {0}x{1}\n", m_srcDataset.getRasterXSize(),
				m_srcDataset.getRasterYSize()));
		buf.append(tr("Bands: {0}\n", m_srcDataset.GetRasterCount()));
		{
			double[] gt = m_srcDataset.GetGeoTransform();
			double origin_x = gt[0];
			double origin_y = gt[3];
			buf.append(tr("Origin: ({0} ; {1})\n", origin_x, origin_y));
		}
		buf.append(tr("Source projection:\n"));
		{
			SpatialReference sr = new SpatialReference(m_srcDataset
					.GetProjection());
			buf.append(sr.ExportToPrettyWkt(1));
			buf.append("\n\n");
		}

		buf.append(tr("Projected image properties:\n"));
		buf.append(tr("Dimensions: {0}x{1}\n", m_image.getWidth(), m_image
				.getHeight()));
		buf.append(tr("Origin: ({0} ; {1})\n", m_geoTransform[0],
				m_geoTransform[3]));
		buf.append(tr("Display projection:\n"));
		{
			String prj = Main.proj.toCode();
			try {
				String wkt = projCodeToSR(prj).ExportToPrettyWkt(1);
				buf.append(wkt);
				buf.append("\n\n");
			} catch (RuntimeException e) {
				buf.append(tr("NOT A VALID PROJECTION: {0}\n\n", prj));
			}
		}

		return buf.toString();
	}

	/**
	 * This gets called when the user right clicks on the layer in the layer
	 * list
	 */
	@Override
	public Action[] getMenuEntries() {
		LayerListDialog lld = LayerListDialog.getInstance();
		return new Action[] { lld.createShowHideLayerAction(),
				lld.createDeleteLayerAction(), SeparatorLayerAction.INSTANCE,
				new LayerListPopup.InfoAction(this) };
	}

	/**
	 * This gets called when hovering over the layer in the layer list
	 */
	@Override
	public String getToolTipText() {
		return m_sourceFile.getAbsolutePath();
	}

	@Override
	public boolean isMergable(Layer other) {
		// Never merge with any layer.
		return false;
	}

	@Override
	public void mergeFrom(Layer from) {
	}

	private SpatialReference projCodeToSR(String proj) {
		SpatialReference sr = new SpatialReference();

		if (proj.startsWith("EPSG:")) {
			String[] parts = proj.split(":");
			int epsg = new Integer(parts[1]);
			sr.ImportFromEPSG(epsg);
		} else {
			sr.SetWellKnownGeogCS(proj);
		}

		return sr;
	}

	private String projCodeToWkt(String proj) {
		return projCodeToSR(proj).ExportToPrettyWkt();
	}

	class ProjectionException extends RuntimeException {
		private static final long serialVersionUID = 3410650905450388838L;
		public String currentProj;

		public ProjectionException(String proj, String message) {
			super(message);
			currentProj = proj;
		}

	}

	/**
	 * Reprojects image and updates transformation matrices, if needed. Throws
	 * NullPointerException if dataset is null Throws ProjectionException if
	 * current projection is invalid
	 */
	private void invalidate() {
		// Guard against null-pointer
		if (m_srcDataset == null) {
			throw new NullPointerException("Dataset is null");
		}

		/*
		 * Course of action: Check if we need to reproject the image iff: a)
		 * Image just loaded, never displayed / projected b) User has changed
		 * the projection in the settings Then reproject image if needed
		 */

		// For now, they seem to use only EPSG codes.
		String currentProj = Main.proj.toCode();

		// Check if image is there or if the projection has changed
		if ((m_image == null) || (!m_lastProj.equals(currentProj))) {
			// Reproject dataset and create image

			// Create destination coordinate system from projection code
			String dstWkt = null;
			try {
				dstWkt = projCodeToWkt(currentProj);
			} catch (RuntimeException e) {
				throw new ProjectionException("'" + currentProj
						+ "' is not a valid projection.", currentProj);
			}

			// Reproject dataset
			int eResampleAlg = gdalconst.GRA_Cubic;
			double maxError = 0.2; // Maximum error in source image pixels
			Dataset projDataset = gdal.AutoCreateWarpedVRT(m_srcDataset, null,
					dstWkt, eResampleAlg, maxError);

			if (projDataset == null) {
				// Image has no transformation info and/or no GCPs
				// Rethrow exception for callers
				throw new NullPointerException(
						tr("Source image could not be reprojected. It is probably not properly georeferenced."));
			}

			// Current geo transform
			m_geoTransform = projDataset.GetGeoTransform();
			// Convert dataset to drawable image
			m_image = GDALTools.datasetToImage(projDataset, false);
			// Don't convert again next time
			m_lastProj = currentProj;
		}
	}

	@Override
	public void paint(Graphics2D g, MapView mv, Bounds box) {

		// Save old graphics transformation
		AffineTransform oldGraphicsTransform = g.getTransform();

		try {
			invalidate();
		} catch (NullPointerException e) {
			// Just display a big red error text
			g.setColor(Color.red);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
			g.drawString(tr(
					"Image layer {0}: IMAGE IS NOT PROPERLY GEOREFERENCED",
					m_name), 20, 100);
			return;
		} catch (ProjectionException e) {
			g.setColor(Color.red);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
			g.drawString(
					"Image layer '" + m_name
							+ "': CANNOT COMPREHEND PROJECTION '"
							+ e.currentProj + "'", 20, 100);
			return;
		}

		// The image transformation follows the formula:
		// sx * xi + tx = xs
		// sy * yi + ty = ys
		// where sx, sy are the scale values in x- and y-direction,
		// xi, yi are the pixel positions
		// tx, ty are the translation values
		// and xs, ys the resulting position on the screen

		// Translation is determined by setting (sx,sy) = (0,0) and (xs,ys) =
		// (projected screen coordinate for upper left image corner) and solving
		// for (tx,ty).

		// Scaling is determined by solving for (sx,sy) with xi=imwidth-1 and
		// xs=(projected screen coordinate for upper right image corner)
		// and the same in y-direction.

		AffineTransform dspTransform = new AffineTransform();

		// Image point (0,0), geographically projected
		Point2D geoOrigin = GDALTools.applyGeoTransform(0, 0, m_geoTransform);

		// Map origin to screen coordinates
		Point2D geoOriginScreenPos = mv.getPoint2D(new EastNorth(geoOrigin
				.getX(), geoOrigin.getY()));

		// Translate image so that image origin lies at the projected screen
		// coordinates
		double tx = geoOriginScreenPos.getX();
		double ty = geoOriginScreenPos.getY();
		dspTransform.translate(tx, ty);

		// Determine scaling

		// Upper right corner of image, geographically projected
		Point2D geoUpperRight = GDALTools.applyGeoTransform(
				m_image.getWidth() - 1, 0, m_geoTransform);
		Point2D geoUpperRightScreenPos = mv.getPoint2D(new EastNorth(
				geoUpperRight.getX(), geoUpperRight.getY()));

		// Bottom right corner of image, geographically projected
		Point2D geoBottomRight = GDALTools
				.applyGeoTransform(m_image.getWidth() - 1,
						m_image.getHeight() - 1, m_geoTransform);
		Point2D geoBottomRightScreenPos = mv.getPoint2D(new EastNorth(
				geoBottomRight.getX(), geoBottomRight.getY()));

		double sx = (geoUpperRightScreenPos.getX() - geoOriginScreenPos.getX())
				/ (m_image.getWidth() - 1);
		double sy = (geoBottomRightScreenPos.getY() - geoUpperRightScreenPos
				.getY())
				/ (m_image.getHeight() - 1);

		dspTransform.scale(sx, sy);
		g.setTransform(dspTransform);
		g.drawImage(m_image, 0, 0, null);

		// Restore painter transformation for other layers
		g.setTransform(oldGraphicsTransform);
	}

	/**
	 * Computes image bounding box
	 */
	@Override
	public void visitBoundingBox(BoundingXYVisitor v) {

		try {
			Point2D geoUpperLeft = GDALTools.applyGeoTransform(0, 0,
					m_geoTransform);
			Point2D geoBottomRight = GDALTools.applyGeoTransform(m_image
					.getWidth() - 1, m_image.getHeight() - 1, m_geoTransform);

			ProjectionBounds bounds = new ProjectionBounds(
					toEastNorth(geoUpperLeft));
			bounds.extend(toEastNorth(geoBottomRight));

			v.visit(bounds);
		} catch (Exception e) {
			System.err.println(tr("Error occurred in visitBoundingBox:"));
			e.printStackTrace(System.err);
		}
	}

	/**
	 * Convenience function to convert a Point2D to a EastNorth instance
	 * 
	 * @param p
	 *            geographically projected coordinates
	 */
	private EastNorth toEastNorth(Point2D p) {
		return new EastNorth(p.getX(), p.getY());
	}

}
