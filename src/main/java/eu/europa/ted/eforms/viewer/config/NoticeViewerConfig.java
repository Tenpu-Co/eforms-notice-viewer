package eu.europa.ted.eforms.viewer.config;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import eu.europa.ted.eforms.viewer.NoticeViewerConstants;
import eu.europa.ted.eforms.viewer.enums.FreemarkerTemplate;
import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;

/**
 * Class providing configuration for Freemarker
 */
public class NoticeViewerConfig {
  private static final Logger logger = LoggerFactory.getLogger(NoticeViewerConfig.class);

  private static Configuration freemarkerConfig;

  private NoticeViewerConfig() {}

  /**
   * Create a Freemarker configuration for locating templates for loading. The templates are first
   * looked up on the filesystem. If not found, then they are loaded from the templates folder
   * bundled with the application.
   * <p>
   * The templates root directory can be configured using the system property
   * {@value NoticeViewerConstants#TEMPLATES_ROOT_DIR_PROPERTY}
   *
   * @return A Freemarker configuration object.
   * @throws IOException
   * @throws TemplateException 
   */
  public static Configuration getFreemarkerConfig() throws IOException, TemplateException {
    if (freemarkerConfig == null) {
      final String templatesRootDir =
          System.getProperty(NoticeViewerConstants.TEMPLATES_ROOT_DIR_PROPERTY);
      final Path templatesRootDirPath = Path.of(Optional.ofNullable(templatesRootDir)
          .orElse(NoticeViewerConstants.DEFAULT_TEMPLATES_ROOT_DIR.toString()));

      logger.debug("Configuring Freemarker using [{}] as the templates root directory.",
          templatesRootDirPath);

      List<TemplateLoader> templateLoaders = new ArrayList<>();
          
      try {
        populateExternalTemplatesDir(templatesRootDirPath);
        templateLoaders.add(new FileTemplateLoader(templatesRootDirPath.toFile()));
      } catch (IOException e) {
        logger.warn("Failed to populate external templates directory [{}]", templatesRootDirPath);
        logger.debug("The error was:", e);
      }

      MultiTemplateLoader multiTemplateLoader =
          new MultiTemplateLoader(templateLoaders.toArray(TemplateLoader[]::new));

      freemarkerConfig = new Configuration(Configuration.VERSION_2_3_31);
      freemarkerConfig.setSetting(Configuration.CACHE_STORAGE_KEY, "strong:10, soft:500");
      freemarkerConfig.clearTemplateCache();
      freemarkerConfig.setTemplateLoader(multiTemplateLoader);

      logger.debug("Freemarker configured successfully.");
    }

    return freemarkerConfig;
  }

  /**
   * Populates a templates folder (external to the application) with the bundled templates. Any
   * existing templates on the target folder will not be overwritten.
   *
   * @param targetTemplatesRootDir The target templates root directory on the filesystem
   * @throws IOException
   * @throws Exception
   */
  private static void populateExternalTemplatesDir(Path targetTemplatesRootDir) throws IOException {
    Validate.notNull(targetTemplatesRootDir, "Undefined templates root directory");

    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    for (FreemarkerTemplate template : FreemarkerTemplate.values()) {

      final Path sourcePath = Path.of("templates", template.getPath());
      final Path targetPath = Path.of(targetTemplatesRootDir.toString(), template.getPath());

      if (!targetPath.toFile().exists()) {
        logger.debug("Copying template [classpath:{}] to [{}]", sourcePath,
            targetPath.toAbsolutePath());

        Path parentPath = targetPath.getParent();
        Files.createDirectories(parentPath);

        try (OutputStream os = Files.newOutputStream(targetPath, StandardOpenOption.CREATE)) {
          IOUtils.copy(cl.getResourceAsStream(sourcePath.toString().replace("\\", "/")), os);
        }
      }
    }
  }
}
