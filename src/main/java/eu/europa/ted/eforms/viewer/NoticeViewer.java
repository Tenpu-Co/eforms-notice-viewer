package eu.europa.ted.eforms.viewer;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.xpath.XPathExpressionException;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import eu.europa.ted.eforms.sdk.SdkConstants;
import eu.europa.ted.eforms.sdk.SdkVersion;
import eu.europa.ted.eforms.viewer.generator.HtmlGenerator;
import eu.europa.ted.eforms.viewer.generator.XslGenerator;
import eu.europa.ted.efx.EfxTranslatorOptions;
import eu.europa.ted.efx.interfaces.TranslatorOptions;
import eu.europa.ted.efx.model.DecimalFormat;
import eu.europa.ted.efx.util.LocaleHelper;

public class NoticeViewer {
  private static final Logger logger = LoggerFactory.getLogger(NoticeViewer.class);

  private static final String MSG_UNDEFINED_NOTICE_DOCUMENT = "Undefined notice document";

  private final Charset charset;
  private final boolean profileXslt;
  private final URIResolver uriResolver;

  private NoticeViewer(final Charset charset, final boolean profileXslt,
      final URIResolver uriResolver) {
    this.charset = ObjectUtils.defaultIfNull(charset, NoticeViewerConstants.DEFAULT_CHARSET);
    this.profileXslt = profileXslt;
    this.uriResolver = uriResolver;
  }

  private NoticeViewer(Builder builder) {
    this(builder.charset, builder.profileXslt, builder.uriResolver);
  }

  /**
   * Creates a HTML file with the contents generated by applying XSL transformation on a notice's
   * XML using a XSL template.
   * <p>
   * This XSL template is generated internally, based on SDK files.
   * <p>
   * The SDK root directory is expected to contain a directory per minor SDK version.
   *
   * @param language The language as a two letter code. If set, it will be used as the primary
   *        language for the translation
   * @param viewId An optional SDK view id to used for loading the corresponding EFX template.
   *        <p>
   *        This can be used to enforce a custom view like notice summary. It could fail if this
   *        custom view is not compatible with the notice sub type.
   *        <p>
   *        If not given, then the notice sub type ID from the notice XML will be used.
   * @param notice A {@link NoticeDocument} object containing the notice's XML contents and metadata
   * @return The path of the generated HTML file
   * @param sdkRoot Path of the root SDK directory
   * @param symbols The {@link DecimalFormat} to use for the translation
   * @param forceBuild If true, forces the re-creation of XSL (re-creates cache entries)
   * @return The path of the generated HTML file
   * @throws IOException when the XML/XSL contents cannot be loaded
   * @throws TransformerException when the XSL transformation fails
   * @throws SAXException when the notice XML cannot be parsed
   * @throws ParserConfigurationException when the XML parser is not configured properly
   * @throws XPathExpressionException when an error occurs while extracting language information
   *         from the notice document
   */
  public Path generateHtmlFile(final String language, String viewId, final NoticeDocument notice,
      final Path outputFile, final Path sdkRoot, final DecimalFormat symbols,
      boolean forceBuild)
      throws IOException, TransformerException, ParserConfigurationException, SAXException,
      XPathExpressionException {
    Validate.notNull(notice, MSG_UNDEFINED_NOTICE_DOCUMENT);

    final String sdkVersion = notice.getEformsSdkVersion();
    viewId = ObjectUtils.defaultIfNull(viewId, notice.getNoticeSubType());

    final Path efxPath = getEfxPath(sdkVersion, viewId, sdkRoot);

    logger.debug("Starting XSL generation using the EFX template at [{}]", efxPath);
    final String xslContents =
        createXslGenerator(sdkRoot).generateString(sdkVersion, efxPath,
            getTranslatorOptions(notice, language, symbols),
            forceBuild);

    return generateHtmlFile(language, viewId, notice, xslContents, outputFile);
  }

  /**
   * Creates a HTML file with the contents generated by applying XSL transformation on a notice's
   * XML using a XSL template.
   * 
   * @param language The language as a two letter code
   * @param viewId The view ID corresponding to the XSL template.
   * @param notice A {@link NoticeDocument} object containing the notice's XML contents and metadata
   * @param xslContents The contents of the XSL template
   * @param outputFile The path of the generated HTML file
   * @return The path of the generated HTML file
   * @throws IOException when the XML/XSL contents cannot be loaded
   * @throws TransformerException when the XSL transformation fails
   */
  public Path generateHtmlFile(final String language, final String viewId,
      final NoticeDocument notice, final String xslContents, Path outputFile)
      throws IOException, TransformerException {
    Validate.notNull(notice, MSG_UNDEFINED_NOTICE_DOCUMENT);

    if (outputFile == null) {
      outputFile = NoticeViewerConstants.OUTPUT_FOLDER_HTML
          .resolve(MessageFormat.format("{0}-{1}.html", viewId, language));
    }

    Files.createDirectories(outputFile.getParent());

    return createHtmlGenerator().generateFile(language, viewId, notice.getXmlContents(),
        xslContents, outputFile);
  }

  /**
   * Generates HTML by applying XSL transformation on a notice's XML using a XSL template.
   * <p>
   * This XSL template is generated internally, based on SDK files.
   * <p>
   * The SDK root directory is expected to contain a directory per minor SDK version.
   *
   * @param language The language as a two letter code. If set, it will be used as the primary
   *        language for the translation
   * @param viewId An optional SDK view id to used for loading the corresponding EFX template.
   *        <p>
   *        This can be used to enforce a custom view like notice summary. It could fail if this
   *        custom view is not compatible with the notice sub type.
   *        <p>
   *        If not given, then the notice sub type ID from the notice XML will be used.
   * @param notice A {@link NoticeDocument} object containing the notice's XML contents and metadata
   * @param sdkRoot Path of the root SDK directory
   * @param symbols The {@link DecimalFormat} to use for the translation
   * @param forceBuild If true, forces the re-creation of XSL (re-creates cache entries)
   * @return Generated HTML as {@link String}
   * @throws TransformerException when the XSL transformation fails
   * @throws IOException when the XML/XSL contents cannot be loaded
   * @throws SAXException when the notice XML cannot be parsed
   * @throws ParserConfigurationException when the XML parser is not configured properly
   * @throws XPathExpressionException when an error occurs while extracting language information
   *         from the notice document
   */
  public String generateHtmlString(final String language, String viewId,
      final NoticeDocument notice, final Path sdkRoot, final DecimalFormat symbols,
      boolean forceBuild)
      throws TransformerException, IOException, ParserConfigurationException, SAXException,
      XPathExpressionException {
    Validate.notNull(notice, MSG_UNDEFINED_NOTICE_DOCUMENT);

    final String sdkVersion = notice.getEformsSdkVersion();
    viewId = ObjectUtils.defaultIfNull(viewId, notice.getNoticeSubType());

    final Path efxPath = getEfxPath(sdkVersion, viewId, sdkRoot);

    logger.debug("Starting XSL generation using the EFX template at [{}]", efxPath);
    final String xslContents =
        createXslGenerator(sdkRoot).generateString(sdkVersion, efxPath,
            getTranslatorOptions(notice, language, symbols), forceBuild);

    return generateHtmlString(language, viewId, notice, xslContents);
  }

  /**
   * Generates HTML by applying XSL transformation on a notice's XML using a XSL template.
   *
   * @param language The language as a two letter code
   * @param viewId The view ID corresponding to the XSL template.
   * @param notice A {@link NoticeDocument} object containing the notice's XML contents and metadata
   * @param xslContents The contents of the XSL template
   * @return Generated HTML as {@link String}
   * @throws TransformerException when the XSL transformation fails
   * @throws IOException when the XML/XSL contents cannot be loaded
   */
  public String generateHtmlString(final String language, final String viewId,
      final NoticeDocument notice, final String xslContents)
      throws TransformerException, IOException {
    Validate.notNull(notice, MSG_UNDEFINED_NOTICE_DOCUMENT);

    logger.info("Generating HTML for language [{}] and view ID [{}]", language, viewId);

    final String html = createHtmlGenerator().generateString(language, viewId,
        notice.getXmlContents(), xslContents);

    logger.info("Finished generating HTML for language [{}] and view ID [{}]", language, viewId);

    return html;
  }

  /**
   * Resolves the path of the EFX template for a view.
   *
   * @param sdkVersionStr The target SDK version
   * @param viewId The view ID to use for finding the EFX template file
   * @param sdkRoot Path of the root SDK folder
   * @return The path of the EFX template
   */
  public static Path getEfxPath(final String sdkVersionStr, final String viewId, Path sdkRoot) {
    Validate.notBlank(viewId, "Undefined view ID");

    final SdkVersion sdkVersion = new SdkVersion(sdkVersionStr);
    final String sdkDir = sdkVersion.toStringWithoutPatch();
    final String resourcePath = SdkConstants.SdkResource.VIEW_TEMPLATES.getPath().toString();
    final String filename = MessageFormat.format("{0}.efx", viewId);
    sdkRoot = ObjectUtils.defaultIfNull(sdkRoot, NoticeViewerConstants.DEFAULT_SDK_ROOT_DIR);

    Path efxPath = Path.of(sdkRoot.toString(), sdkDir, resourcePath, filename).toAbsolutePath();
    logger.debug("EFX path for view ID {} and SDK {}: {}", viewId, sdkVersionStr, efxPath);

    return efxPath;
  }

  /**
   * Creates a {@link TranslatorOptions} instance for a notice.
   *
   * @param notice A {@link NoticeDocument} object containing the notice's XML contents and metadata
   * @param language The language of the primary locale. If not set, the primary locale of the
   *        notice will be used
   * @param symbols A {@link DecimalFormat} instance defining the symbols to be used
   * @return A notice-specific {@link TranslatorOptions} instance
   * @throws XPathExpressionException when an error occurs while extracting language information
   *         from the notice document
   */
  public static TranslatorOptions getTranslatorOptions(final NoticeDocument notice,
      final String language, DecimalFormat symbols)
      throws XPathExpressionException {
    Validate.notNull(notice, MSG_UNDEFINED_NOTICE_DOCUMENT);

    final Locale primaryLocale = Optional.ofNullable(language)
        .filter(StringUtils::isNotBlank)
        .map(LocaleHelper::getLocale)
        .orElse(notice.getPrimaryLocale());

    symbols = ObjectUtils.defaultIfNull(symbols,
        NoticeViewerConstants.DEFAULT_TRANSLATOR_OPTIONS.getDecimalFormat());

    final List<Locale> otherLocales = notice.getOtherLocales();
    if (StringUtils.isNotBlank(language)) {
      otherLocales.add(0, notice.getPrimaryLocale());
    }

    return new EfxTranslatorOptions(symbols, primaryLocale, otherLocales.toArray(Locale[]::new));
  }

  private XslGenerator createXslGenerator(final Path sdkRoot) {
    return XslGenerator.Builder
        .create(new DependencyFactory(sdkRoot))
        .build();
  }

  private HtmlGenerator createHtmlGenerator() {
    return HtmlGenerator.Builder
        .create()
        .withCharset(charset)
        .withProfileXslt(profileXslt)
        .withUriResolver(uriResolver)
        .build();
  }

  /**
   * Builder class for {@link NoticeViewer} instances
   */
  public static final class Builder {
    // required parameters

    // optional parameters
    private Charset charset;
    private boolean profileXslt;
    private URIResolver uriResolver;

    public static Builder create() {
      return new Builder();
    }

    /**
     * @param charset The character set to be used for the HTML output and for reading the XSL
     *        template and the notice XML
     * @return A {@link Builder} instance
     */
    public Builder withCharset(final Charset charset) {
      this.charset = charset;
      return this;
    }

    /**
     * @param profileXslt If true, Enables XSLT profiling
     * @return A {@link Builder} instance
     */
    public Builder withProfileXslt(final boolean profileXslt) {
      this.profileXslt = profileXslt;
      return this;
    }

    /**
     * @param uriResolver The URI resolver to be used during the XSL transformation
     * @return A {@link Builder} instance
     */
    public Builder withUriResolver(URIResolver uriResolver) {
      this.uriResolver = uriResolver;
      return this;
    }

    /**
     * @return A configured {@link NoticeViewer} instance
     */
    public NoticeViewer build() {
      return new NoticeViewer(this);
    }
  }
}
