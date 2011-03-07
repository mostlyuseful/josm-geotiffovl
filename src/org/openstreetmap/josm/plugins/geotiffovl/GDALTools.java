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


Additionally:
The datasetToImage function is adapted from the GDALtest class under gdal/apps
from the java bindings package of gdal (http://www.gdal.org/).
The function printLastError is copied verbatim.
That code was licensed under an MIT/X style license with the following terms:

	Permission is hereby granted, free of charge, to any person obtaining a
	copy of this software and associated documentation files (the "Software"),
	to deal in the Software without restriction, including without limitation
	the rights to use, copy, modify, merge, publish, distribute, sublicense,
	and/or sell copies of the Software, and to permit persons to whom the
	Software is furnished to do so, subject to the following conditions:

	The above copyright notice and this permission notice shall be included
	in all copies or substantial portions of the Software.

	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
	OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
	FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
	THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
	LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
	FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
	DEALINGS IN THE SOFTWARE.

 */

package org.openstreetmap.josm.plugins.geotiffovl;

import java.awt.color.ColorSpace;
import java.awt.geom.Point2D;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.GCP;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;

public class GDALTools {

	/**
	 * Converts screen position (Xpixel,Yline) to projected coordinates
	 * 
	 * Usage example: Point2D pgeo = GDALTools.applyGeoTransform( x, y,
	 * ds.GetGeoTransform() );
	 * 
	 * @param Xpixel
	 *            X position on screen
	 * @param Yline
	 *            Y position on screen
	 * @param gt
	 *            GeoTransform coefficients of length 6
	 * @return A projected coordinate pair with double precision
	 */
	public static Point2D applyGeoTransform(double Xpixel, double Yline,
			double[] gt) {
		double Xgeo = gt[0] + Xpixel * gt[1] + Yline * gt[2];
		double Ygeo = gt[3] + Xpixel * gt[4] + Yline * gt[5];
		return new Point2D.Double(Xgeo, Ygeo);
	}

	public static Point2D applyGeoTransform(Point2D Pscreen, double[] gt) {
		return applyGeoTransform(Pscreen.getX(), Pscreen.getY(), gt);
	}

	/**
	 * Converts projected coordinates to screen position
	 * 
	 * @param Xgeo
	 *            X component of projected coordinate (e.g. longitude)
	 * @param Ygeo
	 *            Y component of projected coordinate (e.g. latitude)
	 * @param gt
	 *            GeoTransform coefficients
	 * @return A screen position with double precision
	 */
	public static Point2D applyInvGeoTransform(double Xgeo, double Ygeo,
			double[] gt) {
		double denom = (gt[1] * gt[5] - gt[2] * gt[4]);
		double Xpixel = -(gt[0] * gt[5] - gt[2] * (gt[3] - Ygeo) - gt[5] * Xgeo)
				/ denom;
		double Yline = (gt[0] * gt[4] - gt[1] * (gt[3] - Ygeo) - gt[4] * Xgeo)
				/ denom;
		return new Point2D.Double(Xpixel, Yline);
	}

	public static Point2D applyInvGeoTransform(Point2D Pgeo, double[] gt) {
		return applyInvGeoTransform(Pgeo.getX(), Pgeo.getY(), gt);
	}

	public static void main(final String[] args) {
		System.out.println("Hello!");

		gdal.AllRegister();
		Dataset ds = gdal
				.Open("/home/moe/dev/osm/openpapermap/TileCache/geo.tif");

		double[] gt = ds.GetGeoTransform();

		Point2D origin = applyGeoTransform(0, 0, gt);
		System.out.println("Origin: " + origin.toString());

		Point2D inv = applyInvGeoTransform(origin, gt);
		System.out.println("Inverted: " + inv.toString());
	}

	public static BufferedImage datasetToImage(Dataset poDataset,
			boolean printDebug) {

		double[] adfGeoTransform = new double[6];

		if (printDebug) {
			System.out.println("Driver: "
					+ poDataset.GetDriver().GetDescription());

			System.out.println("Size is: " + poDataset.getRasterXSize() + "x"
					+ poDataset.getRasterYSize() + "  bands:"
					+ poDataset.getRasterCount());

			if (poDataset.GetProjectionRef() != null)
				System.out.println("Projection is `"
						+ poDataset.GetProjectionRef() + "'");
		}

		Hashtable dict = poDataset.GetMetadata_Dict("");
		Enumeration keys = dict.keys();
		if (printDebug) {
			System.out.println(dict.size()
					+ " items of metadata found (via Hashtable dict):");
			while (keys.hasMoreElements()) {
				String key = (String) keys.nextElement();
				System.out.println(" :" + key + ":==:" + dict.get(key) + ":");
			}
		}

		Vector list = poDataset.GetMetadata_List("");
		Enumeration enumerate = list.elements();
		if (printDebug) {
			System.out.println(list.size()
					+ " items of metadata found (via Vector list):");
			while (enumerate.hasMoreElements()) {
				String s = (String) enumerate.nextElement();
				System.out.println(" " + s);
			}
		}

		Vector GCPs = new Vector();
		poDataset.GetGCPs(GCPs);
		if (printDebug) {
			System.out.println("Got " + GCPs.size() + " GCPs");
			Enumeration e = GCPs.elements();
			while (e.hasMoreElements()) {
				GCP gcp = (GCP) e.nextElement();
				System.out.println(" x:" + gcp.getGCPX() + " y:"
						+ gcp.getGCPY() + " z:" + gcp.getGCPZ() + " pixel:"
						+ gcp.getGCPPixel() + " line:" + gcp.getGCPLine()
						+ " line:" + gcp.getInfo());
			}
		}

		poDataset.GetGeoTransform(adfGeoTransform);

		if (printDebug) {
			System.out.println("Origin = (" + adfGeoTransform[0] + ", "
					+ adfGeoTransform[3] + ")");

			System.out.println("Pixel Size = (" + adfGeoTransform[1] + ", "
					+ adfGeoTransform[5] + ")");
		}

		Band poBand = null;
		// double[] adfMinMax = new double[2];
		Double[] max = new Double[1];
		Double[] min = new Double[1];

		int bandCount = poDataset.getRasterCount();
		ByteBuffer[] bands = new ByteBuffer[bandCount];
		int[] banks = new int[bandCount];
		int[] offsets = new int[bandCount];

		int xsize = poDataset.getRasterXSize();
		int ysize = poDataset.getRasterYSize();
		int pixels = xsize * ysize;
		int buf_type = 0, buf_size = 0;

		for (int band = 0; band < bandCount; band++) {
			/* Bands are not 0-base indexed, so we must add 1 */
			poBand = poDataset.GetRasterBand(band + 1);

			buf_type = poBand.getDataType();
			buf_size = pixels * gdal.GetDataTypeSize(buf_type) / 8;

			if (printDebug) {
				System.out.println(" Data Type = "
						+ gdal.GetDataTypeName(poBand.getDataType()));
				System.out.println(" ColorInterp = "
						+ gdal.GetColorInterpretationName(poBand
								.GetRasterColorInterpretation()));
				System.out.println("Band size is: " + poBand.getXSize() + "x"
						+ poBand.getYSize());
			}

			poBand.GetMinimum(min);
			poBand.GetMaximum(max);
			if (printDebug) {
				if (min[0] != null || max[0] != null) {
					System.out.println("  Min=" + min[0] + " Max=" + max[0]);
				} else {
					System.out.println("  No Min/Max values stored in raster.");
				}
			}

			if (printDebug) {
				if (poBand.GetOverviewCount() > 0) {
					System.out.println("Band has " + poBand.GetOverviewCount()
							+ " overviews.");
				}
			}

			if (printDebug) {
				if (poBand.GetRasterColorTable() != null) {
					System.out.println("Band has a color table with "
							+ poBand.GetRasterColorTable().GetCount()
							+ " entries.");
					for (int i = 0; i < poBand.GetRasterColorTable().GetCount(); i++) {
						System.out
								.println(" "
										+ i
										+ ": "
										+ poBand.GetRasterColorTable()
												.GetColorEntry(i));
					}
				}
			}

			if (printDebug) {
				System.out
						.println("Allocating ByteBuffer of size: " + buf_size);
			}

			ByteBuffer data = ByteBuffer.allocateDirect(buf_size);
			data.order(ByteOrder.nativeOrder());

			int returnVal = 0;
			try {
				returnVal = poBand.ReadRaster_Direct(0, 0, poBand.getXSize(),
						poBand.getYSize(), xsize, ysize, buf_type, data);
			} catch (Exception ex) {
				System.err.println("Could not read raster data.");
				System.err.println(ex.getMessage());
				ex.printStackTrace();
				return null;
			}
			if (returnVal == gdalconstConstants.CE_None) {
				bands[band] = data;
			} else {
				printLastError();
			}
			banks[band] = band;
			offsets[band] = 0;
		}

		DataBuffer imgBuffer = null;
		SampleModel sampleModel = null;
		int data_type = 0, buffer_type = 0;

		if (buf_type == gdalconstConstants.GDT_Byte) {
			byte[][] bytes = new byte[bandCount][];
			for (int i = 0; i < bandCount; i++) {
				bytes[i] = new byte[pixels];
				bands[i].get(bytes[i]);
			}
			imgBuffer = new DataBufferByte(bytes, pixels);
			buffer_type = DataBuffer.TYPE_BYTE;
			sampleModel = new BandedSampleModel(buffer_type, xsize, ysize,
					xsize, banks, offsets);
			data_type = (poBand.GetRasterColorInterpretation() == gdalconstConstants.GCI_PaletteIndex) ? BufferedImage.TYPE_BYTE_INDEXED
					: BufferedImage.TYPE_BYTE_GRAY;
		} else if (buf_type == gdalconstConstants.GDT_Int16) {
			short[][] shorts = new short[bandCount][];
			for (int i = 0; i < bandCount; i++) {
				shorts[i] = new short[pixels];
				bands[i].asShortBuffer().get(shorts[i]);
			}
			imgBuffer = new DataBufferShort(shorts, pixels);
			buffer_type = DataBuffer.TYPE_USHORT;
			sampleModel = new BandedSampleModel(buffer_type, xsize, ysize,
					xsize, banks, offsets);
			data_type = BufferedImage.TYPE_USHORT_GRAY;
		} else if (buf_type == gdalconstConstants.GDT_Int32) {
			int[][] ints = new int[bandCount][];
			for (int i = 0; i < bandCount; i++) {
				ints[i] = new int[pixels];
				bands[i].asIntBuffer().get(ints[i]);
			}
			imgBuffer = new DataBufferInt(ints, pixels);
			buffer_type = DataBuffer.TYPE_INT;
			sampleModel = new BandedSampleModel(buffer_type, xsize, ysize,
					xsize, banks, offsets);
			data_type = BufferedImage.TYPE_CUSTOM;
		}

		WritableRaster raster = Raster.createWritableRaster(sampleModel,
				imgBuffer, null);
		BufferedImage img = null;
		ColorModel cm = null;

		if (poBand.GetRasterColorInterpretation() == gdalconstConstants.GCI_PaletteIndex) {
			data_type = BufferedImage.TYPE_BYTE_INDEXED;
			cm = poBand.GetRasterColorTable().getIndexColorModel(
					gdal.GetDataTypeSize(buf_type));
			img = new BufferedImage(cm, raster, false, null);
		} else {
			ColorSpace cs = null;
			if (bandCount > 2) {
				cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
				cm = new ComponentColorModel(cs, false, false,
						ColorModel.OPAQUE, buffer_type);
				img = new BufferedImage(cm, raster, true, null);
			} else {
				img = new BufferedImage(xsize, ysize, data_type);
				img.setData(raster);
			}
		}
		return img;
	}

	public static void printLastError() {
		System.out.println("Last error: " + gdal.GetLastErrorMsg());
		System.out.println("Last error no: " + gdal.GetLastErrorNo());
		System.out.println("Last error type: " + gdal.GetLastErrorType());
	}

}