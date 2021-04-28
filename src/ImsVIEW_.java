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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;

import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.*;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.FileSaver;
import ij.io.FileInfo;
import ij.io.OpenDialog;
import ij.io.DirectoryChooser;
import ij.io.SaveDialog;
/**
 * Helper plug-in for Isotope Microscopes.
 * @author kabe@cris.hokudai.ac.jp
 * @version 27-Apr-2021
 */
@SuppressWarnings("serial")
public class ImsVIEW_ extends JFrame
	implements ActionListener, ItemListener, 
		MouseListener, MouseWheelListener, KeyListener,
		DropTargetListener, ListSelectionListener, TableModelListener {

	final static String version="2021-04-27rc";
	final static String locx = "ImsVIEW.locx";
	final static String locy = "ImsVIEW.locy";
//	private static int lastNonShiftClick = -1;
	private static JFrame instance;
	static ImageList imgList;
	private static ImageListJTable imgJTable;
	static RoiSynchronizer syncroi;
	// for File Drop
	public static JCheckBox syncCheck, allCheck, showListCheck;
	final static String openStackLabel = "Open As STACK";
	final static String openSliceLabel = "Open Separately";
	final static String syncCheckLabel = "Sync ROI";
	final static String allCheckLabel = "Check All";
	final static String showListLabel = "Show Image list";
	private DropTarget openStackBtnL, openSliceBtnL, openStackBtn, openSliceBtn;
	private ArrayList<DropTarget> dropTargetTab;
	// LUT shortcut
	final static String lutMenuLabel = "LUT menu "+'\u00bb';
	final static int recentLutCount = 3;
	final static String recentLutKey = "ims.recent_lut_";
	RecentlyUsed recentLut;
	FilePopupMenu lutMenu;
	private JButton lutMenuBtn;
	int previousID;
//	private JTabbedPane buttonTabPane;
	private JPanel buttonPanel;
	private String[][] buttons = {
			{ "File",
				"*Open [32bit-RAW] or [HDF v3.2]",
					openStackLabel, openSliceLabel,
				"*Apply Look Up Table",
					lutMenuLabel,
				"*Subtract STACK image",
					"Total image", "Differential", "Subtract Top", "Set parameters...", "Preference...",
				"*Correct Background",
					"Subtract background",
				"*Push out STACK image",
					"Use height map", "Auto-detect height",
				"*Math in Float type",
					"log",
				"*Save",
					"Save checked images as TIFF",
				"*Measurements",
					"Measure all" }
	};
/*	private String[][] buttons = {
			{ "File",
				"*Open [32bit-RAW] or [HDF v3.2]",
					openStackLabel, openSliceLabel,
				"*Apply Look Up Table",
					lutMenuLabel,
				"*Save",
					"Save checked images as TIFF" },
			{ "Calc",
				"*Subtract STACK image",
					"Total image", "Differential", "Subtract Top", "Set parameters...", "Preference...",
				"*Correct Background",
					"Subtract background",
				"*Push out STACK image",
					"Use height map", "Auto-detect height",
				"*Math in Float type",
					"log" },
			{ "Measure",
				"*Another views",
					"Inspect", "Orthogonal Views",
				"*Measurements",
					"Line", "Area", "Depth", "Measure all" }
	};*/
	private String[][] tooltips = {
			{ "File",
				"Open images",
					"Drop a folder or files here.", "Drop files here.", "Drop a folder or files here.",
				"Shortcut to LUT", 
					"list of '.lut' files in lut folder.",
				"Subtract STACK image",
					"Result SLICE = SLICE(n) - SLICE(1)",
					"Result Stack = SLICE(z+1) - SLICE(1)  (z=1,2,3, ... ,n-1)",
					"Result Stack = SLICE(z+1) - SLICE(1)  (z=1,2,3, ... ,n-1)",
					"Set parameters and Calculate.",
					"Configure NR level, log ...",
				"for stack",
					"ROI.",
				"Push out STACK",
					"Z-axis slide.",
					"Z-axis slide(EXPERIMENTAL).",
				"Math in Float type",
					"Log",
				"Save images",
					"Save checked images as TIFF",
				"measure images",
					"'Measure' all opened images." }
	};
/*	private String[][] tooltips = {
			{ "File",
				"Open images",
					"Drop a folder or files here.", "Drop files here.", "Drop a folder or files here.",
				"Shortcut to LUT", 
					"list of '.lut' files in lut folder.",
				"Save images",
					"Save checked images as TIFF" },
			{ "Calc",
				"Subtract STACK image",
					"Result SLICE = SLICE(n) - SLICE(1)",
					"Result Stack = SLICE(z+1) - SLICE(1)  (z=1,2,3, ... ,n-1)",
					"Result Stack = SLICE(z+1) - SLICE(1)  (z=1,2,3, ... ,n-1)",
					"Set parameters and Calculate.",
					"Configure NR level, log ...",
				"for stack",
					"ROI.",
				"Push out STACK image",
					"Z-axis slide.",
					"Z-axis slide(EXPERIMENTAL).",
				"Math in Float type",
					"Log" },
			{ "Measure",
				"another views",
					"pixel value table.",
					"ImageJ - Image - Stacks - Orthogonal Views",
				"measure images",
					"ImageJ - Plugins - Graphics - Dynamic Profiler",
					"ImageJ - Analyze - Measure",
					"ImageJ - Stacks - Plot Z-axis Profile",
					"'Measure' all opened images." }
	};*/

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
		showWindow();
		changeAllCheck( allCheck.getSelectedObjects()!=null );
		if( syncCheck.getSelectedObjects()!=null ) { 
			changeSyncCheck( true );
			syncRoi( true );
		}
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
		
		// left side
		JPanel mainLeftPanel = new JPanel( );
		mainLeftPanel.setLayout( new BorderLayout( 2, 2 ) );
			JPanel mainLeftNorthPanel = new JPanel();
			mainLeftNorthPanel.setLayout( new BoxLayout( mainLeftNorthPanel, BoxLayout.X_AXIS ) );
				// openStack button
				JButton sb0 = new JButton( buttons[0][2] );
				mainLeftNorthPanel.add( sb0 );
				sb0.setMaximumSize( new Dimension( Short.MAX_VALUE, sb0.getMaximumSize().height ) );
				sb0.setMargin( new Insets(4, 8, 4, 8) );
				sb0.setToolTipText( tooltips[0][2] );
				sb0.addActionListener( this );
				sb0.addKeyListener( IJ.getInstance() );
				mainLeftNorthPanel.add( sb0 );
				openStackBtnL = new DropTarget( sb0, this );
				// openSlice button
				JButton sb1 = new JButton( buttons[0][3] );
				mainLeftNorthPanel.add( sb1 );
				sb1.setMaximumSize( new Dimension( Short.MAX_VALUE, sb1.getMaximumSize().height ) );
				sb1.setMargin( new Insets(4, 8, 4, 8) );
				sb1.setToolTipText( tooltips[0][3] );
				sb1.addActionListener( this );
				sb1.addKeyListener( IJ.getInstance() );
				mainLeftNorthPanel.add( sb1 );
	 			openSliceBtnL = new DropTarget( sb1, this );
			mainLeftPanel.add( mainLeftNorthPanel, BorderLayout.NORTH );
			if( imgList==null ) {
				imgList = new ImageList();
				imgList.addTableModelListener( this );
			}
			imgJTable = new ImageListJTable( imgList );
			imgJTable.getSelectionModel().addListSelectionListener( this );
			
			// left side upper
			JScrollPane listScrollPane = new JScrollPane( imgJTable );
			listScrollPane.setPreferredSize( new Dimension( 320, 240 ) );
			listScrollPane.setMinimumSize( new Dimension( 120, 240 ) );
			mainLeftPanel.add( listScrollPane, BorderLayout.CENTER );
			// left side bottom
			JPanel mainLeftSouthPanel = new JPanel();
			mainLeftSouthPanel.setLayout( new FlowLayout( FlowLayout.LEFT, 8, 2) );
				allCheck = new JCheckBox( allCheckLabel, true );
				allCheck.addActionListener( this );
				allCheck.addKeyListener( IJ.getInstance() );
				allCheck.addItemListener( this );
				mainLeftSouthPanel.add( allCheck );
				syncCheck = new JCheckBox( syncCheckLabel, true );
				syncCheck.addActionListener( this );
				syncCheck.addKeyListener( IJ.getInstance() );
				syncCheck.addItemListener( this );
				mainLeftSouthPanel.add( syncCheck );
	  		mainLeftPanel.add( mainLeftSouthPanel, BorderLayout.SOUTH );
		add( mainLeftPanel, BorderLayout.CENTER );

		// right side
//		JPanel mainRightPanel = new JPanel();
//		JPanel mainTopPanel = new JPanel();

//		buttonTabPane = new JPanel();
//		dropTargetTab = new ArrayList<DropTarget>();
//		for( int i=0; i<buttons.length; i++ ) {
			int i=0;
			buttonPanel = new JPanel();
			buttonPanel.setLayout( new BoxLayout( buttonPanel, BoxLayout.Y_AXIS ) );
			for( int j=1; j<buttons[i].length; j++ ) {
				JLabel dummy1 = new JLabel( "" );
				dummy1.setMaximumSize( new Dimension( Short.MAX_VALUE, 4 ) );
				dummy1.setPreferredSize( new Dimension( 180, 4 ) );
				buttonPanel.add( dummy1 );
				String funcChar = buttons[i][j].substring(0,1);
				if( funcChar.equals( "*" ) ) {
					JLabel dummy = new JLabel( buttons[i][j].substring(1) );
					dummy.setVerticalAlignment( SwingConstants.BOTTOM );
					dummy.setMaximumSize( new Dimension( Short.MAX_VALUE, 16 ) );
					buttonPanel.add( dummy );
				} else {
					JButton b = new JButton( buttons[i][j] );
					b.setMaximumSize( new Dimension( Short.MAX_VALUE, b.getMaximumSize().height ) );
					b.setMargin( new Insets(4, 8, 4, 8) );
					b.setToolTipText( tooltips[i][j] );
					b.addActionListener( this );
					b.addKeyListener( IJ.getInstance() );
					buttonPanel.add( b );
					if( buttons[i][j].equals( openStackLabel ) ) {
			 			openStackBtn = new DropTarget( b, this );
			 		} else if( buttons[i][j].equals( openSliceLabel ) ) {
			 			openSliceBtn = new DropTarget( b, this );
			 		} else if ( buttons[i][j].equals( lutMenuLabel ) ) {
			 	 		lutMenuBtn = b;
			 			// add LUT popup menu (same as 'Image'->'Lookup Tables') ...		 	 		
			 			lutMenu = new FilePopupMenu( lutMenuLabel, IJ.getDirectory("luts"), ".lut", this );
			 	 		// AND add recently used buttons
			 	 		recentLut = new RecentlyUsed( recentLutKey, recentLutCount );
			 			recentLut.addButtons( buttonPanel, this );
					}
		 		}
			}

//			buttonTabPane.addTab( buttons[i][0], buttonPanel );
/*			if( IJ.isJava16() ) {
				JLabel l = new JLabel( buttons[i][0] );
				buttonTabPane.setTabComponentAt( i, l );
				dropTargetTab.add( new DropTarget( l, this ) );
			}*/
//		}
		add( buttonPanel, BorderLayout.EAST );

		pack();
		GUI.center(this);
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int x = (int) Prefs.get( locx, screen.width - instance.getWidth() - 16 );
		int y = (int) Prefs.get( locy, 16 );
		instance.setLocation( x, y );
		setVisible(true);
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
			Point ploc = buttonPanel.getLocation();
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
			imgList = null;
		}
	}
	public void itemStateChanged( ItemEvent e ) {
//		IJ.log( "itemStateChanged:"+e.getSource() );
		if( e.getSource().equals( syncCheck ) ) {
			changeSyncCheck( syncCheck.getSelectedObjects()!=null );
		} else if ( e.getSource().equals( allCheck ) ) {
			allCheck.setOpaque( false );
			changeAllCheck( allCheck.getSelectedObjects()!=null );
		} else {
			// other item changed 
			int state = e.getStateChange();
//			ImagePlus imp = imgList.idToImagePlus( Integer.valueOf( e.getItem().toString() ) );
//			IJ.log( "ItemStateChanged:"+e.getSource().toString()+"->"+e.getItem()+", "+state );
//			if( imp==null )
//				return;
//			if( state==1 )
//				imp.getWindow().toFront();
		}
	}
	public void changeSyncCheck( boolean c ) {
		syncRoi( syncCheck.getSelectedObjects()!=null );	
	}
	public void changeAllCheck( boolean c ) {
		syncRoi( false );
		imgList.checkAllRows( c ); //set all checkbox
		if( c )
			if( syncCheck.getSelectedObjects()!=null )
				syncRoi( true );
		this.repaint();	
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
		/*		instance.toFront();
		for( int i=0; i<dropTargetTab.size(); i++ )
			if( dtde.getSource().equals( dropTargetTab.get(i) ) )
				buttonTabPane.setSelectedIndex( i );*/
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
					if( dtde.getSource().equals( openStackBtnL ) )
						new ImsVIEWRunner( this, "Drop:Open as Stack", list );
					if( dtde.getSource().equals( openSliceBtnL ) )
						new ImsVIEWRunner( this, "Drop:Open separately", list );
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
			IJ.log( "Failed to get dropped file: " + dtde );
			for (int i = 0; i < flavors.length; i++)
				IJ.log( "  Possible flavor: "	+ flavors[i].getMimeType());
			dtde.rejectDrop();
		} catch (Exception e) {
			e.printStackTrace();
			dtde.rejectDrop();
		}
	}
	public void dropActionChanged( DropTargetDragEvent dtde ) { }
	public void valueChanged( ListSelectionEvent e ) {
		if( e.getSource().equals( imgJTable.getSelectionModel() ) ) {
			// ImageList Table row has selected.
			int selected = imgJTable.getSelectedRow();
//			IJ.log( "valueChanged: imgJTable.getSelectedRow"+selected );
			if( selected>=0 ) {
				ImagePlus imp = imgList.rowToImagePlus( selected );
				if( imp!=null )
					imp.getWindow().toFront();
			}
		}
	}
	public void tableChanged( TableModelEvent e ) {
		if( e.getType()==TableModelEvent.UPDATE ) {
			// Table cell has edited.
			int row = e.getFirstRow();
			if( row!=e.getLastRow() ) // only single row selection
				return;
//			IJ.log( "tableChanged:" );
			imgList.checkState();
			switch( imgList.countOfSyncCheck() ) {
				case -1:
					allCheck.setForeground( Color.GRAY );
					break;
				case 0:
					allCheck.setForeground( Color.BLACK );
					allCheck.setSelected( false );
					break;
				case 1:
					allCheck.setForeground( Color.BLACK );
					allCheck.setSelected( true );
					break;
				default:
			}
			ImagePlus changedImp = imgList.rowToImagePlus( row );
			switch( e.getColumn() ){
			case ImageList.COLUMN_SYNCROI: // changed CheckBox row
				if( syncroi!=null ) {
//					IJ.log( "Column checked" );
					syncroi.setListener( imgList.getCheckedImp() );
				}
				break;
			case ImageList.COLUMN_TITLE: // changed Title row
				changedImp.setTitle( imgList.getTitleAt( row ) );
				break;
			}
		} else if( e.getType()==TableModelEvent.INSERT ) {
//			IJ.log( "inserted row " );
//			imgList.checkState();
			if( syncroi!=null && imgList.getCheckedImp().size()>0 ) {
				syncroi.setListener( imgList.getCheckedImp() );
				syncroi.notifyListeners( imgList.getCheckedImp().get(0) );
//				IJ.log( "notified as "+imgList.getCheckedImp().get(0));
			}
		}
	}

	void syncRoi( boolean check ) {
		// CheckBox 'Sync ROI' 
		if( check ) { // Checked
			if( syncroi==null ) {
//				IJ.log( "SyncROI checked" );
				syncroi = new RoiSynchronizer( imgList.getCheckedImp() );
			}
			ImagePlus imp = WindowManager.getCurrentImage();
			if( imp!=null ) {
				syncroi.notifyListeners( imp );
			}
		} else if( syncroi!=null ) { // Unchecked
//			IJ.log( "SyncROI unchecked" );
			syncroi.removeAllListeners();
			syncroi = null;
		}
	}
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
				ijRun("Plot Profile");
			} else if( command.equals("Inspect") ) {
				new PixelInspector_();
				if( ImsVIEW_.syncroi!=null )
					ImsVIEW_.syncroi.setListener( ImsVIEW_.imgList.getCheckedImp() );
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
			}
		} 
		if ( command.equals("Differential") ) {
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
		} else if ( command.equals("log") ) {
			new MathInFloat( imp );
		} else if ( command.equals( ImsVIEW_.openStackLabel ) ) {
			String defaultdir;
			if( ( defaultdir = Prefs.get( DIR_OPENSTACK, "---" ) ) != "---" )
				DirectoryChooser.setDefaultDirectory( defaultdir );
			DirectoryChooser od = new DirectoryChooser( "Open directory for '32bit-Real Raw' or 'HDF v3.2' ..." );
			if( od!=null && od.getDirectory()!=null && od.getDirectory()!="" ) {
				new ImsOpener( od.getDirectory(), false, true ).open();
				Prefs.set( DIR_OPENSTACK, od.getDirectory() );
				setBCWindow();
			}

		} else if( command.equals("Drop:Open as Stack") ){
			new ImsOpener( target, false, true ).open();
			setBCWindow();

		} else if( command.equals( ImsVIEW_.openSliceLabel ) ){
			String defaultdir;
			if( ( defaultdir = Prefs.get( DIR_OPENSLICE, "---" ) ) != "---" )
				DirectoryChooser.setDefaultDirectory( defaultdir );
			OpenDialog od = new OpenDialog( "Open file '32bit-Real Raw' or 'HDF v3.2' ...", null );			
			if( od!=null && od.getDirectory()!=null && od.getDirectory()!="" ) {
				new ImsOpener( od.getDirectory()+od.getFileName(), false, false ).open();
				setBCWindow();
				Prefs.set( DIR_OPENSLICE, od.getDirectory() );
			}

		} else if( command.equals("Drop:Open separately") ){
			new ImsOpener( target, false, false ).open();
			setBCWindow();

		} else if( command.equals("Open HDF v3.2") ) {
			OpenDialog od = new OpenDialog( "Open HDF v3.2 file...", null );
			new HDF32Opener( od.getDirectory()+od.getFileName() ).open(true);
			setBCWindow();

		} else if( command.equals("Drop:Open HDF v3.2") ) {
			for( int i=0; i<target.size(); i++ )
				if( target.get(i).isFile() )
					new HDF32Opener( target.get(i).toString() ).open(true);
			setBCWindow();

		} else if( command.equals("Save checked images as TIFF") ) {	/* 2019-04-2 kabe */
			ArrayList<ImagePlus> checkedImg = ImsVIEW_.imgList.getCheckedImp();
			Iterator<ImagePlus> i = checkedImg.iterator();
			while( i.hasNext() ){
				ImagePlus img = i.next();
				// Get Name
				String name = img.getTitle();
				// Get Directory Path
				Path savepath = null;
				FileInfo fi = img.getOriginalFileInfo();
				if( fi == null )
					fi = img.getFileInfo();
				if( fi != null )
					savepath = Paths.get( fi.directory );
				if( savepath.toString().length()>0 && Files.exists( savepath )) {
					// OK, directory is specified
					if( imp.isStack() )
						savepath = savepath.getParent();
				} else {
					// Ask for the directory to save
			  		DirectoryChooser dc = new DirectoryChooser( "Where do you want to save '"+name+"'?" );
					savepath = Paths.get( dc.getDirectory() );
				} 
				// Rename if filename duplicated
				int renum = 0;
				String r = "";
				while( Files.exists( Paths.get( savepath + "/" + name + r + ".tiff") ) )
					r = "(" + renum++ + ")";
				// Set FileInfo
				fi.directory = savepath.toString();
				img.setFileInfo(fi);
				new FileSaver( img ).saveAsTiff( savepath + "/" + name + r + ".tiff" );
				IJ.log( "Saved: " + name + " as " + savepath + "/" + name + r + ".tiff" );
			}
			
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
		} else if ( command.equals("ROI Manager") ) {
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
