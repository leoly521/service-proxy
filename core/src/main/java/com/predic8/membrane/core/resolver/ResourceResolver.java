/* Copyright 2012-2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.resolver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import com.google.common.base.Objects;
import com.predic8.membrane.core.util.LSInputImpl;
import com.predic8.xml.util.ExternalResolver;

public class ResourceResolver {

	public static String combine(String parent, String relativeChild) {
		if (parent.contains(":/")) {
			try {
				return new URI(parent).resolve(relativeChild).toString();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else if (parent.startsWith("/")) {
			try {
				return new URI("file:" + parent).resolve(relativeChild).toString().substring(5);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else {
			return new File(new File(parent).getParent(), relativeChild).getAbsolutePath();
		}
	}

	int count = 0;
	private String[] schemas = new String[10];
	private SchemaResolver[] resolvers = new SchemaResolver[10];
	
	public ResourceResolver() {
		// the default config
		addSchemaResolver(new ClasspathResolver());
		addSchemaResolver(new HTTPResolver());
		addSchemaResolver(new FileResolver());
	}
	
	public void addSchemaResolver(SchemaResolver sr) {
		for (String schema : sr.getSchemas())
			addSchemaResolver(schema == null ? null : schema + ":", sr);
	}
	
	private void addSchemaResolver(String schema, SchemaResolver resolver) {
		for (int i = 0; i < count; i++)
			if (Objects.equal(schemas[i], schema)) {
				// schema already known: replace resolver
				resolvers[i] = resolver;
				return;
			}
		
		// increase array size
		if (++count > schemas.length) {
			String[] schemas2 = new String[schemas.length * 2];
			System.arraycopy(schemas, 0, schemas2, 0, schemas.length);
			schemas = schemas2;
			SchemaResolver[] resolvers2 = new SchemaResolver[resolvers.length * 2];
			System.arraycopy(resolvers, 0, resolvers2, 0, resolvers.length);
			resolvers = resolvers2;
		}
		
		// determine target index
		int newIndex = count - 1;
		if (newIndex > 0 && schemas[newIndex - 1] == null) {
			// move 'null' resolver to last index
			schemas[newIndex] = schemas[newIndex - 1];
			resolvers[newIndex] = resolvers[newIndex - 1];
			newIndex--;
		}
		
		// insert resolver
		schemas[newIndex] = schema;
		resolvers[newIndex] = resolver;
	}

	private SchemaResolver getSchemaResolver(String uri) {
		for (int i = 0; i < count; i++) {
			if (schemas[i] == null)
				return resolvers[i];
			if (uri.startsWith(schemas[i]))
				return resolvers[i];
		}
		throw new RuntimeException("No SchemaResolver defined for " + uri);
	}
	
	public long getTimestamp(String uri) {
		return getSchemaResolver(uri).getTimestamp(uri);
	}
	
	
	public InputStream resolve(String uri) throws FileNotFoundException {
		return getSchemaResolver(uri).resolve(uri);
	}
	
	public List<String> getChildren(String uri) {
		return getSchemaResolver(uri).getChildren(uri);
	}

	
	public LSResourceResolver toLSResourceResolver() {
		return new LSResourceResolver() {
			@Override
			public LSInput resolveResource(String type, String namespaceURI,
					String publicId, String systemId, String baseURI) {
				try {
					if (!systemId.contains("://"))
						systemId = new URI(baseURI).resolve(systemId).toString();
					return new LSInputImpl(publicId, systemId, resolve(systemId));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};
	}

	public ExternalResolver toExternalResolver() {
		return new ExternalResolver() {
			@Override
			public InputStream resolveAsFile(String filename, String baseDir) {
				try {
					if(baseDir != null) {
						return ResourceResolver.this.resolve(baseDir+filename);
					}
					return ResourceResolver.this.resolve(filename);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			
			protected InputStream resolveViaHttp(Object url) {
				try {
					String url2 = (String) url;
					return getSchemaResolver(url2).resolve(url2);
				} catch (FileNotFoundException e) {
					throw new RuntimeException(e);
				}
			}
		};
	}
}