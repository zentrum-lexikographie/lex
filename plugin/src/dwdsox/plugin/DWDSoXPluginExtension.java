package dwdsox.plugin;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.StringReader;
import java.util.HashMap;
import java.util.LinkedList;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;

import org.exist.xmldb.EXistResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import dwdsox.plugin.actions.DWDSoXCreateDocument;
import dwdsox.plugin.actions.DWDSoXContentSearch;
import dwdsox.plugin.actions.DWDSoXMetaSearch;
import ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension;
import ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace;
import ro.sync.exml.workspace.api.standalone.ToolbarComponentsCustomizer;
import ro.sync.exml.workspace.api.standalone.ToolbarInfo;
import ro.sync.exml.workspace.api.standalone.ui.ToolbarButton;

public class DWDSoXPluginExtension implements WorkspaceAccessPluginExtension{

	private static String CONTENTSEARCH_ICONPATH = null;
	private static String METASEARCH_ICONPATH = null;
	private static String CREATEFILE_ICONPATH = null;
	private static String CONTENTSEARCH_XQUERYPATH = null;
	private static LinkedList<LinkedList<String>> CONTENTSEARCH_FILTER = null;
	private static String SERVERURL = null;
	private static String COLLECTION = null;  
	private static String METASEARCH_XQUERYPATH = null; 
	private static LinkedList<String> METASEARCH_TIMESTAMPNODES = null; 
	private static LinkedList<LinkedList<String>> METASEARCH_FILTER = null;
	private static String SETPERMISSIONS_XQUERYPATH = null;
	private static String CHECKID_XQUERYPATH = null;
	private static String STORECOLLECTION = null;
	private static HashMap<String, String> TEMPLATELIST = null;
	private static String GROUP = null;
	private static String PERMISSIONS = null;
	private static String METASEARCH_VALUEINDEXFILE = null;

	@SuppressWarnings("rawtypes")
	@Override
	public void applicationStarted(final StandalonePluginWorkspace pluginWorkspaceAccess) {

		final String pluginDirectory = DWDSoXPlugin.getInstance().getDescriptor().getBaseDir().getAbsolutePath();

		// read config file	
		DocumentBuilder dBuilder;

		boolean error = false;
		//get general config
		try {
			dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = dBuilder.parse(new File(pluginDirectory + "/config.xml"));
			doc.getDocumentElement().normalize();
			SERVERURL = doc.getElementsByTagName("databaseURL").item(0).getTextContent();
			COLLECTION = doc.getElementsByTagName("collection").item(0).getTextContent();
			//VALUEINDEXFILE = doc.getElementsByTagName("valueindexfile").item(0).getTextContent();
		} catch (Exception e) {
			error = true;
			e.printStackTrace();
		} 
		
		//get indexed values from database
		/*try {
			//pluginWorkspaceAccess.open(new URL(SERVERURL.replace("xmldb:exist://", "http://").replace("xmlrpc", "webdav") + VALUEINDEXFILE));
			URL url = new URL(SERVERURL.replace("xmldb:exist://", "http://").replace("xmlrpc", "webdav") + VALUEINDEXFILE);
			Scanner s = new Scanner(url.openStream());
		} catch (IOException e1) {
			e1.printStackTrace();
		}*/
		
		//get config for content search
		try {
			dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = dBuilder.parse(new File(pluginDirectory + "/config.xml"));
			doc.getDocumentElement().normalize();
			CONTENTSEARCH_ICONPATH = ((Element) doc.getElementsByTagName("contentSearch").item(0)).getElementsByTagName("icon").item(0).getTextContent();
			CONTENTSEARCH_ICONPATH = CONTENTSEARCH_ICONPATH.replace("${PluginDir}", pluginDirectory);
			CONTENTSEARCH_XQUERYPATH = ((Element) doc.getElementsByTagName("contentSearch").item(0)).getElementsByTagName("xqueryPath").item(0).getTextContent();
			CONTENTSEARCH_XQUERYPATH = CONTENTSEARCH_XQUERYPATH.replace("${PluginDir}", pluginDirectory);
			NodeList filters = ((Element)((Element) doc.getElementsByTagName("contentSearch").item(0)).getElementsByTagName("filters").item(0)).getElementsByTagName("filter");
			CONTENTSEARCH_FILTER = new LinkedList<LinkedList<String>>();
			for(int i=0; i<filters.getLength(); i++) {
				LinkedList<String> filter = new LinkedList<String>();
				filter.add(((Element)filters.item(i)).getAttribute("name"));
				filter.add(((Element)filters.item(i)).getAttribute("xpath"));
				NodeList items = ((Element) filters.item(i)).getElementsByTagName("item");
				for(int j=0; j<items.getLength(); j++) {
					filter.add(items.item(j).getTextContent());
				}
				CONTENTSEARCH_FILTER.add(filter);
			}
			
		} catch (Exception e) {
			error = true;
			e.printStackTrace();
		} 
		
		// get config for meta search
		try {
			dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = dBuilder.parse(new File(pluginDirectory + "/config.xml"));
			doc.getDocumentElement().normalize();
			METASEARCH_ICONPATH = ((Element) doc.getElementsByTagName("metaSearch").item(0)).getElementsByTagName("icon").item(0).getTextContent();
			METASEARCH_ICONPATH = METASEARCH_ICONPATH.replace("${PluginDir}", pluginDirectory);
			METASEARCH_XQUERYPATH = ((Element) doc.getElementsByTagName("metaSearch").item(0)).getElementsByTagName("xqueryPath").item(0).getTextContent();
			METASEARCH_XQUERYPATH = METASEARCH_XQUERYPATH.replace("${PluginDir}", pluginDirectory);
			METASEARCH_VALUEINDEXFILE = ((Element) doc.getElementsByTagName("metaSearch").item(0)).getElementsByTagName("indexedValues").item(0).getTextContent();
			NodeList filters = ((Element)((Element) doc.getElementsByTagName("metaSearch").item(0)).getElementsByTagName("filters").item(0)).getElementsByTagName("filter");
			METASEARCH_FILTER = new LinkedList<LinkedList<String>>();
			for(int i=0; i<filters.getLength(); i++) {
				LinkedList<String> filter = new LinkedList<String>();
				filter.add(((Element)filters.item(i)).getAttribute("xpath"));
				NodeList items = ((Element) filters.item(i)).getElementsByTagName("item");
				for(int j=0; j<items.getLength(); j++) {
					filter.add(items.item(j).getTextContent());
				}
				METASEARCH_FILTER.add(filter);
			}
			filters = ((Element)((Element) doc.getElementsByTagName("metaSearch").item(0)).getElementsByTagName("timestampNodes").item(0)).getElementsByTagName("timestampNode");
			METASEARCH_TIMESTAMPNODES = new LinkedList<String>();
			for(int i=0; i<filters.getLength(); i++) {
				METASEARCH_TIMESTAMPNODES.add(filters.item(i).getTextContent());
			}
			
			//get search values from exist database index
			final String driver = "org.exist.xmldb.DatabaseImpl";
			// initialize database driver
	        Class cl = Class.forName(driver);
	        Database database = (Database) cl.newInstance();
	        database.setProperty("create-database", "true");
	        DatabaseManager.registerDatabase(database);
	        Collection col = null;
	        XMLResource res = null;
	        try {    
	            // get the collection
	            col = DatabaseManager.getCollection(SERVERURL + COLLECTION);
	            col.setProperty(OutputKeys.INDENT, "no");
	            res = (XMLResource)col.getResource(METASEARCH_VALUEINDEXFILE);
	            dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
	            doc = dBuilder.parse(new InputSource(new StringReader((String) res.getContent())));
	            
	            //add filter from index values for meta search
	            filters = ((Element) ((Element) doc.getElementsByTagName("filters").item(0)).getElementsByTagName("MetaSearch").item(0)).getElementsByTagName("filter");
				for(int i=0; i<filters.getLength(); i++) {
					LinkedList<String> filter = new LinkedList<String>();
					filter.add(((Element)filters.item(i)).getAttribute("xpath"));
					NodeList items = ((Element) filters.item(i)).getElementsByTagName("item");
					for(int j=0; j<items.getLength(); j++) {
						filter.add(items.item(j).getTextContent());
					}
					METASEARCH_FILTER.add(filter);
				}
				
				//add filter from index values for meta search
	            filters = ((Element) ((Element) doc.getElementsByTagName("filters").item(0)).getElementsByTagName("ContentSearch").item(0)).getElementsByTagName("filter");
				for(int i=0; i<filters.getLength(); i++) {
					LinkedList<String> filter = new LinkedList<String>();
					filter.add(((Element)filters.item(i)).getAttribute("name"));
					filter.add(((Element)filters.item(i)).getAttribute("xpath"));
					NodeList items = ((Element) filters.item(i)).getElementsByTagName("item");
					for(int j=0; j<items.getLength(); j++) {
						filter.add(items.item(j).getTextContent());
					}
					CONTENTSEARCH_FILTER.add(filter);
				}
	            
	        } catch(Exception e) {
	        	JOptionPane.showMessageDialog(null,"Failed to access value index file from database: " + SERVERURL + COLLECTION + METASEARCH_VALUEINDEXFILE + "\n" + e.getLocalizedMessage(),"Error",JOptionPane.ERROR_MESSAGE);
	        } finally {
	            //dont forget to clean up!
	            if(res != null) {
	                try { ((EXistResource)res).freeResources(); } catch(XMLDBException xe) {xe.printStackTrace();}
	            }
	            if(col != null) {
	                try { col.close(); } catch(XMLDBException xe) {xe.printStackTrace();}
	            }
	        }
			
		} catch (Exception e) {
			error = true;
			e.printStackTrace();
		} 
		
		// get config for file creation
		try {
			dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = dBuilder.parse(new File(pluginDirectory + "/config.xml"));
			doc.getDocumentElement().normalize();
			CREATEFILE_ICONPATH = ((Element) doc.getElementsByTagName("createFile").item(0)).getElementsByTagName("icon").item(0).getTextContent();
			CREATEFILE_ICONPATH = CREATEFILE_ICONPATH.replace("${PluginDir}", pluginDirectory);
			SETPERMISSIONS_XQUERYPATH = ((Element) doc.getElementsByTagName("createFile").item(0)).getElementsByTagName("setPermissionsXQueryPath").item(0).getTextContent();
			SETPERMISSIONS_XQUERYPATH = SETPERMISSIONS_XQUERYPATH.replace("${PluginDir}", pluginDirectory);
			CHECKID_XQUERYPATH = ((Element) doc.getElementsByTagName("createFile").item(0)).getElementsByTagName("IDCheckXQueryPath").item(0).getTextContent();
			CHECKID_XQUERYPATH = CHECKID_XQUERYPATH.replace("${PluginDir}", pluginDirectory);
			STORECOLLECTION = ((Element) doc.getElementsByTagName("createFile").item(0)).getElementsByTagName("storeCollection").item(0).getTextContent();
			NodeList templates = ((Element)((Element) doc.getElementsByTagName("createFile").item(0)).getElementsByTagName("templates").item(0)).getElementsByTagName("template");
			TEMPLATELIST = new HashMap<String,String>();
			for(int i=0; i<templates.getLength(); i++)
				TEMPLATELIST.put(((Element) templates.item(i)).getAttribute("name"),templates.item(i).getTextContent().replace("${PluginDir}", pluginDirectory));
			GROUP = ((Element) doc.getElementsByTagName("createFile").item(0)).getElementsByTagName("group").item(0).getTextContent();
			PERMISSIONS = ((Element) doc.getElementsByTagName("createFile").item(0)).getElementsByTagName("permissions").item(0).getTextContent();
		} catch (Exception e) {
			error = true;
			e.printStackTrace();
		} 
		final boolean readError = error;
		
		pluginWorkspaceAccess.addToolbarComponentsCustomizer(new ToolbarComponentsCustomizer() {
			@Override
			public void customizeToolbar(ToolbarInfo toolbar) {
				if(!toolbar.getToolbarID().equals("DWDSoXPluginExtensionToolbar")) return;
				
				toolbar.setTitle("DWDSoX - Plugin");
				
				JComponent[] buttons = new JComponent[1];
				if(readError) buttons[0] = new JLabel("Error reading config.xml");
				else {
					buttons = new JComponent[3];
					
					ToolbarButton contentSearch = new ToolbarButton(new ContentSearchAction(pluginWorkspaceAccess,new ImageIcon(CONTENTSEARCH_ICONPATH)), false);
					contentSearch.setToolTipText("Suche nach XML-Inhalten");
					buttons[0] = contentSearch;
					ToolbarButton metaSearch = new ToolbarButton(new MetaSearchAction(pluginWorkspaceAccess,new ImageIcon(METASEARCH_ICONPATH)), false);
					metaSearch.setToolTipText("Suche nach XML-Metadaten");
					buttons[1] = metaSearch;
					ToolbarButton createFile = new ToolbarButton(new CreateFileAction(pluginWorkspaceAccess,new ImageIcon(CREATEFILE_ICONPATH)), false);
					createFile.setToolTipText("Neue Datei in eXist-Datenbank erstellen");
					buttons[2] = createFile;
				}
				toolbar.setComponents(buttons);
			}
		});
		
		// set options
		pluginWorkspaceAccess.setGlobalObjectProperty("editor.check.errors.on.save", Boolean.TRUE);
	}

	@Override
	public boolean applicationClosing() {
		return true;
	}
	
	class CreateFileAction extends AbstractAction {
		private static final long serialVersionUID = 1L;
		StandalonePluginWorkspace WORKSPACEACCESS = null;
	    public CreateFileAction(StandalonePluginWorkspace pluginWorkspaceAccess,ImageIcon icon) {
	        super("",icon);
	        WORKSPACEACCESS = pluginWorkspaceAccess;
	    }
	    public void actionPerformed(ActionEvent e) {
			new DWDSoXCreateDocument(WORKSPACEACCESS, CHECKID_XQUERYPATH, SETPERMISSIONS_XQUERYPATH, SERVERURL, STORECOLLECTION, TEMPLATELIST, GROUP, PERMISSIONS);
	    }
	}
	class ContentSearchAction extends AbstractAction {
		private static final long serialVersionUID = 1L;
		StandalonePluginWorkspace WORKSPACEACCESS = null;
	    public ContentSearchAction(StandalonePluginWorkspace pluginWorkspaceAccess,ImageIcon icon) {
	        super("",icon);
	        WORKSPACEACCESS = pluginWorkspaceAccess;
	    }
	    public void actionPerformed(ActionEvent e) {
			new DWDSoXContentSearch(WORKSPACEACCESS, CONTENTSEARCH_XQUERYPATH, SERVERURL, COLLECTION, CONTENTSEARCH_FILTER);
	    }
	}
	class MetaSearchAction extends AbstractAction {
		private static final long serialVersionUID = 1L;
		StandalonePluginWorkspace WORKSPACEACCESS = null;
	    public MetaSearchAction(StandalonePluginWorkspace pluginWorkspaceAccess,ImageIcon icon) {
	        super("",icon);
	        WORKSPACEACCESS = pluginWorkspaceAccess;
	    }
	    public void actionPerformed(ActionEvent e) {
			new DWDSoXMetaSearch(WORKSPACEACCESS, METASEARCH_TIMESTAMPNODES, METASEARCH_FILTER, METASEARCH_XQUERYPATH, SERVERURL, COLLECTION);
	    }
	}
}
