import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.WindowManager;
import ij.io.FileInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.JLabel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * List of opened image files.
 * @author kabe@cris.hokudai.ac.jp
 * @version 03-Sep-2020
 */
@SuppressWarnings("serial")
public class ImageList extends DefaultTableModel implements ImageListener {
			
	public static final int COLUMN_ID=0, COLUMN_SYNCROI=1, COLUMN_TITLE=2, COLUMN_DIR=3, COLUMN_DEPTH=4;
	static int DIR_STRLEN = 32;
	static String[] columns = {	"Id",	"SyncROI",	"Title",	"Directory",	"Slices" };
	Object[] defaultValues = { 	"0",	false,		"TITLE",	"DIR",			"SLICES" };
	static boolean[] visible = { false,	true,		true,		true,			true };
	static boolean[] editable = {false,	true,		true,		false,			false };
	
	ImageList( ) {
		super( new Object[0][], columns );
		ImagePlus.addImageListener(this);
		refresh();
	}
	public void refresh() {
		while( getRowCount()>0 )
			removeRow(0);
		int[] windowId = WindowManager.getIDList();
		if( windowId!=null && windowId.length>0 )
			for( int r=0; r<windowId.length; r++ ) {
				ImagePlus imp = WindowManager.getImage( windowId[r] );
				if( imp!=null )
					add( imp );
			}
	}

	public void add( ImagePlus imp ) {
		if( imp!=null && update( imp )==false )
//			if( add filter here )
			this.addRow( getImpParams( imp ) );
	};

	boolean update( ImagePlus imp ) {
		if( imp==null )
			return false;
		int i = indexOf( imp.getID() );
		if( i<0 )
			return false;
		Object[] row = getImpParams( imp );
		for( int col=0; col<columns.length; col++ )
			this.setValueAt( row[col], i, col );
		return true;
	}

	public void remove( ImagePlus imp ) {
		if( imp==null )
			return;
		int i = indexOf( imp.getID() );
		if( indexOf( imp.getID() )>=0 )
			this.removeRow( i );
	}

	/* Override */
	public boolean isCellEditable( int row, int column ){
		return ( 0<=column && column<editable.length) ? editable[column] : false;
	}
	
	// Create a ROW from ImagePlus
	Object[] getImagePlusParams( ImagePlus imp, boolean check ) {
		if( imp==null )
			return null;
		Vector row = new Vector();
		Object d = null;
//		IJ.log( "OriginalFI"+imp.getOriginalFileInfo() );
//		IJ.log( "FI"+imp.getFileInfo() );
		for( int c=0; c<columns.length; c++ ) {
//			if( !visible[c] )
//				continue;
			switch( c ) {
			case COLUMN_ID:
				d = imp.getID();
				break;
			case COLUMN_SYNCROI:
				d = check;
				break;
			case COLUMN_DIR:
				FileInfo fi = imp.getOriginalFileInfo();
//				IJ.log( "DIR"+fi );
				if( fi==null )
					fi = imp.getOriginalFileInfo();
				if( fi!=null ) {
					String dir = fi.directory;
					d = dir;
				}
				break;
			case COLUMN_TITLE:
				d = imp.getTitle();
				if( d==null || ((String) d ).length()==0 ){
					d = WindowManager.getUniqueName( "Image" );
					imp.setTitle( (String) d );
				}
				break;
			case COLUMN_DEPTH:
				d = imp.getStackSize();
				break;
			}
			if( d!=null )
				row.add( d );
		}
		return row.toArray();
	}
	Object[] getImpParams( ImagePlus imp ) {
		return getImagePlusParams( imp, ( valueOfCheckAll() || countOfSyncCheck()!=0 ) );
	}

	int indexOf( int windowId ) {
		for( int i=0; i<getRowCount(); i++ )
			if( (Integer) getValueAt( i, COLUMN_ID )==windowId )
				return i; // window exists
		return -1; // not found
	}

	public ImagePlus rowToImagePlus( int row ) {
		return WindowManager.getImage( (Integer) getValueAt( row, COLUMN_ID ) ); 
	}

	// Sync ROI 
	void checkAllRows( boolean flag ) {
		for( int i=0; i<getRowCount(); i++ ) {
			setValueAt( flag, i, COLUMN_SYNCROI );
//			IJ.log( "set row("+i+")="+getValueAt( i, COLUMN_TITLE ) );
		}
		return;
	}
	// Count checked row (0:nothing, 1:all, -1:other)
	int countOfSyncCheck() {
		int num = getRowCount();
		int checked=0;
		for( int i=0; i<num; i++ )
			if( (Boolean) getValueAt( i, COLUMN_SYNCROI )==true )
				checked++;
		if( checked==0 )
			return 0;
		if( checked==num )
			return 1;
		return -1;
	}
	// Look for 'Check all' check box
	private boolean valueOfCheckAll() {
//		return (ImsVIEW_.exists()==true ? ImsVIEW_.allCheck.getSelectedObjects()!=null : false);
//		gaxtsu!	
		if( ImsVIEW_.allCheck==null || ImsVIEW_.allCheck.getSelectedObjects()==null )
			return false;
		else
			return ImsVIEW_.allCheck.getSelectedObjects()!=null ? true : false;
	}
	public void checkState() {
		for( int i=0; i<getRowCount(); i++ ){
//			IJ.log( i+"title:"+getValueAt(i, COLUMN_TITLE)+" chk:"+( getValueAt( i, COLUMN_SYNCROI ) ) );
		}
	}
	
	// some junk method
	public Class getColumnClass( int col ) {
		try {
			return getValueAt( 0, col ).getClass();
		} catch( Exception e ) {
			return String.class;
		}
	}

	ArrayList<Integer> getCheckedRows() {
		ArrayList<Integer> checked = new ArrayList<Integer>();
		for( int i=0; i<getRowCount(); i++ )
			if( (Boolean) getValueAt( i, COLUMN_SYNCROI )==true )
				checked.add( i );
		return checked;
	}
	ArrayList<ImagePlus> getCheckedImp() {	// 2019-04-02 public
		ArrayList<ImagePlus> imp = new ArrayList<ImagePlus>();
		Iterator<Integer> i = getCheckedRows().iterator();
		while( i.hasNext() )
			imp.add( rowToImagePlus( i.next() ) );
		return imp;
	}
	ArrayList<ImagePlus> getAllImp() {
		ArrayList<ImagePlus> imp = new ArrayList<ImagePlus>();
		for( int i=0; i<getRowCount(); i++ )
			imp.add( rowToImagePlus( i ) );
		return imp;
	}
	ArrayList<ImagePlus> getStacksImp() {
		ArrayList<ImagePlus> imp = new ArrayList<ImagePlus>();
		for( int i=0; i<getRowCount(); i++ )
			if( rowToImagePlus( i ).getStackSize()==1 )
				imp.add( rowToImagePlus( i ) );
		return imp;
	}
	ArrayList<ImagePlus> getSlicesImp() {
		ArrayList<ImagePlus> imp = new ArrayList<ImagePlus>();
		for( int i=0; i<getRowCount(); i++ )
			if( rowToImagePlus( i ).getStackSize()==1 )
				imp.add( rowToImagePlus( i ) );
		return imp;
	}
	
	String[] getLabels(){
		String[] label = new String[getRowCount()];
		for( int i=0; i<getRowCount(); i++ )
			label[i] = (String) getValueAt( i, COLUMN_TITLE );
		return label;
	}

	public String getTitleAt( int row ) {
		return (String) getValueAt( row, COLUMN_TITLE );
	};

	

	// Event Listeners
	public void imageClosed( ImagePlus imp) { remove( imp ); }
	public void imageOpened( ImagePlus imp) { add( imp ); }
	public void imageUpdated( ImagePlus imp) { update( imp ); }
}
