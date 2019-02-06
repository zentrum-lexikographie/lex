
package operations;

import java.util.HashMap;
import java.util.StringTokenizer;

import ro.sync.ecss.extensions.api.ArgumentDescriptor;
import ro.sync.ecss.extensions.api.ArgumentsMap;
import ro.sync.ecss.extensions.api.AuthorAccess;
import ro.sync.ecss.extensions.api.AuthorOperation;
import ro.sync.ecss.extensions.api.AuthorOperationException;


public class CreateExistDocumentXquery implements AuthorOperation{
	/**
	 * Argument describing the URL.
	 */
	private static final String ARGUMENT_XQ0PATH = "Collection - xQuery";
	/**
	 * Argument describing the URL.
	 */
	private static final String ARGUMENT_XQ1PATH = "ID - Validation - xQuery";
	/**
	 * Argument describing the URL.
	 */
	private static final String ARGUMENT_XQ2PATH = "File - Creation - XQuery";
	
	/**
	 * Argument describing the URL.
	 */
	private static final String ARGUMENT_URL = "URL";

	/**
	 * Argument describing the URL.
	 */
	private static final String ARGUMENT_COLLECTION = "Collection";
	
	/**
	 * Argument describing chooseable attributes and possible values.
	 */
	private static final String ARGUMENT_TEMPLATELIST = "Template - list";

	/**
	 * Arguments.
	 */
	private static final ArgumentDescriptor[] ARGUMENTS = new ArgumentDescriptor[] {
		new ArgumentDescriptor(
				ARGUMENT_XQ0PATH,
				ArgumentDescriptor.TYPE_STRING,
				"Der Pfad zur XQuery-Datei für die Überprüfung der Existenz einer Collection auf dem eXist-Server, etwa: " + 
				"${frameworkDir}/scripts/checkcollection.xquery"),
		new ArgumentDescriptor(
				ARGUMENT_XQ1PATH,
				ArgumentDescriptor.TYPE_STRING,
				"Der Pfad zur XQuery-Datei für die Überprüfung der Eindeutigkeit einer ID, etwa: " + 
				"${frameworkDir}/scripts/idvalidation.xquery"),
		new ArgumentDescriptor(
				ARGUMENT_XQ2PATH,
				ArgumentDescriptor.TYPE_STRING,
				"Der Pfad zur XQuery-Datei zur Erstellung einer Datei, etwa: " + 
				"${frameworkDir}/scripts/createfile.xquery"),
		new ArgumentDescriptor(
				ARGUMENT_URL,
				ArgumentDescriptor.TYPE_STRING,
				"Die URL der eXist-Datenbank, etwa: " +
				"xmldb:exist://spock.dwds.de:8080/exist/xmlrpc/"),
		new ArgumentDescriptor(
				ARGUMENT_COLLECTION,
				ArgumentDescriptor.TYPE_STRING,
				"Die Collection der Datenbank, etwa: " +
				"/db/dwdsdb/data"),
		new ArgumentDescriptor(
				ARGUMENT_TEMPLATELIST,
				ArgumentDescriptor.TYPE_STRING,
				"Template auis denen die Datei erstellt wird, ergänzt durch die [ID] und die [SCHREIBUNG].\n" +
				"Mehrere Templates als Liste in der Form <Template-Name>:<Pfad zur Template-Datei> eintragen.")
	};

	/**
	 * @see ro.sync.ecss.extensions.api.AuthorOperation#doOperation(AuthorAccess, ArgumentsMap)
	 */
	@SuppressWarnings("unused")
	public void doOperation(AuthorAccess authorAccess, ArgumentsMap args) throws AuthorOperationException {
		// Die übergebenen Argumente werden eingelesen ..
		Object xq0PathArgVal = args.getArgumentValue(ARGUMENT_XQ0PATH);
		Object xq1PathArgVal = args.getArgumentValue(ARGUMENT_XQ1PATH);
		Object xq2PathArgVal = args.getArgumentValue(ARGUMENT_XQ2PATH);
		Object urlArgVal = args.getArgumentValue(ARGUMENT_URL);
		Object collectionArgVal = args.getArgumentValue(ARGUMENT_COLLECTION);
		Object templateListArgVal = args.getArgumentValue(ARGUMENT_TEMPLATELIST);
		
		// .. und überprüft.
		if (xq0PathArgVal != null &&
			xq0PathArgVal instanceof String &&
			xq1PathArgVal != null &&
			xq1PathArgVal instanceof String &&
			xq2PathArgVal != null &&
			xq2PathArgVal instanceof String &&
			urlArgVal != null && 
			urlArgVal instanceof String &&
			collectionArgVal != null &&
			collectionArgVal instanceof String &&
			templateListArgVal != null &&
			templateListArgVal instanceof String) {
			
			// Zerlegt die Filter-Liste in ihre Bestandteile
			HashMap<String, String> templateList = new HashMap<String, String>();
			StringTokenizer templateTokenizer = new StringTokenizer((String) templateListArgVal, "\n");
			while (templateTokenizer.hasMoreTokens() )
			{
				String templateString = (String) templateTokenizer.nextToken();
				templateList.put(templateString.substring(0,templateString.indexOf(":")),
								 templateString.substring(templateString.indexOf(":")+1));
			}

			// Dann wird der Such-Dialog geöfnet
			CreateEXistDocumentXQueryDialog creationDialog = new CreateEXistDocumentXQueryDialog(authorAccess, (String) xq0PathArgVal, (String) xq1PathArgVal, (String) xq2PathArgVal, (String) urlArgVal, (String) collectionArgVal, templateList);
			
		} else {
			throw new IllegalArgumentException(
					"One or more of the argument values are not declared, they are: " +
					"xquery0 - " + xq0PathArgVal +
					"xquery1 - " + xq1PathArgVal +
					"xquery2 - " + xq2PathArgVal +
					"url - " + urlArgVal +
					", collection - " +	collectionArgVal + 
					", template - " + templateListArgVal);
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
