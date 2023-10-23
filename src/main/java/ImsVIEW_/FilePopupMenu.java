import ij.IJ;
import ij.util.StringSorter;

import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;


/**
 * Generate Pop up menu from file list in specified directory.
 * @author Koutaro Abe ( kabe@cris.hokudai.ac.jp )
 * @version 11-Mar-2010
 */
public class FilePopupMenu extends JPopupMenu {

	ArrayList<String> list;
	File f;
	String ext;
	ActionListener al;
	
	FilePopupMenu( String label, String dirPath, String extension, ActionListener listener ) {
		super( label );		
		ext = extension;
		al = listener;
		list = new ArrayList<String>();
		f = new File( dirPath );
		if( f.exists() && f.isDirectory() )
			refresh( );
	}

	/**
	 * update list of files
	 */
	void refresh( ) {
		if( !f.exists() || !f.isDirectory() )
			return;
		String[] filelist = f.list();
		if( filelist==null || filelist.length==0 )
			return;
		if( IJ.isLinux() )
			StringSorter.sort( filelist );
		list = new ArrayList<String>( Arrays.asList( filelist ) );
		int extLength = ext.length();
		removeAll();
		for( int i=0; i<list.size(); i++ ) {
//			IJ.log( i+":"+filelist[i]+","+list.get( i ) );
 			if( list.get( i ).endsWith( ext ) ) {
// 				IJ.log( "  ->added" );
				JMenuItem mi = new JMenuItem( 
						list.get( i ).substring( 0, list.get( i ).length()-extLength ),
						i+1 );
 				mi.addActionListener( al );
				add( mi );
 			}
		}
	}
	
	boolean contains( String s ) {
		return list.contains( s+ext );
	}
	void debug() {
		for( int i=0; i<list.size(); i++ )
			IJ.log( i+":"+list.get( i ) );
	}
}
