package operations;

import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JDialog;

import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.EXistResource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

import ro.sync.ecss.extensions.api.AuthorAccess;

public class CreateEXistDocumentXQueryDialog extends JDialog {

	/**
	 * 
	 */
	private static final long serialVersionUID = -190895918216985737L;

	/**
	 * Dies sind die Parameter fï¿½r die Fenstergröße des Dialogs.
	 */
	static int H_SIZE = 300;
	static int V_SIZE = 180;
		
	private static String XQUERY0_PATH;
	private static String XQUERY1_PATH;
	private static String XQUERY2_PATH;
	private static String URI;
	private static String COLLECTION;
	private static HashMap<String, String> TEMPLATE;
	private static String ID;
	private static String DATE;
	
	final JButton create = new JButton("Datei erzeugen");
	final LinkedList<String> fileList = new LinkedList<String>();
	final TextField filenameField = new TextField("", 20);
	final TextArea debug = new TextArea(); 
	final Label usernameLabel = new Label("Benutzername");
	final TextField usernameField = new TextField("", 20);
	final Label passwordLabel = new Label("Passwort");
	final TextField passwordField = new TextField("", 20);
	final Label templateLabel = new Label("Template");
	final Choice templateChoice = new Choice();

	
	public CreateEXistDocumentXQueryDialog(final AuthorAccess authorAccess, String xqPath0, String xqPath1, String xqPath2, String uri, String collection, HashMap<String, String> templateList) {
		// Calls the parent telling it this dialog is modal(i.e true)
		super((Frame) authorAccess.getWorkspaceAccess().getParentFrame(), false);
		
		//save values to fields for usage in all methods
		XQUERY0_PATH = xqPath0;
		XQUERY1_PATH = xqPath1;
		XQUERY2_PATH = xqPath2; 
		URI = uri;
		COLLECTION = collection;
		TEMPLATE = templateList;
			
		// Für den Dialog wird das Layout (North, South, .., Center) ausgewählt und der Titel gesetzt.
		setLayout(new BorderLayout());
		setTitle("Datei in eXist erzeugen");
		
		//[DEBUG]
		//add("Center", debug);
		
		// Erzeugt das Panel für die Filter-Zeilen
		final Panel inputPanel = new Panel();
		inputPanel.setLayout(new GridLayout(4,2));
		add("North", inputPanel);
		Label filenameLabel = new Label("Schreibung");
		inputPanel.add(filenameLabel);
		inputPanel.add(filenameField);
		inputPanel.add(templateLabel);
		for(String name : templateList.keySet())
			templateChoice.add(name);
		inputPanel.add(templateChoice);
		inputPanel.add(usernameLabel);
		inputPanel.add(usernameField);
	    passwordField.setEchoChar('*');
		inputPanel.add(passwordLabel);
		inputPanel.add(passwordField);
		
		// Unten gibt es die zwei Knöpfe "Datei anlegen" (Default) und "Abbrechen".
		Panel panel = new Panel();
		create.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) {
			createAction(authorAccess);
		}});
		panel.add(create);
		getRootPane().setDefaultButton(create);
		JButton cancel = new JButton("Abbrechen");
		cancel.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) {
			cancelAction();
		}});
		panel.add(cancel);
		add("South", panel);

		// Die Eigenschaften des Dialogfenster werden angepasst: die Größe, der Ort in der Bildschirmmitte, die Schließaktion und die Sichtbarkeit.
		setSize(H_SIZE, V_SIZE);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setVisible(true);
	}

	/**
	 * Bei "Datei Erstellen" wird die ID generiert und auf Eindeutigkeit geprüft und die Datei erstellt. Anschließend wird das Fenster geschlossen.
	 */
	private void createAction(AuthorAccess authorAccess){
		
		fileList.clear();
		// execute xquery
		try {
			ID = generateID(authorAccess);
			DATE = new SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().getTime());
			store(authorAccess);
		} catch(Exception e) {
			System.err.println("error while using xQuery: ");
			e.printStackTrace();
		}
		dispose();
	}
	
	
	/**
	 * Stellt die XQuery zusammen und sendet sie an den Server
	 * @return
	 * @throws Exception
	 */
    @SuppressWarnings({ "rawtypes", "unused" })
	private boolean checkCollection(String collection) throws Exception {

    	final String driver = "org.exist.xmldb.DatabaseImpl";

		// initialize database driver
        Class cl = Class.forName(driver);
        
        Database database = new DatabaseImpl();
        database.setProperty("create-database", "true");
        database = (Database) cl.newInstance();
        DatabaseManager.registerDatabase(database);
        StringBuffer buffer = new StringBuffer();
        FileReader in = new FileReader(XQUERY0_PATH);
        for (int n;(n = in.read()) != -1;buffer.append((char) n));
        in.close(); 
        String xquery = buffer.toString();
        xquery = xquery.replace("[COLLECTION]", collection);
        
        //[DEBUG]
        //debug.setText(debug.getText()+xquery);
       
		String result = "false";
        Collection col = null;
        try { 
            col = DatabaseManager.getCollection(URI + COLLECTION);
            XPathQueryService xpqs = (XPathQueryService)col.getService("XPathQueryService", "1.0");
            xpqs.setProperty("indent", "no");
        	
            ResourceSet resources = xpqs.query(xquery);
             
            //[DEBUG]
        	//debug.setText(debug.getText()+resources.getSize());
       
            ResourceIterator i = resources.getIterator();
            Resource res = null;
            while(i.hasMoreResources()) {
                try {
                    res = i.nextResource();
                    result =((String) res.getContent());
                } finally {
                    //dont forget to cleanup resources
                	((EXistResource)res).freeResources();
                }
            }
        } finally {
            //dont forget to cleanup
            if(col != null) {
                col.close();
            }
        }
    
    	return(Boolean.valueOf(result));
    }
	
	/**
	 * Stellt die XQuery zusammen und sendet sie an den Server
	 * @return
	 * @throws Exception
	 */
    @SuppressWarnings("rawtypes")
	private String generateID(AuthorAccess authorAccess) throws Exception {
    	Random r = new Random();
    	String id = "E_" + r.nextInt(9999999);
    	
    	//[DEBUG] Test for functionality of hit-response for exclusive ids
    	/*LinkedList<String> ids = new LinkedList<String>();
    	ids.add("E_1000000"); ids.add("E_1000001"); ids.add("E_1000002");
    	String id = ids.get(r.nextInt(3));*/
    	
    	final String driver = "org.exist.xmldb.DatabaseImpl";

        // initialize database driver
        Class cl = Class.forName(driver);
        
        Database database = new DatabaseImpl();
        database.setProperty("create-database", "true");
        database = (Database) cl.newInstance();
        DatabaseManager.registerDatabase(database);
        StringBuffer buffer = new StringBuffer();
        FileReader in = new FileReader(XQUERY1_PATH);
        for (int n;(n = in.read()) != -1;buffer.append((char) n));
        in.close(); 
        String xquery = buffer.toString();
        
        xquery = xquery.replace("[ID]", id);        
        
        //[DEBUG]
        //debug.setText(debug.getText()+xquery);
       
    	Collection col = null;
        try { 
            col = DatabaseManager.getCollection(URI + COLLECTION);
            XPathQueryService xpqs = (XPathQueryService)col.getService("XPathQueryService", "1.0");
            xpqs.setProperty("indent", "no");
            ResourceSet resources = xpqs.query(xquery);
            if(resources.getSize()>0) 
            {
            	id = generateID(authorAccess);
            }
			} catch (MalformedURLException e){
				e.printStackTrace();
				
				//[DEBUG]
				//debug.setText("Fehler bei der Validierung der ID");
		} finally {
            //dont forget to cleanup
            if(col != null) {
                col.close();
        	}
    	}
    
    	return(id);
    }
    
	    
    /**
	 * Stellt die XQuery zusammen und sendet sie an den Server
	 * @return
	 * @throws Exception
	 */
    @SuppressWarnings("rawtypes")
	private LinkedList<String> store(AuthorAccess authorAccess) throws Exception {

        LinkedList<String> result = new LinkedList<String>();
        
        final String driver = "org.exist.xmldb.DatabaseImpl";

        // initialize database driver
        Class cl = Class.forName(driver);
        
        Database database = new DatabaseImpl();
        database.setProperty("create-database", "true");
        database = (Database) cl.newInstance();
        DatabaseManager.registerDatabase(database);
        StringBuffer buffer = new StringBuffer();
        FileReader in = new FileReader(XQUERY2_PATH);
        
        for (int n;(n = in.read()) != -1;buffer.append((char) n));
        in.close(); 
        String xquery = buffer.toString();

		//String directory = "00";
        
		//if(filenameField.getText().length()>=2) 
		//{
		//	String first2tokens = filenameField.getText().toLowerCase().replace("ö", "oe").replace("ü", "ue").replace("ä", "ae").substring(0,2);
		//	if(checkCollection(COLLECTION + "/" + first2tokens))
		//		directory = first2tokens;
		//}
		
		buffer = new StringBuffer();
        in = new FileReader(TEMPLATE.get(templateChoice.getSelectedItem()));
        for (int n;(n = in.read()) != -1; buffer.append((char) n));
        in.close(); 
        String template = buffer.toString();
        
        template = template.replace("[SCHREIBUNG]", filenameField.getText());
        template = template.replace("[ID]", ID);
        template = template.replace("[DATUM]", DATE);
        xquery = xquery.replace("[FILENAME]", filenameField.getText() + "-" + ID + ".xml");
        xquery = xquery.replace("[COLLECTION]", COLLECTION);
        //xquery = xquery.replace("[DIRECTORY]", directory);
        xquery = xquery.replace("[USERNAME]", usernameField.getText());
        xquery = xquery.replace("[PASSWORD]", passwordField.getText());
        
        //[DEBUG]
        //debug.setText(debug.getText()+"xquery: "+xquery);

        Collection collection = DatabaseManager.getCollection(URI + COLLECTION + "/" /*+ directory*/,usernameField.getText(),passwordField.getText());        
		XMLResource resource = 
		   (XMLResource) collection.createResource(filenameField.getText() + "-" + ID + ".xml", XMLResource.RESOURCE_TYPE);
		
		resource.setContent(template);
		collection.storeResource(resource);
		
    	try { 
            XPathQueryService xpqs = (XPathQueryService)collection.getService("XPathQueryService", "1.0");
            xpqs.setProperty("indent", "no");
            ResourceSet resources = xpqs.query(xquery);
            if(resources.getSize()>0) 
            {
            	String file = "";
				try {
					file = URI.replace("xmldb:exist://", "http://") + COLLECTION + "/" /*+ directory + "/"*/ + filenameField.getText() + "-" + ID + ".xml";
					file = file.replace("xmlrpc", "webdav");
					authorAccess.getWorkspaceAccess().open(new URL(file));
					//dispose();
				} catch (MalformedURLException e){
					e.printStackTrace();
					//debug.setText("Erstellte Datei konnte nicht geöffnet werden: " + file);
				}
			}
			//else debug.setText("Erstellen der Datei fehlgeschlagen...");
        } finally {
            //dont forget to cleanup
            if(collection != null) {
            	collection.close();
        	}
	    }
		
        //[DEBUG]
        /*for(String res : result)
        {
            debug.setText(debug.getText() + "response: "+res);
        }*/
		return(result);
    }
    
	/**
	 * Beendet den Dialog ohne weitere Aktion
	 */
	private void cancelAction(){
		dispose();
	}
}