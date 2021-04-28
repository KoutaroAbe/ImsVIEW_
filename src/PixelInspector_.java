import java.awt.*;
import java.awt.event.*;

import ij.*;
import ij.gui.*;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.Recorder;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.datatransfer.*;
import ij.gui.Roi;
import ij.IJ;
import ij.util.Java2;
/**
 * Display images in a numerical table.
 * @author kabe@cris.hokudai.ac.jp
 * @version 29-October-2010
 * ORIGINAL: ij.text.TextWindow
 */
@SuppressWarnings("serial")
public class PixelInspector_ extends Frame
	implements ActionListener, FocusListener, MouseMotionListener, ImageListener, KeyListener, ItemListener {

	int DEFAULT_WIDTH=800, DEFAULT_HEIGHT=600;
	public static final String LOC_KEY = "inspect.loc";
	public static final String WIDTH_KEY = "inspect.width";
	public static final String HEIGHT_KEY = "inspect.height";
	public static final String LOG_LOC_KEY = "log.loc";
	public static final String DEBUG_LOC_KEY = "debug.loc";
	static final String FONT_SIZE = "inspect.font.size";
	static final String FONT_ANTI= "inspect.font.anti";
	static final String[] DIGIT = {
		"inspect.float.digit",	// 0:Float
		"inspect.short.digit",	// 1:Short
		"inspect.byte.digit",	// 2:Byte
		"inspect.other.digit"	// 3:Other
	};
	// Source Image
	ImagePlus imp;
    ImageCanvas sourceCanvas;
    // this panel
    InspectorPanel iPanel;
    CheckboxMenuItem antialiased;
    // cell font size
    Font font;
    int[] sizes = {8, 9, 10, 11, 12, 13, 14, 16, 18, 20, 24, 36, 48, 60, 72};
	int fontSize = (int)Prefs.get(FONT_SIZE, 5);
	// cell digit
	String[][] cellformat = {
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
	int cellDigit;
	int cellMarginWidth=12, cellMarginHeight=2;
 
	/**
	Opens a new pixel inspector window.
	@param imp		ImagePlus
	@param width	the width of the window in pixels
	@param height	the height of the window in pixels
	*/
	public PixelInspector_( ImagePlus imp , int width, int height) {
		this.imp = imp;
		if( imp==null ) {
			IJ.noImage();
			return;
		}
		ImageWindow win = imp.getWindow();
        if( win==null)
        	close();
        sourceCanvas = win.getCanvas();

        setTitle( imp.getTitle() );
		enableEvents( AWTEvent.WINDOW_EVENT_MASK );
		if (IJ.isLinux()) setBackground(ImageJ.backgroundColor);

 		cellDigit = (int) Prefs.get( DIGIT[ processorToInt()], 8 );
 	    font = new Font("SanSerif", Font.PLAIN, sizes[fontSize]);
		iPanel = new InspectorPanel( imp, this );
		iPanel.setTitle( imp.getTitle() );
		add("Center", iPanel);
		addMenuBar();

		addKeyListener( iPanel );
        addImageListeners();
 		addFocusListener(this);
		setFont();
		
		ImageJ ij = IJ.getInstance();
		if (ij!=null) {
			Image img = ij.getIconImage();
			if (img!=null)
				try { setIconImage(img); } catch (Exception e) {}
		}
		WindowManager.addWindow(this);	
		Point loc = Prefs.getLocation(LOC_KEY);
		if( width==0 && height==0 ) {
			if( loc!=null ) {
				width = (int)Prefs.get( WIDTH_KEY, DEFAULT_WIDTH );
				height = (int)Prefs.get( HEIGHT_KEY, DEFAULT_HEIGHT );
				IJ.log( "Window(pref):" );
				setLocation(loc);
			} else {
				width = DEFAULT_WIDTH;
				height = DEFAULT_HEIGHT;
			}
		}
		setSize( width, height );
		GUI.center(this);
		this.setVisible( true );
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		syncRoi();
	}
	public PixelInspector_( ImagePlus imp ) { this( imp, 0, 0); }
	public PixelInspector_() { this( WindowManager.getCurrentImage(), 0, 0); }

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
		antialiased = new CheckboxMenuItem( "Antialiased", Prefs.get( FONT_ANTI, IJ.isMacOSX() ? true:false ) );
		antialiased.addItemListener(this);
		m.add(antialiased);
		m.add(new MenuItem("Save Settings"));
		m.addActionListener(this);
		mb.add(m);
		setMenuBar(mb);
	}

    private void addImageListeners() {
        ImagePlus.addImageListener(this);
        ImageWindow win = imp.getWindow();
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
 
	void setFont() {
       iPanel.cellformat = cellFormat() ;
       iPanel.setFont( new Font("SanSerif", Font.PLAIN, sizes[fontSize]), antialiased.getState() );
 	}
	
	// ActionListener
	public void actionPerformed( ActionEvent e ) {
		String cmd = e.getActionCommand();
		if ( cmd.equals("Increase Digit") )
			changeDigit( +1 );
		else if ( cmd.equals("Decrease Digit") )
			changeDigit( -1 );
		else if ( cmd.equals("Make Text Larger") )
			changeFontSize(true);
		else if ( cmd.equals("Make Text Smaller") )
			changeFontSize(false);
		else if ( cmd.equals("Save Settings") )
			saveSettings();
		else 
			iPanel.doCommand(cmd);
	}

	public void processWindowEvent( WindowEvent e ) {
		super.processWindowEvent(e);
		int id = e.getID();
		if( id==WindowEvent.WINDOW_CLOSING )
			close();	
		else if( id==WindowEvent.WINDOW_ACTIVATED )
			WindowManager.setWindow(this);
	}

	public void itemStateChanged( ItemEvent e ) { setFont(); }
	public void close( boolean showDialog ) {
		if ( getTitle().equals("Results") ) {
			if (showDialog && !Analyzer.resetCounter())
				return;
			IJ.setTextPanel(null);
			Prefs.saveLocation(LOC_KEY, getLocation());
			Dimension d = getSize();
			Prefs.set(WIDTH_KEY, d.width);
			Prefs.set(HEIGHT_KEY, d.height);
		} else if (getTitle().equals("Log")) {
			Prefs.saveLocation(LOG_LOC_KEY, getLocation());
			IJ.debugMode = false;
			IJ.log("\\Closed");
			IJ.notifyEventListeners(IJEventListener.LOG_WINDOW_CLOSED);
		} else if (getTitle().equals("Debug")) {
			Prefs.saveLocation(DEBUG_LOC_KEY, getLocation());
		}
		setVisible(false);
		dispose();
		WindowManager.removeWindow(this);
	}
	public void close() { close(true); }
		
	public void syncRoi() {
		iPanel.syncSelectionWithRoi();
	}
	public void setRoi( Roi roi) {
		iPanel.setRoi(roi);
	}
	public void setRoi( int x, int y, int width, int height ) {
		setRoi( new Rectangle( x, y, width, height ) );
	}
	public void setRoi( Rectangle r ) {
		setRoi( new Roi( r.x, r.y, r.width, r.height ) );
	}
	public Roi getRoi() {
		return iPanel.getRoi();
	}
	public Canvas getCanvas() {
		return (Canvas) iPanel.iCanvas;
	}
	void changeFontSize(boolean larger) {
        if (larger) {
            fontSize++;
            if (fontSize==sizes.length) fontSize = sizes.length-1;
        } else {
            fontSize--;
            if (fontSize<0) fontSize = 0;
        }
        IJ.showStatus( sizes[fontSize]+" point" );
        setFont();
    }

	public String cellFormat() {
		return cellformat[ processorToInt() ][cellDigit];
	}
	public String cellFormatMax() {
		String[] format = cellformat[ processorToInt() ];
		return cellformat[ processorToInt() ][ format.length-1 ];
	}
	void changeDigit( int d ) {
		String[] format = cellformat[ processorToInt() ];
        if( d>0 )
            cellDigit++;
        else if( d<0 )
            cellDigit--;
       if( cellDigit>=format.length ) cellDigit = format.length-1;
       if (cellDigit<0) cellDigit = 0;
       IJ.showStatus( "Digits="+cellFormat()+"." );
       setFont();
    }
	public int processorToInt( ) {
		ImageProcessor ip = imp.getProcessor();
		if( ip instanceof FloatProcessor )
			return 0;
		else if( ip instanceof ShortProcessor )
			return 1;
		else if( ip instanceof ByteProcessor )
			return 2;
		else
			return 3;
	}
	void saveSettings() {
		Prefs.set( FONT_SIZE, fontSize );
		Prefs.set( FONT_ANTI, antialiased.getState() );
		Prefs.set( DIGIT[ processorToInt()], cellDigit );
		IJ.showStatus( "Font settings saved (size="+sizes[fontSize]+", antialiased="+antialiased.getState()+")" );
	}
	// focus listener
	public void focusGained( FocusEvent e ) { WindowManager.setWindow(this); }
	public void focusLost( FocusEvent e ) {}
	// image listener
	public void imageOpened( ImagePlus imp ) { }
	public void imageUpdated( ImagePlus imp ) {
		if( imp==this.imp )
			syncRoi();
	}
	public void imageClosed( ImagePlus imp ) {
		if( imp==this.imp ) {
			removeImageListeners();
			close();
		}
	}
	// mouse listener
	public void mousePressed(MouseEvent arg0) { syncRoi(); }
	public void mouseClicked(MouseEvent arg0) { syncRoi(); }
	public void mouseDragged(MouseEvent arg0) { syncRoi(); }
	public void mouseReleased(MouseEvent arg0) { }
	public void mouseExceited(MouseEvent arg0) { }
	public void mouseEntered(MouseEvent arg0) { syncRoi(); }
	public void mouseMoved(MouseEvent arg0) {}
	// key listener
	public void keyPressed(KeyEvent arg0) { syncRoi(); }
	public void keyReleased(KeyEvent arg0) { }
	public void keyTyped(KeyEvent arg0) { }


	/********************************************************************************************************/
	/**
	* Display pixel values table.
	* based on ij.text.TextWindow.java
	*/
	class InspectorPanel extends Panel
		implements AdjustmentListener, ActionListener, ClipboardOwner,
			MouseListener, MouseMotionListener, MouseWheelListener, KeyListener, Runnable {
	
		// Parent Frame
		PixelInspector_ tw;
		// Target Image
		ImagePlus imp;
		ImageProcessor ip;
		// Display area （ target image's coordinate system ）
		Rectangle dispRect;
		// Selection area ( target image's coordinate system ）
		Rectangle seleRect;
	
	  	// Table canvas
		String title = "";
		InspectorCanvas iCanvas;
		Font font;
		PopupMenu pm;
		// Scroll bar
		Scrollbar sbHoriz,sbVert;
		// Top-Left point in virtual table image　（ Panel's coordinate system ）
		int iX,iY;
		// Display format 
	    String cellformat;
	 
		// Table cell selection mode for mouse drag
		int selectMode=-1;
		static final int DRAG_NORM=0;
		static final int DRAG_ROW=1;
		static final int DRAG_COL=2;
		static final int DOUBLE_CLICK_THRESHOLD = 650;
		// when true, ROI follows selection in this panel.  FALSE: selection follows ROI.
		boolean selecting = false;
	
		KeyListener keyListener;
		Cursor resizeCursor = new Cursor(Cursor.E_RESIZE_CURSOR);
	  	Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
		long mouseDownTime;
	    String filePath;
	
		/** Constructs a new TextPanel. */
		public InspectorPanel( ImagePlus imp, PixelInspector_ tw ) {
			this.tw = tw;
			this.imp = imp;
			font = tw.font;
			cellformat = tw.cellFormat();
			if( imp==null ) return;
			ip = imp.getProcessor();
			if( ip==null ) return;
			
			dispRect = new Rectangle( 0, 0, ip.getWidth(), ip.getHeight() );
	
			iCanvas = new InspectorCanvas( this );
			setLayout(new BorderLayout());
			sbHoriz = new Scrollbar( Scrollbar.HORIZONTAL );
			sbHoriz.addAdjustmentListener(this);
			sbHoriz.setFocusable(false); // prevents scroll bar from blinking on Windows
			sbVert = new Scrollbar( Scrollbar.VERTICAL );
			sbVert.addAdjustmentListener(this);
			sbVert.setFocusable(false);
			ImageJ ij = IJ.getInstance();
			if (ij!=null) {
				sbHoriz.addKeyListener(ij);
				sbVert.addKeyListener(ij);
			}
			add("Center",iCanvas);
			add("South", sbHoriz);
			add("East", sbVert);
			addPopupMenu();
			doLayout();
			if( imp.getRoi()!=null )
				syncSelectionWithRoi();
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
		public void setTitle(String title) {
			this.title = title;
		}
		public void setFont(Font font, boolean antialiased) {
			iCanvas.fFont = font;
			iCanvas.iImage = null;
			iCanvas.fMetrics = null;
			iCanvas.antialiased = antialiased;
			if( isShowing() ) updateDisplay();
		}
		/* ---- start Event Listeners ----*/
		public void updateDisplay() {
			adjustScroll();
	//		IJ.log( "updateDisplay: iX,iY=("+iX+","+iY+") selRect(w,h)=("+seleRect.width+","+seleRect.height+")" );
			iCanvas.repaint();
		}
		
	    /** For better performance, open double-clicked files on 
	    	separate thread instead of on event dispatch thread. */
	    public void run() {
	        if (filePath!=null) IJ.open(filePath); // ???
	    }
	
		// Mouse motion
	    public void mousePressed( MouseEvent e ) {
			int x=e.getX(), y=e.getY();
			if (e.isPopupTrigger() || e.isMetaDown())
				pm.show( e.getComponent(), x, y );
	 		else if( e.isShiftDown() )
				setSelectionEnd( x, y );
			else {
	 			setSelectionStart( x, y );
	 			selecting = true;
	 			handleDoubleClick();
	 		}
		}
		public void mouseExited ( MouseEvent e ) { selecting = false; }
		public void mouseMoved ( MouseEvent e ) { }
		public void mouseDragged ( MouseEvent e ) {
			if( e.isPopupTrigger() || e.isMetaDown() ) return;
			int x=e.getX(), y=e.getY();
			// canvas外にdragされたらスクロール
			if( x>iCanvas.getWidth() )
				iX += x-iCanvas.getWidth();
			else if( x<0 )
				iX += x;
			if( y>iCanvas.getHeight() )
				iY += y-iCanvas.getHeight();
			else if( y<0 )
				iY += y;
			setSelectionEnd(x, y);
			selecting = true;
		}
	 	public void mouseReleased ( MouseEvent e ) { selecting = false; }
		public void mouseClicked ( MouseEvent e ) {}
		public void mouseEntered ( MouseEvent e ) {}
		public void mouseWheelMoved( MouseWheelEvent e) {
			synchronized(this) {
				int rot = e.getWheelRotation();
				sbVert.setValue( sbVert.getValue()+rot );
				iY = iCanvas.cell.height*sbVert.getValue();
				iCanvas.repaint();
			}
		}
	
		/** Unused keyPressed and keyTyped events will be passed to 'listener'.*/
		public void addKeyListener(KeyListener listener) {
			keyListener = listener;
		}
		public void keyPressed(KeyEvent e) {
			int key = e.getKeyCode();
	//		IJ.log( "keyPressed: key=("+key+"), shift=("+e.isShiftDown()+")" );
			if( key==KeyEvent.VK_UP || key==KeyEvent.VK_DOWN || key==KeyEvent.VK_LEFT || key==KeyEvent.VK_RIGHT ) {
				moveSelectionByKey( key, e.isShiftDown() );
			} else if ( key==KeyEvent.VK_W )
				close();
			if( keyListener!=null
					&& key!=KeyEvent.VK_C && key!=KeyEvent.VK_A
					&& key!=KeyEvent.VK_9 && key!=KeyEvent.VK_0 )		
				keyListener.keyPressed(e);
		}
		public void keyReleased (KeyEvent e) {}
		public void keyTyped (KeyEvent e) {
			if( keyListener!=null )
				keyListener.keyTyped(e);
		}
	
		public void adjustmentValueChanged (AdjustmentEvent e) { 
			iX = iCanvas.cell.width * sbHoriz.getValue();
	 		iY = iCanvas.cell.height * sbVert.getValue();
			iCanvas.repaint();
	//		IJ.log( "adjustmentValueChanged: iX,iY=("+iX+","+iY+")"  );
	 	}
	 
		public void actionPerformed (ActionEvent e) {
			String cmd=e.getActionCommand();
			doCommand(cmd);
		}
	
		public void lostOwnership ( Clipboard clip, Transferable cont ) {}
	
	 	void doCommand(String cmd) {
	 		if( cmd==null ) return;
			if( cmd.equals("Close") ) close();
			else if( cmd.equals("Copy") ) copySelection();
			else if( cmd.equals("Select All") ) selectAll();
	 		else if( cmd.equals("Options...") ) IJ.doCommand("Input/Output...");
		}
		/* ---- end of Event Listeners ----*/
	 	
		/** Get cell value with coordinate check. */
	 	public String getCell( int col, int row ) {
			if( dispRect.contains( col, row ) )	return null;
			return getChars( col, row );
		}
		/** Get cell value without coordinate check. */
		String getChars( int x, int y ) {
			if( ip==null ) return null;
			return new java.text.DecimalFormat( cellformat ).format( ip.getPixelValue( x, y ) );
		}	
		String getCharsFullDigit( int x, int y ) {
			if( ip==null ) return null;
			return new java.text.DecimalFormat( tw.cellFormatMax() ).format( ip.getPixelValue( x, y ) );
		}	
	    String stringOf( float v, int digits, boolean expMode ) {
	        if ( expMode ) {
	            int exp = (int) Math.floor( Math.log(Math.abs(v))/Math.log(10) );
	            double mant = v/Math.pow(10,exp);
	            digits = (exp > 0 && exp < 10) ? 5 : 4;
	            if (v<0) digits--;      //space needed for minus
	            return IJ.d2s(mant,digits)+"e"+exp;
	        } else
	            return IJ.d2s(v, digits);
	    }
	
	    /** Get selected cells as ROI */
	    public Roi getRoi() {
			if( seleRect==null || seleRect.getBounds()==null ) return null;
			Rectangle rect = (Rectangle) seleRect.clone();
			if( rect.width<0 ) {
				rect.x += rect.width;
				rect.width = -rect.width;
			}
			if( rect.height<0 ) {
				rect.y += rect.height;
				rect.height = -rect.height;
			}
			return new Roi(rect);
	    }
	    /** Set cells selected following ROI */
	    public void setRoi( Roi roi ) {
			if( selecting==true || roi==null || roi.getBounds()==null ) return;
			seleRect = roi.getBounds();
			center( seleRect );
	    }

	    /** Set ROI following selection area */
		public void syncRoiWithSelection( ) {
			imp.setRoi( getRoi(), true );
			imp.updateImage();
//			imp.updateAndDraw();
		}
		/** Set selection area following ROI */
		public void syncSelectionWithRoi( ) {
			setRoi( imp.getRoi() );
		}
	
		/** Focus center of argument  */
		public void center( Rectangle r ) {
//			IJ.log( "center: ("+r.x+","+r.y+")-("+r.width+","+r.height+")" );
//			IJ.log( "center: iCanvas.cell("+iCanvas.cell.width+","+iCanvas.cell.height+")" );
			if( r==null || iCanvas==null ) return;
			iX = (int) (r.x+r.width/2 - dispRect.x)*iCanvas.cell.width + iCanvas.cell.width/2 + iCanvas.cell.width - iCanvas.getWidth()/2;
			iY = (int) (r.y+r.height/2 - dispRect.y)*iCanvas.cell.height + iCanvas.cell.height/2 + iCanvas.cell.height - iCanvas.getHeight()/2;
//			IJ.log( "center: iX,iY=("+iX+","+iY+")" );
			adjustScroll();
			iCanvas.repaint();
		}
		void moveSelectionByKey( int key, boolean shift ){
	//		IJ.log( "moveSelectionByKey: key=("+key+"), shift=("+shift+")" );
			if( shift ){ // update selectionEnd point
				Point p = movePointByKey( key, new Point( seleRect.x+seleRect.width, seleRect.y+seleRect.height ) );
				seleRect.setSize( p.x-seleRect.x, p.y-seleRect.y );
			} else { // update selectionStart point
				seleRect.setLocation( movePointByKey( key, new Point( seleRect.x, seleRect.y ) ) );
			}
			syncRoiWithSelection( );
			center( seleRect );
		}
		Point movePointByKey( int key, Point p ) {
			if( key==KeyEvent.VK_UP ) p.y--;
			if( key==KeyEvent.VK_DOWN ) p.y++;
			if( key==KeyEvent.VK_LEFT ) p.x--;
			if( key==KeyEvent.VK_RIGHT ) p.x++;
			if( p.y<dispRect.y ) p.y=dispRect.y;
			if( p.y>=dispRect.y+dispRect.height ) p.y=dispRect.y+dispRect.height-1;
			if( p.x<dispRect.x ) p.x=dispRect.x;
			if( p.x>=dispRect.x+dispRect.width ) p.y=dispRect.x+dispRect.width-1;
			return p;
		}
	
		/** Start selection. */
		void setSelectionStart( int x, int y ) {
//			resetSelection();
//	 		IJ.log( "PixelInspector_.selectionStart("+x+","+y+")" );
//	 		IJ.log( "  iCanvas.cell.(width,height)=("+iCanvas.cell.width+","+iCanvas.cell.height+")" );
			Dimension d = iCanvas.getSize();
			if( iCanvas.cell.height==0 || x>d.width || y>d.height) return;
	      	if( x<iCanvas.cell.width && y<iCanvas.cell.height ) // click Top-Left
	      		selectAll();
	      	else if( x<iCanvas.cell.width ){ // click row number
	      		seleRect = new Rectangle( dispRect.x, yToRow(y), dispRect.width, 0 );
	      		selectMode = DRAG_ROW;
	      	} else if( y<iCanvas.cell.height ){ // click column number
	      		seleRect = new Rectangle( xToCol(x), dispRect.y, 0, dispRect.height );
	      		selectMode = DRAG_COL;
	      	} else { // click cell
	      		seleRect = new Rectangle( xToCol(x), yToRow(y), 0, 0 );
	      		selectMode = DRAG_NORM;
	      	}
//	 		IJ.log( "  selection: iX,iY=("+iX+","+iY+") selRect(w,h)=("+seleRect.width+","+seleRect.height+")" );
			syncRoiWithSelection();
	 		iCanvas.repaint();
		}
	
		/** Extend selection area. */
		void setSelectionEnd( int x, int y) {
//	 		IJ.log( "PixelInspector_.setSelectionEnd("+x+","+y+")" );
//	 		IJ.log( "  iCanvas.cell.(width,height)=("+iCanvas.cell.width+","+iCanvas.cell.height+")" );
	     	if( seleRect.x==xToCol(x) && seleRect.y==yToRow(y) ) return;
	     	if( selectMode==DRAG_NORM || selectMode==DRAG_COL ) {
	     		seleRect.width = xToCol(x) - seleRect.x;
	     	}
			if( selectMode==DRAG_NORM || selectMode==DRAG_ROW ) {
				seleRect.height = yToRow(y) - seleRect.y;
			}
//			IJ.log( "  extendSelection: iX,iY=("+iX+","+iY+") selRect(w,h)=("+seleRect.width+","+seleRect.height+")" );
			syncRoiWithSelection();
			iCanvas.repaint();
		}
	
		/** Selects all cells. */
		public void selectAll() {
			seleRect = (Rectangle) dispRect.clone();
			iCanvas.repaint();
		}
	
		/** Clears the selection, if any. */
		public void resetSelection() {
			seleRect = null;
			selectMode = -1;
			iCanvas.repaint();
		}
	
		int xToCol( int x ){
			return ((x+iX)/iCanvas.cell.width)-1+dispRect.x;
		}
		int yToRow( int y ){
			return ((y+iY)/iCanvas.cell.height)-1+dispRect.y;
		}
	
		/* (x,y)が選択した範囲内にあればtrue */
		boolean insideSelection( int x, int y ){
			Rectangle rect = new Rectangle( seleRect );
			if( rect.width<0 ) {
				rect.width = -rect.width;
				rect.x = rect.x - rect.width;
			}
			rect.width++;
			if( rect.height<0 ) {
				rect.height = -rect.height;
				rect.y = rect.y - rect.height;
			}
			rect.height++;
			return rect.contains( x, y );
		}
	
	    /** Adjust scroll bar */
		synchronized void adjustScroll() {
			Dimension d = iCanvas.getSize();
			if( iCanvas.cell.width>0 ) {
				if( iX<0 ) iX=0;
				int value = iX/iCanvas.cell.width;			// スクロールハンドルの開始位置
				int visible = d.width/iCanvas.cell.width;	// スクロールハンドルの長さ
				int maximum = dispRect.width+1;		// スクロールバーの長さ
				if( visible<0 ) visible=0;
				if( visible>maximum ) visible=maximum;
				if( value>(maximum-visible) ) value=maximum-visible;
				sbHoriz.setValues( value, visible, 0, maximum );
				iX = iCanvas.cell.width*value;
	//			IJ.log( "adjustHScroll: Horizontal: value="+value+", visible="+visible+", max="+maximum );
			}
			if( iCanvas.cell.height>0 ) {
				if( iY<0 ) iY=0;
				int value = iY/iCanvas.cell.height;			// スクロールハンドルの開始位置
				int visible = d.height/iCanvas.cell.height;	// スクロールハンドルの長さ
				int maximum = dispRect.height+1;		// スクロールバーの長さ
				if( visible<0 ) visible=0;
				if( visible>maximum ) visible=maximum;
				if( value>(maximum-visible) ) value=maximum-visible;
				sbVert.setValues( value, visible, 0, maximum);
				iY = iCanvas.cell.height*value;
	//			IJ.log( "adjustScroll: Vertical: value="+value+", visible="+visible+", max="+maximum  );
			}
	//		IJ.log( "adjustScroll: iX,iY=("+iX+","+iY+")" );
		}
	
		// Double clicked
		void handleDoubleClick() {
			if( seleRect==null ) return;
			boolean doubleClick = System.currentTimeMillis()-mouseDownTime<=DOUBLE_CLICK_THRESHOLD;
			mouseDownTime = System.currentTimeMillis();
			if( doubleClick ) {
				center( seleRect );
			}
		}
	
		/**
		Copies the current selection to the system clipboard. 
		Returns the number of characters copied.
		*/
		public int copySelection() {
			if (Recorder.record && title.equals("Results"))
				Recorder.record("String.copyResults");
			if( seleRect==null )
				selectAll();
			StringBuffer sb = new StringBuffer();
			sb.append( title );
			for( int x=0; x<seleRect.width; x++ )
				sb.append( "\t"+(seleRect.x+x) );
			sb.append( "\n" );
			for( int y=0; y<seleRect.height; y++ ) {
				sb.append( ""+(seleRect.y+y) );
				for( int x=0; x<seleRect.width; x++ ) {
					sb.append( "\t"+getCharsFullDigit( seleRect.x+x, seleRect.y+y ) );
				}
				sb.append( "\n" );
			}
			String s = new String(sb);
			Clipboard clip = getToolkit().getSystemClipboard();
			if (clip==null) return 0;
			StringSelection cont = new StringSelection(s);
			clip.setContents(cont,this);
			if (s.length()>0) {
				IJ.showStatus( "("+(seleRect.width)+","+(seleRect.height)+") pixels copied to clipboard");
				if (this.getParent() instanceof ImageJ)
					Analyzer.setUnsavedMeasurements(false);
			}
			return s.length();
		}
		
		int copyAll() {
			selectAll();
			int length = copySelection();
			resetSelection();
			return length;
		}
	}
	
	
	
	
	/********************************************************************************************************/	
	/**
	* Display pixel values table.
	* based on ij.text.TextWindow.java
	*/
	class InspectorCanvas extends Canvas {
	
		InspectorPanel tp;
		Font fFont;
		FontMetrics fMetrics;
		Graphics gImage;
		Image iImage;
		public Dimension cell = new Dimension( 48, 24 );
		boolean antialiased;
	
		InspectorCanvas(InspectorPanel tp ) {
			this.tp = tp;
			fFont = tp.font;
			FontMetrics fm = getFontMetrics( fFont );
			String[] c = tp.cellformat.split(";");
			cell.setSize( fm.stringWidth( c[0] )+8, fm.getHeight()+2 );
			addMouseListener(tp);
			addMouseMotionListener(tp);
			addKeyListener(tp);
			addMouseWheelListener(tp);
		}
		
		@Override
		public void setBounds(int x, int y, int width, int height) {
	    	super.setBounds(x, y, width, height);
			tp.adjustScroll();
	    	iImage = null;
		}
	
		@Override
		public void update(Graphics g) {
			paint(g);
		}
	  
		@Override
		public void paint(Graphics g) {
			if( tp==null || g==null ) return;
			Dimension cSize = getSize();
			if( cSize.width<=0 || cSize.height<=0 ) return;
			g.setColor(Color.lightGray);
			if( iImage==null )
				makeImage( cSize.width, cSize.height );
			if( cell==null )
				setCellSize();
			tp.adjustScroll();
	
			// 白で塗りつぶす
			gImage.setColor( Color.white );
			gImage.fillRect( 0, 0, cSize.width, cSize.height );
	
			int x = 0, y = 0;
			int col0 = tp.dispRect.x+tp.iX/cell.width;
			int row0 = tp.dispRect.y+tp.iY/cell.height;
			
			int col = col0;
			int row = row0;
	//		IJ.log( "roi (x,y)=("+tp.roiRect.x+","+tp.roiRect.y+") (w,h)=("+tp.roiRect.width+","+tp.roiRect.height+")" );
	//		IJ.log( "scroll (ix,iy)=("+tp.iX+","+tp.iY+") (col0,row0)=("+col0+","+row0+")" );
	
			gImage.setColor( Color.lightGray );
			gImage.drawLine( 0, 0, cell.width-1, cell.height-1 );
			gImage.drawLine( 0, cell.height-1, cSize.width-1, cell.height-1 );
			gImage.drawLine( cell.width-1, 0, cell.width-1, cSize.height-1 );
			// X軸カラムを描画
			x += cell.width;
			while( col<tp.dispRect.x+tp.dispRect.width && x<cSize.width ) {
				drawCell( x, y, Color.black, Color.lightGray, ""+col ); // X-axis
				x += cell.width;
				col++;
			}
			// セル描画
			y = cell.height;
			while( row<tp.dispRect.y+tp.dispRect.height && y<cSize.height ) {
				x = 0;
				drawCell( x, y, Color.black, Color.lightGray, ""+row ); // Y-axis
				x += cell.width;
				col = col0;
				while( col<tp.dispRect.x+tp.dispRect.width && x<cSize.width ) {
					String chars = tp.getChars( col, row );
					if( tp.seleRect!=null && tp.insideSelection( col, row ) ){
						drawCell( x, y, Color.white, Color.black, chars ); //selected area
					} else {
						drawCell( x, y, Color.black, null, chars );					
					}
					x += cell.width;
					col++;
				}
				y += cell.height;
				row++;
			}
			if( iImage!=null )
				g.drawImage( iImage, 0, 0, null );
		}
	
		void drawCell( int x, int y, Color fg, Color bg, String s ) {
			if( bg!=null ) {
				gImage.setColor( bg );
				gImage.fillRect( x, y, cell.width-1, cell.height-1 );
			}
			if( fg!=null )
				gImage.setColor( fg );
			if( s!=null )
				gImage.drawString( s, x+2, y+cell.height-5 );
		}
	
	 	void makeImage(int iWidth, int iHeight) {
			iImage = createImage( iWidth, iHeight );
			if (gImage!=null) {
				gImage.dispose();
	//			IJ.log( "gImage.dispose()" );
			}
			gImage = iImage.getGraphics();
			gImage.setFont( fFont );
			Java2.setAntialiasedText( gImage, antialiased );
			setCellSize();
		}
	
		void setCellSize() {// auto resize cell
			if( fMetrics==null )
				fMetrics = gImage.getFontMetrics();
			String[] c = tp.cellformat.split(";");
			cell.setSize( fMetrics.stringWidth( c[0] )+cellMarginWidth, fMetrics.getHeight()+cellMarginHeight );
		}
		public PixelInspector_ getImage() {
			return tp.tw;
		}
	}

}
