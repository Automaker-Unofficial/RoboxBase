package celtech.roboxbase.postprocessor.nouveau;

import celtech.roboxbase.BaseLookup;
import celtech.roboxbase.configuration.fileRepresentation.HeadFile;
import celtech.roboxbase.configuration.fileRepresentation.PrinterSettingsOverrides;
import celtech.roboxbase.configuration.fileRepresentation.SlicerParametersFile;
import static celtech.roboxbase.configuration.fileRepresentation.SlicerParametersFile.SupportType.*;
import celtech.roboxbase.postprocessor.GCodeOutputWriter;
import celtech.roboxbase.postprocessor.NozzleProxy;
import celtech.roboxbase.postprocessor.PrintJobStatistics;
import celtech.roboxbase.postprocessor.RoboxiserResult;
import celtech.roboxbase.postprocessor.nouveau.filamentSaver.FilamentSaver;
import celtech.roboxbase.postprocessor.nouveau.nodes.FillSectionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.GCodeDirectiveNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.GCodeEventNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.InnerPerimeterSectionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.LayerChangeDirectiveNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.LayerNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.MCodeNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.NozzleValvePositionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.OuterPerimeterSectionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.SectionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.SkinSectionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.SkirtSectionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.ToolSelectNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.nodeFunctions.DurationCalculationException;
import celtech.roboxbase.postprocessor.nouveau.nodes.nodeFunctions.SupportsPrintTimeCalculation;
import celtech.roboxbase.postprocessor.nouveau.nodes.providers.ExtrusionProvider;
import celtech.roboxbase.postprocessor.nouveau.nodes.providers.FeedrateProvider;
import celtech.roboxbase.postprocessor.nouveau.nodes.providers.MovementProvider;
import celtech.roboxbase.postprocessor.nouveau.nodes.providers.Renderable;
import celtech.roboxbase.printerControl.model.Head;
import celtech.roboxbase.printerControl.model.Head.HeadType;
import celtech.roboxbase.printerControl.model.Printer;
import celtech.roboxbase.services.CameraTriggerData;
import celtech.roboxbase.utils.SystemUtils;
import celtech.roboxbase.utils.TimeUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javafx.beans.property.DoubleProperty;
import libertysystems.stenographer.Stenographer;
import libertysystems.stenographer.StenographerFactory;
import org.parboiled.Parboiled;
import org.parboiled.parserunners.BasicParseRunner;
import org.parboiled.support.ParsingResult;
import celtech.roboxbase.postprocessor.nouveau.verifier.OutputVerifier;
import celtech.roboxbase.postprocessor.nouveau.verifier.VerifierResult;

/**
 *
 * @author Ian
 */
public class PostProcessor
{

    private final Stenographer steno = StenographerFactory.getStenographer(PostProcessor.class.getName());

    private final String unretractTimerName = "Unretract";
    private final String orphanTimerName = "Orphans";
    private final String nozzleControlTimerName = "NozzleControl";
    private final String perRetractTimerName = "PerRetract";
    private final String closeTimerName = "Close";
    private final String unnecessaryToolchangeTimerName = "UnnecessaryToolchange";
    private final String cameraEventTimerName = "CameraEvent";
    private final String openTimerName = "Open";
    private final String assignExtrusionTimerName = "AssignExtrusion";
    private final String layerResultTimerName = "LayerResult";
    private final String heaterSaverTimerName = "HeaterSaver";
    private final String parseLayerTimerName = "ParseLayer";
    private final String writeOutputTimerName = "WriteOutput";
    private final String countLinesTimerName = "CountLines";
    private final String outputVerifierTimerName = "OutputVerifier";

    private final String nameOfPrint;
    private final Set<Integer> usedExtruders;
    private final Printer printer;
    private final String gcodeFileToProcess;
    private final String gcodeOutputFile;
    private final HeadFile headFile;
    private final SlicerParametersFile slicerParametersFile;
    private final DoubleProperty taskProgress;
    private final boolean safetyFeaturesRequired;

    private final List<NozzleProxy> nozzleProxies = new ArrayList<>();

    private final PostProcessorFeatureSet featureSet;

    private PostProcessingMode postProcessingMode = PostProcessingMode.TASK_BASED_NOZZLE_SELECTION;

    protected List<Integer> layerNumberToLineNumber;
    protected List<Double> layerNumberToPredictedDuration;
    protected double predictedDuration = 0;

    private final UtilityMethods postProcessorUtilityMethods;
    private final NodeManagementUtilities nodeManagementUtilities;
    private final NozzleAssignmentUtilities nozzleControlUtilities;
    private final CloseLogic closeLogic;
    private final FilamentSaver heaterSaver;
    private final OutputVerifier outputVerifier;

    private final TimeUtils timeUtils = new TimeUtils();

    private static final double timeForInitialHoming_s = 20;
    private static final double timeForPurgeAndLevelling_s = 40;
    private static final double timeForNozzleSelect_s = 1;
    private static final double zMoveRate_mms = 4;
    private static final double nozzlePositionChange_s = 0.25;
    // Add a percentage to each movement to factor in acceleration across the whole print
    private static final double movementFudgeFactor = 1.1;

    public PostProcessor(String nameOfPrint,
            Set<Integer> usedExtruders,
            Printer printer,
            String gcodeFileToProcess,
            String gcodeOutputFile,
            HeadFile headFile,
            SlicerParametersFile settings,
            PrinterSettingsOverrides printerOverrides,
            PostProcessorFeatureSet postProcessorFeatureSet,
            String headType,
            DoubleProperty taskProgress,
            Map<Integer, Integer> objectToNozzleNumberMap,
            CameraTriggerData cameraTriggerData,
            boolean safetyFeaturesRequired)
    {
        this.nameOfPrint = nameOfPrint;
        this.usedExtruders = usedExtruders;
        this.printer = printer;
        this.gcodeFileToProcess = gcodeFileToProcess;
        this.gcodeOutputFile = gcodeOutputFile;
        this.headFile = headFile;
        this.featureSet = postProcessorFeatureSet;
        this.slicerParametersFile = settings;
        this.taskProgress = taskProgress;
        this.safetyFeaturesRequired = safetyFeaturesRequired;

        nozzleProxies.clear();

        for (int nozzleIndex = 0;
                nozzleIndex < slicerParametersFile.getNozzleParameters()
                .size(); nozzleIndex++)
        {
            NozzleProxy proxy = new NozzleProxy(slicerParametersFile.getNozzleParameters().get(nozzleIndex));
            proxy.setNozzleReferenceNumber(nozzleIndex);
            nozzleProxies.add(proxy);
        }

        if (headFile.getType() == HeadType.DUAL_MATERIAL_HEAD)
        {
            switch (printerOverrides.getPrintSupportTypeOverride())
            {
                case MATERIAL_1:
                    postProcessingMode = PostProcessingMode.SUPPORT_IN_FIRST_MATERIAL;
                    break;
                case MATERIAL_2:
                    postProcessingMode = PostProcessingMode.SUPPORT_IN_SECOND_MATERIAL;
                    break;
            }
        } else
        {
            postProcessingMode = PostProcessingMode.TASK_BASED_NOZZLE_SELECTION;
        }

        postProcessorUtilityMethods = new UtilityMethods(featureSet, settings, headType, cameraTriggerData);
        nodeManagementUtilities = new NodeManagementUtilities(featureSet);
        nozzleControlUtilities = new NozzleAssignmentUtilities(nozzleProxies, slicerParametersFile, headFile, featureSet, postProcessingMode, objectToNozzleNumberMap);
        closeLogic = new CloseLogic(slicerParametersFile, featureSet, headType);
        heaterSaver = new FilamentSaver();
        outputVerifier = new OutputVerifier();
    }

    public RoboxiserResult processInput()
    {
        RoboxiserResult result = new RoboxiserResult();

        BufferedReader fileReader = null;
        GCodeOutputWriter writer = null;

        float finalEVolume = 0;
        float finalDVolume = 0;
        double timeForPrint_secs = 0;

        layerNumberToLineNumber = new ArrayList<>();
        layerNumberToPredictedDuration = new ArrayList<>();

        predictedDuration = 0;

        int layerCounter = -1;

        OutputUtilities outputUtilities = new OutputUtilities();

        timeUtils.timerStart(this, "PostProcessor");
        steno.info("Beginning post-processing operation");

        //Cura has line delineators like this ';LAYER:1'
        try
        {
            File inputFile = new File(gcodeFileToProcess);
            timeUtils.timerStart(this, countLinesTimerName);
            int linesInGCodeFile = SystemUtils.countLinesInFile(inputFile);
            timeUtils.timerStop(this, countLinesTimerName);

            int linesRead = 0;
            double lastPercentSoFar = 0;

            fileReader = new BufferedReader(new FileReader(inputFile));

            writer = BaseLookup.getPostProcessorOutputWriterFactory().create(gcodeOutputFile);

            boolean nozzle0HeatRequired = false;
            boolean nozzle1HeatRequired = false;

            boolean eRequired = false;
            boolean dRequired = false;

            int defaultObjectNumber = 0;

            if (headFile.getType() == Head.HeadType.DUAL_MATERIAL_HEAD)
            {
                nozzle0HeatRequired = usedExtruders.contains(1)
                        || postProcessingMode == PostProcessingMode.SUPPORT_IN_SECOND_MATERIAL;
                eRequired = usedExtruders.contains(0);
                nozzle1HeatRequired = usedExtruders.contains(0)
                        || postProcessingMode == PostProcessingMode.SUPPORT_IN_FIRST_MATERIAL;
                dRequired = usedExtruders.contains(1);
            } else
            {
                nozzle0HeatRequired = false;
                nozzle1HeatRequired = false;
                eRequired = true;
            }

            outputUtilities.prependPrePrintHeader(writer, headFile.getTypeCode(),
                    nozzle0HeatRequired,
                    nozzle1HeatRequired,
                    safetyFeaturesRequired);

            StringBuilder layerBuffer = new StringBuilder();

            OpenResult lastOpenResult = null;

            List<LayerPostProcessResult> postProcessResults = new ArrayList<>();
            LayerPostProcessResult lastPostProcessResult = new LayerPostProcessResult(null, 0, 0, 0, 0, defaultObjectNumber, null, null, null, 0, -1);

            for (String lineRead = fileReader.readLine(); lineRead != null; lineRead = fileReader.readLine())
            {
                linesRead++;
                double percentSoFar = ((double) linesRead / (double) linesInGCodeFile) * 100;
                if (percentSoFar - lastPercentSoFar >= 1)
                {
                    if (taskProgress != null)
                    {
                        taskProgress.set(percentSoFar);
                    }
                    lastPercentSoFar = percentSoFar;
                }

                lineRead = lineRead.trim();
                if (lineRead.matches(";LAYER:[-]*[0-9]+"))
                {
                    if (layerCounter >= 0)
                    {
                        //Parse the layer!
                        LayerPostProcessResult parseResult = parseLayer(layerBuffer, lastPostProcessResult, writer, headFile.getType());
                        postProcessResults.add(parseResult);
                        lastPostProcessResult = parseResult;
                    }

                    layerCounter++;
                    layerBuffer = new StringBuilder();
                    // Make sure this layer command is at the start
                    layerBuffer.append(lineRead);
                    layerBuffer.append('\n');
                } else if (!lineRead.equals(""))
                {
                    //Ignore blank lines
                    // stash it in the buffer
                    layerBuffer.append(lineRead);
                    layerBuffer.append('\n');
                }
            }

            //This catches the last layer - if we had no data it won't do anything
            LayerPostProcessResult lastLayerParseResult = parseLayer(layerBuffer, lastPostProcessResult, writer, headFile.getType());
            postProcessResults.add(lastLayerParseResult);

            timeUtils.timerStart(this, heaterSaverTimerName);
            if (headFile.getType() == HeadType.DUAL_MATERIAL_HEAD)
            {
                heaterSaver.saveHeaters(postProcessResults);
            }
            timeUtils.timerStop(this, heaterSaverTimerName);

            for (LayerPostProcessResult resultToBeProcessed : postProcessResults)
            {
                timeUtils.timerStart(this, assignExtrusionTimerName);
                NozzleAssignmentUtilities.ExtrusionAssignmentResult assignmentResult = nozzleControlUtilities.assignExtrusionToCorrectExtruder(resultToBeProcessed.getLayerData());
                timeUtils.timerStop(this, assignExtrusionTimerName);

                //Add the opens first - we leave it until now as the layer we have just processed may have affected the one before
                //NOTE
                //Since we're using the open/close state here we need to make sure this is the last open/close thing we do...
                //NOTE
                if (featureSet.isEnabled(PostProcessorFeature.OPEN_AND_CLOSE_NOZZLES))
                {
                    timeUtils.timerStart(this, openTimerName);
                    lastOpenResult = postProcessorUtilityMethods.insertOpens(resultToBeProcessed.getLayerData(), lastOpenResult, nozzleProxies, headFile.getTypeCode());
                    timeUtils.timerStop(this, openTimerName);
                }

                timeUtils.timerStart(this, writeOutputTimerName);
                outputUtilities.writeLayerToFile(resultToBeProcessed.getLayerData(), writer);
                timeUtils.timerStop(this, writeOutputTimerName);
                postProcessorUtilityMethods.updateLayerToLineNumber(resultToBeProcessed, layerNumberToLineNumber, writer);
                predictedDuration += postProcessorUtilityMethods.updateLayerToPredictedDuration(resultToBeProcessed, layerNumberToPredictedDuration, writer);

                finalEVolume += assignmentResult.getEVolume();
                finalDVolume += assignmentResult.getDVolume();
                timeForPrint_secs += resultToBeProcessed.getTimeForLayer();
            }

            timeUtils.timerStart(this, writeOutputTimerName);
            outputUtilities.appendPostPrintFooter(writer, finalEVolume, finalDVolume, timeForPrint_secs,
                    headFile.getTypeCode(),
                    nozzle0HeatRequired,
                    nozzle1HeatRequired,
                    safetyFeaturesRequired);
            timeUtils.timerStop(this, writeOutputTimerName);

            /**
             * TODO: layerNumberToLineNumber uses lines numbers from the GCode
             * file so are a little less than the line numbers for each layer
             * after roboxisation. As a quick fix for now set the line number of
             * the last layer to the actual maximum line number.
             */
            layerNumberToLineNumber.set(layerNumberToLineNumber.size() - 1,
                    writer.getNumberOfLinesOutput());
            int numLines = writer.getNumberOfLinesOutput();

            String statsProfileName = "";
            float statsLayerHeight = 0;

            if (slicerParametersFile != null)
            {
                statsProfileName = slicerParametersFile.getProfileName();
                statsLayerHeight = slicerParametersFile.getLayerHeight_mm();
            }

            PrintJobStatistics roboxisedStatistics = new PrintJobStatistics(
                    nameOfPrint,
                    statsProfileName,
                    statsLayerHeight,
                    numLines,
                    finalEVolume,
                    finalDVolume,
                    0,
                    layerNumberToLineNumber,
                    layerNumberToPredictedDuration,
                    predictedDuration);

            result.setRoboxisedStatistics(roboxisedStatistics);

            timeUtils.timerStart(this, outputVerifierTimerName);
            List<VerifierResult> verificationResults = outputVerifier.verifyAllLayers(postProcessResults, headFile.getType());
            timeUtils.timerStop(this, outputVerifierTimerName);

            if (verificationResults.size() > 0)
            {
                steno.error("Fatal errors found in post-processed file");
                for (VerifierResult verifierResult : verificationResults)
                {
                    if (verifierResult.getNodeInError() instanceof Renderable)
                    {
                        steno.error(verifierResult.getResultType().getDescription()
                                + " at Layer:" + verifierResult.getLayerNumber()
                                + " Tool:" + verifierResult.getToolnumber()
                                + " Node:" + ((Renderable) verifierResult.getNodeInError()).renderForOutput());
                    } else
                    {
                        steno.error(verifierResult.getResultType().getDescription()
                                + " at Layer:" + verifierResult.getLayerNumber()
                                + " Tool:" + verifierResult.getToolnumber()
                                + " Node:" + verifierResult.getNodeInError().toString());
                    }
                }
                steno.error("======================================");
            }

            outputPostProcessingTimerReport();

            timeUtils.timerStop(this, "PostProcessor");
            steno.info("Post-processing took " + timeUtils.timeTimeSoFar_ms(this, "PostProcessor") + "ms");

            if (verificationResults.size() > 0)
            {
                result.setSuccess(false);
            } else
            {
                result.setSuccess(true);
            }
        } catch (IOException ex)
        {
            steno.error("Error reading post-processor input file: " + gcodeFileToProcess);
        } catch (RuntimeException ex)
        {
            if (ex.getCause() != null)
            {
                steno.error("Fatal postprocessing error on layer " + layerCounter + " got exception: " + ex.getCause().getMessage());
            } else
            {
                steno.error("Fatal postprocessing error on layer " + layerCounter);
            }
            ex.printStackTrace();
        } finally
        {
            if (fileReader != null)
            {
                try
                {
                    fileReader.close();
                } catch (IOException ex)
                {
                    steno.error("Failed to close post processor input file - " + gcodeFileToProcess);
                }
            }

            if (writer != null)
            {
                try
                {
                    writer.close();
                } catch (IOException ex)
                {
                    steno.error("Failed to close post processor output file - " + gcodeOutputFile);
                }
            }
        }
        steno.info("About to exit post processor with result " + result.isSuccess());

        return result;
    }

    private LayerPostProcessResult parseLayer(StringBuilder layerBuffer, LayerPostProcessResult lastLayerParseResult, GCodeOutputWriter writer, HeadType headType)
    {
        LayerPostProcessResult parseResultAtEndOfThisLayer = null;

        // Parse the last layer if it exists...
        if (layerBuffer.length() > 0)
        {
            CuraGCodeParser gcodeParser = Parboiled.createParser(CuraGCodeParser.class
            );

            if (lastLayerParseResult
                    != null)
            {
                gcodeParser.setFeedrateInForce(lastLayerParseResult.getLastFeedrateInForce());
            }

            BasicParseRunner runner = new BasicParseRunner<>(gcodeParser.Layer());

            timeUtils.timerStart(this, parseLayerTimerName);
            ParsingResult result = runner.run(layerBuffer.toString());

            timeUtils.timerStop(this, parseLayerTimerName);

            if (result.hasErrors()
                    || !result.matched)
            {
                throw new RuntimeException("Parsing failure");
            } else
            {
                LayerNode layerNode = gcodeParser.getLayerNode();
                int lastFeedrate = gcodeParser.getFeedrateInForce();
                parseResultAtEndOfThisLayer = postProcess(layerNode, lastLayerParseResult, headType);
                parseResultAtEndOfThisLayer.setLastFeedrateInForce(lastFeedrate);
            }
        } else
        {
            parseResultAtEndOfThisLayer = lastLayerParseResult;
        }

        return parseResultAtEndOfThisLayer;
    }

    private LayerPostProcessResult postProcess(LayerNode layerNode, LayerPostProcessResult lastLayerParseResult, HeadType headType)
    {
        // We never want unretracts
        timeUtils.timerStart(this, unretractTimerName);
        nodeManagementUtilities.removeUnretractNodes(layerNode);
        timeUtils.timerStop(this, unretractTimerName);

        timeUtils.timerStart(this, orphanTimerName);
        nodeManagementUtilities.rehomeOrphanObjects(layerNode, lastLayerParseResult);
        timeUtils.timerStop(this, orphanTimerName);

        int lastObjectNumber = -1;

        timeUtils.timerStart(this, nozzleControlTimerName);
        lastObjectNumber = nozzleControlUtilities.insertNozzleControlSectionsByObject(layerNode, lastLayerParseResult);
        timeUtils.timerStop(this, nozzleControlTimerName);

        nodeManagementUtilities.recalculateSectionExtrusion(layerNode);

        timeUtils.timerStart(this, perRetractTimerName);
        nodeManagementUtilities.calculatePerRetractExtrusionAndNode(layerNode);
        timeUtils.timerStop(this, perRetractTimerName);

        timeUtils.timerStart(this, closeTimerName);
        closeLogic.insertCloseNodes(layerNode, lastLayerParseResult, nozzleProxies);
        timeUtils.timerStop(this, closeTimerName);

        timeUtils.timerStart(this, unnecessaryToolchangeTimerName);
        postProcessorUtilityMethods.suppressUnnecessaryToolChangesAndInsertToolchangeCloses(layerNode, lastLayerParseResult, nozzleProxies);
        timeUtils.timerStop(this, unnecessaryToolchangeTimerName);

        if (featureSet.isEnabled(PostProcessorFeature.INSERT_CAMERA_CONTROL_POINTS))
        {
            timeUtils.timerStart(this, cameraEventTimerName);
            postProcessorUtilityMethods.insertCameraTriggersAndCloses(layerNode, lastLayerParseResult, nozzleProxies);
            timeUtils.timerStop(this, cameraEventTimerName);
        }

        timeUtils.timerStart(this, layerResultTimerName);
        LayerPostProcessResult postProcessResult = determineLayerPostProcessResult(layerNode, lastLayerParseResult);
        postProcessResult.setLastObjectNumber(lastObjectNumber);
        timeUtils.timerStop(this, layerResultTimerName);

        return postProcessResult;
    }

    private LayerPostProcessResult determineLayerPostProcessResult(LayerNode layerNode, LayerPostProcessResult lastLayerPostProcessResult)
    {
        Iterator<GCodeEventNode> layerIterator = layerNode.treeSpanningIterator(null);

        float eValue = 0;
        float dValue = 0;
        double timeForLayer = 0;
        double cumulativeTime = 0;
        double timeInThisTool = 0;
        int lastFeedrate = -1;

        SupportsPrintTimeCalculation lastMovementProvider = null;
        SectionNode lastSectionNode = null;
        ToolSelectNode lastToolSelectNode = null;
        ToolSelectNode firstToolSelectNodeWithSameNumber = null;
        double cumulativeTimeInLastTool = 0;

        if (lastLayerPostProcessResult != null)
        {
            cumulativeTime = lastLayerPostProcessResult.getTimeForLayer_cumulative_secs();
        }

        if (layerNode.getLayerNumber() == 0)
        {
            timeForLayer += timeForInitialHoming_s + timeForPurgeAndLevelling_s;
        }

        while (layerIterator.hasNext())
        {
            GCodeEventNode foundNode = layerIterator.next();

            if (foundNode instanceof ExtrusionProvider)
            {
                ExtrusionProvider extrusionProvider = (ExtrusionProvider) foundNode;
                eValue += extrusionProvider.getExtrusion().getE();
                dValue += extrusionProvider.getExtrusion().getD();
            }

            if (foundNode instanceof SupportsPrintTimeCalculation)
            {
                SupportsPrintTimeCalculation timeCalculationNode = (SupportsPrintTimeCalculation) foundNode;

                if (lastMovementProvider != null)
                {
                    try
                    {
                        double time = lastMovementProvider.timeToReach((MovementProvider) foundNode) * movementFudgeFactor;
                        timeForLayer += time;
                        timeInThisTool += time;
                    } catch (DurationCalculationException ex)
                    {
                        if (ex.getFromNode() instanceof Renderable
                                && ex.getToNode() instanceof Renderable)
                        {
                            steno.error("Unable to calculate duration correctly for nodes source:"
                                    + ((Renderable) ex.getFromNode()).renderForOutput()
                                    + " destination:"
                                    + ((Renderable) ex.getToNode()).renderForOutput());
                        } else
                        {
                            steno.error("Unable to calculate duration correctly for nodes source:"
                                    + ex.getFromNode().getMovement().renderForOutput()
                                    + " destination:"
                                    + ex.getToNode().getMovement().renderForOutput());
                        }

                        throw new RuntimeException("Unable to calculate duration correctly on layer "
                                + layerNode.getLayerNumber(), ex);
                    }
                }
                lastMovementProvider = timeCalculationNode;
                if (((FeedrateProvider) lastMovementProvider).getFeedrate().getFeedRate_mmPerMin() < 0)
                {
                    ((FeedrateProvider) lastMovementProvider).getFeedrate().setFeedRate_mmPerMin(lastLayerPostProcessResult.getLastFeedrateInForce());
                }
                lastFeedrate = ((FeedrateProvider) lastMovementProvider).getFeedrate().getFeedRate_mmPerMin();
            } else if (foundNode instanceof ToolSelectNode)
            {
                timeForLayer += timeForNozzleSelect_s;
                timeInThisTool += timeForNozzleSelect_s;
            } else if (foundNode instanceof LayerChangeDirectiveNode)
            {
                LayerChangeDirectiveNode lNode = (LayerChangeDirectiveNode) foundNode;
                double heightChange = lNode.getMovement().getZ() - layerNode.getLayerHeight_mm();
                if (heightChange > 0)
                {
                    timeForLayer += heightChange / zMoveRate_mms;
                    timeInThisTool += heightChange / zMoveRate_mms;
                }
            } else if (foundNode instanceof NozzleValvePositionNode)
            {
                timeForLayer += nozzlePositionChange_s;
                timeInThisTool += nozzlePositionChange_s;
            } else if (!(foundNode instanceof FillSectionNode)
                    && !(foundNode instanceof InnerPerimeterSectionNode)
                    && !(foundNode instanceof SkinSectionNode)
                    && !(foundNode instanceof OuterPerimeterSectionNode)
                    && !(foundNode instanceof SkirtSectionNode)
                    && !(foundNode instanceof MCodeNode))
            {
                steno.info("Not possible to calculate time for: " + foundNode.getClass().getName() + " : " + foundNode.toString());
            }

            if (foundNode instanceof ToolSelectNode)
            {
                ToolSelectNode newToolSelectNode = (ToolSelectNode) foundNode;

                if (lastToolSelectNode != null)
                {
                    lastToolSelectNode.setEstimatedDuration(timeInThisTool);

                    if (newToolSelectNode.getToolNumber() != lastToolSelectNode.getToolNumber())
                    {
                        cumulativeTimeInLastTool = 0;
                        firstToolSelectNodeWithSameNumber = newToolSelectNode;
                    }

                    cumulativeTimeInLastTool += timeInThisTool;
                } else
                {
                    firstToolSelectNodeWithSameNumber = newToolSelectNode;
                }

                lastToolSelectNode = newToolSelectNode;
                timeInThisTool = 0;
            } else if (foundNode instanceof SectionNode)
            {
                lastSectionNode = (SectionNode) foundNode;
            } else if (foundNode instanceof GCodeDirectiveNode
                    && ((GCodeDirectiveNode) foundNode).getGValue() == 4)
            {
                GCodeDirectiveNode directive = (GCodeDirectiveNode) foundNode;
                if (directive.getGValue() == 4)
                {
                    //Found a dwell
                    Optional<Integer> sValue = directive.getSValue();
                    if (sValue.isPresent())
                    {
                        //Seconds
                        timeForLayer += sValue.get();
                    }
                    Optional<Integer> pValue = directive.getPValue();
                    if (pValue.isPresent())
                    {
                        //Microseconds
                        timeForLayer += pValue.get() / 1000.0;
                    }
                }
            }

            foundNode.setFinishTimeFromStartOfPrint_secs(cumulativeTime + timeForLayer);
        }

        if (lastSectionNode == null)
        {
            lastSectionNode = lastLayerPostProcessResult.getLastSectionNodeInForce();
        }

        if (lastToolSelectNode == null)
        {
            lastToolSelectNode = lastLayerPostProcessResult.getLastToolSelectInForce();
            cumulativeTimeInLastTool = lastLayerPostProcessResult.getTimeUsingLastTool();
        } else
        {
            lastToolSelectNode.setEstimatedDuration(timeInThisTool);
            cumulativeTimeInLastTool += timeInThisTool;
        }

        cumulativeTime += timeForLayer;
        layerNode.setFinishTimeFromStartOfPrint_secs(cumulativeTime);

        return new LayerPostProcessResult(layerNode, eValue, dValue, timeForLayer, cumulativeTime, -1,
                lastSectionNode, lastToolSelectNode, firstToolSelectNodeWithSameNumber, cumulativeTimeInLastTool, lastFeedrate);
    }

    private void outputPostProcessingTimerReport()
    {
        steno.debug("Post Processor Timer Report");
        steno.debug("============");
        steno.debug(unretractTimerName + " " + timeUtils.timeTimeSoFar_ms(this, unretractTimerName));
        steno.debug(orphanTimerName + " " + timeUtils.timeTimeSoFar_ms(this, orphanTimerName));
        steno.debug(nozzleControlTimerName + " " + timeUtils.timeTimeSoFar_ms(this, nozzleControlTimerName));
        steno.debug(perRetractTimerName + " " + timeUtils.timeTimeSoFar_ms(this, perRetractTimerName));
        steno.debug(unnecessaryToolchangeTimerName + " " + timeUtils.timeTimeSoFar_ms(this, unnecessaryToolchangeTimerName));
        if (featureSet.isEnabled(PostProcessorFeature.INSERT_CAMERA_CONTROL_POINTS))
        {
            steno.debug(cameraEventTimerName + " " + timeUtils.timeTimeSoFar_ms(this, cameraEventTimerName));
        }
        if (featureSet.isEnabled(PostProcessorFeature.OPEN_AND_CLOSE_NOZZLES))
        {
            steno.debug(closeTimerName + " " + timeUtils.timeTimeSoFar_ms(this, closeTimerName));
            steno.debug(openTimerName + " " + timeUtils.timeTimeSoFar_ms(this, openTimerName));
        }
        steno.debug(assignExtrusionTimerName + " " + timeUtils.timeTimeSoFar_ms(this, assignExtrusionTimerName));
        steno.debug(layerResultTimerName + " " + timeUtils.timeTimeSoFar_ms(this, layerResultTimerName));
        steno.debug(parseLayerTimerName + " " + timeUtils.timeTimeSoFar_ms(this, parseLayerTimerName));
        steno.info(heaterSaverTimerName + " " + timeUtils.timeTimeSoFar_ms(this, heaterSaverTimerName));
        steno.info(outputVerifierTimerName + " " + timeUtils.timeTimeSoFar_ms(this, outputVerifierTimerName));
        steno.debug(writeOutputTimerName + " " + timeUtils.timeTimeSoFar_ms(this, writeOutputTimerName));
        steno.debug("============");
    }
}
