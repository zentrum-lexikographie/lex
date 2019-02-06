
package operations;

import java.util.LinkedList;
import java.util.StringTokenizer;

import javax.swing.text.BadLocationException;

import ro.sync.ecss.extensions.api.ArgumentDescriptor;
import ro.sync.ecss.extensions.api.ArgumentsMap;
import ro.sync.ecss.extensions.api.AuthorAccess;
import ro.sync.ecss.extensions.api.AuthorOperation;
import ro.sync.ecss.extensions.api.AuthorOperationException;
import ro.sync.ecss.extensions.api.node.AttrValue;
import ro.sync.ecss.extensions.api.node.AuthorElement;
import ro.sync.ecss.extensions.api.node.AuthorNode;


public class GenerateExclusiveID implements AuthorOperation{
	/**
	 * Argument describing the URL.
	 */
	private static final String ARGUMENT_ATTRNAME = "Name des einzufügenden Attributs für die ID";
	/**
	 * Argument describing the URL.
	 */
	private static final String ARGUMENT_BASEIDXPATH = "Attribut aus dem die Grund-ID gewonnen wird";
	/**
	 * Argument describing the URL.
	 */
	private static final String ARGUMENT_COMPAREIDXPATH = "Attribute, von deren Werte sich die ID unterscheiden muss, eins pro Zeile";

	/**
	 * Arguments.
	 */
	private static final ArgumentDescriptor[] ARGUMENTS = new ArgumentDescriptor[] {
		new ArgumentDescriptor(
				ARGUMENT_ATTRNAME,
				ArgumentDescriptor.TYPE_STRING,
				"Der Name des einzufügenden ID-Attributs, etwa: " + 
				"xml:id"),
		new ArgumentDescriptor(
				ARGUMENT_BASEIDXPATH,
				ArgumentDescriptor.TYPE_STRING,
				"Der Knoten und der Name des Attributs aus dem die ID per Suffix gebildet wird, getrennt durch ':::', etwa: " + 
				"/DWDS/Artikel:::xml:id"),
		new ArgumentDescriptor(
				ARGUMENT_COMPAREIDXPATH,
				ArgumentDescriptor.TYPE_STRING,
				"Die Knoten und Namen der Attribute mit denen die neue ID abgeglichen werden soll um Eindeutigkeit herzustellen." +
				"Pro Zeile ein Attribut angeben, getrennt duch ':::', etwa: " +
				"//Lesart:::xml:id")
	};

	/**
	 * @see ro.sync.ecss.extensions.api.AuthorOperation#doOperation(AuthorAccess, ArgumentsMap)
	 */
	public void doOperation(AuthorAccess authorAccess, ArgumentsMap args) throws AuthorOperationException {
		// Die übergebenen Argumente werden eingelesen ..
		Object attrName = args.getArgumentValue(ARGUMENT_ATTRNAME);
		Object baseIDXPath = args.getArgumentValue(ARGUMENT_BASEIDXPATH);
		Object compareIDXPath = args.getArgumentValue(ARGUMENT_COMPAREIDXPATH);
		
		// .. und überprüft.
		if (attrName != null &&
			attrName instanceof String &&
			baseIDXPath != null &&
			baseIDXPath instanceof String &&
			compareIDXPath != null &&
			compareIDXPath instanceof String) {

			//create XPath expression for base ID search
			String baseIDXPathString = (String) baseIDXPath;
			String baseIDName = baseIDXPathString.substring(baseIDXPathString.indexOf(":::")+3); 
			baseIDXPathString = baseIDXPathString.substring(0,baseIDXPathString.indexOf(":::"));
			baseIDXPathString += "[@" + baseIDName + "]";
						
			//get base ID from attribute referenced by xpath expression
			AuthorNode[] baseIDAttributes = authorAccess.getDocumentController().findNodesByXPath(baseIDXPathString, true, true, true);
			if(baseIDAttributes.length==0) {
				System.err.println("Keine Dokument-ID gefunden");
				return;
			}
			String baseID = "";
			baseID = ((AuthorElement) baseIDAttributes[0]).getAttribute(baseIDName).getValue();
			if(baseIDAttributes.length==0 || baseID==null || baseID.equals(""))
			{
				System.err.println("Keine Dokument-ID gefunden");
				return;
			}
			//change first letter E to S
			baseID = "S" + baseID.substring(1);
			
			//tokenize the list of attributes with which the id has to be compared
			LinkedList<String> compareAttributeNodeList = new LinkedList<String>();
			LinkedList<String> compareAttributeNameList = new LinkedList<String>();
			StringTokenizer attributeTokenizer = new StringTokenizer((String) compareIDXPath, "\n");
			while (attributeTokenizer.hasMoreTokens() )
			{
				String compareAttributeString = (String) attributeTokenizer.nextToken();
				compareAttributeNameList.add(compareAttributeString.substring(compareAttributeString.indexOf(":::")+3));
				compareAttributeNodeList.add(compareAttributeString.substring(0, compareAttributeString.indexOf(":::")));
			}
			
			//collect all attribute values referenced by the xpath expression, that the ID has to get compared with
			LinkedList<String> compareIDs = new LinkedList<String>();
			for(int i=0; i<1;i++) {
				String attributeNode = compareAttributeNodeList.get(i);
				String attributeName = compareAttributeNameList.get(i);
				for(AuthorNode node : authorAccess.getDocumentController().findNodesByXPath(attributeNode+"[@"+attributeName+"]", true, true, true)) {
					compareIDs.add(((AuthorElement) node).getAttribute(attributeName).getValue());
				}
			}
			
			//find id that is not already used in document
			String id;
			for(int i=0; true; i++) {
				id = baseID+"_"+i;
				if(!compareIDs.contains(id)) break;
			}
			
			//insert id at caret position
			try {
				AuthorNode caretNode = authorAccess.getDocumentController().getNodeAtOffset(authorAccess.getEditorAccess().getCaretOffset());
				authorAccess.getDocumentController().setAttribute((String) attrName, new AttrValue(id), (AuthorElement) caretNode);
			} catch (BadLocationException e) {
				System.err.println("Knoten an Cursor Position nicht gefunden");
				e.printStackTrace();
			}
			
		} else {
			throw new IllegalArgumentException(
					"One or more of the argument values are not declared, they are: " +
					"Base ID Attribute - " + baseIDXPath +
					"Compare Attributes - " + compareIDXPath);
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
