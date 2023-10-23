import ij.CommandListener;
import ij.Executer;
import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.PlotWindow;
import ij.gui.HistogramWindow;
import ij.gui.Roi;
import ij.io.FileInfo;
import ij.plugin.frame.PlugInFrame;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Vector;

import javax.activation.ActivationDataFlavor;
import javax.activation.DataHandler;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;

/**
* Synchronize ROIs.
* @author Koutaro Abe ( kabe@cris.hokudai.ac.jp )
* @version 04-Jul-2013
*/
public class SyncManager extends PlugInFrame
	implements ListSelectionListener, CommandListener, ImageListener {
	private static Frame instance;
	private static SyncTableModel list;
	private static JTable table;
	public static Color activeBgColor=Color.pink;
	
	public SyncManager( boolean hidden ) {
		super("SyncManager");
		enableEvents(AWTEvent.WINDOW_EVENT_MASK);
		if( !isExists() ) {
			list = new SyncTableModel();
			addWindowListener( this );
			WindowManager.addWindow( this );
			ImagePlus.addImageListener(this);
			Executer.addCommandListener(this);
			if( !hidden )
				showWindow();
		} else {
			if( !hidden )
				WindowManager.toFront( this );
		}
	}
	public SyncManager() { this( false ); }

	void showWindow() {
		table = new JTable( list );
		table.getSelectionModel().addListSelectionListener( this );
		table.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
//		table.setTransferHandler( new TableRowTransferHandler() );
		table.setDropMode( DropMode.INSERT_ROWS );
		table.setDragEnabled(true);
		table.setAutoResizeMode( JTable.AUTO_RESIZE_OFF );
		// hide invisible column
		for( int i=0; i<list.getColumnCount(); i++ )
			if( !list.isVisibleColumn(i) ) 
				table.removeColumn( table.getColumn( SyncTableModel.columns[i] ) );
		if (IJ.isLinux())
			table.setBackground(Color.white);
		JPanel panel = new JPanel( new BorderLayout());
		JScrollPane listScrollPane = new JScrollPane( table );
		listScrollPane.setPreferredSize( new Dimension( 480, 240 ) );
		listScrollPane.setMinimumSize( new Dimension( 240, 240 ) );
		panel.add( listScrollPane, BorderLayout.CENTER );
		add( panel );
		pack();
		setVisible( true );
		show();
	}

	public static boolean isExists(){
		return (list!=null);
	}
	
	public void addRow( Object obj ){
		SyncWindow o=null;
		String d="";
		// Create SyncObject 'o'.
		if( obj instanceof PixelInspector2_ ){
			o=new SyncPixelInspector2( (PixelInspector2_)obj );
		} else if( obj instanceof ImagePlus ){
			ImagePlus imp=(ImagePlus)obj;
			if( imp.getWindow() instanceof PlotWindow ){
				o=new SyncPlotProfile( imp );
			} else if( imp.getWindow() instanceof HistogramWindow ){
				o=new SyncHistogram( imp );
			} else {
				o=new SyncImagePlus( imp );
				d=imp.getOriginalFileInfo().directory;
			}
		}
		// 該当無し or 既にWindowリストに存在する場合は中止
		if( o==null || list.indexOf( o )>=0 )
			return;
		// Set ROI
		Roi r=list.getCurrentRoi();
		if( r!=null )
			o.setMyRoi( r );
		// リスト内の他ウィンドウと同期
		for( int i=0; i<list.getRowCount(); i++ ){
			SyncWindow existsInList=list.windowAt( i );
			if( list.checkSrcAt( i ) )
				existsInList.addFollower( o );
			if( list.checkDstAt( i ) )
				o.addFollower( existsInList );
		}
		if( d=="" )
			d="(no file)";
		list.addRow( new Object[] { true, true, d, o.getTitle(), o, o.getParent() } );
//		IJ.log( " ++added "+newWindow );
	}

	public void removeRow( Object o ){
		if( o instanceof SyncWindow )
			list.removeRow( list.indexOf( (SyncWindow)o ) );
		else
			removeRow( list.parentToSyncWindow( o ) );
	}

	/** Clear list */
	public void clear(){
		list.setRowCount(0);
	}
	/** Refresh window list */
	public void reload() {
		int[] windowId = WindowManager.getIDList();
		if( windowId!=null && windowId.length>0 )
			for( int i: windowId ) {
				ImagePlus imp = WindowManager.getImage( i );
				if( imp!=null )
					addRow( imp );
			}
		Frame[] frame = WindowManager.getNonImageWindows();
		if( frame!=null && frame.length>0 )
			for( Frame f: frame )
				addRow( f );
	}
	
	// ListSelectionListener
	public void valueChanged(ListSelectionEvent e) {
		int row=e.getFirstIndex();
		IJ.log( "valueChanged:"+e );
		if( row==e.getLastIndex() )
			list.listListeners(row);
	}

	@Override
	public void close(){
		clear();
		setVisible(false);
		WindowManager.removeWindow(this);
		dispose();
	}

	// ImageListener
	public void imageOpened(ImagePlus imp) { IJ.log("SyncManager.imageOpened:"+imp ); addRow( imp ); }
	public void imageClosed(ImagePlus imp) { IJ.log("SyncManager.imageClosed:"+imp ); removeRow( imp ); }
	public void imageUpdated(ImagePlus imp) {}

	// CommandListener
	public String commandExecuting(String command) {

		IJ.log( "Ev:commandExecuting ("+command+") " );
		/*		Thread t=new Thread(){
			public void run(){
				try {
					sleep(2000);	// wait for command done...
					reload();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		};
		t.start();
*/
		return command;
	}

	
	/**
	 * List model for synchronized windows.
	 */
	public static class SyncTableModel extends DefaultTableModel {
		static int COL_CKSRC=0, COL_CKDST=1, COL_DIR=2, COL_NAME=3, COL_OBJ=4, COL_PARENT=5;
		static String[] columns = { "Src?", "Dst?", "Dir", "Name", "Window", "Parent" };
		static Object[] defaultValues = { false, false, "directory", "image name", new Object(), new Object() };
		static boolean[] visible = { true, true, true, true, false, false };
		static boolean[] editable = { true, false, false, true, false, false };
		
		SyncTableModel() {
			super( new Object[0][], columns );
		}

		public boolean isVisibleColumn( int index ) {
			return ( 0<=index && index<visible.length ) ? visible[index] : false;
		}

		@Override
		public boolean isCellEditable( int row, int column ){
			return ( 0<=column && column<editable.length) ? editable[column] : false;
		}

		@Override
		public Class<?> getColumnClass( int column ) {
			return defaultValues[column].getClass();
		}

		/* debug */
		public void listListeners( int row ){
			SyncWindow obj=(SyncWindow) getValueAt( row, COL_OBJ );
			IJ.log( "Listeners to "+obj.title+" is ..." );
			for( SyncWindow s: new Vector<SyncWindow>( obj.listFollowers() ) ){
				IJ.log( "  "+s.title );
			}
			IJ.log( "  ." );
		}

		/** Get index number of SyncObject in list */
		int indexOf( SyncWindow so ) {
			if( so==null )
				return -1;
			Object p=so.getParent();		
			for( int i=0; i<getRowCount(); i++ ){
				SyncWindow s=((SyncWindow)getValueAt( i, COL_OBJ ));
				if( p.equals( s.getParent() ) )
					return i; // found
			}
			return -1; // not found
		}
		
		SyncWindow windowAt( int i ){
			return (SyncWindow)getValueAt( i, COL_OBJ );
		}
		boolean checkSrcAt( int i ){
			return (Boolean) getValueAt( i, COL_CKSRC ) ? true : false ;
		}
		boolean checkDstAt( int i ){
			return (Boolean) getValueAt( i, COL_CKDST ) ? true : false ;
		}
		void setCheckSrcAt( int i, boolean b ){
			setValueAt( i, COL_CKSRC, b ? 1 : 0 );
		}
		void setCheckDstAt( int i, boolean b ){
			setValueAt( i, COL_CKDST, b ? 1 : 0 );
		}
		/** Get current ROI */
		Roi getCurrentRoi(){
			for( int i=0; i<getRowCount(); i++ ){
				if( (Boolean) getValueAt( i, COL_CKSRC ) )
					return windowAt( i ).getRoi();	// ROI of all windows should have been already synchronized.
			}
			return null; // not found
		}
		
		/** Return SyncObject in the list. */
		SyncWindow parentToSyncWindow( Object o ) {
			if( o==null )
				return null;
			for( int i=0; i<getRowCount(); i++ ){
				SyncWindow s=(SyncWindow)getValueAt( i, COL_OBJ );
				if( o.equals( s.getParent() ) )
					return s; // found
			}
			return null; // not found
		}
		


	}
	/** Interface for SyncObject */
	interface SyncWindowInterface {
		public Object getParent();
		public String getTitle();
		public void setMyRoi( Roi roi );
		public void addTrigger();
		public void removeTrigger();
		public Roi getRoi();
		public void addFollower( SyncWindow follower );
		public void removeFollower( SyncWindow follower );
		public void notifyRoi( Roi roi );
	}

	/**
	 * Wrapper class for several different types of windows to sync.
	 */
	class SyncWindow implements SyncWindowInterface, KeyListener, MouseListener, MouseMotionListener {
		Object parent=null;
		Color originalBgColor;
		Vector<SyncWindow> followers;
		String title;
		SyncWindow( Object obj ){
			followers = new Vector<SyncWindow>();
		}
		public Object getParent(){ return parent; }
		public String getTitle(){ return title; }
		/* As event receiver */
		public void setMyRoi(Roi roi){}
		/* As event source */
		public void addTrigger(){}
		public void removeTrigger(){}
		public Roi getRoi(){ return null; }
		public void addFollower( SyncWindow follower ) {
			if( follower!=null && !followers.contains( follower ) && follower.getParent()!=parent ){
				followers.addElement( follower );
			}
		}
		public void removeFollower( SyncWindow follower ) {
			if( followers.contains( follower ) )
				followers.removeElement( follower );
		}
		public Vector<SyncWindow> listFollowers() { return followers; }
		public void notifyRoi( Roi roi ) {
			synchronized (followers) {
				for (int i=0; i<followers.size(); i++)
					followers.elementAt(i).setMyRoi( roi );
			}
		}
		// MouseMotionListener
		public void mouseDragged(MouseEvent e){}
		public void mouseMoved(MouseEvent e){}
		// MouseListener
		public void mouseClicked(MouseEvent e){}
		public void mouseEntered(MouseEvent e){}
		public void mouseExited(MouseEvent e){}
		public void mousePressed(MouseEvent e){}
		public void mouseReleased(MouseEvent e){}
		// KeyListener
		public void keyPressed(KeyEvent e){}
		public void keyReleased(KeyEvent e){}
		public void keyTyped(KeyEvent e){}
	}

	/** For ImagePlus */
	class SyncImagePlus extends SyncWindow implements ImageListener {
		SyncImagePlus( ImagePlus obj ){
			super( obj );
			parent=obj;
			title=((ImagePlus) parent).getTitle();
			originalBgColor=((ImagePlus) parent).getWindow().getBackground();
			addTrigger();
		}
		@Override
		public void setMyRoi(Roi roi) { ((ImagePlus) parent).setRoi( roi ); }
		@Override
		public void addTrigger() {
			((ImagePlus) parent).getCanvas().addKeyListener(this);
			((ImagePlus) parent).getCanvas().addMouseListener(this);
			((ImagePlus) parent).getCanvas().addMouseMotionListener(this);
			((ImagePlus) parent).getWindow().setBackground( activeBgColor );
		}
		@Override
		public void removeTrigger() {
			((ImagePlus) parent).getCanvas().addKeyListener(this);
			((ImagePlus) parent).getCanvas().addMouseListener(this);
			((ImagePlus) parent).getCanvas().addMouseMotionListener(this);
			((ImagePlus) parent).getWindow().setBackground( originalBgColor );
		}
		@Override
		public Roi getRoi() { return ((ImagePlus) parent).getRoi(); }
		@Override
		public void mouseDragged(MouseEvent e) { notifyRoi( getRoi() ); }
		@Override
		public void mouseClicked(MouseEvent e) { notifyRoi( getRoi() ); }
		@Override
		public void keyPressed(KeyEvent e) { notifyRoi( getRoi() ); }
		@Override
		public void keyReleased(KeyEvent e) { notifyRoi( getRoi() ); }
		@Override
		public void keyTyped(KeyEvent e) { notifyRoi( getRoi() ); }
		public void imageOpened(ImagePlus imp) {}
		public void imageClosed(ImagePlus imp) { SyncManager.removeRow(this); }
		public void imageUpdated(ImagePlus imp) { notifyRoi( getRoi() ); }
	}

	/** For PixelInspector2 */
	class SyncPixelInspector2 extends SyncWindow implements PixelInspectorListener {
		ImagePlus source;
		SyncPixelInspector2( PixelInspector2_ obj ){
			super( obj );
			parent=obj;
			source=((PixelInspector2_) parent).getSource();
			title=((PixelInspector2_) parent).getTitle();
			originalBgColor=((PixelInspector2_) parent).getBackground();
			addTrigger();
		}
		@Override
		public void setMyRoi(Roi roi) { ((PixelInspector2_) parent).setRoi( roi ); }
		@Override
		public void addTrigger() {
			((PixelInspector2_) parent).addEventListener(this);
			((PixelInspector2_) parent).setBackground( activeBgColor );
		}
		@Override
		public void removeTrigger() {
			((PixelInspector2_) parent).removeEventListener(this);
			((PixelInspector2_) parent).setBackground( originalBgColor );
		}
		@Override
		public Roi getRoi() { return ((PixelInspector2_) parent).getRoi(); }
		@Override
		public void addFollower( SyncWindow follower ) {
			if( source==follower.getParent() )
				return;
			super.addFollower( follower );
		}
		// PixelInspectorEvent
		public void piOpened( PixelInspector2_ pi ) { }
		public void piClosed( PixelInspector2_ pi ) { SyncManager.removeRow(this); }
		public void piUpdated( PixelInspector2_ pi ){ notifyRoi( getRoi() ); }
	}

	/** For PlotProfile */
	class SyncPlotProfile extends SyncWindow {
		PlotWindow source;
		SyncPlotProfile( ImagePlus obj ){
			super( obj );
			parent=obj;
			source=((PlotWindow) ((ImagePlus) parent).getWindow() );
			title=((ImagePlus) parent).getTitle();
			originalBgColor=source.getBackground();
		}
		public void setMyRoi(Roi roi) { 
			source.mouseDragged( new MouseEvent( source, 0, 0, 0, 0, 0, 1, false) ); // DUMMY mouse event
		}
		public void addTrigger() {} // I don't notify anyone.
		public void removeTrigger() {}
		public void notifyRoi( Roi roi ) {}
	}
	
	/** For HistgramWindow */
	class SyncHistogram extends SyncWindow {
		HistogramWindow source;
		SyncHistogram( ImagePlus obj ){
			super( obj );
			parent=obj;
			source=((HistogramWindow) ((ImagePlus) parent).getWindow() );
			title=((ImagePlus) parent).getTitle();
			originalBgColor=source.getBackground();
		}
		@Override
		public void setMyRoi(Roi roi) { 
			source.mouseDragged( new MouseEvent( source, 0, 0, 0, 0, 0, 1, false) ); // DUMMY mouse event
		}
		@Override
		public void addTrigger() {} // I don't notify anyone.
		@Override
		public void removeTrigger() {}
		@Override
		public void notifyRoi( Roi roi ) {}
	}
}

/*
//Demo - BasicDnD (Drag and Drop and Data Transfer)>http://docs.oracle.com/javase/tutorial/uiswing/dnd/basicdemo.html
class TableRowTransferHandler extends TransferHandler {
	private final DataFlavor localObjectFlavor;
	private Object[] transferedObjects = null;

	public TableRowTransferHandler() {
		localObjectFlavor = new ActivationDataFlavor(Object[].class, DataFlavor.javaJVMLocalObjectMimeType, "Array of items");
	}
	@Override
	protected Transferable createTransferable(JComponent c) {
		JTable table = (JTable) c;
		DefaultTableModel model = (DefaultTableModel)table.getModel();
		ArrayList<Object> list = new ArrayList<Object>();
		for(int i: indices = table.getSelectedRows()) {
			list.add(model.getDataVector().elementAt(i));
		}
		transferedObjects = list.toArray();
		return new DataHandler(transferedObjects, localObjectFlavor.getMimeType());
	}
	@Override
	public boolean canImport(TransferSupport info) {
		JTable table = (JTable)info.getComponent();
		boolean isDropable = info.isDrop() && info.isDataFlavorSupported(localObjectFlavor);
		table.setCursor(isDropable?DragSource.DefaultMoveDrop:DragSource.DefaultMoveNoDrop);
		return isDropable;
	}
	@Override
	public int getSourceActions(JComponent c) {
		return MOVE; //TransferHandler.COPY_OR_MOVE;
	}
	@Override
	public boolean importData(TransferSupport info) {
		if(!canImport(info))
			return false;
		JTable target = (JTable)info.getComponent();
		JTable.DropLocation dl = (JTable.DropLocation)info.getDropLocation();
		DefaultTableModel model = (DefaultTableModel)target.getModel();
		int index = dl.getRow();
		//boolean insert = dl.isInsert();
		int max = model.getRowCount();
		if(index<0 || index>max)
			index = max;
		addIndex = index;
		try{
			Object[] values = (Object[])info.getTransferable().getTransferData(localObjectFlavor);
			addCount = values.length;
			for(int i=0;i<values.length;i++) {
				int idx = index++;
				model.insertRow(idx, (Vector)values[i]);
				target.getSelectionModel().addSelectionInterval(idx, idx);
			}
			return true;
		} catch(UnsupportedFlavorException ufe) {
			ufe.printStackTrace();
		} catch(IOException ioe) {
			ioe.printStackTrace();
		}
		return false;
	}
	@Override
	protected void exportDone(JComponent c, Transferable data, int action) {
		cleanup(c, action == MOVE);
	}
	private void cleanup(JComponent c, boolean remove) {
		if(remove && indices != null) {
			JTable source = (JTable)c;
			source.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			DefaultTableModel model  = (DefaultTableModel)source.getModel();
			if(addCount > 0) {
				for(int i=0;i<indices.length;i++) {
					if(indices[i]>=addIndex) {
						indices[i] += addCount;
					}
				}
			}
			for(int i=indices.length-1;i>=0;i--) {
				model.removeRow(indices[i]);
			}
		}
		indices  = null;
		addCount = 0;
		addIndex = -1;
	}
	private int[] indices = null;
	private int addIndex  = -1; //Location where items were added
	private int addCount  = 0;  //Number of items added.
}
*/
