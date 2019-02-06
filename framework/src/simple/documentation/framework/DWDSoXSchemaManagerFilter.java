package simple.documentation.framework;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import ro.sync.contentcompletion.xml.CIAttribute;
import ro.sync.contentcompletion.xml.CIElement;
import ro.sync.contentcompletion.xml.CIValue;
import ro.sync.contentcompletion.xml.Context;
import ro.sync.contentcompletion.xml.SchemaManagerFilter;
import ro.sync.contentcompletion.xml.WhatAttributesCanGoHereContext;
import ro.sync.contentcompletion.xml.WhatElementsCanGoHereContext;
import ro.sync.contentcompletion.xml.WhatPossibleValuesHasAttributeContext;


public class DWDSoXSchemaManagerFilter implements SchemaManagerFilter {
	
	private Set<String> authorExtensionActions = new HashSet<String>();	//list of possible author actions
	
	@Override
	public List<CIElement> filterElements(List<CIElement> elementList,
			WhatElementsCanGoHereContext context) {
		List<CIElement> newElementList = new LinkedList<CIElement>();
		
		for(String actionName	 : authorExtensionActions) {
			
			String[] tokenized = actionName.split(" ");
			
			//add all elements of the old element list to the new element list, that matches with an author action
	        for(CIElement element : elementList) {

	        	for(String token : tokenized) {
	        		if(token.equals(element.getQName())) {
	        			newElementList.add(element);
	        			break;
	        		}
	          	}
	        }
		}
        return newElementList;
	}
	
	//get all possible author actions
	public void setAuthorActionSet(Set<String> authorActionList) {
		authorExtensionActions = authorActionList;
	}
	
	
	
	
	@Override
	public String getDescription() {
		return null;
	}

	@Override
	public List<CIValue> filterAttributeValues(List<CIValue> list,
			WhatPossibleValuesHasAttributeContext arg1) {
		return list;
	}

	@Override
	public List<CIAttribute> filterAttributes(List<CIAttribute> arg0,
			WhatAttributesCanGoHereContext arg1) {
		return arg0;
	}

	@Override
	public List<CIValue> filterElementValues(List<CIValue> list, Context arg1) {
		return list;
	}

}
