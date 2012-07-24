package zephyr.plugin.critterview.views;

import static rlpark.plugin.critterbot.data.CritterbotLabels.Accel;
import static rlpark.plugin.critterbot.data.CritterbotLabels.BusVoltage;
import static rlpark.plugin.critterbot.data.CritterbotLabels.Current;
import static rlpark.plugin.critterbot.data.CritterbotLabels.IRDistance;
import static rlpark.plugin.critterbot.data.CritterbotLabels.IRLight;
import static rlpark.plugin.critterbot.data.CritterbotLabels.Light;
import static rlpark.plugin.critterbot.data.CritterbotLabels.Mag;
import static rlpark.plugin.critterbot.data.CritterbotLabels.MicrophoneFFT;
import static rlpark.plugin.critterbot.data.CritterbotLabels.Motor;
import static rlpark.plugin.critterbot.data.CritterbotLabels.RotationVel;
import static rlpark.plugin.critterbot.data.CritterbotLabels.Speed;
import static rlpark.plugin.critterbot.data.CritterbotLabels.Temperature;
import static rlpark.plugin.critterbot.data.CritterbotLabels.Thermal;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.action.IToolBarManager;

import rlpark.plugin.critterbot.CritterbotProblem;
import rlpark.plugin.critterbot.crtrlog.CrtrLogFile;
import rlpark.plugin.critterbot.data.CritterbotLabels;
import rlpark.plugin.rltoys.envio.observations.Legend;
import rlpark.plugin.rltoys.utils.Utils;
import zephyr.plugin.core.ZephyrCore;
import zephyr.plugin.core.api.synchronization.Chrono;
import zephyr.plugin.core.api.synchronization.Clock;
import zephyr.plugin.core.internal.actions.RestartAction;
import zephyr.plugin.core.internal.actions.TerminateAction;
import zephyr.plugin.core.internal.helpers.ClassViewProvider;
import zephyr.plugin.core.internal.observations.EnvironmentView;
import zephyr.plugin.core.internal.observations.ObsLayout;
import zephyr.plugin.core.internal.observations.ObsWidget;
import zephyr.plugin.core.internal.observations.SensorCollection;
import zephyr.plugin.core.internal.observations.SensorGroup;
import zephyr.plugin.core.internal.observations.SensorTextGroup;
import zephyr.plugin.core.internal.observations.SensorTextGroup.TextClient;
import zephyr.plugin.core.internal.views.Restartable;
import zephyr.plugin.critterview.FileHandler;

@SuppressWarnings({ "synthetic-access", "restriction" })
public class ObservationView extends EnvironmentView<CritterbotProblem> implements Restartable {
  static public class Provider extends ClassViewProvider {
    public Provider() {
      super(CritterbotProblem.class);
    }
  }

  protected class IntegerTextClient extends TextClient {
    private final int labelIndex;

    public IntegerTextClient(Legend legend, String obsLabel, String textLabel) {
      super(textLabel);
      labelIndex = legend.indexOf(obsLabel);
    }

    @Override
    public String currentText() {
      if (labelIndex < 0 || currentObservation == null)
        return "0000";
      return String.valueOf((int) currentObservation[labelIndex]);
    }
  }

  double[] currentObservation;
  private final TerminateAction terminateAction;
  private final RestartAction restartAction;
  private String filepath;

  public ObservationView() {
    terminateAction = new TerminateAction(this);
    terminateAction.setEnabled(false);
    restartAction = new RestartAction(this);
    restartAction.setEnabled(false);
  }

  @Override
  protected ObsLayout getObservationLayout(Clock clock, CritterbotProblem current) {
    Legend legend = current.legend();
    SensorGroup irDistanceGroup = new SensorGroup("IR Distance Sensors", startsWith(legend, IRDistance), 0, 255);
    SensorGroup lightGroup = new SensorGroup("Light Sensors", startsWith(legend, Light), 0, 800);
    SensorGroup motorSpeedGroup = new SensorGroup("Speed", startsWith(legend, Motor + Speed), -35, 35);
    SensorGroup motorCurrentGroup = new SensorGroup("Current", startsWith(legend, Motor + Current), 0, 90);
    SensorGroup motorTemperatureGroup = new SensorGroup("Temperature", startsWith(legend, Motor + Temperature), 40, 175);
    SensorCollection motorCollection = new SensorCollection("Motors", motorSpeedGroup, motorCurrentGroup,
                                                            motorTemperatureGroup);
    SensorGroup rotVelGroup = new SensorGroup("Gyroscope", startsWith(legend, RotationVel), 0, 255);
    SensorGroup accelGroup = new SensorGroup("Accelerometers", startsWith(legend, Accel), -2048, 2048);
    SensorGroup magGroup = new SensorGroup("Magnetometers", startsWith(legend, Mag), -2048, 2048);
    SensorCollection inertialCollection = new SensorCollection("Inertial Sensors", rotVelGroup, accelGroup);
    SensorGroup irLightGroup = new SensorGroup("IR Light Sensors", startsWith(legend, IRLight), 0, 255);
    SensorGroup thermalGroup = new SensorGroup("Thermal Sensors", startsWith(legend, Thermal), 14600, 15100);
    SensorGroup leftMicrophoneGroup = new SensorGroup("Microphone Left", startsWith(legend, MicrophoneFFT + "Left"), 0,
                                                      80);
    SensorGroup rightMicrophoneGroup = new SensorGroup("Microphone Right", startsWith(legend, MicrophoneFFT + "Right"),
                                                       0, 80);
    SensorTextGroup infoGroup = createInfoGroup(clock, legend);
    return new ObsLayout(new ObsWidget[][] { { infoGroup, irDistanceGroup, lightGroup },
        { magGroup, motorCollection, inertialCollection }, { irLightGroup, thermalGroup },
        { leftMicrophoneGroup, rightMicrophoneGroup } });
  }

  @Override
  protected void setToolbar(IToolBarManager toolBarManager) {
    toolBarManager.add(restartAction);
    toolBarManager.add(terminateAction);
  }

  private SensorTextGroup createInfoGroup(final Clock clock, final Legend legend) {
    TextClient busVoltageTextClient = new TextClient("Voltage:") {
      int busVoltageIndex = legend.indexOf(BusVoltage);

      @Override
      public String currentText() {
        if (currentObservation == null)
          return "00.0V";
        return String.format("%.1fV", currentObservation[busVoltageIndex] / 10.0);
      }
    };
    TextClient loopTimeTextClient = new TextClient("Loop Time:") {
      @Override
      public String currentText() {
        return Chrono.toPeriodString(clock.lastPeriodNano());
      }
    };
    TextClient cycleTimeTextClient = new TextClient("Cycle Time:") {
      int cycleTimeIndex = legend.indexOf(CritterbotLabels.CycleTime);

      @Override
      public String currentText() {
        if (currentObservation == null)
          return "00%";
        return String.valueOf((int) currentObservation[cycleTimeIndex]) + "%";
      }
    };
    new IntegerTextClient(legend, CritterbotLabels.CycleTime, "");
    return new SensorTextGroup("Info", busVoltageTextClient, loopTimeTextClient, cycleTimeTextClient,
                               new IntegerTextClient(legend, CritterbotLabels.PowerSource, "Power Source"),
                               new IntegerTextClient(legend, CritterbotLabels.ChargeState, "Charge State"),
                               new IntegerTextClient(legend, CritterbotLabels.MonitorState, "Monitor State"),
                               new IntegerTextClient(legend, CritterbotLabels.ErrorFlags, "Error Flag"));
  }

  private int[] startsWith(Legend legend, String prefix) {
    List<Integer> indexes = new ArrayList<Integer>();
    for (Map.Entry<String, Integer> entry : legend.legend().entrySet())
      if (entry.getKey().startsWith(prefix))
        indexes.add(entry.getValue());
    Collections.sort(indexes);
    return Utils.asIntArray(indexes);
  }

  private void setViewTitle(CritterbotProblem problem) {
    if (problem == null) {
      setViewName("Observation", "");
      return;
    }
    CrtrLogFile logFile = problem instanceof CrtrLogFile ? (CrtrLogFile) problem : null;
    String viewTitle = logFile == null ? problem.getClass().getSimpleName() : new File(logFile.filepath).getName();
    String tooltip = logFile == null ? "" : logFile.filepath;
    setViewName(viewTitle, tooltip);
  }

  @Override
  public void restart() {
    close();
    ZephyrCore.start(new Runnable() {
      @Override
      public void run() {
        FileHandler.handle(filepath);
      }
    });
  }

  @Override
  public void dispose() {
    close();
    super.dispose();
  }

  @Override
  protected boolean isInstanceSupported(Object instance) {
    return CritterbotProblem.class.isInstance(instance);
  }

  @Override
  protected void setLayout(Clock clock, CritterbotProblem current) {
    super.setLayout(clock, current);
    boolean restartable = current instanceof CrtrLogFile;
    restartAction.setEnabled(restartable);
    filepath = restartable ? ((CrtrLogFile) current).filepath : null;
    terminateAction.setEnabled(true);
    setViewTitle(current);
  }

  @Override
  protected boolean synchronize(CritterbotProblem current) {
    currentObservation = current.lastReceivedObs();
    synchronize(currentObservation);
    return true;
  }

  @Override
  protected void unsetLayout() {
    super.unsetLayout();
    restartAction.setEnabled(false);
    terminateAction.setEnabled(false);
    setViewTitle(null);
  }
}
