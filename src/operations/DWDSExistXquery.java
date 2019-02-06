
package operations;

import java.util.LinkedList;
import java.util.StringTokenizer;

import ro.sync.ecss.extensions.api.ArgumentDescriptor;
import ro.sync.ecss.extensions.api.ArgumentsMap;
import ro.sync.ecss.extensions.api.AuthorAccess;
import ro.sync.ecss.extensions.api.AuthorOperation;
import ro.sync.ecss.extensions.api.AuthorOperationException;


public class DWDSExistXquery implements AuthorOperation{
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
	 * Argument describing chooseable attributes and possible values.
	 */
	private static final String ARGUMENT_ATTRIBUTELIST = "attribute list";

	/**
	 * Arguments.
	 */
	private static final ArgumentDescriptor[] ARGUMENTS = new ArgumentDescriptor[] {
		new ArgumentDescriptor(
				ARGUMENT_XQPATH,
				ArgumentDescriptor.TYPE_STRING,
				"Der Pfad zur XQuery-Datei zur eXist-Suche, etwa: " + 
				"{frameworkDir}/XQuerys/Element-Value-Filter.xquery"),
		new ArgumentDescriptor(
				ARGUMENT_URL,
				ArgumentDescriptor.TYPE_STRING,
				"Die URL der eXist-Datenbank, etwa: " +
				"xmldb:exist://kirk.bbaw.de:8080/exist/xmlrpc/"),
		new ArgumentDescriptor(
				ARGUMENT_COLLECTION,
				ArgumentDescriptor.TYPE_STRING,
				"Die Collection der Datenbank, etwa: " +
				"/db/dwdsdb/data"),
		new ArgumentDescriptor(
				ARGUMENT_ATTRIBUTELIST,
				ArgumentDescriptor.TYPE_STRING,
				"Liste der Elemente und ihrer möglichen Werte, einzutragen in der Form:\n" +
				"Xpath-Ausdruck:Wert1,Wert2,Wert3,...\n" +
				"Xpath-Ausdruck:Wert1,Wert2,Wert3,...\n" +
				"...\n" +
				"Der Ausdruck geht von den gefundenen Lesart-Knoten aus. Der XPath der Definition einer Lesart muss also zum Beispiel mit '/s:Definition' angesteuert werden, die Schreibweise des Artikels mit '/ancestor::s:Artikel/s:Formangabe/s:Schreibweise'\n" +
				"Werden keine Werte angegeben, generiert die Suchfunktion eine Text-Suche, anstatt eines Menüs zur Auswahl der Werte.")
	};

	/**
	 * @see ro.sync.ecss.extensions.api.AuthorOperation#doOperation(AuthorAccess, ArgumentsMap)
	 */
	public void doOperation(AuthorAccess authorAccess, ArgumentsMap args) throws AuthorOperationException {
		// Die übergebenen Argumente werden eingelesen ..
		Object xqPathArgVal = args.getArgumentValue(ARGUMENT_XQPATH);
		Object urlArgVal = args.getArgumentValue(ARGUMENT_URL);
		Object collectionArgVal = args.getArgumentValue(ARGUMENT_COLLECTION);
		Object attributeListArgVal = args.getArgumentValue(ARGUMENT_ATTRIBUTELIST);
		
		// .. und überprüft.
		if (xqPathArgVal != null &&
			xqPathArgVal instanceof String &&
			urlArgVal != null && 
			urlArgVal instanceof String &&
			collectionArgVal != null &&
			collectionArgVal instanceof String &&
			attributeListArgVal != null &&
			attributeListArgVal instanceof String) {

			// Zerlegt die Filter-Liste in ihre Bestandteile
			LinkedList<LinkedList<String>> filterList = new LinkedList<LinkedList<String>>();
			StringTokenizer attributeTokenizer = new StringTokenizer((String) attributeListArgVal, "\n");
			while (attributeTokenizer.hasMoreTokens() )
			{
				LinkedList<String> valueList = new LinkedList<String>();
				String attributeString = (String) attributeTokenizer.nextToken();
				valueList.add(attributeString.substring(0,attributeString.lastIndexOf(":")));
				StringTokenizer valueTokenizer = new StringTokenizer(attributeString.substring(attributeString.lastIndexOf(":")+1) , ",");
				while (valueTokenizer.hasMoreTokens() )
				{
					valueList.add(valueTokenizer.nextToken());
				}
				filterList.add(valueList);
			}
			
			// Dann wird der Such-Dialog geöffnet
			@SuppressWarnings("unused")
			DWDSEXistXQueryFrame attributeDialog = new DWDSEXistXQueryFrame(authorAccess, (String) xqPathArgVal, (String) urlArgVal, (String) collectionArgVal, filterList);
			
		} else {
			throw new IllegalArgumentException(
					"One or more of the argument values are not declared, they are: " + 
					"url - " + urlArgVal +
					", collection - " +	collectionArgVal + 
					", attribute list - " + attributeListArgVal);
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
		return "Öffnet einen Dialog, in dem anhand eines von mehreren Attributen und einem von dessen möglichen Werten, Dateien aufgelistet werden.";
	}
}
