package com.oakonell.utils.xml;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class XMLUtils {
	private XMLUtils() {
		// prevent instantiation
	}

	public static void setTextContent(Node node, String string) {
		// This would just be node.setTextContent(string) in API level >= 2.2
		node.appendChild(node.getOwnerDocument().createTextNode(string));
	}

	public static String getTextContent(Node node) {
		// This would just be node.getTextContent() in API level >= 2.2
		StringBuffer buffer = new StringBuffer();
		NodeList childList = node.getChildNodes();
		for (int i = 0; i < childList.getLength(); i++) {
			Node child = childList.item(i);
			if (child.getNodeType() != Node.TEXT_NODE)
				continue; // skip non-text nodes
			buffer.append(child.getNodeValue());
		}
		return buffer.toString();
	}

	public static String getTextContent(Element element, String name) {
		NodeList nodes = element.getElementsByTagName(name);
		if (nodes.getLength() != 1) {
			throw new RuntimeException("Unexpected number of '" + name
					+ "' elements " + nodes.getLength());
		}
		Element singleElem = (Element) nodes.item(0);
		return XMLUtils.getTextContent(singleElem);
	}

	public static List<Element> getChildElementsByName(Element parent,
			String name) {
		List<Element> result = new ArrayList<Element>();
		NodeList childNodes = parent.getChildNodes();
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node child = childNodes.item(i);
			if (child.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			String nodeName = child.getNodeName();
			if (name.equals(nodeName.trim())) {
				result.add((Element) child);
			}
		}
		return result;
	}

	public static Element getChildElementByName(Element parent, String name) {
		List<Element> children = getChildElementsByName(parent, name);
		if (children.size() == 0) {
			return null;
		}
		if (children.size() > 1) {
			throw new RuntimeException("Unexpected number of '" + name
					+ "' elements " + children.size());
		}
		return children.get(0);
	}

	private static String docToString(NodeList list) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < list.getLength(); i++) {
			Node item = list.item(i);

			if (item.getNodeType() == Node.TEXT_NODE) {
				builder.append(item.getNodeValue());
			} else {
				builder.append("\n<").append(item.getNodeName());
				NamedNodeMap attributes = item.getAttributes();
				if (attributes != null) {
					int max = attributes.getLength();
					for (int j = 0; j < max; j++) {
						Node attribute = attributes.item(j);
						builder.append(" ").append(attribute.getNodeName())
								.append("=\"").append(attribute.getNodeValue())
								.append("\"");
					}
				}
				builder.append(">");
			}
			if (item.getChildNodes().getLength() > 0) {
				builder.append(docToString(item.getChildNodes()));
			}
			if (item.getNodeType() != Node.TEXT_NODE) {
				if (builder.charAt(builder.length() - 1) == '>') {
					builder.append("\n");
				}
				builder.append("</").append(item.getNodeName()).append(">")
						.toString();
			}
		}
		return builder.toString();
	}

	public static final String xmlDocumentToString(Document document) {
		// borrowed and modified from
		// http://wurstgranulat.de/projekte/java-library-android-xml-document-string/
		return docToString(document.getChildNodes()).trim();
	}
}
