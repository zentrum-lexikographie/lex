package operations;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileReader;
import java.util.LinkedList;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

import org.exist.xmldb.DatabaseImpl;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

import ro.sync.ecss.extensions.api.AuthorAccess;

public class DeleteEXistXDocumentXQueryDialog extends JDialog {

	/**
	 * 
	 */
	private static final long serialVersionUID = -190895918216985737L;

	/**
	 * Dies sind die Parameter für die Fenstergröße des Dialogs.
	 */
	static int H_SIZE = 200;
	static int V_SIZE = 130;
		
	private static String XQUERY_PATH;
	private static String URI;
	private static String FILEPATH;
	
	final JButton delete = new JButton("Datei löschen");
	final TextField filenameField = new TextField("", 20);
	//final Choice directoryChoice = new Choice();
	final TextArea debug = new TextArea(); 
	final Label usernameLabel = new Label("Benutzername");
	final TextField usernameField = new TextField("", 20);
	final Label passwordLabel = new Label("Passwort");
	final TextField passwordField = new TextField("", 20);

	
	public DeleteEXistXDocumentXQueryDialog(final AuthorAccess authorAccess, String xqPath2, String uri) {
		// Calls the parent telling it this dialog is modal(i.e true)
		super((Frame) authorAccess.getWorkspaceAccess().getParentFrame(), false);
		
		//save values to fields for usage in all methods
		XQUERY_PATH = xqPath2; 
		URI = uri;
		FILEPATH = authorAccess.getEditorAccess().getEditorLocation().getPath();
			
		// Für den Dialog wird das Layout (North, South, .., Center) ausgewählt und der Titel gesetzt.
		setLayout(new BorderLayout());
		setTitle("Datei in eXist löschen");
				
		// Erzeugt das Panel für die Filter-Zeilen
		final Panel inputPanel = new Panel();
		inputPanel.setLayout(new GridLayout(2,2));
		Label questionLabel = new Label("Löschen der Datei " + FILEPATH);
		
		//[DEBUG]
		//add("North",debug);
		
		add("North",questionLabel);
		add("Center", inputPanel);
		//filenameField.setText("Debug");
		inputPanel.add(usernameLabel);
		inputPanel.add(usernameField);
	    passwordField.setEchoChar('*');
		inputPanel.add(passwordLabel);
		inputPanel.add(passwordField);
		
		// Unten gibt es die zwei Knöpfe "Löschen" (Default) und "Abbrechen".
		Panel panel = new Panel();
		delete.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) {
			deleteAction(authorAccess);
		}});
		panel.add(delete);
		getRootPane().setDefaultButton(delete);
		JButton cancel = new JButton("Abbrechen");
		cancel.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) {
			cancelAction();
		}});
		panel.add(cancel);
		add("South", panel);

		// Die Eigenschaften des Dialogfenster werden angepasst: die Größe, der Ort in der Bildschirmmitte, die SchlieÃŸaktion und die Sichtbarkeit.
		setSize(H_SIZE, V_SIZE);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setVisible(true);
	}

	/**
	 * Bei "Suchen" wird die Suche gestartet und die Ergebnisse in der Liste dargestellt.
	 */
    @SuppressWarnings("rawtypes")
	private void deleteAction(AuthorAccess authorAccess) {
        
        //[DEBUG]
        //JOptionPane.showMessageDialog(null,FILEPATH.substring(FILEPATH.lastIndexOf("/")+1),"Debug Info",JOptionPane.INFORMATION_MESSAGE);
        
        Collection collection = null;
		try {
	        // initialize database driver
	        final String driver = "org.exist.xmldb.DatabaseImpl";
	        Class cl = Class.forName(driver);
			Database database = new DatabaseImpl();
	        database.setProperty("create-database", "true");
	        database = (Database) cl.newInstance();
	        DatabaseManager.registerDatabase(database);
	        
			// delete resource
			collection = DatabaseManager.getCollection(URI + FILEPATH.substring(FILEPATH.indexOf("webdav")+6,FILEPATH.lastIndexOf("/")+1),usernameField.getText(),passwordField.getText());        
			XMLResource resource = (XMLResource) collection.getResource(FILEPATH.substring(FILEPATH.lastIndexOf("/")+1));
			collection.removeResource(resource);
			
			// execute xquery
			//xQuery(authorAccess);
			authorAccess.getEditorAccess().close(false);
		} catch (ClassNotFoundException e) {
			JOptionPane.showMessageDialog(null,"Löschen der Datei fehlgeschlagen. Datenbank-Treiber konnte nicht initialisiert werden.\n" + e.getMessage() + "\n" + e.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);
		} catch (XMLDBException e) {
			JOptionPane.showMessageDialog(null,"Datenbank-Zugriff fehlgeschlagen.\n" + e.getMessage() + "\n" + e.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);
		} catch (InstantiationException e) {
			JOptionPane.showMessageDialog(null,"Löschen der Datei fehlgeschlagen. Datenbank-Treiber konnte nicht initialisiert werden.\n" + e.getMessage() + "\n" + e.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);
		} catch (IllegalAccessException e) {
			JOptionPane.showMessageDialog(null,"Löschen der Datei fehlgeschlagen. Datenbank-Treiber konnte nicht initialisiert werden.\n" + e.getMessage() + "\n" + e.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,"Löschen der Datei fehlgeschlagen. Unbekannter Fehler. (5) \n" + e.getMessage() + "\n" + e.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);
		} finally {
        	try {
				collection.close();
			} catch (XMLDBException e) {
				e.printStackTrace();
			}
	    }
		dispose();
	}
	
	/**
	 * Stellt die XQuery zusammen und sendet sie an den Server
	 * @return
	 * @throws Exception
	 */
    @SuppressWarnings({ "rawtypes", "unused" })
	private LinkedList<String> xQuery(AuthorAccess authorAccess) throws Exception {

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

        xquery = xquery.replace("[FILEPATH]", FILEPATH.substring(FILEPATH.indexOf("webdav")+6));
        xquery = xquery.replace("[USERNAME]", usernameField.getText());
        xquery = xquery.replace("[PASSWORD]", passwordField.getText());
        
        //[DEBUG]
        //JOptionPane.showMessageDialog(null,xquery,"Debug Info",JOptionPane.INFORMATION_MESSAGE);

        Collection col = null;
        try { 
            col = DatabaseManager.getCollection(URI/* + COLLECTION*/);
            XPathQueryService xpqs = (XPathQueryService)col.getService("XPathQueryService", "1.0");
            xpqs.setProperty("indent", "no");
            ResourceSet resources = xpqs.query(xquery);
            if(resources.getSize()>0) {}
			else JOptionPane.showMessageDialog(null,"Löschen der Datei fehlgeschlagen.","Error",JOptionPane.ERROR_MESSAGE);
        } finally {
            //dont forget to cleanup
            if(col != null) {
                col.close(); 
        	}
	    }
        //[DEBUG]
        /*String debug = "";
        for(String res : result)
        {
            debug += "\n"+res;
        }*/
        //JOptionPane.showMessageDialog(null,debug,"Debug Info",JOptionPane.INFORMATION_MESSAGE);
		return(result);
    }
    
	/**
	 * Beendet den Dialog ohne weitere Aktion
	 */
	private void cancelAction(){
		dispose();
	}
}