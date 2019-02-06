// /usr/local/Oxygen XML Editor 15.1/frameworks/DWDSoX/Extension/DWDSoX.jar

package operations;

import ro.sync.ecss.extensions.api.ArgumentDescriptor;
import ro.sync.ecss.extensions.api.ArgumentsMap;
import ro.sync.ecss.extensions.api.AuthorAccess;
import ro.sync.ecss.extensions.api.AuthorOperation;
import ro.sync.ecss.extensions.api.AuthorOperationException;


public class ListEditedExistFiles implements AuthorOperation{
	/**
	 * Argument describing the URL.
	 */
	private static final String ARGUMENT_XQPATH = "xquery-path";
	
	/**
	 * Argument describing the URL.
	 */
	private static final String ARGUMENT_URL = "URL";

	/**
	 * Argument describing the URL.
	 */
	private static final String ARGUMENT_COLLECTION = "collection";
	
	/**
	 * Arguments.
	 */
	private static final ArgumentDescriptor[] ARGUMENTS = new ArgumentDescriptor[] {
		new ArgumentDescriptor(
				ARGUMENT_XQPATH,
				ArgumentDescriptor.TYPE_STRING,
				"Der Pfad zur XQuery-Datei, etwa: " + 
				"???"),
		new ArgumentDescriptor(
				ARGUMENT_URL,
				ArgumentDescriptor.TYPE_STRING,
				"Die URL der eXist-Datenbank, etwa: " +
				"???"),
		new ArgumentDescriptor(
				ARGUMENT_COLLECTION,
				ArgumentDescriptor.TYPE_STRING,
				"Die Collection der Datenbank, etwa: " +
				"/db/dwdswb/src/")
	};

	/**
	 * @see ro.sync.ecss.extensions.api.AuthorOperation#doOperation(AuthorAccess, ArgumentsMap)
	 */
	public void doOperation(AuthorAccess authorAccess, ArgumentsMap args) throws AuthorOperationException {
		// Die ï¿½bergebenen Argumente werden eingelesen ..
		Object xqPathArgVal = args.getArgumentValue(ARGUMENT_XQPATH);
		Object urlArgVal = args.getArgumentValue(ARGUMENT_URL);
		Object collectionArgVal = args.getArgumentValue(ARGUMENT_COLLECTION);
		
		// .. und überprüft.
		if (xqPathArgVal != null &&
			xqPathArgVal instanceof String &&
			urlArgVal != null && 
			urlArgVal instanceof String &&
			collectionArgVal != null &&
			collectionArgVal instanceof String) {
			
			// Dann wird der Such-Dialog geöffnet
			@SuppressWarnings("unused")
			EditedExistFileListDialog attributeDialog = new EditedExistFileListDialog(authorAccess, (String) xqPathArgVal, (String) urlArgVal, (String) collectionArgVal);
			
		} else {
			throw new IllegalArgumentException(
					"One or more of the argument values are not declared, they are: " + 
					"url - " + urlArgVal +
					", collection - " +	collectionArgVal);
		}
	}

	/**
	 * @see ro.sync.ecss.extensions.api.AuthorOperation#getArguments()
	 */
	public ArgumentDescriptor[] getArguments() {
		return ARGUMENTS;
	}

	/**
	 * @see ro.sync.ecss.extensions.api.AuthorOperation#getDescription()
	 */
	public String getDescription() {
		return "Öffnet einen Dialog, in welchem ein Attribut via XQuery-Abfrage nach einem Element-Wert aus einer eXist-Collection ausgewählt werden kann. Das Attribut wird dann an Cursor-Position eingefügt.";
	}
}
