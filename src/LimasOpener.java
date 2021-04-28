import ij.IJ;
import ij.ImagePlus;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
import ij.process.ImageProcessor;
import java.io.*;
/**
 * Image file opener for LIMAS.
 * @author kabe@cris.hokudai.ac.jp
 * @version 22-Jan-2015
 */
class LimasOpener {

	String filepath;
	FileInputStream f;
	boolean validFile;
	int pixLength=4;
	int row, col;
	byte[] array;
	long count;
	ImagePlus image;
	boolean DEBUG=false;

	public LimasOpener( String file ) {
		filepath = file;
		try {
			validFile = checkHeader();
			if( validFile )
				parse();
			fileClose();
		} catch (IOException e) {
			IJ.log( ""+e );
		}
	}
	public LimasOpener( File file ) {
		this( file.getPath() );
	}
	public boolean isValid() {
		return validFile;
	}
	public ImagePlus open( boolean showImage ) {
		if( image!=null && showImage )
			image.show();
		return image;
	}
	// Check magic number in file head.
	boolean checkHeader() throws IOException {
		validFile = false;
    	fileSet(0);
    	row = toInteger( fileRead(4) );
    	col = toInteger( fileRead(4) );
		int l = fileRemains();
		if( l>1 && l==row*col*4 ){
			pixLength = 4;
			array = fileRead( row*col*pixLength );
			validFile = true;
	    	IJ.log( "LimasOpener.32bit/pix("+row+","+col+")="+l+"byte.");
		} else if ( l>1 && l==row*col*8 ){
			pixLength = 8;
			array = fileRead( row*col*pixLength );
			validFile = true;    			
	    	IJ.log( "LimasOpener.64bit/pix("+row+","+col+")="+l+"byte.");
		}
		return validFile;
    }

	void parse() { 
		ImageProcessor ip;
		int pointer = 0;
		switch( pixLength ){
		case 4:
			ip = new ShortProcessor( col, row );
			for( int y=0; y<row; y++ )
				for( int x=0; x<col; x++ ) {
					ip.setf( x, y, toInteger( partOf( array, pointer, pixLength ) ) );
					pointer += pixLength;
				}
			break;
		case 8:	// NOT WORK CORRECTLY
			ip = new FloatProcessor( col, row );
			IJ.log( "This file may be 'Double:64bit-float'.\nIt's not supported at this time." );
			for( int y=0; y<row; y++ )
				for( int x=0; x<col; x++ ) {
					long longbit = toLong( partOf( array, pointer, pixLength ) );
					if( x==8 && y==7 )
//						IJ.log( ""+longbit+"->"+Double.longBitsToDouble( longbit )+"->"+(float)Double.longBitsToDouble( longbit ) );
					ip.setf( x, y, (float)Double.longBitsToDouble( longbit ) );
					pointer += pixLength;
				}
			break;
		default:
			return;
		}
		image = new ImagePlus( "LIMAS-"+pixLength, ip );
	}

	/* file read */
	void fileSet( long offset ) throws IOException {
		if( f!=null )
			f.close();
    	f = new FileInputStream( filepath );
    	count = 0;
		fileSkip( offset );
	}
	void fileSkip( long length ) throws IOException {
		count += f.skip( length );
	}
	byte[] fileRead( int length ) throws IOException {
		byte[] buf = new byte[length];
		int bytes = f.read(buf);
		if( bytes>0 ) count += bytes;
        return buf;
	}
	int fileRemains() throws IOException {
		return f.available();
	}
	void fileClose() throws IOException {
		if( f!=null )
			f.close();
		f = null;
		count = 0;
	}

	/* return part of array */
	static byte[] partOf( byte[] array, int offset, int length ) {
		byte[] r = new byte[length] ;
		for( int i=0; i<length && i+offset<array.length; i++ ) {
			r[i] = array[i+offset];
		}
		return r ;
	}
	/* return byte array as string */
	static String toString( byte[] byteArray ) {
       String rstr = new String() ;
       for( int i=0; i<byteArray.length; i++ ) {
//		   if( buffer[i+index]==0 ) break;
    	   rstr += (char) byteArray[i];
       }
       return rstr ;
	}
	/* byte array as integer */
	static int toInteger( byte[] byteArray ) {
		int r=0;
		for( int i=0; i<byteArray.length; i++ ) {
			r = r << 8;
			r += (int) byteArray[i] & 0xff;
		}
		return r;
	}
	/* byte array as long */
	static long toLong( byte[] byteArray ) {
		int r=0;
		for( int i=0; i<byteArray.length; i++ ) {
			r = r << 8;
			r += (int) byteArray[i] & 0xff;
		}
		return r;
	}

}


