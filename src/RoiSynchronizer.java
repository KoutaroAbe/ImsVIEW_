import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.Roi;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Iterator;

/**
* Synchronize ROIs for selected images.
* @author kabe@cris.hokudai.ac.jp
* @version 25-Jul-2011
*/
public class RoiSynchronizer implements ImageListener, MouseListener, KeyListener, MouseMotionListener {
	static ArrayList<Object> listeners;  // store instance of ImagePlus or of PixelInspector
	
	RoiSynchronizer( ArrayList<ImagePlus> list ) {
//		IJ.log( "RoiSync.new:"+list );
		listeners = new ArrayList<Object>();
		setListener( list );
	}
	void addListener( Object obj ){
//		IJ.log( "RoiSync.addlisteners:"+obj );
		if( obj==null )
			return;
		if( obj instanceof ImagePlus ) {
			ImagePlus imp = (ImagePlus) obj;
			Canvas ic = (Canvas) imp.getWindow().getCanvas();
			if( ic==null )
				return;
			synchronized( listeners ){
				listeners.add( imp );
				MouseMotionListener[] m = ic.getMouseMotionListeners();
				addMMKListeners( ic );
				// search and add its child PixelInspector 
				for( int j=0; j<m.length; j++ )
					if( m[j] instanceof PixelInspector_ ) {
						PixelInspector_ p = (PixelInspector_)m[j];
						listeners.add( p );
						addMMKListeners( p.getCanvas() );
					}
			}
		}
	}
	void setListener( ArrayList<ImagePlus> list ) {
		removeAllListeners();
		Iterator<ImagePlus> i = list.iterator();
		while( i.hasNext() ){
			addListener( i.next() );
		}
	}
	void removeAllListeners() {
//		IJ.log( "removeAllListeners:" );
		if( listeners==null || listeners.size()==0 )
			return;
		Iterator<Object> i = listeners.iterator();
		while( i.hasNext() ){
			try {
				Object obj = i.next();
				if( obj instanceof ImagePlus )
					removeMMKListeners( ( (ImagePlus)obj ).getWindow().getCanvas() );
				else if( obj instanceof PixelInspector_ )
					removeMMKListeners( ( (PixelInspector_)obj ).getCanvas() );
			} catch( java.lang.NullPointerException e ) {
				continue;
			}
		}
		listeners.clear();
	}

	void addMMKListeners( Canvas ic ) {
		if( ic==null )
			return;
		ic.addMouseListener( this );
		ic.addMouseMotionListener( this );
		ic.addKeyListener( this );
	}
	void removeMMKListeners( Canvas ic ) {
		if( ic==null )
			return;
		ic.removeMouseListener( this );
		ic.removeMouseMotionListener( this );
		ic.removeKeyListener( this );
	}
	/**
	 * @param sourceObj
	 *			 object of a user specified ROI. 
	 */
	protected void notifyListeners( Object sourceObj ) {
		Roi roi;
//		IJ.log( "RoiSync.notifyListeners: source="+sourceObj );
		if( sourceObj instanceof ij.ImagePlus ) {
			sourceObj = ( (ImagePlus)sourceObj ).getCanvas().getImage();
			roi = ((ImagePlus) sourceObj).getRoi();
		} else if( sourceObj instanceof ij.gui.ImageCanvas ) {
			sourceObj = ( (ImageCanvas)sourceObj ).getImage();
			roi = ((ImagePlus) sourceObj).getRoi();
		} else if( sourceObj instanceof PixelInspector_.InspectorCanvas ) {
			sourceObj = ((PixelInspector_.InspectorCanvas) sourceObj).getImage();
			roi = ((PixelInspector_) sourceObj).getRoi();
		} else {
			return;		
		}
		synchronized( listeners ) {
			for ( int i=0; i<listeners.size(); i++ ) {
				Object l = listeners.get(i);
				if( sourceObj.equals( l ) )	// skip because l is source
					continue;
				if( l instanceof ImagePlus )
					( (ImagePlus)l ).setRoi( roi );
				else if( l instanceof PixelInspector_ )
					( (PixelInspector_)l ).setRoi( roi );
			}
		}
	}
	public void imageClosed(ImagePlus imp) {}
	public void imageOpened(ImagePlus imp) {}
	public void imageUpdated(ImagePlus imp) { notifyListeners( imp ); }
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mousePressed(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) { notifyListeners( e.getSource() ); }
	public void mouseDragged(MouseEvent e) { notifyListeners( e.getSource() ); }
	public void mouseMoved(MouseEvent e) {}
	public void keyPressed(KeyEvent e) { notifyListeners( e.getSource() ); }
	public void keyReleased(KeyEvent e) {}
	public void keyTyped(KeyEvent e) { notifyListeners( e.getSource() ); }
}
