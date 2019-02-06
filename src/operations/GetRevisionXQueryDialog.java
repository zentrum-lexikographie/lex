package operations;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.EXistResource;
import org.jdesktop.swingx.JXTable;
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

public class GetRevisionXQueryDialog extends JDialog {

	/**
	 * 
	 */
	private static final long serialVersionUID = -190895918216985737L;

	/**
	 * Dies sind die Parameter für die Fenstergröße des Dialogs.
	 */
	static int H_SIZE = 400;
	static int V_SIZE = 250;

	private static String XQUERY_PATH_LIST;
	private static String XQUERY_PATH_REVISION;
	private static String URI;
	private static String FILEPATH;
	private static String FILENAME;
	private static String TEMPDIRECTORY;
	
	final JButton open = new JButton("Revision Öffnen");
	final TextField filenameField = new TextField("", 20);
	//final Choice directoryChoice = new Choice();
	final TextArea debug = new TextArea(); 
	static String[] captions = {"Revision", "Benutzer", "Datum"};
	static String[][] content = new String[0][0];
	@SuppressWarnings("serial")
	JXTable revisionTable = new JXTable(new DefaultTableModel(content, captions){
	    @Override
	    public boolean isCellEditable(int row, int column) {
	        return false;
	    }
	});
	
	public GetRevisionXQueryDialog(final AuthorAccess authorAccess, String xqPathList, String xqPathRevision, String uri) {
		// Calls the parent telling it this dialog is modal(i.e true)
		super((Frame) authorAccess.getWorkspaceAccess().getParentFrame(), false);
		
		//save values to fields for usage in all methods
		XQUERY_PATH_LIST = xqPathList; 
		XQUERY_PATH_REVISION = xqPathRevision; 
		URI = uri;
		File file = new File(authorAccess.getEditorAccess().getEditorLocation().getPath());
		FILEPATH = authorAccess.getEditorAccess().getEditorLocation().getPath();
		FILENAME = file.getName();
		TEMPDIRECTORY = System.getProperty("java.io.tmpdir");
			
		// Für den Dialog wird das Layout (North, South, .., Center) ausgewählt und der Titel gesetzt.
		setLayout(new BorderLayout());
		setTitle("Revisionen für " + FILENAME);
				
		ResourceSet resources = null;
		try {
			resources = getRevisionList();
			if(resources==null) {
				dispose();
				return;
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,"Ermitteln der Revisionen fehlgeschlagen.\n" + "Möglicherweise existiert Datei " + FILEPATH + " nicht in Datenbank.\n"+ e.getCause() + ": " + e.getMessage() + "\n" + e.getStackTrace(),"Error",JOptionPane.ERROR_MESSAGE);
			dispose();
			return;
		}
		
		revisionTable.setFillsViewportHeight(true);
		revisionTable.setShowGrid(false);
		revisionTable.setIntercellSpacing(new Dimension(0, 0));
		revisionTable.getColumnModel().getColumn(0).setMaxWidth(80);
		revisionTable.getColumnModel().getColumn(1).setMaxWidth(120);
		add("Center", new JScrollPane(revisionTable));
		//Öffnen mit Doppelklick
		revisionTable.addMouseListener(new MouseAdapter() {
			   public void mouseClicked(MouseEvent e) {
				      if (e.getClickCount() == 2) {
				         JTable target = (JTable)e.getSource();
							openRevisionAction(authorAccess, (String)revisionTable.getModel().getValueAt(target.getSelectedRow(), 0)); // third column is file path
				         }
				   }
				});
		
		class FillListThread extends Thread {

	        private ResourceSet resources = null;
	        
	        public FillListThread(ResourceSet resources) {
	        	this.resources = resources;
			}

	        public void run() {
	        	try {
		        	Resource res = null;
		            try {
						DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
						ResourceIterator i = resources.getIterator();
				        while(i.hasMoreResources()) {
				        	String[] data = new String[3];
			                res = i.nextResource();
			                DocumentBuilder builder = domFactory.newDocumentBuilder();
				            InputSource is = new InputSource();
				            is.setCharacterStream(new StringReader((String) res.getContent()));
				            Document doc = builder.parse(is);
			            	String revisionNumber = ((Element) doc.getElementsByTagName("v:revision").item(0)).getAttribute("rev");
				            Node userNode = doc.getElementsByTagName("v:user").item(0);
				            Node dateNode = doc.getElementsByTagName("v:date").item(0);

				            // indent the entry to illustrate hierarchy level

				            if(revisionNumber!=null) data[0] = revisionNumber;
				            else data[0] = "";
				            if(userNode!=null) data[1] = userNode.getTextContent();
				            else data[1] = "";
				            if(dateNode!=null) 
				            	{
				            		data[2] = dateNode.getTextContent();
				            		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
				            		Date date = dateFormat.parse(data[2]);
				            		dateFormat.applyPattern("dd.MM.yyyy, HH:mm:ss");
				            		data[2] = dateFormat.format(date);
				            	}
				            else data[2] = "";

				    		((DefaultTableModel)revisionTable.getModel()).addRow(data);
			            }
	    		} finally {
	                //dont forget to cleanup resources
	            	((EXistResource)res).freeResources();
				}
			} catch(Exception e) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				//String exceptionAsString = sw.toString();
	            JOptionPane.showMessageDialog(null,"Fehler beim Auswerten der Revisionsliste." + e.getMessage() + "\n","Error",JOptionPane.ERROR_MESSAGE);
	            dispose();
	            return;
			}
		}
	    }

		Thread fillThread = new FillListThread(resources);
	    fillThread.start();
		
		// Unten gibt es die zwei Knöpfe "Öffnen" (Default) und "Abbrechen".
		Panel panel = new Panel();
		open.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) {
			openRevisionAction(authorAccess, (String)revisionTable.getModel().getValueAt(revisionTable.getSelectedRow(), 0));
		}});
		panel.add(open);
		getRootPane().setDefaultButton(open);
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

	private ResourceSet getRevisionList() throws Exception {

		ResourceSet resources = null;
        
        final String driver = "org.exist.xmldb.DatabaseImpl";

        // initialize database driver
        @SuppressWarnings("rawtypes")
		Class cl = Class.forName(driver);
        
        Database database = new DatabaseImpl();
        database.setProperty("create-database", "true");
        database = (Database) cl.newInstance();
        DatabaseManager.registerDatabase(database);
        StringBuffer buffer = new StringBuffer();
        FileReader in = new FileReader(XQUERY_PATH_LIST);
        for (int n;(n = in.read()) != -1;buffer.append((char) n));
        in.close(); 
        String xquery = buffer.toString();

        xquery = xquery.replace("[FILEPATH]", FILEPATH.substring(FILEPATH.indexOf("webdav")+6));

        Collection col = null;
        try { 
            col = DatabaseManager.getCollection(URI/* + COLLECTION*/);
            XPathQueryService xpqs = (XPathQueryService)col.getService("XPathQueryService", "1.0");
            xpqs.setProperty("indent", "no");
            resources = xpqs.query(xquery);
            
            if(resources.getSize()>0) {}
			else {
				JOptionPane.showMessageDialog(null,"Keine Revisionen gefunden.","Information",JOptionPane.INFORMATION_MESSAGE);
	            return(null);
			}
    		
        } finally {
            //dont forget to cleanup
            if(col != null) {
                col.close(); 
        	}
	    }
        
        //[DEBUG]
        /*String debugText = xquery + "\n\n";
        for(ResourceIterator ri = resources.getIterator(); ri.hasMoreResources();) debugText += ri.nextResource().getContent().toString() + "\n";
        JTextArea textArea = new JTextArea(debugText);
	    JScrollPane scrollPane = new JScrollPane(textArea);  
	    textArea.setLineWrap(true);  
	    textArea.setWrapStyleWord(true); 
	    scrollPane.setPreferredSize( new Dimension( 500, 500 ) );
	    JOptionPane.showMessageDialog(null,scrollPane,"DEBUG XML Text",JOptionPane.INFORMATION_MESSAGE);*/
        
		return(resources);
	}

	/**
	 * Bei "Suchen" wird die Suche gestartet und die Ergebnisse in der Liste dargestellt.
	 */
	private void openRevisionAction(AuthorAccess authorAccess, String index){
		
		File tempFile = new File(TEMPDIRECTORY + "/" + FILENAME.substring(0,FILENAME.lastIndexOf(".xml")) + "_rev" + index + ".xml");
		// execute xquery
		try {
			String fileText = getRevisionText(authorAccess, index);
	        
			//[DEBUG]
	        //JOptionPane.showMessageDialog(null,tempFile + File.separator,"Debug Info",JOptionPane.INFORMATION_MESSAGE);
			
			tempFile.createNewFile();
	        PrintWriter out = new PrintWriter(tempFile, "UTF-8");
	        out.print(fileText);
			out.close();
			authorAccess.getWorkspaceAccess().open(new URL("file:" + tempFile.getAbsolutePath()));
		} catch(Exception e) {
			JOptionPane.showMessageDialog(null,"Erstellen der temporären Datei fehlgeschlagen.\n" + e.getMessage() + "\n" + e.getStackTrace() + "\n" + tempFile.getAbsolutePath(),"Error",JOptionPane.ERROR_MESSAGE);
		}
		dispose();
	}
	
	/**
	 * Stellt die XQuery zusammen und sendet sie an den Server
	 * @return
	 * @throws Exception
	 */
    @SuppressWarnings("rawtypes")
	private String getRevisionText(AuthorAccess authorAccess, String index) throws Exception {

        String result = "";
        
        final String driver = "org.exist.xmldb.DatabaseImpl";

        // initialize database driver
        Class cl = Class.forName(driver);
        
        Database database = new DatabaseImpl();
        database.setProperty("create-database", "true");
        database = (Database) cl.newInstance();
        DatabaseManager.registerDatabase(database);
        StringBuffer buffer = new StringBuffer();
        FileReader in = new FileReader(XQUERY_PATH_REVISION);
        for (int n;(n = in.read()) != -1;buffer.append((char) n));
        in.close(); 
        String xquery = buffer.toString();

        xquery = xquery.replace("[FILEPATH]", FILEPATH.substring(FILEPATH.indexOf("webdav")+6));
        xquery = xquery.replace("[REVISION]", index);
        
        //[DEBUG]
        //JOptionPane.showMessageDialog(null,xquery,"get revision xquery to be sent",JOptionPane.INFORMATION_MESSAGE);

        Collection col = null;
        try { 
            col = DatabaseManager.getCollection(URI/* + COLLECTION*/);
            XPathQueryService xpqs = (XPathQueryService)col.getService("XPathQueryService", "1.0");
            xpqs.setProperty("indent", "no");
            ResourceSet resources = xpqs.query(xquery);
            if(resources.getSize()>0) {}
			else JOptionPane.showMessageDialog(null,"Ermitteln der Revision fehlgeschlagen.","Error",JOptionPane.ERROR_MESSAGE);
            
            ResourceIterator i = resources.getIterator();
            while(i.hasMoreResources()) {
            	result += (String) i.nextResource().getContent();
            }
        } finally {
            //dont forget to cleanup
            if(col != null) {
                col.close(); 
        	}
	    }
        
        //[DEBUG]
        //JOptionPane.showMessageDialog(null,result,"Result of get revision xQuery",JOptionPane.INFORMATION_MESSAGE);
		return(result);
    }
    
	/**
	 * Beendet den Dialog ohne weitere Aktion
	 */
	private void cancelAction(){
		dispose();
	}
}