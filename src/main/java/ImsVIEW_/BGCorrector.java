import ij.*;
import ij.process.*;
import ij.gui.*;
/**
 * Reduce background noise from 32bit-REAL Stack.
 * @author Koutaro Abe ( kabe@cris.hokudai.ac.jp )
 * @version 22-July-2011
 */
public class BGCorrector {

	public ImagePlus srcImp;
	public String srcTitle;
	private String dstTitle;
	private ImageStack srcStack, dstStack;
	private int size, width, height;
	private Roi roi;
	private static String prefkey="Delta.";
	private static String pref_dbg=prefkey+"debug";
	static boolean debugflag;
	
	/**
	 * @param imp
	 *   ImagePlus including source stack.
	 */
	public BGCorrector( ImagePlus imp ) {
		this.srcImp = imp;
		if( this.srcImp==null) {
			IJ.error( "BGCorrector: Please select a Stack Image window.");
			return;
		}
		roi = srcImp.getRoi();
		if( roi==null ) {
			IJ.error( "BGCorrector: Please set ROI.");
			return;
		}
		srcTitle = srcImp.getTitle();
		srcStack = srcImp.getStack();
		size = srcStack.getSize();
		if( srcStack==null || ( size < 2) ) {
			IJ.error( "BGCorrector: Selected Image is not a Stack.");
			return;
		}
		width = srcStack.getWidth();
		height = srcStack.getHeight();
		debugflag = Prefs.get( pref_dbg, false );
		
		dstStack = subtractStack( getAverage() );
		show( "BGC-"+srcTitle );
	}
	public float[] getAverage() {
		float[] avg = new float[size];
		ImageProcessor ip;
		try {
			srcImp.lock();
			float sum;
			int count;
			if( debugflag )
				IJ.log( "size="+size );
			for( int z = 1; z<=size; z++ ) {
				sum = 0;
				count = 0;
				ip = srcStack.getProcessor( z );
				for( int y = 0; y <= height; y++ )
					for( int x = 0; x <= width; x++ )
						if( roi.contains( x, y ) ) {
							sum += ip.getf( x, y );
							count++;
						}
				avg[z-1] = sum / count;
				if( debugflag )
					IJ.log( "z:"+z+" sum="+sum+", count="+count+", average="+avg[z-1] );
			}
		} catch( Exception e ) {
			IJ.error( "Error:"+e );
		} finally {
			srcImp.unlock();
		}
		return avg;
	}
	public ImageStack subtractStack( float[] avg ) {
		float[] srcArray, dstArray;
		ImageProcessor dstIp;
		int pixels = width * height;	
		ImageStack stack = new ImageStack( width, height );
		IJ.showStatus( "Delta process status" );
		IJ.showProgress( 0.0 );
		try {
			for( int z = 1; z <= size; z++ ) {
				IJ.showProgress(  1, size-z );
	 			srcArray = (float[]) srcStack.getPixels( z );
				dstIp = new FloatProcessor( width, height );
	 			dstArray = (float[]) dstIp.getPixels();
				for( int j = 0; j < pixels; j++ ) {
					dstArray[j] = srcArray[j] - avg[z-1];
					if( j==0 && debugflag )
						IJ.log( "z:"+z+" src="+srcArray[j]+", avg="+avg[z-1]+", result="+dstArray[j] );
				}
				stack.addSlice( srcStack.getSliceLabel( z ), dstIp );
			}
		} catch( Exception e ) {
			IJ.error( "Error:"+e );
		} finally {
			srcImp.unlock();
		}
		return stack;
	}
	
	public void show( String title ){
		dstTitle = title;
		if( dstStack==null ) return;
		ImagePlus window = new ImagePlus( title, dstStack );
		if ( window != null ) window.show();
	}

	public void show(){
		show( dstTitle );
	}
}
