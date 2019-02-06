
package operations;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import javax.swing.JOptionPane;
import javax.swing.text.BadLocationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import ro.sync.ecss.extensions.api.ArgumentDescriptor;
import ro.sync.ecss.extensions.api.ArgumentsMap;
import ro.sync.ecss.extensions.api.AuthorAccess;
import ro.sync.ecss.extensions.api.AuthorOperation;
import ro.sync.ecss.extensions.api.AuthorOperationException;
import ro.sync.ecss.extensions.api.node.AuthorDocumentFragment;


public class DWDSWebViewXSLT implements AuthorOperation{

	/**
	 * Argument describing the URL.
	 */
	private static final String ARGUMENT_XSLT = "XSLT-Skript";
	/**
	 * Argument describing the path of the template html file.
	 */
	private static final String TEMPLATEHTML_URL = "HTML-Template-Datei";
	/**
	 * Argument describing the path of the target html file.
	 */
	private static final String TARGETHTML_URL = "HTML-Ergebnis-Datei";

	/**
	 * Arguments.
	 */
	private static final ArgumentDescriptor[] ARGUMENTS = new ArgumentDescriptor[] {
		new ArgumentDescriptor(
				ARGUMENT_XSLT,
				ArgumentDescriptor.TYPE_STRING,
				"Der Pfad zum XSLT-Skript, etwa: \n" +
				"${frameworkDir}/scripts/dwdswb.xml"),
		new ArgumentDescriptor(
				TEMPLATEHTML_URL,
				ArgumentDescriptor.TYPE_STRING,
				"Pfad zur HTML-Datei, die das statische Template enthält, z.B.: " +
				"${frameworkDir}/scripts/webViewTemplate.html \n"),
		new ArgumentDescriptor(
				TARGETHTML_URL,
				ArgumentDescriptor.TYPE_STRING,
				"Pfad zur temporären HTML-Datei, z.B.: " +
				"${frameworkDir}/scripts/webViewResult.html \n")
	};

	/**
	 * @see ro.sync.ecss.extensions.api.AuthorOperation#doOperation(AuthorAccess, ArgumentsMap)
	 */
	public void doOperation(AuthorAccess authorAccess, ArgumentsMap args) throws AuthorOperationException {
		// Die übergebenen Argumente werden eingelesen ..
		Object xsltArgVal = args.getArgumentValue(ARGUMENT_XSLT);
		Object targetHTMLArgVal = args.getArgumentValue(TARGETHTML_URL);
		Object templateHTMLArgVal = args.getArgumentValue(TEMPLATEHTML_URL);
		
		// .. und überprüft.
		if (xsltArgVal != null && 
			xsltArgVal instanceof String &&
			templateHTMLArgVal != null && 
			templateHTMLArgVal instanceof String &&
			targetHTMLArgVal != null && 
			targetHTMLArgVal instanceof String) {
			
			//get the content of the whole document
			String originalXml = "Fehler aufgetreten";
			try {
				AuthorDocumentFragment documentFragment = authorAccess.getDocumentController().createDocumentFragment(authorAccess.getDocumentController().getAuthorDocumentNode().getStartOffset(), authorAccess.getDocumentController().getAuthorDocumentNode().getEndOffset());
				originalXml = authorAccess.getDocumentController().serializeFragmentToXML(documentFragment);
			} catch (BadLocationException e) {
		        JOptionPane.showMessageDialog(null,"Fehler beim Auslesen des Dokuments","Error",JOptionPane.ERROR_MESSAGE);
				e.printStackTrace();
			}
			if(originalXml.length()==0) {
				JOptionPane.showMessageDialog(null,"Fehler beim Auslesen des Dokuments","Error",JOptionPane.ERROR_MESSAGE);
				return;
			}
			StringReader originalXMLreader = new StringReader(originalXml);
			
			//send document content to url and retrieve web view content
//			String responseString = "";
//			DefaultHttpClient httpclient = new DefaultHttpClient();
//			HttpPost httpPost = new HttpPost((String)xsltArgVal);
//			ArrayList <NameValuePair> nvps = new ArrayList <NameValuePair>();
//			nvps.add(new BasicNameValuePair("article", originalXml));
//			try {
//				httpPost.setEntity(new UrlEncodedFormEntity(nvps,"UTF-8"));
//				HttpResponse responseObject = httpclient.execute(httpPost);
//				responseString += IOUtils.toString(responseObject.getEntity().getContent(),"utf-8");
//				
//				//unescape html character entities
//				responseString = StringEscapeUtils.unescapeHtml4(responseString);
//				
//				//link local refrences to server
//				responseString = responseString.replace("/static/", "http://alpha.dwds.de/static/");
//			} catch (Exception e) {
//				e.printStackTrace();
//				responseString += e.getMessage() + "\n";
//			} finally {
//			    httpPost.releaseConnection();
//			}
			
			//transform content with saxon xsl transformer
		    /** 
		     * Simple transformation method. 
		     * @param sourcePath - Absolute path to source xml file. 
		     * @param xsltPath - Absolute path to xslt file. 
		     * @param resultDir - Directory where you want to put resulting files. 
		     */  
			
			String result = "Fehler aufgetreten";
			
	        TransformerFactory tFactory = TransformerFactory.newInstance();  
	        try {
	            Transformer transformer =  
	                tFactory.newTransformer(new StreamSource(new File((String) xsltArgVal)));  
	            
	            StringWriter resultWriter = new StringWriter();
	            transformer.transform(new StreamSource(originalXMLreader),  
	                                  new StreamResult(resultWriter));  
	            result = resultWriter.toString();
	        } catch (Exception e) {  
	        	JOptionPane.showMessageDialog(null,e.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);
	        }

			//insert transformed content in template text
	        BufferedReader br;
			try {
				br = new BufferedReader(new FileReader((String) templateHTMLArgVal));
		        try {
		            StringBuilder sb = new StringBuilder();
		            String line = br.readLine();
	
		            while (line != null) {
		                sb.append(line);
		                sb.append(System.lineSeparator());
		                line = br.readLine();
		            }
		            String templateString = sb.toString();
		            result = templateString.replace("[ARTICLE]", result);
		        } finally {
		            br.close();
		        }
			} catch (Exception e1) {
				e1.printStackTrace();
			}

			//save to temporary file
			try {
				File tempHTML = new File((String) targetHTMLArgVal);
				PrintWriter out = new PrintWriter(tempHTML,"utf-8");
				out.println(result);
				out.close();

				//open in standard Browser
				if(Desktop.isDesktopSupported())
					Desktop.getDesktop().open(tempHTML);
				else {
		        	JOptionPane.showMessageDialog(null,"Die Desktop-Library scheint nicht installiert oder funktionsuntüchtig zu sein. Daher kann der Browser nicht automatisch geöffnet werden. Dafür richten Sie bitte die java.awt.Desktop - Bilbiothek ein. Unter Ubuntu finden Sie diese Bibliothek im Paket 'libgnome2-0'.\n" + 
		        			"Um die Voranschau ohne Desktop-Library anzusehen, öffnen Sie die Datei: \n" + (String) targetHTMLArgVal,"Information",JOptionPane.WARNING_MESSAGE);
				}
				
				// try browsers until one works
				//StartBrowser.open((String)tempHTMLArgVal);
				
			} catch (IOException e) {
	        	JOptionPane.showMessageDialog(null,"Could not write to target File","Error",JOptionPane.ERROR_MESSAGE);
			}
			//Öffne neue Datei mit empfangenem Text, bzw. öffne Online-Datei
			//authorAccess.getWorkspaceAccess().createNewEditor("html", null, webViewXml);

		} else {
			throw new IllegalArgumentException(
					"One or more of the argument values are not declared, they are: " + 
					"url - " + xsltArgVal);
		}
	}

	/**
	 * @see ro.sync.ecss.extensions.api.AuthorOperation#getArguments()
	 */
	public ArgumentDescriptor[] getArguments() {
		return ARGUMENTS;
	}

	/**
	 * @see ro.sync.ecss.extensions.api.AuthorOperation#getDescription()
	 */
	public String getDescription() {
		return "Öffnet einen Dialog, in dem anhand eines von mehreren Attributen und einem von dessen möglichen Werten, Dateien aufgelistet werden.";
	}
}
