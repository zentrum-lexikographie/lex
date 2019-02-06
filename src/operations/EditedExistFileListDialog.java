package operations;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.List;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileReader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.EXistResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.XPathQueryService;

import ro.sync.ecss.extensions.api.AuthorAccess;

public class EditedExistFileListDialog extends JDialog {

	/**
	 * 
	 */
	private static final long serialVersionUID = -190895918216985737L;

	/**
	 * Dies sind die Parameter für die Fenstergröße des Dialogs.
	 */
	static int H_SIZE = 400;
	static int V_SIZE = 300;
	
	private static String XQUERY_PATH;
	private static String URI;
	private static String COLLECTION;
	
	final List descriptionList = new List();
	final TextField eingabeFeld = new TextField();
	final LinkedList<String> fileList = new LinkedList<String>();
	
	public EditedExistFileListDialog(final AuthorAccess authorAccess, String xqPath, String uri, String collection) {
		// Calls the parent telling it this dialog is modal(i.e true)
		super((Frame) authorAccess.getWorkspaceAccess().getParentFrame(), true);
		
		//save values to fields for usage in all methods
		XQUERY_PATH = xqPath; 
		URI = uri;
		COLLECTION = collection;
		
		// FÃ¼r den Dialog wird das Layout (North, South, .., Center) ausgewÃ¤hlt und der Titel gesetzt.
		setLayout(new BorderLayout());
		setTitle("Geänderte Dateien öffnen");

		// In der Mitte wird das Auswahlfeld mit den RegistereintrÃ¤gen erzeugt, ..
		descriptionList.setMultipleMode(true);
		add("Center", descriptionList);
		
		// Unten gibt es die drei KnÃ¶pfe "Alles/Nichts auswÃ¤hlen", "Ãffnen" (als Default) und "Abbrechen".
		Panel panel = new Panel();
		final JButton select = new JButton("Alles auswählen");
		select.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) {
			selectAction(select);
		}});
		panel.add(select);
		JButton open = new JButton("Öffnen");
		open.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) {
			openAction(authorAccess);
		}});
		getRootPane().setDefaultButton(open);
		panel.add(open);
		JButton close = new JButton("Schließen");
		close.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) {
			closeAction();
		}});
		panel.add(close);
		add("South", panel);

		descriptionList.removeAll();
		LinkedList<String> result = new LinkedList<String>();
		// execute xquery
		try {
			result = xQuery();
		} catch(Exception e) {
			System.err.println("error while using xQuery: ");
			e.printStackTrace();
		}

		// add specified element content to list
		DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
		for (String hit : result){
	        try {
	            DocumentBuilder builder = domFactory.newDocumentBuilder();
	            InputSource is = new InputSource();
	            is.setCharacterStream(new StringReader(hit));
	            Document doc = builder.parse(is);
	            Element node = doc.getDocumentElement();
	            Node term = node.getElementsByTagName("Schreibung").item(0);
	            Node file = node.getElementsByTagName("file").item(0);
	            
	            if(term==null || term.getTextContent().equals("")) {
	            	descriptionList.add("File");
	            }
	            else {
	            	descriptionList.add(term.getTextContent() + ": " + file.getTextContent());
	            }
	            if(file==null) {
	            	fileList.add("");
	            }
	            else {
	            	fileList.add(file.getTextContent());
	            }
	        } catch (Exception e) {
	            e.printStackTrace();
	            descriptionList.add("Error!");
	        }
		}
		
		// Die Eigenschaften des Dialogfenster werden angepasst: die GrÃ¶Ãe, der Ort in der Bildschirmmitte, die SchlieÃaktion und die Sichtbarkeit.
		setSize(H_SIZE, V_SIZE);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setVisible(true);
	}
		
    @SuppressWarnings("rawtypes")
	private LinkedList<String> xQuery() throws Exception {

        LinkedList<String> result = new LinkedList<String>();
        
        final String driver = "org.exist.xmldb.DatabaseImpl";

        // initialize database driver
        Class cl = Class.forName(driver);
        
        Database database = new DatabaseImpl();
        database.setProperty("create-database", "true");
        database = (Database) cl.newInstance();
        DatabaseManager.registerDatabase(database);
        StringBuffer buffer = new StringBuffer();
        FileReader in = new FileReader(XQUERY_PATH);
        for (int n;(n = in.read()) != -1;buffer.append((char) n));
        in.close(); 
        String xquery = buffer.toString();
        Collection col = null;
        try { 
            col = DatabaseManager.getCollection(URI + COLLECTION);
            XPathQueryService xpqs = (XPathQueryService)col.getService("XPathQueryService", "1.0");
            xpqs.setProperty("indent", "no");
            ResourceSet resources = xpqs.query(xquery);
            ResourceIterator i = resources.getIterator();
            Resource res = null;
            while(i.hasMoreResources()) {
                try {
                    res = i.nextResource();
                    result.add((String) res.getContent());
                } finally {
                    //dont forget to cleanup resources
                	((EXistResource)res).freeResources();
                }
            }
        } finally {
            //don't forget to cleanup
            if(col != null) {
                col.close();
            }
        }
		return(result);
    }
	
	public void selectAction(JButton selectButton) {
		if(selectButton.getText().equalsIgnoreCase("Alles auswÃ¤hlen")) {
			for(int i=0; i<=descriptionList.getItemCount(); i++)
				descriptionList.select(i);
			selectButton.setText("Nichts auswÃ¤hlen");
		} 
		else {
			for(int i=0; i<=descriptionList.getItemCount(); i++)
				descriptionList.deselect(i);
			selectButton.setText("Alles auswÃ¤hlen");
		}
	}
    
	public void openAction(AuthorAccess authorAccess) {
		String file = "";
		try {
			for(int index : descriptionList.getSelectedIndexes()) 
			{
				file = fileList.get(index);
				file = URI.replace("xmldb:exist://", "http://") + file;
				file = file.replace("xmlrpc/", "webdav");
				authorAccess.getWorkspaceAccess().open(new URL(file));
			}
		} catch (MalformedURLException e){
			e.printStackTrace();
			descriptionList.add("could not open " + file);
		}
	}
	
	public void closeAction(){
		dispose();
	}
}