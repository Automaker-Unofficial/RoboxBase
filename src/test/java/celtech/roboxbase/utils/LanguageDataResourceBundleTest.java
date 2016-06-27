package celtech.roboxbase.utils;

import celtech.roboxbase.configuration.BaseConfiguration;
import celtech.roboxbase.i18n.UTF8Control;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author ianhudson
 */
public class LanguageDataResourceBundleTest
{

    @Test
    public void testLocaleUK()
    {
        Properties testProperties = new Properties();

        testProperties.setProperty("language", "UK");

        String installDir = "/Users/ianhudson/Development/RoboxBase/target/test-classes/InstallDir/AutoMaker";
        BaseConfiguration.setInstallationProperties(
                testProperties,
                installDir,
                "");

        Locale.setDefault(Locale.UK);
        ResourceBundle bundle = ResourceBundle.getBundle("celtech.roboxbase.i18n.LanguageData");
        assertEquals(
                "Nozzle firmware control", bundle.getString("error.ERROR_B_POSITION_LOST"));
        assertEquals(
                1033, bundle.keySet().size());
    }

//    @Test
//    public void testLocaleFrance_included()
//    {
//        Properties testProperties = new Properties();
//
//        testProperties.setProperty("language", "FRANCE");
//
//        String installDir = "/Users/ianhudson/Development/RoboxBase/target/test-classes/InstallDir/AutoMaker";
//        BaseConfiguration.setInstallationProperties(
//                testProperties,
//                installDir,
//                "");
//
//        Locale.setDefault(Locale.FRANCE);
//        ResourceBundle bundle = ResourceBundle.getBundle("celtech.roboxbase.i18n.LanguageData");
//        assertEquals(
//                "Contrôle firmware de la buse", bundle.getString("error.ERROR_B_POSITION_LOST"));
//        assertEquals(
//                1033, bundle.keySet().size());
//    }

    @Test
    public void testLocaleNonExistent()
    {
        Properties testProperties = new Properties();

        testProperties.setProperty("language", "ITALIAN");

        String installDir = "/Users/ianhudson/Development/RoboxBase/target/test-classes/InstallDir/AutoMaker";
        BaseConfiguration.setInstallationProperties(
                testProperties,
                installDir,
                "");

        Locale.setDefault(Locale.ITALIAN);
        ResourceBundle bundle = ResourceBundle.getBundle("celtech.roboxbase.i18n.LanguageData");
        assertEquals(
                "Nozzle firmware control", bundle.getString("error.ERROR_B_POSITION_LOST"));
        assertEquals(
                1033, bundle.keySet().size());
    }

}
