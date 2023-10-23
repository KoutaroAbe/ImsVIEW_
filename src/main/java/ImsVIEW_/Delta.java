import ij.*;
import ij.process.*;
import ij.gui.*;
/**
 * Subtract 32bit-REAL Stack and create a new Stack or a new Image.
 * @author Koutaro Abe ( kabe@cris.hokudai.ac.jp )
 * @version 19-Nov-2012
 */
public class Delta {

	public ImagePlus srcImp;
	public String srcTitle, dstTitle;
	private ImageStack srcStack, dstStack;
	private int stackSize;
	int zDelta, zInterval;
	boolean fixFront;
	static int aDepth;
	static boolean debugflag;
	private static String prefkey="Delta.";
	private static String pref_nrl=prefkey+"noiseReductionLevel";
	private static String pref_dbg=prefkey+"debug";
	
	/**
	 * @param imp
	 *   ImagePlus including source stack.
	 */
	public Delta( ImagePlus imp ) {
		srcImp = imp;
		if( srcImp==null) {
			IJ.error( "Delta: Please select a Stack Image window.");
			return;
		}
		srcTitle = imp.getTitle();
		srcStack = this.srcImp.getStack();
		if( srcStack==null || ( (stackSize=srcStack.getSize()) < 2) ) {
			IJ.error( "Delta: Selected Image is not a Stack.");
			srcImp=null;
			return;
		}
		aDepth = (int) Prefs.get( pref_nrl, 1 );
		debugflag = Prefs.get( pref_dbg, false );
	}
	
	public ImageStack differential( ){
		if( srcImp==null ) return null;
		dstTitle = "Dd-"+srcTitle;
		dstStack = getStack( 1, 1, aDepth, false );
		return dstStack;
	}
	public ImageStack total( ){
		if( srcImp==null ) return null;
		dstTitle = "Dt-"+srcTitle;
		dstStack = getStack( stackSize-1, stackSize, aDepth, false );
		return dstStack;
	}			
	public ImageStack top( ){
		if( srcImp==null ) return null;
		dstTitle = "Dl-"+srcTitle;
		dstStack = getStack( 1, 1, aDepth, true );
		return dstStack;
	}			
	public ImageStack setParameters() {
		if( srcImp==null ) return null;
		dstTitle = "D-"+srcTitle;
		int zDlt=-1, zInt=1, nrDepth=aDepth;
		boolean fixFront=false;
		while( zDlt<1 || zInt<0 || nrDepth<1 ) {
			GenericDialog gd = new GenericDialog( "Delta - Parameters" );
			if( zDlt==-1 ) zDlt=1;
			gd.addStringField( "Image Name ", dstTitle, 24 );
			gd.addNumericField( "Subtract Slice( Z+", zDlt, 0, 3, ")  from  Slice( Z )." );
			gd.addNumericField( "Z interval: ", zInt, 0, 3, " slice(s)" );
			gd.addNumericField( "Noise Reduction level: ", nrDepth, 0, 3, " slice(s)" );
			gd.pack();
			gd.showDialog();
			if( gd.wasCanceled() )
				return null;
			dstTitle = gd.getNextString();
			zDlt = (int) gd.getNextNumber();
			zInt = (int) gd.getNextNumber();
			fixFront = false;
			nrDepth = (int) gd.getNextNumber();
		}
		dstStack = getStack( zDlt, zInt, nrDepth, fixFront );
		return dstStack;
	}
	public static void setNoiseReductionLevel() {
		int nr=-1;
		boolean log=false;
		while( nr<1 ) {
			GenericDialog gd = new GenericDialog( "Delta - Set Preference" );
			if( nr==-1 ) nr = (int) Prefs.get( pref_nrl, 1 );
			gd.addNumericField( "Noise Reduction level: ", nr, 0, 3, " slice(s)" );
			gd.addCheckbox( "Enable log: ", Prefs.get( pref_dbg, false ) );
			gd.pack();
			gd.showDialog();
			if( gd.wasCanceled() )
				return;
			nr = (int) gd.getNextNumber();
			log = gd.getNextBoolean();
		}
		aDepth = nr;
		debugflag = log;
		Prefs.set( pref_nrl, nr );
		Prefs.set( pref_dbg, debugflag );
		return;
	}

	/**
 *	@param zDelta
 *			差分を取る相手とのz軸方向の距離
 *	@param zInterval
 *			演算をするz軸の間隔
 *	@param nrDepth
 *			平均をとって平滑する場合、平均をとる対象（z軸の幅）
 *	@param fixFront 
 *			trueの場合、常にトップとの差分を取る
 *	@return
 *			ImageStack。各pixelの値は、z軸1単位あたりの値に換算される。
 */
	public ImageStack getStack( int zDelta, int zInterval, int nrDepth, boolean fixFront ) {
		int zDlt=zDelta, zInt=zInterval, nrDpt=nrDepth;
		if( srcStack==null || zDlt==0 || nrDpt==0 ) return null;
		if( zInt==0 ) zInt = stackSize;
		float[] dstArray; 
		float[][] fFront, fBack;
		float pFront, pBack;
		int zFront, zBack, zNum;
		String nameFront, nameBack;
		int width = srcStack.getWidth();
		int height = srcStack.getHeight();
		int pixels = width * height;	
		ImageStack resultStack = new ImageStack( width, height );
		fFront = new float[nrDpt][];
		fBack = new float[nrDpt][];

		try {
			srcImp.lock();
			IJ.showStatus( "Delta process status" );
			IJ.showProgress( 0.0 );
			if( debugflag )
				IJ.log( "size="+stackSize+", zDelta="+zDlt+", zInterval="+zInt+", nrDepth="+nrDpt );
			for( int i = 1; i <= stackSize - zDlt; i = i + zInt  ) {
				IJ.showProgress(  i, stackSize-zDlt );
				zFront = fixFront ? 1 : i;
				zBack = i + zDlt;
				zNum = 0;
				for( int k = 0; k<nrDpt; k++ ){
					if( zBack + k <= stackSize && zFront + k <= stackSize ) {
						fBack[zNum] = (float[]) srcStack.getPixels( zBack + k );
						fFront[zNum++] = (float[]) srcStack.getPixels( zFront + k );
					}
				}
				nameBack = srcStack.getSliceLabel( zBack );
				nameFront = srcStack.getSliceLabel( zFront );
				ImageProcessor dstIp = new FloatProcessor( width, height );
	 			dstArray = (float[]) dstIp.getPixels();
				for( int j = 0; j < pixels; j++ ) {
					pBack = 0;
					pFront = 0;
					for( int k = 0; k<zNum; k++ ) {
						pBack += fBack[k][j];
						pFront += fFront[k][j];
						if( j==0 && debugflag )
							IJ.log( k+": back["+(zBack+k)+"]="+fBack[k][j]+", front["+(zFront+k)+"]="+fFront[k][j] );
					}
					if( j==0 && debugflag )
						IJ.log( "sum: back="+pBack+", front="+pFront  );
					dstArray[j] = ( pBack - pFront )/zNum / zDlt;
					if( j==0 && debugflag )
						IJ.log( "( back.avg - front.avg ) / z.delta = ( "+pBack/zNum+" - "+pFront/zNum+" ) / "+zDlt+" = "+dstArray[j] );
				}
				resultStack.addSlice( nameFront + " - " + nameBack, dstIp );
			}
		} catch( Exception e ) {
			IJ.error( "Error:"+e );
		} finally {
			srcImp.unlock();
		}
		return resultStack;
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
