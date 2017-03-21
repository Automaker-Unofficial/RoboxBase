package celtech.roboxbase.comms.remote.clear;

/**
 *
 * @author Ian
 */
public class SuitablePrintJob
{
    private String printJobID;
    private String printJobName;
    private String printProfileName;
    private double durationInSeconds;
    private double eVolume;
    private double dVolume;

    public double getDurationInSeconds()
    {
        return durationInSeconds;
    }

    public void setDurationInSeconds(double durationInSeconds)
    {
        this.durationInSeconds = durationInSeconds;
    }

    public String getPrintJobID()
    {
        return printJobID;
    }

    public void setPrintJobID(String printJobID)
    {
        this.printJobID = printJobID;
    }

    public String getPrintJobName()
    {
        return printJobName;
    }

    public void setPrintJobName(String printJobName)
    {
        this.printJobName = printJobName;
    }

    public String getPrintProfileName()
    {
        return printProfileName;
    }

    public void setPrintProfileName(String printProfileName)
    {
        this.printProfileName = printProfileName;
    }

    public double getdVolume()
    {
        return dVolume;
    }

    public void setdVolume(double dVolume)
    {
        this.dVolume = dVolume;
    }

    public double geteVolume()
    {
        return eVolume;
    }

    public void seteVolume(double eVolume)
    {
        this.eVolume = eVolume;
    }
    
    
}