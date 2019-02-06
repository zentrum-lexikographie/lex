package simple.documentation.framework;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.text.BadLocationException;

import ro.sync.contentcompletion.xml.CIElement;
import ro.sync.contentcompletion.xml.WhatElementsCanGoHereContext;
import ro.sync.ecss.extensions.api.AuthorAccess;
import ro.sync.ecss.extensions.api.AuthorCaretEvent;
import ro.sync.ecss.extensions.api.AuthorCaretListener;
import ro.sync.ecss.extensions.api.AuthorDocumentController;
import ro.sync.ecss.extensions.api.AuthorExtensionStateListener;
import ro.sync.ecss.extensions.api.AuthorSchemaManager;

public class DWDSoXAuthorExtensionStateListener implements
		AuthorExtensionStateListener {

	private Map<String, Object> authorExtensionActionMap = new HashMap<String, Object>(); 
	private boolean enabledStatesCanBeChanged = false;
		
	@Override
	public String getDescription() {
		return "activates/deactivates context sensitively action options in bar menu and context menu\n";
	}

    /**
     * @see ro.sync.ecss.extensions.api.AuthorExtensionStateListener#activated(ro.sync.ecss.extensions.api.AuthorAccess)
     */
	public void activated(final AuthorAccess authorAccess){

		final Map<String, Object> authorExtensionActions = authorAccess.getEditorAccess().getActionsProvider().getAuthorExtensionActions();
	    Iterator<Object> actionsIter = authorExtensionActions.values().iterator();
	    while(actionsIter.hasNext()) {
	      final AbstractAction action = (AbstractAction) actionsIter.next();
	      action.addPropertyChangeListener(new PropertyChangeListener() {
	        @Override
	        public void propertyChange(PropertyChangeEvent evt) {
	          if("enabled".equals(evt.getPropertyName())){
	            if(!enabledStatesCanBeChanged) {
	              //Enabled states of actions cannot be changed now, probably the Oxygen code tries to
	              //enable or disable the action based on the XPath condition
	              //So set it back as it was before.
	              try {
	                enabledStatesCanBeChanged = true;
	                action.setEnabled((Boolean) evt.getOldValue());
	              } finally {
	                enabledStatesCanBeChanged = false;
	              }
	            }
	          }
	        }
	      });
	    }
	    
		//get access to author actions
		authorExtensionActionMap = authorAccess.getEditorAccess().getActionsProvider().getAuthorExtensionActions();
			
		//Add a caret listener to enable/disable extension actions
		authorAccess.getEditorAccess().addAuthorCaretListener(new AuthorCaretListener() {
	    	
			
		  @Override
	      public void caretMoved(AuthorCaretEvent caretEvent) {
			  //get access to document and schema
		  	  AuthorDocumentController docController = authorAccess.getDocumentController();
		  	  AuthorSchemaManager schemaManager = docController.getAuthorSchemaManager();

	  		 //get current caret context
    		  int offset = authorAccess.getEditorAccess().getCaretOffset();
    		  try {
    			  //get list of possible elements at current caret position
    			  WhatElementsCanGoHereContext goHereContext = schemaManager.createWhatElementsCanGoHereContext(offset); 
    			  if(goHereContext==null) return;
    			  List<CIElement> possibleChildren = schemaManager.whatElementsCanGoHere(goHereContext); 
    			  if(possibleChildren==null) possibleChildren = new LinkedList<CIElement>();

	    		  //iterate over all possible author actions
actionFor: 	  	  for(Map.Entry<String, Object> authorExtensionAction : authorExtensionActionMap.entrySet()) {
			    	  String actionName = authorExtensionAction.getKey();
			    	  // author actions can be tagged to be ignored by context evaluation
		    		  if(actionName.contains("[nocontextvalidation]")) continue;
		    		  AbstractAction actionValue = (AbstractAction) authorExtensionAction.getValue();
		    		  //String actionDesc = (String) actionValue.getValue(Action.SHORT_DESCRIPTION);
		    		  
		    		  try {
		                  enabledStatesCanBeChanged = true;
			    		  //at start unable every single action
			    		  actionValue.setEnabled(false);
			    		  
			    		  String[] tokenized = actionName.split(" ");
		
		    			  //if list contains actual action enable action
		    			  for(CIElement children : possibleChildren) {
		    				  //reenable the action if the tag name appears as token in the action ID
		    				  for(String token : tokenized) {
		    					  if(token.equals(children.getQName())) {
			    					  actionValue.setEnabled(true);
			    					  continue actionFor;
		    					  }
		    				  }
		    			  }
		    		  }
		    		  finally {
		                  enabledStatesCanBeChanged = false;
		    		  }
	    		  }
	    	  } catch (BadLocationException e) {
	    		  System.err.println("Exception in CaretListener.caretMoved - Extension: bad location");
	    		  e.printStackTrace();
	    	  }
	      }
	    });
		
        /*authorAccess.getWorkspaceAccess().getEditorAccess(authorAccess.getEditorAccess().getEditorLocation()).addEditorListener(new WSEditorListener() {
            public boolean  editorAboutToBeSavedVeto(int operationType) {
            	
            	//set attribute in node to current time
                try {
					SwingUtilities.invokeAndWait(new Runnable() {
					    public void run() {
			            	//get current date
			            	Date actualDate = new Date();
			            	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			            	String dateString = "0000-00-00";
							dateString = dateFormat.format(actualDate);
			            	
			            	//get node
			            	AuthorElement node = authorAccess.getDocumentController().getAuthorDocumentNode().getRootElement().getElementsByLocalName("Artikel")[0];
			            	
			            	//set attribute of node to current time
					    	authorAccess.getDocumentController().setAttribute("Modifikation", new AttrValue(dateString), node);
					    }
					 });
				} catch (InvocationTargetException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
	  			return(true);
            }
           });*/
	}

	@Override
	public void deactivated(AuthorAccess authorAccess) {
	}
	
	public Set<String> getAuthorActionSet() {
		return(authorExtensionActionMap.keySet());
	}
}