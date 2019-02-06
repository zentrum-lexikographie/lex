package dwdsox.plugin;

import ro.sync.exml.plugin.Plugin;
import ro.sync.exml.plugin.PluginDescriptor;

public class DWDSoXPlugin extends Plugin{
	/**
	    * Plugin instance.
	    */
	    private static DWDSoXPlugin instance = null;  
	    
	    /**
	    * UppercasePlugin constructor.
	    * 
	    * @param descriptor Plugin descriptor object.
	    */
	    public DWDSoXPlugin(PluginDescriptor descriptor) {
	    	super(descriptor);
	    		    
	        if (instance != null) {
	            throw new IllegalStateException("Already instantiated !");
	        }    
	        instance = this;
	    }
	    
	    /**
	    * Get the plugin instance.
	    * 
	    * @return the shared plugin instance.
	    */
	    public static DWDSoXPlugin getInstance() {
	        return instance;
	    }
	}