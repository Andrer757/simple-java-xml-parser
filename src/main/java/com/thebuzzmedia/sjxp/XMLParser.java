/**   
 * Copyright 2011 The Buzz Media, LLC
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thebuzzmedia.sjxp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import com.thebuzzmedia.sjxp.rule.IRule;
import com.thebuzzmedia.sjxp.rule.IRule.Type;

/*
 * TODO: Consider making this class thread-safe, or atleast behave itself better
 * when multiple threads call it.
 * 
 * Either synchronize the methods or keep track of the currently-executing thread
 * and if another thread calls parse, throw an exception with a reminder to the
 * caller that this class is not thread-safe?
 */

/**
 * Class used to define a parser that makes parsing using the performance of an
 * XML Pull Parser with the ease of XPath-like expressions possible.
 * 
 * <h3>Thread Safety</h3> This class is not thread-safe, however instances of
 * {@link XMLParser} can safely be re-used to parse multiple files back-to-back.
 * 
 * @param <T>
 *            The class type of any user-supplied object that the caller wishes
 *            to be passed through from one of the {@link XMLParser}'s
 *            <code>parse</code> methods directly to the handler when an
 *            {@link IRule} matches. This is typically a data storage mechanism
 *            like a DAO or cache used to store the parsed value in some
 *            valuable way, but it can ultimately be anything. If you do not
 *            need to make use of the user object, there is no need to
 *            parameterize the class.
 * 
 * @author Riyad Kalla (software@thebuzzmedia.com)
 */
public class XMLParser<T> {
	/**
	 * Flag used to indicate if debugging output has been enabled by setting the
	 * "sjxp.debug" system property to <code>true</code>. This value will be
	 * <code>false</code> if the "sjxp.debug" system property is undefined or
	 * set to <code>false</code>.
	 * <p/>
	 * This system property can be set on startup with:<br/>
	 * <code>
	 * -Dsjxp.debug=true
	 * </code> or by calling {@link System#setProperty(String, String)} before
	 * this class is loaded.
	 * <p/>
	 * This is <code>false</code> by default.
	 */
	public static final Boolean DEBUG = Boolean.getBoolean("sjxp.debug");

	/**
	 * Flag used to indicate if this parser should be namespace-aware by setting
	 * the "sjxp.namespaces" system property to <code>true</code>. This value
	 * will be <code>true</code> if the "sjxp.namespaces" system property is
	 * undefined. Namespace awareness can only be disabled by setting this
	 * system property to <code>false</code>.
	 * <p/>
	 * <strong>NOTE</strong>: If you intentionally disable namespace awareness,
	 * any {@link IRule} you provide that uses namespace qualified values (e.g.
	 * [http://w3.org/text]book) will fail to match as the parser can no longer
	 * see namespace URIs.
	 * <p/>
	 * This system property can be set on startup with:<br/>
	 * <code>
	 * -Dsjxp.namespaces=true
	 * </code> or by calling {@link System#setProperty(String, String)} before
	 * this class is loaded.
	 * <p/>
	 * This is <code>true</code> by default.
	 */
	public static final Boolean ENABLE_NAMESPACES = (System
			.getProperty("sjxp.namespaces") == null ? Boolean.TRUE : Boolean
			.getBoolean("sjxp.namespaces"));

	/**
	 * Flag used to indicate if this parser should validate the parsed XML
	 * against the references DTD or XML Schema by setting the "sjxp.validation"
	 * system property to <code>true</code>. This value will be
	 * <code>false</code> if the "sjxp.validation" system property is undefined
	 * or set to <code>false</code>.
	 * <p/>
	 * This system property can be set on startup with:<br/>
	 * <code>
	 * -Dsjxp.validation=true
	 * </code> or by calling {@link System#setProperty(String, String)} before
	 * this class is loaded.
	 * <p/>
	 * This is <code>false</code> by default.
	 */
	public static final Boolean ENABLE_VALIDATION = Boolean
			.getBoolean("sjxp.validation");

	/**
	 * Prefix to every log message this library logs. Using a well-defined
	 * prefix helps make it easier both visually and programmatically to scan
	 * log files for messages produced by this library.
	 * <p/>
	 * The value is "[sjxp] " (including the space).
	 */
	public static final String LOG_MESSAGE_PREFIX = "[sjxp] ";

	/**
	 * Singleton {@link XmlPullParserFactory} instance used to create new
	 * underlying {@link XmlPullParser} instances for each instance of
	 * {@link XMLParser}.
	 */
	public static final XmlPullParserFactory XPP_FACTORY;

	/**
	 * Static initializer used to init the {@link XmlPullParserFactory} with the
	 * configured namespace and validation settings.
	 */
	static {
		if (DEBUG)
			log("Debug output ENABLED");

		try {
			XPP_FACTORY = XmlPullParserFactory.newInstance();

			// Configure pull parser features
			XPP_FACTORY.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,
					ENABLE_NAMESPACES);
			XPP_FACTORY.setFeature(XmlPullParser.FEATURE_VALIDATION,
					ENABLE_VALIDATION);

			if (DEBUG)
				log("XmlPullParserFactory configured [namespaces=%s, validation=%s]",
						ENABLE_NAMESPACES, ENABLE_VALIDATION);
		} catch (XmlPullParserException e) {
			throw new RuntimeException(
					"An exception occurred while calling XmlPullParserFactory.newInstance(). A library providing the impl of the XML Pull Parser spec (e.g. XPP3 or Android SDK) must be available at runtime.",
					e);
		}
	}

	/**
	 * Helper method used to ensure a message is loggable before it is logged
	 * and then pre-pend a universal prefix to all log messages generated by
	 * this library to make the log entries easy to parse visually or
	 * programmatically.
	 * <p/>
	 * If a message cannot be logged (logging is disabled) then this method
	 * returns immediately.
	 * <p/>
	 * <strong>NOTE</strong>: Because Java will auto-box primitive arguments
	 * into Objects when building out the <code>params</code> array, care should
	 * be taken not to call this method with primitive values unless
	 * {@link #DEBUG} is <code>true</code>; otherwise the VM will be spending
	 * time performing unnecessary auto-boxing calculations.
	 * 
	 * @param message
	 *            The log message in <a href=
	 *            "http://download.oracle.com/javase/6/docs/api/java/util/Formatter.html#syntax"
	 *            >format string syntax</a> that will be logged.
	 * @param params
	 *            The parameters that will be swapped into all the place holders
	 *            in the original messages before being logged.
	 * 
	 * @see #LOG_MESSAGE_PREFIX
	 */
	protected static void log(String message, Object... params) {
		if (DEBUG)
			System.out.printf(LOG_MESSAGE_PREFIX + message + '\n', params);
	}

	private String toStringCache;
	private boolean continueParsing = true;

	private Location location;
	private XmlPullParser xpp;

	private Map<String, List<IRule<T>>> tagRuleMap;
	private Map<String, List<IRule<T>>> attrRuleMap;
	private Map<String, List<IRule<T>>> charRuleMap;

	/**
	 * Create a new parser that uses the given {@link IRule}s when parsing any
	 * XML content.
	 * 
	 * @param rules
	 *            The rules applied to any parsed content.
	 * 
	 * @throws IllegalArgumentException
	 *             if <code>rules</code> is <code>null</code> or empty.
	 * @throws XMLParserException
	 *             if the {@link #XPP_FACTORY} is unable to create a new
	 *             {@link XmlPullParser} instance and throws an exception.
	 */
	public XMLParser(IRule<T>... rules) throws IllegalArgumentException,
			XMLParserException {
		if (rules == null || rules.length == 0)
			throw new IllegalArgumentException(
					"rules cannot be null or empty, you must provide at least 1 rule to execute otherwise parsing will do nothing.");

		location = new Location();

		try {
			xpp = XPP_FACTORY.newPullParser();
		} catch (XmlPullParserException e) {
			throw new XMLParserException(
					"An exception occurred while trying to create a new XmlPullParser instance using the XmlPullParserFactory.",
					e);
		}

		// Load all the rules
		initRules(rules);
	}

	/**
	 * Overridden to provide a nicely formatted representation of the parser for
	 * easy debugging.
	 * <p/>
	 * As an added bonus, since {@link XMLParser}s are intended to be immutable,
	 * the result of <code>toString</code> is cached on the first call and the
	 * cache returned every time to avoid re-computing the completed
	 * {@link String}.
	 * 
	 * @return a nicely formatted representation of the parser for easy
	 *         debugging.
	 */
	@Override
	public synchronized String toString() {
		if (toStringCache == null) {
			toStringCache = this.getClass().getName() + "[attributeRules="
					+ attrRuleMap + ", characterRules=" + charRuleMap + "]";
		}

		return toStringCache;
	}

	/**
	 * Used to indicate to the parser that you would like it to stop parsing.
	 * <p/>
	 * Internally the parser uses a simple <code>boolean</code> to indicate if
	 * it should keep parsing. A call to this method sets the boolean value to
	 * <code>false</code> which the parser checks at the next parse event and
	 * then stops.
	 * <p/>
	 * This is a safe operation that simply flips a flag to tell the underlying
	 * {@link XmlPullParser} to stop working after it's done with its current
	 * parse event and return from whichever <code>parse</code> method was
	 * called.
	 */
	public void stop() {
		continueParsing = false;
	}

	/**
	 * Parse the XML out of the given stream matching the {@link IRule}s
	 * provided when the {@link XMLParser} was instantiated.
	 * <p/>
	 * The underlying {@link XmlPullParser} will attempt to determine the
	 * stream's encoding based on the pull parser spec or fall back to a default
	 * of UTF-8.
	 * <p/>
	 * This class will make no attempt at closing the given {@link InputStream},
	 * the caller must take care to clean up that resource.
	 * <h3>Stopping Parsing</h3>
	 * Parsing can be safely stopped by calling {@link #stop()}. This allows
	 * {@link IRule} implementations control over stopping parsing, for example,
	 * if an arbitrary threshold is hit. A followup call to any of the
	 * <code>parse</code> methods will reset the stopped state.
	 * 
	 * @param source
	 *            The stream that XML content will be read out of.
	 * 
	 * @throws IllegalArgumentException
	 *             if <code>source</code> is <code>null</code>.
	 * @throws XMLParserException
	 *             if any error occurs with the underlying stream during parsing
	 *             of if the XML content itself is malformed and the underlying
	 *             pull parser cannot parse it.
	 */
	public void parse(InputStream source) throws IllegalArgumentException,
			XMLParserException {
		try {
			parse(source, null, null);
		} catch (UnsupportedEncodingException e) {
			// no-op, this should never happen as null is a valid encoding.
		}
	}

	/**
	 * Parse the XML out of the given stream matching the {@link IRule}s
	 * provided when the {@link XMLParser} was instantiated.
	 * <p/>
	 * The underlying {@link XmlPullParser} will attempt to determine the
	 * stream's encoding based on the pull parser spec or fall back to a default
	 * of UTF-8.
	 * <p/>
	 * This class will make no attempt at closing the given {@link InputStream},
	 * the caller must take care to clean up that resource.
	 * <h3>Stopping Parsing</h3>
	 * Parsing can be safely stopped by calling {@link #stop()}. This allows
	 * {@link IRule} implementations control over stopping parsing, for example,
	 * if an arbitrary threshold is hit. A followup call to any of the
	 * <code>parse</code> methods will reset the stopped state.
	 * 
	 * @param source
	 *            The stream that XML content will be read out of.
	 * @param userObject
	 *            The user-supplied object passed through from this parse method
	 *            to the matching {@link IRule}'s <code>handleXXX</code> method
	 *            when a match is found, or <code>null</code> if no user object
	 *            is needed. Passing through a user-object is just meant as a
	 *            convenience for giving the handler methods on the
	 *            {@link IRule}'s access to objects like DAOs that can be used
	 *            to persist or process parsed data easily.
	 * 
	 * @throws IllegalArgumentException
	 *             if <code>source</code> is <code>null</code>.
	 * @throws XMLParserException
	 *             if any error occurs with the underlying stream during parsing
	 *             of if the XML content itself is malformed and the underlying
	 *             pull parser cannot parse it.
	 */
	public void parse(InputStream source, T userObject)
			throws IllegalArgumentException, XMLParserException {
		try {
			parse(source, null, userObject);
		} catch (UnsupportedEncodingException e) {
			// no-op, this should never happen as null is a valid encoding.
		}
	}

	/**
	 * Parse the XML out of the given stream (producing content matching the
	 * given encoding) matching the {@link IRule}s provided when the
	 * {@link XMLParser} was instantiated.
	 * <p/>
	 * This class will make no attempt at closing the given {@link InputStream},
	 * the caller must take care to clean up that resource.
	 * <h3>Stopping Parsing</h3>
	 * Parsing can be safely stopped by calling {@link #stop()}. This allows
	 * {@link IRule} implementations control over stopping parsing, for example,
	 * if an arbitrary threshold is hit. A followup call to any of the
	 * <code>parse</code> methods will reset the stopped state.
	 * 
	 * @param source
	 *            The stream that XML content will be read out of.
	 * @param encoding
	 *            The character encoding (e.g. "UTF-8") of the data from the
	 *            given stream. If the encoding is not known, passing
	 *            <code>null</code> or calling {@link #parse(InputStream)}
	 *            instead will allow the underlying {@link XmlPullParser} to try
	 *            and automatically determine the encoding.
	 * 
	 * @throws IllegalArgumentException
	 *             if <code>source</code> is <code>null</code>.
	 * @throws UnsupportedEncodingException
	 *             if <code>encoding</code> represents an encoding name that is
	 *             not recognized by {@link Charset#isSupported(String)}
	 * @throws XMLParserException
	 *             if any error occurs with the underlying stream during parsing
	 *             of if the XML content itself is malformed and the underlying
	 *             pull parser cannot parse it.
	 */
	public void parse(InputStream source, String encoding)
			throws IllegalArgumentException, UnsupportedEncodingException,
			XMLParserException {
		parse(source, encoding, null);
	}

	/**
	 * Parse the XML out of the given stream (producing content matching the
	 * given encoding) matching the {@link IRule}s provided when the
	 * {@link XMLParser} was instantiated.
	 * <p/>
	 * This class will make no attempt at closing the given {@link InputStream},
	 * the caller must take care to clean up that resource.
	 * <h3>Stopping Parsing</h3>
	 * Parsing can be safely stopped by calling {@link #stop()}. This allows
	 * {@link IRule} implementations control over stopping parsing, for example,
	 * if an arbitrary threshold is hit. A followup call to any of the
	 * <code>parse</code> methods will reset the stopped state.
	 * 
	 * @param source
	 *            The stream that XML content will be read out of.
	 * @param encoding
	 *            The character encoding (e.g. "UTF-8") of the data from the
	 *            given stream. If the encoding is not known, passing
	 *            <code>null</code> or calling {@link #parse(InputStream)}
	 *            instead will allow the underlying {@link XmlPullParser} to try
	 *            and automatically determine the encoding.
	 * @param userObject
	 *            The user-supplied object passed through from this parse method
	 *            to the matching {@link IRule}'s <code>handleXXX</code> method
	 *            when a match is found, or <code>null</code> if no user object
	 *            is needed. Passing through a user-object is just meant as a
	 *            convenience for giving the handler methods on the
	 *            {@link IRule}'s access to objects like DAOs that can be used
	 *            to persist or process parsed data easily.
	 * 
	 * @throws IllegalArgumentException
	 *             if <code>source</code> is <code>null</code>.
	 * @throws UnsupportedEncodingException
	 *             if <code>encoding</code> represents an encoding name that is
	 *             not recognized by {@link Charset#isSupported(String)}
	 * @throws XMLParserException
	 *             if any error occurs with the underlying stream during parsing
	 *             of if the XML content itself is malformed and the underlying
	 *             pull parser cannot parse it.
	 */
	public void parse(InputStream source, String encoding, T userObject)
			throws IllegalArgumentException, UnsupportedEncodingException,
			XMLParserException {
		if (source == null)
			throw new IllegalArgumentException("source cannot be null");
		if (encoding != null) {
			// If empty, ensure it is null so XPP gets encoding from XML header
			if (encoding.trim().length() == 0)
				encoding = null;
			// Extra-safe, make sure the provided encoding is valid
			else if (!Charset.isSupported(encoding))
				throw new UnsupportedEncodingException(
						"Encoding ["
								+ encoding
								+ "] is not a valid charset encoding in this runtime according to Charset.isSupported(encoding).");
		}

		try {
			xpp.setInput(source, encoding);

			if (DEBUG)
				log("Underlying XmlPullParser input set [type=InputStream, encoding=%s (null is OK), userObject=%s]",
						xpp.getInputEncoding(), (userObject == null ? ""
								: userObject));
		} catch (XmlPullParserException e) {
			throw new XMLParserException(
					"Unable to set the given InputStream (with an optional encoding of '"
							+ encoding
							+ "') as input for the underlying XmlPullParser.",
					e);
		}

		try {
			doParse(userObject);
		} catch (IOException e) {
			throw new XMLParserException(
					"An exception occurred while parsing the given source, the XML document may be malformed.",
					e);
		} catch (XmlPullParserException e) {
			throw new XMLParserException(
					"An error with the underlying data stream being parsed occurred.",
					e);
		}
	}

	protected void initRules(IRule<T>... rules) {
		// calculate a rough optimal size for the rule maps
		int optSize = (rules.length > 64 ? rules.length * 2 : 64);

		// init the rule maps
		tagRuleMap = new HashMap<String, List<IRule<T>>>(optSize);
		attrRuleMap = new HashMap<String, List<IRule<T>>>(optSize);
		charRuleMap = new HashMap<String, List<IRule<T>>>(optSize);

		// init the rules
		List<IRule<T>> ruleList = null;

		for (IRule<T> rule : rules) {
			switch (rule.getType()) {
			case TAG:
				// Get the rule list for this path
				ruleList = tagRuleMap.get(rule.getLocationPath());

				// If there wasn't already a rule list, create and add it
				if (ruleList == null) {
					ruleList = new ArrayList<IRule<T>>(3);
					tagRuleMap.put(rule.getLocationPath(), ruleList);
				}
				break;

			case ATTRIBUTE:
				// Get the rule list for this path
				ruleList = attrRuleMap.get(rule.getLocationPath());

				// If there wasn't already a rule list, create and add it
				if (ruleList == null) {
					ruleList = new ArrayList<IRule<T>>(3);
					attrRuleMap.put(rule.getLocationPath(), ruleList);
				}
				break;

			case CHARACTER:
				// Get the rule list for this path
				ruleList = charRuleMap.get(rule.getLocationPath());

				// If there wasn't already a rule list, create and add it
				if (ruleList == null) {
					ruleList = new ArrayList<IRule<T>>(3);
					charRuleMap.put(rule.getLocationPath(), ruleList);
				}
				break;
			}

			// Add the rule to the list for the given path
			ruleList.add(rule);
		}

		if (DEBUG)
			log("Initialized %d TAG rules, %d ATTRIBUTE rules and %d CHARACTER rules.",
					tagRuleMap.size(), attrRuleMap.size(), charRuleMap.size());
	}

	/**
	 * Uses the underlying {@link XmlPullParser} to begin parsing through the
	 * XML content from the given stream. This method's implementation is
	 * simple, acting like a traffic-cop responding to
	 * {@link XmlPullParser#START_TAG}, {@link XmlPullParser#TEXT},
	 * {@link XmlPullParser#END_TAG} and {@link XmlPullParser#END_DOCUMENT}
	 * events by calling the appropriate <code>doXXX</code> methods.
	 * <p/>
	 * Developers creating a subclass of {@link XMLParser} are meant to override
	 * one of the {@link #doStartTag(Object)}, {@link #doText(Object)},
	 * {@link #doEndTag(Object)} and {@link #doEndDocument(Object)} methods to
	 * add custom behavior and not necessarily override this central method.
	 * <h3>Stopping Parsing</h3>
	 * Parsing can be safely stopped by calling {@link #stop()}. This allows
	 * {@link IRule} implementations control over stopping parsing, for example,
	 * if an arbitrary threshold is hit. A followup call to any of the
	 * <code>parse</code> methods will reset the stopped state.
	 * 
	 * @param userObject
	 *            The user-supplied object passed through from this parse method
	 *            to the matching {@link IRule}'s <code>handleXXX</code> method
	 *            when a match is found, or <code>null</code> if no user object
	 *            is needed. Passing through a user-object is just meant as a
	 *            convenience for giving the handler methods on the
	 *            {@link IRule}'s access to objects like DAOs that can be used
	 *            to persist or process parsed data easily.
	 * 
	 * @throws IOException
	 *             if an error occurs with reading from the underlying
	 *             {@link InputStream} given to one of the public
	 *             <code>parse</code> methods.
	 * @throws XmlPullParserException
	 *             if an error occurs while parsing the XML content from the
	 *             underlying stream; typically resulting from malformed or
	 *             invalid XML.
	 */
	protected void doParse(T userObject) throws IOException,
			XmlPullParserException {
		location.clear();
		continueParsing = true;

		if (DEBUG)
			log("Parsing starting...");

		long startTime = System.currentTimeMillis();

		while (continueParsing) {
			switch (xpp.next()) {
			case XmlPullParser.START_TAG:
				doStartTag(userObject);
				break;

			case XmlPullParser.TEXT:
				doText(userObject);
				break;

			case XmlPullParser.END_TAG:
				doEndTag(userObject);
				break;

			case XmlPullParser.END_DOCUMENT:
				continueParsing = false;
				doEndDocument(userObject);
				break;
			}
		}

		if (DEBUG) {
			long duration = System.currentTimeMillis() - startTime;
			log("Parse COMPLETE, elapsed time: %dms (approx %f seconds)",
					duration, (double) duration / (double) 1000);
		}
	}

	/**
	 * Used to process a {@link XmlPullParser#START_TAG} event.
	 * <p/>
	 * By default this updates the internal location state of the parser,
	 * processes all {@link IRule}s of type {@link Type#TAG} and processes all
	 * {@link IRule}s of type {@link Type#ATTRIBUTE} that match the parser's
	 * current location.
	 * 
	 * @param userObject
	 *            The user-supplied object passed through from this parse method
	 *            to the matching {@link IRule}'s <code>handleXXX</code> method
	 *            when a match is found, or <code>null</code> if no user object
	 *            is needed. Passing through a user-object is just meant as a
	 *            convenience for giving the handler methods on the
	 *            {@link IRule}'s access to objects like DAOs that can be used
	 *            to persist or process parsed data easily.
	 */
	protected void doStartTag(T userObject) {
		// Update parser location
		location.push(xpp.getName(), xpp.getNamespace());

		if (DEBUG)
			log("START_TAG: %s", location);

		// Get the rules for the current path
		List<IRule<T>> tagRuleList = tagRuleMap.get(location.toString());
		List<IRule<T>> attrRuleList = attrRuleMap.get(location.toString());

		// If there are no rules for the current path, then we are done.
		if ((tagRuleList == null || tagRuleList.isEmpty())
				&& (attrRuleList == null || attrRuleList.isEmpty()))
			return;

		if (DEBUG)
			log("\t%d TAG rules and %d ATTR rules found for START_TAG...",
					(tagRuleList == null ? 0 : tagRuleList.size()),
					(attrRuleList == null ? 0 : attrRuleList.size()));

		// Process the TAG rules
		if (tagRuleList != null) {
			for (IRule<T> rule : tagRuleList) {
				if (DEBUG)
					log("\t\tRunning TAG Rule: %s", rule);

				rule.handleTag(this, true, userObject);
			}
		}

		// Process the ATTR rules
		if (attrRuleList != null) {
			for (IRule<T> rule : attrRuleList) {
				if (DEBUG)
					log("\t\tRunning ATTR Rule: %s", rule);

				String[] attrNames = rule.getAttributeNames();

				// Be safe, jump to the next rule if this one has no name
				// entries
				if (attrNames == null || attrNames.length == 0)
					continue;

				/*
				 * PERFORMANCE: Generating the substrings is the fastest way to
				 * parse out the matching rules as it shares the same underlying
				 * char[] used to represent the entire location path or
				 * attribute name and just creates a new simple String instance
				 * with modified index/offset values that is GC'ed quickly and
				 * easily (uses a special package-protected String constructor).
				 * 
				 * Using regexp to match, splitting the rule or just about any
				 * other approach would have been magnitudes more expensive both
				 * in memory and CPU requirements than doing a simple substring.
				 */
				for (int i = 0; i < attrNames.length; i++) {
					String attrName = attrNames[i];
					String localName = null;
					String namespaceURI = null;

					// Parse the namespaceURI out of the name if necessary
					if (attrName.charAt(0) == '[') {
						int endIndex = attrName.indexOf(']');

						/*
						 * Make sure the rule is valid so we avoid out of bounds
						 * and keep the caller informed when their rules are
						 * busted by failing fast.
						 */
						if (endIndex <= 2)
							throw new XMLParserException(
									"namespace URI for rule looks to be incomplete or empty for IRule: "
											+ rule);

						namespaceURI = attrName.substring(1, endIndex);
					}

					int startIndex = (namespaceURI == null ? 0 : namespaceURI
							.length() + 2);

					/*
					 * Make sure the rule is valid so we avoid out of bounds and
					 * keep the caller informed when their rules are busted by
					 * failing fast.
					 */
					if (attrName.length() - startIndex <= 1)
						throw new XMLParserException(
								"local name for rule looks to be missing for IRule: "
										+ rule);

					// Parse the local name
					localName = attrName.substring(startIndex,
							attrName.length());

					// Give the parsed attribute value to the matching rule
					rule.handleParsedAttribute(this, i,
							xpp.getAttributeValue(namespaceURI, localName),
							userObject);
				}
			}
		}
	}

	/**
	 * Used to process a {@link XmlPullParser#TEXT} event.
	 * <p/>
	 * By default this processes all {@link IRule}s of type
	 * {@link Type#CHARACTER} that match the parser's current location.
	 * 
	 * @param userObject
	 *            The user-supplied object passed through from this parse method
	 *            to the matching {@link IRule}'s <code>handleXXX</code> method
	 *            when a match is found, or <code>null</code> if no user object
	 *            is needed. Passing through a user-object is just meant as a
	 *            convenience for giving the handler methods on the
	 *            {@link IRule}'s access to objects like DAOs that can be used
	 *            to persist or process parsed data easily.
	 */
	protected void doText(T userObject) {
		if (DEBUG)
			log("TEXT: %s", location);

		// Get the rules for the current path
		List<IRule<T>> ruleList = charRuleMap.get(location.toString());

		// If there are no rules for the current path, then we are done.
		if (ruleList == null || ruleList.isEmpty())
			return;

		if (DEBUG)
			log("\t%d rules found for TEXT...", ruleList.size());

		String text = xpp.getText();

		// Give the parsed text to all matching IRules for this path
		for (IRule<T> rule : ruleList) {
			if (DEBUG)
				log("\t\tRunning Rule: %s", rule);

			rule.handleParsedCharacters(this, text, userObject);
		}
	}

	/**
	 * Used to process a {@link XmlPullParser#END_TAG} event.
	 * 
	 * @param userObject
	 *            The user-supplied object passed through from this parse method
	 *            to the matching {@link IRule}'s <code>handleXXX</code> method
	 *            when a match is found, or <code>null</code> if no user object
	 *            is needed. Passing through a user-object is just meant as a
	 *            convenience for giving the handler methods on the
	 *            {@link IRule}'s access to objects like DAOs that can be used
	 *            to persist or process parsed data easily.
	 */
	protected void doEndTag(T userObject) {
		// Get the rules for the current path
		List<IRule<T>> tagRuleList = tagRuleMap.get(location.toString());

		// If there are no rules for the current path, then we are done.
		if (tagRuleList == null || tagRuleList.isEmpty())
			return;

		if (DEBUG)
			log("\t%d TAG rules found for END_TAG...", tagRuleList.size());

		// Process the TAG rules
		for (IRule<T> rule : tagRuleList) {
			if (DEBUG)
				log("\t\tRunning TAG Rule: %s", rule);

			rule.handleTag(this, true, userObject);
		}

		// Update parser location
		location.pop();

		if (DEBUG)
			log("END_TAG: %s", location);
	}

	/**
	 * Used to process a {@link XmlPullParser#END_DOCUMENT} event.
	 * <p/>
	 * By default this method simply logs a debug statement if debugging is
	 * enabled, but this stub is provided to make overriding the default
	 * behavior easier if desired.
	 * 
	 * @param userObject
	 *            The user-supplied object passed through from this parse method
	 *            to the matching {@link IRule}'s <code>handleXXX</code> method
	 *            when a match is found, or <code>null</code> if no user object
	 *            is needed. Passing through a user-object is just meant as a
	 *            convenience for giving the handler methods on the
	 *            {@link IRule}'s access to objects like DAOs that can be used
	 *            to persist or process parsed data easily.
	 */
	protected void doEndDocument(T userObject) {
		if (DEBUG)
			log("END_DOCUMENT, Parsing COMPLETE");
	}

	/**
	 * Simple and fast class used to mock the behavior of a stack in the form of
	 * a string for the purposes of "pushing" and "popping" the parser's current
	 * location within an XML document as it processes START and END_TAG events.
	 * <p/>
	 * Performance is optimized by using a {@link StringBuilder} who's length is
	 * chopped (which just adjusts an <code>int</code> value) to simulate a
	 * "pop" off the top.
	 * <p/>
	 * The only object-creation step in SJXP that couldn't be optimized out (or
	 * at least not yet) is the creation of the {@link String} representation of
	 * the {@link StringBuilder} when it needs to be used as a key lookup in the
	 * rule hashes. A simple attempt at caching of the value (until it changes)
	 * was made to avoid immediate recalls to return faster which is the
	 * behavior of the START_TAG and TEXT handlers running back-to-back.
	 * <p/>
	 * Fortunately, in the grand-scheme of life and XML parsing, the
	 * <code>toString</code> call is a very small price to pay as path lengths
	 * are typically small, usually less than 64 chars.
	 * 
	 * @author Riyad Kalla (software@thebuzzmedia.com)
	 */
	class Location {
		private String toStringCache;

		private StringBuilder path;
		private List<Integer> lengthList;

		/**
		 * Creates a new empty location.
		 */
		public Location() {
			path = new StringBuilder(256);
			lengthList = new ArrayList<Integer>(16);
		}

		/**
		 * Overridden to return the value of {@link StringBuilder#toString()}.
		 */
		@Override
		public synchronized String toString() {
			if (toStringCache == null) {
				toStringCache = path.toString();
			}

			return toStringCache;
		}

		/**
		 * Used to clear all the internal state of the location.
		 */
		public void clear() {
			toStringCache = null;
			path.setLength(0);
			lengthList.clear();
		}

		/**
		 * "Pushes" a new local name and optional namespace URI onto the "stack"
		 * by appending it to the current location path that represents the
		 * parser's location inside of the XML doc.
		 * 
		 * @param localName
		 *            The local name of the tag (e.g. "title").
		 * @param namespaceURI
		 *            Optionally, the full qualifying namespace URI for this
		 *            tag.
		 */
		public void push(String localName, String namespaceURI) {
			// Clear the cache first to be safe.
			toStringCache = null;

			// Remember the length before we inserted this last entry
			lengthList.add(path.length());

			// Add separator
			path.append('/');

			// Add the namespace URI if there is one.
			if (namespaceURI != null && namespaceURI.length() > 0)
				path.append('[').append(namespaceURI).append(']');

			// Append the local name
			path.append(localName);
		}

		/**
		 * "Pops" the last pushed path element off the "stack" by re-adjusting
		 * the {@link StringBuilder}'s length to what it was before the last
		 * element was appended.
		 * <p/>
		 * This effectively cops the last element off the path without doing a
		 * more costly {@link StringBuilder#delete(int, int)} operation that
		 * would incur a call to
		 * {@link System#arraycopy(Object, int, Object, int, int)}.
		 */
		public void pop() {
			// Clear the cache first to be safe.
			toStringCache = null;

			// Get the length before the last insertion
			Integer lastLength = lengthList.remove(lengthList.size() - 1);

			// 'Pop' the last insertion by cropping the length to exclude it.
			path.setLength(lastLength);
		}
	}
}