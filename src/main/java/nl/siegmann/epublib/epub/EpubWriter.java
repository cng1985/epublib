package nl.siegmann.epublib.epub;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import nl.siegmann.epublib.Constants;
import nl.siegmann.epublib.bookprocessor.BookProcessor;
import nl.siegmann.epublib.bookprocessor.HtmlCleanerBookProcessor;
import nl.siegmann.epublib.bookprocessor.MissingResourceBookProcessor;
import nl.siegmann.epublib.bookprocessor.SectionHrefSanityCheckBookProcessor;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

/**
 * Generates an epub file. Not thread-safe, single use object.
 * 
 * @author paul
 *
 */
public class EpubWriter {
	
	@SuppressWarnings("unused")
	private final static Logger log = Logger.getLogger(EpubWriter.class); 
	
	private HtmlProcessor htmlProcessor;
	private List<BookProcessor> bookProcessingPipeline;
	
	public EpubWriter() {
		this.bookProcessingPipeline = setupBookProcessingPipeline();
	}
	
	
	private List<BookProcessor> setupBookProcessingPipeline() {
		List<BookProcessor> result = new ArrayList<BookProcessor>();
		result.addAll(Arrays.asList(new BookProcessor[] {
			new SectionHrefSanityCheckBookProcessor(),
			new HtmlCleanerBookProcessor(),
			new MissingResourceBookProcessor()
		}));
		return result;
	}
	
	
	public void write(Book book, OutputStream out) throws IOException, XMLStreamException, FactoryConfigurationError {
		book = processBook(book);
		ZipOutputStream resultStream = new ZipOutputStream(out);
		writeMimeType(resultStream);
		writeContainer(resultStream);
		writeResources(book, resultStream);
		writeNcxDocument(book, resultStream);
		writePackageDocument(book, resultStream);
		resultStream.close();
	}
	
	private Book processBook(Book book) {
		for(BookProcessor bookProcessor: bookProcessingPipeline) {
			book = bookProcessor.processBook(book, this);
		}
		return book;
	}


	private void writeResources(Book book, ZipOutputStream resultStream) throws IOException {
		for(Resource resource: book.getResources()) {
			resultStream.putNextEntry(new ZipEntry("OEBPS/" + resource.getHref()));
			InputStream inputStream = resource.getInputStream();
			IOUtils.copy(inputStream, resultStream);
			inputStream.close();
		}
	}
	
	private void writePackageDocument(Book book, ZipOutputStream resultStream) throws XMLStreamException, IOException {
		resultStream.putNextEntry(new ZipEntry("OEBPS/content.opf"));
		XMLOutputFactory xmlOutputFactory = createXMLOutputFactory();
		Writer out = new OutputStreamWriter(resultStream);
		XMLStreamWriter xmlStreamWriter = xmlOutputFactory.createXMLStreamWriter(out);
		PackageDocument.write(this, xmlStreamWriter, book);
		xmlStreamWriter.flush();
	}

	private void writeNcxDocument(Book book, ZipOutputStream resultStream) throws IOException, XMLStreamException, FactoryConfigurationError {
		NCXDocument.write(book, resultStream);
	}

	private void writeContainer(ZipOutputStream resultStream) throws IOException {
		resultStream.putNextEntry(new ZipEntry("META-INF/container.xml"));
		Writer out = new OutputStreamWriter(resultStream);
		out.write("<?xml version=\"1.0\"?>\n");
		out.write("<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n");
		out.write("\t<rootfiles>\n");
		out.write("\t\t<rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n");
		out.write("\t</rootfiles>\n");
		out.write("</container>");
		out.flush();
	}

	private void writeMimeType(ZipOutputStream resultStream) throws IOException {
		resultStream.putNextEntry(new ZipEntry("mimetype"));
		resultStream.write((Constants.MediaTypes.epub).getBytes());
	}

	XMLEventFactory createXMLEventFactory() {
		return XMLEventFactory.newInstance();
	}
	
	XMLOutputFactory createXMLOutputFactory() {
		XMLOutputFactory result = XMLOutputFactory.newInstance();
//		result.setProperty(name, value)
		return result;
	}
	
	String getNcxId() {
		return "ncx";
	}
	
	String getNcxHref() {
		return "toc.ncx";
	}

	String getNcxMediaType() {
		return "application/x-dtbncx+xml";
	}

	public HtmlProcessor getHtmlProcessor() {
		return htmlProcessor;
	}


	public void setHtmlProcessor(HtmlProcessor htmlProcessor) {
		this.htmlProcessor = htmlProcessor;
	}


	public List<BookProcessor> getBookProcessingPipeline() {
		return bookProcessingPipeline;
	}


	public void setBookProcessingPipeline(List<BookProcessor> bookProcessingPipeline) {
		this.bookProcessingPipeline = bookProcessingPipeline;
	}
}