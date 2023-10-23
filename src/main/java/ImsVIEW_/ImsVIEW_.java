import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.*;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.OpenDialog;
import ij.io.DirectoryChooser;

/**
 * Helper plug-in for Isotope Microscope.
 * @author Koutaro Abe ( kabe@cris.hokudai.ac.jp )
 * @version 19-Nov-2012
 */
@SuppressWarnings("serial")
public class ImsVIEW_ extends JFrame
	implements ActionListener, MouseListener, MouseWheelListener, KeyListener,
		DropTargetListener {

	final static String version="2013-05-19";
	final static String locx = "ImsVIEW.locx";
	final static String locy = "ImsVIEW.locy";
//	private static int lastNonShiftClick = -1;
	private static JFrame instance;
	static SyncManager syncManager;
	// for File Drop
	public static JCheckBox syncCheck, allCheck;
	final static String openStackLabel = "Open As STACK";
	final static String openSliceLabel = "Open Separately";
	final static String syncCheckLabel = "Sync ROI";
	final static String allCheckLabel = "Check All";
	private DropTarget openStackBtn, openSliceBtn;
	private ArrayList<DropTarget> dropTargetTab;
	final static String roimanagerLabel = "ROI Manager";
	final static String togglesyncLabel = "Toggle Sync", listlistenersLabel="list listeners";
	// LUT shortcut
	final static String lutMenuLabel = "LUT menu "+'\u00bb';
	final static int recentLutCount = 3;
	final static String recentLutKey = "ims.recent_lut_";
	RecentlyUsed recentLut;
	FilePopupMenu lutMenu;
	private JButton lutMenuBtn;
	int previousID;
	private JTabbedPane buttonTabPane;

	public ImsVIEW_() {
		super("ImsVIEW");
		if ( IJ.versionLessThan( "1.39t" ) ){
			IJ.showMessage( "This plug-in requires ImageJ version 1.39t or later." );
			return;
		}
		if ( instance!=null ) {
			instance.setVisible( true );
			instance.toFront();
			return;
		}
		instance = this;
		if( syncManager==null )
			syncManager = new SyncManager( true );
		showWindow();
	}

	static boolean exists() {
		return instance!=null;
	}
	
	void showWindow() {
		ImageJ ij = IJ.getInstance();
 		addKeyListener(ij);
 		addMouseListener(this);
		addMouseWheelListener(this);
		WindowManager.addWindow(this);

		setTitle( "ImsVIEW ver."+version );
		setLayout( new BorderLayout( 5, 5 ) );
		
		// right side
		buttonTabPane = new JTabbedPane();
		dropTargetTab = new ArrayList<DropTarget>();
		JLabel dummy = new JLabel( "" );
		dummy.setMaximumSize( new Dimension( Short.MAX_VALUE, 4 ) );
		dummy.setPreferredSize( new Dimension( 180, 4 ) );

		// TAB0
		JPanel panel0 = new JPanel();
		panel0.setLayout( new BoxLayout( panel0, BoxLayout.Y_AXIS ) );
		panel0.add( dummy );
		panel0.add( title( "Open [32bit-RAW] or [HDF v3.2]" ) );
		JButton b0 = button( openStackLabel, "Drop a folder or files here." );
		panel0.add( b0 );
		openStackBtn = new DropTarget( b0, this );
		JButton b1 = button( openSliceLabel, "Drop a folder or files here." );
		panel0.add( b1 );
		openSliceBtn = new DropTarget( b1, this );	
		panel0.add( title( "Apply Look Up Table" ) );
		lutMenuBtn = button( lutMenuLabel, "Shortcut to LUT" );
		panel0.add( lutMenuBtn );
		// add LUT popup menu (same as 'Image'->'Lookup Tables') ...		 	 		
		lutMenu = new FilePopupMenu( lutMenuLabel, IJ.getDirectory("luts"), ".lut", this );
 		// AND add recently used buttons
 		recentLut = new RecentlyUsed( recentLutKey, recentLutCount );
		recentLut.addButtons( panel0, this );
		panel0.add( title( "" ) );
		panel0.add( button( roimanagerLabel, "" ) );
		panel0.add( button( togglesyncLabel, "" ) );
		panel0.add( button( listlistenersLabel, "" ) );
		buttonTabPane.addTab( "Files", panel0 );
		if( IJ.isJava16() ) {
			JLabel l = new JLabel( "Files" );
			buttonTabPane.setTabComponentAt( 0, l );
			dropTargetTab.add( new DropTarget( l, this ) );
		}

		// TAB1
		JPanel panel1 = new JPanel();
		panel1.setLayout( new BoxLayout( panel1, BoxLayout.Y_AXIS ) );
		panel1.add( title( "Subtract STACK image" ) );
		panel1.add( button( "Total image", "" ) );
		panel1.add( button( "Differential", "" ) );
		panel1.add( button( "Subtract Top", "" ) );
		panel1.add( button( "Set parameters...", "Set parameters and Calculate." ) );
		panel1.add( button( "Preference...", "Configure NR level, log ..." ) );
		panel1.add( title( "Correct Background" ) );
		panel1.add( button( "Subtract background", "Result = SLICE(n) - SLICE(1)" ) );
		panel1.add( title( "Push out STACK image" ) );
		panel1.add( button( "Use height map", "Z-axis slide." ) );
		panel1.add( button( "Auto-detect height", "Z-axis slide(EXPERIMENTAL)." ) );
		buttonTabPane.addTab( "Calc", panel1 );

		// TAB2
		JPanel panel2 = new JPanel();
		panel2.setLayout( new BoxLayout( panel2, BoxLayout.Y_AXIS ) );
		panel2.add( title( "Another views" ) );
		panel2.add( button( "Inspect", "pixel value table." ) );
		panel2.add( button( "Orthogonal Views", "ImageJ - Image - Stacks - Orthogonal Views" ) );
		panel2.add( title( "Measurements" ) );
		panel2.add( button( "Lined", "ImageJ - Plugins - Graphics - Dynamic Profiler" ) );
		panel2.add( button( "Area", "ImageJ - Analyze - Measure" ) );
		panel2.add( button( "Depth", "ImageJ - Stacks - Plot Z-axis Profile" ) );
		panel2.add( button( "Measure all", "'Measure' all opened images." ) );
		buttonTabPane.addTab( "Measure", panel2 );

		// TAB3
		JPanel panel3 = new JPanel();
		panel3.setLayout( new BoxLayout( panel3, BoxLayout.Y_AXIS ) );
		panel3.add( title( "1. Open" ) );
		panel3.add( button( "Open image(s)", "Open slices." ) );
		panel3.add( dummy );
		panel3.add( title( "2. Select" ) );
		panel3.add( dummy );
		panel3.add( title( "3. Set measurements" ) );
		panel3.add( button( "Measure", "" ) );
		panel3.add( button( "Measure all", "" ) );
		buttonTabPane.addTab( "ROI", panel3 );

		add( buttonTabPane, BorderLayout.EAST );

		pack();
		GUI.center(this);
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int x = (int) Prefs.get( locx, screen.width - instance.getWidth() - 16 );
		int y = (int) Prefs.get( locy, 16 );
		instance.setLocation( x, y );
		setVisible(true);
	}

	JLabel title( String title ) {
		JLabel t = new JLabel( title );
		t.setVerticalAlignment( SwingConstants.BOTTOM );
		t.setMaximumSize( new Dimension( Short.MAX_VALUE, 16 ) );
		return t;		
	}

	JButton button( String name, String tips ) {
		JButton b = new JButton( name );
		b.setMaximumSize( new Dimension( Short.MAX_VALUE, b.getMaximumSize().height ) );
		b.setMargin( new Insets(4, 8, 4, 8) );
		b.setToolTipText( tips );
		b.addActionListener( this );
		b.addKeyListener( IJ.getInstance() );
		return b;
	}
	
	public void actionPerformed( ActionEvent e ) {
//		int modifiers = e.getModifiers();
//		boolean altKeyDown = (modifiers&ActionEvent.ALT_MASK)!=0 || IJ.altKeyDown();
//		boolean shiftKeyDown = (modifiers&ActionEvent.SHIFT_MASK)!=0 || IJ.shiftKeyDown();
		IJ.setKeyUp(KeyEvent.VK_ALT);
		IJ.setKeyUp(KeyEvent.VK_SHIFT);
		String label = e.getActionCommand();
		if( label==null )
			return;
		else if (label.equals( lutMenuLabel ) ) {
			Point ploc = buttonTabPane.getLocation();
			Point bloc = lutMenuBtn.getLocation();
			lutMenu.refresh( );
			lutMenu.show( this, ploc.x, bloc.y );
			return;
		}
		ImagePlus imp = WindowManager.getCurrentImage();
		if( imp!=null ) {
			ImageProcessor ip = imp.getProcessor();
			int id = imp.getID();
			if( id!=previousID )
				ip.snapshot();
			previousID = id;
			new ImsVIEWRunner( this, label, imp, ip );
		} else {
			IJ.showStatus( "No image" );
			previousID = 0;
			new ImsVIEWRunner( this, label );
		}
	}

	@Override
	public void processWindowEvent( WindowEvent e ) {
		super.processWindowEvent(e);
		if( e.getID()==WindowEvent.WINDOW_CLOSING ) {
			Prefs.set( locx, instance.getX() );
			Prefs.set( locy, instance.getY() );
			instance = null;
		}
	}
	public void mouseClicked(MouseEvent arg0) { }
	public void mouseEntered(MouseEvent me) { }
	public void mouseExited(MouseEvent arg0) { }
	public void mousePressed(MouseEvent arg0) { }
	public void mouseReleased(MouseEvent arg0) { }
	public void mouseWheelMoved(MouseWheelEvent arg0) { }
	public void keyPressed(KeyEvent arg0) { }
	public void keyReleased(KeyEvent arg0) { }
	public void keyTyped(KeyEvent arg0) { }
	public void dragEnter( DropTargetDragEvent dtde ) {
		instance.toFront();
		for( int i=0; i<dropTargetTab.size(); i++ )
			if( dtde.getSource().equals( dropTargetTab.get(i) ) )
				buttonTabPane.setSelectedIndex( i );
	}
	public void dragExit( DropTargetEvent arg0 ) { }
	public void dragOver( DropTargetDragEvent dtde ) {}
	@SuppressWarnings("unchecked")
	public void drop( DropTargetDropEvent dtde ) { 		// based on DropTest2.java 
		try {
			// Ok, get the dropped object and try to figure out what it is
			Transferable tr = dtde.getTransferable();
/*			if( tr.isDataFlavorSupported( DataFlavor.javaFileListFlavor ) ){
				IJ.log( "isDataFlavorSupported" );
				java.util.List<File> files = (java.util.List<File>) tr.getTransferData( DataFlavor.javaFileListFlavor );
				IJ.log( "files.size = "+files.size() );
				for( int i=0; i<files.size(); i++ )
					IJ.log(" files["+i+"]="+files.toString() );
				if( dtde.getSource().equals( openBtn ) )
					new ImsVIEWRunner( this, "Drop:Open as Stack", files );
				if( dtde.getSource().equals( openSliceBtn ) )
					new ImsVIEWRunner( this, "Drop:Open separately", files );
				dtde.dropComplete(true);
				return;
			} */
			DataFlavor[] flavors = tr.getTransferDataFlavors();
//			IJ.log( "Transferable: " + tr.toString()+"("+flavors.length+")" );
			for (int i = 0; i < flavors.length; i++) {
//				IJ.log( "Possible flavor: "	+ flavors[i].getMimeType());
				// Check for file lists specifically
				if( flavors[i].isFlavorJavaFileListType() ) {
					dtde.acceptDrop( DnDConstants.ACTION_COPY_OR_MOVE );
					java.util.List<File> list = (java.util.List) tr.getTransferData(flavors[i]);
//					for (int j = 0; j < list.size(); j++)
//						IJ.log( list.get(j) +":" + list.get(j).getClass()+"\n" );
					if( dtde.getSource().equals( openStackBtn ) )
						new ImsVIEWRunner( this, "Drop:Open as Stack", list );
					if( dtde.getSource().equals( openSliceBtn ) )
						new ImsVIEWRunner( this, "Drop:Open separately", list );
					// If we made it this far, everything worked.
					dtde.dropComplete(true);
					return;
				}
			}
			// Hmm, the user must not have dropped a file list
			IJ.log( "Sorry, drop failed: " + dtde );
			for (int i = 0; i < flavors.length; i++)
				IJ.log( "  Possible flavor: "	+ flavors[i].getMimeType());
			dtde.rejectDrop();
		} catch (Exception e) {
			e.printStackTrace();
			dtde.rejectDrop();
		}
	}
	public void dropActionChanged( DropTargetDragEvent dtde ) { }
	public void itemStateChanged(ItemEvent e) { }
}




class ImsVIEWRunner extends Thread {

	static final String DIR_OPENSTACK = "ims.openAsStack.defaultdir";
	static final String DIR_OPENSLICE = "ims.openAsSlice.defaultdir";

	private String command;
	private ImagePlus imp;
	private ImageProcessor ip;
	private java.util.List<File> target;
	RoiManager roiManager;
	ResultsTable resultTable;
	ImsVIEW_ parent;

	ImsVIEWRunner( ImsVIEW_ parent, String command, ImagePlus imp, ImageProcessor ip, java.util.List<File> target ) {
		super(command);
		this.command = command;
		this.imp = imp;
		this.ip = ip;
		this.target = target;
		this.parent = parent;
		setPriority(Math.max(getPriority()-2, MIN_PRIORITY));
		start();
	}
	ImsVIEWRunner( ImsVIEW_ parent, String command, ImagePlus imp, ImageProcessor ip ) {
		this( parent, command, imp, ip, null );
	}
	ImsVIEWRunner( ImsVIEW_ parent, String command, java.util.List<File> target ) {
		this( parent, command, null, null, target );
	}
	ImsVIEWRunner( ImsVIEW_ parent, String command ) {
		this( parent, command, null, null, null );
	}

	@Override
	public void run() {
		try {
			runCommand( command, imp, ip, target );
		} catch ( OutOfMemoryError e ) {
			IJ.outOfMemory( command );
		} catch( Exception e ) {
			CharArrayWriter caw = new CharArrayWriter( );
			PrintWriter pw = new PrintWriter( caw );
			e.printStackTrace( pw );
			IJ.log( caw.toString( ) );
			IJ.showStatus("");
		}
		if ( imp!=null )
			imp.unlock();
	}

	void runCommand(String command, ImagePlus imp, ImageProcessor ip, java.util.List<File> target ) {
		IJ.showStatus(command + "...");
		if( imp!=null && ip!=null ) { // these commands requires 'current image'.
			if( command.equals("Depth") ) {
				ijRun("Plot Z-axis Profile");
				
			} else if( command.equals("Area") ) {
				ijRun("Measure");
				
			} else if( command.equals("Line") ) {
				ijRun("Dynamic Profiler");
				
			} else if( command.equals("Inspect") ) {
				new PixelInspector2_();
//				if( ImsVIEW_.syncroi!=null )
//					ImsVIEW_.syncroi.setListener( ImsVIEW_.imgList.getCheckedImp() );
				
			} else if ( command.equals("Orthogonal Views") ) {
				ijRun("Orthogonal Views");
				
			} else if( command.equals("Measure all") ) {
				// ---> better to use imgList 
				int[] image = WindowManager.getIDList();
				if( image.length<1 ) // no image windows opened
					return;
				if( resultTable==null )
					resultTable = new ResultsTable();
				Roi[] rois = { null };
				roiManager = RoiManager.getInstance();
				if( roiManager!=null )
					rois = roiManager.getSelectedRoisAsArray();
				for( int j=0; j<rois.length; j++ )
					for( int i=0; i<image.length; i++ ){
						ImagePlus currentImp = WindowManager.getImage( image[i] );
						if( currentImp==null )
							continue;
						measureImp( currentImp, rois[j], resultTable  );
					}
				resultTable.show("Results all images");
				
			} else if ( command.equals("Differential") ) {
				Delta d = new Delta( imp );
				d.differential();
				d.show();
				
			} else if ( command.equals("Total image" ) ) {
				Delta d = new Delta( imp );
				d.total();
				d.show();
				
			} else if ( command.equals("Subtract Top" ) ) {
				Delta d = new Delta( imp );
				d.top();
				d.show();
				
			} else if ( command.equals("Set parameters..." ) ) {
				Delta d = new Delta( imp );
				d.setParameters();
				d.show();
				
			} else if ( command.equals("Preference..." ) ) {
				Delta.setNoiseReductionLevel();
				
			} else if ( command.equals("Subtract background" ) ) {
				new BGCorrector( imp );
				
			} else if ( command.equals("Use height map" ) ) {
				new Pushdown( imp.getStack(), false );
				imp.updateImage();
				
			} else if ( command.equals("Auto-detect height" ) ) {
				new Pushdown( imp.getStack(), true );
				imp.updateImage();
				
			} else if ( command.equals( ImsVIEW_.togglesyncLabel ) ) {
//				ImsVIEW_.syncManager.toggleReciever( imp );
				
			} else if ( command.equals( ImsVIEW_.listlistenersLabel ) ) {
//				ImsVIEW_.syncroi.refleshReciever( );
			}
		}
		// not requires 'current image'.
		if ( command.equals( ImsVIEW_.openStackLabel ) ) { 
			String defaultdir;
			if( ( defaultdir = Prefs.get( DIR_OPENSTACK, "---" ) ) != "---" )
				DirectoryChooser.setDefaultDirectory( defaultdir );
			DirectoryChooser od = new DirectoryChooser( "Open directory for '32bit-Real Raw' or 'HDF v3.2' ..." );
			if( od!=null && od.getDirectory()!=null && od.getDirectory()!="" ) {
				new ImsOpener( od.getDirectory(), false, true ).open();
				Prefs.set( DIR_OPENSTACK, od.getDirectory() );
			}

		} else if( command.equals("Drop:Open as Stack") ){
			new ImsOpener( target, false, true ).open();

		} else if( command.equals( ImsVIEW_.openSliceLabel ) ){
			String defaultdir;
			if( ( defaultdir = Prefs.get( DIR_OPENSLICE, "---" ) ) != "---" )
				DirectoryChooser.setDefaultDirectory( defaultdir );
			OpenDialog od = new OpenDialog( "Open file '32bit-Real Raw' or 'HDF v3.2' ...", null );			
			if( od!=null && od.getDirectory()!=null && od.getDirectory()!="" ) {
				new ImsOpener( od.getDirectory()+od.getFileName(), false, false ).open();
				Prefs.set( DIR_OPENSLICE, od.getDirectory() );
			}

		} else if( command.equals("Drop:Open separately") ){
			new ImsOpener( target, false, false ).open();

		} else if( command.equals("Open HDF v3.2") ) {
			OpenDialog od = new OpenDialog( "Open HDF v3.2 file...", null );
			new HDF32Opener( od.getDirectory()+od.getFileName() ).open(true);

		} else if( command.equals("Drop:Open HDF v3.2") ) {
			for( int i=0; i<target.size(); i++ )
				if( target.get(i).isFile() )
					new HDF32Opener( target.get(i).toString() ).open(true);

		} else if( command.equals("BATCH Total image /slice") ) {
			try {
				String topDir;
				ImagePlus sourceImp, destImp;
				topDir = new DirectoryChooser( "Select top folder" ).getDirectory();
				if( topDir==null )
					return;
				java.util.List<File> list = Arrays.asList( new File(topDir).listFiles() );	// ディレクトリ内のファイル名リスト
				IJ.log( "BATCH - Top directory:"+topDir );
				for( int i=0; i<list.size(); i++ ) {
					// ディレクトリ内のファイル名(source)ごとにloop
					if( !list.get(i).isDirectory() ) continue;
					// ディレクトリ内のディレクトリを、スタック画像として開く
					ImsOpener d = new ImsOpener( list.subList(i,i), false, true );
					d.open();
					sourceImp = WindowManager.getCurrentImage();
					if ( sourceImp==null || sourceImp.getStack().getSize()<2 ) continue;
					// 新しい画像（Delta）を作る
					Delta delta = new Delta( sourceImp );
					delta.total();
					delta.show();
					destImp = WindowManager.getCurrentImage();
					String saveFilename = delta.srcTitle+".tif";
					// 新しい画像（Delta）を保存
					Macro.saveAs( topDir+"/"+saveFilename );
					IJ.log( "Saved  '"+saveFilename+"'." );
					destImp.close();
					//　
					if( sourceImp!=null ) sourceImp.close();
				}
				IJ.log( "BATCH - complete." );
			} catch( Exception e ) {
				if( !Macro.MACRO_CANCELED.equals(e.getMessage()) )
					IJ.showMessage( "Error in 'BATCH Total image /slice':"+e );
				return;
			}
		} else if ( command.equals( ImsVIEW_.roimanagerLabel ) ) {
			roiManager = RoiManager.getInstance();
			if( roiManager==null )
				roiManager = new RoiManager();
			roiManager.toFront();

		} else if ( parent.lutMenu.contains( command ) ) {
			ijRun( command );
			parent.recentLut.add( command );
		}
		if( imp!=null ) {
			imp.updateAndDraw();
			imp.unlock();
		}
		IJ.showStatus("");
	}

	void setBCWindow( double saturated ) {
		ijRun("Brightness/Contrast...");
		ijRun("Enhance Contrast", "saturated="+Double.toString( saturated ) );
	}
	void setBCWindow() { setBCWindow(0.35); }
	
	void ijRun( String command, String options ) {
		try {
			IJ.run(command, options);				
		} catch( Exception e ) {
			if (!Macro.MACRO_CANCELED.equals(e.getMessage()))
				IJ.handleException(e);
		}		
	}
	void ijRun( String command ) { ijRun( command, null ); }
	
	boolean openHDF32( String filepath ) {
		try {
			if ( filepath==null )
				return false;
			new HDF32Opener( filepath );
			return true;
		} catch( Exception e ) {
			if (!Macro.MACRO_CANCELED.equals(e.getMessage()))
				IJ.showMessage( ""+e );
			return false;
		}
	}

	boolean measureImp(ImagePlus imp, Roi roi, ResultsTable rt ) {
		if (imp==null)
			return false;
		int measurements = Analyzer.getMeasurements();
		if( imp.getStackSize()>1 )
			Analyzer.setMeasurements( measurements|Measurements.SLICE );
		if( roi!=null )
			imp.setRoi( roi );
		Analyzer analyzer = new Analyzer(imp, measurements, rt);
		analyzer.measure();
		return true;
	}	

	boolean error(String msg) {
		IJ.showMessage(msg);
		Macro.abort();
		return false;
	}
}
