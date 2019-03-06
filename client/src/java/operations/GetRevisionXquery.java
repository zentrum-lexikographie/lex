
package operations;

import ro.sync.ecss.extensions.api.ArgumentDescriptor;
import ro.sync.ecss.extensions.api.ArgumentsMap;
import ro.sync.ecss.extensions.api.AuthorAccess;
import ro.sync.ecss.extensions.api.AuthorOperation;
import ro.sync.ecss.extensions.api.AuthorOperationException;


public class GetRevisionXquery implements AuthorOperation{
	/**
	 * Argument describing the URL.
	 */
	private static final String ARGUMENT_XQPATH_LIST = "Revision - List - XQuery";

	/**
	 * Argument describing the URL.
	 */
	private static final String ARGUMENT_XQPATH_REVISION = "Get - Revision - XQuery";
	
	/**
	 * Argument describing the URL.
	 */
	private static final String ARGUMENT_URL = "URL";

	/**
	 * Arguments.
	 */
	private static final ArgumentDescriptor[] ARGUMENTS = new ArgumentDescriptor[] {
		new ArgumentDescriptor(
				ARGUMENT_XQPATH_LIST,
				ArgumentDescriptor.TYPE_STRING,
				"Der Pfad zur XQuery-Datei zur Ermittlung aller Revisionen, etwa: " + 
				"${frameworkDir}/scripts/getrevisionlist.xquery"),
		new ArgumentDescriptor(
				ARGUMENT_XQPATH_REVISION,
				ArgumentDescriptor.TYPE_STRING,
				"Der Pfad zur XQuery-Datei zur Abfrage einer bestimmten Revision, etwa: " + 
				"${frameworkDir}/scripts/getrevision.xquery"),
		new ArgumentDescriptor(
				ARGUMENT_URL,
				ArgumentDescriptor.TYPE_STRING,
				"Die URL der eXist-Datenbank, einschließlich der Collection, etwa: " +
				"xmldb:exist://spock.dwds.de:8080/exist/xmlrpc/db/dwdswb/data")
	};

	/**
	 * @see ro.sync.ecss.extensions.api.AuthorOperation#doOperation(AuthorAccess, ArgumentsMap)
	 */
	public void doOperation(AuthorAccess authorAccess, ArgumentsMap args) throws AuthorOperationException {
		// Die übergebenen Argumente werden eingelesen ..
		Object xqPathListArgVal = args.getArgumentValue(ARGUMENT_XQPATH_LIST);
		Object xqPathRevisionArgVal = args.getArgumentValue(ARGUMENT_XQPATH_REVISION);
		Object urlArgVal = args.getArgumentValue(ARGUMENT_URL);
		
		// .. und überprüft.
		if (xqPathListArgVal != null &&
			xqPathListArgVal instanceof String &&
			xqPathRevisionArgVal != null &&
			xqPathRevisionArgVal instanceof String &&
			urlArgVal != null) {

			// Dann wird der Such-Dialog geöfnet
			@SuppressWarnings("unused")
			GetRevisionXQueryDialog creationDialog = new GetRevisionXQueryDialog(authorAccess, (String) xqPathListArgVal, (String) xqPathRevisionArgVal, (String) urlArgVal);
			
		} else {
			throw new IllegalArgumentException(
					"One or more of the argument values are not declared, they are: " +
					"Revision - List - XQuery - " + xqPathListArgVal + 
					"Get - Revision - XQuery - " + xqPathRevisionArgVal + 
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
