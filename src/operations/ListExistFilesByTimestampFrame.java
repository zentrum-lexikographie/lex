package operations;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.FileReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.EXistResource;
import org.jdesktop.swingx.JXComboBox;
import org.jdesktop.swingx.JXDatePicker;
import org.jdesktop.swingx.JXPanel;
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

public class ListExistFilesByTimestampFrame extends JFrame implements ClipboardOwner {

	/**
	 * 
	 */
	private static final long serialVersionUID = -190895918216985737L;

	/**
	 * Dies sind die Parameter für die Fenstergröße des Dialogs.
	 */
	static int H_SIZE = 440;
	static int V_SIZE = 380;
	static String ICON_PATH = DWDSEXistXQueryFrame.class.getProtectionDomain().getCodeSource().getLocation().getPath().replace("DWDSoX.jar", "../presentation/DWDSeXist.png");
	
	private static String XQUERY_PATH;
	private static String URI;
	private static String COLLECTION;

	static String[] captions = {"URL","Zeitstempel","Schreibung","Element","Status","Änderung"};
	static String[][] content = new String[0][0];
	@SuppressWarnings("serial")
	JXTable descriptionTable = new JXTable(new DefaultTableModel(content, captions){
	    @Override
	    public boolean isCellEditable(int row, int column) {
	        return false;
	    }
	});
	final JButton search = new JButton("Suchen");
	final LinkedList<String> fileList = new LinkedList<String>();
	final JCheckBox dateCheckBox = new JCheckBox();
	JXDatePicker startDate;
	JXDatePicker endDate;
	JXComboBox nodeRestrictionChoice = new JXComboBox();
	final TextField nodeRestrictionText = new TextField();
	private Thread fillThread = null;
	private LinkedList<LinkedList<String>> FILTERLIST;
	final LinkedList<HashMap<String, Object>> filters = new LinkedList<HashMap<String, Object>>(); 
	final JLabel statusLabel = new JLabel();
	//DEBUG
	//final TextField statusLabel = new TextField();
	int sortColumn = 0;
	
	public ListExistFilesByTimestampFrame(final AuthorAccess authorAccess, LinkedList<String> nodeRestrictionList, LinkedList<LinkedList<String>> filtersList, String xqPath, String uri, String collection) {
		
		//save values to fields for usage in all methods
		XQUERY_PATH = xqPath; 
		URI = uri;
		COLLECTION = collection;
		FILTERLIST = filtersList;
		
		// Für den Dialog wird das Layout (North, South, .., Center) ausgewählt und der Titel gesetzt.
		setLayout(new BorderLayout());
		setTitle("eXist - Metadaten-Suche");

		//Erzeugt Eingabemaske für Zeitraum
		final JXPanel searchPanel = new JXPanel(new BorderLayout());
		final JXPanel datePanel = new JXPanel(new FlowLayout());
		final JXPanel filtersPanel = new JXPanel(new BorderLayout());
		add("North", searchPanel);
		searchPanel.add("North", datePanel);
		searchPanel.add("South", filtersPanel);
		
		// Richtet die Auswahlbox für die Knoten ein
		nodeRestrictionText.setColumns(10);
		nodeRestrictionList.push("");
		nodeRestrictionList.add("Freitext");
		nodeRestrictionChoice = new JXComboBox(nodeRestrictionList.toArray());
		nodeRestrictionChoice.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				if(nodeRestrictionChoice.getSelectedItem().equals("Freitext")) {
					datePanel.remove(nodeRestrictionChoice);
					datePanel.add(nodeRestrictionText);
					datePanel.validate();
					datePanel.validate();
					nodeRestrictionText.requestFocus();
				}
				else nodeRestrictionText.setText((String)nodeRestrictionChoice.getSelectedItem());
			}
		});
		
		// Erzeugt das Feld für den Zeitraum-Startwert
		datePanel.add(dateCheckBox);
		
		startDate = new JXDatePicker(new Date());
		startDate.setName("Start-Datum");
		startDate.setFormats("dd.MM.yyyy");
		Calendar cal = Calendar.getInstance();
		cal.setTime(startDate.getDate());
		cal.add(Calendar.YEAR, -2);
		Date date2YearsAgo = cal.getTime();
		startDate.setDate(date2YearsAgo);
		JXPanel startPanel = new JXPanel();
		startPanel.add(startDate);
		startDate.setEnabled(false);
		datePanel.add(startPanel);
		
		final JLabel bisLabel = new JLabel("bis");
		datePanel.add(bisLabel);
		
		endDate = new JXDatePicker(new Date());
		endDate.setFormats("dd.MM.yyyy");
		endDate.setName("End-Datum");
		JXPanel endPanel = new JXPanel();
		endPanel.add(endDate);
		endDate.setEnabled(false);
		datePanel.add(endPanel);
		
		final JLabel inLabel = new JLabel("in");
		inLabel.setEnabled(false);
		datePanel.add(inLabel);
		
		nodeRestrictionChoice.setEnabled(false);
		nodeRestrictionChoice.setEnabled(false);
		datePanel.add(nodeRestrictionChoice);
		
		dateCheckBox.addActionListener(new ActionListener() {
		      public void actionPerformed(ActionEvent actionEvent) {
		          startDate.setEnabled(dateCheckBox.isSelected());
		          bisLabel.setEnabled(dateCheckBox.isSelected());
		          endDate.setEnabled(dateCheckBox.isSelected());
		          inLabel.setEnabled(dateCheckBox.isSelected());
		          nodeRestrictionChoice.setEnabled(dateCheckBox.isSelected());
		          nodeRestrictionText.setEnabled(dateCheckBox.isSelected());
		        }
		      }); 
		
		
		search.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) {
			searchAction(authorAccess);
		}});
		
		createNewFilter(authorAccess, filtersPanel);
		
		getRootPane().setDefaultButton(search);
		
		validate();
		filtersPanel.validate();
		searchPanel.validate();
		datePanel.validate();
			
			
		// In der Mitte wird das Auswahlfeld mit den Registereinträgen erzeugt, bei Doppelklick wird das Element geöffnet
		descriptionTable.setFillsViewportHeight(true);
		descriptionTable.setShowGrid(false);
		descriptionTable.setIntercellSpacing(new Dimension(0, 0));
	    descriptionTable.removeColumn(descriptionTable.getColumnModel().getColumn(0));
		descriptionTable.getColumnModel().getColumn(0).setMaxWidth(80);
		add("Center", new JScrollPane(descriptionTable));
		//Öffnen mit Doppelklick
		descriptionTable.addMouseListener(new MouseAdapter() {
			   public void mouseClicked(MouseEvent e) {
				      if (e.getClickCount() == 2) {
				         JTable target = (JTable)e.getSource();
							openAction(authorAccess, (String)descriptionTable.getModel().getValueAt(target.getSelectedRow(), 0)); // third column is file path
				         }
				   }
				});
        // Unten gibt es die 3 Knöpfe "Liste kopieren", "Öffnen" (Default) und "Schließen".
		JXPanel panel = new JXPanel();
		JButton copy = new JButton("Liste kopieren");
		copy.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) {
			copyAction(authorAccess);
		}});
		panel.add(copy);
		JButton open = new JButton("Öffnen");
		open.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) {
			openAction(authorAccess,(String)descriptionTable.getModel().getValueAt(descriptionTable.getSelectedRow(), 0));
		}});
		panel.add(open);
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

	@SuppressWarnings("unchecked")
	private void createNewFilter(final AuthorAccess authorAccess, final JXPanel filtersPanel) {
		
		//Erzeugt neue FilterMap und FilterPanel
		final HashMap<String, Object> filterMap = new HashMap<String, Object>();
		filters.add(filterMap);
		final JXPanel filterPanel = new JXPanel(new BorderLayout());
		filterMap.put("panel", filterPanel);
		
		JButton removeButton = new JButton("-");
		removeButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent arg0) {
				removeFilter(authorAccess, filtersPanel, filterMap);
			}});
		removeButton.setPreferredSize(new Dimension(45,1));
		filterPanel.add("East", removeButton);
		
		// Erzeugt die Filter-ChoiceBox, fällt sie mit den wählbaren Filtern und fügt sie zum Panel hinzu
		JXComboBox filterBox = new JXComboBox();
		filterMap.put("filterType", filterBox);
		for(LinkedList<String> values : FILTERLIST) {
			String filterName = values.get(0);
			// Kürzt Bezeichnung ab auf den Namen des letztes Elements im XPath 
			filterName = filterName.substring(filterName.lastIndexOf("/")+1);
			filterName = filterName.substring(filterName.lastIndexOf(":")+1);
			filterBox.addItem(filterName);
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
		filtersPanel.removeAll();
		filtersPanel.setLayout(new GridLayout(filters.size()+1,1));
		for(HashMap<String, Object> filter : filters)
		{
			filtersPanel.add((Component) filter.get("panel"));
		}
		
		// Erzeuge die Info-Bar
		filtersPanel.add(createInfoBar(authorAccess, filtersPanel));
		validate();
		filtersPanel.validate();
	}

	/**
	 * Bei der Auswahl eines Attributs wird die Werte-Auswahl mit den entsprechenden Werten gefÃ¼llt, vorrausgesetzt es handelt sich um eine Choice-Box und kein Eingabfeld
	 */
	@SuppressWarnings("unchecked")
	private Object filterSelected(HashMap<String, Object> filter) {
		JXComboBox filterBox = (JXComboBox) filter.get("filterType");
		Object valueBox = null;
		// Erzeugt das Werte-Auswahl-Element, entweder als Choice Element, wenn vordefinierte Werte angegeben wurden oder als Eingabefeld und initialisiert ggf. die Wertevorauswahl
		if(FILTERLIST.get(filterBox.getSelectedIndex()).size()==1) {
			TextField valueTextField = new TextField();
			valueBox = valueTextField;
		}
		else {
			JXComboBox valueChoice = new JXComboBox();
			LinkedList<String> values = FILTERLIST.get(filterBox.getSelectedIndex());
			for(String value : values.subList(1, values.size())) {
				((JXComboBox) valueChoice).addItem(value);
			}
			valueBox = valueChoice;
		}
		return(valueBox);
	}

	/**
	 * Erzeugt die Info-Bar, mit Erklärungs-Label und Neuer-Filter-Button
	 * @param searchPanel
	 * @return
	 */
	private JXPanel createInfoBar(final AuthorAccess authorAccess, final JXPanel filtersPanel) {
		// Erzeugt den Knopf, um eine neue Zeile hinzuzufügen
		JXPanel infoBar = new JXPanel(new BorderLayout());
		infoBar.add("West", search);
		getRootPane().setDefaultButton(search);
		infoBar.add("Center",statusLabel);
		JButton toggleButton = new JButton("+");
		toggleButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent arg0) {
				createNewFilter(authorAccess, filtersPanel);
			}});
		toggleButton.setPreferredSize(new Dimension(45,1));
		infoBar.add("East", toggleButton);
		return(infoBar);
	}
	
	/**
	 * Entfernt die entsprechende Filter-Eingabezeile und aktualisiert die GrafikoberflÃ¤che
	 * @param filtersPanel - zugrunde liegendes Panel
	 * @param filterMap - Filter-Oberfläche welche entfernt werden soll
	 */
	private void removeFilter(final AuthorAccess authorAccess, final JXPanel filtersPanel, HashMap<String,Object> filterMap) {
		// Entfernt den filter aus der Filter-Liste
		filters.remove(filterMap);
		
		// Erzeuge neues Layout für das SearchPanel
		filtersPanel.removeAll();
		filtersPanel.setLayout(new GridLayout(filters.size()+1,1));
		for(HashMap<String, Object> filter : filters)
		{
			filtersPanel.add((Component) filter.get("panel"));
		}
		
		filtersPanel.add(createInfoBar(authorAccess, filtersPanel));
		validate();
		filtersPanel.validate();
	}
	
	/**
	 * Bei "Suchen" wird die Suche gestartet und die Ergebnisse in der Liste dargestellt.
	 */
	@SuppressWarnings("deprecation")
	private void searchAction(AuthorAccess authorAccess){

		statusLabel.setText("Suche gestartet. Warte auf Antwort der eXist-Datenbank...");
		if(fillThread!=null) fillThread.stop();
		((DefaultTableModel)descriptionTable.getModel()).setRowCount(0);
		fileList.clear();
		
		try {
			// execute xquery
			ResourceSet resources = xQuery();
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
					        	String[] data = new String[6];
				                res = i.nextResource();
				                DocumentBuilder builder = domFactory.newDocumentBuilder();
					            InputSource is = new InputSource();
					            is.setCharacterStream(new StringReader((String) res.getContent()));
					            Document doc = builder.parse(is);
					            Element node = doc.getDocumentElement();
					            Node timestamp = node.getElementsByTagName("Timestamp").item(0);
					            Node file = node.getElementsByTagName("file").item(0);
					            Node parent = node.getElementsByTagName("parent").item(0);
					            Node term = node.getElementsByTagName("Schreibung").item(0);
					            Node status = node.getElementsByTagName("Status").item(0);
					            Node latestModification = node.getElementsByTagName("LatestModification").item(0);

					            // indent the entry to illustrate hierarchy level

					            if(file!=null) data[0] = file.getTextContent();
					            else data[0] = "";
					            if(timestamp!=null) data[1] = timestamp.getTextContent();
					            else data[1] = "";
					            if(term!=null) data[2] = term.getTextContent();
					            else data[2] = "";
					            if(parent!=null) data[3] = parent.getTextContent();
					            else data[3] = "";
					            if(status!=null) data[4] = status.getTextContent();
					            else data[4] = "";
					            if(latestModification!=null) data[5] = latestModification.getTextContent();
					            else data[5] = "";
					        
								((DefaultTableModel)descriptionTable.getModel()).addRow(data);
					            
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
    	
    	// Zusammensetzen des finalen Filters
        if(nodeRestrictionText.getText().equals("Artikel")) xquery = xquery.replace("[RESTRICTION]", "");
        if(nodeRestrictionText.getText().equals("")) xquery = xquery.replace("[RESTRICTION]", /*"/"*/"");
        xquery = xquery.replace("[RESTRICTION]", "/s:" + nodeRestrictionText.getText());

        String filterString = "";
        for(HashMap<String, Object> filterMap : filters) {
        	int index = ((JXComboBox) filterMap.get("filterType")).getSelectedIndex();
        	String xpath = FILTERLIST.get(index).get(0);
        	String value = (String)((JXComboBox) filterMap.get("value")).getSelectedItem();
        	        	
        	// Zusammensetzen des finalen Filters
            filterString += "[" + xpath + "='" + value + "']";
        }
        if(!dateCheckBox.isSelected()) {
            xquery = xquery.replace("[START]", "");
            xquery = xquery.replace("[END]", "");
        }
        SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd");
        xquery = xquery.replace("[START]", "where data($hit)>='" + timeFormat.format(startDate.getDate()) + "'");
        xquery = xquery.replace("[END]", "and data($hit)<='" + timeFormat.format(endDate.getDate()) + "'");
        xquery = xquery.replace("[FILTER]", filterString);
        xquery = xquery.replace("[COLLECTION]", COLLECTION);

        //[DEBUG]
        //statusLabel.setText(xquery.replace("\n"," "));

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
	 * Kopiert die Liste in die Zwischenablage
	 * @param authorAccess
	 */
	private void copyAction(AuthorAccess authorAccess) {
		Clipboard systemClip = Toolkit.getDefaultToolkit().getSystemClipboard();
		String copyText = "";
		for(int x=0; x<descriptionTable.getRowCount(); x++) {
			for(int y=0; y<descriptionTable.getColumnCount(); y++) {
				copyText += descriptionTable.getValueAt(x, y) + " ";
			}
			copyText += "\n";			
		}
		systemClip.setContents(new StringSelection(copyText),this);
	}
	
	/**
	 * Öffnet die Datei, in der sich die ausgewählte Lesart befindet
	 * @param authorAccess
	 */
	private void openAction(AuthorAccess authorAccess, String file) {
		try {
			file = URI.replace("xmldb:exist://", "http://") + file;
			file = file.replace("xmlrpc", "webdav");
//			file = "oxygen:" + URI + file;
//			file = file.replace("xmlrpc/", "xmlrpc");
//			file = "oxygen:/eXist-Test$eXist-Test-Verbindung";
			authorAccess.getWorkspaceAccess().open(new URL(file));
			//dispose();
		} catch (MalformedURLException e){
			e.printStackTrace();
			//descriptionTable.add("could not open " + file); TODO
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
		// Don't care
		
	}
}