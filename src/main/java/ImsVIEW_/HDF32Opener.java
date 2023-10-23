import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.image.IndexColorModel;
import java.io.*;
import java.util.ArrayList;  
import java.util.HashMap;
/**
 * Image file opener for HDF Version 3.2.
 * @author kabe@mail.cris.hokudai.ac.jp
 * @version 05-Aug-2010
 * CAUTION: supporting only DFTAG_SDG and DFTAG_NDG, DFTAG_SDD, DFTAG_SDL.
 */
class HDF32Opener {

	static final int HDF_MAGIC_NUMBER=0x0e031301;
	static final int BIN=0, STR=1, INT=2, FLP=3;
	String filepath;
	boolean validFile;
	static FileInputStream f;
	static long count;
	static DataDescriptorList ddlist;
	ImagePlus image;
	static boolean DEBUG=false;

	public HDF32Opener( File file ) {
		filepath = file.getPath();
		try {
			validFile = checkHeader();
			parse();
		} catch (IOException e) {
			IJ.log( ""+e );
		}
	}
	public HDF32Opener( String file ) {
		filepath = file;
		try {
			validFile = checkHeader();
			parse();
		} catch (IOException e) {
			IJ.log( ""+e );
		}
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
		validFile = toInteger( fileRead(4) )==HDF_MAGIC_NUMBER;
		fileClose();
		return validFile;
    }

	// parse Data Descriptor block
	void parse() throws IOException {
		if( validFile==false )
			return;
		ddlist = new DataDescriptorList();
		int ddNums, ddNext;
    	fileSet(4);
		// Store all tag into DData{ tag[], reference number[], FilePointer[] }
		do {
			ddNums = toInteger( fileRead(2) );	// number of DD in this block.
			ddNext = toInteger( fileRead(4) );	// top of next block.
			for( int i=0; i<ddNums; i++ ) {
				int tag = toInteger( fileRead(2) );
				int ref = toInteger( fileRead(2) );
				long offset = toInteger( fileRead(4) );
				int length = toInteger( fileRead(4) );
				ddlist.add( tag, ref, new Location( offset, length, BIN ) );
			}
			if( ddNext>0 ) {
				f.skip( ddNext-count );
//		            IJ.log( "To next block, skip "+ (ddNext-count) + "bytes" );
			}
		} while( ddNext>0 );

        int sdg = ddlist.getIndexByTag( 700 );
        int ndg = ddlist.getIndexByTag( 720 );
        if( sdg>=0 ) {
//	        	IJ.log( "DFTAG_SDG found: index"+sdg+"." );
        	image = parseSDG( ddlist.getLocation( sdg ) );
        } else if( ndg>=0 ) {
//	        	IJ.log( "DFTAG_NDG found: index"+ndg+"." );
        	image = parseSDG( ddlist.getLocation( ndg ) );
        } else {
        	if( DEBUG ) {
        		IJ.log( "??? DFTAG_SDG(NDG) not found in this file. Dump all tags." );
		        for( int i=0; i<ddlist.size(); i++ )
		            IJ.log( ddlist.toStr(i)+ddlist.getLocation(i).debug() );
        	}
        }
        fileClose();
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

	/* Array of 'Data Descriptor' block */
	class DataDescriptorList {
		
		ArrayList <Integer> tagAry, refAry;	// Tag, Reference Number
		ArrayList <Location> locAry;		// Data location in file includes offset and length
		
		DataDescriptorList() {
			tagAry = new ArrayList<Integer>();
			refAry = new ArrayList<Integer>();
			locAry = new ArrayList<Location>();
		}
		void add( int tag, int ref, Location loc ) {
			tagAry.add( tag );
			refAry.add( ref );
			locAry.add( loc );
		}
		int size() { return tagAry.size(); }
		int tag( int index ) { return tagAry.get(index); }
		int ref( int index ) { return refAry.get(index); }
		Location getLocation( int index ) {
//			IJ.log( "DData.getFs("+index+"/"+t.size()+")" );
			if( index<0 || locAry.size()<index )
				return null;
			return locAry.get( index );
		}
		Location getLocationByTag( int tag ){
			int index = getIndexByTag( tag );
			if( index<0 || locAry.size()<index )
				return null;
			return getLocation( index );
		}
		Location getLocationByTagAndRef( int tag, int ref ){
			int index = getIndex( tag, ref );
			if( index<0 || locAry.size()<index )
				return null;
			return getLocation( index );
		}
		int getIndex( int tag, int ref ) {
			for( int i=0; i<this.tagAry.size(); i++ )
				if( tag==tagAry.get(i) && ref==refAry.get(i) )
					return i;
			return -1;
		}
		int getIndexByTag( int tag ) {
			for( int i=0; i<tagAry.size(); i++ )
				if( tag==tagAry.get(i) )
					return i;
			return -1;
		}
		String toStr( int index ) throws IOException {
			Location p = locAry.get(index);
			return "["+tagAry.get(index)+","+refAry.get(index)+"]"+p.debug();
		}
		void parseByIndex( HashMap<String,Location> hash , int index ) throws IOException {
			if( getLocation( index )==null ) return;
			parse( hash, tag(index), ref(index) );
		}
	}
	
	/* Points location in file, with data type(BIN, STR, INT, FLP) */
	class Location {
		public long ofs;			// offset
		public int len, typ, current;	// length, type, current position
		Location( long offset, int length, int type ) {
			this.ofs = offset;
			this.len = length;
			this.typ = type;
			current = 0;
		}
		// return part of location as new instance
		Location pop( int type, int length ) {
			if( len<current+length )
				length = len - current;
			Location result = new Location( ofs+current, length, type );
			current += length;
			return result;
		}
		Location pop( int type ) {
			Location result = new Location( ofs+current, len-current, typ );
			current = len;
			return result;
		}
		// read actual file
		byte[] get() throws IOException {
			fileSet( ofs );
			return fileRead( len );
		}
		Object value() throws IOException {
			switch( this.typ ) {
			case 0: // binary as bytes array
				return get();
			case 1: // string
				return HDF32Opener.toString( get() );
			case 2: // integer
				return HDF32Opener.toInteger( get() );
			case 3: // float
				return Float.intBitsToFloat( HDF32Opener.toInteger( get() ) );
			}
			return null;
		}
		String strValue() throws IOException {
			return HDF32Opener.toString( this.get() );			
		}
		int intValue() throws IOException {
			return HDF32Opener.toInteger( this.get() );
		}
		Float flpValue() throws IOException {
			return Float.intBitsToFloat( HDF32Opener.toInteger( this.get() ) );
		}
		String debug() {
			return "ofs="+ofs+", len="+len+"byte, type="+typ;
		}
	}

	/* Push TAG_NAME and Location into HashMap */
	void parse( HashMap<String,Location> hash , int tag, int ref ) throws IOException {
		Location fs = ddlist.getLocationByTagAndRef( tag, ref );
		String p="";
		// String p = prefix.length()==0 ? "" : prefix+":";
		switch( tag ){
		case 30: // Tag Version
			hash.put( p+"DFTAG_VERSION.major", fs.pop(INT,4) );
			hash.put( p+"DFTAG_VERSION.minor", fs.pop(INT,4) );
			hash.put( p+"DFTAG_VERSION.release", fs.pop(INT,4) );
			hash.put( p+"DFTAG_VERSION.string", fs.pop(STR) );
			break;
		case 100:
			hash.put( p+"DFTAG_FID", fs.pop(STR) );
			break;
		case 101: // File Descriptor
			hash.put( p+"DFTAG_FD", fs.pop(STR) );
			break;
		case 102: // Tag Identifier
			hash.put( p+"DFTAG_TID", fs.pop(STR) );
			break;
		case 103: // Tag Descriptor
			hash.put( p+"DFTAG_TD", fs.pop(STR) );
			break;
		case 104: // Data Identifier Label
			hash.put( p+"DFTAG_IDS", fs.pop(STR) );
			break;
		case 105: // Data Identifier Annotation
			hash.put( p+"DFTAG_DIA", fs.pop(STR) );
			break;
		case 106: // Number Type
			hash.put( p+"DFTAG_NT.version", fs.pop(INT,1) );
			hash.put( p+"DFTAG_NT.type", fs.pop(INT,1) );
			hash.put( p+"DFTAG_NT.width", fs.pop(INT,1) );
			hash.put( p+"DFTAG_NT.class", fs.pop(INT,1) );
   			break;
		case 200: // Image Dimension-8
			hash.put( p+"DFTAG_ID8.x_dim", fs.pop(INT,2) );
			hash.put( p+"DFTAG_ID8.y_dim", fs.pop(INT,2) );
   			break;
		case 201: // Image Palette-8
			hash.put( p+"DFTAG_IP8", fs.pop(BIN) );
   			break;
		case 202: // Raster Image-8
			hash.put( p+"DFTAG_RI8", fs.pop(BIN) );
   			break;
		case 203: // Compressed Image-8
			hash.put( p+"DFTAG_CI8", fs.pop(BIN) );
   			break;
		case 204: // IMCOMP Image-8
			hash.put( p+"DFTAG_II8", fs.pop(BIN) );
   			break;
		case 300: // Image Dimension
			hash.put( p+"DFTAG_ID", fs.pop(BIN) );
   			break;
		case 301: // Lookup Table
			hash.put( p+"DFTAG_LUT", fs.pop(BIN) );
   			break;
   		case 302: // Raster Image
			hash.put( p+"DFTAG_RI", fs.pop(BIN) );
   			break;
   		case 303: // Compressed Image
			hash.put( p+"DFTAG_CI", fs.pop(BIN) );
   			break;
   		case 306: // Raster Image Group
			hash.put( p+"DFTAG_RIG", fs.pop(BIN) );
   			break;
		case 307: // LUT Dimension
			hash.put( p+"DFTAG_LD", fs.pop(BIN) );
   			break;
		case 308: // Matte Dimension
			hash.put( p+"DFTAG_MD", fs.pop(BIN) );
   			break;
		case 309: // Matte Data
			hash.put( p+"DFTAG_MA", fs.pop(BIN) );
   			break;
		case 310: // Color Correction
			hash.put( p+"DFTAG_CCN", fs.pop(BIN) );
   			break;
		case 311: // Color Format
			hash.put( p+"DFTAG_CFM", fs.pop(BIN) );
   			break;
		case 312: // Aspect Ratio
			hash.put( p+"DFTAG_AR", fs.pop(FLP,4) );
   			break;
		case 500: // XY Position
			hash.put( p+"DFTAG_XYP.x", fs.pop(INT,4) );
			hash.put( p+"DFTAG_XYP.y", fs.pop(INT,4) );
   			break;
		case 700: // Scientific Data Group
			hash.put( p+"DFTAG_SDG", fs.pop(BIN) );
   			break;
		case 701: // SDD Dimension Record
			hash.put( p+"DFTAG_SDD", fs.pop(BIN) );
   			break;
		case 702: // Scientific Data
			hash.put( p+"DFTAG_SD", fs.pop(BIN) );
			break;
		case 703: // SCales
			hash.put( p+"DFTAG_SDS", fs.pop(FLP,4) );
   			break;
		case 704: // Labels
			hash.put( p+"DFTAG_SDL", fs.pop(STR) );
   			break;
		case 705: // Units
			hash.put( p+"DFTAG_SDU", fs.pop(STR) );
   			break;
		case 706: // Formats
			hash.put( p+"DFTAG_SDF", fs.pop(STR) );
   			break;
		case 707: // Maximum/minimum
			hash.put( p+"DFTAG_SDM.max", fs.pop(FLP,4) );
			hash.put( p+"DFTAG_SDM.min", fs.pop(FLP,4) );
   			break;
		case 708: // Coordinate system
			hash.put( p+"DFTAG_SDC", fs.pop(STR) );
   			break;
		case 709: // Transpose
			hash.put( p+"DFTAG_SDT", fs.pop(STR) );
   			break;
		case 710: // SDS Link
			hash.put( p+"DFTAG_SDLNK", fs.pop(BIN) );
   			break;
		case 720: // Numeric Data Group
			hash.put( p+"DFTAG_NDG", fs.pop(BIN) );
   			break;
		default: // Others
			hash.put( p+"UNKNOWN", fs.pop(BIN) );
		}
	}

	/* Parse DFTAG_SDG or DFTAG_NDG and create image */
	ImagePlus parseSDG( Location fs ) throws IOException {
		try {
			int[] otherTags = { 707, 301 }; // DFTAG_SDM, DFTAG_LUT
			if( fs==null || fs.typ!=BIN )
				throw new HdfException( "Data not found for DFTAG_SDG or DFTAG_NDG." );
			HashMap<String,Location> sdgDataList = new HashMap<String,Location>();
			byte[] b = fs.get();
			for( int i=0; i*4<b.length; i+=4 )
				parse( sdgDataList, 
						HDF32Opener.toInteger( partOf( b, i  , 2) ),
						HDF32Opener.toInteger( partOf( b, i+2, 2) ) );
			for( int i=0; i<otherTags.length ; i++ )
				ddlist.parseByIndex( sdgDataList, ddlist.getIndexByTag( otherTags[i] ) );
			/* dump all list for DFTAG_SDG
			Iterator<String> it = sdg.keySet().iterator();
			IJ.log( "parseSDG get "+sdg.size()+" references."  );
	        while ( it.hasNext() ) {
	            Object o = it.next();
	            IJ.log( o + " = " + sdg.get(o).debug() );
	        }	*/
			/* required tag for DFTAG_SDG */
			if( !sdgDataList.containsKey( "DFTAG_SD") )
				throw new HdfException( "DFTAG_SD not found." );
			DFTAG_SDD sdd = new DFTAG_SDD( sdgDataList.get( "DFTAG_SDD" ) );
			if( sdd.rank()<1 )
				throw new HdfException( "DFTAG_SDD has not enough dimension("+sdd.rank()+")." );
			DFTAG_SDL sdl = new DFTAG_SDL( sdgDataList.get( "DFTAG_SDL" ) );
			if( sdl.rank()<2 )
				throw new HdfException( "DFTAG_SDL has not enough dimension("+sdl.rank()+")." );
			float min=0, max=0;
			if( sdgDataList.containsKey( "DFTAG_SDM") ) {
				max = Float.intBitsToFloat( sdgDataList.get( "DFTAG_SDM.max" ).intValue() );
				min = Float.intBitsToFloat( sdgDataList.get( "DFTAG_SDM.min" ).intValue() );
//				if( DEBUG )
					IJ.log( "DFTAG_SDM says (min,max)=("+min+","+max+")" );
			}
			/* Create image */
			byte[] img = sdgDataList.get( "DFTAG_SD" ).get();
			String name = "HDF-"+sdl.label(0);
			int row = sdd.dim(0);
			int col = sdd.dim(1);
			FloatProcessor ip = new FloatProcessor( col, row );
			int pointer = 0;
			for( int y=0; y<row; y++ )
				for( int x=0; x<col; x++ ) {
					ip.setf( x, y, Float.intBitsToFloat( toInteger( partOf( img, pointer, 4 ) ) ) );
					pointer += 4;
				}
//			IJ.log( "Imported "+ip.toString() );
			ImagePlus imp = new ImagePlus( name, ip );
			if ( imp != null ) {
//				imp.show();
				if( DEBUG )
					IJ.log( "parseSDG: image exists." );
				/* parse and set LUT */
				if( sdgDataList.containsKey( "DFTAG_LUT") ) {
//					IJ.log( "Found DFTAG_LUT" );
					byte[] lutAry = sdgDataList.get( "DFTAG_LUT" ).get();
					int j = 0, size = 256;
					byte[] red = new byte[size], green = new byte[size], blue = new byte[size];
					for( int i=0; i<size; i++ ) {
						red[i] = lutAry[j++];
						green[i] = lutAry[j++];
						blue[i] = lutAry[j++];
//						if( DEBUG )
//							IJ.log( "["+i+"]"+red[i]+","+green[i]+","+blue[i] );
					}
					/*String s="";
					for( int i=0; i<size; i++ ) s += red[i]+",";
					IJ.log( "r:"+s );
					s="";
					for( int i=0; i<size; i++ ) s += green[i]+",";
					IJ.log( "g:"+s );
					s="";
					for( int i=0; i<size; i++ ) s += blue[i]+",";
					IJ.log( "g:"+s ); */
					
					ImageProcessor ipr = imp.getChannelProcessor();
					IndexColorModel cm = new IndexColorModel(8, size, red, green, blue );
					if (imp.isComposite())
						( (CompositeImage) imp ).setChannelColorModel( cm );
					else
						ipr.setColorModel( cm );
					if( min!=0 || max!=0 )
						ipr.setMinAndMax(min, max);
//					imp.updateAndRepaintWindow();
				}
				return imp;
			}
		} catch (HdfException e) {
			IJ.showMessage( "HDF32opener", ""+e );
		}
		return null;
	}

	// 701: // SDD Dimension Record]
	class DFTAG_SDD {
		// 01,   2345, 6789, 01,  23,   45,   67,   89
		// rank, dim1, dim2, ref, ref1, ref2, ref3, ref4
		int rank;
		int[] dim;
//		int[] ref;
		DFTAG_SDD( Location fs ) throws IOException {
//			IJ.log( "DFTAG_SDD:"+fs.debug() );
			if( fs==null || fs.typ!=BIN ) {
				rank = 0;
				dim = new int[0];
				return;
			}
			rank = fs.pop(INT,2).intValue();
//			IJ.log( "DFTAG_SDD:rank="+rank );
			dim = new int[rank];
			for( int i=0; i<rank; i++ )
				dim[i] = fs.pop(INT,4).intValue();
//			for( int i=0; i<=rank; i++ ) // ref, ref1, ref2, ref4, ...
//				ref[i] = ddlist.getId( fs.pop(INT,2).intValue(), fs.pop(INT,2).intValue() );
		}
		int dim( int rank ){ return dim[rank]; }
		int rank() { return rank; }
	}

	// 704: // SDL Data Labels
	class DFTAG_SDL {
		String[] label;
		DFTAG_SDL( Location fs ) throws IOException {
//			IJ.log( "DFTAG_SDL:"+fs.debug() );
			if( fs==null ) {
				label = new String[0];
				return;
			}
			byte[] b = fs.get();
			int rank = 0;
			for( int i=0; i<b.length; i++ )
				if( b[i]==0 )
					rank++;
//			IJ.log( "DFTAG_SDL:rank="+rank );
			label = new String[rank];
			for( int r=0; r<rank; r++ )
				label[r] = "";
			int r = 0;
			for( int i=0; i<b.length; i++ ){
				if( b[i]==0 )
					r++;
				else
					label[r] += (char) b[i];
			}
		}
		String label( int rank ) { return rank<rank() ? label[rank] : "" ; }
		int rank() { return label.length; }
	}

	@SuppressWarnings("serial")
	public class HdfException extends Exception {
		public HdfException(String message) { super(message); }
	}
}


