package impl.org.controlsfx.skin;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;

import org.controlsfx.control.spreadsheet.SpreadsheetCell;
import org.controlsfx.control.spreadsheet.SpreadsheetCellEditor;
import org.controlsfx.control.spreadsheet.SpreadsheetView;

public class SpreadsheetCellEditorImpl<T>{

	/***************************************************************************
	 * * Protected/Private Fields * *
	 **************************************************************************/

	// transient properties - these fields will change based on the current
	// cell being edited.
	private SpreadsheetCell<T> modelCell;
	private SpreadsheetCellImpl<T> viewCell;

	// private internal fields
	private SpreadsheetEditor spreadsheetEditor;
	private InvalidationListener editorListener;
	private InvalidationListener il;
	private boolean editing = false;
	private SpreadsheetView spreadsheetView;
	private SpreadsheetCellEditor<T> spreadsheetCellEditor;
    private SpreadsheetCellImpl<?> lastHover = null;

	/***************************************************************************
	 * * Constructor * *
	 **************************************************************************/

	/**
	 * Construct the SpreadsheetCellEditor.
	 */
	public SpreadsheetCellEditorImpl() {
		this.spreadsheetEditor = new SpreadsheetEditor();
	}

	/***************************************************************************
	 * * Public Methods * *
	 **************************************************************************/
	/**
	 * Update the internal {@link SpreadsheetCell}.
	 * @param cell
	 */
	public void updateDataCell(SpreadsheetCell<T> cell) {
		this.modelCell = cell;
	}

	/**
	 * Update the internal {@link SpreadsheetCellImpl}
	 * @param cell
	 */
	public void updateSpreadsheetCell(SpreadsheetCellImpl<T> cell) {
		this.viewCell = cell;
	}

	/**
	 * Update the SpreadsheetView
	 * @param spreadsheet
	 */
	public void updateSpreadsheetView(SpreadsheetView spreadsheet) {
		this.spreadsheetView = spreadsheet;
	}
	/**
	 * Update the SpreadsheetCellEditor
	 * @param spreadsheetCellEditor
	 */
	public void updateSpreadsheetCellEditor(final SpreadsheetCellEditor<T> spreadsheetCellEditor) {
		this.spreadsheetCellEditor = spreadsheetCellEditor;
	}
    
    public SpreadsheetCellImpl<?> getLastHover() {
    	return lastHover;
    }
    
    public void setLastHover(SpreadsheetCellImpl<?> lastHover) {
    	this.lastHover = lastHover;
    }
    
	/**
	 * Whenever you want to stop the edition, you call that method.<br/>
	 * True means you're trying to commit the value, then {@link #validateEdit()}
	 * will be called in order to verify that the value is correct.<br/>
	 * False means you're trying to cancel the value and it will be follow by {@link #end()}.<br/>
	 * See SpreadsheetCellEditor description
	 * @param b true means commit, false means cancel
	 */
	public void endEdit(boolean b){
		if(b){
			T value = spreadsheetCellEditor.validateEdit();
			if(value != null && viewCell != null){
				modelCell.setItem(value);
				viewCell.commitEdit(modelCell);
				end();
				spreadsheetCellEditor.end();
			}
		}else{
			viewCell.cancelEdit();
			end();
			spreadsheetCellEditor.end();
		}
	}


	/**
	 * Return if this editor is currently being used.
	 * @return if this editor is being used.
	 */
	public boolean isEditing() {
		return editing;
	}

	public SpreadsheetCell<T> getModelCell() {
		return modelCell;
	}

	/***************************************************************************
	 * * Protected/Private Methods * *
	 **************************************************************************/
	void startEdit() {
		editing = true;
		spreadsheetEditor.startEdit();

		// If the SpreadsheetCell is deselected, we commit.
		// Sometimes, when you you touch the scrollBar when editing,
		// this is called way
		// too late and the SpreadsheetCell is null, so we need to be
		// careful.
		il = new InvalidationListener() {
			@Override
			public void invalidated(Observable observable) {
				endEdit(false);
			}
		};

		viewCell.selectedProperty().addListener(il);

		// In ANY case, we stop when something move in scrollBar Vertical
		editorListener = new InvalidationListener() {
			@Override
			public void invalidated(Observable arg0) {
				endEdit(false);
			}
		};
		spreadsheetView.getSpreadsheetSkin().getVBar().valueProperty().addListener(editorListener);
		//FIXME We need to REALLY find a way to stop edition when anything happen
		// This is one way but it will need further investigation
		spreadsheetView.disabledProperty().addListener(editorListener);

		//Then we call the user editor in order for it to be ready
		spreadsheetCellEditor.startEdit();

		viewCell.setGraphic(spreadsheetCellEditor.getEditor());
	}


	private void end() {
		editing = false;
		spreadsheetEditor.end();
		if (viewCell != null) {
			viewCell.selectedProperty().removeListener(il);
		}
		il = null;

		spreadsheetView.getSpreadsheetSkin().getVBar().valueProperty().removeListener(editorListener);
		spreadsheetView.disabledProperty().removeListener(editorListener);
		editorListener = null;
		this.modelCell = null;
		this.viewCell = null;
	}


	private class SpreadsheetEditor {

		/***********************************************************************
		 * * Private Fields * *
		 **********************************************************************/
		private SpreadsheetRowImpl original;
		private boolean isMoved;

		private int getCellCount() {
			return spreadsheetView.getSpreadsheetSkin().getCellsSize();
		}

		private boolean addCell(SpreadsheetCellImpl<?> cell){
			SpreadsheetRowImpl temp = spreadsheetView.getSpreadsheetSkin().getRow(getCellCount()-1-spreadsheetView.getFixedRows().size());
			if(temp != null){
				temp.addCell(cell);
				return true;
			}
			return false;
		}
		/***********************************************************************
		 * * Public Methods * *
		 **********************************************************************/

		/**
		 * In case the cell is spanning in rows. We want the cell to be fully
		 * accessible so we need to remove it from its tableRow and add it to the
		 * last row possible. Then we translate the cell so that it's invisible for
		 * the user.
		 */
		public void startEdit() {
			// Case when RowSpan if larger and we're not on the last row
			if (modelCell != null && modelCell.getRowSpan() > 1
					&& modelCell.getRow() != getCellCount() - 1) {
				original = (SpreadsheetRowImpl) viewCell.getTableRow();

				final double temp = viewCell.getLocalToSceneTransform().getTy();
				isMoved = addCell(viewCell);
				if (isMoved) {
					viewCell.setTranslateY(temp
							- viewCell.getLocalToSceneTransform().getTy());
					original.putFixedColumnToBack();
				}
			}
		}

		/**
		 * When we have finish editing. We put the cell back to its right TableRow.
		 */
		public void end() {
			if (modelCell != null && modelCell.getRowSpan() > 1) {
				viewCell.setTranslateY(0);
				if (isMoved) {
					original.addCell(viewCell);
					original.putFixedColumnToBack();
				}
			}
		}
	}
}