import ij.IJ;
import ij.Prefs;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JPanel;

/**
 * list recently selected items.
 * @author Koutaro Abe ( kabe@cris.hokudai.ac.jp )
 * @version 11-Mar-2010
 */
public class RecentlyUsed {

	static final String empty = "---";
	private int historyNum;
	private String preferenceKey;
	private ArrayList<String> list;
	JButton[] buttons;
	
	RecentlyUsed( String prefKey, int itemsCount ) {
		if( itemsCount<1 || prefKey.length()<1 )
			return;
		preferenceKey = prefKey;
		historyNum = itemsCount;
		list = new ArrayList<String>();
		load();
		buttons = new JButton[historyNum];
	}
	RecentlyUsed( String key ) { this( key, 3 ); }

	int size() { return list.size(); }
	String key() { return preferenceKey; }
	ArrayList<String> list() { return list; }
	String get( int i ) {
		if( i<0 || i>=historyNum )
			return null;
		return ( i>=size() ) ? empty : list.get( i );
	}

	/** load list from IJ_Prefs.txt */
	void load() {
		list.clear();
		for( int i=0; i<historyNum; i++ )
			list.add( Prefs.get( preferenceKey+i, empty ) );
	}

	/** save current list to IJ_Prefs.txt */
	void save() {
		for( int i=0; i<historyNum; i++ )
			Prefs.set( preferenceKey+i, get(i) );
	}

	/** add an item at the TOP of list, and save */
	void add( String s ) {
		if( s.equals( empty ) ){
			IJ.log( "empty mark");
			return; }
		if( list.contains( s ) )
			list.remove( s );
		list.add( 0, s );
		if( list.size()>historyNum )
			list.remove( historyNum );
		updateButtons();
		save();
	}
	void debug() {
		for( int i=0; i<size(); i++ )
			IJ.log( "list["+i+"]="+list.get(i) );
	}

	/** add listed items as buttons */
	void addButtons( JPanel panel, ActionListener parent ) {
 		for( int i=0; i<historyNum; i++ ) {
			buttons[i] = new JButton( get( i ) );
			buttons[i].setMaximumSize( new Dimension( Short.MAX_VALUE, buttons[i].getMaximumSize().height ) );
			buttons[i].setToolTipText( tooltip( i ) );
			buttons[i].setBackground( Color.LIGHT_GRAY );
			buttons[i].setForeground( Color.DARK_GRAY );
			buttons[i].addActionListener( parent );
			buttons[i].addKeyListener( IJ.getInstance() );
			panel.add( buttons[i] );
		}
	}

	/** update all buttons */
	void updateButtons() {
 		for( int i=0; i<historyNum; i++ ) {
  			buttons[i].setText( get( i ) );
 			buttons[i].setToolTipText( tooltip( i ) );
		}
	}
	String tooltip( int i ) {
		return "Recently used: "+(i+1);
	}
}
