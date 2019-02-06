package operations;

import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.List;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.FileReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.LinkedList;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
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
import ro.sync.ecss.extensions.api.AuthorOperationException;

public class DWDSEXistXQueryFrame extends JFrame implements ClipboardOwner {

	/**
	 * 
	 */
	private static final long serialVersionUID = -190895918216985737L;

	/**
	 * Dies sind die Parameter für die Fenstergröße des Dialogs.
	 */
	static int H_SIZE = 450;
	static int V_SIZE = 350;
	static String ICON_PATH = DWDSEXistXQueryFrame.class.getProtectionDomain().getCodeSource().getLocation().getPath().replace("DWDSoX.jar", "../presentation/DWDSeXist.png");
	
	private static String XQUERY_PATH;
	private static String URI;
	private static String COLLECTION;
	private static LinkedList<LinkedList<String>> FILTERLIST;
	private Thread fillThread = null;
	
	final LinkedList<String> idList = new LinkedList<String>();
	final List descriptionList = new List();
	final LinkedList<HashMap<String, Object>> filters = new LinkedList<HashMap<String, Object>>(); 
	final JButton search = new JButton("Suchen");
	final LinkedList<String> fileList = new LinkedList<String>();
	final JLabel statusLabel = new JLabel(" Ein * am Beginn/Ende öffnet den Suchterm.");
	
	public DWDSEXistXQueryFrame(final AuthorAccess authorAccess, String xqPath, String uri, String collection, LinkedList<LinkedList<String>> filterList) {
		// Calls the parent telling it this dialog is modal(i.e true)
		//super((Frame) authorAccess.getWorkspaceAccess().getParentFrame(), false);
		
		//save values to fields for usage in all methods
		XQUERY_PATH = xqPath; 
		URI = uri;
		COLLECTION = collection;
		FILTERLIST = filterList;
		
		// Für den Dialog wird das Layout (North, South, .., Center) ausgewählt und der Titel gesetzt.
		setLayout(new BorderLayout());
		setTitle("eXist - Content-Suche");
		
		// Erzeugt das Panel für die Filter-Zeilen 
		final Panel searchPanel = new Panel();
		add("North", searchPanel);
		
		createNewFilter(authorAccess, searchPanel);
		
		// In der Mitte wird das Auswahlfeld mit den Registereinträgen erzeugt, die bei Doppelklick die entsprechende Datei öffnen
		descriptionList.setMultipleMode(false);
		add("Center", descriptionList);
		descriptionList.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) {
			openAction(authorAccess);
		}});
		
		// Unten gibt es die drei Knöpfe "ID einfügen", "Öffnen" (Default) und "Abbrechen".
		Panel panel = new Panel();
		JButton copy = new JButton("Liste kopieren");
		copy.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) {
			copyAction(authorAccess);
		}});
		panel.add(copy);
		JButton open = new JButton("Öffnen");
		open.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) {
			statusLabel.setText("Suche gestartet. Warte auf Antwort der eXist-Datenbank...");
			openAction(authorAccess);
		}});
		panel.add(open);
		JButton insertID = new JButton("ID Einfügen");
		insertID.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) {
			insertIDAction(authorAccess);
		}});
		panel.add(insertID);
		JButton cancel = new JButton("Schließen");
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
		setAlwaysOnTop (true);
		
		// hübsches Symbol für das Fenster
		String decodedPath = "";
		try {decodedPath = URLDecoder.decode(ICON_PATH, "UTF-8");} 
			catch (UnsupportedEncodingException e) {e.printStackTrace();}
		ImageIcon icon = new ImageIcon(decodedPath);
		setIconImage(icon.getImage());
	}

	/**
	 * Entfernt die entsprechende Filter-Eingabezeile und aktualisiert die GrafikoberflÃ¤che
	 * @param searchPanel - zugrunde liegendes Panel
	 * @param filterMap - Filter-Oberfläche welche entfernt werden soll
	 */
	private void removeFilter(final AuthorAccess authorAccess, final Panel searchPanel, HashMap<String,Object> filterMap) {
		// Entfernt den filter aus der Filter-Liste
		filters.remove(filterMap);
		
		// Erzeuge neues Layout für das SearchPanel
		searchPanel.removeAll();
		searchPanel.setLayout(new GridLayout(filters.size()+1,1));
		for(HashMap<String, Object> filter : filters)
		{
			searchPanel.add((Component) filter.get("panel"));
		}
		
		searchPanel.add(createInfoBar(authorAccess, searchPanel));
		validate();
		searchPanel.validate();
	}
	
	/**
	 * Erzeugt eine Eingabezeile für eine neue Zeile und aktualisiert die Grafikoberfläche
	 * @param searchPanel
	 */
	private void createNewFilter(final AuthorAccess authorAccess, final Panel searchPanel) {
		
		//Erzeugt neue FilterMap und FilterPanel
		final HashMap<String, Object> filterMap = new HashMap<String, Object>();
		filters.add(filterMap);
		final Panel filterPanel = new Panel(new BorderLayout());
		filterMap.put("panel", filterPanel);
		
		JButton removeButton = new JButton("-");
		removeButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent arg0) {
				removeFilter(authorAccess, searchPanel, filterMap);
			}});
		removeButton.setPreferredSize(new Dimension(45,1));
		filterPanel.add("East", removeButton);
		
		// Erzeugt die Filter-ChoiceBox, fällt sie mit den wählbaren Filtern und fügt sie zum Panel hinzu
		Choice filterBox = new Choice();
		filterMap.put("filterType", filterBox);
		for(LinkedList<String> values : FILTERLIST) {
			String filterName = values.get(0);
			// Kürzt Bezeichnung ab auf den Namen des letztes Elements im XPath 
			filterName = filterName.substring(filterName.lastIndexOf("/")+1);
			filterName = filterName.substring(filterName.lastIndexOf(":")+1);
			filterBox.add(filterName);
		}
		filterPanel.add("West", filterBox);
		filterBox.addItemListener(new ItemListener(){
			// aktualisiert die Werte-Auswahl / das Eingabefeld 
			public void itemStateChanged(ItemEvent event) 
			{
				filterPanel.remove((Component) filterMap.get("value"));
				Object valueBox = filterSelected(filterMap);
				filterMap.put("value", valueBox);
				filterPanel.add("Center", (Component) valueBox);
				filterPanel.validate();
			}});
		filterBox.requestFocus();
		
		// Erzeuge Eingabe-Element für den Wert des Filters
		Object valueBox = filterSelected(filterMap);
		filterMap.put("value", valueBox);
		filterPanel.add("Center", (Component) valueBox);
		
		// Erzeuge neues Layout für das SearchPanel
		searchPanel.removeAll();
		searchPanel.setLayout(new GridLayout(filters.size()+1,1));
		for(HashMap<String, Object> filter : filters)
		{
			searchPanel.add((Component) filter.get("panel"));
		}
		
		// Erzeuge die Info-Bar
		searchPanel.add(createInfoBar(authorAccess, searchPanel));
		validate();
		searchPanel.validate();
	}
	
	/**
	 * Erzeugt die Info-Bar, mit Erklärungs-Label und Neuer-Filter-Button
	 * @param searchPanel
	 * @return
	 */
	private Panel createInfoBar(final AuthorAccess authorAccess, final Panel searchPanel) {
		// Erzeugt den Knopf, um eine neue Zeile hinzuzufügen
		Panel infoBar = new Panel(new BorderLayout());
		search.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) {
			searchAction(authorAccess);
		}});
		infoBar.add("West", search);
		getRootPane().setDefaultButton(search);
		infoBar.add("Center", statusLabel);
		JButton toggleButton = new JButton("+");
		toggleButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent arg0) {
				createNewFilter(authorAccess, searchPanel);
			}});
		toggleButton.setPreferredSize(new Dimension(45,1));
		infoBar.add("East", toggleButton);
		return(infoBar);
	}
	
	/**
	 * Bei der Auswahl eines Attributs wird die Werte-Auswahl mit den entsprechenden Werten gefÃ¼llt, vorrausgesetzt es handelt sich um eine Choice-Box und kein Eingabfeld
	 */
	private Object filterSelected(HashMap<String, Object> filter) {
		Choice filterBox = (Choice) filter.get("filterType");
		Object valueBox = null;
		// Erzeugt das Werte-Auswahl-Element, entweder als Choice Element, wenn vordefinierte Werte angegeben wurden oder als Eingabefeld und initialisiert ggf. die Wertevorauswahl
		if(FILTERLIST.get(filterBox.getSelectedIndex()).size()==1) {
			TextField valueTextField = new TextField();
			valueBox = valueTextField;
		}
		else {
			Choice valueChoice = new Choice();
			LinkedList<String> values = FILTERLIST.get(filterBox.getSelectedIndex());
			for(String value : values.subList(1, values.size())) {
				((Choice) valueChoice).add(value);
			}
			valueBox = valueChoice;
		}
		return(valueBox);
	}

	/**
	 * Bei "Suchen" wird die Suche gestartet und die Ergebnisse in der Liste dargestellt.
	 */
	@SuppressWarnings("deprecation")
	private void searchAction(AuthorAccess authorAccess){
		
		if(fillThread!=null) fillThread.stop();
		descriptionList.removeAll();
		idList.clear();
		fileList.clear();
		// execute xquery
		try {
			ResourceSet resources = xQuery();
			descriptionList.removeAll();
			statusLabel.setText(" " + resources.getSize() + " Artikel gefunden");
			
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
				                res = i.nextResource();
				                DocumentBuilder builder = domFactory.newDocumentBuilder();
					            InputSource is = new InputSource();
					            is.setCharacterStream(new StringReader((String) res.getContent()));
					            Document doc = builder.parse(is);
					            Element node = doc.getDocumentElement();
					            Node file = node.getElementsByTagName("file").item(0);
					            Node id = node.getElementsByTagName("id").item(0);
					            //Node level = node.getElementsByTagName("level").item(0);
					            Node definition = node.getElementsByTagName("Definition").item(0);
					            //Node dia = node.getElementsByTagName("dia").item(0);
					            Node term = node.getElementsByTagName("Schreibung").item(0);
		
					            // indent the entry to illustrate hierarchy level
					            String level_space = "";
					            //for(int i = 0; i<Integer.parseInt(level.getTextContent()); i++) level_space += "   ";
				               	String description = "";
				            	description = level_space + "- " + term.getTextContent();
				            	//if(dia!=null && !dia.getTextContent().equals("")) description += " (" + dia.getTextContent() + ")";
				            	if(definition!=null && !definition.getTextContent().equals("")) description += ": " + definition.getTextContent();
				            	descriptionList.add(description);
					            if(id==null) {
					            	idList.add("");
					            }
					            else {
					            	idList.add(id.getTextContent());
					            }
					            if(file==null) {
					            	fileList.add("");
					            }
					            else {
					            	fileList.add(file.getTextContent());
					            }
					            
				            }
		    		} finally {
		                //dont forget to cleanup resources
		            	((EXistResource)res).freeResources();
					}
				} catch(Exception e) {
	    			System.err.println("error while using xQuery: ");
	    			e.printStackTrace();
				}
			}
		    }
		
		    fillThread = new FillListThread(resources);
		    fillThread.start();

		} catch(Exception e) {
			System.err.println("error while using xQuery: ");
			e.printStackTrace();
		}
	}
	
	/**
	 * Stellt die XQuery zusammen und sendet sie an den Server
	 * @return
	 * @throws Exception
	 */
    @SuppressWarnings("rawtypes")
	private ResourceSet xQuery() throws Exception {

    	ResourceSet resources = null;      
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

        String filterString = "";
        boolean first = true;
        for(HashMap<String, Object> filterMap : filters) {
        	int index = ((Choice) filterMap.get("filterType")).getSelectedIndex();
        	String xpath = FILTERLIST.get(index).get(0);
        	String value = "";
        	
        	// Unterscheidung zwischen TextField- und Aufklapp-Menü-Eingabe
        	if(filterMap.get("value") instanceof TextField)
        		value = ((TextField) filterMap.get("value")).getText();
        	if(filterMap.get("value") instanceof Choice)
        		value = ((Choice) filterMap.get("value")).getSelectedItem();
        	
            //feature for searching for any word that contains, begins, respectively ends with expression
            if(value.startsWith("*")) 
            	if(value.endsWith("*"))
            		value = "[contains(., \"" + value + "\")]";
            	else 
            		value = "[ends-with(., \"" + value +"\")]";
            else 
            	if(value.endsWith("*"))
            		value = "[starts-with(., \"" + value + "\")]";
            else 	
            	value = "=\"" + value +"\" ";
            // delete all *-signs
            value = value.replaceAll("\\*", "");
        	
        	// Konkatenation der Filter mittels "and"
        	if(first) {
        		filterString += "where ";
                first = false;
        	}
        	else filterString += " and ";
        	
        	// Zusammensetzen des finalen Filters
            filterString += "$hit" + xpath + value;
        }
        xquery = xquery.replace("[FILTER]", filterString);
        xquery = xquery.replace("[COLLECTION]", COLLECTION);
        //[DEBUG]
        //descriptionList.add(xquery);

        Collection col = null;
        try { 
            col = DatabaseManager.getCollection(URI + COLLECTION);
            XPathQueryService xpqs = (XPathQueryService)col.getService("XPathQueryService", "1.0");
            xpqs.setProperty("indent", "no");
            resources = xpqs.query(xquery);
        } finally {
            //dont forget to cleanup
            if(col != null) {
                col.close();
            }
        }
        //[DEBUG]
        /*for(String res : result)
        {
            descriptionList.add(res);
        }*/
		return(resources);
    }
    
    /**
     * Fügt die ID der ausgewÃ¤hlten Lesart an der Cursorposition ein
     * @param authorAccess
     */
	private void insertIDAction(AuthorAccess authorAccess) {
		String id = "";
		try {
			id = idList.get(descriptionList.getSelectedIndex());
			authorAccess.getDocumentController().insertXMLFragment(id,authorAccess.getEditorAccess().getCaretOffset());
		} catch(AuthorOperationException e) {
			e.printStackTrace();
		}
		//if(!id.equals("")) dispose();
	}

	/**
	 * Kopiert die Liste in die Zwischenablage
	 * @param authorAccess
	 */
	private void copyAction(AuthorAccess authorAccess) {
		Clipboard systemClip = Toolkit.getDefaultToolkit().getSystemClipboard();
		String copyText = "";
		for(String line : descriptionList.getItems()) {
			copyText += line.substring(2) + "\n";
		}
		systemClip.setContents(new StringSelection(copyText),this);
	}
	
	/**
	 * Öffnet die Datei, in der sich die ausgewählte Lesart befindet
	 * @param authorAccess
	 */
	private void openAction(AuthorAccess authorAccess) {
		String file = "";
		try {
			file = fileList.get(descriptionList.getSelectedIndex());
			file = URI.replace("xmldb:exist://", "http://") + file;
			file = file.replace("xmlrpc", "webdav");
//			file = "oxygen:" + URI + file;
//			file = file.replace("xmlrpc/", "xmlrpc");
//			file = "oxygen:/eXist-Test$eXist-Test-Verbindung";
			authorAccess.getWorkspaceAccess().open(new URL(file));
			//dispose();
		} catch (MalformedURLException e){
			e.printStackTrace();
			descriptionList.add("could not open " + file);
		}
	}
	
	/**
	 * Beendet den Dialog ohne weitere Aktion
	 */
	private void cancelAction(){
		dispose();
	}

	@Override
	public void lostOwnership(Clipboard arg0, Transferable arg1) {
		// Dont't care...
	}
}