import ij.*;
import ij.process.*;
import ij.gui.*;
/**
 * (((Only for check, not used.)))
 * @author kabe@cris.hokudai.ac.jp
 * @version 09-Jan-2015
 */
public class MathInFloat {

	public ImagePlus srcImp;
	int width, height;
	public String srcTitle, dstTitle;
	
	/**
	 * @param imp
	 *   ImagePlus including source stack.
	 */
	public MathInFloat( ImagePlus imp ) {
		srcImp = imp;
		if( srcImp==null) {
			IJ.error( "MathInFloat: Please select a Image window.");
			return;
		}
		srcTitle = imp.getTitle();
		width = imp.getWidth();
		height = imp.getHeight();
		ImageStack srcStack = srcImp.getStack();
		if( srcStack!=null ) {
			ImageStack dstStack = new ImageStack( width, height );
			for( int z=1; z<=srcStack.getSize(); z++ ){
				ImageProcessor r = process( srcStack.getProcessor(z) );
				if( r!=null ) dstStack.addSlice( srcStack.getSliceLabel(z), r );
			}
			new ImagePlus( imp.getTitle()+"-log", dstStack ).show();
		} else {
			ImageProcessor r = process( srcImp.getProcessor() );
			new ImagePlus( imp.getTitle()+"-log", r ).show();
		}
		srcImp.unlock();
		return;
	}

	ImageProcessor process( ImageProcessor src ) {
		if( src==null ) return null;
		int pixels = width * height;
		float[] srcArray = (float[]) src.getPixels();
		ImageProcessor result = new FloatProcessor( width, height );
		float[] dstArray = (float[]) result.getPixels();
		for( int i = 0; i < pixels; i++ ) {
			dstArray[i] = (float) Math.log10( srcArray[i] );
		}
		return result;
	}

}
