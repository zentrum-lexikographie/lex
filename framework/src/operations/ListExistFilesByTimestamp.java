// /usr/local/Oxygen XML Editor 15.1/frameworks/DWDSoX/Extension/DWDSoX.jar

package operations;

import java.util.LinkedList;
import java.util.StringTokenizer;

import ro.sync.ecss.extensions.api.ArgumentDescriptor;
import ro.sync.ecss.extensions.api.ArgumentsMap;
import ro.sync.ecss.extensions.api.AuthorAccess;
import ro.sync.ecss.extensions.api.AuthorOperation;
import ro.sync.ecss.extensions.api.AuthorOperationException;


public class ListExistFilesByTimestamp implements AuthorOperation{
	/**
	 * Argument describing the search filters.
	 */
	private static final String ARGUMENT_NODERESTRICTIONS = "timestamp nodes";
	/**
	 * Argument describing the search filters.
	 */
	private static final String ARGUMENT_FILTERS = "search filters";
	/**
	 * Argument describing the path of the xquery.
	 */
	private static final String ARGUMENT_XQPATH = "xquery path";
	
	/**
	 * Argument describing the URL.
	 */
	private static final String ARGUMENT_URL = "URL";

	/**
	 * Argument describing the collection.
	 */
	private static final String ARGUMENT_COLLECTION = "collection";
	
	/**
	 * Arguments.
	 */
	private static final ArgumentDescriptor[] ARGUMENTS = new ArgumentDescriptor[] {
		new ArgumentDescriptor(
				ARGUMENT_FILTERS,
				ArgumentDescriptor.TYPE_STRING,
				"Liste der Attributwerte nach denen die Suchergebnisse gefiltert werden sollen:\n" +
					"Xpath-Ausdruck:Wert1,Wert2,Wert3,...\n" +
					"Xpath-Ausdruck:Wert1,Wert2,Wert3,...\n" +
					"...\n" +
					"Der Ausdruck geht von den gefundenen Lesart-Knoten aus. Der XPath der Definition einer Lesart muss also zum Beispiel mit '/s:Definition' angesteuert werden, die Schreibweise des Artikels mit '/ancestor::s:Artikel/s:Formangabe/s:Schreibweise'\n" +
					"Werden keine Werte angegeben, generiert die Suchfunktion eine Text-Suche, anstatt eines Menüs zur Auswahl der Werte."),
		new ArgumentDescriptor(
				ARGUMENT_NODERESTRICTIONS,
				ArgumentDescriptor.TYPE_STRING,
				"Liste der Knoten, in denen nach Zeitstempeln gesucht werden soll.: " + 
					"${frameworkDir}/XQueries/timestampsearch.xquery" + 
					"wird kein Knoten angegeben, werden alle Knoten durchsucht."),
		new ArgumentDescriptor(
				ARGUMENT_XQPATH,
				ArgumentDescriptor.TYPE_STRING,
				"Der Pfad zur XQuery-Datei, etwa: " + 
					"${frameworkDir}/XQueries/timestampsearch.xquery"),
		new ArgumentDescriptor(
				ARGUMENT_URL,
				ArgumentDescriptor.TYPE_STRING,
				"Die URL der eXist-Datenbank, etwa: " +
					"xmldb:exist://kira.bbaw.de:8080/exist/xmlrpc"),
		new ArgumentDescriptor(
				ARGUMENT_COLLECTION,
				ArgumentDescriptor.TYPE_STRING,
				"Die Collection der Datenbank, etwa: " +
					"/db/dwdswb/data")
	};

	/**
	 * @see ro.sync.ecss.extensions.api.AuthorOperation#doOperation(AuthorAccess, ArgumentsMap)
	 */
	public void doOperation(AuthorAccess authorAccess, ArgumentsMap args) throws AuthorOperationException {
		// Die ï¿½bergebenen Argumente werden eingelesen ..
		Object xqPathArgVal = args.getArgumentValue(ARGUMENT_XQPATH);
		Object urlArgVal = args.getArgumentValue(ARGUMENT_URL);
		Object collectionArgVal = args.getArgumentValue(ARGUMENT_COLLECTION);
		Object nodeRestrictions = args.getArgumentValue(ARGUMENT_NODERESTRICTIONS);
		Object filters = args.getArgumentValue(ARGUMENT_FILTERS);
		
		// .. und überprüft.
		if (xqPathArgVal != null &&
			xqPathArgVal instanceof String &&
			urlArgVal != null && 
			urlArgVal instanceof String &&
			collectionArgVal != null &&
			collectionArgVal instanceof String &&
			nodeRestrictions != null &&
			nodeRestrictions instanceof String &&
			filters != null &&
			filters instanceof String) {
			
			LinkedList<String> nodeRestrictionList = new LinkedList<String>();
			StringTokenizer nodeTokenizer = new StringTokenizer((String) nodeRestrictions, "\n");
			while (nodeTokenizer.hasMoreTokens()) {
				nodeRestrictionList.add(nodeTokenizer.nextToken());
			}

			// Zerlegt die Filter-Liste in ihre Bestandteile
			LinkedList<LinkedList<String>> filtersList = new LinkedList<LinkedList<String>>();
			StringTokenizer filtersTokenizer = new StringTokenizer((String) filters, "\n");
			while (filtersTokenizer.hasMoreTokens() )
			{
				LinkedList<String> valueList = new LinkedList<String>();
				String attributeString = (String) filtersTokenizer.nextToken();
				valueList.add(attributeString.substring(0,attributeString.lastIndexOf(":")));
				StringTokenizer valueTokenizer = new StringTokenizer(attributeString.substring(attributeString.lastIndexOf(":")+1) , ",");
				while (valueTokenizer.hasMoreTokens() )
				{
					valueList.add(valueTokenizer.nextToken());
				}
				filtersList.add(valueList);
			}
			
			// Dann wird der Such-Dialog geÃ¶ffnet
			@SuppressWarnings("unused")
			ListExistFilesByTimestampFrame attributeDialog = new ListExistFilesByTimestampFrame(authorAccess, nodeRestrictionList, filtersList, (String) xqPathArgVal, (String) urlArgVal, (String) collectionArgVal);
			
		} else {
			throw new IllegalArgumentException(
					"One or more of the argument values are not declared, they are: " + 
					"url - " + urlArgVal +
					", collection - " +	collectionArgVal +
					", node restrictions - " +	nodeRestrictions +
					", xpath - " +	xqPathArgVal);
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
		return "Öffnet einen Dialog, in welchem ein Attribut via XQuery-Abfrage nach einem Element-Wert aus einer eXist-Collection ausgewÃ¤hlt werden kann. Das Attribut wird dann an Cursor-Position eingefügt.";
	}
}
