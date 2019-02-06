
package operations;

import ro.sync.ecss.extensions.api.ArgumentDescriptor;
import ro.sync.ecss.extensions.api.ArgumentsMap;
import ro.sync.ecss.extensions.api.AuthorAccess;
import ro.sync.ecss.extensions.api.AuthorOperation;
import ro.sync.ecss.extensions.api.AuthorOperationException;


public class DeleteEXistDocumentXquery implements AuthorOperation{
	/**
	 * Argument describing the URL.
	 */
	private static final String ARGUMENT_XQPATH = "File - Deletion - XQuery";
	
	/**
	 * Argument describing the URL.
	 */
	private static final String ARGUMENT_URL = "URL";

	/**
	 * Arguments.
	 */
	private static final ArgumentDescriptor[] ARGUMENTS = new ArgumentDescriptor[] {
		new ArgumentDescriptor(
				ARGUMENT_XQPATH,
				ArgumentDescriptor.TYPE_STRING,
				"Der Pfad zur XQuery-Datei zur Löschung einer Dateia aus eXist, etwa: " + 
				"${frameworkDir}/scripts/deletefile.xquery"),
		new ArgumentDescriptor(
				ARGUMENT_URL,
				ArgumentDescriptor.TYPE_STRING,
				"Die URL der eXist-Datenbank, etwa: " +
				"xmldb:exist://sock.dwds.de:8080/exist/xmlrpc")
	};

	/**
	 * @see ro.sync.ecss.extensions.api.AuthorOperation#doOperation(AuthorAccess, ArgumentsMap)
	 */
	public void doOperation(AuthorAccess authorAccess, ArgumentsMap args) throws AuthorOperationException {
		// Die übergebenen Argumente werden eingelesen ..
		Object xqPathArgVal = args.getArgumentValue(ARGUMENT_XQPATH);
		Object urlArgVal = args.getArgumentValue(ARGUMENT_URL);
		
		// .. und überprüft.
		if (xqPathArgVal != null &&
			xqPathArgVal instanceof String &&
			urlArgVal != null) {

			// Dann wird der Such-Dialog geöfnet
			@SuppressWarnings("unused")
			DeleteEXistXDocumentXQueryDialog creationDialog = new DeleteEXistXDocumentXQueryDialog(authorAccess, (String) xqPathArgVal, (String) urlArgVal);
			
		} else {
			throw new IllegalArgumentException(
					"One or more of the argument values are not declared, they are: " +
					"File - Deletion - XQuery - " + xqPathArgVal + 
					"URL - " + urlArgVal);
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
		return "Öffnet einen Dialog zur Generierung einer Datei auf einem eXist-Server.";
	}
}
