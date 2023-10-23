import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ij.io.FileInfo;
import ij.io.FileOpener;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.util.StringSorter;
import ij.measure.Calibration;
/**
 * Image file opener for image file.
 * @author Koutaro Abe ( kabe@cris.hokudai.ac.jp )
 * @version 10-Aug-2010
 * ORGINAL: ij.io.ImportDialog
 */
public class ImsOpener {

//	static final String ImsFilenameRegex = "^_[0-9]+_s[0-9]+b[0-9]+$"; // strict
//	static final String ImsFilenameRegex = "^_*$";
	static final int IMSWIDTH = 608, IMSHEIGHT = 576, IMSTYPE = 5, IMSPIXSIZE = 4;
	static final int IMSFILESIZE = IMSWIDTH * IMSHEIGHT * IMSPIXSIZE;
	static final String TYPE = "ims.raw.type";
	static final String WIDTH = "ims.raw.width";
	static final String HEIGHT = "ims.raw.height";
	static final String OFFSET = "ims.raw.offset";
	static final String N = "ims.raw.n";
	static final String GAP = "ims.raw.gap";
	static final String OPTIONS = "ims.raw.options";
	static final int WHITE_IS_ZERO = 1;
	static final int INTEL_BYTE_ORDER = 2;
	static final int OPEN_ALL = 4;

	// default settings
	private static int choiceSelection = Prefs.getInt(TYPE, IMSTYPE);
	private static int width = Prefs.getInt(WIDTH, IMSWIDTH);
	private static int height = Prefs.getInt(HEIGHT, IMSHEIGHT);
	private static long offset = Prefs.getInt(OFFSET, 0);
	private static int nImages = Prefs.getInt(N, 1);
	private static int gapBetweenImages = Prefs.getInt(GAP, 0);
	private static int options;
	private static boolean whiteIsZero, intelByteOrder;
	private static boolean virtual;
	private boolean asStack;
	private static FileInfo lastFileInfo;
	private static String[] types = { "8-bit", "16-bit Signed",
			"16-bit Unsigned", "32-bit Signed", "32-bit Unsigned",
			"32-bit Real", "64-bit Real", "24-bit RGB", "24-bit RGB Planar",
			"24-bit BGR", "24-bit Integer", "32-bit ARGB", "32-bit ABGR",
			"1-bit Bitmap" };
	static {
		options = Prefs.getInt(OPTIONS, 0);
		whiteIsZero = (options & WHITE_IS_ZERO) != 0;
		intelByteOrder = (options & INTEL_BYTE_ORDER) != 0;
		// openAll = (options&OPEN_ALL)!=0;
	}
	private static boolean dialog;
	private List<File> fileList;

	public ImsOpener( List<File> filePathList, boolean dialogFlag, boolean asStackFlag) {
		if( filePathList.size()==0 )
			return;
		fileList = filePathList;
		asStack = asStackFlag;
		dialog = dialogFlag;
//		IJ.showStatus("ImsOpener("+ fileList.size() +" files): ");
	}

	public ImsOpener( List<File> filePathList, boolean dialog) {
		this(filePathList, dialog, false);
	}

	public ImsOpener( List<File> filePathList) {
		this(filePathList, false, false);
	}

	public ImsOpener( String filePathString, boolean dialogFlag, boolean asStackFlag) {
		if( filePathString.length()==0 )
			return;
		fileList = new ArrayList<File>();
		fileList.add( new File(filePathString) );
		asStack = asStackFlag;
		dialog = dialogFlag;
		IJ.showStatus("ImsOpener(1 file): " + filePathString );
	}

	public ImsOpener( String filePathString, boolean dialog) {
		this(filePathString, dialog, false);
	}

	public ImsOpener( String filePathString) {
		this(filePathString, false, false);
	}

	// not used.
	boolean showDialog() {
		if (!dialog)
			return true;
		if (choiceSelection >= types.length)
			choiceSelection = 0;
		GenericDialog gd = new GenericDialog("Import...", IJ.getInstance());
		gd.addChoice("Image Type:", types, types[IMSTYPE]);
		gd.addNumericField("Width:", width, IMSWIDTH, 6, "pixels");
		gd.addNumericField("Height:", height, IMSHEIGHT, 6, "pixels");
		gd.addNumericField("Offset to First Image:", offset, 0, 6, "bytes");
		gd.addNumericField("Number of Images:", nImages, 0, 6, null);
		gd.addNumericField("Gap Between Images:", gapBetweenImages, 0, 6, "bytes");
		gd.addCheckbox("White is Zero", whiteIsZero);
		gd.addCheckbox("Little-Endian Byte Order", intelByteOrder);
		gd.addCheckbox("Open All Files in Folder", asStack);
		gd.addCheckbox("Use Virtual Stack", virtual);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		choiceSelection = gd.getNextChoiceIndex();
		width = (int) gd.getNextNumber();
		height = (int) gd.getNextNumber();
		offset = (long) gd.getNextNumber();
		nImages = (int) gd.getNextNumber();
		gapBetweenImages = (int) gd.getNextNumber();
		whiteIsZero = gd.getNextBoolean();
		intelByteOrder = gd.getNextBoolean();
		asStack = gd.getNextBoolean();
		virtual = gd.getNextBoolean();
		IJ.register(ImsOpener.class);
		return true;
	}

	/**
	 * Displays the dialog and opens the specified image or images. Does nothing
	 * if the dialog is canceled.
	 */
	public void open() {
		int i;
		if( fileList.size()<1 )
			return;
		if( asStack==true ) {
			if( fileList.size()==1 && fileList.get(0).isDirectory() ) { // when selected one directory
				// Open a directory as a Stack
				File[] children = fileList.get(0).listFiles();
				List<File> childList = new ArrayList<File>();
				for( i=0; i<children.length; i++ )
					childList.add( children[i] );
				openAsStack( childList, getFileInfo( fileList.get(0) ) );
			} else
				// Open multiple files as a Stack
				openAsStack( fileList, getFileInfo( fileList.get(0).getParentFile() ) );
		} else
			// Open Slice(s) 
			for( i=0; i<fileList.size(); i++ )
				openAsSlice( getFileInfo( fileList.get(i) ) );
	}

	/** Open a slice from a image file. */
	ImagePlus openAsSlice( FileInfo fi ){
		if( fi==null || fi.directory.length()==0 || fi.fileName.length()==0 )
			return null;
		String filepath = fi.directory+fi.fileName;
		File f = new File( filepath );
		if( f.isDirectory() ) {
			IJ.showMessage( "Could not open directory.\nPlease DRAG image file(s) and DROP into button." );
			return null;	
		}
		ImagePlus imp = new HDF32Opener( filepath ).open(true);
		if( imp==null && isIms(f) ) {
			imp = new FileOpener(fi).open(true);
		}
		if( imp==null ) {
			IJ.log( "Skipped '" + fi.fileName +"' (invalid file size)." );
			return null;
		}
		return imp;
	}
	
	/** Open a stack from multiple image files. */
	ImagePlus openAsStack( List<File> list, FileInfo fi) {
		if (list==null || fi==null )
			return null;
		// sort list by filename
		String[] listAry = new String[list.size()];
		for( int i=0; i<list.size(); i++ )
			listAry[i] = list.get(i).getPath();
		StringSorter.sort(listAry);
		for( int i=0; i<list.size(); i++ )
			list.set( i, new File( listAry[i] ) );
		ImageStack stack = null;
		ImagePlus imp = null;
		File f = null;
		int skipped = 0;
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		for (int i=0; i<list.size(); i++) {
			f = list.get(i);
			fi.fileName = f.getName();
			fi.directory = f.getParent();
//			IJ.log( "openAsStack["+i+"] "+fi.fileName+": file?"+f.isFile()+" dir?"+f.isDirectory()+" hidden?"+f.isHidden() );
			if( f.isFile() ) {
				imp = new HDF32Opener( f ).open(false);
				if( imp==null && isIms(f) )
					imp = new FileOpener(fi).open(false);
				if( imp==null ) {
					IJ.log( "Skipped '" + fi.fileName +"' (invalid file size). continue ..." );
					skipped++;
					continue;
				}
				if (stack == null)
					stack = imp.createEmptyStack();
				try {
					ImageProcessor ip = imp.getProcessor();
					if (ip.getMin() < min)
						min = ip.getMin();
					if (ip.getMax() > max)
						max = ip.getMax();
					stack.addSlice( fi.fileName, ip );
				} catch (OutOfMemoryError e) {
					IJ.outOfMemory("OpenAll");
					stack.trim();
					break;
				}
				IJ.showStatus((stack.getSize() + 1) + ": " + listAry[i]);
			}
		}
		if( skipped>0 )
			IJ.log( "Opened "+(list.size()-skipped)+" file(s)." );
		if (stack==null)
			return null;
		imp = new ImagePlus( new File(fi.directory).getName(), stack);
		if (imp.getBitDepth() == 16 || imp.getBitDepth() == 32)
			imp.getProcessor().setMinAndMax(min, max);
		Calibration cal = imp.getCalibration();
		if (fi.fileType == FileInfo.GRAY16_SIGNED) {
			double[] coeff = new double[2];
			coeff[0] = -32768.0;
			coeff[1] = 1.0;
			cal.setFunction(Calibration.STRAIGHT_LINE, coeff, "Gray Value");
		}
		imp.show();
//		IJ.log( "Opened: id="+imp.getID() );
		return imp;
	}

	
	/**
	 * Displays the dialog and returns a FileInfo object that can be used to
	 * open the image. Returns null if the dialog is canceled. The fileName and
	 * directory fields are null if the no argument constructor was used.
	 */
	public FileInfo getFileInfo( File file ) {
		if (!showDialog())
			return null;
		String imageType = types[choiceSelection];
		FileInfo fi = new FileInfo();
		fi.fileFormat = FileInfo.RAW;
		fi.fileName = file.getName();
		fi.directory = file.getParent();
		if (fi.directory.length()>0 && !fi.directory.endsWith(Prefs.separator))
			fi.directory += Prefs.separator;
		fi.width = width;
		fi.height = height;
		if (offset > 2147483647)
			fi.longOffset = offset;
		else
			fi.offset = (int) offset;
		fi.nImages = nImages;
		fi.gapBetweenImages = gapBetweenImages;
		fi.intelByteOrder = intelByteOrder;
		fi.whiteIsZero = whiteIsZero;
		if (imageType.equals("8-bit"))
			fi.fileType = FileInfo.GRAY8;
		else if (imageType.equals("16-bit Signed"))
			fi.fileType = FileInfo.GRAY16_SIGNED;
		else if (imageType.equals("16-bit Unsigned"))
			fi.fileType = FileInfo.GRAY16_UNSIGNED;
		else if (imageType.equals("32-bit Signed"))
			fi.fileType = FileInfo.GRAY32_INT;
		else if (imageType.equals("32-bit Unsigned"))
			fi.fileType = FileInfo.GRAY32_UNSIGNED;
		else if (imageType.equals("32-bit Real"))
			fi.fileType = FileInfo.GRAY32_FLOAT;
		else if (imageType.equals("64-bit Real"))
			fi.fileType = FileInfo.GRAY64_FLOAT;
		else if (imageType.equals("24-bit RGB"))
			fi.fileType = FileInfo.RGB;
		else if (imageType.equals("24-bit RGB Planar"))
			fi.fileType = FileInfo.RGB_PLANAR;
		else if (imageType.equals("24-bit BGR"))
			fi.fileType = FileInfo.BGR;
		else if (imageType.equals("24-bit Integer"))
			fi.fileType = FileInfo.GRAY24_UNSIGNED;
		else if (imageType.equals("32-bit ARGB"))
			fi.fileType = FileInfo.ARGB;
		else if (imageType.equals("32-bit ABGR"))
			fi.fileType = FileInfo.ABGR;
		else if (imageType.equals("1-bit Bitmap"))
			fi.fileType = FileInfo.BITMAP;
		else
			fi.fileType = FileInfo.GRAY8;
		if (IJ.debugMode)
			IJ.log("ImportDialog: " + fi);
		lastFileInfo = (FileInfo) fi.clone();
		return fi;
	}

	/** Called once when ImageJ quits. */
	public static void savePreferences() {
		Prefs.set(TYPE, Integer.toString(choiceSelection));
		Prefs.set(WIDTH, Integer.toString(width));
		Prefs.set(HEIGHT, Integer.toString(height));
		Prefs.set(OFFSET, Integer.toString(offset > 2147483647 ? 0
				: (int) offset));
		Prefs.set(N, Integer.toString(nImages));
		Prefs.set(GAP, Integer.toString(gapBetweenImages));
		int options = 0;
		if (whiteIsZero)
			options |= WHITE_IS_ZERO;
		if (intelByteOrder)
			options |= INTEL_BYTE_ORDER;
		// if (openAll)
		// options |= OPEN_ALL;
		Prefs.set(OPTIONS, Integer.toString(options));
	}

	/**
	 * Returns the FileInfo object used to import the last raw image, or null if
	 * a raw image has not been imported.
	 */
	public static FileInfo getLastFileInfo() {
		return lastFileInfo;
	}
	/**  Is this IMS file? */
	public boolean isIms( File f ) {
		if( !f.isFile() )
			return false;
		long s = f.length();
		if( (s % IMSPIXSIZE)!=0 || (s % IMSWIDTH)!=0 )
			return false;
		return true;
	}
}
