package simple.documentation.framework;

import java.util.List;
import java.util.Map;

import javax.swing.text.BadLocationException;

import ro.sync.ecss.extensions.api.AuthorAccess;
import ro.sync.ecss.extensions.api.AuthorSchemaAwareEditingHandler;
import ro.sync.ecss.extensions.api.InvalidEditException;
import ro.sync.ecss.extensions.api.content.OffsetInformation;
import ro.sync.ecss.extensions.api.node.AuthorDocumentFragment;
import ro.sync.ecss.extensions.api.node.AuthorNode;

public class DWDSoXAuthorSchemaAwareEditingHandler implements
		AuthorSchemaAwareEditingHandler {

	// set level-attribute for Hauptlesart
	@Override
	public AuthorDocumentFragment handleCreateDocumentFragment(int startOffset,
			int endOffset, int id, AuthorAccess authorAccess) throws BadLocationException {
		return null;
	}
	

	@Override
	//restrict backspace/del - deletion of nodes that cannot be restored by author
	public boolean handleDelete(int offset, int deleteType/*(DEL or BACKSPACE*/, AuthorAccess authorAccess,
			boolean wordLevel) throws InvalidEditException {
		try {
			OffsetInformation offsetInfo = authorAccess.getDocumentController().getContentInformationAtOffset(offset);
			OffsetInformation preoffsetInfo = authorAccess.getDocumentController().getContentInformationAtOffset(offset-1);
			
			//in case of deletion of a character ignore handle
			//for BACKSPACE
			if(deleteType==AuthorSchemaAwareEditingHandler.ACTION_ID_BACKSPACE) {
				if(preoffsetInfo.getPositionType()==OffsetInformation.IN_CONTENT) {
					authorAccess.getDocumentController().delete(offset-1, offset-1);
					return(true);
				}
			}
			//for DEL
			else {
				if(offsetInfo.getPositionType()==OffsetInformation.IN_CONTENT) {
					authorAccess.getDocumentController().delete(offset, offset);
					return(true);
				}
			}
			
			//find offset node
			AuthorNode offsetNode;
			//for BACKSPACE-deletion
			if(deleteType==AuthorSchemaAwareEditingHandler.ACTION_ID_BACKSPACE) {
				offsetNode = offsetInfo.getNodeForOffset();
			}
			//for DEL-deletion
			else {
				offsetNode = offsetInfo.getNodeForOffset();
			}
			//if node can be inserted again via author action: delete
			Map<String, Object> authorExtensionActionMap = authorAccess.getEditorAccess().getActionsProvider().getAuthorExtensionActions();
			for(String action : authorExtensionActionMap.keySet()) {
        		if(action.contains(offsetNode.getName())) authorAccess.getDocumentController().deleteNode(offsetNode); 	
			}			
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
		return(true);
	}

	@Override
	public boolean handleDeleteElementTags(AuthorNode arg0, AuthorAccess arg1)
			throws InvalidEditException {
		return false;
	}

	@Override
	//restrict deletion of nodes that cannot be restored by author
	public boolean handleDeleteNodes(AuthorNode[] node, int deleteType /*outline deletion or drag and drop*/,
			AuthorAccess authorAccess) throws InvalidEditException {
		
		//if node can be inserted again by author via author action: delete
		Map<String, Object> authorExtensionActionMap = authorAccess.getEditorAccess().getActionsProvider().getAuthorExtensionActions();
		if(authorExtensionActionMap.keySet().contains(node[0].getName())) {
			return(false);
		}
		//if node cannot be inserted again by author: do not delete
		return true;
	}

	@Override
	//restrict deletion of nodes by selection, only allow deletion of text
	public boolean handleDeleteSelection(int start_offset, int end_offset, int delete_type,
			AuthorAccess authorAccess) throws InvalidEditException {
		try {
			OffsetInformation start_offsetInfo = authorAccess.getDocumentController().getContentInformationAtOffset(start_offset);
			OffsetInformation end_offsetInfo = authorAccess.getDocumentController().getContentInformationAtOffset(end_offset);
			
			if((start_offsetInfo.getPositionType()==OffsetInformation.IN_CONTENT) &&
			   (end_offsetInfo.getPositionType()==OffsetInformation.IN_CONTENT) &&
			   (start_offsetInfo.getNodeForOffset()==end_offsetInfo.getNodeForOffset()))
				return false;
		} catch (BadLocationException e) {
			e.printStackTrace();
		}

		return true;
	}

	@Override
	public boolean handleJoinElements(AuthorNode arg0, List<AuthorNode> arg1,
			AuthorAccess arg2) throws InvalidEditException {
		return false;
	}

	@Override
	public boolean handlePasteFragment(int arg0, AuthorDocumentFragment[] arg1,
			int arg2, AuthorAccess arg3) throws InvalidEditException {
		return false;
	}

	@Override
	public boolean handleTyping(int arg0, char arg1, AuthorAccess arg2)
			throws InvalidEditException {
		return false;
	}
	
	// Was zur Hölle soll das sein?
	public boolean handleTypingFallback(int integer, char character, AuthorAccess authoraccess) {
		return(false);
	}

}
