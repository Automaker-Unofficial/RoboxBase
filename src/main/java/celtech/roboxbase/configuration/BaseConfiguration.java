package celtech.roboxbase.configuration;

import celtech.roboxbase.ApplicationFeature;
import celtech.roboxbase.configuration.datafileaccessors.PrinterContainer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import libertysystems.configuration.ConfigNotLoadedException;
import libertysystems.configuration.Configuration;
import libertysystems.stenographer.Stenographer;
import libertysystems.stenographer.StenographerFactory;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author Ian Hudson @ Liberty Systems Limited
 */
public class BaseConfiguration
{

    private static final Stenographer steno = StenographerFactory.getStenographer(BaseConfiguration.class.getName());

    /*
     * THINGS THAT SHOULD BE IN GUI ONLY
     */
    public static final int NUMBER_OF_TEMPERATURE_POINTS_TO_KEEP = 210;
    public static final float maxTempToDisplayOnGraph = 300;
    public static final float minTempToDisplayOnGraph = 35;
    /*
     * END OF THINGS THAT SHOULD BE IN GUI ONLY
     */

    /*
     * CONSTANTS
     */
    public static final float filamentDiameterToYieldVolumetricExtrusion = 1.1283791670955125738961589031215f;
    public static final int maxPermittedTempDifferenceForPurge = 15;

    private static String applicationName = null;
    private static String applicationShortName = null;

    private static Configuration configuration = null;
    private static String applicationInstallDirectory = null;

    private static String commonApplicationDirectory = null;

    public static final String applicationConfigComponent = "ApplicationConfiguration";
    private static String userStorageDirectory = null;

    public static final String userStorageDirectoryComponent = "UserDataStorageDirectory";
    private static String applicationStorageDirectory = null;

    public static final String applicationStorageDirectoryComponent = "ApplicationDataStorageDirectory";

    private static String printerFileDirectory = null;
    public static final String printerDirectoryPath = "Printers";
    public static final String printerFileExtension = ".roboxprinter";

    private static String headFileDirectory = null;
    public static final String headDirectoryPath = "Heads";
    public static final String headFileExtension = ".roboxhead";

    public static final String modelStorageDirectoryPath = "Models";
    public static final String userTempDirectoryPath = "Temp";
    private static String userTempFileDirectory = null;

    public static final String filamentDirectoryPath = "Filaments";
    public static final String filamentFileExtension = ".roboxfilament";
    private static String filamentFileDirectory = null;
    private static String userFilamentFileDirectory = null;

    private static MachineType machineType = null;

    private static boolean autoRepairHeads = true;

    private static boolean autoRepairReels = true;

    private static Properties installationProperties = null;
    private static String applicationVersion = null;
    private static String applicationTitleAndVersion = null;

    private static String printFileSpoolDirectory = null;

    public static final String printSpoolStorageDirectoryPath = "PrintJobs";

    public static final int mmOfFilamentOnAReel = 240000;

    /**
     * The extension for statistics files in print spool directories
     */
    public static String statisticsFileExtension = ".statistics";
    public static final String gcodeTempFileExtension = ".gcode";
    public static final String stlTempFileExtension = ".stl";
    public static final String amfTempFileExtension = ".amf";

    public static final String gcodePostProcessedFileHandle = "_robox";
    public static final String printProfileFileExtension = ".roboxprofile";

    public static final String customSettingsProfileName = "Custom";

    public static final String draftSettingsProfileName = "Draft";

    public static final String normalSettingsProfileName = "Normal";

    public static final String fineSettingsProfileName = "Fine";

    public static final String macroFileExtension = ".gcode";
    public static final String macroFileSubpath = "Macros/";

    private static String printProfileFileDirectory = null;
    private static String userPrintProfileFileDirectory = null;
    public static final String printProfileDirectoryPath = "PrintProfiles";
    public static final int maxPrintSpoolFiles = 20;

    private static String applicationLanguageRaw = null;

    private static CoreMemory coreMemory = null;

    private static Properties applicationMemoryProperties = null;
    private static final String fileMemoryItem = "FileMemory";

    private static final Set<ApplicationFeature> applicationFeatures = new HashSet();

    public static void initialise(Class classToCheck)
    {
        getApplicationInstallDirectory(classToCheck);
        PrinterContainer.getCompletePrinterList();
    }

    public static void shutdown()
    {
        writeApplicationMemory();
    }

    /**
     * Used in testing only
     *
     * @param testingProperties
     * @param applicationInstallDirectory
     * @param userStorageDirectory
     */
    public static void setInstallationProperties(Properties testingProperties,
            String applicationInstallDirectory, String userStorageDirectory)
    {
        installationProperties = testingProperties;
        steno.info("App dir: " + applicationInstallDirectory);
        BaseConfiguration.applicationInstallDirectory = applicationInstallDirectory;
        BaseConfiguration.userStorageDirectory = userStorageDirectory;
    }

    public static MachineType getMachineType()
    {
        if (machineType == null)
        {
            String osName = System.getProperty("os.name");

            if (osName.startsWith("Windows 95"))
            {
                machineType = MachineType.WINDOWS_95;
            } else if (osName.startsWith("Windows"))
            {
                machineType = MachineType.WINDOWS;
            } else if (osName.startsWith("Mac"))
            {
                machineType = MachineType.MAC;
            } else if (osName.startsWith("Linux"))
            {
                steno.debug("We have a linux variant");
                ProcessBuilder builder = new ProcessBuilder("uname", "-m");

                Process process = null;

                try
                {
                    process = builder.start();
                    InputStream is = process.getInputStream();
                    InputStreamReader isr = new InputStreamReader(is);
                    BufferedReader br = new BufferedReader(isr);
                    String line;

                    machineType = MachineType.LINUX_X86;

                    while ((line = br.readLine()) != null)
                    {
                        if (line.equalsIgnoreCase("x86_64") == true)
                        {
                            machineType = MachineType.LINUX_X64;
                            steno.debug("Linux 64 bit detected");
                            break;
                        }
                    }
                } catch (IOException ex)
                {
                    machineType = MachineType.UNKNOWN;
                    steno.error("Error whilst determining linux machine type " + ex);
                }

            }
        }

        return machineType;
    }

    public static String getApplicationName()
    {
        if (configuration == null)
        {
            try
            {
                configuration = Configuration.getInstance();
            } catch (ConfigNotLoadedException ex)
            {
                steno.error(
                        "Couldn't load configuration - the application cannot derive the install directory");
            }
        }

        if (configuration != null && applicationName == null)
        {
            try
            {
                applicationName = configuration.getFilenameString(applicationConfigComponent,
                        "ApplicationName", null);
            } catch (ConfigNotLoadedException ex)
            {
                steno.error(
                        "Couldn't determine application name - the application will not run correctly");
            }
        }
        return applicationName;
    }

    public static String getApplicationShortName()
    {
        if (configuration == null)
        {
            try
            {
                configuration = Configuration.getInstance();
            } catch (ConfigNotLoadedException ex)
            {
                steno.error(
                        "Couldn't load configuration - the application cannot derive the install directory");
            }
        }

        if (configuration != null && applicationShortName == null)
        {
            try
            {
                applicationShortName = configuration.getFilenameString(applicationConfigComponent,
                        "ApplicationShortName", null);
                steno.debug("Application short name = " + applicationShortName);
            } catch (ConfigNotLoadedException ex)
            {
                steno.error(
                        "Couldn't determine application short name - the application will not run correctly");
            }
        }
        return applicationShortName;
    }

    public static String getApplicationInstallDirectory(Class classToCheck)
    {
        if (configuration == null)
        {
            try
            {
                configuration = Configuration.getInstance();
            } catch (ConfigNotLoadedException ex)
            {
                steno.error(
                        "Couldn't load configuration - the application cannot derive the install directory");
            }
        }

        if (configuration != null && applicationInstallDirectory == null)
        {
            try
            {
                String fakeAppDirectory = configuration.getFilenameString(applicationConfigComponent,
                        "FakeInstallDirectory",
                        null);

                if (fakeAppDirectory == null)
                {
                    try
                    {
                        String path = classToCheck.getProtectionDomain().getCodeSource().getLocation().getPath();
                        URI uri = new URI(path);
                        File file = new File(uri.getSchemeSpecificPart());
                        String actualPath = file.getCanonicalPath();
                        actualPath = actualPath.replaceFirst("[a-zA-Z0-9]*\\.jar", "");
                        applicationInstallDirectory = actualPath;
                    } catch (URISyntaxException ex)
                    {
                        steno.error(
                                "URI Syntax Exception whilst attempting to determine the application path - the application is unlikely to run correctly.");
                    } catch (IOException ex)
                    {
                        steno.error(
                                "IO Exception whilst attempting to determine the application path - the application is unlikely to run correctly.");
                    }
                } else
                {
                    applicationInstallDirectory = fakeAppDirectory;
                }

//                try
//                {
//                    steno.info("For path of " + path);
//                    File filepath = new File(path);
//                    URI uri = new URI(path);
//                    String actualPath = filepath.getCanonicalPath();
//                    actualPath = actualPath.replaceFirst("[a-zA-Z0-9]*\\.jar", "");
//
//                    applicationInstallDirectoryURI = new URI(actualPath);
//                    steno.info("Got URI of " + applicationInstallDirectoryURI);
//
//                    applicationInstallDirectory = actualPath;
//                    steno.info("Got app install dir of " + applicationInstallDirectory);
//                } catch (URISyntaxException | IOException ex)
//                {
//                    steno.exception("Unable to establish install directory", ex);
//                }
            } catch (ConfigNotLoadedException ex)
            {
                steno.error(
                        "Couldn't load configuration - the application cannot derive the install directory");
            }
        }
        return applicationInstallDirectory;
    }

    public static String getCommonApplicationDirectory()
    {
        if (commonApplicationDirectory == null)
        {
            commonApplicationDirectory = applicationInstallDirectory + "../Common/";
        }

        return commonApplicationDirectory;
    }

    public static String getApplicationHeadDirectory()
    {
        if (headFileDirectory == null)
        {
            headFileDirectory = getCommonApplicationDirectory() + headDirectoryPath + '/';
        }

        return headFileDirectory;
    }

    public static String getApplicationPrinterDirectory()
    {
        if (printerFileDirectory == null)
        {
            printerFileDirectory = getCommonApplicationDirectory() + printerDirectoryPath + '/';
        }

        return printerFileDirectory;
    }

    public static boolean isAutoRepairHeads()
    {
        return autoRepairHeads;
    }

    public static void setAutoRepairHeads(boolean value)
    {
        autoRepairHeads = value;
    }

    public static boolean isAutoRepairReels()
    {
        return autoRepairReels;
    }

    public static void setAutoRepairReels(boolean value)
    {
        autoRepairReels = value;
    }

    private static void loadProjectProperties()
    {
        InputStream input = null;

        try
        {
            input = new FileInputStream(applicationInstallDirectory + "application.properties");

            // load a properties file
            installationProperties = new Properties();
            installationProperties.load(input);
        } catch (IOException ex)
        {
            steno.warning("Couldn't load application.properties");
        } finally
        {
            if (input != null)
            {
                try
                {
                    input.close();
                } catch (IOException ex)
                {
                    steno.exception("Error closing properties file", ex);
                }
            }
        }
    }

    public static String getApplicationVersion()
    {
        if (installationProperties == null)
        {
            loadProjectProperties();
        }
        if (installationProperties != null
                && applicationVersion == null)
        {
            applicationVersion = installationProperties.getProperty("version");
        }

        return applicationVersion;
    }

    public static void setTitleAndVersion(String titleAndVersion)
    {
        applicationTitleAndVersion = titleAndVersion;
    }

    public static String getTitleAndVersion()
    {
        return applicationTitleAndVersion;
    }

    public static String getPrintSpoolDirectory()
    {
        if (printFileSpoolDirectory == null)
        {
            printFileSpoolDirectory = getUserStorageDirectory() + printSpoolStorageDirectoryPath
                    + File.separator;

            File dirHandle = new File(printFileSpoolDirectory);

            if (!dirHandle.exists())
            {
                dirHandle.mkdirs();
            }
        }

        return printFileSpoolDirectory;
    }

    public static String getUserStorageDirectory()
    {
        if (configuration == null)
        {
            try
            {
                configuration = Configuration.getInstance();
            } catch (ConfigNotLoadedException ex)
            {
                steno.error(
                        "Couldn't load configuration - the application cannot derive the install directory");
            }
        }

        if (configuration != null && userStorageDirectory == null)
        {
            if (getMachineType() == MachineType.WINDOWS)
            {
                String registryValue = WindowsRegistry.currentUser("Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders", "Personal");

                if (registryValue != null)
                {
                    Path regPath = Paths.get(registryValue);
                    if (Files.exists(regPath, LinkOption.NOFOLLOW_LINKS))
                    {
                        userStorageDirectory = registryValue + "\\"
                                + getApplicationName() + File.separator;
                    }
                }
            }

            // Other OSes +
            // Just in case we're on a windows machine and the lookup failed...
            if (userStorageDirectory == null)
            {
                try
                {
                    userStorageDirectory = configuration.getFilenameString(
                            applicationConfigComponent, userStorageDirectoryComponent, null)
                            + getApplicationName() + File.separator;
                    steno.debug("User storage directory = " + userStorageDirectory);
                } catch (ConfigNotLoadedException ex)
                {
                    steno.error(
                            "Couldn't determine user storage location - the application will not run correctly");
                }
            }
        }

        if (userStorageDirectory != null)
        {
            File userStorageDirRef = new File(userStorageDirectory);

            if (!userStorageDirRef.exists())
            {
                try
                {
                    FileUtils.forceMkdir(userStorageDirRef);
                } catch (IOException ex)
                {
                    steno.exception("Couldn't create user storage directory: " + userStorageDirectory, ex);
                }
            }
        }

        return userStorageDirectory;
    }

    public static String getApplicationPrintProfileDirectory()
    {
        if (printProfileFileDirectory == null)
        {
            printProfileFileDirectory = getCommonApplicationDirectory() + printProfileDirectoryPath
                    + '/';
        }

        return printProfileFileDirectory;
    }

    public static String getUserPrintProfileDirectory()
    {
        userPrintProfileFileDirectory = getUserStorageDirectory() + printProfileDirectoryPath
                + '/';

        File dirHandle = new File(userPrintProfileFileDirectory);

        if (!dirHandle.exists())
        {
            dirHandle.mkdirs();
        }

        return userPrintProfileFileDirectory;
    }

    public static String getUserTempDirectory()
    {
        userTempFileDirectory = getUserStorageDirectory() + userTempDirectoryPath
                + '/';

        File dirHandle = new File(userTempFileDirectory);

        if (!dirHandle.exists())
        {
            dirHandle.mkdirs();
        }

        return userTempFileDirectory;
    }

    public static String getApplicationStorageDirectory()
    {
        if (configuration == null)
        {
            try
            {
                configuration = Configuration.getInstance();
            } catch (ConfigNotLoadedException ex)
            {
                steno.error(
                        "Couldn't load configuration - the application cannot derive the install directory");
            }
        }

        if (configuration != null && applicationStorageDirectory == null)
        {
            try
            {
                applicationStorageDirectory = configuration.getFilenameString(
                        applicationConfigComponent, applicationStorageDirectoryComponent, null);
                steno.debug("Application storage directory = " + applicationStorageDirectory);
            } catch (ConfigNotLoadedException ex)
            {
                steno.error(
                        "Couldn't determine application storage location - the application will not run correctly");
            }
        }
        return applicationStorageDirectory;
    }

    public static String getApplicationModelDirectory()
    {
        return getCommonApplicationDirectory().concat(modelStorageDirectoryPath).concat("/");
    }

    public static Properties getInstallationProperties()
    {
        return installationProperties;
    }

    public static String getApplicationFilamentDirectory()
    {
        if (filamentFileDirectory == null)
        {
            filamentFileDirectory = BaseConfiguration.getCommonApplicationDirectory() + filamentDirectoryPath + '/';
        }

        return filamentFileDirectory;
    }

    public static String getUserFilamentDirectory()
    {
        userFilamentFileDirectory = BaseConfiguration.getUserStorageDirectory() + filamentDirectoryPath + '/';

        File dirHandle = new File(userFilamentFileDirectory);

        if (!dirHandle.exists())
        {
            dirHandle.mkdirs();
        }

        return userFilamentFileDirectory;
    }

    public static String getApplicationInstallationLanguage()
    {
        if (BaseConfiguration.getInstallationProperties() == null)
        {
            BaseConfiguration.loadProjectProperties();
        }

        if (applicationLanguageRaw == null)
        {
            applicationLanguageRaw = BaseConfiguration.getInstallationProperties().getProperty("language").replaceAll("_",
                    "-");
        }

        return applicationLanguageRaw;
    }

    public static String getBinariesDirectory()
    {
        return BaseConfiguration.getCommonApplicationDirectory() + "bin/";
    }

    public static void enableApplicationFeature(ApplicationFeature feature)
    {
        applicationFeatures.add(feature);
    }

    public static void disableApplicationFeature(ApplicationFeature feature)
    {
        applicationFeatures.remove(feature);
    }

    public static boolean isApplicationFeatureEnabled(ApplicationFeature feature)
    {
        return applicationFeatures.contains(feature);
    }

    private static void loadApplicationMemoryProperties()
    {
        InputStream input = null;

        if (applicationMemoryProperties == null)
        {
            applicationMemoryProperties = new Properties();

            try
            {
                File inputFile = new File(BaseConfiguration.getApplicationStorageDirectory() + BaseConfiguration.getApplicationName()
                        + ".properties");
                if (inputFile.exists())
                {
                    input = new FileInputStream(inputFile);

                    // load a properties file
                    applicationMemoryProperties.load(input);
                }
            } catch (IOException ex)
            {
                ex.printStackTrace();
            } finally
            {
                if (input != null)
                {
                    try
                    {
                        input.close();
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static String getApplicationMemory(String propertyName)
    {
        if (applicationMemoryProperties == null)
        {
            loadApplicationMemoryProperties();
        }
        return applicationMemoryProperties.getProperty(propertyName);
    }

    public static void setApplicationMemory(String propertyName, String value)
    {
        if (applicationMemoryProperties == null)
        {
            loadApplicationMemoryProperties();
        }
        applicationMemoryProperties.setProperty(propertyName, value);

        writeApplicationMemory();
    }

    /**
     *
     */
    public static void writeApplicationMemory()
    {
        if (applicationMemoryProperties == null)
        {
            loadApplicationMemoryProperties();
        }

        OutputStream output = null;

        try
        {
            output = new FileOutputStream(BaseConfiguration.getApplicationStorageDirectory() + BaseConfiguration.getApplicationName()
                    + ".properties");

            applicationMemoryProperties.save(output, BaseConfiguration.getApplicationName() + " runtime properties");
        } catch (IOException ex)
        {
            ex.printStackTrace();
        } finally
        {
            if (output != null)
            {
                try
                {
                    output.close();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

}
