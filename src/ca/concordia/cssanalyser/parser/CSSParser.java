package ca.concordia.cssanalyser.parser;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.css.sac.InputSource;
import org.w3c.css.sac.Parser;
import org.w3c.css.sac.helpers.ParserFactory;

import ca.concordia.cssanalyser.cssmodel.StyleSheet;


public class CSSParser {

	private CSSDocumentHandler docHandler;

	private Parser parser;
	
	private Logger LOGGER = LoggerFactory.getLogger(CSSParser.class);
	
	static {
		System.setProperty("org.w3c.css.sac.parser", "org.w3c.flute.parser.Parser");
	}

	public CSSParser() {

	}
	
	public StyleSheet parseCSSString(String css) {
		try {
			return parseStreamCSS(new ByteArrayInputStream(css.getBytes()));
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	

	public StyleSheet parseExternalCSS(String path) throws Exception {
		try {
			LOGGER.warn("Parsing " + path);
			URL uri = new URL("file", null, -1, path);
			StyleSheet styleSheet = parseStreamCSS(uri.openStream());
			styleSheet.setPath(path);
			LOGGER.warn("Parsed " + path);
			return styleSheet;
		}
		catch (Exception e) {
			LOGGER.warn(e.toString());
			throw e;
		}
	}
	
	public StyleSheet parseStreamCSS(InputStream inputStream) throws Exception {
				
		StyleSheet styleSheet = new StyleSheet();

		InputSource source = new InputSource();

		source.setByteStream(inputStream);
		//source.setURI(uri.toString());
		ParserFactory factory = new ParserFactory();

		parser = factory.makeParser();

		docHandler = new CSSDocumentHandler(styleSheet);

		parser.setDocumentHandler(docHandler);

		parser.parseStyleSheet(source);
			
		inputStream.close();

		return styleSheet;
	}
}
