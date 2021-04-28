import ij.IJ;
import java.awt.Color;
import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/**
 * View model for ImageList.
 * @author kabe@cris.hokudai.ac.jp
 * @version 03-Sep-2020
 */

@SuppressWarnings("serial")
public class ImageListJTable extends JTable {
	ImageList list;

	ImageListJTable ( ImageList imageList ){
		super( imageList );
		list = imageList;
		if (IJ.isLinux()) setBackground(Color.white);
		setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
		setAutoResizeMode( JTable.AUTO_RESIZE_OFF );
		resizeColumn( imageList.COLUMN_SYNCROI );
//		setCellAlignment( imageList.COLUMN_DIR, SwingConstants.LEFT);
		setCellAlignment( imageList.COLUMN_DIR, JLabel.TRAILING);
	}

	public void resizeColumn( int column ){
		if( getRowCount()==0 ) {
			list.addRow( new Object[]{ "", "Sync  ", "TITLE567890123456", "DIR3456789012345678901234567890123456789012345678901234567890123", "SLIC" } );
			for( int i=0; i<list.columns.length; i++ )
				resizeColumn0( i );
			list.removeRow( 0 );
		} else {
			resizeColumn0( column );
		}
	}

	void resizeColumn0( int column ) {
 		TableColumnModel columnModel = this.getColumnModel();
		int maxwidth = 0;
		for( int row=0; row<this.getRowCount(); row++ ){
			TableCellRenderer rend = this.getCellRenderer( row, column );
			Component comp = rend.getTableCellRendererComponent(
					this,
					this.getValueAt( row, column ),
					false, false,
					row, column );
			maxwidth = Math.max( comp.getPreferredSize().width, maxwidth );
		}
		TableColumn tcol = columnModel.getColumn( column );
		if( list.visible[column] ) {
			tcol.setMaxWidth( maxwidth*2 );		
			tcol.setPreferredWidth( maxwidth+1 );
			this.revalidate();
		} else {
			tcol.setWidth( 0 );
			tcol.setMinWidth( 0 );
			tcol.setMaxWidth( 0 );
		}
	};

/**
 * 	
 * @param column
 * @param alignment : JLabel.RIGHT, JLabel.Left, etc
 */
	public void setCellAlignment(int column, int alignment) {
		DefaultTableCellRenderer r = new DefaultTableCellRenderer();
		r.setHorizontalAlignment(alignment);
		this.getColumnModel().getColumn(column).setCellRenderer(r);
	}
}
