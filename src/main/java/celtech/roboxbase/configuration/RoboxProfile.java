package celtech.roboxbase.configuration;

import celtech.roboxbase.configuration.slicer.NozzleParameters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import libertysystems.stenographer.Stenographer;
import libertysystems.stenographer.StenographerFactory;

/**
 *
 * @author George Salter
 */
public class RoboxProfile {
    
    private static final Stenographer STENO = StenographerFactory.getStenographer(RoboxProfile.class.getName());
    
    // Special nozzle parameter settings
    private static final String EJECTION_VOLUME = "ejectionVolume";
    private static final String PARTIAL_B_MINIMUM = "partialBMinimum";
    
    private final String name;
    private final String headType;
    private final boolean standardProfile;
    
    private Map<String, String> settings;
    private List<NozzleParameters> nozzleParameters;
    
    public RoboxProfile(String name, String headType, boolean standardProfile, Map<String, String> settings) {
        this.name = name;
        this.headType = headType;
        this.standardProfile = standardProfile;
        this.settings = settings;
        createNozzleParameters();
    }
    
    /**
     * Copy constructor
     * 
     * @param roboxProfile 
     */
    public RoboxProfile(RoboxProfile roboxProfile) {
        this.name = roboxProfile.getName();
        this.headType = roboxProfile.getHeadType();
        this.standardProfile = roboxProfile.isStandardProfile();
        this.settings = new HashMap<>(roboxProfile.getSettings());
        createNozzleParameters();
    }

    public String getName() {
        return name;
    }

    public String getHeadType() {
        return headType;
    }

    public boolean isStandardProfile() {
        return standardProfile;
    }

    public Map<String, String> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, String> settings) {
        this.settings = settings;
        createNozzleParameters();
    }
    
    public List<NozzleParameters> getNozzleParameters() {
        return this.nozzleParameters;
    }
    
    public void addOrOverride(String settingId, String value) {
        settings.put(settingId, value);
    }
    
    public float getSpecificFloatSetting(String settingId) {
        String value = settings.get(settingId);
        if(value == null) {
            STENO.error("Setting with id: " + settingId + " does not exist. Returning 0.");
            return 0f;
        }
        float floatValue = Float.valueOf(value);
        return floatValue;
    }
    
    public int getSpecificIntSetting(String settingId) {
        String value = settings.get(settingId);
        if(value == null) {
            STENO.error("Setting with id: " + settingId + " does not exist. Returning 0.");
            return 0;
        }
        int intValue = Integer.valueOf(value);
        return intValue;
    }
    
    public boolean getSpecificBooleanSetting(String settingId) {
        String value = settings.get(settingId);
        if(value == null) {
            STENO.error("Setting with id: " + settingId + " does not exist. Returning false.");
            return false;
        }
        boolean booleanValue = Boolean.valueOf(value);
        return booleanValue;
    }
    
    public String getSpecificSettingAsString(String settingId) {
        String value = settings.get(settingId);
        if(value == null) {
            STENO.error("Setting with id: " + settingId + " does not exist. Returning empty String.");
            return "";
        } 
        return value;
    }

    @Override
    public String toString() {
        return name;
    }
    
    // There is a lot that could go wrong here, need to think about how to handle unexpected values.
    private void createNozzleParameters() {
        STENO.debug("Creating nozzle parameters for RoboxProfile: " + name);
        
        List<NozzleParameters> createdNozzleParameters = new ArrayList<>();
        
        if(settings.containsKey(EJECTION_VOLUME)) {
            String ejectionVolume = settings.get(EJECTION_VOLUME);
            for(String value : ejectionVolume.split(":")) {
                NozzleParameters nozzleParameters = new NozzleParameters();
                nozzleParameters.setEjectionVolume(Float.parseFloat(value));
                createdNozzleParameters.add(nozzleParameters);
            }
        }
        
        if(settings.containsKey(PARTIAL_B_MINIMUM)) {
            String partialBMinimum = settings.get(PARTIAL_B_MINIMUM);
            String[] values = partialBMinimum.split(":");
            for(int i = 0; i < values.length; i++) {
                createdNozzleParameters.get(i).setPartialBMinimum(Float.parseFloat(values[i]));
            }
        }
       
        nozzleParameters = createdNozzleParameters;
    }
}