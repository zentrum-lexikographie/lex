
package simple.documentation.framework;

import ro.sync.contentcompletion.xml.SchemaManagerFilter;
import ro.sync.ecss.extensions.api.AuthorExtensionStateListener;

public class DWDSoXExtensionsBundle extends ro.sync.ecss.extensions.api.ExtensionsBundle{
	private DWDSoXAuthorExtensionStateListener authorExtensionStateListener;

	@Override
	public String getDescription() {
		return "A costum extension bundle extended by a context-sensitive grey-out of the lexicography menu and a manipulation of the content completion";
	}

	@Override
	public String getDocumentTypeID() {
		return "DWDS.Framework.document.type";
	}

	public AuthorExtensionStateListener createAuthorExtensionStateListener() {
		authorExtensionStateListener = new DWDSoXAuthorExtensionStateListener();
		return authorExtensionStateListener;
	}
	
	public SchemaManagerFilter createSchemaManagerFilter() {
		DWDSoXSchemaManagerFilter schemaManagerFilter = new DWDSoXSchemaManagerFilter(); 
		//give authorAccess from ExtensionsStateListener to SchemaManagerFilter
		schemaManagerFilter.setAuthorActionSet(authorExtensionStateListener.getAuthorActionSet());
		return schemaManagerFilter;
	}
	
	public DWDSoXAuthorSchemaAwareEditingHandler getAuthorSchemaAwareEditingHandler() {
		return new DWDSoXAuthorSchemaAwareEditingHandler();
		
	}
}