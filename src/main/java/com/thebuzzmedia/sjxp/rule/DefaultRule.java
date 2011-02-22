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
package com.thebuzzmedia.sjxp.rule;

public class DefaultRule implements IRule {
	private Type type;
	private String locationPath;
	private String[] attributeNames;

	public DefaultRule(Type type, String locationPath, String... attributeNames)
			throws IllegalArgumentException {
		if (type == null)
			throw new IllegalArgumentException("type cannot be null");
		if (locationPath == null || locationPath.length() == 0)
			throw new IllegalArgumentException(
					"locationPath cannot be null or empty");
		/*
		 * Pedantic, while we could remove a single trailing slash easily
		 * enough, there is the very-small-chance the users has multiple
		 * trailing slashes... again easy to remove, but at this point they are
		 * being really sloppy and we are letting it slide. Instead, fire an
		 * exception up-front and teach people how the API behaves immediately
		 * and what is required. Makes everyone's lives easier.
		 */
		if (locationPath.charAt(locationPath.length() - 1) == '/')
			throw new IllegalArgumentException(
					"locationPath cannot end in a trailing slash (/), please remove it.");
		if ((type == Type.ATTRIBUTE && (attributeNames == null || attributeNames.length == 0)))
			throw new IllegalArgumentException(
					"Type.ATTRIBUTE was specified but attributeNames was null or empty. One or more attribute names must be provided for this rule if it is going to match any attribute values.");
		/*
		 * Pedantic, but it will warn the caller of what is likely an
		 * programming error condition very early on so they don't bang their
		 * head against the wall as to why the parser isn't picking up their
		 * attributes.
		 */
		if (type == Type.CHARACTER && attributeNames != null
				&& attributeNames.length > 0)
			throw new IllegalArgumentException(
					"Type.CHARACTER was specified, but attribute names were passed in. This is likely a mistake and can be fixed by simply not passing in the ignored attribute names.");

		this.type = type;
		this.locationPath = locationPath;
		this.attributeNames = attributeNames;
	}

	@Override
	public String toString() {
		StringBuilder builder = null;

		/*
		 * toString is only used during debugging, so make the toString output
		 * of the rule pretty so it is easier to track in debug messages.
		 */
		if (attributeNames != null && attributeNames.length > 0) {
			builder = new StringBuilder();

			for (String name : attributeNames)
				builder.append(name).append(',');

			// Chop the last stray comma
			builder.setLength(builder.length() - 1);
		}

		return DefaultRule.class.getName() + "[type=" + type
				+ ", locationPath=" + locationPath + ", attributeNames="
				+ (builder == null ? "" : builder.toString()) + "]";
	}

	public Type getType() {
		return type;
	}

	public String getLocationPath() {
		return locationPath;
	}

	public String[] getAttributeNames() {
		return attributeNames;
	}

	public void handleParsedCharacters(String text) {
		// no-op impl
	}

	public void handleParsedAttribute(int index, String value) {
		// no-op impl
	}
}