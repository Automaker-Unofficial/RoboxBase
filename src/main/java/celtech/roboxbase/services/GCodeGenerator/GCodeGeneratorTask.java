package celtech.roboxbase.services.gcodegenerator;

import celtech.roboxbase.BaseLookup;
import celtech.roboxbase.MaterialType;
import celtech.roboxbase.configuration.BaseConfiguration;
import celtech.roboxbase.configuration.RoboxProfile;
import celtech.roboxbase.configuration.SlicerType;
import celtech.roboxbase.configuration.slicer.Cura3ConfigConvertor;
import celtech.roboxbase.configuration.slicer.SlicerConfigWriter;
import celtech.roboxbase.configuration.slicer.SlicerConfigWriterFactory;
import celtech.roboxbase.printerControl.PrintJob;
import celtech.roboxbase.printerControl.model.Printer;
import celtech.roboxbase.services.postProcessor.GCodePostProcessingResult;
import celtech.roboxbase.services.postProcessor.PostProcessorTask;
import celtech.roboxbase.services.slicer.PrintQualityEnumeration;
import celtech.roboxbase.services.slicer.ProgressReceiver;
import celtech.roboxbase.services.slicer.SliceResult;
import celtech.roboxbase.services.slicer.SlicerTask;
import celtech.roboxbase.utils.models.PrintableMeshes;
import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Task;
import libertysystems.stenographer.Stenographer;
import libertysystems.stenographer.StenographerFactory;

/**
 *
 * @author Tony Aldhous
 */
public class GCodeGeneratorTask extends Task<GCodeGeneratorResult> implements ProgressReceiver
{
    private static final Stenographer steno = StenographerFactory.getStenographer(GCodeGeneratorTask.class.getName());
    private Printer printerToUse = null;
    private PrintableMeshes meshesToUse = null;
    private PrintableMeshes meshesToPrint = null;
    private String gCodeDirectoryName = null;

    /**
     *
     */
    public GCodeGeneratorTask()
    {
    }

    /**
     *
     * @param printerToUse
     * @param meshSupplier
     * @param gCodeDirectoryName
     * 
     */
    public void initialise(Printer printerToUse,
                           Supplier<PrintableMeshes> meshSupplier,
                           String gCodeDirectoryName)
    {
        this.printerToUse = printerToUse;
        this.meshesToUse= meshSupplier.get();
        this.gCodeDirectoryName = gCodeDirectoryName;
        updateProgress(0.0, 100.0);
        this.updateMessage("Preparing to slice ...");
    }

    @Override
    protected GCodeGeneratorResult call()
    {
        GCodeGeneratorResult result = new GCodeGeneratorResult();
        try {
            if (isCancelled())
            {
                result.setCancelled(true);
                return result;
            }
            
            prepareSettingsForSlicing();
            updateProgress(10.0, 100.0);
            if (isCancelled())
            {
                result.setCancelled(true);
                return result;
            }
            updateMessage(BaseLookup.i18n("printerStatus.slicing"));
            PrintJob printJob = new PrintJob(meshesToPrint.getPrintQuality().getFriendlyName(), gCodeDirectoryName);
            String slicerOutputFileName = printJob.getGCodeFileLocation();
            String postProcOutputFileName = printJob.getRoboxisedFileLocation();

            SliceResult slicerResult = SlicerTask.doSlicing(meshesToPrint.getPrintQuality().getFriendlyName(),
                                                            meshesToPrint,
                                                            gCodeDirectoryName,
                                                            printerToUse,
                                                            this,
                                                            steno);
            result.setSlicerResult(slicerResult, slicerOutputFileName);
            updateProgress(60.0, 100.0);
            if (isCancelled())
            {
                result.setCancelled(true);
                return result;
            }
            if (slicerResult.isSuccess())
            {
                updateMessage(BaseLookup.i18n("printerStatus.postProcessing"));
                DoubleProperty progress = new SimpleDoubleProperty();
                progress.addListener((n, ov, nv) -> this.updateProgress(60.0 + 0.4 * nv.doubleValue(), 100.0));
                GCodePostProcessingResult postProcessingResult = PostProcessorTask.doPostProcessing(
                    meshesToPrint.getPrintQuality().getFriendlyName(),
                    meshesToPrint,
                    gCodeDirectoryName,
                    printerToUse,
                    progress,
                    meshesToPrint.getDefaultSlicerType());
                result.setPostProcessingResult(postProcessingResult, postProcOutputFileName);
            }
            updateMessage("Done");
            updateProgress(100.0, 100.0);
        }
        catch (IOException ex)
        {
            steno.exception("There was an exception", ex);
        }
        return result;
    }
    
    private void prepareSettingsForSlicing()
    {
        RoboxProfile settingsToUse = new RoboxProfile(meshesToUse.getSettings());        
        
        SlicerType slicerTypeToUse = meshesToUse.getDefaultSlicerType();

        SlicerConfigWriter configWriter = SlicerConfigWriterFactory.getConfigWriter(
                slicerTypeToUse);

        if(printerToUse != null) {
            // This is a hack to force the fan speed to 100% when using PLA
            if (printerToUse.reelsProperty().containsKey(0))
            {
                if (printerToUse.reelsProperty().get(0).materialProperty().get() == MaterialType.PLA)
                {
                    settingsToUse.addOrOverride("enableCooling", "true");
                    settingsToUse.addOrOverride("minFanSpeed_percent", "100");
                    settingsToUse.addOrOverride("maxFanSpeed_percent", "100");
                }
            }

            if (printerToUse.reelsProperty().containsKey(1))
            {
                if (printerToUse.reelsProperty().get(1).materialProperty().get() == MaterialType.PLA)
                {
                    settingsToUse.addOrOverride("enableCooling", "true");
                    settingsToUse.addOrOverride("minFanSpeed_percent", "100");
                    settingsToUse.addOrOverride("maxFanSpeed_percent", "100");
                }
            }
            // End of hack

            // Hack to change raft related settings for Draft ABS prints
            if (meshesToUse.getPrintQuality() == PrintQualityEnumeration.DRAFT
                    && ((printerToUse.effectiveFilamentsProperty().get(0) != null
                    && printerToUse.effectiveFilamentsProperty().get(0).getMaterial() == MaterialType.ABS)
                    || (printerToUse.effectiveFilamentsProperty().get(1) != null
                    && printerToUse.effectiveFilamentsProperty().get(0).getMaterial() == MaterialType.ABS)))
            {
                settingsToUse.addOrOverride("raftBaseLinewidth_mm", "1.250");
                settingsToUse.addOrOverride("raftAirGapLayer0_mm", "0.285");
                settingsToUse.addOrOverride("interfaceLayers", "1");
            }

            // Hack to change raft related settings for Normal ABS prints
            if (meshesToUse.getPrintQuality() == PrintQualityEnumeration.NORMAL
                    && ((printerToUse.effectiveFilamentsProperty().get(0) != null
                    && printerToUse.effectiveFilamentsProperty().get(0).getMaterial() == MaterialType.ABS)
                    || (printerToUse.effectiveFilamentsProperty().get(1) != null
                    && printerToUse.effectiveFilamentsProperty().get(1).getMaterial() == MaterialType.ABS)))
            {
                settingsToUse.addOrOverride("raftAirGapLayer0_mm", "0.4");
            }
            // End of hack
        }

        // Create a new set of meshes with the updated settings. 
        meshesToPrint = new PrintableMeshes(
                meshesToUse.getMeshesForProcessing(),
                meshesToUse.getUsedExtruders(),
                meshesToUse.getExtruderForModel(),
                meshesToUse.getProjectName(),
                meshesToUse.getRequiredPrintJobID(),
                settingsToUse,
                meshesToUse.getPrintOverrides(),
                meshesToUse.getPrintQuality(),
                meshesToUse.getDefaultSlicerType(),
                meshesToUse.getCentreOfPrintedObject(),
                meshesToUse.isSafetyFeaturesRequired(),
                meshesToUse.isCameraEnabled(),
                meshesToUse.getCameraTriggerData());

        configWriter.setPrintCentre((float) (meshesToUse.getCentreOfPrintedObject().getX()),
                (float) (meshesToUse.getCentreOfPrintedObject().getZ()));
        
        String configFileName = gCodeDirectoryName
                + File.separator
                + meshesToUse.getPrintQuality()
                + BaseConfiguration.printProfileFileExtension;

        configWriter.generateConfigForSlicer(settingsToUse, configFileName);

        if (slicerTypeToUse == SlicerType.Cura3) {
            Cura3ConfigConvertor cura3ConfigConvertor = new Cura3ConfigConvertor(printerToUse, meshesToPrint);
            cura3ConfigConvertor.injectConfigIntoCura3SettingsFile(configFileName, gCodeDirectoryName + File.separator);
        }
    }
    
    
    @Override
    public void progressUpdateFromSlicer(String message, float workDone)
    {
        updateProgress(10.0 + 0.5 * workDone, 100.0);
    }
}
