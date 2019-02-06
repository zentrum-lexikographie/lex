package dwdsox.plugin.actions;

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
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

import org.exist.xmldb.DatabaseImpl;
//import org.exist.xmldb.EXistResource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

import ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace;

public class DWDSoXCreateDocument extends JDialog {

	/**
	 * 
	 */
	private static final long serialVersionUID = -190895918216985737L;

	/**
	 * Dies sind die Parameter fï¿½r die Fenstergröße des Dialogs.
	 */
	static int H_SIZE = 300;
	static int V_SIZE = 180;
		
	//private static String XQUERY0_PATH;
	private static String CHECKIDXQUERY_PATH;
	private static String SETPERMISSIONSXQUERY_PATH;
	private static String URI;
	private static String COLLECTION;
	private static HashMap<String, String> TEMPLATE;
	private static String GROUP;
	private static String PERMISSIONS;
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

	
	public DWDSoXCreateDocument(final StandalonePluginWorkspace pluginWorkspaceAccess, String checkIDXQueryPath, String setPermissionsXQueryPath, String uri, String collection, HashMap<String, String> templateList, String group, String permissions) {
		// Calls the parent telling it this dialog is modal(i.e true)
		super((Frame) pluginWorkspaceAccess.getParentFrame(), false);
		
		//save values to fields for usage in all methods
		CHECKIDXQUERY_PATH = checkIDXQueryPath;
		SETPERMISSIONSXQUERY_PATH = setPermissionsXQueryPath; 
		URI = uri;
		COLLECTION = collection;
		TEMPLATE = templateList;
		GROUP = group;
		PERMISSIONS = permissions;
			
		// Für den Dialog wird das Layout (North, South, .., Center) ausgewählt und der Titel gesetzt.
		setLayout(new BorderLayout());
		setTitle("Datei in eXist erzeugen");
		
		//[DEBUG]
		//add("Center", debug);
		//debug.setText(xqPath0 + "\n" + xqPath1 + "\n" + xqPath2 + "\n" + uri + "\n" + collection + "\n" + templateList.toString());
		
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
			createAction(pluginWorkspaceAccess);
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
	private void createAction(StandalonePluginWorkspace pluginWorkspaceAccess){
		
		fileList.clear();
		// execute xquery

		try {
			ID = generateID(pluginWorkspaceAccess);
			DATE = new SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().getTime());
			store(pluginWorkspaceAccess);
		} catch(Exception e) {
			JOptionPane.showMessageDialog(null,e.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
		dispose();
	}
	
	/**
	 * Stellt die XQuery zusammen und sendet sie an den Server
	 * @return
	 * @throws Exception
	 */
    @SuppressWarnings("rawtypes")
	private String generateID(StandalonePluginWorkspace pluginWorkspaceAccess) throws Exception {
    	Random r = new Random();
    	String id = "E_" + r.nextInt(9999999);
    	
    	final String driver = "org.exist.xmldb.DatabaseImpl";

        // initialize database driver
        Class cl = Class.forName(driver);
        
        Database database = new DatabaseImpl();
        database.setProperty("create-database", "true");
        database = (Database) cl.newInstance();
        DatabaseManager.registerDatabase(database);
        StringBuffer buffer = new StringBuffer();
        FileReader in = new FileReader(CHECKIDXQUERY_PATH);
        for (int n;(n = in.read()) != -1;buffer.append((char) n));
        in.close(); 
        String xquery = buffer.toString();
        
        xquery = xquery.replace("[ID]", id);
        
        //[DEBUG]
        //JOptionPane.showMessageDialog(null,URI + COLLECTION + ":" + xquery,"Debug Info",JOptionPane.INFORMATION_MESSAGE);
       
    	Collection col = null;
        try { 
            col = DatabaseManager.getCollection(URI + COLLECTION);
            XPathQueryService xpqs = (XPathQueryService)col.getService("XPathQueryService", "1.0");
            xpqs.setProperty("indent", "no");

            ResourceSet resources = xpqs.query(xquery);
            if(resources.getSize()>0) 
            {
            	id = generateID(pluginWorkspaceAccess);
            }
		} finally {
            //dont forget to cleanup
            if(col != null) {
                col.close();
        	}
    	}
    
        return(id);
    }
    
	    
    /**
	 * Erzeugt die Datei auf dem Server und setzt die Rechte
	 * @return
	 * @throws Exception
	 */
    @SuppressWarnings("rawtypes")
	private LinkedList<String> store(StandalonePluginWorkspace pluginWorkspaceAccess) throws Exception {

        LinkedList<String> result = new LinkedList<String>();
        
        final String driver = "org.exist.xmldb.DatabaseImpl";

        // initialize database driver
        Class cl = Class.forName(driver);
        
        Database database = new DatabaseImpl();
        database.setProperty("create-database", "true");
        database = (Database) cl.newInstance();
        DatabaseManager.registerDatabase(database);
        
        // generate file
		StringBuffer buffer = new StringBuffer();
        FileReader in = new FileReader(TEMPLATE.get(templateChoice.getSelectedItem()));
        for (int n;(n = in.read()) != -1; buffer.append((char) n));
        in.close(); 
        String template = buffer.toString();
        
        template = template.replace("[SCHREIBUNG]", filenameField.getText());
        template = template.replace("[ID]", ID);
        template = template.replace("[DATUM]", DATE);
        
        //[DEBUG]
        //JOptionPane.showMessageDialog(null,URI + COLLECTION + "/" /*+ directory*/,"Debug Info",JOptionPane.INFORMATION_MESSAGE);
		
        //error-proof filename conversion
	    String temp = Normalizer.normalize(filenameField.getText(), Normalizer.Form.NFD);
	    Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
	    String filename = pattern.matcher(temp).replaceAll("");
	    filename = filename.replace("ß", "ss");
	    filename = filename.replace(" ", "-");
	    filename = filename.replaceAll("[^\\p{Alpha}\\p{Digit}\\-]","_");

        Collection collection = DatabaseManager.getCollection(URI + COLLECTION + "/" /*+ directory*/,usernameField.getText(),passwordField.getText());        
		XMLResource resource = 
		   (XMLResource) collection.createResource(filename + "-" + ID + ".xml", XMLResource.RESOURCE_TYPE);
		
		resource.setContent(template);
		collection.storeResource(resource);

        //[DEBUG]
	    //JOptionPane.showMessageDialog(null,URI + COLLECTION + "/" + filename,"Debug Info",JOptionPane.INFORMATION_MESSAGE);

		// set file permissions
        buffer = new StringBuffer();
        in = new FileReader(SETPERMISSIONSXQUERY_PATH);
        
        for (int n;(n = in.read()) != -1;buffer.append((char) n));
        in.close(); 
        String xquery = buffer.toString();
        xquery = xquery.replace("[RESOURCE]", COLLECTION + "/" + filename + "-" + ID + ".xml");
        xquery = xquery.replace("[GROUP]", GROUP);
        xquery = xquery.replace("[PERMISSIONS]", PERMISSIONS);
        xquery = xquery.replace("[USER]", usernameField.getText());
        xquery = xquery.replace("[PASSWORD]", passwordField.getText());

        //[DEBUG]
	    //JOptionPane.showMessageDialog(null,"Permission XQuery: " + xquery,"Debug Info",JOptionPane.INFORMATION_MESSAGE);
		
        Collection col = null;
        col = DatabaseManager.getCollection(URI + COLLECTION);
        XPathQueryService xpqs = (XPathQueryService)col.getService("XPathQueryService", "1.0");
        xpqs.setProperty("indent", "no");
        xpqs.query(xquery);
        
    	String file = "";
		try {
			file = URI.replace("xmldb:exist://", "http://").replace("xmlrpc", "webdav") + COLLECTION + "/" + filename + "-" + ID + ".xml";
			pluginWorkspaceAccess.open(new URL(file));
		} catch (MalformedURLException e){
			e.printStackTrace();
			JOptionPane.showMessageDialog(null,"Erstellte Datei konnte nicht geöffnet werden: " + file,"Fehler",JOptionPane.ERROR_MESSAGE);
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