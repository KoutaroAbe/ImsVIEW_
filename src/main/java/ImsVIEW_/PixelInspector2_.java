import java.awt.*;
import java.awt.event.*;

import ij.*;
import ij.gui.*;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.PlugInFrame;
import ij.plugin.frame.Recorder;
//import ij.plugin.frame.Recorder;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.datatransfer.*;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import ij.gui.Roi;
import ij.IJ;
import ij.util.Java2;

/** ******************************************************************************************************
 * Display each pixel values.
 * @author Koutaro Abe ( kabe@cris.hokudai.ac.jp )
 * @version 4-Jul-2013
 */
@SuppressWarnings("serial")
public class PixelInspector2_ extends PlugInFrame
	implements ActionListener, MouseMotionListener, ImageListener, KeyListener, ItemListener {

	int DEFAULT_WIDTH=800, DEFAULT_HEIGHT=600;
	public static final String TITLE_PREFIX = "PI:";
	public static final String LOC_KEY = "inspect.loc";
	public static final String WIDTH_KEY = "inspect.width";
	public static final String HEIGHT_KEY = "inspect.height";
	public static final String LOG_LOC_KEY = "log.loc";
	public static final String DEBUG_LOC_KEY = "debug.loc";
	public static final String FONT_SIZE_KEY = "inspect.font.size";
	public static final String FONT_ANTIALIAS_KEY= "inspect.font.anti";
	public static final String[] DIGIT_KEY = {
		"inspect.float.digit",	// 0:Float
		"inspect.short.digit",	// 1:Short
		"inspect.byte.digit",	// 2:Byte
		"inspect.other.digit"	// 3:Other
	};
	protected static final int OPENED=0, CLOSED=1, UPDATED=2;
	// Source Image
	ImagePlus sourceImp;
	ImageProcessor sourceIp;
    ImageCanvas sourceCanvas;
	/** Selection area ( target image's coordinate system j */
	Selection selectionArea;
    // My Components
    PixelInspector2_ PlugInFrame;
    InspectorPanel panel;
    InspectorCanvas canvas;
	CellFormat cell;
	/** Window size */
	int width, height;
	/** Display area i in srcImp coordinate j */
	Rectangle displayArea;
    CheckboxMenuItem antialiased;
    // My listeners
	private Vector listeners = new Vector();
	/** Select Operation: TRUE=lock, FALSE=allow other */
	private boolean lock = false;
	private final AtomicBoolean painting = new AtomicBoolean(true);
 
	/**
	Open a new window.
	@param imp = ImagePlus
	@param width, height = size of the window
	*/
	public PixelInspector2_( String title ){
		super( "" );
		// find ImagePlus
		int[] windowId = WindowManager.getIDList();
		if( windowId!=null && windowId.length>0 )
			for( int i: windowId ) {
				ImagePlus imp = WindowManager.getImage( i );
				if( imp!=null && imp.getTitle().equals(title) )
					create( imp );
			}
		else
			IJ.noImage();
	}
	public PixelInspector2_( ImagePlus imp ) {
		super( TITLE_PREFIX+imp.getTitle() );
		if( imp!=null )
			create( imp );
		else
			IJ.noImage();
	}
	public PixelInspector2_() { this( WindowManager.getCurrentImage() ); }

	public void create( ImagePlus imp ) {
		sourceImp = imp;
		sourceIp = imp.getProcessor();
        sourceCanvas = imp.getWindow().getCanvas();
		// Check duplication 
		for( Frame f: WindowManager.getNonImageWindows() ) {
			IJ.log( "NonImageWin:"+f.getTitle() );
			if( f instanceof PixelInspector2_ && ((PixelInspector2_)f).sourceImp.equals( imp ) ){	// already opened?
				dispose();
				f.toFront();
				return;
			}
		}
		// Construction
//        setTitle( TITLE_PREFIX+imp.getTitle() );
		Point loc = Prefs.getLocation(LOC_KEY);
		if( loc!=null ) {
			width = (int)Prefs.get( WIDTH_KEY, DEFAULT_WIDTH );
			height = (int)Prefs.get( HEIGHT_KEY, DEFAULT_HEIGHT );
			setLocation(loc);
		} else {
			width = DEFAULT_WIDTH;
			height = DEFAULT_HEIGHT;
		}
		setSize( width, height );
		synchronized( painting ){
			panel = new InspectorPanel();
			add("Center", panel);
			addMenuBar();
			GUI.center(this);
			panel.repaint();
			setVisible( true );
			show();
			while( painting.get()==true ) { ; }
		}
		if( SyncManager.isExists() )
			SyncManager.addRow(this);
		notifyListeners( OPENED );
        addImageListeners();
		WindowManager.addWindow(this);
		setRoi( imp.getRoi() );
	}

	public void close() {
		notifyListeners( CLOSED );
		super.close();
	}
	/** Get Source ImagePlus */
	public ImagePlus getSource() { return sourceImp; }
    /** Get my ROI */
    public Roi getRoi() {
		return ( selectionArea==null ) ? null : new Roi( selectionArea.getBounds() );
    }
    /** Set my ROI and the last point that is dragging with mouse on source image. */
    public void setRoi( Roi roi, int x, int y ){
//		IJ.log( "setRoi: selecting:"+lock );
    	if( lock==true || roi==null )	// ignore override Roi when locked.
    		return;
//		if( roi.getType()!=Roi.RECTANGLE ) /** todo */
		if( selectionArea==null )
			selectionArea=new Selection( roi.getBounds() );
		Point o=getOppositeCorner( roi.getBounds(), x, y );
		if( o==null ){	// Probably, Roi is being moved on source image by grabbing some point.
			selectionArea.setBounds( roi.getBounds() );
			canvas.setCenter( new Point( x, y ) );
		} else {	// or, is being extended.
			selectionArea.setStart( o );
			selectionArea.setEnd( x, y );
    		canvas.setCenter( selectionArea.getEndPoint() );
		}
    }
    /** Set my ROI */
    public void setRoi( Roi roi ) {
    	if( lock==true )
    		return;
    	if( roi==null ){
    		selectionArea=null;
    	} else {
    		if( selectionArea==null )
    			selectionArea=new Selection( roi.getBounds() );
    		else
    			selectionArea.setBounds( roi.getBounds() );
    		IJ.log("setRoi:canvas"+canvas);
    		canvas.setCenter( selectionArea.getEndPoint() );
    	}
    }
	/** Get opposite corner of the selecting rectangle */
	Point getOppositeCorner( Rectangle r, int x, int y ){
		if( r==null )
			return null;
		if( x==r.x && y==r.y ){ // p is left-top
			return new Point( r.x+r.width-1, r.y+r.height-1 );
		} else if( x==r.x && y==r.y+r.height ){ // left-bottom
			return new Point( r.x+r.width-1, r.y );
		} else if( x==r.x+r.width && y==r.y ){ // right-top
			return new Point( r.x, r.y+r.height-1 );
		} else if( x==r.x+r.width && y==r.y+r.height ){ // right-bottom
			return new Point( r.x, r.y );
		}
//		IJ.log( "************ opposite:"+r+", ("+x+","+y+") has no result." );
		return null;
	}

	/** Frame constructor - menu bar */
	void addMenuBar() {
		MenuBar mb = new MenuBar();
		if (Menus.getFontSize()!=0)
			mb.setFont(Menus.getFont());
		Menu m = new Menu("File");
		m.add(new MenuItem("Close", new MenuShortcut(KeyEvent.VK_W)));
		m.addActionListener(this);
		mb.add(m);
		m = new Menu("Edit");
		m.add(new MenuItem("Copy", new MenuShortcut(KeyEvent.VK_C)));
		m.add(new MenuItem("Select All", new MenuShortcut(KeyEvent.VK_A)));
		m.addActionListener(this);
		mb.add(m);
		m = new Menu("View");
		m.add(new MenuItem("Increase Digit", new MenuShortcut(KeyEvent.VK_0)));
		m.add(new MenuItem("Decrease Digit", new MenuShortcut(KeyEvent.VK_9)));
		m.addSeparator();
		m.add(new MenuItem("Make Text Smaller"));
		m.add(new MenuItem("Make Text Larger"));
		m.addSeparator();
		antialiased = new CheckboxMenuItem( "Antialiased", Prefs.get( FONT_ANTIALIAS_KEY, IJ.isMacOSX() ? true:false ) );
		antialiased.addItemListener(this);
		m.add(antialiased);
		m.add(new MenuItem("Save Settings"));
		m.addActionListener(this);
		mb.add(m);
		setMenuBar(mb);
	}

	/** Add my listener */
	public void addEventListener(PixelInspectorListener listener) {
		listeners.addElement(listener);
	}
	/** Remove my listener */
	public void removeEventListener(PixelInspectorListener listener) {
		listeners.removeElement(listener);
	}
	/** Notify open, close, updates to my listeners */
	protected void notifyListeners(int id) {
		synchronized (listeners) {
			for (int i=0; i<listeners.size(); i++) {
				PixelInspectorListener listener = (PixelInspectorListener)listeners.elementAt(i);
				switch (id) {
					case OPENED:
						listener.piOpened(this);
						break;
					case CLOSED:
						listener.piClosed(this);
						break;
					case UPDATED: 
						listener.piUpdated(this);
						break;
				}
			}
		}
	}
	
	/** Frame constructor - listen to events on the source image. */
	private void addImageListeners() {
        ImagePlus.addImageListener(this);
        ImageWindow win = sourceImp.getWindow();
        if ( win==null ) close();
        sourceCanvas = win.getCanvas();
        sourceCanvas.addMouseMotionListener(this);
        sourceCanvas.addKeyListener(this);
    }
    private void removeImageListeners() {
        ImagePlus.removeImageListener(this);
        sourceCanvas.removeMouseMotionListener(this);
        sourceCanvas.removeKeyListener(this);
    }
 
	/** Commands on my menu */
	public void actionPerformed( ActionEvent e ) {
		String cmd = e.getActionCommand();
		if ( cmd.equals("Increase Digit") )
			cell.changeDigit( +1 );
		else if ( cmd.equals("Decrease Digit") )
			cell.changeDigit( -1 );
		else if ( cmd.equals("Make Text Larger") )
			cell.changeFontSize( +1 );
		else if ( cmd.equals("Make Text Smaller") )
			cell.changeFontSize( -1 );
		else if ( cmd.equals("Save Settings") )
			cell.saveSettings();
		else 
			panel.doCommand( cmd );
	}
	/** WindowEvent on my frame */
	public void processWindowEvent( WindowEvent e ) {
		super.processWindowEvent( e );
		int id = e.getID();
		if( id==WindowEvent.WINDOW_CLOSING )
			close();	
		else if( id==WindowEvent.WINDOW_ACTIVATED )
			WindowManager.setWindow(this);
	}

	// ItemListener (myself)
	public void itemStateChanged( ItemEvent e ){}
	// FocusListener (myself)
	public void focusGained( FocusEvent e ){ WindowManager.setWindow(this); }
	public void focusLost( FocusEvent e ) {}
	// ImageListener (IJ)
	public void imageOpened( ImagePlus imp ) {}
	public void imageUpdated( ImagePlus imp ) {
		if( imp==sourceImp )
			setRoi( imp.getRoi() );
	}
	public void imageClosed( ImagePlus imp ) {
		if( imp==sourceImp ) {
			removeImageListeners();
			close();
		}
	}
	// MouseMotionListener (on the source image)
	public void mouseDragged(MouseEvent e){ setRoi( sourceImp.getRoi(), e.getX(), e.getY() ); }
	public void mouseMoved(MouseEvent arg0) { }
	// KeyListener (source image)
	public void keyPressed(KeyEvent arg0){ setRoi( sourceImp.getRoi() ); }
	public void keyReleased(KeyEvent arg0) { setRoi( sourceImp.getRoi() ); }
	public void keyTyped(KeyEvent arg0) { }

	
	/** ******************************************************************************************************
	* Display pixel values table.
	* based on ij.text.TextWindow.java
	*/
	class InspectorPanel extends Panel
		implements AdjustmentListener, ActionListener, ClipboardOwner,
			MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {
	
		// Components on this panel
		PopupMenu pm;
		Scrollbar sbHoriz,sbVert;
		// InspectorCanvas canvas; // declared in PixelInspector2_
	 
		/** Selection mode for mouse dragging */
		private int selectMode=-1;
		static final int DRAG_NORM=0;
		static final int DRAG_ROW=1;
		static final int DRAG_COL=2;
		static final int DOUBLE_CLICK_THRESHOLD = 650;
	
		KeyListener keyListener;
		private long mouseDownTime;
	    String filePath;
	
		/** Constructs a new TextPanel. */
		public InspectorPanel() {
//			IJ.log( "InspectorPanel.init:" );
			setLayout( new BorderLayout() );
			canvas = new InspectorCanvas();
			canvas.addMouseListener( this );
			canvas.addMouseMotionListener( this );
			canvas.addKeyListener( this );
			canvas.addMouseWheelListener( this );
			sbHoriz = new Scrollbar( Scrollbar.HORIZONTAL );
			sbHoriz.addAdjustmentListener( this );
			sbHoriz.setFocusable( false ); // prevents scroll bar from blinking on Windows
			sbVert = new Scrollbar( Scrollbar.VERTICAL );
			sbVert.addAdjustmentListener( this );
			sbVert.setFocusable( false );
			ImageJ ij = IJ.getInstance();
			if (ij!=null) {
				sbHoriz.addKeyListener(ij); /** ToDo : not ij but this? */
				sbVert.addKeyListener(ij);
			}
			add("Center",canvas);
			add("South", sbHoriz);
			add("East", sbVert);
			addPopupMenu();
			doLayout();
		}
	  
		void addPopupMenu() {
			pm=new PopupMenu();
			addPopupItem("Copy");
			addPopupItem("Select All");
			add(pm);
		}
		void addPopupItem(String s) {
			MenuItem mi=new MenuItem(s);
			mi.addActionListener(this);
			pm.add(mi);
		}

	    /** For better performance, open double-clicked files on 
	    	separate thread instead of on event dispatch thread. */
	    public void run() {
//	        if (filePath!=null) IJ.open(filePath); // ???
	    }
	
		/* ---- Event Listeners are below ----*/
		// MouseMotionListener
	    public void mousePressed( MouseEvent e ) {
//	    	IJ.log( "panel.mousePressed: locking="+lock );
			int x=e.getX(), y=e.getY();
			if( e.isPopupTrigger() || e.isMetaDown())
				pm.show( e.getComponent(), x, y );
	 		else if( e.isShiftDown() ){
	 			lock=true;
				setSelectionEnd( x, y );
	 		} else {
 				// Double click?
	 			boolean doubleClick = System.currentTimeMillis()-mouseDownTime<=DOUBLE_CLICK_THRESHOLD;
 				mouseDownTime = System.currentTimeMillis();	// for next
 				if( doubleClick ) {
 					canvas.setCenter( selectionArea.getEndPoint() );
 				} else {	// Click, and holding
		 			lock=true;
		 			setSelectionStart( x, y );
 				}
	 		}
//	    	IJ.log( "panel.mousePressed: >>>"+lock );
		}
		public void mouseExited ( MouseEvent e ){ lock = false; }
		public void mouseMoved ( MouseEvent e ){}
		public void mouseDragged ( MouseEvent e ){
//	    	IJ.log("panel.mouseDragged: "+lock );
			if( e.isPopupTrigger() || e.isMetaDown() )
				return;
			int x=e.getX(), y=e.getY();
			lock = true;
			// Scroll(=move display.(x,y) ) when the pointer is out of table.
			if( x>displayArea.width*cell.width+cell.width )
				displayArea.x += (x-displayArea.width*cell.width-cell.width)/4;
			else if( x<cell.width )
				displayArea.x += (x-cell.width)/4;
			if( y>displayArea.height*cell.height+cell.height )
				displayArea.y += (y-displayArea.height*cell.height-cell.height)/4;
			else if( y<cell.height )
				displayArea.y += (y-cell.height)/4;
			if( selectionArea.x1!=canvas.xToCol(x) || selectionArea.y1!=canvas.yToRow(y) )
				setSelectionEnd( x, y );
//	    	IJ.log( "panel.mouseDragged: >>>"+lock );
		}
	 	public void mouseReleased ( MouseEvent e ){ lock = false; }
		public void mouseClicked ( MouseEvent e ){}
		public void mouseEntered ( MouseEvent e ){}
		public void mouseWheelMoved( MouseWheelEvent e ){
			synchronized( this ){
				cell.changeFontSize( e.getWheelRotation() );
//				canvas.setCenter( new Point( canvas.xToCol( e.getX() ), canvas.yToRow( e.getY() ) ) );	// Zoom cursor
				canvas.setCenter( canvas.getCenter() );	// Zoom center of the window
			}
		}
	
		// Unused keyPressed and keyTyped events will be passed to 'listener'.
		public void addKeyListener( KeyListener listener ){ keyListener=listener; }
		public void keyPressed( KeyEvent e ){
//			IJ.log("panel.keyPressed:");
			int key = e.getKeyCode();
			if( key==KeyEvent.VK_UP || key==KeyEvent.VK_DOWN || key==KeyEvent.VK_LEFT || key==KeyEvent.VK_RIGHT ) {
				moveSelectionByKey( key, e.isShiftDown() );
			} else if( key==KeyEvent.VK_W )	// Ctrl-w to close the window.
				close();
			else if( key==KeyEvent.VK_ESCAPE )	// Esc to clear selection.
				resetSelection();
			if( keyListener!=null
					&& key!=KeyEvent.VK_C && key!=KeyEvent.VK_A
					&& key!=KeyEvent.VK_9 && key!=KeyEvent.VK_0 )		
				keyListener.keyPressed(e);
		}
		public void keyReleased (KeyEvent e) {
//	 		IJ.log( "keyReleased: >>>"+lock );
	 		if( lock==true && e.isShiftDown()!=true )
	 			lock=false;
//	 		IJ.log( "panel.keyReleased: "+lock );
		}
		public void keyTyped (KeyEvent e) {
			if( keyListener!=null )
				keyListener.keyTyped(e);
		}
		// AdjustmentListener
		public void adjustmentValueChanged (AdjustmentEvent e) {
			IJ.log("panel.adjustmentValueChanged:");
			displayArea.x = sbHoriz.getValue();
			displayArea.y = sbVert.getValue();
	 		canvas.repaint();
	 	}
		// ClipboardOwner
		public void lostOwnership ( Clipboard clip, Transferable cont ) {}	// prepare for trouble
	 
		public void actionPerformed (ActionEvent e) {
			String cmd=e.getActionCommand();
			doCommand(cmd);
		}
	
	 	void doCommand(String cmd) {
	 		if( cmd==null ) return;
			if( cmd.equals("Close") ) close();
			else if( cmd.equals("Copy") ) copySelection();
			else if( cmd.equals("Select All") ) selectAll();
	 		else if( cmd.equals("Options...") ) IJ.doCommand("Input/Output...");
		}
		/* ---- end of Event Listeners ----*/
	 	
		/** Get the cell value without coordinate check. */
		String getChars( int x, int y ) {
			return new java.text.DecimalFormat( cell.fString() ).format( sourceIp.getPixelValue( x, y ) );
		}
		/** Get the cell value with full length. */
		String getCharsFullDigit( int x, int y ) {
			return new java.text.DecimalFormat( cell.fullString() ).format( sourceIp.getPixelValue( x, y ) );
		}
	    /** Set source image my ROI, and notify to listeners. */
		public void notifyMyRoi( ) {
			sourceImp.setRoi( getRoi(), true );
			notifyListeners( UPDATED );
		}

		void moveSelectionByKey( int key, boolean shift ){
			if( shift ){ // extend Roi
				selectionArea.setEnd( movePointByKey( key, selectionArea.getEndPoint() ) );
				lock=true;
			} else if( selectionArea!=null ) {	// move Roi
				Point p0=movePointByKey( key, selectionArea.getStartPoint() );
				Point p1=movePointByKey( key, selectionArea.getEndPoint() );
				Selection s=new Selection( p0.x, p0.y, p1.x, p1.y );
				if( selectionArea.getWidth()==s.getWidth() && selectionArea.getHeight()==s.getHeight() )
					selectionArea=s;
				lock=false;
			}
			notifyMyRoi( );
			canvas.setCenter( selectionArea.getEndPoint() );
		}
		Point movePointByKey( int key, Point p ) {
			if( key==KeyEvent.VK_UP ) 
				if( p.y>0) p.y--;
			if( key==KeyEvent.VK_DOWN )
				if( p.y<sourceImp.getHeight()-1 ) p.y++;
			if( key==KeyEvent.VK_LEFT )
				if( p.x>0 ) p.x--;
			if( key==KeyEvent.VK_RIGHT )
				if( p.x<sourceImp.getWidth()-1 ) p.x++;
			return p;
		}
	
		/** Start selection. */
		void setSelectionStart( int x, int y ) {
//			IJ.log( "setSelectionStart: ("+canvas.xToCol(x)+","+canvas.yToRow(y)+")"  );
	      	if( x<cell.width && y<cell.height ) // click top-left cell
	      		selectAll();
	      	else if( x<cell.width ){ // click row number cell
	      		selectionArea = new Selection( 0, canvas.yToRow(y), sourceImp.getWidth(), canvas.yToRow(y) );
	      		selectMode = DRAG_ROW;
	      	} else if( y<cell.height ){ // click column number cell
	      		selectionArea = new Selection( canvas.xToCol(x), 0, canvas.xToCol(x), sourceImp.getHeight() );
	      		selectMode = DRAG_COL;
	      	} else { // click value cell
	      		selectionArea = new Selection( canvas.xToCol(x), canvas.yToRow(y), canvas.xToCol(x), canvas.yToRow(y) );
	      		selectMode = DRAG_NORM;
	      	}
			canvas.repaint();
			notifyMyRoi();
		}
	
		/** Extend selection area. */
		void setSelectionEnd( int x, int y) {
//			IJ.log( "setSelectionEnd: ("+canvas.xToCol(x)+","+canvas.yToRow(y)+")" );
	     	if( selectMode==DRAG_NORM ){
	     		selectionArea.setEnd( canvas.xToCol(x),  canvas.yToRow(y) );
	     	} else if( selectMode==DRAG_COL ) {
     			selectionArea.setEnd( canvas.xToCol(x), sourceImp.getHeight() );
	     	} else if( selectMode==DRAG_ROW ) {
    			selectionArea.setEnd( sourceImp.getWidth(), canvas.yToRow(y) );
			}
			canvas.repaint();
			notifyMyRoi();
		}
	
		/** Select all cells. */
		public void selectAll() {
			selectionArea=new Selection( 0, 0, sourceImp.getWidth(), sourceImp.getHeight() );
			notifyMyRoi();
			canvas.repaint();
		}
	
		/** Clear selection. */
		public void resetSelection() {
			selectionArea = null;
			selectMode = -1;
			canvas.repaint();
			notifyMyRoi();
		}
		
		/**
		Copy the current selection to the system clip board. 
		Returns the length of characters copied.
		*/
		public int copySelection() {
/*			if (Recorder.record && title.equals("Results"))
				Recorder.record("String.copyResults"); */
			if( selectionArea==null )
				selectAll();
			StringBuffer sb = new StringBuffer();
			sb.append( getTitle() );
			for( int x=selectionArea.x0; x<=selectionArea.x1; x++ )
				sb.append( "\t"+x );
			sb.append( '\n' );
			for( int y=selectionArea.y0; y<=selectionArea.y1; y++ ) {
				sb.append( ""+y );
				for( int x=selectionArea.x0; x<=selectionArea.x1; x++ ) {
					sb.append( "\t"+getCharsFullDigit( x, y ) );
				}
				sb.append( '\n' );
			}
			String s = new String(sb);
			Clipboard clip = getToolkit().getSystemClipboard();
			if (clip==null) return 0;
			StringSelection cont = new StringSelection(s);
			clip.setContents(cont,this);
			if (s.length()>0) {
				IJ.showStatus( "("+selectionArea.getWidth()+","+selectionArea.getHeight()+") pixels copied to clipboard");
				if (this.getParent() instanceof ImageJ)
					Analyzer.setUnsavedMeasurements(false);
			}
			return s.length();
		}
		
		void log( String s ){
			IJ.log(""+s+" Diplay("+displayArea.x+","+displayArea.y+")-("+displayArea.width+","+displayArea.height+")" );
		}
		void log(){
			log("");
		}
	}
	
	/** ******************************************************************************************************
	 * Rectangle with START point and END point.
	 */
	class Selection {
		/** START point */
		int x0, y0;
		/** END point */
		int x1, y1;
		Selection( int x0, int y0, int x1, int y1 ){ setBounds( x0, y0, x1, y1 ); }
		Selection( Rectangle r ){ this( r.x, r.y, r.x+r.width, r.x+r.height ); }
		/** Set start point */
		void setStart( int x, int y ){
			x0=x; y0=y;
		}
		void setStart( Point p ){ setStart( p.x, p.y ); }
		Point getStartPoint(){ return new Point( x0, y0 ); }
		void setEnd( int x, int y ){ x1=x; y1=y; }
		void setEnd( Point p ){ setEnd( p.x, p.y ); }
		Point getEndPoint(){ return new Point( x1, y1 ); }
		void setBounds( int x0, int y0, int x1, int y1 ){
			this.x0=x0;	this.y0=y0;
			this.x1=x1;	this.y1=y1;
		}
		/** Set Rectangle with conversion.
		 * Rectangle( 0, 0, 1, 1 ) ==> this( 0, 0, 0, 0 ) 
		 */
		void setBounds( Rectangle r ){ setBounds( r.x, r.y, r.x+r.width-1, r.y+r.height-1 ); }
		/** Get Rectangle with conversion.
		 *  this( 0, 0, 0, 0 ) ==> Rectangle( 0, 0, 1, 1 )
		 *  and the height and the width are always positive.
		 */
		Rectangle getBounds(){
			int x, y, width, height;
			if( x0<=x1 ){
				x=x0; width=x1-x0+1;
			} else {
				x=x1; width=x0-x1+1;
			}
			if( y0<=y1 ){
				y=y0; height=y1-y0+1;
			} else {
				y=y1; height=y0-y1+1;
			}
			return new Rectangle( x, y, width, height );
		}
		int getWidth(){ return ( x0<=x1 ) ? x1-x0+1 : x0-x1+1; }
		int getHeight(){ return ( y0<=y1 ) ? y1-y0+1 : y0-y1+1; }
		boolean contains( int pointX, int pointY ){ return getBounds().contains( pointX, pointY ); }
		boolean contains( Point p ){ return contains( p.x, p.y ); }
	}
	
	/** ******************************************************************************************************
	* Display pixel values table.
	*/
	class InspectorCanvas extends Canvas {
	
		Graphics gImage;
		Image iImage;

		// Text decoration
		static final int OTHER=0, HEADER=1, SELECT=2, BEGIN=3, END=4;
		final Color[] fg={ Color.black, Color.black, Color.white, Color.white, Color.white };
		final Color[] bg={ Color.white, Color.lightGray, Color.black, Color.gray, Color.gray };
		final Color[] rim={ null, null, Color.black, Color.red, Color.yellow };
			
		InspectorCanvas() {
//			IJ.log( "InspectorCanvas.init:" );
//			super();
			setSize( DEFAULT_WIDTH, DEFAULT_HEIGHT );
			displayArea=new Rectangle( 0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
			cell=new CellFormat();
		}
		
		@Override
		public void setBounds(int x, int y, int width, int height) {
	    	super.setBounds(x, y, width, height);
	    	iImage = null;
		}
	
		@Override
		public void paint(Graphics g) {
//			IJ.log( "panel.paint:" );
			painting.set( true );
			if( panel==null || g==null )
				return;
			Dimension canvasSize = getSize();
			if( canvasSize.width<=0 || canvasSize.height<=0 )
				return;
			if( iImage==null ){
				iImage=createImage( canvasSize.width, canvasSize.height );
				if (gImage!=null)
					gImage.dispose();
				gImage=iImage.getGraphics();
			}
			gImage.setFont( cell.getFont() );
			Java2.setAntialiasedText( gImage, cell.antialiased );
			FontMetrics m=gImage.getFontMetrics();
			// update cell size
			String[] s=cell.fString().split(";");
			cell.width=m.stringWidth( s[0] )+cell.marginWidth;
			cell.height=m.getHeight()+cell.marginHeight;
			gImage.setColor( Color.white );
			gImage.fillRect( 0, 0, canvasSize.width, canvasSize.height );
			
			updateScrollBar();
			int gx=0, gy=0, mode;
			int col=displayArea.x, row=displayArea.y;

			// Top-left cell
			drawCell( gx, gy , HEADER, "" );
			// Top row, X-axis cells
			gx += cell.width;
			while( col<displayArea.x+displayArea.width ) {
				drawCell( gx, gy, HEADER,  ""+col ); // Draw X-axis
				gx += cell.width;
				col++;
			}
			// 2nd row or later
			gy = cell.height;
			while( row<displayArea.y+displayArea.height ) {
				gx=0;
				drawCell( gx, gy, HEADER, ""+row ); // Y-axis cells
				gx+=cell.width;
				for( col=displayArea.x; col<(displayArea.x+displayArea.width); col++ ) {
					mode=OTHER;
					if( selectionArea!=null ){
						if( selectionArea.x0==col && selectionArea.y0==row )
							mode=BEGIN;
						else if( selectionArea.x1==col && selectionArea.y1==row )
							mode=END;
						else if( insideSelection( col, row ) )
							mode=SELECT;
					}
					drawCell( gx, gy, mode, getValue( col, row)  );	// Value cells
					gx+=cell.width;
				}
				gy+=cell.height;
				row++;
			}
			drawCrossbar();
			if( iImage!=null )
				g.drawImage( iImage, 0, 0, null );
			painting.set( false );
		}

	    /** Adjust display area and scroll bar */
		synchronized void updateScrollBar() {
			// Adjust horizontal
			displayArea.width=getSize().width/cell.width-1;
			if( displayArea.x<0 )
				displayArea.x=0;
			else if( sourceImp.getWidth()<=displayArea.x+displayArea.width )
				displayArea.x=sourceImp.getWidth()-displayArea.width;
			// Update horizontal scroll bar
			int maximum = sourceImp.getWidth()-displayArea.width;	// length of scroll bar
			int visible = displayArea.width;						// length of scroll handle
			if( visible<0 )
				visible=0;
			else if( maximum<visible )
				visible=maximum;
			panel.sbHoriz.setValues( displayArea.x, visible, 0, maximum );
//			IJ.log( "  Horizontal: value="+displayArea.x+", visible="+visible+", max="+maximum );
			// Adjust vertical
			displayArea.height=getSize().height/cell.height-1;
			if( displayArea.y<0 )
				displayArea.y=0;
			else if( sourceImp.getHeight()<=displayArea.y+displayArea.height )
				displayArea.y=sourceImp.getHeight()-displayArea.height;
			// Update vertical scroll bar
			maximum = sourceImp.getHeight()-displayArea.height;	// length of scroll bar
			visible = displayArea.height;						// length of scroll handle
			if( visible<0 )
				visible=0;
			else if( maximum<visible )
				visible=maximum;
			panel.sbVert.setValues( displayArea.y, visible, 0, maximum);
//			IJ.log( "  Vertical: value="+displayArea.y+", visible="+visible+", max="+maximum  );
		}
	
		/** Convert x position on this canvas to X position on source image */
		public int xToCol( int x ){ return displayArea.x+(x/cell.width)-1; }
		/** Convert Y position on this canvas to Y position on source image */
		public int yToRow( int y ){ return displayArea.y+(y/cell.height)-1; }
		/** Is (x,y) inside selection area? */
		boolean insideSelection( int x, int y ){ return selectionArea.getBounds().contains( x, y ); }
		String getValue( int x, int y ) {
			return new java.text.DecimalFormat( cell.fString() ).format( sourceIp.getPixelValue( x, y ) );
		}	
		/** Get center point of current display area */
		public Point getCenter(){
			return new Point( displayArea.x+displayArea.width/2, displayArea.y+displayArea.height/2 );
		}
		/** Set center point of display area */
		void setCenter( Point p ){
			if( p==null )
				return;
			displayArea.x=p.x-displayArea.width/2;
			displayArea.y=p.y-displayArea.height/2;
			repaint();
		}
		/** Draw a cell
		 * @param x, y is in canvas coordinate
		 */
		private void drawCell( int gx, int gy, int mode, String s ) {
			if( bg[mode]!=null ){
				gImage.setColor( bg[mode] );
				gImage.fillRect( gx, gy, cell.width-1, cell.height-1 );
			}
			if( fg[mode]!=null ){
				gImage.setColor( fg[mode] );
				gImage.drawString( s, gx+2, gy+cell.height-5 );
			}
			if( rim[mode]!=null ){
				gImage.setColor( rim[mode] );
				gImage.drawRect( gx, gy, cell.width-1, cell.height-1 );
			}
		}

		/** Mark center point */
		private void drawCrossbar(){
			Point center=getCenter();
			int col=center.x-displayArea.x+1, row=center.y-displayArea.y+1;
			gImage.setColor( Color.blue );
			gImage.drawRect( cell.width*col, cell.height*row, cell.width, cell.height );
			gImage.drawRect( cell.width*col-1, 0, cell.width, cell.height-2 );
			gImage.drawRect( 0, cell.height*row-1, cell.width-2, cell.height );
		}
	}

	/** ******************************************************************************************************
	 * Cell format strings, digits of number, size
	 */
	class CellFormat {
	    final int[] sizes = { 6, 7, 8, 9, 10, 11, 12, 13, 14, 16, 18, 20, 24, 36, 48, 60 };
		// cell digit
		final String[][] formString = {
			// 0:Float
			{	" 0.0E00;-0.0E00",
				" 0.00E00;-0.00E00",
				" 0.000E00;-0.000E00",
				" 0.0000E00;-0.0000E00",
				" 0.00000E00;-0.00000E00",
				" 0.000000E00;-0.000000E00",
				" 0.0000000E00;-0.0000000E00",
				" 0.00000000E00;-0.000-0000E00",
				" 0.000000000E00;-0.000000000E00",
				" 0.0000000000E00;-0.0000000000E00"
			},
			// 1:Short
			{	" 000;-000",
				" 0000;-0000",
				" 00000;-00000",
				" 000000;-000000",
				" 0000000;-0000000",
				" 00000000;-00000000",
				" 00000000;-00000000",
				" 00000000;-00000000",
				" 00000000;-00000000",
				" 00000000;-00000000"
			},
			// 2:Byte
			{	"000", "000", "000", "000", "000", "000", "000", "000", "000", "000" },
			// 3:Other
			{	" 000;-000",
				" 0000;-0000",
				" 00000;-00000",
				" 000000;-000000",
				" 0000000;-0000000",
				" 00000000;-00000000",
				" 00000000;-00000000",
				" 00000000;-00000000",
				" 00000000;-00000000",
				" 00000000;-00000000"
			},
		};
	    // Font attributes
	    String name="SanSerif";
	    int style=Font.PLAIN;
		int size;
		boolean antialiased;
		// Type of this source image
	    private int processer;
	    // Parameters
	    String[] fString;
		int digit;
		int marginWidth=12, marginHeight=2;
		int width=32, height=24;
		
		CellFormat(){
			ImageProcessor ip = sourceImp.getProcessor();
			if( ip instanceof FloatProcessor )		processer=0;
			else if( ip instanceof ShortProcessor )	processer=1;
			else if( ip instanceof ByteProcessor )	processer=2;
			else									processer=3;
			loadSettings();
			fString=formString[processer];
		}

		int getSize(){ return sizes[size]; }
		String fString() { return fString[digit]; }
		String fullString() { return fString[ fString.length-1 ]; }
		Font getFont(){ return new Font(name, style, getSize()); }
		void changeFontSize( int rotate ) {
			if( rotate>0 ) {
	        	if (size<sizes.length-1) size++;
			} else if( rotate<0 ) {
	            if (size>0) size--;
			}
			canvas.repaint();
	        IJ.showStatus( "Set font size: "+getSize()+" point" );
	    }
		void changeDigit( int d ) {
	        if( d>0 ){
	        	if( digit<fString.length-1 ) digit++;
	        } else if( d<0 ){
	        	if( digit>0 ) digit--;
	        }
			canvas.repaint();
	        IJ.showStatus( "Digits="+fString()+"." );
	    }

		void loadSettings(){
	 		size=(int) Prefs.get( FONT_SIZE_KEY, 5 );
			antialiased=(boolean) Prefs.get( FONT_ANTIALIAS_KEY,  IJ.isMacOSX() ? true : false );
	 		digit=(int) Prefs.get( DIGIT_KEY[processer], 8 );
		}
		void saveSettings(){
			Prefs.set( FONT_SIZE_KEY, size );
			Prefs.set( FONT_ANTIALIAS_KEY, antialiased );
			Prefs.set( DIGIT_KEY[processer], digit );
			IJ.showStatus( "Font settings saved (size="+getSize()+", antialiased="+antialiased+")" );
		}
	}

}


