package eu.europa.ted.eforms.viewer;

import eu.europa.ted.efx.interfaces.Renderer;

public class XsltRenderer extends IndentedStringWriter implements Renderer {

  private static int variableCounter = 0;

  public XsltRenderer() {
    super(10);
  }

  @Override
  public String toString() {
    return super.toString();
  }

  @Override
  public String renderFile(String body, String templates) {
    final IndentedStringWriter writer = new IndentedStringWriter(0);

    writer.writeLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    writer.openTag("xsl:stylesheet",
        "version=\"2.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:fn=\"http://www.w3.org/2005/xpath-functions\" xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\" xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\" xmlns:efext=\"http://data.europa.eu/p27/eforms-ubl-extensions/1\" xmlns:efac=\"http://data.europa.eu/p27/eforms-ubl-extension-aggregate-components/1\" xmlns:efbc=\"http://data.europa.eu/p27/eforms-ubl-extension-basic-components/1\" xmlns:ext=\"urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2\"");

    writer.writeLine("<xsl:output method=\"html\" encoding=\"UTF-8\" indent=\"yes\"/>");
    writer.writeLine("<xsl:variable name=\"labels\" select=\"fn:document('labels.xml')\"/>");

    // Root template.
    writer.openTag("xsl:template", "match=\"/\"");

    writer.openTag("html");

    writer.openTag("head");
    writer.openTag("style");
    writer.writeLine("section { padding: 6px 6px 6px 36px; }");
    writer.writeLine(".text { font-size: 12pt; color: black; }");
    writer.writeLine(".label { font-size: 12pt; color: green; }");
    writer.writeLine(".dynamic-label { font-size: 12pt; color: blue; }");
    writer.writeLine(".value { font-size: 12pt; color: red; }");
    writer.closeTag("style");
    writer.closeTag("head");

    writer.openTag("body");
    writer.writeBlock(body);
    writer.closeTag("body");

    writer.closeTag("html");

    writer.closeTag("xsl:template");

    // Other templates.
    writer.writeBlock(templates);

    writer.closeTag("xsl:stylesheet");

    return writer.toString();
  }

  @Override
  public String renderValueReference(String valueReference) {
    return String.format("<span class=\"value\"><xsl:value-of select=\"%s\"/></span>",
        valueReference);
  }

  @Override
  public String renderLabelFromKey(final String key) {
    return String.format(
        "<span class=\"label\"><xsl:value-of select=\"($labels/properties/entry[./@key='%s']/text(), ' Label not found (%s)')[1]\"/></span>",
        key, key);
  }

  @Override
  public String renderLabelFromExpression(final String expression) {
    final IndentedStringWriter writer = new IndentedStringWriter(0);
    final String variableName = String.format("label%d", ++variableCounter);

    writer.writeLine(
        String.format("<xsl:variable name=\"%s\" select=\"%s\"/>", variableName, expression));

    writer.writeLine(String.format(
        "<span class=\"dynamic-label\"><xsl:value-of select=\"($labels/properties/entry[./@key=$%s]/text(), concat('Label not found (', $%s, ')'))[1]\"/></span>",
        variableName, variableName));

    return writer.toString();
  }

  @Override
  public String renderFreeText(String freeText) {
    // TODO: This should be escaped for HTML:
    return String.format("<span class=\"text\"><xsl:text>%s</xsl:text></span>", freeText);
  }

  @Override
  public String renderTemplate(String name, String number, String content) {
    IndentedStringWriter writer = new IndentedStringWriter(0);
    writer.openTag("xsl:template", String.format("name='%s'", name));
    writer.openTag("section", "title=\"" + name + "\"");
    writer.writeLine(String.format("<xsl:text>%s&#160;</xsl:text>", number));
    writer.writeBlock(content);
    writer.closeTag("section");
    writer.closeTag("xsl:template");
    return writer.toString();
  }

  @Override
  public String renderCallTemplate(String name, String context) {
    final IndentedStringWriter writer = new IndentedStringWriter(0);
    final String tag = "xsl:for-each";
    writer.openTag(tag, String.format("select=\"%s\"", context));
    writer.writeBlock(String.format("<xsl:call-template name=\"%s\"/>", name));
    writer.closeTag(tag);
    return writer.toString();
  }
}
