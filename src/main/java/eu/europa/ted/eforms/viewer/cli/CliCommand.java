package eu.europa.ted.eforms.viewer.cli;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Base64;  
import java.util.Enumeration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.helper.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import eu.europa.ted.eforms.viewer.DependencyFactory;
import eu.europa.ted.eforms.viewer.NoticeDocument;
import eu.europa.ted.eforms.viewer.NoticeViewer;
import eu.europa.ted.eforms.viewer.NoticeViewerConstants;
import eu.europa.ted.eforms.viewer.config.NoticeViewerConfig;
import eu.europa.ted.eforms.viewer.generator.XslGenerator;
import eu.europa.ted.eforms.viewer.util.xml.TranslationUriResolver;

import eu.europa.ted.efx.EfxTranslatorOptions;
import eu.europa.ted.efx.model.DecimalFormat;
import freemarker.template.TemplateException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "", mixinStandardHelpOptions = true, description = "eForms Notice Viewer",
    versionProvider = CliCommand.ManifestVersionProvider.class)
public class CliCommand implements Callable<Integer> {
  private static final Logger logger = LoggerFactory.getLogger(CliCommand.class);

  @Spec
  CommandSpec spec; // injected by picocli

  private String language;

  @Parameters(index = "1", description = "XML file to view as base64 string.", defaultValue = "")
  private String noticeXmlEncoded;

  @Option(names = {"-i", "--viewId"}, description = "View ID to use.")
  private String viewId;

  @Option(names = {"-v", "--sdkVersionId"}, description = "eForms SDK version ID to use.")
  private String sdkVersionId;

  @Option(names = {"-o", "--outputPath"}, description = "Output path.")
  private String outputPath;

  @Option(names = {"-r", "--sdkRoot"}, description = "SDK resources root folder.")
  private String sdkResourcesRoot;

  @Option(names = {"-p", "--profileXslt"}, description = "Enable XSLT profiling.")
  private boolean profileXslt;

  @Option(names = {"-x", "--only-xslt"},
      description = "Generate only the XSLT file.")
  private boolean onlyXslt;

  @Option(names = {"-f", "--force"},
      description = "Force re-building of XSL by clearing any cached content.")
  private boolean forceBuild;

  @Option(names = {"-t", "--templatesRoot"}, description = "Templates root folder.")
  void setTemplatesRoot(String templatesRoot) {
    System.setProperty(NoticeViewerConstants.TEMPLATES_ROOT_DIR_PROPERTY, templatesRoot);
  }

  @Parameters(index = "0", description = "Two letter language code.")
  public void setLanguage(String language) {
    if (StringUtils.isBlank(language) || language.length() != 2) {
      throw new ParameterException(spec.commandLine(),
          MessageFormat.format(
              "Language: expecting two letter code like 'en', 'fr', ..., but found \'\'{0}\'\'",
              language));
    }
    this.language = language;
  }

  /**
   * @throws IOException If an error occurs during input or output
   * @throws ParserConfigurationException Error related to XML reader configuration
   * @throws SAXException XML parse error related
   * @throws InstantiationException
   * @throws URISyntaxException
   * @throws TransformerException
   * @throws XPathExpressionException
   */
  @Override
  public Integer call()
      throws IOException, SAXException, ParserConfigurationException, InstantiationException,
      URISyntaxException, TemplateException, TransformerException, XPathExpressionException {

    // Initialise Freemarker templates so that the templates folder will be populated
    NoticeViewerConfig.getFreemarkerConfig();

    final Path sdkRoot = Optional.ofNullable(sdkResourcesRoot)
        .map(Path::of)
        .orElse(NoticeViewerConstants.DEFAULT_SDK_ROOT_DIR);

    if (onlyXslt) {

      final Path efxPath = NoticeViewer.getEfxPath(sdkVersionId, viewId, sdkRoot);

      logger.debug("Starting XSL generation using the EFX template at [{}]", efxPath);
      final Path xsltPath =
        XslGenerator.Builder
          .create(new DependencyFactory(sdkRoot))
          .build()
          .generateFile(sdkVersionId, efxPath,
            new EfxTranslatorOptions(NoticeViewerConstants.DEFAULT_TRANSLATOR_OPTIONS.getDecimalFormat(), language),
          true);

      logger.debug("Created XSLT file: {}", xsltPath);

      return 0;
    }

    Base64.Decoder decoder = Base64.getDecoder();  
    final String xmlContents = new String(decoder.decode(noticeXmlEncoded));
    NoticeDocument notice = new NoticeDocument(xmlContents);

    final String html =
        NoticeViewer.Builder
            .create()
            .withProfileXslt(profileXslt)
            .withUriResolver(new TranslationUriResolver(notice.getEformsSdkVersion(), sdkRoot))
            .build()
            .generateHtmlString(language, viewId, notice, sdkRoot,
                NoticeViewerConstants.DEFAULT_TRANSLATOR_OPTIONS.getDecimalFormat(),
                forceBuild);

    Base64.Encoder encoder = Base64.getEncoder();  
    logger.info(encoder.encodeToString(html.getBytes()));

    return 0;
  }

  /**
   * {@link IVersionProvider} implementation that returns version information from the
   * picocli-x.x.jar file's {@code /META-INF/MANIFEST.MF} file.
   */
  static class ManifestVersionProvider implements IVersionProvider {
    public String[] getVersion() throws Exception {
      Enumeration<URL> resources =
          CommandLine.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
      while (resources.hasMoreElements()) {
        URL url = resources.nextElement();
        try {
          Manifest manifest = new Manifest(url.openStream());
          if (isApplicableManifest(manifest)) {
            Attributes attr = manifest.getMainAttributes();
            return new String[] {get(attr, "Implementation-Title") + " version \""
                + get(attr, "Implementation-Version") + "\""};
          }
        } catch (IOException ex) {
          return new String[] {"Unable to read from " + url + ": " + ex};
        }
      }
      return new String[0];
    }

    private boolean isApplicableManifest(Manifest manifest) {
      Attributes attributes = manifest.getMainAttributes();
      return "eforms-notice-viewer".equals(get(attributes, "Implementation-Title"));
    }

    private static Object get(Attributes attributes, String key) {
      return attributes.get(new Attributes.Name(key));
    }
  }
}
