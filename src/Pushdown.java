import java.util.ArrayList;

import ij.*;
import ij.process.*;
import ij.gui.*;
/**
 * Push down the stack image by using a height map image like a 2D contour gauge.
 * @author kabe@cris.hokudai.ac.jp
 * @version 12-Nov-2010
 */
public class Pushdown {

	private static ImageStack stack;
	ImageProcessor mapIp;
	private int width, height, depth, baseHeight;
	private float ratio;
	
	/**
	 * @param stack
	 *   Source float stack.
	 * @param depthmap
	 *   Float image as depth map.
	 * @param ratio
	 *   trim pushing depth.
	 */
	public Pushdown( ImageStack imageStack, boolean autoMap ) {
		if( imageStack==null || imageStack.getSize()<1 ) {
			IJ.error( "No target stack.");
			return;
		}
		stack = imageStack;
		width = stack.getWidth();
		height = stack.getHeight();
		depth = stack.getSize();
		DepthMapIp fp = autoMap ? makeMap() : selectMap() ;
		if( fp==null || fp.ip==null )
			return;
		push( fp );
	}

	DepthMapIp makeMap() {
		GenericDialog gd = new GenericDialog( "Pushdown - Parameters" );
		gd.addNumericField( "Threshold ", 0.0, 6, 9, "" );
		gd.pack();
		gd.showDialog();
		if( gd.wasCanceled() )
			return null;
		float threshold = (float) gd.getNextNumber();
		float[] srcAry;
		IJ.log( "threshold:"+threshold );
		int z;
		DepthMapIp ip = new DepthMapIp( null );
		for( int i=0; i<width*height; i++ ){
			for( z=depth; z>0; z-- ) {
				srcAry = (float[]) stack.getPixels( z );
				if( srcAry[i]>threshold )
					break;
			}
			ip.ip.setf( i , z );
		}
		new ImagePlus( "Auto-Generated HightMap", ip.ip ).show();
		return ip;
	}
	DepthMapIp selectMap() {
		ImagePlus mapImp = null;
		ArrayList<ImagePlus> labels = ImsVIEW_.imgList.getSlicesImp();
		if( labels.size()<1 ){
			IJ.error("No height map." );
			return null;
		}
		String[] itemList = new String[labels.size()];
		for( int i=0; i<labels.size(); i++ )
			itemList[i] = labels.get(i).getTitle();
		String defaultItem = itemList[0];
		
		boolean valid=false;
		while( valid==false ) {
			GenericDialog gd = new GenericDialog( "PushDown - Parameters" );
			gd.addChoice( "Height map", itemList, defaultItem );
			gd.addNumericField( "Map depth ratio :", 1, 0, 6, ""  );
			gd.showDialog();
			if( gd.wasCanceled() )
				return null;
			ratio = (float) gd.getNextNumber();
			mapImp = WindowManager.getImage( itemList[gd.getNextChoiceIndex()] );
			if( width==mapImp.getWidth() || height==mapImp.getHeight() )
				valid = true;
			else
				IJ.error( "Stack size and Height map size is not match." );
			// loop while got valid parameter, or press cancel.
		}
		DepthMapIp ip = new DepthMapIp( mapImp.getProcessor() );
		ip.chopHeight();
		ip.multiply( ratio );
		return ip;
	}
	
		void addMargin( int num ) {
		// add margin to source stack
		String topTitle = stack.getSliceLabel( 1 );
		for( int i=0; i<num+1 ; i++ ) {
			FloatProcessor margin = new FloatProcessor( width, height );
			margin.setValue( 0.0f );
			margin.fill();
			stack.addSlice( topTitle+"-ext"+i, margin );
		}
		IJ.log( "Added "+baseHeight+" slices, total depth="+stack.getSize() );
		return;
	}
	
	void push( DepthMapIp map ) {
		float[] srcAry, dstIntAry, dstFracAry;
		float offset, offsetFrac, sVal, intVal, fracVal;
		int offsetInt;
		if( map==null )
			return;
		map.getMaxMin();
		if( map.min<0 ) {
			map.ip.add( map.min );
		}
		map.chopHeight();
		map.getMaxMin();
		addMargin( (int) map.max );
		IJ.showProgress( 0.0 );
		for( int i=0; i<width*height; i++ ){
			IJ.showProgress( i, width*height );
			offset = map.max - (float) map.ip.getf( i );
			offsetInt = (int) offset;
			offsetFrac = offset - offsetInt;
			for( int z=depth; z>0; z-- ) {
				srcAry = (float[]) stack.getPixels( z );
				sVal = srcAry[i];
				if( offsetInt>0 ) {
					intVal = sVal * ( 1 - offsetFrac );
					dstIntAry = (float[]) stack.getPixels( z + offsetInt );
					dstIntAry[i] += intVal;
					srcAry[i] -= intVal;
				}
				if( offsetFrac>0 ) {
					fracVal = sVal * offsetFrac;
					dstFracAry = (float[]) stack.getPixels( z + offsetInt + 1 );
					dstFracAry[i] += fracVal;
					srcAry[i] -= fracVal;
				}
			}
		}
	}

	class DepthMapIp {
		public FloatProcessor ip;
		public float max, min;
		public int maxIdx, minIdx;

		DepthMapIp( ImageProcessor ip ) {
			if( ip==null )
				this.ip= new FloatProcessor( width, height );
			else if( ip instanceof FloatProcessor )
				this.ip = (FloatProcessor) ip;
			else
				this.ip = (FloatProcessor) ip.convertToFloat();
			getMaxMin();
		}

		void getMaxMin() {
			max = Float.MIN_VALUE;
			min = Float.MAX_VALUE;
			maxIdx = 0;
			minIdx = 0;
			for( int i=0; i<ip.getPixelCount(); i++ ) {
				Float f = ip.getf( i );
				if( f<min ) {
					min = f;
					minIdx = i;
				}
				if( f>max ) {
					max = f;
					maxIdx = i;
				}
			}
			IJ.log( "getMaxMin: max="+max+"(@"+maxIdx+"), min="+min+"(@"+minIdx+")" );
		}
		void multiply( Float ratio ) {
			ip.multiply( ratio );
			IJ.log( "multiply depth map: max="+max+", min="+min );
		}
		void chopHeight() {
			ip.add( -min );
			getMaxMin();
			IJ.log( "chop depth map: max="+max+", min="+min );
		}
	}
	
}
